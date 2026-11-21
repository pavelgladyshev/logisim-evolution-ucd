`timescale 1ns/1ps
// riscvTimer against a record of the Java Timer (TimerTrace.java: lines of hex, a step each): at each step the
// Timer gets the inputs that the Java Timer had, with a tick where that had a rising clock edge, and after it its
// Data Out, its interrupt request and its two registers must be the Java Timer's. At every FPGA clock the interrupt
// request must be the comparison of the two registers. It stops at the 10th difference, or at the end of the record.
//    iverilog -DTRACE='"default.trace"' -o tb tb_timer_replay.v riscvTimer.v && vvp -n tb
// With -DMTIMECMP_ADDR and -DMTIME_ADDR, the two addresses the record was made for. With -DIDLE=n, n FPGA clocks
// pass between the inputs and the tick. With -DGENERATED, the module is the one Logisim generates for a Timer
// (Timer.v from the FPGA flow, which has riscvTimer in it) instead of riscvTimer itself.
`ifndef MTIMECMP_ADDR
 `define MTIMECMP_ADDR 32'h02004000
`endif
`ifndef MTIME_ADDR
 `define MTIME_ADDR 32'h0200bff8
`endif
`ifndef IDLE
 `define IDLE 0
`endif
module tb_timer_replay;
   reg clock = 0;
   always #1 clock = ~clock;

   reg         tick = 0, reset = 0, re = 0, we = 0;
   reg  [31:0] address = 0, dataIn = 0;
   wire [31:0] dataOut;
   wire        irq;
`ifdef GENERATED
   Timer #(.mtimecmpAddr(`MTIMECMP_ADDR), .mtimeAddr(`MTIME_ADDR)) uut (
      .clock(clock), .tick(tick), .reset(reset), .address(address), .re(re), .we(we), .dataIn(dataIn),
      .dataOut(dataOut), .irq(irq));
 `define REGS uut.timer
`else
   riscvTimer #(.MTIMECMP_ADDR(`MTIMECMP_ADDR), .MTIME_ADDR(`MTIME_ADDR)) uut (
      .clock(clock), .tick(tick), .reset(reset), .address(address), .re(re), .we(we), .dataIn(dataIn),
      .dataOut(dataOut), .irq(irq));
 `define REGS uut
`endif

   integer f, r, step = 0, edges = 0, errors = 0;
   reg [3:0]  flags;
   reg [31:0] a, d, eOut;
   reg        eIrq;
   reg [63:0] eTime, eCmp;

   task difference;
      begin
         errors = errors + 1;
         if (errors == 10) begin $display("stopped at the 10th difference"); $finish; end
      end
   endtask

   // the interrupt request is the comparison of the registers, whatever happens
   always @(negedge clock)
      if (irq !== (`REGS.s_mtime >= `REGS.s_mtimecmp)) begin
         $display("step %0d: irq %b with mtime %h and mtimecmp %h", step, irq, `REGS.s_mtime, `REGS.s_mtimecmp);
         difference;
      end

   initial begin
      f = $fopen(`TRACE, "r");
      if (f == 0) begin $display("no record %s", `TRACE); $finish; end
      r = $fscanf(f, "%h %h %h %h %h %h %h\n", flags, a, d, eOut, eIrq, eTime, eCmp);
      while (r == 7) begin
         // the clock is low: the inputs of the step, then its tick if it has one
         reset = flags[1]; re = flags[2]; we = flags[3]; address = a; dataIn = d;
         if (flags[0]) begin
            repeat (`IDLE) @(negedge clock);
            tick = 1;
         end
         @(negedge clock);
         tick = 0;
         step = step + 1;
         edges = edges + flags[0];
         if (dataOut !== eOut || irq !== eIrq || `REGS.s_mtime !== eTime || `REGS.s_mtimecmp !== eCmp) begin
            $display("step %0d (flags %h address %h data %h): out %h irq %b mtime %h mtimecmp %h; Java: %h %b %h %h",
                     step, flags, a, d, dataOut, irq, `REGS.s_mtime, `REGS.s_mtimecmp, eOut, eIrq, eTime, eCmp);
            difference;
         end
         r = $fscanf(f, "%h %h %h %h %h %h %h\n", flags, a, d, eOut, eIrq, eTime, eCmp);
      end
      if (!$feof(f)) begin $display("the record's line %0d is not a step", step + 1); errors = errors + 1; end
      $display("%0d steps, %0d edges, %0d differences", step, edges, errors);
      $finish;
   end
endmodule
