`timescale 1ns/1ps
// An SDR SDRAM of 2M words of 32 bits (4 banks of 2048 rows of 256 columns), as inside the GW2AR-18 of the Tang
// Nano 20K, for testing logisimSdram. It reads and writes as the SDRAM does with CAS latency 2 and bursts of one
// word (a read's word is out from T_AC after the next clock edge to T_OH after the one after), and counts as errors
// the commands that break its rules: the start-up (nothing for POWER_UP ns, then a precharge of all banks, at least
// 2 refreshes and the mode, CAS latency 2 and bursts of 1), the times between commands, in ns as for a 7 ns part,
// rows that are not open or are open already, and refreshes that come later than REFRESH ns after the last one.
module sdram_model #(
   parameter real T_RCD = 18.0, T_RP = 18.0, T_RC = 63.0, T_RAS = 42.0, T_RRD = 14.0, T_RFC = 63.0, T_WR = 14.0,
   parameter real T_AC = 6.0, T_OH = 2.5,
   parameter real POWER_UP = 200000.0,
   parameter real REFRESH = 15625.0
) (
   input         clk,
   input         cke,
   input         cs_n,
   input         ras_n,
   input         cas_n,
   input         we_n,
   input  [3:0]  dqm,
   input  [10:0] addr,
   input  [1:0]  ba,
   inout  [31:0] dq
);
   reg  [31:0] mem [0:2097151];             // {bank, row, column}
   reg         open [0:3];
   reg  [10:0] row [0:3];
   real        tAct [0:3], tPre [0:3], tWrite [0:3];
   real        tLastAct = -1.0e9, tRef = -1.0e9, tMode = -1.0e9, tClk = 0.0, period = 0.0, now, gap;
   real        maxRefreshGap = 0.0;
   integer     errors = 0, reads = 0, writes = 0, refreshes = 0, initRefreshes = 0, b;
   reg         prechargedAll = 0, moded = 0, started = 0, lateReported = 0;

   // read data
   reg         rdNext = 0;                  // a READ at the last edge
   reg  [31:0] rdWord;
   reg  [3:0]  rdMask;
   reg         outOn = 0;
   reg  [31:0] outWord = 0;
   reg  [3:0]  outMask = 4'hf;
   genvar i;
   generate for (i = 0; i < 4; i = i + 1) begin : lanes
      assign dq[8*i +: 8] = (outOn && !outMask[i]) ? outWord[8*i +: 8] : 8'bz;
   end endgenerate

   initial for (b = 0; b < 4; b = b + 1) begin
      open[b] = 0; row[b] = 0; tAct[b] = -1.0e9; tPre[b] = -1.0e9; tWrite[b] = -1.0e9;
   end

   task fail(input [8*48:1] what);
      begin
         errors = errors + 1;
         if (errors <= 10) $display("SDRAM %0t ns: %0s", $realtime, what);
      end
   endtask

   task closeBank(input integer bank, input real at);
      begin
         open[bank] = 0;
         tPre[bank] = at;
      end
   endtask

   wire [20:0] index = {ba, row[ba], addr[7:0]};

   always @(posedge clk) begin
      now = $realtime;
      if (tClk > 0.0) period = now - tClk;
      tClk = now;
      // a READ's word: out from T_AC after the edge after it, until T_OH after the next
      if (rdNext) begin
         outWord <= #(T_AC) rdWord;
         outMask <= #(T_AC) rdMask;
         outOn <= #(T_AC) 1'b1;
      end else if (outOn) outOn <= #(T_OH) 1'b0;
      rdNext <= 1'b0;
      if (started && !lateReported && now - tRef > REFRESH) begin
         fail("a refresh is late");
         lateReported = 1;
      end
      if (cke !== 1'b1) fail("CKE is not 1");
      if (^{cs_n, ras_n, cas_n, we_n} === 1'bx) fail("a command line is X or Z");
      else if (!cs_n && {ras_n, cas_n, we_n} != 3'b111) begin
         if (now < POWER_UP) fail("a command during the power-up");
         case ({ras_n, cas_n, we_n})
            3'b011 : begin                          // ACTIVE
               if (!started) fail("ACTIVE before the start-up");
               if (open[ba]) fail("ACTIVE of an open bank");
               if (now - tPre[ba] < T_RP) fail("tRP (precharge to active)");
               if (now - tAct[ba] < T_RC) fail("tRC (active to active)");
               if (now - tLastAct < T_RRD) fail("tRRD (active to active, other bank)");
               if (now - tRef < T_RFC) fail("tRFC (refresh to active)");
               if (now - tMode < 2.0 * period - 0.01) fail("tMRD (mode to active)");
               open[ba] = 1; row[ba] = addr; tAct[ba] = now; tLastAct = now;
            end
            3'b101, 3'b100 : begin                  // READ, WRITE
               if (!open[ba]) fail("READ or WRITE of a closed bank");
               else begin
                  if (now - tAct[ba] < T_RCD) fail("tRCD (active to read or write)");
                  if (^addr[7:0] === 1'bx) fail("a column is X or Z");
                  if (!we_n) begin
                     if (dqm != 4'hf && outOn) fail("a WRITE while the SDRAM drives DQ");
                     for (b = 0; b < 4; b = b + 1)
                        if (!dqm[b]) begin
                           if (^dq[8*b +: 8] === 1'bx) fail("written data is X or Z");
                           mem[index][8*b +: 8] = dq[8*b +: 8];
                        end
                     writes = writes + 1;
                     tWrite[ba] = now;
                     if (addr[10]) closeBank(ba, (now + T_WR > tAct[ba] + T_RAS) ? now + T_WR : tAct[ba] + T_RAS);
                  end else begin
                     rdNext <= 1'b1;
                     rdWord <= mem[index];
                     rdMask <= dqm;
                     reads = reads + 1;
                     if (addr[10]) closeBank(ba, (now + period > tAct[ba] + T_RAS) ? now + period : tAct[ba] + T_RAS);
                  end
               end
            end
            3'b010 : begin                          // PRECHARGE
               for (b = 0; b < 4; b = b + 1)
                  if ((addr[10] || ba == b) && open[b]) begin
                     if (now - tAct[b] < T_RAS) fail("tRAS (active to precharge)");
                     if (now - tWrite[b] < T_WR || now - tWrite[b] < 2.0 * period - 0.01) fail("tWR (write to precharge)");
                     closeBank(b, now);
                  end
               if (addr[10]) prechargedAll = 1;
            end
            3'b001 : begin                          // REFRESH
               for (b = 0; b < 4; b = b + 1)
                  if (open[b] || now - tPre[b] < T_RP) fail("a refresh while a bank is not idle");
               if (now - tRef < T_RFC) fail("tRFC (refresh to refresh)");
               if (started) begin
                  gap = now - tRef;
                  if (gap > maxRefreshGap) maxRefreshGap = gap;
               end
               tRef = now;
               refreshes = refreshes + 1;
               if (prechargedAll) initRefreshes = initRefreshes + 1;
            end
            3'b000 : begin                          // MODE
               for (b = 0; b < 4; b = b + 1)
                  if (open[b] || now - tPre[b] < T_RP) fail("the mode while a bank is not idle");
               if (now - tRef < T_RFC) fail("tRFC (refresh to mode)");
               if (addr != 11'b000_0010_0000 || ba != 2'b00) fail("a mode other than CAS latency 2, bursts of 1");
               if (!prechargedAll || initRefreshes < 2) fail("the mode before a precharge and 2 refreshes");
               tMode = now;
               moded = 1;
               started = 1;
            end
            default : fail("an unknown command");
         endcase
      end
   end
endmodule
