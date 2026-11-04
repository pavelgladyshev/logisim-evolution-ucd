// riscvSdBlockStorage on a Sipeed Tang Nano 20K, with a state machine for a CPU: half a second after power-up it
// reads block BLOCK (0 unless set) of the microSD card in the TF slot to memory address 0, as LogOS's driver would (memory address,
// block number, command, then the status until it is not busy), granting the bus as the RV32IM CPU does, then sends
// the status and the block in hex over the board's USB serial port (115200 8N1). Block 0 of a card that holds a
// partition table ends with 55 aa, and so does the first block of a FAT partition. S1 reads it again. Nothing is
// written to the card.
// LEDs (lit low): 0 waiting for the card, 1 error, 2 done, 5 alive.
module sd_board_test #(parameter [31:0] BLOCK = 0) (
   input        clk,          // 27 MHz
   input        s1,           // 1 while pressed
   output       uart_tx,
   output       sd_sck,
   output       sd_mosi,
   output       sd_cs,
   input        sd_miso,
   output [5:0] led
);
   // a tick of about 1 MHz, as Logisim's 1024 kHz gives on this board
   reg [4:0] tickCount = 0;
   wire tick = (tickCount == 5'd25);
   always @(posedge clk) tickCount <= tick ? 5'd0 : tickCount + 5'd1;

   reg  [2:0]  addr = 0;
   reg         re = 0, we = 0;
   reg  [31:0] dataIn = 0;
   wire [31:0] dataOut, dmaAddr;
   wire        busReq, memRead, memWrite;
   wire [3:0]  be;
   reg         busAck = 0;
   reg  [31:0] ram [0:127];
   reg  [31:0] ramWord;
   wire [31:0] memDataIn = ramWord;

   riscvSdBlockStorage #(.SLOW_DIV(34), .FAST_DIV(1), .POWER_UP(54000)) storage (
      .clock(clk), .tick(tick), .reset(1'b0), .addr(addr), .re(re), .we(we), .dataIn(dataIn), .dataOut(dataOut),
      .busReq(busReq), .busAck(busAck), .memRead(memRead), .memWrite(memWrite), .dmaAddr(dmaAddr), .be(be),
      .memDataIn(memDataIn), .sdSck(sd_sck), .sdMosi(sd_mosi), .sdCs(sd_cs), .sdMiso(sd_miso));

   // memory, and the bus grant as the CPU gives it
   always @(posedge clk) begin
      ramWord <= ram[dmaAddr[8:2]];
      if (tick) begin
         if (memWrite) ram[dmaAddr[8:2]] <= dataOut;
         if (busReq && !busAck) busAck <= 1'b1;
         else if (!busReq && busAck) busAck <= 1'b0;
      end
   end
   wire cpuTick = tick && !busReq && !busAck;   // a tick at which the CPU's bus cycle ends

   // the "CPU": registers, then polling
   localparam P_WAIT = 0, P_MEM = 1, P_BLOCK = 2, P_CMD = 3, P_POLL = 4, P_PRINT = 5, P_IDLE = 6;
   reg [2:0]  phase = P_WAIT;
   reg [24:0] wait_ = 0;
   reg [31:0] status = 0;
   reg        start = 0;
   reg        s1Last = 0;
   always @(posedge clk) begin
      start <= 1'b0;
      s1Last <= s1;
      case (phase)
         P_WAIT  : if (wait_ == 25'd13_500_000) phase <= P_MEM; else wait_ <= wait_ + 25'd1;
         P_MEM   : if (cpuTick) begin addr <= 1; dataIn <= 0; we <= 1; phase <= P_BLOCK; end
         P_BLOCK : if (cpuTick) begin addr <= 2; dataIn <= BLOCK; we <= 1; phase <= P_CMD; end
         P_CMD   : if (cpuTick) begin addr <= 0; dataIn <= 1; we <= 1; phase <= P_POLL; end
         P_POLL  : if (cpuTick) begin
                      we <= 0; addr <= 3; re <= 1;
                      if (re) begin
                         status <= dataOut;
                         if (dataOut != 32'd1) begin re <= 0; start <= 1'b1; phase <= P_PRINT; end
                      end
                   end
         P_PRINT : if (printDone) phase <= P_IDLE;
         P_IDLE  : if (s1 && !s1Last) phase <= P_MEM;
         default : ;
      endcase
   end

   // the report: "status N" and the block in hex, 32 bytes a line
   reg [9:0]  index = 0;          // 0: the status line, then byte 0..511, then the end
   reg [3:0]  part = 0;
   reg        printing = 0;
   wire       printDone = printing && (index == 10'd513) && txIdle && !txGo;
   reg  [7:0] txByte;
   reg        txGo = 0;
   wire       txIdle;
   wire [7:0] dataByte = ram[(index - 10'd1) >> 2][8 * ((index - 10'd1) & 2'd3) +: 8];
   function [7:0] hex(input [3:0] n); hex = (n < 10) ? 8'h30 + n : 8'h57 + n; endfunction
   always @(posedge clk) begin
      txGo <= 1'b0;
      if (start) begin printing <= 1; index <= 0; part <= 0; end
      else if (printing && txIdle && !txGo && index != 10'd513) begin
         txGo <= 1'b1;
         if (index == 0) begin
            case (part)
               0: txByte <= "s"; 1: txByte <= "t"; 2: txByte <= "a"; 3: txByte <= "t"; 4: txByte <= "u";
               5: txByte <= "s"; 6: txByte <= " "; 7: txByte <= hex(status[3:0]); 8: txByte <= 8'h0d;
               default: txByte <= 8'h0a;
            endcase
            if (part == 9) begin part <= 0; index <= 1; end else part <= part + 4'd1;
         end else begin
            case (part)
               0: txByte <= hex(dataByte[7:4]);
               1: txByte <= hex(dataByte[3:0]);
               default: txByte <= ((index & 10'd31) == 10'd0) ? 8'h0d : 8'h20;
            endcase
            if (part == 2 && (index & 10'd31) == 10'd0) part <= 3;          // CR, then LF
            else if (part == 3) begin txByte <= 8'h0a; part <= 0; index <= index + 10'd1; end
            else if (part == 2) begin part <= 0; index <= index + 10'd1; end
            else part <= part + 4'd1;
         end
      end
      if (printDone) printing <= 0;
   end

   // 115200 8N1 from 27 MHz
   reg [9:0] frame = 10'h3ff;
   reg [3:0] bitsLeft = 0;
   reg [8:0] baud = 0;
   assign txIdle = (bitsLeft == 0);
   assign uart_tx = frame[0];
   always @(posedge clk)
      if (txGo && txIdle) begin frame <= {1'b1, txByte, 1'b0}; bitsLeft <= 4'd10; baud <= 0; end
      else if (bitsLeft != 0) begin
         if (baud == 9'd233) begin baud <= 0; frame <= {1'b1, frame[9:1]}; bitsLeft <= bitsLeft - 4'd1; end
         else baud <= baud + 9'd1;
      end

   reg [23:0] alive = 0;
   always @(posedge clk) alive <= alive + 24'd1;
   assign led = ~{alive[23], 2'b00, phase == P_IDLE, status[1], phase == P_POLL};
endmodule
