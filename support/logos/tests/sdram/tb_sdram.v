`timescale 1ns/1ps
// logisimSdram against sdram_model, with the bus cycles of a CPU at ticks every PERIOD clocks of 27 MHz: random
// addresses (often the same few), reads, writes, both and neither, with random byte enables. What the RAM gives at
// the end of a cycle with its output enabled must be what Logisim's RAM gives: the word at the address, with the
// writes of the cycles before. Each cycle's word must be out within the cycle, and no address may be X.
//    iverilog -DPERIOD=12 -o tb tb_sdram.v sdram_model.v logisimSdram.v && vvp -n tb
// With -DGENERATED=<module>, the RAM is the module that Logisim's FPGA flow generates for a RAM of 32 bits with byte
// enables (in its memory folder; it has logisimSdram in it), whose FPGA memory is SDRAM; add -DBLOCK_RAM when its
// FPGA memory is block RAM (no SDRAM, and no start-up: the memory starts with 0s).
`ifndef PERIOD
 `define PERIOD 12
`endif
`ifndef CYCLES
 `define CYCLES 40000
`endif
`ifndef ADDR_BITS
 `define ADDR_BITS 18
`endif
module tb_sdram;
   localparam integer PERIOD = `PERIOD, ADDR_BITS = `ADDR_BITS;
   reg clock = 0;
   always #18.5185 clock = ~clock;
   integer tickCount = 0;
   wire tick = (tickCount == PERIOD - 1);
   always @(posedge clock) tickCount <= tick ? 0 : tickCount + 1;

   reg  [ADDR_BITS-1:0] address = 0;
   reg  [31:0] dataIn = 0;
   reg         we = 0, oe = 0;
   reg  [3:0]  be = 4'hf;
   wire [31:0] dataOut, dq;
   wire        ready, sdClk, cke, csN, rasN, casN, weN;
   wire [3:0]  dqm;
   wire [10:0] a;
   wire [1:0]  ba;

`ifdef GENERATED
 `ifdef BLOCK_RAM
   assign ready = 1'b1;
   `GENERATED dut (.clock(clock), .tick(tick), .address(address), .dataIn(dataIn), .we(we), .oe(oe),
      .byteEnable0(be[0]), .byteEnable1(be[1]), .byteEnable2(be[2]), .byteEnable3(be[3]), .dataOut(dataOut));
 `else
   wire [22:0] sdramOut;
   assign {ba, a, dqm, weN, casN, rasN, csN, cke, sdClk} = sdramOut;
   assign ready = dut.sdram.ready;
   `GENERATED dut (.clock(clock), .tick(tick), .address(address), .dataIn(dataIn), .we(we), .oe(oe),
      .byteEnable0(be[0]), .byteEnable1(be[1]), .byteEnable2(be[2]), .byteEnable3(be[3]), .dataOut(dataOut),
      .sdramOut(sdramOut), .sdramDq(dq));
  `define CONTROLLER dut.sdram
 `endif
`else
   logisimSdram #(.ADDR_BITS(ADDR_BITS), .POWER_UP(5402), .REFRESH(378), .T_RFC(3)) dut (
      .clock(clock), .tick(tick), .address(address), .dataIn(dataIn), .we(we), .oe(oe), .be(be),
      .dataOut(dataOut), .ready(ready), .sdramClk(sdClk), .sdramCke(cke), .sdramCsN(csN), .sdramRasN(rasN),
      .sdramCasN(casN), .sdramWeN(weN), .sdramDqm(dqm), .sdramAddr(a), .sdramBa(ba), .sdramDq(dq));
 `define CONTROLLER dut
`endif
   integer contention = 0;
`ifdef CONTROLLER
   sdram_model card (.clk(sdClk), .cke(cke), .cs_n(csN), .ras_n(rasN), .cas_n(casN), .we_n(weN), .dqm(dqm),
      .addr(a), .ba(ba), .dq(dq));
   // the FPGA and the SDRAM both driving DQ
   always #0.5 if (`CONTROLLER.s_dqOe && card.outOn) contention = contention + 1;
`endif

   // Logisim's RAM
   reg  [31:0] memory [0:(1 << ADDR_BITS) - 1];
   reg  [ADDR_BITS-1:0] hot [0:7];
   integer i, seed = 1, r, cycles = 0, checked = 0, errors = 0, late = 0, sinceTick = 0, dataAt = 0, maxDataAt = 0;
   reg running = 0;
   reg [31:0] expect;
   initial begin
      for (i = 0; i < (1 << ADDR_BITS); i = i + 1) memory[i] = 0;
      for (i = 0; i < 8; i = i + 1) hot[i] = $random(seed);
      hot[7] = 0;
      hot[6] = {ADDR_BITS{1'b1}};
   end

   // when the word of a cycle is taken (in FPGA clocks after its tick)
   always @(posedge clock) begin
      sinceTick = tick ? 0 : sinceTick + 1;
`ifdef CONTROLLER
      if (`CONTROLLER.s_state == 5'd12) begin
         dataAt = sinceTick;
         if (dataAt > maxDataAt && running) maxDataAt = dataAt;
      end
`endif
   end

   always @(posedge clock) if (tick) begin
      if (running) begin
         // the end of a bus cycle: the CPU takes the RAM's word, then the RAM writes
         if (oe) begin
            checked = checked + 1;
            if (dataOut !== memory[address]) begin
               errors = errors + 1;
               if (errors <= 10)
                  $display("FAIL cycle %0d: word %h at %h, Logisim's RAM has %h", cycles, dataOut, address, memory[address]);
            end
         end
         if (we) for (i = 0; i < 4; i = i + 1) if (be[i]) memory[address][8*i +: 8] = dataIn[8*i +: 8];
`ifdef CONTROLLER
         if (`CONTROLLER.s_state >= 5'd9 && `CONTROLLER.s_state <= 5'd13) late = late + 1;
`endif
         cycles = cycles + 1;
      end else if (ready) running = 1;
      // the next bus cycle
      r = $random(seed);
      address <= running ? (r[0] ? hot[r[3:1]] : $random(seed)) : 0;
      dataIn <= $random(seed);
      we <= running && (r[5:4] == 2'd0 || r[13:12] == 2'd3 && r[14]);
      oe <= running && (r[7:6] != 2'd0);
      be <= (r[9:8] == 2'd0) ? (4'b0001 << r[11:10]) : (r[9:8] == 2'd1) ? (r[10] ? 4'b1100 : 4'b0011) : 4'b1111;
      if (running && cycles == `CYCLES) begin
         $display("%0d cycles of %0d clocks, %0d words checked: %0d wrong, %0d late; the word at T%0d at the latest",
                  cycles, PERIOD, checked, errors, late, maxDataAt);
`ifdef CONTROLLER
         $display("SDRAM: %0d errors, %0d reads, %0d writes, %0d refreshes (at most %0.2f us apart), %0d ns of contention",
                  card.errors, card.reads, card.writes, card.refreshes, card.maxRefreshGap / 1000.0, contention / 2);
         $display("%0s", (errors == 0 && late == 0 && card.errors == 0 && contention == 0) ? "PASS" : "FAIL");
`else
         $display("%0s", (errors == 0) ? "PASS" : "FAIL");
`endif
         $finish;
      end
   end
endmodule
