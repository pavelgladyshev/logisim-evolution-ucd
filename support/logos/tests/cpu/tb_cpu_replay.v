`timescale 1ns/1ps
// riscvCpu against a record of the Java CPU's clock edges (rv32imTrace, as lines of hex by trace2hex.py): before
// each tick the CPU gets the inputs that the Java CPU took at that edge, and after it its outputs must be the Java
// CPU's. It stops at the 10th difference, or at the end of the record.
//    iverilog -DTRACE='"boot.hex"' -o tb tb_cpu_replay.v riscvCpu.v && vvp -n tb
// With -DBINARY, TRACE is the record itself, rv32imTrace's 16 bytes an edge.
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
   reg [127:0] record;
   // the next record: 5 when there is one, as $fscanf's count
   task next;
      begin
`ifdef BINARY
         r = $fread(record, f);
         if (r == 16) begin
            din   = {record[103:96], record[111:104], record[119:112], record[127:120]};
            fin   = record[95:88];
            eAddr = {record[63:56], record[71:64], record[79:72], record[87:80]};
            eData = {record[31:24], record[39:32], record[47:40], record[55:48]};
            fout  = record[23:16];
            r = 5;
         end else r = 0;
`else
         r = $fscanf(f, "%h %h %h %h %h\n", din, fin, eAddr, eData, fout);
`endif
      end
   endtask
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
`ifdef BINARY
      f = $fopen(`TRACE, "rb");
`else
      f = $fopen(`TRACE, "r");
`endif
      // what the CPU gives before its first edge
      next;
      #4 check;
      next;
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
         if (edge_ % 1000000 == 0) $display("%0d edges", edge_);
         next;
      end
      $display("%0d edges, %0d differences", edge_, errors);
      $finish;
   end
endmodule
