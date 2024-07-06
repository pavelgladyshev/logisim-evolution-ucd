# Running tower of hanoi test in command line mode (without GUI)

(from macOS Terminal from build/libs folder):

     java -jar logisim-evolution-3.9.0dev-all.jar -b ../../circuits/towers_of_hanoi/towers_test.circ -t tty 2>/dev/null

With -t tty, ASCII display and keyboard in Logisim circuit are mapped to console.

In my tests, towers of hanoi run at 4.9 KHz effective clock speed.§

