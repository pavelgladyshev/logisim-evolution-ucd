// A model of Gowin's GW_JTAG for simulation: a JTAG TAP of 8 IR bits (IDCODE 0x11 with the GW2A-18C's 0x0000081b,
// BYPASS 0xff, the user instructions ER1 0x42 and ER2 0x43), with what the user's logic sees as measured on a Tang
// Nano 20K (see riscvDebugTransport): at the rising edges of tck_o, shift_dr_capture_dr_o is high at the edges of
// Shift-DR only, tdi_o has the bit of the edge before, update_dr_o is high at the edge of Update-DR, and
// test_logic_reset_o is low in Test-Logic-Reset (high otherwise). The pads
// are the test bench's `JTAG_PADS.tck, .tms, .tdi and .tdo (the TAP's TDO, which changes at falling edges).
`ifndef JTAG_PADS
 `define JTAG_PADS tb_debug
`endif
module GW_JTAG (
   input  tck_pad_i, tms_pad_i, tdi_pad_i, tdo_er1_i, tdo_er2_i,
   output tdo_pad_o, tck_o, tdi_o, test_logic_reset_o, run_test_idle_er1_o, run_test_idle_er2_o,
          shift_dr_capture_dr_o, pause_dr_o, update_dr_o, enable_er1_o, enable_er2_o
);
   localparam TLR = 4'd0, RTI = 4'd1, SEL_DR = 4'd2, CAP_DR = 4'd3, SH_DR = 4'd4, EX1_DR = 4'd5, PAU_DR = 4'd6,
              EX2_DR = 4'd7, UPD_DR = 4'd8, SEL_IR = 4'd9, CAP_IR = 4'd10, SH_IR = 4'd11, EX1_IR = 4'd12,
              PAU_IR = 4'd13, EX2_IR = 4'd14, UPD_IR = 4'd15;
   wire tck = `JTAG_PADS.tck, tms = `JTAG_PADS.tms, tdi = `JTAG_PADS.tdi;
   reg [3:0]  state = TLR;
   reg [7:0]  ir = 8'h11, irSr = 8'h00;
   reg [31:0] idSr = 32'd0;
   reg        bypass = 1'b0, tdiLate = 1'b0, tdo = 1'b0;
   always @(posedge tck) begin
      tdiLate <= tdi;
      case (state)
         CAP_IR: irSr <= 8'h01;
         SH_IR:  irSr <= {tdi, irSr[7:1]};
         CAP_DR: begin idSr <= 32'h0000081b; bypass <= 1'b0; end
         SH_DR:  begin idSr <= {tdi, idSr[31:1]}; bypass <= tdi; end
         UPD_IR: ir <= irSr;
         default: ;
      endcase
      if (state == TLR) ir <= 8'h11;
      case (state)
         TLR:    state <= tms ? TLR : RTI;
         RTI:    state <= tms ? SEL_DR : RTI;
         SEL_DR: state <= tms ? SEL_IR : CAP_DR;
         CAP_DR: state <= tms ? EX1_DR : SH_DR;
         SH_DR:  state <= tms ? EX1_DR : SH_DR;
         EX1_DR: state <= tms ? UPD_DR : PAU_DR;
         PAU_DR: state <= tms ? EX2_DR : PAU_DR;
         EX2_DR: state <= tms ? UPD_DR : SH_DR;
         UPD_DR: state <= tms ? SEL_DR : RTI;
         SEL_IR: state <= tms ? TLR : CAP_IR;
         CAP_IR: state <= tms ? EX1_IR : SH_IR;
         SH_IR:  state <= tms ? EX1_IR : SH_IR;
         EX1_IR: state <= tms ? UPD_IR : PAU_IR;
         PAU_IR: state <= tms ? EX2_IR : PAU_IR;
         EX2_IR: state <= tms ? UPD_IR : SH_IR;
         UPD_IR: state <= tms ? SEL_DR : RTI;
      endcase
   end
   always @(negedge tck)
      tdo <= (state == SH_IR) ? irSr[0]
           : (state != SH_DR) ? 1'b0
           : (ir == 8'h42) ? tdo_er1_i : (ir == 8'h43) ? tdo_er2_i : (ir == 8'h11) ? idSr[0] : bypass;
   assign tdo_pad_o = tdo;
   always @(*) `JTAG_PADS.tdo = tdo;
   assign tck_o = tck;
   assign tdi_o = tdiLate;
   assign test_logic_reset_o = state != TLR;
   assign run_test_idle_er1_o = state == RTI && ir == 8'h42;
   assign run_test_idle_er2_o = state == RTI && ir == 8'h43;
   assign shift_dr_capture_dr_o = state == SH_DR;
   assign pause_dr_o = state == PAU_DR;
   assign update_dr_o = state == UPD_DR;
   assign enable_er1_o = ir == 8'h42;
   assign enable_er2_o = ir == 8'h43;
endmodule
