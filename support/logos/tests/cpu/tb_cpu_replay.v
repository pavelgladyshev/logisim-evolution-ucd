`timescale 1ns/1ps
// riscvCpu against a record of the Java CPU's clock edges (rv32imTrace, as lines of hex by trace2hex.py): before
// each tick the CPU gets the inputs that the Java CPU took at that edge, and after it its outputs must be the Java
// CPU's. It stops at the 10th difference, or at the end of the record.
//    iverilog -DTRACE='"boot.hex"' -o tb tb_cpu_replay.v riscvCpu.v && vvp -n tb
`ifndef PERIOD
 `define PERIOD 64
`endif
module tb_cpu_replay;
   reg clock = 0;
   always #1 clock = ~clock;
   reg [7:0] count = 0;
   wire tick = (count == `PERIOD - 1);
   always @(posedge clock) count <= tick ? 8'd0 : count + 8'd1;

   reg  [31:0] dataIn = 0;
   reg         timerIrq = 0, extIrq = 0, busReq = 0;
   wire [31:0] address, dataOut;
   wire        memRead, memWrite, busAck, late;
   wire [3:0]  be;
   riscvCpu #(.RESET_ADDR(32'h0), .TLB_ENTRIES(16), .START(8)) cpu (
      .clock(clock), .tick(tick), .reset(1'b0), .dataIn(dataIn), .timerIrq(timerIrq), .extIrq(extIrq),
      .busReq(busReq), .address(address), .dataOut(dataOut), .memRead(memRead), .memWrite(memWrite), .be(be),
      .busAck(busAck), .late(late));

   integer f, r, edge_ = 0, errors = 0;
   reg [31:0] din, eAddr, eData;
   reg [7:0]  fin, fout;
   task check;
      begin
         if (address !== eAddr || dataOut !== eData || memRead !== fout[0] || memWrite !== fout[1]
             || be !== fout[5:2] || busAck !== fout[6] || late) begin
            errors = errors + 1;
            $display("edge %0d: address %h data %h rd %b wr %b be %b ack %b late %b; Java: %h %h %b %b %b %b  (pc %h ir %h priv %0d)",
                     edge_, address, dataOut, memRead, memWrite, be, busAck, late,
                     eAddr, eData, fout[0], fout[1], fout[5:2], fout[6], cpu.pc, cpu.ir, cpu.priv);
            if (errors == 10) begin $display("stopped at the 10th difference"); $finish; end
         end
      end
   endtask

   initial begin
      f = $fopen(`TRACE, "r");
      // what the CPU gives before its first edge
      r = $fscanf(f, "%h %h %h %h %h\n", din, fin, eAddr, eData, fout);
      #4 check;
      r = $fscanf(f, "%h %h %h %h %h\n", din, fin, eAddr, eData, fout);
      while (r == 5) begin
         dataIn = din; timerIrq = fin[0]; extIrq = fin[1]; busReq = fin[2];
         @(posedge clock); while (!tick) @(posedge clock);       // the edge
         @(negedge clock);
         edge_ = edge_ + 1;
`ifdef DUMP_FROM
         if (edge_ >= `DUMP_FROM && edge_ < `DUMP_FROM + 8)
            $display("edge %0d: pc %h ir %h priv %0d mstatus %h sepc %h stvec %h scause %h satp %h mepc %h mcause %h | out %h, Java %h",
                     edge_, cpu.pc, cpu.ir, cpu.priv, cpu.mstatus, cpu.sepc, cpu.stvec, cpu.scause, cpu.satp,
                     cpu.mepc, cpu.mcause, address, eAddr);
`endif
         check;
         if (edge_ % 100000 == 0) $display("%0d edges", edge_);
         r = $fscanf(f, "%h %h %h %h %h\n", din, fin, eAddr, eData, fout);
      end
      $display("%0d edges, %0d differences", edge_, errors);
      $finish;
   end
endmodule
