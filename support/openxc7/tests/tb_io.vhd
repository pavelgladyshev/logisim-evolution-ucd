-- Unit test of the generated TTY (serial transmitter with a 1024-character block-RAM queue) and Keyboard
-- (serial receiver with a queue of bufferLength characters) in VHDL, with 8 FPGA clocks per bit: the same cases
-- as tb_io.v.
LIBRARY ieee;
USE ieee.std_logic_1164.all;
USE ieee.numeric_std.all;
USE ieee.math_real.all;

ENTITY tb_io IS
END tb_io;

ARCHITECTURE sim OF tb_io IS
   CONSTANT CPB : integer := 8;
   TYPE bytes IS ARRAY (natural RANGE <>) OF std_logic_vector(7 DOWNTO 0);
   SIGNAL clock      : std_logic := '0';
   SIGNAL done       : boolean := false;
   SIGNAL ttyTick    : std_logic := '0';
   SIGNAL ttyWe      : std_logic := '0';
   SIGNAL ttyClear   : std_logic := '0';
   SIGNAL ttyData    : std_logic_vector(6 DOWNTO 0) := (OTHERS => '0');
   SIGNAL tx         : std_logic;
   SIGNAL kTick      : std_logic := '0';
   SIGNAL kRe        : std_logic := '0';
   SIGNAL kClear     : std_logic := '0';
   SIGNAL rxLine     : std_logic := '1';
   SIGNAL avl        : std_logic;
   SIGNAL kData      : std_logic_vector(6 DOWNTO 0);
   SIGNAL rx         : bytes(0 TO 8191);
   SIGNAL nrx        : natural := 0;
   SIGNAL lineErrors : natural := 0;
