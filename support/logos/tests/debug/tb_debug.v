`timescale 1ns/1ps
// riscvCpu with DEBUG, a RAM of 64 KB at 0 (prog.hex) and the GW_JTAG model, whose pins OpenOCD drives through
// remote_bitbang (rbb_vpi.vpi) on port 9824. The tick comes every 26 clocks, as on the Tang Nano 20K at 1024 kHz.
//    iverilog -g2012 -o tb tb_debug.v gw_jtag_model.v riscvCpu.v riscvDebug.v
//    vvp -n -M . -mrbb_vpi tb          (then openocd -f sim.cfg, and gdb)
module tb_debug;
   reg clock = 0;
   always #18.5 clock = ~clock;
   reg [7:0] count = 0;
   wire tick = count == 8'd25;
   always @(posedge clock) count <= tick ? 8'd0 : count + 8'd1;

   reg tck = 0, tms = 0, tdi = 0, tdo = 0;
   always @(posedge clock) $rbb_poll(9824, tck, tms, tdi, tdo);

   reg  [31:0] ram [0:16383];
   integer i;
   initial begin for (i = 0; i < 16384; i = i + 1) ram[i] = 32'd0; $readmemh("prog.hex", ram); end
   wire [31:0] address, dataOut;
   wire        memRead, memWrite, busAck, late;
   wire [3:0]  be;
   wire [31:0] dataIn = ram[address[15:2]];
   reg         tickD = 0;
   always @(posedge clock) begin
      tickD <= tick;
      if (tickD && memWrite) begin
         if (be[0]) ram[address[15:2]][7:0]   <= dataOut[7:0];
         if (be[1]) ram[address[15:2]][15:8]  <= dataOut[15:8];
         if (be[2]) ram[address[15:2]][23:16] <= dataOut[23:16];
         if (be[3]) ram[address[15:2]][31:24] <= dataOut[31:24];
      end
   end
   riscvCpu #(.RESET_ADDR(32'h0), .TLB_ENTRIES(16), .START(8), .DEBUG(1)) cpu (
      .clock(clock), .tick(tick), .reset(1'b0), .dataIn(dataIn), .timerIrq(1'b0), .extIrq(1'b0), .busReq(1'b0),
      .address(address), .dataOut(dataOut), .memRead(memRead), .memWrite(memWrite), .be(be), .busAck(busAck),
      .late(late));
   reg lateSaid = 0;
   always @(posedge clock) if (late && !lateSaid) begin $display("LATE: a tick came before a step was done"); lateSaid = 1; end
endmodule
