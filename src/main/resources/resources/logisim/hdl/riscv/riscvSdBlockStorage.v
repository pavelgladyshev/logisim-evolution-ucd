// The Block Storage of Logisim's riscv library on an FPGA: its registers and DMA as the Java component has them,
// and a microSD card in SPI mode where the component has a disk image file. Shipped with Logisim and appended to
// the module Logisim generates for a Block Storage, which instantiates this one.
//
// Registers (addr is the word index): 0 command (1 read a block, 2 write one), 1 memory address, 2 block number,
// 3 status (bit 0 busy, bit 1 error: 1 while a command runs, 3 after an error), 4 result (1 after an error). They
// read 0 while the DMA has or wants the bus, as in the component, and can be read while the card works.
//
// Bus side, acting only at a tick, as the component does at a rising clock edge. A read command reads the block from
// the card into a 512-byte buffer first, then requests the bus (busReq), and from the tick that sees busAck writes the
// block to memory, a 32-bit word a tick, little endian (memWrite, dmaAddr, dataOut, be = 1111). A write command
// requests the bus at once, reads the block from memory a word a tick (memRead; the word on memDataIn at the next
// tick), releases the bus and then writes the block to the card. The status shows busy until all is done.
//
// Where it differs from the component, because a card takes time where a file does not: the status is busy while
// the card works, with the CPU running; a read or write command clears an earlier error (in the component an error
// stays, but the next command ends before the CPU looks); and a command written while one runs is ignored.
//
// Card side, every global clock: SPI mode 0 (sdSck idles low, the card samples sdMosi at its rising edge), at most
// 400 kHz while the card starts, then fast. It starts the card after power-up and after reset, and again before a
// command if that failed (no card, for example): CMD0, CMD8 (an SD 2.0 card answers it), CMD55 + ACMD41 until the
// card is ready, then for an SD 2.0 card CMD58 for its capacity. A high-capacity card (SDHC, SDXC) takes block
// numbers; a standard one (SDSC, 2 GB and less) takes byte addresses and gets CMD16 for 512-byte blocks. A block is
// read with CMD17 and written with CMD24. A card that does not answer as expected ends the command with an error.
module riscvSdBlockStorage #(
   parameter SLOW_DIV = 34,    // global clocks per half SPI clock while the card starts: at most 400 kHz
   parameter FAST_DIV = 1,     // afterwards
   parameter POWER_UP = 54000  // global clocks after power-up before the card is spoken to (at least 1 ms)
)(
   input             clock,
   input             tick,
   input             reset,
   input      [2:0]  addr,
   input             re,
   input             we,
   input      [31:0] dataIn,
   output     [31:0] dataOut,
   output            busReq,
   input             busAck,
   output            memRead,
   output            memWrite,
   output     [31:0] dmaAddr,
   output     [3:0]  be,
   input      [31:0] memDataIn,
   output            sdSck,
   output            sdMosi,
   output            sdCs,
   input             sdMiso
);

   // ================================================================ bus side
   localparam D_IDLE = 3'd0, D_CARD_READ = 3'd1, D_REQ_READ = 3'd2, D_READING = 3'd3,
              D_REQ_WRITE = 3'd4, D_WRITING = 3'd5, D_CARD_WRITE = 3'd6;

   reg [2:0]  s_dma;
   reg [31:0] s_command;
   reg [31:0] s_memAddr;
   reg [31:0] s_blockAddr;
   reg        s_busy;
   reg        s_error;
   reg [31:0] s_result;
   reg [6:0]  s_word;              // the DMA's word in the block
   reg [31:0] s_dmaAddr;
   reg [31:0] s_cardBlock;         // the block number of the command, for the card side

   reg        s_cardDone;          // the card side's report: done (until the bus side takes it at a tick) ...
   reg        s_cardFailed;        // ... and whether it failed

   wire s_dmaActive = (s_dma == D_REQ_READ) || (s_dma == D_READING) || (s_dma == D_REQ_WRITE) || (s_dma == D_WRITING);
   wire s_transfer  = (s_dma == D_READING) || (s_dma == D_WRITING);

   reg [31:0] s_register;
   always @(*)
      case (addr)
         3'd0    : s_register = s_command;
         3'd1    : s_register = s_memAddr;
         3'd2    : s_register = s_blockAddr;
         3'd3    : s_register = {30'd0, s_error, s_busy};
         3'd4    : s_register = s_result;
         default : s_register = 32'd0;
      endcase

   // the block buffer: 128 words with one write port and one read port, a block RAM
   reg  [31:0] s_buffer [0:127];
   reg  [31:0] s_bufferWord;       // the word at s_bufferReadAddr, one global clock later
   wire [6:0]  s_bufferReadAddr;
   wire        s_bufferWrite;
   wire [6:0]  s_bufferWriteAddr;
   wire [31:0] s_bufferWriteData;

   assign dataOut  = (s_dma == D_READING) ? s_bufferWord : (re && !s_dmaActive) ? s_register : 32'd0;
   assign busReq   = s_dmaActive;
   assign memWrite = (s_dma == D_READING);
   assign memRead  = (s_dma == D_WRITING);
   assign dmaAddr  = s_transfer ? s_dmaAddr : 32'd0;
   assign be       = s_transfer ? 4'b1111 : 4'b0000;

   initial
   begin
      s_dma       = D_IDLE;
      s_command   = 0;
      s_memAddr   = 0;
      s_blockAddr = 0;
      s_busy      = 1'b0;
      s_error     = 1'b0;
      s_result    = 0;
      s_word      = 0;
      s_dmaAddr   = 0;
      s_cardBlock = 0;
   end

   always @(posedge clock)
      if (reset)
      begin
         s_dma       <= D_IDLE;
         s_command   <= 0;
         s_memAddr   <= 0;
         s_blockAddr <= 0;
         s_busy      <= 1'b0;
         s_error     <= 1'b0;
         s_result    <= 0;
         s_word      <= 0;
      end
      else if (tick)
      begin
         if (we && (addr == 3'd1)) s_memAddr <= dataIn;
         if (we && (addr == 3'd2)) s_blockAddr <= dataIn;
         if (we && (addr == 3'd0) && (s_dma == D_IDLE))
         begin
            s_command   <= dataIn;
            s_result    <= 0;
            s_word      <= 0;
            s_dmaAddr   <= s_memAddr;
            s_cardBlock <= s_blockAddr;
            if ((dataIn == 32'd1) || (dataIn == 32'd2))
            begin
               s_busy  <= 1'b1;
               s_error <= 1'b0;
               s_dma   <= (dataIn == 32'd1) ? D_CARD_READ : D_REQ_WRITE;
            end
            else
               s_busy <= 1'b0;
         end
         case (s_dma)
            D_CARD_READ  : if (s_cardDone)
                              if (s_cardFailed)
                              begin
                                 s_error  <= 1'b1;   // busy stays: the status reads 3
                                 s_result <= 32'd1;
                                 s_dma    <= D_IDLE;
                              end
                              else
                                 s_dma <= D_REQ_READ;
            D_REQ_READ   : if (busAck) s_dma <= D_READING;
            D_READING    : begin                     // memory takes the word at this tick
                              s_dmaAddr <= s_dmaAddr + 32'd4;
                              s_word    <= s_word + 7'd1;
                              if (s_word == 7'd127)
                              begin
                                 s_busy    <= 1'b0;
                                 s_command <= 0;
                                 s_dma     <= D_IDLE;
                              end
                           end
            D_REQ_WRITE  : if (busAck) s_dma <= D_WRITING;
            D_WRITING    : begin                     // the buffer takes memory's word at this tick
                              s_dmaAddr <= s_dmaAddr + 32'd4;
                              s_word    <= s_word + 7'd1;
                              if (s_word == 7'd127) s_dma <= D_CARD_WRITE;
                           end
            D_CARD_WRITE : if (s_cardDone)
                           begin
                              if (s_cardFailed)
                              begin
                                 s_error  <= 1'b1;
                                 s_result <= 32'd1;
                              end
                              else
                              begin
                                 s_busy    <= 1'b0;
                                 s_command <= 0;
                              end
                              s_dma <= D_IDLE;
                           end
            default      : ;
         endcase
      end

   // ================================================================ SPI, a byte at a time
   reg        s_slow;              // the start-up clock
   reg        s_spiStart;          // a byte goes out (s_spiTx) and comes in (s_spiRx) ...
   reg        s_spiBusy;
   reg        s_spiDone;           // ... and this is 1 for a global clock when it has
   reg  [7:0] s_spiTx;
   reg  [7:0] s_spiRx;
   reg  [7:0] s_spiCount;
   reg  [2:0] s_spiBits;
   reg  [7:0] s_spiShift;
   reg        s_sck;
   reg        s_mosi;
   reg        s_cs;

   assign sdSck  = s_sck;
   assign sdMosi = s_mosi;
   assign sdCs   = s_cs;

   wire [7:0] s_spiDiv = s_slow ? SLOW_DIV : FAST_DIV;

   initial
   begin
      s_spiBusy  = 1'b0;
      s_spiDone  = 1'b0;
      s_spiCount = 0;
      s_spiBits  = 0;
      s_spiShift = 8'hff;
      s_spiRx    = 8'hff;
      s_sck      = 1'b0;
      s_mosi     = 1'b1;
   end

   always @(posedge clock)
   begin
      s_spiDone <= 1'b0;
      if (s_spiStart && !s_spiBusy)
      begin
         s_spiBusy  <= 1'b1;
         s_spiShift <= s_spiTx;
         s_mosi     <= s_spiTx[7];
         s_spiBits  <= 3'd0;
         s_spiCount <= 8'd0;
      end
      else if (s_spiBusy)
      begin
         if (s_spiCount == s_spiDiv - 8'd1)
         begin
            s_spiCount <= 8'd0;
            if (!s_sck)
            begin                                    // rising edge: the card's bit is read
               s_sck   <= 1'b1;
               s_spiRx <= {s_spiRx[6:0], sdMiso};
            end
            else
            begin                                    // falling edge: the next bit goes out
               s_sck      <= 1'b0;
               s_spiShift <= {s_spiShift[6:0], 1'b1};
               s_mosi     <= s_spiShift[6];
               s_spiBits  <= s_spiBits + 3'd1;
               if (s_spiBits == 3'd7)
               begin
                  s_spiBusy <= 1'b0;
                  s_spiDone <= 1'b1;
                  s_mosi    <= 1'b1;
               end
            end
         end
         else
            s_spiCount <= s_spiCount + 8'd1;
      end
   end

   // ================================================================ the card
   localparam C_POWER_UP = 5'd0,   C_CLOCKS = 5'd1,     C_CMD0 = 5'd2,       C_CMD8 = 5'd3,       C_CMD8_R7 = 5'd4,
              C_CMD55 = 5'd5,      C_ACMD41 = 5'd6,     C_ACMD41_R1 = 5'd7,  C_CMD58 = 5'd8,      C_CMD58_OCR = 5'd9,
              C_CMD16 = 5'd10,     C_STARTED = 5'd11,   C_READY = 5'd12,     C_READ = 5'd13,      C_READ_TOKEN = 5'd14,
              C_READ_DATA = 5'd15, C_READ_CRC = 5'd16,  C_WRITE = 5'd17,     C_WRITE_TOKEN = 5'd18,
              C_WRITE_DATA = 5'd19, C_WRITE_CRC = 5'd20, C_WRITE_RESP = 5'd21, C_WRITE_BUSY = 5'd22, C_END = 5'd23,
              C_FAILED = 5'd24,    C_SEND = 5'd25,      C_RESPONSE = 5'd26,  C_BYTE = 5'd27;

   reg  [4:0]  s_card;             // the card side's step
   reg  [4:0]  s_next;             // the step after a byte (C_BYTE)
   reg  [4:0]  s_after;            // the step after a command (C_SEND), with the card's answer in s_r1
   reg  [47:0] s_frame;            // the command being sent, its first byte in bits 47:40
   reg  [2:0]  s_sent;             // its bytes sent
   reg  [7:0]  s_r1;
   reg  [19:0] s_count;            // global clocks, bytes or tries, depending on the step
   reg  [11:0] s_tries;
   reg         s_ready;            // the card has started
   reg         s_v2;               // it answered CMD8
   reg         s_blocks;           // it takes block numbers (SDHC, SDXC) rather than byte addresses
   reg  [23:0] s_assembly;         // the bytes of the word being read, little endian
   reg  [8:0]  s_byte;             // the byte in the block

   wire        s_wantRead  = (s_dma == D_CARD_READ) && !s_cardDone;
   wire        s_wantWrite = (s_dma == D_CARD_WRITE) && !s_cardDone;
   wire [31:0] s_cardAddr  = s_blocks ? s_cardBlock : {s_cardBlock[22:0], 9'd0};

   // the buffer: the DMA writes memory's words into it and the card side the words it reads; the DMA reads its word
   // and the card side the word of the byte it sends next
   assign s_bufferWrite     = (tick && (s_dma == D_WRITING)) ||
                              ((s_card == C_READ_DATA) && s_spiDone && (s_byte[1:0] == 2'd3));
   assign s_bufferWriteAddr = (s_dma == D_WRITING) ? s_word : s_byte[8:2];
   assign s_bufferWriteData = (s_dma == D_WRITING) ? memDataIn : {s_spiRx, s_assembly};
   assign s_bufferReadAddr  = (s_dma == D_READING) ? s_word : s_byte[8:2];

   always @(posedge clock)
   begin
      if (s_bufferWrite) s_buffer[s_bufferWriteAddr] <= s_bufferWriteData;
      s_bufferWord <= s_buffer[s_bufferReadAddr];
   end

   initial
   begin
      s_card       = C_POWER_UP;
      s_next       = C_POWER_UP;
      s_after      = C_POWER_UP;
      s_frame      = 0;
      s_sent       = 0;
      s_r1         = 8'hff;
      s_count      = 0;
      s_tries      = 0;
      s_ready      = 1'b0;
      s_v2         = 1'b0;
      s_blocks     = 1'b0;
      s_assembly   = 0;
      s_byte       = 0;
      s_slow       = 1'b1;
      s_cs         = 1'b1;
      s_spiStart   = 1'b0;
      s_spiTx      = 8'hff;
      s_cardDone   = 1'b0;
      s_cardFailed = 1'b0;
   end

   always @(posedge clock)
   begin
      s_spiStart <= 1'b0;
      if (tick && s_cardDone) s_cardDone <= 1'b0;   // the bus side takes the report at this tick
      if (reset)
      begin
         s_card     <= C_POWER_UP;
         s_count    <= 0;
         s_ready    <= 1'b0;
         s_slow     <= 1'b1;
         s_cs       <= 1'b1;
         s_cardDone <= 1'b0;
      end
      else
         case (s_card)
            C_POWER_UP    : if (s_count >= POWER_UP)
                            begin
                               s_count <= 0;
                               s_slow  <= 1'b1;
                               s_cs    <= 1'b1;
                               s_card  <= C_CLOCKS;
                            end
                            else
                               s_count <= s_count + 20'd1;
            C_CLOCKS      : if (s_count == 20'd10)    // 80 clocks with CS high
                            begin
                               s_cs    <= 1'b0;
                               s_frame <= {8'h40, 32'd0, 8'h95};
                               s_after <= C_CMD0;
                               s_card  <= C_SEND;
                            end
                            else
                            begin
                               s_count <= s_count + 20'd1;
                               s_spiTx <= 8'hff;
                               s_next  <= C_CLOCKS;
                               s_card  <= C_BYTE;
                            end
            C_CMD0        : if (s_r1 == 8'h01)
                            begin
                               s_frame <= {8'h48, 32'h000001aa, 8'h87};
                               s_after <= C_CMD8;
                               s_card  <= C_SEND;
                            end
                            else
                               s_card <= C_FAILED;
            C_CMD8        : begin
                               s_v2    <= !s_r1[2];          // an illegal command: an SD 1.x card
                               s_count <= 0;
                               s_tries <= 0;
                               s_card  <= s_r1[2] ? C_CMD55 : C_CMD8_R7;
                            end
            C_CMD8_R7     : if (s_count == 20'd4)
                               s_card <= C_CMD55;
                            else
                            begin
                               s_count <= s_count + 20'd1;
                               s_spiTx <= 8'hff;
                               s_next  <= C_CMD8_R7;
                               s_card  <= C_BYTE;
                            end
            C_CMD55       : begin
                               s_frame <= {8'h77, 32'd0, 8'h65};
                               s_after <= C_ACMD41;
                               s_card  <= C_SEND;
                            end
            C_ACMD41      : begin                     // CMD55's answer is not looked at
                               s_frame <= {8'h69, s_v2 ? 32'h40000000 : 32'd0, 8'h77};
                               s_after <= C_ACMD41_R1;
                               s_card  <= C_SEND;
                            end
            C_ACMD41_R1   : if (s_r1 == 8'h00)
                            begin                     // the card is ready
                               if (s_v2)
                               begin
                                  s_frame <= {8'h7a, 32'd0, 8'hfd};
                                  s_after <= C_CMD58;
                                  s_card  <= C_SEND;
                               end
                               else
                               begin
                                  s_blocks <= 1'b0;
                                  s_frame  <= {8'h50, 32'd512, 8'h15};
                                  s_after  <= C_CMD16;
                                  s_card   <= C_SEND;
                               end
                            end
                            else if ((s_r1 == 8'h01) && (s_tries != 12'hfff))
                            begin
                               s_tries <= s_tries + 12'd1;
                               s_card  <= C_CMD55;
                            end
                            else
                               s_card <= C_FAILED;
            C_CMD58       : if (s_r1[7:1] == 7'd0)
                            begin
                               s_count <= 0;
                               s_card  <= C_CMD58_OCR;
                            end
                            else
                               s_card <= C_FAILED;
            C_CMD58_OCR   : if (s_count == 20'd4)
                            begin
                               if (s_blocks)
                                  s_card <= C_STARTED;   // a high-capacity card: its blocks are 512 bytes
                               else
                               begin
                                  s_frame <= {8'h50, 32'd512, 8'h15};
                                  s_after <= C_CMD16;
                                  s_card  <= C_SEND;
                               end
                            end
                            else
                            begin
                               if (s_count == 20'd1) s_blocks <= s_spiRx[6];   // OCR bit 30: card capacity status
                               s_count <= s_count + 20'd1;
                               s_spiTx <= 8'hff;
                               s_next  <= C_CMD58_OCR;
                               s_card  <= C_BYTE;
                            end
            C_CMD16       : s_card <= (s_r1 == 8'h00) ? C_STARTED : C_FAILED;
            C_STARTED     : begin
                               s_cs    <= 1'b1;
                               s_slow  <= 1'b0;
                               s_ready <= 1'b1;
                               s_spiTx <= 8'hff;
                               s_next  <= C_READY;
                               s_card  <= C_BYTE;
                            end
            C_READY       : if (s_wantRead || s_wantWrite)
                            begin
                               if (!s_ready)
                               begin                  // start the card again first
                                  s_count <= POWER_UP;
                                  s_card  <= C_POWER_UP;
                               end
                               else
                               begin
                                  s_cs    <= 1'b0;
                                  s_frame <= {s_wantRead ? 8'h51 : 8'h58, s_cardAddr, 8'hff};
                                  s_after <= s_wantRead ? C_READ : C_WRITE;
                                  s_card  <= C_SEND;
                               end
                            end
            // ---------------------------------------------------- CMD17
            C_READ        : if (s_r1 == 8'h00)
                            begin
                               s_count <= 0;
                               s_spiTx <= 8'hff;
                               s_next  <= C_READ_TOKEN;
                               s_card  <= C_BYTE;
                            end
                            else
                               s_card <= C_FAILED;
            C_READ_TOKEN  : if (s_spiRx == 8'hfe)
                            begin
                               s_byte     <= 0;
                               s_spiTx    <= 8'hff;
                               s_spiStart <= 1'b1;
                               s_card     <= C_READ_DATA;
                            end
                            else if ((s_spiRx != 8'hff) || (s_count == 20'hfffff))
                               s_card <= C_FAILED;   // a data error token, or no token
                            else
                            begin
                               s_count <= s_count + 20'd1;
                               s_spiTx <= 8'hff;
                               s_next  <= C_READ_TOKEN;
                               s_card  <= C_BYTE;
                            end
            C_READ_DATA   : if (s_spiDone)            // the buffer takes a word after its fourth byte
                            begin
                               s_assembly <= {s_spiRx, s_assembly[23:8]};
                               if (s_byte == 9'd511)
                               begin
                                  s_count <= 0;
                                  s_card  <= C_READ_CRC;
                               end
                               else
                               begin
                                  s_byte     <= s_byte + 9'd1;
                                  s_spiTx    <= 8'hff;
                                  s_spiStart <= 1'b1;
                               end
                            end
            C_READ_CRC    : if (s_count == 20'd2)
                               s_card <= C_END;
                            else
                            begin
                               s_count <= s_count + 20'd1;
                               s_spiTx <= 8'hff;
                               s_next  <= C_READ_CRC;
                               s_card  <= C_BYTE;
                            end
            // ---------------------------------------------------- CMD24
            C_WRITE       : if (s_r1 == 8'h00)
                            begin
                               s_spiTx <= 8'hff;      // a byte before the data token
                               s_next  <= C_WRITE_TOKEN;
                               s_card  <= C_BYTE;
                            end
                            else
                               s_card <= C_FAILED;
            C_WRITE_TOKEN : begin
                               s_byte  <= 0;
                               s_count <= 0;
                               s_spiTx <= 8'hfe;
                               s_next  <= C_WRITE_DATA;
                               s_card  <= C_BYTE;
                            end
            C_WRITE_DATA  : begin                     // s_bufferWord holds the word of s_byte by now
                               case (s_byte[1:0])
                                  2'd0    : s_spiTx <= s_bufferWord[7:0];
                                  2'd1    : s_spiTx <= s_bufferWord[15:8];
                                  2'd2    : s_spiTx <= s_bufferWord[23:16];
                                  default : s_spiTx <= s_bufferWord[31:24];
                               endcase
                               if (s_byte != 9'd511) s_byte <= s_byte + 9'd1;
                               s_next <= (s_byte == 9'd511) ? C_WRITE_CRC : C_WRITE_DATA;
                               s_card <= C_BYTE;
                            end
            C_WRITE_CRC   : begin                     // two bytes of CRC, which the card does not check, then
                               s_count <= s_count + 20'd1;   // the data response
                               s_spiTx <= 8'hff;
                               s_next  <= (s_count == 20'd2) ? C_WRITE_RESP : C_WRITE_CRC;
                               s_card  <= C_BYTE;
                            end
            C_WRITE_RESP  : if ((s_spiRx & 8'h1f) == 8'h05)
                            begin                     // accepted: the card is busy while it reads 0
                               s_count <= 0;
                               s_spiTx <= 8'hff;
                               s_next  <= C_WRITE_BUSY;
                               s_card  <= C_BYTE;
                            end
                            else
                               s_card <= C_FAILED;
            C_WRITE_BUSY  : if (s_spiRx == 8'hff)
                               s_card <= C_END;
                            else if (s_count == 20'hfffff)
                               s_card <= C_FAILED;
                            else
                            begin
                               s_count <= s_count + 20'd1;
                               s_spiTx <= 8'hff;
                               s_next  <= C_WRITE_BUSY;
                               s_card  <= C_BYTE;
                            end
            // ---------------------------------------------------- the end of a command
            C_END         : begin                     // CS high and a byte for the card to finish
                               s_cs         <= 1'b1;
                               s_cardFailed <= 1'b0;
                               s_cardDone   <= 1'b1;
                               s_spiTx      <= 8'hff;
                               s_next       <= C_READY;
                               s_card       <= C_BYTE;
                            end
            C_FAILED      : begin
                               s_cs         <= 1'b1;
                               s_ready      <= 1'b0;
                               s_slow       <= 1'b1;
                               s_cardFailed <= 1'b1;
                               if (s_wantRead || s_wantWrite) s_cardDone <= 1'b1;
                               s_card       <= C_READY;
                            end
            // ---------------------------------------------------- a command: a byte of 1s, its six bytes, then bytes
            // until one with bit 7 at 0 (at most 8), in s_r1 (0xff if none)
            C_SEND        : begin
                               s_sent  <= 3'd0;
                               s_count <= 0;
                               s_spiTx <= 8'hff;
                               s_next  <= C_RESPONSE;
                               s_card  <= C_BYTE;
                            end
            C_RESPONSE    : if (s_sent != 3'd6)
                            begin
                               s_spiTx <= s_frame[47:40];
                               s_frame <= {s_frame[39:0], 8'hff};
                               s_sent  <= s_sent + 3'd1;
                               s_next  <= C_RESPONSE;
                               s_card  <= C_BYTE;
                            end
                            else if ((s_count != 0) && !s_spiRx[7])
                            begin
                               s_r1   <= s_spiRx;
                               s_card <= s_after;
                            end
                            else if (s_count == 20'd9)
                            begin
                               s_r1   <= 8'hff;
                               s_card <= s_after;
                            end
                            else
                            begin
                               s_count <= s_count + 20'd1;
                               s_spiTx <= 8'hff;
                               s_next  <= C_RESPONSE;
                               s_card  <= C_BYTE;
                            end
            // ---------------------------------------------------- a byte out and in, then step s_next
            C_BYTE        : if (s_spiDone)
                               s_card <= s_next;
                            else if (!s_spiBusy && !s_spiStart)
                               s_spiStart <= 1'b1;
            default       : s_card <= C_FAILED;
         endcase
   end

endmodule
