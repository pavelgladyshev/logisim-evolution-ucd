// The Timer of Logisim's riscv library on an FPGA: the RISC-V machine timer, mtime and mtimecmp of 64 bits each,
// read and written as words of 32 bits, doing at each tick what the Java component (Timer, TimerRegisters) does
// at a rising edge of its clock, so that the two can be checked against each other edge for edge
// (support/logos/tests/timer). Shipped with Logisim and appended to the module Logisim generates for a Timer,
// which instantiates this one.
//
// The rules, which are the Java component's. Address selects one of four words, the first of these that it
// equals (the sums modulo 2^32): MTIMECMP_ADDR, the low word of mtimecmp; MTIME_ADDR, the low word of mtime;
// MTIMECMP_ADDR + 4, the high word of mtimecmp; MTIME_ADDR + 4, the high word of mtime. At a tick, with the
// inputs as they are then:
//   1. with reset 1, mtime becomes 0 and mtimecmp MTIMECMP_RESET, and nothing else happens: no write, no count;
//   2. otherwise, with we 1, the selected word takes dataIn, and the other words stay;
//   3. and then mtime, as the write left it, counts on by one, modulo 2^64: a write of w to its low word leaves
//      w + 1 there after the tick, and of 0xffffffff leaves 0 and carries into the high word.
// Between ticks reset 1 holds mtime at 0 and mtimecmp at MTIMECMP_RESET (from the next FPGA clock). irq is 1
// exactly while mtime >= mtimecmp, both unsigned: it is a register that every change of the two sets with them.
// dataOut is the selected word while re is 1 and 0 otherwise, with no clock.
//
// After the FPGA starts, the registers are as after reset. mtime is zero then, as the ACLINT specification
// requires of a reset. The specifications leave mtimecmp undefined after reset (the privileged specification says
// nothing of it, ACLINT says "unknown state"), so correct software writes both words of mtimecmp before it enables
// the timer interrupt. This Timer's own choice, MTIMECMP_RESET, is all ones: no interrupt is requested before
// software sets mtimecmp, and software that writes only its low word gets no interrupt.
module riscvTimer #(
   parameter [31:0] MTIMECMP_ADDR  = 32'h02004000,
   parameter [31:0] MTIME_ADDR     = 32'h0200bff8,
   parameter [63:0] MTIMECMP_RESET = 64'hffffffffffffffff
)(
   input             clock,
   input             tick,
   input             reset,
   input      [31:0] address,
   input             re,
   input             we,
   input      [31:0] dataIn,
   output     [31:0] dataOut,
   output            irq
);
   localparam [63:0] MTIME_RESET = 64'd0;

   reg [63:0] s_mtime    = MTIME_RESET;
   reg [63:0] s_mtimecmp = MTIMECMP_RESET;
   reg        s_irq      = (MTIME_RESET >= MTIMECMP_RESET);

   // the word that the address selects: the first of the four that it equals
   wire s_cmpLow   = (address == MTIMECMP_ADDR);
   wire s_timeLow  = ~s_cmpLow & (address == MTIME_ADDR);
   wire s_cmpHigh  = ~s_cmpLow & ~s_timeLow & (address == MTIMECMP_ADDR + 32'd4);
   wire s_timeHigh = ~s_cmpLow & ~s_timeLow & ~s_cmpHigh & (address == MTIME_ADDR + 32'd4);

   // what a tick without reset makes of the registers: the write, and then mtime counts on
   wire [63:0] s_written  = {(we & s_timeHigh) ? dataIn : s_mtime[63:32],
                             (we & s_timeLow)  ? dataIn : s_mtime[31:0]};
   wire [63:0] s_nextTime = s_written + 64'd1;
   wire [63:0] s_nextCmp  = {(we & s_cmpHigh) ? dataIn : s_mtimecmp[63:32],
                             (we & s_cmpLow)  ? dataIn : s_mtimecmp[31:0]};

   assign dataOut = ~re        ? 32'd0 :
                    s_cmpLow   ? s_mtimecmp[31:0] :
                    s_timeLow  ? s_mtime[31:0] :
                    s_cmpHigh  ? s_mtimecmp[63:32] :
                    s_timeHigh ? s_mtime[63:32] : 32'd0;
   assign irq     = s_irq;

   always @(posedge clock)
   begin
      if (reset)
      begin
         s_mtime    <= MTIME_RESET;
         s_mtimecmp <= MTIMECMP_RESET;
         s_irq      <= (MTIME_RESET >= MTIMECMP_RESET);
      end
      else if (tick)
      begin
         s_mtime    <= s_nextTime;
         s_mtimecmp <= s_nextCmp;
         s_irq      <= (s_nextTime >= s_nextCmp);
      end
   end
endmodule