BEGIN
   clock <= NOT clock AFTER 1 ns WHEN NOT done ELSE clock;

   tty : ENTITY work.TTY
      GENERIC MAP (clksPerBit => CPB)
      PORT MAP (clock => clock, tick => ttyTick, data => ttyData, we => ttyWe, clear => ttyClear, uartTx => tx);

   kbd : ENTITY work.Keyboard
      GENERIC MAP (bufferLength => 4, clksPerBit => CPB)
      PORT MAP (clock => clock, tick => kTick, readEnable => kRe, clear => kClear, uartRx => rxLine,
                available => avl, data => kData);

   -- UART decoder: the bytes the TTY sends
   decoder : PROCESS
      VARIABLE b : std_logic_vector(7 DOWNTO 0);
   BEGIN
      WAIT UNTIL falling_edge(tx);
      FOR i IN 1 TO CPB / 2 LOOP WAIT UNTIL rising_edge(clock); END LOOP;
      IF tx /= '0' THEN lineErrors <= lineErrors + 1; END IF;
      FOR bit IN 0 TO 7 LOOP
         FOR i IN 1 TO CPB LOOP WAIT UNTIL rising_edge(clock); END LOOP;
         b(bit) := tx;
      END LOOP;
      FOR i IN 1 TO CPB LOOP WAIT UNTIL rising_edge(clock); END LOOP;
      IF tx /= '1' THEN lineErrors <= lineErrors + 1; END IF;
      rx(nrx) <= b;
      nrx <= nrx + 1;
   END PROCESS;

   main : PROCESS
      ALIAS queueFull  IS << SIGNAL .tb_io.tty.s_queueFull  : std_logic >>;
      ALIAS queueEmpty IS << SIGNAL .tb_io.tty.s_queueEmpty : std_logic >>;
      ALIAS bitsLeft   IS << SIGNAL .tb_io.tty.s_bitsLeft   : std_logic_vector(3 DOWNTO 0) >>;
      ALIAS timer      IS << SIGNAL .tb_io.tty.s_timer      : std_logic_vector(15 DOWNTO 0) >>;
      VARIABLE expect : bytes(0 TO 8191);
      VARIABLE nexp, rxBase, errors : natural := 0;
      VARIABLE seed1, seed2 : positive := 7;
      VARIABLE r : real;

      PROCEDURE put(b : natural) IS
      BEGIN
         expect(nexp) := std_logic_vector(to_unsigned(b, 8));
         nexp := nexp + 1;
      END PROCEDURE;

      -- what a terminal gets for a character the TTY component shows (and nothing for the others)
      PROCEDURE expectChar(c : natural) IS
      BEGIN
         IF c = 10 OR c = 13 THEN put(13); put(10);
         ELSIF c = 8 THEN put(8); put(32); put(8);
         ELSIF c = 12 THEN put(27); put(91); put(50); put(74); put(27); put(91); put(72);
         ELSIF c >= 32 AND c /= 127 THEN put(c);
         END IF;
      END PROCEDURE;

      -- write one character at a tick (the write enable is sampled with the tick)
      PROCEDURE ttyWrite(c : natural) IS
      BEGIN
         WAIT UNTIL falling_edge(clock);
         ttyData <= std_logic_vector(to_unsigned(c, 7)); ttyWe <= '1'; ttyTick <= '1';
         WAIT UNTIL falling_edge(clock);
         ttyWe <= '0'; ttyTick <= '0';
      END PROCEDURE;

      PROCEDURE ttyWriteExpect(c : natural) IS
      BEGIN
         WAIT UNTIL falling_edge(clock);
         IF queueFull /= '1' THEN expectChar(c); END IF;
         ttyData <= std_logic_vector(to_unsigned(c, 7)); ttyWe <= '1'; ttyTick <= '1';
         WAIT UNTIL falling_edge(clock);
         ttyWe <= '0'; ttyTick <= '0';
      END PROCEDURE;

      PROCEDURE ttyCheck(name : string) IS
         VARIABLE bad, got : natural := 0;
      BEGIN
         -- wait until the queue and the line are idle
         IF NOT (queueEmpty = '1' AND unsigned(bitsLeft) = 0 AND unsigned(timer) = 0) THEN
            WAIT UNTIL queueEmpty = '1' AND unsigned(bitsLeft) = 0 AND unsigned(timer) = 0;
         END IF;
         FOR i IN 1 TO 4 * CPB LOOP WAIT UNTIL rising_edge(clock); END LOOP;
         got := nrx - rxBase;
         IF got /= nexp THEN bad := 1; END IF;
         FOR i IN 0 TO minimum(got, nexp) - 1 LOOP
            IF rx(rxBase + i) /= expect(i) THEN bad := bad + 1; END IF;
         END LOOP;
         IF bad = 0 THEN
            REPORT "TTY " & name & ": ok (" & integer'image(got) & " bytes)";
         ELSE
            REPORT "TTY " & name & ": FAIL, " & integer'image(got) & " bytes received, " & integer'image(nexp)
                   & " expected" SEVERITY error;
            errors := errors + 1;
         END IF;
         rxBase := nrx;
         nexp := 0;
      END PROCEDURE;

      PROCEDURE send(c : natural) IS
         CONSTANT v : std_logic_vector(7 DOWNTO 0) := std_logic_vector(to_unsigned(c, 8));
      BEGIN
         rxLine <= '0';
         FOR i IN 1 TO CPB LOOP WAIT UNTIL rising_edge(clock); END LOOP;
         FOR bit IN 0 TO 7 LOOP
            rxLine <= v(bit);
            FOR i IN 1 TO CPB LOOP WAIT UNTIL rising_edge(clock); END LOOP;
         END LOOP;
         rxLine <= '1';
         FOR i IN 1 TO 2 * CPB LOOP WAIT UNTIL rising_edge(clock); END LOOP;
      END PROCEDURE;

      -- take the keys one by one (read enable at a tick), then the queue must be empty
      PROCEDURE expectKeys(keys : bytes; name : string) IS
         VARIABLE bad : natural := 0;
      BEGIN
         FOR i IN keys'RANGE LOOP
            WAIT UNTIL falling_edge(clock);
            IF avl /= '1' OR kData /= keys(i)(6 DOWNTO 0) THEN bad := bad + 1; END IF;
            kRe <= '1'; kTick <= '1';
            WAIT UNTIL falling_edge(clock);
            kRe <= '0'; kTick <= '0';
         END LOOP;
         WAIT UNTIL falling_edge(clock);
         IF avl /= '0' OR unsigned(kData) /= 0 THEN bad := bad + 1; END IF;
         IF bad = 0 THEN
            REPORT "Keyboard " & name & ": ok";
         ELSE
            REPORT "Keyboard " & name & ": FAIL" SEVERITY error;
            errors := errors + 1;
         END IF;
      END PROCEDURE;

   BEGIN
      FOR i IN 1 TO 10 LOOP WAIT UNTIL rising_edge(clock); END LOOP;
      -- TTY: a short text with a newline (sent as CR LF)
      ttyWriteExpect(character'pos('H')); ttyWriteExpect(character'pos('i'));
      ttyWriteExpect(10); ttyWriteExpect(character'pos('X'));
      ttyCheck("text");
      -- TTY: control characters the TTY component ignores are not sent; CR, backspace and form feed are
      ttyWriteExpect(0); ttyWriteExpect(1); ttyWriteExpect(27); ttyWriteExpect(127);
      ttyWriteExpect(character'pos('a')); ttyWriteExpect(13); ttyWriteExpect(character'pos('b'));
      ttyWriteExpect(8); ttyWriteExpect(12); ttyWriteExpect(character'pos('c'));
      ttyCheck("control characters");
      -- TTY: a rising clear clears the screen; while clear is 1 nothing is written
      WAIT UNTIL falling_edge(clock); ttyClear <= '1'; expectChar(12);
      ttyWrite(character'pos('x')); ttyWrite(character'pos('y'));
      WAIT UNTIL falling_edge(clock); ttyClear <= '0';
      ttyWriteExpect(character'pos('z'));
      ttyCheck("clear");
      -- TTY: random gaps, including a write just as the line becomes idle
      FOR i IN 0 TO 299 LOOP
         uniform(seed1, seed2, r);
         FOR j IN 1 TO integer(floor(r * 128.0)) LOOP WAIT UNTIL rising_edge(clock); END LOOP;
         IF i MOD 11 = 5 THEN ttyWriteExpect(10); ELSE ttyWriteExpect(33 + (i MOD 90)); END IF;
      END LOOP;
      ttyCheck("random gaps");
      -- TTY: a burst far faster than the line: 1024 queued, the rest dropped until there is room
      FOR i IN 0 TO 1499 LOOP
         IF i MOD 50 = 7 THEN ttyWriteExpect(8); ELSE ttyWriteExpect(65 + (i MOD 26)); END IF;
      END LOOP;
      ttyCheck("burst");

      -- Keyboard: 6 characters into a buffer of 4 (the last 2 are dropped)
      send(97); send(98); send(99); send(100); send(101); send(102);
      expectKeys((x"61", x"62", x"63", x"64"), "buffer of 4");
      -- Keyboard: Enter (CR) becomes '\n', Backspace (DEL) '\b', other control characters are dropped
      send(13); send(1); send(127); send(8); send(27); send(122);
      expectKeys((x"0a", x"08", x"08", x"7a"), "control characters");
      -- Keyboard: clear empties the queue
      send(113); send(114);
      WAIT UNTIL falling_edge(clock); kClear <= '1';
      WAIT UNTIL falling_edge(clock); kClear <= '0';
      WAIT UNTIL falling_edge(clock);
      IF avl /= '0' THEN
         REPORT "Keyboard clear: FAIL" SEVERITY error;
         errors := errors + 1;
      ELSE
         REPORT "Keyboard clear: ok";
      END IF;

      REPORT integer'image(errors + lineErrors) & " error(s)";
      done <= true;
      WAIT;
   END PROCESS;
END sim;
