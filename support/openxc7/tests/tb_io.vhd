-- Unit test of the generated TTY (serial transmitter with a 1024-character block-RAM queue) and Keyboard
-- (serial receiver with a queue of bufferLength characters) in VHDL, with 8 FPGA clocks per bit: the same cases
-- as tb_io.v. It only uses the ports of the two components: the line is idle when no byte has arrived for
-- longer than a frame, and the burst is checked against what was written, in order.
LIBRARY ieee;
USE ieee.std_logic_1164.all;
USE ieee.numeric_std.all;
USE ieee.math_real.all;

ENTITY tb_io IS
END tb_io;

ARCHITECTURE sim OF tb_io IS
   CONSTANT CPB   : integer := 8;      -- clocks per bit
   CONSTANT QUEUE : integer := 1024;   -- characters the TTY can hold, so that many are never dropped
   TYPE bytes IS ARRAY (natural RANGE <>) OF std_logic_vector(7 DOWNTO 0);
   TYPE chars IS ARRAY (natural RANGE <>) OF natural;
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
      VARIABLE written : chars(0 TO 4095);            -- the characters written to the TTY since the last check
      VARIABLE nwritten, rxBase, errors : natural := 0;
      VARIABLE seed1, seed2 : positive := 7;
      VARIABLE r : real;

      -- what a terminal gets for a character the TTY component shows (nothing for the others)
      PROCEDURE expandChar(c : natural; expansion : OUT bytes; n : OUT natural) IS
      BEGIN
         IF c = 10 OR c = 13 THEN
            expansion(0) := x"0d"; expansion(1) := x"0a"; n := 2;
         ELSIF c = 8 THEN
            expansion(0) := x"08"; expansion(1) := x"20"; expansion(2) := x"08"; n := 3;
         ELSIF c = 12 THEN
            expansion(0) := x"1b"; expansion(1) := x"5b"; expansion(2) := x"32"; expansion(3) := x"4a";
            expansion(4) := x"1b"; expansion(5) := x"5b"; expansion(6) := x"48"; n := 7;
         ELSIF c >= 32 AND c /= 127 THEN
            expansion(0) := std_logic_vector(to_unsigned(c, 8)); n := 1;
         ELSE
            n := 0;
         END IF;
      END PROCEDURE;

      -- write one character at a tick (the write enable is sampled with the tick)
      PROCEDURE ttyWrite(c : natural; record_it : boolean := true) IS
      BEGIN
         WAIT UNTIL falling_edge(clock);
         ttyData <= std_logic_vector(to_unsigned(c, 7)); ttyWe <= '1'; ttyTick <= '1';
         IF record_it THEN
            written(nwritten) := c;
            nwritten := nwritten + 1;
         END IF;
         WAIT UNTIL falling_edge(clock);
         ttyWe <= '0'; ttyTick <= '0';
      END PROCEDURE;

      -- the line is idle when no byte has arrived for longer than one frame
      PROCEDURE waitIdle IS
         VARIABLE last : natural;
      BEGIN
         LOOP
            last := nrx;
            FOR i IN 1 TO 12 * CPB LOOP WAIT UNTIL rising_edge(clock); END LOOP;
            EXIT WHEN nrx = last AND tx = '1';
         END LOOP;
      END PROCEDURE;

      -- every character written must have arrived, in order; "guaranteed" of them may not be dropped
      PROCEDURE ttyCheck(name : string; guaranteed : natural := integer'high) IS
         VARIABLE expansion : bytes(0 TO 6);
         VARIABLE n, pos, drops, bad : natural := 0;
         VARIABLE matched : boolean;
      BEGIN
         waitIdle;
         pos := rxBase;
         drops := 0;
         bad := 0;
         FOR i IN 0 TO nwritten - 1 LOOP
            expandChar(written(i), expansion, n);
            matched := pos + n <= nrx;
            IF matched THEN
               FOR j IN 0 TO n - 1 LOOP
                  IF rx(pos + j) /= expansion(j) THEN matched := false; END IF;
               END LOOP;
            END IF;
            IF matched THEN
               pos := pos + n;
            ELSE
               drops := drops + 1;
               IF i < guaranteed THEN bad := bad + 1; END IF;
            END IF;
         END LOOP;
         IF pos /= nrx THEN bad := bad + 1; END IF;      -- bytes arrived that were never written
         IF bad = 0 AND drops = 0 THEN
            REPORT "TTY " & name & ": ok (" & integer'image(nrx - rxBase) & " bytes)";
         ELSIF bad = 0 THEN
            REPORT "TTY " & name & ": ok (" & integer'image(nrx - rxBase) & " bytes, "
                   & integer'image(drops) & " characters dropped when the queue was full)";
         ELSE
            REPORT "TTY " & name & ": FAIL (" & integer'image(nrx - rxBase) & " bytes, "
                   & integer'image(bad) & " wrong)" SEVERITY error;
            errors := errors + 1;
         END IF;
         rxBase := nrx;
         nwritten := 0;
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
      ttyWrite(character'pos('H')); ttyWrite(character'pos('i'));
      ttyWrite(10); ttyWrite(character'pos('X'));
      ttyCheck("text");
      -- TTY: control characters the TTY component ignores are not sent; CR, backspace and form feed are
      ttyWrite(0); ttyWrite(1); ttyWrite(27); ttyWrite(127);
      ttyWrite(character'pos('a')); ttyWrite(13); ttyWrite(character'pos('b'));
      ttyWrite(8); ttyWrite(12); ttyWrite(character'pos('c'));
      ttyCheck("control characters");
      -- TTY: a rising clear clears the screen; while clear is 1 nothing is written
      WAIT UNTIL falling_edge(clock); ttyClear <= '1';
      written(nwritten) := 12; nwritten := nwritten + 1;          -- the clear itself clears the screen
      ttyWrite(character'pos('x'), false); ttyWrite(character'pos('y'), false);
      WAIT UNTIL falling_edge(clock); ttyClear <= '0';
      ttyWrite(character'pos('z'));
      ttyCheck("clear");
      -- TTY: random gaps, including a write just as the line becomes idle
      FOR i IN 0 TO 299 LOOP
         uniform(seed1, seed2, r);
         FOR j IN 1 TO integer(floor(r * 128.0)) LOOP WAIT UNTIL rising_edge(clock); END LOOP;
         IF i MOD 11 = 5 THEN ttyWrite(10); ELSE ttyWrite(33 + (i MOD 90)); END IF;
      END LOOP;
      ttyCheck("random gaps");
      -- TTY: a burst far faster than the line: the queue holds 1024, the rest are dropped until there is room
      FOR i IN 0 TO 1499 LOOP
         IF i MOD 50 = 7 THEN ttyWrite(8); ELSE ttyWrite(65 + (i MOD 26)); END IF;
      END LOOP;
      ttyCheck("burst", QUEUE);

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
