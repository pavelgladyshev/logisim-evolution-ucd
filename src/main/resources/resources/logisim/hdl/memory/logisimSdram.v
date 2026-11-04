// Logisim's RAM in an SDR SDRAM of 2M words of 32 bits (4 banks of 2048 rows of 256 columns), such as the 8 MB
// inside the GW2AR-18 of a Sipeed Tang Nano 20K: the FPGA flow's HDL for a RAM whose FPGA memory is SDRAM.
//
// It does what Logisim's RAM does with an asynchronous read and a separate data bus: it writes the enabled bytes at
// its clock's rising edge, and shows the word at its address, whatever the byte enables, while its output enable is
// not 0; while it is 0, the output keeps its value. On the FPGA, a cycle of the RAM's clock is a bus cycle from one
// tick to the next (T0 = the FPGA clock edge at which tick = 1, Tn = n FPGA clocks later). The RAM reads the word at
// the address that its inputs give at T1 and then, if the cycle writes, writes there:
//    T1  the address, data, write and output enables and byte enables are registered (they had a clock to settle),
//        and the row is opened (ACTIVE)
//    T2  READ, with auto-precharge unless the cycle writes
//    T4  the word read (CAS latency 2) goes to the output, if the output is enabled
//    T5  WRITE of the enabled bytes, with auto-precharge; the bank is idle again at T8
// So the memory changes before the tick that ends the cycle instead of at it, which nothing can see: the cycle has
// read already, and the next cycle reads what was written, as in Logisim. A refresh (one every REFRESH clocks, 4096
// in 64 ms) takes T_RFC clocks between two bus cycles; a tick during one starts its cycle up to T_RFC clocks late.
// Hence ticks must be at least 12 FPGA clocks apart: at most 2.25 MHz with the Tang Nano 20K's 27 MHz.
//
// The SDRAM's clock is the FPGA's, inverted: the SDRAM takes the commands, addresses and data in the middle of an
// FPGA clock, and its read data, which comes a few ns after its clock edge, is taken at the FPGA clock edge half a
// clock later. That works for FPGA clocks of up to 50 MHz.
//
// After the FPGA starts, the module waits POWER_UP clocks (200 us), starts the SDRAM (precharge, 8 refreshes, CAS
// latency 2 and bursts of 1 word) and writes 0 to its words, in whole rows, from 0 to 2^ADDR_BITS - 1, since
// Logisim's RAM starts with 0s: at 27 MHz, about 10 ms for 1 MB. Then ready is 1. A tick before that is remembered,
// so the bus cycle it starts is done afterwards; the FPGA flow starts its ticks late when a RAM is in the SDRAM.
module logisimSdram #(
   parameter integer ADDR_BITS = 18,     // the RAM's address bits (words of 32 bits): at most 21
   parameter integer POWER_UP  = 5402,   // FPGA clocks to wait before the SDRAM's start-up: at least 200 us
   parameter integer REFRESH   = 378,    // FPGA clocks between two refreshes: at most 15.6 us
   parameter integer T_RFC     = 3       // FPGA clocks a refresh takes: at least 70 ns, and 2
) (
   input  wire                 clock,
   input  wire                 tick,
   input  wire [ADDR_BITS-1:0] address,
   input  wire [31:0]          dataIn,
   input  wire                 we,
   input  wire                 oe,
   input  wire [3:0]           be,
   output wire [31:0]          dataOut,
   output wire                 ready,
   output wire                 sdramClk,
   output wire                 sdramCke,
   output wire                 sdramCsN,
   output wire                 sdramRasN,
   output wire                 sdramCasN,
   output wire                 sdramWeN,
   output wire [3:0]           sdramDqm,
   output wire [10:0]          sdramAddr,
   output wire [1:0]           sdramBa,
   inout  wire [31:0]          sdramDq
);
   // the commands, as chip select, RAS, CAS and write enable (all low active)
   localparam [3:0] C_INHIBIT = 4'b1111, C_NOP = 4'b0111, C_ACTIVE = 4'b0011, C_READ = 4'b0101,
                    C_WRITE = 4'b0100, C_PRECHARGE = 4'b0010, C_REFRESH = 4'b0001, C_MODE = 4'b0000;
   // CAS latency 2, sequential bursts of 1 word
   localparam [10:0] MODE = 11'b000_0010_0000;
   // the rows to clear: 256 words each, {row, bank} = word address >> 8
   localparam integer CLEAR_ROWS = (ADDR_BITS > 8) ? (1 << (ADDR_BITS - 8)) : 1;

   localparam [4:0] S_POWER = 5'd0, S_PRECHARGE_ALL = 5'd1, S_INIT_REFRESH = 5'd2, S_MODE = 5'd3,
                    S_CLEAR_ACTIVE = 5'd4, S_CLEAR_WRITE = 5'd5, S_CLEAR_PRECHARGE = 5'd6, S_CLEAR_REFRESH = 5'd7,
                    S_IDLE = 5'd8, S_ACTIVE = 5'd9, S_READ = 5'd10, S_LATENCY = 5'd11, S_DATA = 5'd12,
                    S_WRITE = 5'd13, S_REFRESH = 5'd14, S_WAIT = 5'd15;

   reg  [3:0]  s_cmd = C_INHIBIT;
   reg  [3:0]  s_dqm = 4'b1111;
   reg  [10:0] s_addr = 11'd0;
   reg  [1:0]  s_ba = 2'd0;
   reg         s_dqOe = 1'b0;
   reg  [31:0] s_dqOut = 32'd0;
   reg  [31:0] s_dataOut = 32'd0;
   reg  [4:0]  s_state = S_POWER;
   reg  [4:0]  s_next = S_POWER;
   reg  [19:0] s_count = POWER_UP;
   reg  [2:0]  s_refreshes = 3'd0;
   reg  [12:0] s_clearRow = 13'd0;
   reg  [7:0]  s_column = 8'd0;
   reg  [15:0] s_refreshCount = REFRESH - 1;
   reg         s_refreshDue = 1'b0;
   reg         s_tick = 1'b0;        // a tick whose bus cycle has not started yet
   reg         s_ready = 1'b0;
   reg  [20:0] s_addressReg = 21'd0;
   reg  [31:0] s_dataInReg = 32'd0;
   reg         s_weReg = 1'b0;
   reg         s_oeReg = 1'b0;
   reg  [3:0]  s_beReg = 4'd0;
   wire [20:0] s_address = address;  // column 7:0, bank 9:8, row 20:10

   assign {sdramCsN, sdramRasN, sdramCasN, sdramWeN} = s_cmd;
   assign sdramCke  = 1'b1;
   assign sdramClk  = ~clock;
   assign sdramDqm  = s_dqm;
   assign sdramAddr = s_addr;
   assign sdramBa   = s_ba;
   assign sdramDq   = s_dqOe ? s_dqOut : 32'bz;
   assign dataOut   = s_dataOut;
   assign ready     = s_ready;

   always @(posedge clock) begin
      s_cmd  <= C_NOP;
      s_dqOe <= 1'b0;
      s_dqm  <= 4'b0000;
      if (tick) s_tick <= 1'b1;
      case (s_state)
         // start-up: 200 us of nothing, then precharge, 8 refreshes and the mode
         S_POWER : begin
            s_cmd <= C_INHIBIT;
            s_dqm <= 4'b1111;
            if (s_count == 20'd0) s_state <= S_PRECHARGE_ALL;
            else s_count <= s_count - 20'd1;
         end
         S_PRECHARGE_ALL : begin
            s_cmd <= C_PRECHARGE;
            s_dqm <= 4'b1111;
            s_addr <= 11'b100_0000_0000;                 // all banks
            s_refreshes <= 3'd7;
            s_count <= 20'd0;                            // 2 clocks: tRP
            s_next <= S_INIT_REFRESH;
            s_state <= S_WAIT;
         end
         S_INIT_REFRESH : begin
            s_cmd <= C_REFRESH;
            s_dqm <= 4'b1111;
            s_refreshes <= s_refreshes - 3'd1;
            s_count <= T_RFC - 2;
            s_next <= (s_refreshes == 3'd0) ? S_MODE : S_INIT_REFRESH;
            s_state <= S_WAIT;
         end
         S_MODE : begin
            s_cmd <= C_MODE;
            s_dqm <= 4'b1111;
            s_ba <= 2'd0;
            s_addr <= MODE;
            s_count <= 20'd1;                            // 3 clocks: tMRD
            s_next <= S_CLEAR_ACTIVE;
            s_state <= S_WAIT;
         end
         // clearing: each row opened, written with 0 a column a clock, closed and followed by a refresh
         S_CLEAR_ACTIVE : begin
            s_cmd <= C_ACTIVE;
            s_ba <= s_clearRow[1:0];
            s_addr <= s_clearRow[12:2];
            s_column <= 8'd0;
            s_state <= S_CLEAR_WRITE;
         end
         S_CLEAR_WRITE : begin
            s_cmd <= C_WRITE;
            s_addr <= {3'b000, s_column};                // no auto-precharge
            s_dqOe <= 1'b1;
            s_dqOut <= 32'd0;
            s_column <= s_column + 8'd1;
            if (s_column == 8'd255) begin
               s_count <= 20'd1;                         // 3 clocks: tWR
               s_next <= S_CLEAR_PRECHARGE;
               s_state <= S_WAIT;
            end
         end
         S_CLEAR_PRECHARGE : begin
            s_cmd <= C_PRECHARGE;
            s_addr <= 11'd0;                             // the bank of the row
            s_count <= 20'd0;                            // 2 clocks: tRP
            s_next <= S_CLEAR_REFRESH;
            s_state <= S_WAIT;
         end
         S_CLEAR_REFRESH : begin
            s_cmd <= C_REFRESH;
            s_refreshDue <= 1'b0;
            s_clearRow <= s_clearRow + 13'd1;
            s_count <= T_RFC - 2;
            s_next <= (s_clearRow == CLEAR_ROWS - 1) ? S_IDLE : S_CLEAR_ACTIVE;
            s_state <= S_WAIT;
         end
         // running: a bus cycle at each tick, refreshes in between
         S_IDLE : begin
            s_ready <= 1'b1;
            if (tick || s_tick) begin
               s_tick <= 1'b0;
               s_state <= S_ACTIVE;
            end else if (s_refreshDue) s_state <= S_REFRESH;
         end
         S_ACTIVE : begin                                // T1
            s_addressReg <= s_address;
            s_dataInReg <= dataIn;
            s_weReg <= we;
            s_oeReg <= oe;
            s_beReg <= be;
            s_cmd <= C_ACTIVE;
            s_ba <= s_address[9:8];
            s_addr <= s_address[20:10];
            s_state <= S_READ;
         end
         S_READ : begin                                  // T2
            s_cmd <= C_READ;
            s_addr <= {~s_weReg, 2'b00, s_addressReg[7:0]};
            s_state <= S_LATENCY;
         end
         S_LATENCY : s_state <= S_DATA;                  // T3
         S_DATA : begin                                  // T4
            if (s_oeReg) s_dataOut <= sdramDq;
            s_state <= s_weReg ? S_WRITE : S_IDLE;
         end
         S_WRITE : begin                                 // T5
            s_cmd <= C_WRITE;
            s_addr <= {1'b1, 2'b00, s_addressReg[7:0]};  // auto-precharge
            s_dqm <= ~s_beReg;
            s_dqOe <= 1'b1;
            s_dqOut <= s_dataInReg;
            s_count <= 20'd1;                            // 3 clocks: tWR, then tRP
            s_next <= S_IDLE;
            s_state <= S_WAIT;
         end
         S_REFRESH : begin
            s_cmd <= C_REFRESH;
            s_refreshDue <= 1'b0;
            s_count <= T_RFC - 2;
            s_next <= S_IDLE;
            s_state <= S_WAIT;
         end
         S_WAIT : begin
            if (s_count == 20'd0) s_state <= s_next;
            else s_count <= s_count - 20'd1;
         end
         default : s_state <= S_IDLE;
      endcase
      // a refresh is due every REFRESH clocks
      if (s_refreshCount == 16'd0) begin
         s_refreshCount <= REFRESH - 1;
         s_refreshDue <= 1'b1;
      end else s_refreshCount <= s_refreshCount - 16'd1;
   end
endmodule
