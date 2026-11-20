// Debugging riscvCpu on an FPGA, as the RISC-V External Debug Support (version 0.13.2) has it: a Debug Transport
// Module on the FPGA's own JTAG port, and a Debug Module that halts and resumes the CPU and reaches its registers
// and the memory through it. OpenOCD drives them; gdb connects to OpenOCD. Shipped with Logisim and appended to the
// module Logisim generates for the RV32IM CPU when its Debug Enable attribute is set.
//
// The JTAG port is Gowin's (GW_JTAG, the GW2A of the Tang Nano 20K): its user instructions ER1 (IR 0x42) and ER2
// (0x43) are dtmcs and dmi, for OpenOCD's "riscv set_ir dtmcs 0x42" and "riscv set_ir dmi 0x43".

// ---------------------------------------------------------------------------------- the Debug Transport Module
// dtmcs and dmi (7 address bits) on ER1 and ER2, and the DMI's requests to the clock's domain and back. As seen at
// the rising edges of GW_JTAG's tck_o: shift_dr_capture_dr_o is high at the edges of Shift-DR only, tdi_o has the
// bit of the edge before, update_dr_o is high at the edge of Update-DR, and test_logic_reset_o is low in
// Test-Logic-Reset (high otherwise). So a data register loads at the edges before Shift-DR, shifts at the edges of
// Shift-DR and once more at the edge after it (the last bit), and is taken at Update-DR.
module riscvDebugTransport (
   input  wire        clock,
   output reg         dmiReq = 1'b0,       // a request, for one clock
   output reg  [6:0]  dmiAddr = 7'd0,
   output reg  [31:0] dmiWdata = 32'd0,
   output reg  [1:0]  dmiOp = 2'd0,        // 1 read, 2 write
   input  wire [31:0] dmiRdata             // the answer, the clock after dmiReq
);
   wire tck, tdi, shifting, updating, er1, er2, notReset;
   reg  [31:0] csSr = 32'd0;
   reg  [40:0] dmSr = 41'd0;
   GW_JTAG jtag (.tck_pad_i(1'b0), .tms_pad_i(1'b0), .tdi_pad_i(1'b0), .tdo_er1_i(csSr[0]), .tdo_er2_i(dmSr[0]),
                 .tdo_pad_o(), .tck_o(tck), .tdi_o(tdi), .test_logic_reset_o(notReset), .run_test_idle_er1_o(),
                 .run_test_idle_er2_o(), .shift_dr_capture_dr_o(shifting), .pause_dr_o(),
                 .update_dr_o(updating), .enable_er1_o(er1), .enable_er2_o(er2));

   // the TCK domain
   reg        shifted = 1'b0, tail = 1'b0;
   reg        reqT = 1'b0;                  // toggles for each request to the clock's domain
   reg  [1:0] ackS = 2'b00;                 // the clock domain's answer toggle, here
   reg        sticky = 1'b0;                // a request came while one was in flight: op 3 until dmireset
   reg        busyAtCapture = 1'b0;
   reg  [6:0] rAddr = 7'd0;
   reg [31:0] rData = 32'd0;
   reg  [1:0] rOp = 2'd0;
   reg [31:0] respData = 32'd0;             // written by the clock's domain before it answers
   reg        ackT = 1'b0;
   wire       inFlight = reqT != ackS[1];
   wire [1:0] dmiStat = (sticky || inFlight) ? 2'd3 : 2'd0;

   always @(posedge tck) begin
      ackS <= {ackS[0], ackT};
      if (!notReset) begin
         shifted <= 1'b0; tail <= 1'b0; sticky <= 1'b0;
      end else if (shifting) begin
         if (er1) csSr <= {tdi, csSr[31:1]};
         if (er2) dmSr <= {tdi, dmSr[40:1]};
         shifted <= 1'b1;
      end else if (updating) begin
         if (shifted && er1 && (csSr[16] || csSr[17])) sticky <= 1'b0;    // dmireset, dmihardreset
         if (shifted && er2 && (dmSr[1:0] == 2'd1 || dmSr[1:0] == 2'd2)) begin
            if (busyAtCapture) sticky <= 1'b1;                            // ignored, as op 3 said
            else begin rAddr <= dmSr[40:34]; rData <= dmSr[33:2]; rOp <= dmSr[1:0]; reqT <= ~reqT; end
         end
         shifted <= 1'b0; tail <= 1'b0;
      end else if (shifted && !tail) begin
         if (er1) csSr <= {tdi, csSr[31:1]};
         if (er2) dmSr <= {tdi, dmSr[40:1]};
         tail <= 1'b1;
      end else if (!shifted) begin
         // Capture-DR (and the edges before it): version 1 (0.13), 7 address bits, idle 1
         csSr <= {14'd0, 1'b0, 1'b0, 1'b0, 3'd1, dmiStat, 6'd7, 4'd1};
         dmSr <= {rAddr, respData, dmiStat};
         busyAtCapture <= sticky || inFlight;
      end
   end

   // the clock's domain
   reg [2:0] reqS = 3'b000;
   reg       pending = 1'b0;
   always @(posedge clock) begin
      reqS <= {reqS[1:0], reqT};
      dmiReq <= 1'b0;
      if (reqS[2] != reqS[1]) begin
         dmiReq <= 1'b1; dmiAddr <= rAddr; dmiWdata <= rData; dmiOp <= rOp;
         pending <= 1'b1;
      end else if (pending && !dmiReq) begin
         respData <= dmiRdata;
         ackT <= reqS[2];
         pending <= 1'b0;
      end
   end
endmodule

// ------------------------------------------------------------------------------------------- the Debug Module
// One hart, no program buffer: abstract commands reach the registers (Access Register, 32 bits: the CPU's GPRs and
// CSRs), and the system bus access reaches the memory, of 8, 16 and 32 bits, through the halted CPU's bus. Both go
// to the CPU as requests that it does when halted.
module riscvDebugModule (
   input  wire        clock,
   input  wire        dmiReq,
   input  wire [6:0]  dmiAddr,
   input  wire [31:0] dmiWdata,
   input  wire [1:0]  dmiOp,
   output reg  [31:0] dmiRdata = 32'd0,
   // the CPU
   output reg         haltReq = 1'b0,
   output reg         resumeReq = 1'b0,    // until the CPU has resumed
   output reg         ndmreset = 1'b0,
   output reg         cpuReq = 1'b0,       // until cpuDone
   output reg         cpuMem = 1'b0,       // memory (else a register)
   output reg         cpuWrite = 1'b0,
   output reg  [15:0] cpuRegno = 16'd0,
   output reg  [31:0] cpuAddr = 32'd0,
   output reg  [1:0]  cpuSize = 2'd2,      // 0 a byte, 1 a halfword, 2 a word
   output reg  [31:0] cpuWdata = 32'd0,
   input  wire        cpuDone,
   input  wire [31:0] cpuRdata,
   input  wire        halted,
   input  wire        resumed,             // a pulse: the CPU has resumed
   input  wire        wasReset             // a pulse: the CPU was reset
);
   reg        dmactive = 1'b0, haveReset = 1'b1, resumeAck = 1'b0;
   reg [31:0] data0 = 32'd0;
   reg        absBusy = 1'b0, absPending = 1'b0, absWrite = 1'b0;
   reg [2:0]  cmderr = 3'd0;
   reg [15:0] absRegno = 16'd0;
   reg        sbBusy = 1'b0, sbPending = 1'b0, sbWriting = 1'b0, sbBusyError = 1'b0;
   reg        sbReadOnAddr = 1'b0, sbReadOnData = 1'b0, sbAutoInc = 1'b0;
   reg [2:0]  sbAccess = 3'd2, sbError = 3'd0;
   reg [31:0] sbAddr = 32'd0, sbData = 32'd0;
   reg        current = 1'b0;               // the request to the CPU is the system bus's (else the command's)

   wire [31:0] dmstatus = {9'd0, 1'b0, 2'b00, {2{haveReset}}, {2{resumeAck}}, 2'b00, {2{ndmreset}},
                           {2{!halted && !ndmreset}}, {2{halted}}, 1'b1, 1'b0, 1'b0, 1'b0, 4'd2};
   wire [31:0] dmcontrol = {haltReq, 29'd0, ndmreset, dmactive};
   wire [31:0] abstractcs = {3'd0, 5'd0, 11'd0, absBusy, 1'b0, cmderr, 4'd0, 4'd1};
   wire [31:0] sbcs = {3'd1, 6'd0, sbBusyError, sbBusy, sbReadOnAddr, sbAccess, sbAutoInc, sbReadOnData, sbError,
                       7'd32, 5'b00111};
   wire        sbOk = !sbBusyError && sbError == 3'd0;

   // a system bus access, if it can be done: its size and alignment, and a halted CPU
   task sbStart(input write);
      begin
         if (sbAccess > 3'd2) sbError <= 3'd4;
         else if ((sbAccess == 3'd1 && sbAddr[0]) || (sbAccess == 3'd2 && sbAddr[1:0] != 2'd0)) sbError <= 3'd3;
         else if (!halted) sbError <= 3'd7;
         else begin sbBusy <= 1'b1; sbPending <= 1'b1; sbWriting <= write; end
      end
   endtask

   always @(posedge clock) begin
      if (resumed) resumeReq <= 1'b0;
      if (resumed) resumeAck <= 1'b1;
      if (wasReset) haveReset <= 1'b1;

      // the CPU: one request at a time, the command's first
      if (!cpuReq) begin
         if (absPending) begin
            cpuReq <= 1'b1; cpuMem <= 1'b0; cpuWrite <= absWrite; cpuRegno <= absRegno; cpuWdata <= data0;
            current <= 1'b0; absPending <= 1'b0;
         end else if (sbPending) begin
            cpuReq <= 1'b1; cpuMem <= 1'b1; cpuWrite <= sbWriting; cpuAddr <= sbAddr; cpuSize <= sbAccess[1:0];
            cpuWdata <= sbData; current <= 1'b1; sbPending <= 1'b0;
         end
      end else if (cpuDone) begin
         cpuReq <= 1'b0;
         if (current) begin
            if (!sbWriting) sbData <= cpuRdata;
            if (sbAutoInc) sbAddr <= sbAddr + (32'd1 << sbAccess);
            sbBusy <= 1'b0;
         end else begin
            if (!absWrite) data0 <= cpuRdata;
            absBusy <= 1'b0;
         end
      end

      if (dmiReq) begin
         dmiRdata <= 32'd0;
         if (dmiOp == 2'd1)
            case (dmiAddr)
               7'h04: dmiRdata <= data0;
               7'h10: dmiRdata <= dmcontrol;
               7'h11: dmiRdata <= dmstatus;
               7'h16: dmiRdata <= abstractcs;
               7'h38: dmiRdata <= sbcs;
               7'h39: dmiRdata <= sbAddr;
               7'h3c: begin
                     dmiRdata <= sbData;
                     if (sbBusy) sbBusyError <= 1'b1;
                     else if (sbReadOnData && sbOk) sbStart(1'b0);
                  end
               7'h40: dmiRdata <= {31'd0, halted};
               default: ;
            endcase
         else if (dmiOp == 2'd2) begin
            if (dmiAddr == 7'h10) begin
               dmactive <= dmiWdata[0];
               if (!dmiWdata[0]) begin
                  // the Debug Module's reset
                  haltReq <= 1'b0; resumeReq <= 1'b0; ndmreset <= 1'b0; data0 <= 32'd0; cmderr <= 3'd0;
                  sbBusyError <= 1'b0; sbError <= 3'd0; sbReadOnAddr <= 1'b0; sbReadOnData <= 1'b0;
                  sbAutoInc <= 1'b0; sbAccess <= 3'd2;
               end else begin
                  haltReq <= dmiWdata[31];
                  ndmreset <= dmiWdata[1];
                  if (dmiWdata[28]) haveReset <= 1'b0;
                  if (dmiWdata[30] && !dmiWdata[31]) begin
                     resumeAck <= !halted;
                     resumeReq <= halted;
                  end
               end
            end else if (dmactive)
               case (dmiAddr)
                  7'h04: if (absBusy) begin if (cmderr == 3'd0) cmderr <= 3'd1; end else data0 <= dmiWdata;
                  7'h16: cmderr <= cmderr & ~dmiWdata[10:8];
                  7'h17: begin
                        if (absBusy) begin if (cmderr == 3'd0) cmderr <= 3'd1; end
                        else if (cmderr == 3'd0) begin
                           if (dmiWdata[31:24] != 8'd0 || dmiWdata[18]) cmderr <= 3'd2;       // only Access Register,
                           else if (!dmiWdata[17]) ;                                           // no postexec
                           else if (dmiWdata[22:20] != 3'd2) cmderr <= 3'd2;                   // 32 bits
                           else if (dmiWdata[15:0] >= 16'h1020) cmderr <= 3'd2;                // CSRs, GPRs
                           else if (!halted) cmderr <= 3'd4;
                           else begin
                              absBusy <= 1'b1; absPending <= 1'b1;
                              absWrite <= dmiWdata[16]; absRegno <= dmiWdata[15:0];
                           end
                        end
                     end
                  7'h38: begin
                        if (dmiWdata[22]) sbBusyError <= 1'b0;
                        sbError <= sbError & ~dmiWdata[14:12];
                        sbReadOnAddr <= dmiWdata[20];
                        sbAccess <= dmiWdata[19:17];
                        sbAutoInc <= dmiWdata[16];
                        sbReadOnData <= dmiWdata[15];
                     end
                  7'h39: begin
                        if (sbBusy) sbBusyError <= 1'b1;
                        else begin
                           sbAddr <= dmiWdata;
                           if (sbReadOnAddr && sbOk) begin sbAddr <= dmiWdata; sbStartAt(dmiWdata); end
                        end
                     end
                  7'h3c: begin
                        if (sbBusy) sbBusyError <= 1'b1;
                        else begin sbData <= dmiWdata; if (sbOk) sbStart(1'b1); end
                     end
                  default: ;
               endcase
         end
      end
   end

   // a read at sbaddress0's new value (sbreadonaddr): as sbStart, with that address
   task sbStartAt(input [31:0] a);
      begin
         if (sbAccess > 3'd2) sbError <= 3'd4;
         else if ((sbAccess == 3'd1 && a[0]) || (sbAccess == 3'd2 && a[1:0] != 2'd0)) sbError <= 3'd3;
         else if (!halted) sbError <= 3'd7;
         else begin sbBusy <= 1'b1; sbPending <= 1'b1; sbWriting <= 1'b0; end
      end
   endtask
endmodule
