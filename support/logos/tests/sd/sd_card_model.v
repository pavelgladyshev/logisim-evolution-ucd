// A microSD card in SPI mode, for simulation: enough of the protocol to start it and to read and write single
// blocks, with the variations a controller has to cope with.
//   HIGH_CAPACITY  1: SDHC/SDXC (block numbers, CCS = 1); 0: SDSC (byte addresses)
//   V2             1: answers CMD8 (SD 2.0); 0: CMD8 is an illegal command (SD 1.x)
//   ACMD41_BUSY    how many ACMD41s it answers "still starting" (0x01) before 0x00
//   READ_WAIT      bytes of 0xff before a read's data token
//   WRITE_BUSY     bytes of 0x00 (busy) after a write's data response
//   PRESENT        0: no card, MISO stays high
// Mode 0: MOSI is read at SCK's rising edge, MISO changes at its falling edge.
module sd_card_model #(
   parameter HIGH_CAPACITY = 1,
   parameter V2            = 1,
   parameter ACMD41_BUSY   = 3,
   parameter READ_WAIT     = 5,
   parameter WRITE_BUSY    = 20,
   parameter PRESENT       = 1,
   parameter BLOCKS        = 16
)(
   input      sck,
   input      mosi,
   input      cs,
   output reg miso
);
   reg [7:0] mem [0:BLOCKS*512-1];
   integer i;
   initial begin
      for (i = 0; i < BLOCKS * 512; i = i + 1) mem[i] = (i * 7 + (i >> 9) * 13) & 8'hff;
      miso = 1'b1;
   end

   // bytes still to send
   reg [7:0] out [0:1023];
   integer outHead = 0, outTail = 0;
   task push(input [7:0] b); begin out[outTail] = b; outTail = (outTail + 1) % 1024; end endtask

   reg [7:0] rx = 0;
   integer bits = 0;
   reg [7:0] cur = 8'hff;          // the byte going out now
   reg [47:0] cmd;
   integer cmdBytes = 0;
   reg idle = 1, appCmd = 0;
   integer acmd41s = 0;
   // a write: waiting for the token, then the data
   integer writeBlock = -1, writeBytes = -1;
   integer errors = 0;
   integer reads = 0, writes = 0;

   integer block, k;
   task command(input [47:0] c);
      reg [5:0] index;
      reg [31:0] arg;
      begin
         index = c[45:40];
         arg = c[39:8];
         push(8'hff);                  // one byte before the answer
         case (index)
            0  : begin idle = 1; push(8'h01); end
            8  : if (V2) begin push(8'h01); push(8'h00); push(8'h00); push(8'h01); push(arg[7:0]); end
                 else push(8'h05);
            55 : begin appCmd = 1; push(idle ? 8'h01 : 8'h00); end
            41 : if (appCmd) begin
                    appCmd = 0;
                    if (HIGH_CAPACITY && !arg[30]) push(8'h01);   // an SDHC card waits for HCS
                    else if (acmd41s < ACMD41_BUSY) begin acmd41s = acmd41s + 1; push(8'h01); end
                    else begin idle = 0; push(8'h00); end
                 end else push(8'h05);
            58 : begin push(idle ? 8'h01 : 8'h00); push(HIGH_CAPACITY ? 8'hc0 : 8'h80); push(8'hff);
                       push(8'h80); push(8'h00); end
            16 : push((arg == 512) ? 8'h00 : 8'h40);
            17 : begin
                    block = HIGH_CAPACITY ? arg : arg / 512;
                    if (idle || block >= BLOCKS || (!HIGH_CAPACITY && arg % 512 != 0)) push(8'h40);
                    else begin
                       reads = reads + 1;
                       push(8'h00);
                       for (k = 0; k < READ_WAIT; k = k + 1) push(8'hff);
                       push(8'hfe);
                       for (k = 0; k < 512; k = k + 1) push(mem[block * 512 + k]);
                       push(8'h12); push(8'h34);  // CRC, not checked
                    end
                 end
            24 : begin
                    block = HIGH_CAPACITY ? arg : arg / 512;
                    if (idle || block >= BLOCKS || (!HIGH_CAPACITY && arg % 512 != 0)) push(8'h40);
                    else begin push(8'h00); writeBlock = block; writeBytes = -1; end
                 end
            default : push(8'h04);
         endcase
      end
   endtask

   task received(input [7:0] b);
      begin
         if (writeBlock >= 0) begin
            if (writeBytes < 0) begin
               if (b == 8'hfe) writeBytes = 0;          // the data token
            end else if (writeBytes < 512) begin
               mem[writeBlock * 512 + writeBytes] = b;
               writeBytes = writeBytes + 1;
            end else if (writeBytes < 513) writeBytes = writeBytes + 1;   // CRC 1
            else begin                                   // CRC 2: the data response, then busy
               writes = writes + 1;
               push(8'he5);                              // xxx0_0101: accepted
               for (k = 0; k < WRITE_BUSY; k = k + 1) push(8'h00);
               writeBlock = -1;
            end
         end else if (cmdBytes == 0) begin
            if (b[7:6] == 2'b01) begin cmd = {b, 40'd0}; cmdBytes = 1; end
         end else begin
            cmd[47 - 8 * cmdBytes -: 8] = b;
            cmdBytes = cmdBytes + 1;
            if (cmdBytes == 6) begin cmdBytes = 0; command(cmd); end
         end
      end
   endtask

   always @(posedge sck) if (!cs && PRESENT) begin
      rx = {rx[6:0], mosi};
      bits = bits + 1;
      if (bits == 8) begin
         bits = 0;
         received(rx);
         if (outHead != outTail) begin cur = out[outHead]; outHead = (outHead + 1) % 1024; end
         else cur = 8'hff;
      end
   end
   // the first bit of a byte goes out at the falling edge that ends the byte before (or when CS falls)
   always @(negedge sck) if (!cs && PRESENT) miso <= cur[7 - bits];
   always @(negedge cs) if (PRESENT) begin bits = 0; miso <= cur[7]; end
   always @(posedge cs) begin bits = 0; miso <= 1'b1; end
endmodule
