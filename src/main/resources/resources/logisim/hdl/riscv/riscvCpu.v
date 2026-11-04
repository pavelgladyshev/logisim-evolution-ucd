// The RV32IM CPU of Logisim's riscv library on an FPGA: a model of the Java CPU (rv32im, rv32imData), which does
// at each rising edge of its clock what the Java CPU does there, with a separate data bus, so that the two can be
// checked against each other edge for edge (rv32imTrace records the Java CPU's edges).
//
// The Java CPU, at a rising edge: takes the word on its data bus (the answer to the bus cycle it set up at the edge
// before), executes a step and sets up its next bus cycle, all at once. Here a step takes several FPGA clocks: START
// FPGA clocks after a tick (when every device has answered: the SDRAM takes 7 at most) the CPU takes the data bus,
// the interrupt requests and Bus Request, and a sequencer (u) does the step for the next tick, which then only puts
// the bus cycle that the step set up on the outputs. So the outputs change at the tick, as in Logisim, and a RAM
// can take them one FPGA clock later. Each of the sequencer's steps takes one clock; the longest step, a division
// (four bits of its quotient a clock) whose next fetch faults, takes 14: with START 8, a CPU cycle of 23 FPGA clocks
// at least, which the FPGA flow checks (with the Tang Nano 20K's 27 MHz, Logisim's ticks of 2.25 MHz and slower, such
// as 2048 kHz: 26 FPGA clocks a cycle).
//
// As the Java CPU: a TLB of TLB_ENTRIES entries searched in order, behind a one-entry cache of the last
// translation, and least recently used replacement (here as ranks, 0 the most recent, which order the entries as
// the Java CPU's time stamps do); a page table walk of two bus cycles a PTE; every CSR address from 0x000 to
// 0xBFF exists (those without a meaning keep what is written, in block RAM) and from 0xC00 reads 0; no
// instruction cache, no GDB.
module riscvCpu #(
   parameter [31:0] RESET_ADDR  = 32'h0,
   parameter integer TLB_ENTRIES = 16,
   parameter integer START       = 8
) (
   input  wire        clock,
   input  wire        tick,
   input  wire        reset,
   input  wire [31:0] dataIn,
   input  wire        timerIrq,
   input  wire        extIrq,
   input  wire        busReq,
   output reg  [31:0] address = RESET_ADDR,
   output reg  [31:0] dataOut = 32'd0,
   output reg         memRead = 1'b1,
   output reg         memWrite = 1'b0,
   output reg  [3:0]  be = 4'd0,
   output reg         busAck = 1'b0,
   output reg         late = 1'b0          // (for tests) a tick came before the CPU's step was done
);
   localparam [1:0] PRIV_U = 2'd0, PRIV_S = 2'd1, PRIV_M = 2'd3;
   localparam [1:0] A_FETCH = 2'd0, A_LOAD = 2'd1, A_STORE = 2'd2;
   localparam [2:0] P_IDLE = 3'd0, P_L1_ADDR = 3'd1, P_L1_LATCH = 3'd2, P_L0_ADDR = 3'd3, P_L0_LATCH = 3'd4;
   localparam [3:0] U_WAIT = 4'd0, U_EDGE = 4'd1, U_PTW = 4'd2, U_IRQ = 4'd3, U_EXEC = 4'd4, U_LS = 4'd5,
                    U_FETCH = 4'd6, U_MUL = 4'd7, U_MULW = 4'd8, U_DIV = 4'd9, U_DIVW = 4'd10, U_CSR = 4'd11,
                    U_DONE = 4'd12;
   localparam integer RW = (TLB_ENTRIES <= 2) ? 1 : (TLB_ENTRIES <= 4) ? 2 : (TLB_ENTRIES <= 8) ? 3 :
                           (TLB_ENTRIES <= 16) ? 4 : (TLB_ENTRIES <= 32) ? 5 : 6;

   // ------------------------------------------------------------------------------------------- the CPU's state
   reg  [31:0] pc = RESET_ADDR;
   reg  [31:0] ir = 32'h00000013;
   reg  [31:0] x [0:31];
   reg  [1:0]  priv = PRIV_M;
   reg         fetching = 1'b1, addressing = 1'b0, granted = 1'b0;
   // the bus cycle the CPU has set up (rv32imData's address, outputData, outputDataWidth, memRead, memWrite)
   reg  [31:0] bAddr = RESET_ADDR, bData = 32'd0;
   reg  [2:0]  bWidth = 3'd0;
   reg         bRead = 1'b1, bWrite = 1'b0;
   // CSRs with a meaning
   reg  [31:0] mstatus = 32'h00001800, medeleg = 0, mideleg = 0, mie = 0, mtvec = 0, mepc = 0, mcause = 0,
               mtval = 0, mip = 0, stvec = 0, sepc = 0, scause = 0, stval = 0, satp = 0;
   localparam [31:0] SSTATUS_MASK = 32'h000C0122, SIE_MASK = 32'h00000222, SIP_WRITE_MASK = 32'h00000002;
   // the other CSRs, which keep what is written
   reg  [31:0] csrMem [0:3071];
   reg  [31:0] csrMemOut = 0;
   // the page table walker
   reg  [2:0]  ptwState = P_IDLE;
   reg  [31:0] ptwVa = 0, pendingVa = 0;
   reg  [1:0]  ptwType = A_FETCH;
   // the TLB, and its last translation
   reg               tValid [0:TLB_ENTRIES-1];
   reg  [19:0]       tVpn   [0:TLB_ENTRIES-1];
   reg  [21:0]       tPpn   [0:TLB_ENTRIES-1];
   reg  [6:0]        tPerms [0:TLB_ENTRIES-1];   // R W X U G A D, bits 0 to 6
   reg               tMega  [0:TLB_ENTRIES-1];
   reg  [8:0]        tAsid  [0:TLB_ENTRIES-1];
   reg  [RW-1:0]     tRank  [0:TLB_ENTRIES-1];
   reg         lValid = 1'b0, lMega = 1'b0;
   reg  [19:0] lVpn = 0;
   reg  [8:0]  lAsid = 0;
   reg  [33:0] lPaBase = 0;
   reg  [6:0]  lPerms = 0;

   integer i;
   initial begin
      for (i = 0; i < 32; i = i + 1) x[i] = 32'd0;
      for (i = 0; i < 3072; i = i + 1) csrMem[i] = 32'd0;
      for (i = 0; i < TLB_ENTRIES; i = i + 1) begin
         tValid[i] = 1'b0; tVpn[i] = 0; tPpn[i] = 0; tPerms[i] = 0; tMega[i] = 1'b0; tAsid[i] = 0; tRank[i] = i;
      end
   end

   // ------------------------------------------------------------------------------- the sequencer and its inputs
   reg  [3:0]  u = U_WAIT;
   reg  [7:0]  phase = 8'd0;              // FPGA clocks since the tick
   reg  [31:0] dIn = 0;                   // what the CPU takes at the edge
   reg         tIrq = 1'b0, eIrq = 1'b0, bReq = 1'b0;
   reg  [31:0] lsVa = 0;                  // a load's or store's virtual address, for its translation
   reg  [65:0] product = 0;
   reg  [31:0] divQuo = 0, divRem = 0, divDiv = 0;
   reg  [5:0]  divCount = 0;
   reg         divNegQ = 1'b0, divNegR = 1'b0;

   // ------------------------------------------------------------------------------------------------ the decoding
   wire [6:0]  opcode = ir[6:0];
   wire [4:0]  rd = ir[11:7], rs1 = ir[19:15], rs2 = ir[24:20];
   wire [2:0]  f3 = ir[14:12];
   wire [6:0]  f7 = ir[31:25];
   wire [11:0] csrAddr = ir[31:20];
   wire [31:0] immI = {{20{ir[31]}}, ir[31:20]};
   wire [31:0] immS = {{20{ir[31]}}, ir[31:25], ir[11:7]};
   wire [31:0] immB = {{19{ir[31]}}, ir[31], ir[7], ir[30:25], ir[11:8], 1'b0};
   wire [31:0] immU = {ir[31:12], 12'b0};
   wire [31:0] immJ = {{11{ir[31]}}, ir[31], ir[19:12], ir[20], ir[30:21], 1'b0};
   wire [31:0] rs1v = x[rs1], rs2v = x[rs2];

   // the privilege of loads and stores: mstatus.MPP when MPRV in M-mode
   wire [1:0]  lsPriv = (priv == PRIV_M && mstatus[17]) ? mstatus[12:11] : priv;
   wire        fetchTranslated = priv != PRIV_M && satp[31];
   wire        lsTranslated = lsPriv != PRIV_M && satp[31];
   wire [8:0]  asid = satp[30:22];

   // PermissionCheck.check
   function permOk(input [6:0] perms, input [1:0] access, input [1:0] mode, input sum, input mxr);
      begin
         if (!perms[5]) permOk = 1'b0;                                   // A
         else if (access == A_STORE && !perms[6]) permOk = 1'b0;         // D
         else if (mode == PRIV_U && !perms[3]) permOk = 1'b0;            // U
         else if (mode == PRIV_S && perms[3] && (access == A_FETCH || !sum)) permOk = 1'b0;
         else permOk = (access == A_FETCH) ? perms[2] : (access == A_LOAD) ? (perms[0] || (mxr && perms[2])) : perms[1];
      end
   endfunction

   // ---------------------------------------------------------------------------- the TLB's lookup of lookupVa
   reg  [31:0]   lookupVa;
   reg           lkFast, lkHit;
   reg  [RW-1:0] lkIdx;
   reg  [33:0]   lkPaBase;
   reg  [6:0]    lkPerms;
   reg           lkMega;
   wire [33:0]   lkPa = lkPaBase | (lkMega ? {12'b0, lookupVa[21:0]} : {22'b0, lookupVa[11:0]});
   always @(*) begin
      lookupVa = (u == U_LS) ? lsVa : pc;
      lkFast = lValid && lAsid == asid && (lMega ? lVpn[19:10] == lookupVa[31:22] : lVpn == lookupVa[31:12]);
      lkHit = 1'b0;
      lkIdx = 0;
      for (i = TLB_ENTRIES - 1; i >= 0; i = i - 1)
         if (tValid[i] && (tMega[i] ? tVpn[i][19:10] == lookupVa[31:22] : tVpn[i] == lookupVa[31:12])
             && (tPerms[i][4] || tAsid[i] == asid)) begin
            lkHit = 1'b1;
            lkIdx = i;                                                  // the first that matches
         end
      if (lkFast) begin
         lkPaBase = lPaBase; lkPerms = lPerms; lkMega = lMega; lkHit = 1'b1;
      end else begin
         lkPaBase = tMega[lkIdx] ? {tPpn[lkIdx][21:10], 22'b0} : {tPpn[lkIdx], 12'b0};
         lkPerms = tPerms[lkIdx]; lkMega = tMega[lkIdx];
      end
   end

   // where an insert goes: the first free entry, else the least recently used
   reg  [RW-1:0] victim;
   reg           anyFree;
   always @(*) begin
      anyFree = 1'b0;
      victim = 0;
      for (i = TLB_ENTRIES - 1; i >= 0; i = i - 1)
         if (tRank[i] == TLB_ENTRIES - 1) victim = i;
      for (i = TLB_ENTRIES - 1; i >= 0; i = i - 1)
         if (!tValid[i]) begin anyFree = 1'b1; victim = i; end
   end

   // a lookup's effects, when it does not hit the last translation: the entry is the most recent, and the last
   task tlbTouch(input [RW-1:0] e);
      integer j;
      begin
         for (j = 0; j < TLB_ENTRIES; j = j + 1)
            if (tRank[j] < tRank[e]) tRank[j] <= tRank[j] + 1'b1;
         tRank[e] <= 0;
      end
   endtask
   task tlbLookupEffects;
      begin
         if (!lkFast && lkHit) begin
            tlbTouch(lkIdx);
            lValid <= 1'b1; lVpn <= lookupVa[31:12]; lAsid <= asid; lPaBase <= lkPaBase; lPerms <= lkPerms;
            lMega <= lkMega;
         end
      end
   endtask

   // ---------------------------------------------------------------------------------- interrupts and traps
   // the interrupt to take (rv32imData.pendingInterrupt), with mip's MTIP and MEIP from the requests
   wire [31:0] mipNow = {mip[31:12], eIrq, mip[10:8], tIrq, mip[6:0]};
   wire [31:0] pendingBits = mipNow & mie;
   wire        mEnabled = priv != PRIV_M || mstatus[3];
   wire        sEnabled = priv == PRIV_U || (priv == PRIV_S && mstatus[1]);
   wire [31:0] mBits = mEnabled ? (pendingBits & ~mideleg) : 32'd0;
   wire [31:0] sBits = sEnabled ? (pendingBits & mideleg) : 32'd0;
   reg  [3:0]  irqCode;
   reg         irqTake;
   always @(*) begin
      irqTake = 1'b1;
      if (mBits[11]) irqCode = 4'd11; else if (mBits[3]) irqCode = 4'd3; else if (mBits[7]) irqCode = 4'd7;
      else if (mBits[9]) irqCode = 4'd9; else if (mBits[1]) irqCode = 4'd1; else if (mBits[5]) irqCode = 4'd5;
      else if (sBits[11]) irqCode = 4'd11; else if (sBits[3]) irqCode = 4'd3; else if (sBits[7]) irqCode = 4'd7;
      else if (sBits[9]) irqCode = 4'd9; else if (sBits[1]) irqCode = 4'd1; else if (sBits[5]) irqCode = 4'd5;
      else begin irqTake = 1'b0; irqCode = 4'd0; end
   end

   // TrapHandler.handle: to S-mode if delegated from S- or U-mode, else to M-mode
   task trap(input interrupt, input [3:0] code, input [31:0] tval);
      reg delegated;
      begin
         delegated = priv != PRIV_M && (interrupt ? mideleg[code] : medeleg[code]);
         if (delegated) begin
            sepc <= pc & ~32'd3;
            pc <= (interrupt && stvec[1:0] == 2'd1) ? {stvec[31:2], 2'b00} + {code, 2'b00} : stvec & ~32'd3;
            scause <= {interrupt, 27'd0, code};
            stval <= tval;
            mstatus[5] <= mstatus[1];        // SPIE
            mstatus[1] <= 1'b0;              // SIE
            mstatus[8] <= priv == PRIV_S;    // SPP
            priv <= PRIV_S;
         end else begin
            mepc <= pc & ~32'd3;
            pc <= (interrupt && mtvec[1:0] == 2'd1) ? {mtvec[31:2], 2'b00} + {code, 2'b00} : mtvec & ~32'd3;
            mcause <= {interrupt, 27'd0, code};
            mtval <= tval;
            mstatus[7] <= mstatus[3];        // MPIE
            mstatus[3] <= 1'b0;              // MIE
            mstatus[12:11] <= priv;          // MPP
            priv <= PRIV_M;
         end
      end
   endtask

   // ------------------------------------------------------------------------------------------- bus cycles
   task busRead(input [31:0] a);   // a PTE or a fetch
      begin
         bAddr <= a; bData <= 32'd0; bWidth <= 3'd0; bRead <= 1'b1; bWrite <= 1'b0;
      end
   endtask
   task ptwStart(input [31:0] va, input [1:0] access);
      begin
         ptwVa <= va; ptwType <= access; ptwState <= P_L1_ADDR; pendingVa <= va;
         busRead({satp[19:0], 12'b0} + {20'b0, va[31:22], 2'b00});
         fetching <= 1'b0; addressing <= 1'b0;
      end
   endtask
   // a valid and aligned load's or store's bus cycle
   wire [2:0] lsWidth = (f3[1:0] == 2'd0) ? 3'd1 : (f3[1:0] == 2'd1) ? 3'd2 : 3'd4;
   task lsBus(input [31:0] a);
      begin
         bAddr <= a; bWidth <= lsWidth; fetching <= 1'b0; addressing <= 1'b1;
         if (opcode == 7'h23) begin bData <= rs2v; bRead <= 1'b0; bWrite <= 1'b1; end
         else begin bData <= 32'd0; bRead <= 1'b1; bWrite <= 1'b0; end
      end
   endtask

   // a TLB insert by the page table walker
   task tlbInsert(input [21:0] ppn, input [6:0] perms, input mega);
      begin
         tValid[victim] <= 1'b1; tVpn[victim] <= ptwVa[31:12]; tPpn[victim] <= ppn; tPerms[victim] <= perms;
         tMega[victim] <= mega; tAsid[victim] <= asid;
         tlbTouch(victim);
         lValid <= 1'b0;
      end
   endtask

   // ----------------------------------------------------------------------------------- loads, stores, ALU
   wire        loadValid = f3 == 3'd0 || f3 == 3'd1 || f3 == 3'd2 || f3 == 3'd4 || f3 == 3'd5;
   wire        storeValid = f3 == 3'd0 || f3 == 3'd1 || f3 == 3'd2;
   wire [31:0] loadVa = rs1v + immI;
   wire [31:0] storeVa = rs1v + immS;
   wire [31:0] lsVaNow = (opcode == 7'h23) ? storeVa : loadVa;
   wire        misaligned = (lsWidth == 3'd2 && lsVaNow[0]) || (lsWidth == 3'd4 && lsVaNow[1:0] != 2'd0);
   wire [31:0] loadShifted = dIn >> {loadVa[1:0], 3'b000};
   reg  [31:0] loadValue;
   always @(*)
      case (f3)
         3'd0: loadValue = {{24{loadShifted[7]}}, loadShifted[7:0]};
         3'd1: loadValue = {{16{loadShifted[15]}}, loadShifted[15:0]};
         3'd4: loadValue = {24'd0, loadShifted[7:0]};
         3'd5: loadValue = {16'd0, loadShifted[15:0]};
         default: loadValue = dIn;
      endcase

   // integer instructions, as ArithmeticInstruction: aluIllegal for the funct7s it does not take
   reg  [31:0] aluValue;
   reg         aluIllegal;
   always @(*) begin
      aluIllegal = 1'b0;
      aluValue = 32'd0;
      if (opcode == 7'h13)
         case (f3)
            3'd0: aluValue = rs1v + immI;
            3'd1: aluValue = rs1v << rs2;
            3'd2: aluValue = ($signed(rs1v) < $signed(immI)) ? 32'd1 : 32'd0;
            3'd3: aluValue = (rs1v < immI) ? 32'd1 : 32'd0;
            3'd4: aluValue = rs1v ^ immI;
            3'd5: if (f7 == 7'h00) aluValue = rs1v >> rs2;
                  else if (f7 == 7'h20) aluValue = $signed(rs1v) >>> rs2;
                  else aluIllegal = 1'b1;
            3'd6: aluValue = rs1v | immI;
            default: aluValue = rs1v & immI;
         endcase
      else
         case (f3)
            3'd0: if (f7 == 7'h00) aluValue = rs1v + rs2v;
                  else if (f7 == 7'h20) aluValue = rs1v - rs2v;
                  else aluIllegal = 1'b1;
            3'd1: aluValue = rs1v << rs2v[4:0];
            3'd2: aluValue = ($signed(rs1v) < $signed(rs2v)) ? 32'd1 : 32'd0;
            3'd3: aluValue = (rs1v < rs2v) ? 32'd1 : 32'd0;
            3'd4: aluValue = rs1v ^ rs2v;
            3'd5: if (f7 == 7'h00) aluValue = rs1v >> rs2v[4:0];
                  else if (f7 == 7'h20) aluValue = $signed(rs1v) >>> rs2v[4:0];
                  else aluIllegal = 1'b1;
            3'd6: aluValue = rs1v | rs2v;
            default: aluValue = rs1v & rs2v;
         endcase
   end

   reg branchTaken;
   always @(*)
      case (f3)
         3'd0: branchTaken = rs1v == rs2v;
         3'd1: branchTaken = rs1v != rs2v;
         3'd4: branchTaken = $signed(rs1v) < $signed(rs2v);
         3'd5: branchTaken = $signed(rs1v) >= $signed(rs2v);
         3'd6: branchTaken = rs1v < rs2v;
         default: branchTaken = rs1v >= rs2v;
      endcase

   // four steps of the restoring division: four bits of the quotient
   reg [31:0] divQuoNext, divRemNext;
   reg [32:0] divShifted;
   reg [33:0] divDifference;
   integer k;
   always @(*) begin
      divQuoNext = divQuo;
      divRemNext = divRem;
      for (k = 0; k < 4; k = k + 1) begin
         divShifted = {divRemNext, divQuoNext[31]};
         divDifference = {1'b0, divShifted} - {2'b00, divDiv};
         if (!divDifference[33]) begin
            divRemNext = divDifference[31:0];
            divQuoNext = {divQuoNext[30:0], 1'b1};
         end else begin
            divRemNext = divShifted[31:0];
            divQuoNext = {divQuoNext[30:0], 1'b0};
         end
      end
   end

   // ----------------------------------------------------------------------------------------------- CSRs
   wire        csrWillWrite = (f3 == 3'd2 || f3 == 3'd3 || f3 == 3'd6 || f3 == 3'd7) ? rs1 != 5'd0 : 1'b1;
   wire        csrDenied = priv < csrAddr[9:8] || (csrWillWrite && csrAddr[11:10] == 2'b11);
   reg  [31:0] csrValue;
   reg         csrDedicated;
   always @(*) begin
      csrDedicated = 1'b1;
      case (csrAddr)
         12'h300: csrValue = mstatus;
         12'h302: csrValue = medeleg;
         12'h303: csrValue = mideleg;
         12'h304: csrValue = mie;
         12'h305: csrValue = mtvec;
         12'h341: csrValue = mepc;
         12'h342: csrValue = mcause;
         12'h343: csrValue = mtval;
         12'h344: csrValue = mip;
         12'h100: csrValue = mstatus & SSTATUS_MASK;
         12'h104: csrValue = mie & SIE_MASK;
         12'h105: csrValue = stvec;
         12'h141: csrValue = sepc;
         12'h142: csrValue = scause;
         12'h143: csrValue = stval;
         12'h144: csrValue = mip & SIE_MASK;
         12'h180: csrValue = satp;
         default: begin
            csrDedicated = csrAddr[11:10] == 2'b11;
            csrValue = csrDedicated ? 32'd0 : csrMemOut;
         end
      endcase
   end
   wire [31:0] csrOperand = f3[2] ? {27'd0, rs1} : rs1v;
   wire [31:0] csrNew = (f3[1:0] == 2'd1) ? csrOperand : (f3[1:0] == 2'd2) ? (csrValue | csrOperand)
                                                     : (csrValue & ~csrOperand);
   reg         csrMemWe;
   always @(posedge clock) begin
      csrMemOut <= csrMem[csrAddr];
      if (csrMemWe) csrMem[csrAddr] <= csrNew;
   end
   always @(*) csrMemWe = u == U_CSR && !csrDenied && f3 != 3'd4 && f3 != 3'd0 && csrWillWrite && !csrDedicated;

   // ------------------------------------------------------------------------------------------- the step
   task fetchDone;   // fetchNextInstruction's start, before its translation
      begin
         fetching <= 1'b1; addressing <= 1'b0;
      end
   endtask

   always @(posedge clock) begin
      if (phase != 8'hff) phase <= phase + 8'd1;
      case (u)
         U_WAIT : if (phase == START - 1) begin
               dIn <= dataIn; tIrq <= timerIrq; eIrq <= extIrq; bReq <= busReq;
               u <= U_EDGE;
               if (reset) begin
                  // the Reset pin, as rv32imData.reset
                  pc <= RESET_ADDR; priv <= PRIV_M; mstatus[3] <= 1'b0; mstatus[17] <= 1'b0; mcause <= 32'd0;
                  ptwState <= P_IDLE; lValid <= 1'b0;
                  for (i = 0; i < TLB_ENTRIES; i = i + 1) tValid[i] <= 1'b0;
                  u <= U_FETCH;
               end
            end
         U_EDGE : begin
               // rv32im.clockEdge: the bus granted at an edge with Bus Request, and released at the first without
               if (granted && !bReq) begin granted <= 1'b0; u <= U_DONE; end
               else if (!granted && bReq) begin granted <= 1'b1; u <= U_DONE; end
               else if (granted) u <= U_DONE;
               else u <= (ptwState != P_IDLE) ? U_PTW : U_IRQ;
            end
         U_PTW : case (ptwState)
               P_L1_ADDR : begin ptwState <= P_L1_LATCH; u <= U_DONE; end
               P_L0_ADDR : begin ptwState <= P_L0_LATCH; u <= U_DONE; end
               default : begin
                  // a PTE: dIn
                  if (!dIn[0] || (dIn[2] && !dIn[1])
                      || (ptwState == P_L0_LATCH && !dIn[1] && !dIn[3])
                      || ((dIn[1] || dIn[3]) && ptwState == P_L1_LATCH && dIn[19:10] != 10'd0)
                      || ((dIn[1] || dIn[3])
                          && !permOk(dIn[7:1], ptwType, ptwType == A_FETCH ? priv : lsPriv, mstatus[18], mstatus[19]))) begin
                     // a page fault, and the handler's fetch
                     ptwState <= P_IDLE;
                     pendingVa <= ptwVa;
                     trap(1'b0, ptwType == A_FETCH ? 4'd12 : ptwType == A_LOAD ? 4'd13 : 4'd15, ptwVa);
                     u <= U_FETCH;
                  end else if (dIn[1] || dIn[3]) begin
                     // a leaf: into the TLB; a fetch fetches, a load or store goes on as at an edge
                     tlbInsert(dIn[31:10], dIn[7:1], ptwState == P_L1_LATCH);
                     ptwState <= P_IDLE;
                     u <= (ptwType == A_FETCH) ? U_FETCH : U_IRQ;
                  end else begin
                     // to level 0
                     busRead({dIn[29:10], 12'b0} + {20'b0, ptwVa[21:12], 2'b00});
                     fetching <= 1'b0; addressing <= 1'b0;
                     ptwState <= P_L0_ADDR;
                     u <= U_DONE;
                  end
               end
            endcase
         U_IRQ : begin
               mip <= mipNow;
               if (!addressing && irqTake) begin
                  trap(1'b1, irqCode, 32'd0);
                  u <= U_FETCH;
               end else begin
                  if (fetching) ir <= dIn;
                  u <= U_EXEC;
               end
            end
         U_EXEC : begin
               u <= U_FETCH;
               case (opcode)
                  7'h13 : begin
                        if (aluIllegal) trap(1'b0, 4'd2, 32'd0);
                        else begin if (rd != 0) x[rd] <= aluValue; pc <= pc + 32'd4; end
                     end
                  7'h33 : begin
                        if (f7 == 7'h01) begin
                           if (f3[2]) begin
                              // division: by 0 and the overflow at once, as ArithmeticInstruction
                              if (rs2v == 32'd0) begin
                                 if (rd != 0) x[rd] <= f3[1] ? rs1v : 32'hffffffff;
                                 pc <= pc + 32'd4;
                              end else if (!f3[0] && rs1v == 32'h80000000 && rs2v == 32'hffffffff) begin
                                 if (rd != 0) x[rd] <= f3[1] ? 32'd0 : rs1v;
                                 pc <= pc + 32'd4;
                              end else begin
                                 divNegQ <= !f3[0] && (rs1v[31] ^ rs2v[31]);
                                 divNegR <= !f3[0] && rs1v[31];
                                 divQuo <= (!f3[0] && rs1v[31]) ? -rs1v : rs1v;
                                 divDiv <= (!f3[0] && rs2v[31]) ? -rs2v : rs2v;
                                 divRem <= 32'd0;
                                 divCount <= 6'd32;
                                 u <= U_DIV;
                              end
                           end else u <= U_MUL;
                        end else begin
                           if (aluIllegal) trap(1'b0, 4'd2, 32'd0);
                           else begin if (rd != 0) x[rd] <= aluValue; pc <= pc + 32'd4; end
                        end
                     end
                  7'h03, 7'h23 : begin
                        if (addressing) begin
                           // the data: a load takes it, a store is done
                           if (opcode == 7'h03 && rd != 0) x[rd] <= loadValue;
                           pc <= pc + 32'd4;
                           addressing <= 1'b0;
                        end else if (!(opcode == 7'h03 ? loadValid : storeValid)) begin
                           trap(1'b0, 4'd2, 32'd0);
                        end else if (misaligned) begin
                           pendingVa <= lsVaNow;
                           trap(1'b0, opcode == 7'h03 ? 4'd4 : 4'd6, lsVaNow);
                        end else if (lsTranslated) begin
                           lsVa <= lsVaNow;
                           u <= U_LS;
                        end else begin
                           lsBus(lsVaNow);
                           u <= U_DONE;
                        end
                     end
                  7'h63 : begin
                        if (f3 == 3'd2 || f3 == 3'd3) trap(1'b0, 4'd2, 32'd0);
                        else pc <= branchTaken ? pc + immB : pc + 32'd4;
                     end
                  7'h6f : begin if (rd != 0) x[rd] <= pc + 32'd4; pc <= pc + immJ; end
                  7'h67 : begin
                        if (f3 != 3'd0) trap(1'b0, 4'd2, 32'd0);
                        else begin if (rd != 0) x[rd] <= pc + 32'd4; pc <= (rs1v + immI) & ~32'd1; end
                     end
                  7'h37 : begin if (rd != 0) x[rd] <= immU; pc <= pc + 32'd4; end
                  7'h17 : begin if (rd != 0) x[rd] <= pc + immU; pc <= pc + 32'd4; end
                  7'h0f : begin
                        if (f3 == 3'd0 || f3 == 3'd1) pc <= pc + 32'd4;
                        else trap(1'b0, 4'd2, 32'd0);
                     end
                  7'h73 : begin
                        if (f3 != 3'd0) begin
                           if (csrDenied) trap(1'b0, 4'd2, 32'd0);
                           else u <= U_CSR;
                        end else if (ir == 32'h00000073) begin
                           trap(1'b0, priv == PRIV_U ? 4'd8 : priv == PRIV_S ? 4'd9 : 4'd11, 32'd0);
                        end else if (ir == 32'h00100073) begin
                           trap(1'b0, 4'd3, 32'd0);
                        end else if (ir == 32'h10200073 && priv != PRIV_U) begin
                           // sret
                           pc <= sepc;
                           mstatus[1] <= mstatus[5]; mstatus[5] <= 1'b1;
                           priv <= mstatus[8] ? PRIV_S : PRIV_U;
                           mstatus[8] <= 1'b0; mstatus[17] <= 1'b0;
                        end else if (ir == 32'h30200073 && priv == PRIV_M) begin
                           // mret
                           pc <= mepc;
                           mstatus[3] <= mstatus[7]; mstatus[7] <= 1'b1;
                           priv <= mstatus[12:11];
                           mstatus[12:11] <= PRIV_U;
                           if (mstatus[12:11] != PRIV_M) mstatus[17] <= 1'b0;
                        end else if (ir == 32'h10500073) begin
                           pc <= pc + 32'd4;                       // wfi
                        end else if ((ir & 32'hfe007fff) == 32'h12000073 && priv != PRIV_U) begin
                           // sfence.vma: all, by address, by ASID (rs2's 9 bits), or both
                           for (i = 0; i < TLB_ENTRIES; i = i + 1)
                              if ((rs1 == 5'd0 && rs2 == 5'd0)
                                  || (rs1 != 5'd0 && rs2 == 5'd0
                                      && (tMega[i] ? tVpn[i][19:10] == rs1v[31:22] : tVpn[i] == rs1v[31:12]))
                                  || (rs1 == 5'd0 && rs2 != 5'd0 && !tPerms[i][4] && tAsid[i] == rs2v[8:0])
                                  || (rs1 != 5'd0 && rs2 != 5'd0 && !tPerms[i][4] && tAsid[i] == rs2v[8:0]
                                      && (tMega[i] ? tVpn[i][19:10] == rs1v[31:22] : tVpn[i] == rs1v[31:12])))
                                 tValid[i] <= 1'b0;
                           lValid <= 1'b0;
                           pc <= pc + 32'd4;
                        end else trap(1'b0, 4'd2, 32'd0);
                     end
                  default : trap(1'b0, 4'd2, 32'd0);
               endcase
            end
         U_LS : begin
               // a load's or store's translation (lsVa)
               tlbLookupEffects;
               if (!lkHit) begin
                  ptwStart(lsVa, opcode == 7'h03 ? A_LOAD : A_STORE);
                  u <= U_DONE;
               end else if (!permOk(lkPerms, opcode == 7'h03 ? A_LOAD : A_STORE, lsPriv, mstatus[18], mstatus[19])) begin
                  pendingVa <= lsVa;
                  trap(1'b0, opcode == 7'h03 ? 4'd13 : 4'd15, lsVa);
                  u <= U_FETCH;
               end else begin
                  lsBus(lkPa[31:0]);
                  u <= U_DONE;
               end
            end
         U_MUL : begin
               product <= $signed({f3[1:0] == 2'd3 ? 1'b0 : rs1v[31], rs1v})
                          * $signed({f3[1:0] == 2'd1 ? rs2v[31] : 1'b0, rs2v});
               u <= U_MULW;
            end
         U_MULW : begin
               if (rd != 0) x[rd] <= (f3[1:0] == 2'd0) ? product[31:0] : product[63:32];
               pc <= pc + 32'd4;
               u <= U_FETCH;
            end
         U_DIV : begin
               // four bits of the quotient each clock, eight clocks
               divQuo <= divQuoNext;
               divRem <= divRemNext;
               divCount <= divCount - 6'd4;
               if (divCount == 6'd4) u <= U_DIVW;
            end
         U_DIVW : begin
               if (rd != 0) x[rd] <= f3[1] ? (divNegR ? -divRem : divRem) : (divNegQ ? -divQuo : divQuo);
               pc <= pc + 32'd4;
               u <= U_FETCH;
            end
         U_CSR : begin
               // csrMemOut has the CSR, if it is not one with a meaning
               if (f3 == 3'd4) trap(1'b0, 4'd2, 32'd0);
               else begin
                  if (rd != 0) x[rd] <= csrValue;
                  if (csrWillWrite)
                     case (csrAddr)
                        12'h300: mstatus <= csrNew;
                        12'h302: medeleg <= csrNew;
                        12'h303: mideleg <= csrNew;
                        12'h304: mie <= csrNew;
                        12'h305: mtvec <= csrNew;
                        12'h341: mepc <= csrNew & ~32'd3;
                        12'h342: mcause <= csrNew;
                        12'h343: mtval <= csrNew;
                        12'h344: mip <= csrNew;
                        12'h100: mstatus <= (mstatus & ~SSTATUS_MASK) | (csrNew & SSTATUS_MASK);
                        12'h104: mie <= (mie & ~SIE_MASK) | (csrNew & SIE_MASK);
                        12'h105: stvec <= csrNew;
                        12'h141: sepc <= csrNew & ~32'd3;
                        12'h142: scause <= csrNew;
                        12'h143: stval <= csrNew;
                        12'h144: mip <= (mip & ~SIP_WRITE_MASK) | (csrNew & SIP_WRITE_MASK);
                        12'h180: satp <= csrNew;
                        default: ;
                     endcase
                  pc <= pc + 32'd4;
               end
               u <= U_FETCH;
            end
         U_FETCH : begin
               // fetchNextInstruction
               fetchDone;
               if (!fetchTranslated) begin
                  busRead(pc);
                  u <= U_DONE;
               end else begin
                  tlbLookupEffects;
                  if (!lkHit) begin
                     ptwStart(pc, A_FETCH);
                     u <= U_DONE;
                  end else if (!permOk(lkPerms, A_FETCH, priv, mstatus[18], mstatus[19])) begin
                     pendingVa <= pc;
                     trap(1'b0, 4'd12, pc);
                     u <= U_FETCH;                         // the handler's fetch
                  end else begin
                     busRead(lkPa[31:0]);
                     u <= U_DONE;
                  end
               end
            end
         default : ;   // U_DONE: the step is done, for the tick
      endcase
      if (tick) begin
         // the tick: the bus cycle the step set up, on the outputs
         phase <= 8'd0;
         late <= late || (u != U_DONE);
         u <= U_WAIT;
         if (granted) begin
            address <= 32'd0; dataOut <= 32'd0; memRead <= 1'b0; memWrite <= 1'b0; be <= 4'd0; busAck <= 1'b1;
         end else begin
            address <= bAddr;
            memRead <= bRead;
            memWrite <= bWrite;
            busAck <= 1'b0;
            dataOut <= !bWrite ? 32'd0 : (bWidth == 3'd1) ? ({24'd0, bData[7:0]} << {bAddr[1:0], 3'b000})
                     : (bWidth == 3'd2) ? ({16'd0, bData[15:0]} << {bAddr[1:0], 3'b000}) : bData;
            be <= (!bRead && !bWrite) ? 4'b0000 : (bWidth == 3'd4) ? 4'b1111
                : (bWidth == 3'd1) ? (4'b0001 << bAddr[1:0])
                : (bWidth == 3'd2) ? (bAddr[1:0] == 2'd0 ? 4'b0011 : 4'b1100) : 4'b0000;
         end
      end
   end
endmodule
