// riscvSdBlockStorage against sd_card_model: the CPU's side as the testbench, the card's as the model.
// The testbench writes the registers at ticks, grants the bus as the RV32IM component does (at the tick that sees
// busReq, released at the tick that sees it low again) and is the RAM: it takes dataOut at a tick with memWrite and
// gives the word at dmaAddr on memDataIn.  iverilog -DHC=1 -DV2=1 -DPRESENT=1 ...
`ifndef HC
 `define HC 1
`endif
`ifndef V2
 `define V2 1
`endif
`ifndef PRESENT
 `define PRESENT 1
`endif
module tb_sd;
   reg clock = 0;
   always #1 clock = ~clock;
   reg [2:0] tickCount = 0;
   wire tick = (tickCount == 3'd7);              // a tick every 8 global clocks
   always @(posedge clock) tickCount <= tickCount + 3'd1;

   reg  [2:0]  addr = 0;
   reg         re = 0, we = 0;
   reg  [31:0] dataIn = 0;
   wire [31:0] dataOut, dmaAddr;
   wire        busReq, memRead, memWrite;
   wire [3:0]  be;
   reg         busAck = 0;
   wire        sck, mosi, cs, miso;
   reg  [31:0] ram [0:4095];
   wire [31:0] memDataIn = ram[dmaAddr[13:2]];

   riscvSdBlockStorage #(.SLOW_DIV(4), .FAST_DIV(1), .POWER_UP(200)) dut (
      .clock(clock), .tick(tick), .reset(1'b0), .addr(addr), .re(re), .we(we), .dataIn(dataIn), .dataOut(dataOut),
      .busReq(busReq), .busAck(busAck), .memRead(memRead), .memWrite(memWrite), .dmaAddr(dmaAddr), .be(be),
      .memDataIn(memDataIn), .sdSck(sck), .sdMosi(mosi), .sdCs(cs), .sdMiso(miso));
   sd_card_model #(.HIGH_CAPACITY(`HC), .V2(`V2), .PRESENT(`PRESENT)) card (.sck(sck), .mosi(mosi), .cs(cs), .miso(miso));

   // the CPU's bus grant and the RAM, at each tick; and a record of the DMA's ticks
   integer transfers = 0, lastAddr = -4, gaps = 0, badBe = 0;
   always @(posedge clock) if (tick) begin
      if (memWrite) ram[dmaAddr[13:2]] <= dataOut;
      if (memRead || memWrite) begin
         transfers = transfers + 1;
         if (dmaAddr != lastAddr + 4) gaps = gaps + 1;
         lastAddr = dmaAddr;
         if (be != 4'b1111) badBe = badBe + 1;
      end
      if (busReq && !busAck) busAck <= 1'b1;
      else if (!busReq && busAck) busAck <= 1'b0;
   end

   // the CPU's bus cycles end at a tick at which it neither has granted the bus nor grants or releases it (the
   // component then does nothing else at that edge): the last global clock before such a tick
   task cpuTick;
      begin
         @(negedge clock); while (!((tickCount == 3'd7) && !busReq && !busAck)) @(negedge clock);
      end
   endtask
   task write(input [2:0] a, input [31:0] v);
      begin
         cpuTick; addr = a; dataIn = v; we = 1;
         @(negedge clock); we = 0;
      end
   endtask
   task read(input [2:0] a, output [31:0] v);
      begin
         cpuTick; addr = a; re = 1; #0.5 v = dataOut;
         @(negedge clock); re = 0;
      end
   endtask

   integer errors = 0, i, polls;
   reg [31:0] status, result, w;
   task command(input [31:0] cmd, input [31:0] memAddr, input [31:0] block, input [31:0] expectStatus);
      begin
         transfers = 0; lastAddr = memAddr - 4; gaps = 0; badBe = 0;
         write(1, memAddr); write(2, block); write(0, cmd);
         polls = 0;
         status = 1;
         while (status == 1 && polls < 2000000) begin read(3, status); polls = polls + 1; end
         read(4, result);
         if (status != expectStatus) begin
            $display("FAIL command %0d block %0d: status %0d, expected %0d", cmd, block, status, expectStatus);
            errors = errors + 1;
         end
         if (expectStatus == 0 && (transfers != 128 || gaps != 0 || badBe != 0)) begin
            $display("FAIL command %0d: %0d transfers, %0d address gaps, %0d bad byte enables", cmd, transfers, gaps, badBe);
            errors = errors + 1;
         end
         if (expectStatus == 0 && busAck) begin
            $display("FAIL command %0d: the bus is still granted", cmd); errors = errors + 1;
         end
         if (expectStatus == 3 && result != 1) begin
            $display("FAIL command %0d: result %0d after an error", cmd, result); errors = errors + 1;
         end
      end
   endtask

   initial begin
      for (i = 0; i < 4096; i = i + 1) ram[i] = 32'hdeadbeef;
      #20000;
      if (`PRESENT) begin
         // read block 3 to 0x100
         command(1, 32'h100, 3, 0);
         for (i = 0; i < 128; i = i + 1) begin
            w = {card.mem[3*512 + 4*i + 3], card.mem[3*512 + 4*i + 2], card.mem[3*512 + 4*i + 1], card.mem[3*512 + 4*i]};
            if (ram[(32'h100 >> 2) + i] !== w) begin
               if (errors < 5) $display("FAIL read: word %0d is %h, the card has %h", i, ram[(32'h100 >> 2) + i], w);
               errors = errors + 1;
            end
         end
         // write a pattern from 0x2000 to block 5, then read it back to 0x3000
         for (i = 0; i < 128; i = i + 1) ram[(32'h2000 >> 2) + i] = 32'h01020304 * i + 32'h55aa0000;
         command(2, 32'h2000, 5, 0);
         for (i = 0; i < 512; i = i + 1)
            if (card.mem[5*512 + i] !== ram[(32'h2000 >> 2) + i / 4][8 * (i % 4) +: 8]) begin
               if (errors < 5) $display("FAIL write: byte %0d on the card is %h", i, card.mem[5*512 + i]);
               errors = errors + 1;
            end
         command(1, 32'h3000, 5, 0);
         for (i = 0; i < 128; i = i + 1)
            if (ram[(32'h3000 >> 2) + i] !== ram[(32'h2000 >> 2) + i]) begin
               if (errors < 5) $display("FAIL read back: word %0d", i);
               errors = errors + 1;
            end
         // a block the card does not have: an error, then a good read works again
         command(1, 32'h100, 1000, 3);
         command(1, 32'h100, 3, 0);
         $display("card: %0d reads, %0d writes", card.reads, card.writes);
      end else begin
         command(1, 32'h100, 3, 3);
         command(2, 32'h100, 3, 3);
      end
      $display("%0d errors (HC=%0d V2=%0d PRESENT=%0d)", errors, `HC, `V2, `PRESENT);
      $finish;
   end
endmodule
