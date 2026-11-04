// logisimSdram on a Sipeed Tang Nano 20K, with a state machine for a CPU that does a bus cycle at each tick, TICK
// clocks of 27 MHz apart, over all 2^ADDR_BITS words of the SDRAM (8 MB unless set), in passes:
//    zero    (the first pass only) each word is 0, as the module leaves them after its start-up
//    write   each word gets a pattern of its address and the pass
//    read    each word has its pattern
//    bytes   one byte of each word (the word's address mod 4) gets another pattern, with that byte enable alone
//    verify  each word has its pattern with that byte changed
// After each pass it sends a line over the board's USB serial port (115200 8N1) with the pass's number, the wrong
// words of each phase and the first wrong word (address, what it was, what it should have been), in hex.
// LEDs (lit low): 0 a wrong word, 1 SDRAM ready, 2 the passes, 5 alive.
module sdram_board_test #(
   parameter integer ADDR_BITS = 21,
   parameter integer TICK = 12
) (
   input         clk,
   output        uart_tx,
   output [5:0]  led,
   output        sdram_clk,
   output        sdram_cke,
   output        sdram_cs_n,
   output        sdram_cas_n,
   output        sdram_ras_n,
   output        sdram_wen_n,
   output [3:0]  sdram_dqm,
   output [10:0] sdram_addr,
   output [1:0]  sdram_ba,
   inout  [31:0] sdram_dq
);
   localparam [ADDR_BITS:0] WORDS = 1 << ADDR_BITS;

   reg  [7:0] tickCount = 0;
   wire tick = (tickCount == TICK - 1);
   always @(posedge clk) tickCount <= tick ? 8'd0 : tickCount + 8'd1;

   reg  [ADDR_BITS-1:0] address = 0;
   reg  [31:0] dataIn = 0;
   reg         we = 0, oe = 0;
   reg  [3:0]  be = 4'hf;
   wire [31:0] dataOut;
   wire        ready;
   logisimSdram #(.ADDR_BITS(ADDR_BITS), .POWER_UP(5402), .REFRESH(378), .T_RFC(3)) ram (
      .clock(clk), .tick(tick), .address(address), .dataIn(dataIn), .we(we), .oe(oe), .be(be),
      .dataOut(dataOut), .ready(ready), .sdramClk(sdram_clk), .sdramCke(sdram_cke), .sdramCsN(sdram_cs_n),
      .sdramRasN(sdram_ras_n), .sdramCasN(sdram_cas_n), .sdramWeN(sdram_wen_n), .sdramDqm(sdram_dqm),
      .sdramAddr(sdram_addr), .sdramBa(sdram_ba), .sdramDq(sdram_dq));

   function [31:0] pattern(input [20:0] i, input [7:0] s);
      pattern = {~i[10:0], i} ^ {4{s}};
   endfunction
   function [7:0] bytePattern(input [20:0] i, input [7:0] s);
      bytePattern = i[7:0] ^ i[15:8] ^ s ^ 8'h3c;
   endfunction
   function [31:0] merged(input [20:0] i, input [7:0] s);
      begin
         merged = pattern(i, s);
         merged[8 * i[1:0] +: 8] = bytePattern(i, s);
      end
   endfunction

   localparam [2:0] P_START = 0, P_ZERO = 1, P_WRITE = 2, P_READ = 3, P_BYTES = 4, P_VERIFY = 5, P_REPORT = 6;
   reg  [2:0]  phase = P_START;
   reg  [ADDR_BITS:0] index = 0;
   wire [20:0] i = index[ADDR_BITS-1:0];
   reg  [7:0]  pass = 0;
   reg         checking = 0;
   reg  [1:0]  checkKind = 0;            // 0 zero, 1 write (read phase), 2 bytes (verify phase)
   reg  [31:0] expect = 0;
   reg  [ADDR_BITS-1:0] checkAddr = 0;
   reg  [23:0] errors [0:2];
   reg         haveBad = 0, anyBad = 0;
   reg  [23:0] badAddr = 0;
   reg  [31:0] badGot = 0, badWant = 0;
   // the report of the last pass
   reg  [7:0]  rPass = 0;
   reg  [23:0] rZero = 0, rWrite = 0, rBytes = 0, rAddr = 0;
   reg  [31:0] rGot = 0, rWant = 0;
   reg         rBad = 0, startPrint = 0;
   initial begin errors[0] = 0; errors[1] = 0; errors[2] = 0; end

   always @(posedge clk) begin
      startPrint <= 1'b0;
      if (tick) begin
         // the word of the cycle that ends here
         if (checking && dataOut != expect) begin
            errors[checkKind] <= errors[checkKind] + 24'd1;
            anyBad <= 1'b1;
            if (!haveBad) begin
               haveBad <= 1'b1; badAddr <= checkAddr; badGot <= dataOut; badWant <= expect;
            end
         end
         checking <= 1'b0;
         we <= 1'b0;
         oe <= 1'b0;
         be <= 4'hf;
         case (phase)
            P_START : if (ready) begin phase <= P_ZERO; index <= 0; end
            P_ZERO, P_READ, P_VERIFY : if (index != WORDS) begin
                  address <= i; oe <= 1'b1; checking <= 1'b1; checkAddr <= i;
                  checkKind <= (phase == P_ZERO) ? 2'd0 : (phase == P_READ) ? 2'd1 : 2'd2;
                  expect <= (phase == P_ZERO) ? 32'd0 : (phase == P_READ) ? pattern(i, pass) : merged(i, pass);
                  index <= index + 1'b1;
               end else begin
                  index <= 0;
                  phase <= (phase == P_ZERO) ? P_WRITE : (phase == P_READ) ? P_BYTES : P_REPORT;
               end
            P_WRITE : if (index != WORDS) begin
                  address <= i; dataIn <= pattern(i, pass); we <= 1'b1; index <= index + 1'b1;
               end else begin index <= 0; phase <= P_READ; end
            P_BYTES : if (index != WORDS) begin
                  address <= i; dataIn <= {4{bytePattern(i, pass)}}; be <= 4'b0001 << i[1:0]; we <= 1'b1;
                  index <= index + 1'b1;
               end else begin index <= 0; phase <= P_VERIFY; end
            P_REPORT : if (!checking) begin    // the last word of the verify phase was checked at this tick
                  rPass <= pass; rZero <= errors[0]; rWrite <= errors[1]; rBytes <= errors[2];
                  rBad <= haveBad; rAddr <= badAddr; rGot <= badGot; rWant <= badWant;
                  startPrint <= 1'b1;
                  errors[0] <= 0; errors[1] <= 0; errors[2] <= 0; haveBad <= 1'b0;
                  pass <= pass + 8'd1;
                  phase <= P_WRITE;
               end
            default : ;
         endcase
      end
   end

   // the report: "pass PP zero ZZZZZZ write WWWWWW bytes BBBBBB" and " bad AAAAAA GGGGGGGG WWWWWWWW" if any
   function [7:0] hex(input [3:0] n);
      hex = (n < 10) ? 8'h30 + n : 8'h57 + n;
   endfunction
   reg  [6:0] ci = 0;
   reg        printing = 0;
   reg  [7:0] c;
   wire [6:0] lineEnd = rBad ? 7'd73 : 7'd45;
   always @(*) begin
      c = " ";
      case (ci)
         0: c = "p"; 1: c = "a"; 2: c = "s"; 3: c = "s"; 5: c = hex(rPass[7:4]); 6: c = hex(rPass[3:0]);
         8: c = "z"; 9: c = "e"; 10: c = "r"; 11: c = "o";
         13: c = hex(rZero[23:20]); 14: c = hex(rZero[19:16]); 15: c = hex(rZero[15:12]);
         16: c = hex(rZero[11:8]); 17: c = hex(rZero[7:4]); 18: c = hex(rZero[3:0]);
         20: c = "w"; 21: c = "r"; 22: c = "i"; 23: c = "t"; 24: c = "e";
         26: c = hex(rWrite[23:20]); 27: c = hex(rWrite[19:16]); 28: c = hex(rWrite[15:12]);
         29: c = hex(rWrite[11:8]); 30: c = hex(rWrite[7:4]); 31: c = hex(rWrite[3:0]);
         33: c = "b"; 34: c = "y"; 35: c = "t"; 36: c = "e"; 37: c = "s";
         39: c = hex(rBytes[23:20]); 40: c = hex(rBytes[19:16]); 41: c = hex(rBytes[15:12]);
         42: c = hex(rBytes[11:8]); 43: c = hex(rBytes[7:4]); 44: c = hex(rBytes[3:0]);
         46: c = "b"; 47: c = "a"; 48: c = "d";
         50: c = hex(rAddr[23:20]); 51: c = hex(rAddr[19:16]); 52: c = hex(rAddr[15:12]);
         53: c = hex(rAddr[11:8]); 54: c = hex(rAddr[7:4]); 55: c = hex(rAddr[3:0]);
         57: c = hex(rGot[31:28]); 58: c = hex(rGot[27:24]); 59: c = hex(rGot[23:20]); 60: c = hex(rGot[19:16]);
         61: c = hex(rGot[15:12]); 62: c = hex(rGot[11:8]); 63: c = hex(rGot[7:4]); 64: c = hex(rGot[3:0]);
         66: c = hex(rWant[31:28]); 67: c = hex(rWant[27:24]); 68: c = hex(rWant[23:20]); 69: c = hex(rWant[19:16]);
         70: c = hex(rWant[15:12]); 71: c = hex(rWant[11:8]); 72: c = hex(rWant[7:4]); 73: c = hex(rWant[3:0]);
         default: c = " ";
      endcase
      if (ci == lineEnd + 7'd1) c = 8'h0d;
      if (ci == lineEnd + 7'd2) c = 8'h0a;
   end

   reg [9:0] frame = 10'h3ff;
   reg [3:0] bitsLeft = 0;
   reg [8:0] baud = 0;
   wire      txIdle = (bitsLeft == 0);
   assign uart_tx = frame[0];
   always @(posedge clk) begin
      if (startPrint) begin printing <= 1'b1; ci <= 0; end
      else if (printing && txIdle) begin
         frame <= {1'b1, c, 1'b0}; bitsLeft <= 4'd10; baud <= 0;
         if (ci == lineEnd + 7'd2) printing <= 1'b0; else ci <= ci + 7'd1;
      end else if (bitsLeft != 0) begin
         if (baud == 9'd233) begin baud <= 0; frame <= {1'b1, frame[9:1]}; bitsLeft <= bitsLeft - 4'd1; end
         else baud <= baud + 9'd1;
      end
   end

   reg [23:0] alive = 0;
   always @(posedge clk) alive <= alive + 24'd1;
   assign led = ~{alive[23], 2'b00, pass[0], ready, anyBad};
endmodule
