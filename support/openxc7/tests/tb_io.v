`timescale 1ns/1ps
// Unit test of the generated TTY (serial transmitter with a 1024-character block-RAM queue) and Keyboard
// (serial receiver with a queue of bufferLength characters), with 8 FPGA clocks per bit.
module tb_io;
   localparam CPB = 8;
   reg clock = 0;
   always #1 clock = ~clock;
   integer errors = 0;

   // ---------------- TTY ----------------
   reg        ttyTick = 0, ttyWe = 0, ttyClear = 0;
   reg  [6:0] ttyData = 0;
   wire       tx;
   TTY #(.clksPerBit(CPB)) tty (.clock(clock), .tick(ttyTick), .data(ttyData), .we(ttyWe), .clear(ttyClear),
                                .uartTx(tx));

   // UART decoder: received bytes in rx[]
   reg [7:0] rx [0:4095];
   integer   nrx = 0;
   integer   b;
   reg [7:0] byte_;
   initial forever begin
      @(negedge tx);
      repeat (CPB / 2) @(posedge clock);
      if (tx !== 1'b0) begin $display("TTY: bad start bit"); errors = errors + 1; end
      for (b = 0; b < 8; b = b + 1) begin
         repeat (CPB) @(posedge clock);
         byte_[b] = tx;
      end
      repeat (CPB) @(posedge clock);
      if (tx !== 1'b1) begin $display("TTY: bad stop bit"); errors = errors + 1; end
      rx[nrx] = byte_;
      nrx = nrx + 1;
   end

   // write one character at a tick (the write enable is sampled with the tick)
   task ttyWrite(input [6:0] c);
   begin
      @(negedge clock); ttyData = c; ttyWe = 1; ttyTick = 1;
      @(negedge clock); ttyWe = 0; ttyTick = 0;
   end
   endtask

   // expected output, built as characters are accepted into the queue
   reg [7:0] expect_ [0:8191];
   integer   nexp = 0;
   task put(input [7:0] b); begin expect_[nexp] = b; nexp = nexp + 1; end endtask
   // what a terminal gets for a character the TTY component shows (and nothing for the others)
   task expectChar(input [6:0] c);
   begin
      if (c == 7'h0a || c == 7'h0d) begin put(8'h0d); put(8'h0a); end
      else if (c == 7'h08) begin put(8'h08); put(8'h20); put(8'h08); end
      else if (c == 7'h0c) begin put(8'h1b); put(8'h5b); put(8'h32); put(8'h4a); put(8'h1b); put(8'h5b); put(8'h48); end
      else if (c >= 7'h20 && c != 7'h7f) put({1'b0, c});
   end
   endtask
   task ttyWriteExpect(input [6:0] c);
   begin
      @(negedge clock);
      if (!tty.s_queueFull) expectChar(c);
      ttyData = c; ttyWe = 1; ttyTick = 1;
      @(negedge clock); ttyWe = 0; ttyTick = 0;
   end
   endtask

   task ttyCheck(input [8*40-1:0] name);
      integer i, bad;
   begin
      // wait until the queue and the line are idle
      wait (tty.s_queueEmpty && tty.s_bitsLeft == 0 && tty.s_timer == 0);
      repeat (4 * CPB) @(posedge clock);
      bad = 0;
      if (nrx != nexp) bad = 1;
      for (i = 0; i < nexp && i < nrx; i = i + 1) if (rx[i] !== expect_[i]) bad = bad + 1;
      if (bad) begin
         $display("TTY %0s: FAIL, %0d bytes received, %0d expected, first bytes %h %h %h / %h %h %h", name,
                  nrx, nexp, rx[0], rx[1], rx[2], expect_[0], expect_[1], expect_[2]);
         errors = errors + 1;
      end else $display("TTY %0s: ok (%0d bytes)", name, nrx);
      nrx = 0; nexp = 0;
   end
   endtask

   // ---------------- Keyboard ----------------
   reg        kTick = 0, kRe = 0, kClear = 0, rxLine = 1;
   wire       avl;
   wire [6:0] kData;
   Keyboard #(.clksPerBit(CPB), .bufferLength(4)) kbd (.clock(clock), .tick(kTick), .readEnable(kRe), .clear(kClear),
                                                        .uartRx(rxLine), .available(avl), .data(kData));
   task send(input [7:0] c);
      integer i;
   begin
      rxLine = 0; repeat (CPB) @(posedge clock);
      for (i = 0; i < 8; i = i + 1) begin rxLine = c[i]; repeat (CPB) @(posedge clock); end
      rxLine = 1; repeat (2 * CPB) @(posedge clock);
   end
   endtask
   task pop(output [6:0] c, output a);
   begin
      @(negedge clock); c = kData; a = avl; kRe = 1; kTick = 1;
      @(negedge clock); kRe = 0; kTick = 0;
   end
   endtask
   task expectKeys(input [8*8-1:0] s, input integer n, input [8*40-1:0] name);
      integer i, bad;
      reg [6:0] c;
      reg a;
   begin
      bad = 0;
      for (i = n - 1; i >= 0; i = i - 1) begin
         pop(c, a);
         if (!a || c !== s[8*i +: 7]) bad = bad + 1;
      end
      @(negedge clock);
      if (avl !== 1'b0 || kData !== 7'd0) bad = bad + 1;
      if (bad) begin $display("Keyboard %0s: FAIL", name); errors = errors + 1; end
      else $display("Keyboard %0s: ok", name);
   end
   endtask

   integer i, gap, seed = 7;
   initial begin
      repeat (10) @(posedge clock);
      // TTY: a short text with a newline (sent as CR LF)
      ttyWriteExpect("H"); ttyWriteExpect("i"); ttyWriteExpect(7'h0a); ttyWriteExpect("X");
      ttyCheck("text");
      // TTY: control characters the TTY component ignores are not sent; CR, backspace and form feed are
      ttyWriteExpect(7'h00); ttyWriteExpect(7'h01); ttyWriteExpect(7'h1b); ttyWriteExpect(7'h7f);
      ttyWriteExpect("a"); ttyWriteExpect(7'h0d); ttyWriteExpect("b"); ttyWriteExpect(7'h08);
      ttyWriteExpect(7'h0c); ttyWriteExpect("c");
      ttyCheck("control characters");
      // TTY: a rising clear clears the screen; while clear is 1 nothing is written
      @(negedge clock); ttyClear = 1; expectChar(7'h0c);
      ttyWrite("x"); ttyWrite("y");
      @(negedge clock); ttyClear = 0;
      ttyWriteExpect("z");
      ttyCheck("clear");
      // TTY: random gaps, including a write just as the line becomes idle
      for (i = 0; i < 300; i = i + 1) begin
         gap = $random(seed) & 127;
         repeat (gap) @(posedge clock);
         ttyWriteExpect((i % 11 == 5) ? 7'h0a : 7'h21 + (i % 90));
      end
      ttyCheck("random gaps");
      // TTY: a burst far faster than the line: 1024 queued, the rest dropped until there is room
      for (i = 0; i < 1500; i = i + 1) ttyWriteExpect((i % 50 == 7) ? 7'h08 : 7'h41 + (i % 26));
      ttyCheck("burst");

      // Keyboard: 6 characters into a buffer of 4 (the last 2 are dropped)
      send("a"); send("b"); send("c"); send("d"); send("e"); send("f");
      expectKeys("abcd", 4, "buffer of 4");
      // Keyboard: Enter (CR) becomes '\n', Backspace (DEL) '\b', other control characters are dropped
      send(8'h0d); send(8'h01); send(8'h7f); send(8'h08); send(8'h1b); send("z");
      expectKeys({8'h0a, 8'h08, 8'h08, "z"}, 4, "control characters");
      // Keyboard: clear empties the queue
      send("q"); send("r");
      @(negedge clock); kClear = 1; @(negedge clock); kClear = 0;
      @(negedge clock);
      if (avl !== 1'b0) begin $display("Keyboard clear: FAIL"); errors = errors + 1; end
      else $display("Keyboard clear: ok");

      $display("%0d error(s)", errors);
      $finish;
   end
endmodule
