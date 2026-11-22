# Logisim-evolution on a Sipeed Tang Nano 20K or 9K (Gowin)

This version of Logisim-evolution also builds circuits for Gowin FPGAs and loads them onto a **Sipeed Tang Nano 20K**
(GW2AR-LV18QN88C8/I7) or a **Sipeed Tang Nano 9K** (GW1NR-LV9QN88PC6/I5), from the same **FPGA** menu as the Cmod A7
in [the openXC7 guide](../openxc7/README.md). The toolchain is open source:

- [Yosys](https://github.com/YosysHQ/yosys) (`synth_gowin`) for synthesis
- [nextpnr-himbaechel](https://github.com/YosysHQ/nextpnr) with its Gowin target for place and route
- [Project Apicula](https://github.com/YosysHQ/apicula)'s `gowin_pack` for the bitstream
- [openFPGALoader](https://github.com/trabucayre/openFPGALoader) to load it over the board's USB-C port

## 1. Installation

On macOS:

```bash
brew install yosys boost eigen openfpgaloader cmake python@3.13 git
support/gowin/setup_toolchain.sh
support/openxc7/install_logisim.sh
```

`setup_toolchain.sh` installs Apicula in a Python environment and builds nextpnr-himbaechel with the chip databases
of the Tang Nano 20K (GW2A-18C) and 9K (GW1N-9C), all in `~/gowin` (about 0.5 GB, a few minutes). Logisim finds
`~/gowin/bin` by itself. The same Logisim serves the Cmod A7 and the Tang Nano, so `install_logisim.sh` is the one
from the openXC7 setup. The prerequisites for Kubuntu/Ubuntu 24.04 are at the top of `setup_toolchain.sh`; so far
the script has been run on macOS only.

On Windows nothing is built: YosysHQ's [OSS CAD Suite](https://github.com/YosysHQ/oss-cad-suite-build) has every
program of the flow, and this downloads the release Logisim was tested with (about 600 MB, checked against the
SHA-256 that GitHub publishes for it) and unpacks it as `%USERPROFILE%\gowin` (2.1 GB), where Logisim finds it by
itself. It needs no administrator rights:

```powershell
powershell -ExecutionPolicy Bypass -File support\gowin\setup_windows.ps1
```

The board then needs one driver change, once, as the Cmod A7 does ([windows.md](../openxc7/windows.md)): in
[Zadig](https://zadig.akeo.ie), under **Options → List All Devices**, give **USB Serial Converter A** (Interface 0,
the JTAG that loads the FPGA) the **WinUSB** driver. Leave **USB Serial Converter B** alone: it is the COM port for a
circuit's TTY and Keyboard. The debuggers of both boards (a BL616 on the 20K, a BL702 on the 9K) present themselves
as the same two-interface FTDI chip, USB ID 0403:6010.

## 2. From a circuit to the board

The steps are those of the Cmod A7 (section 2 of [the openXC7 guide](../openxc7/README.md)), with these differences:

- The board is **SIPEED_TANG_NANO_20K** or **SIPEED_TANG_NANO_9K**. Each picture shows both faces: the top above,
  the bottom below, where the header pins carry their FPGA pin numbers.
- Both boards' clock is 27 MHz, so the fastest tick is 6.75 MHz. Logisim divides 27 MHz by an even number, so
  1024 kHz in the list gives 1.04 MHz (27 MHz / 26); that is the setting for a processor.
- The console shows nextpnr's `Max frequency for clock …`. nextpnr checks every path against one cycle of the 27 MHz
  clock, but a circuit's registers change at most every fourth cycle, on their clock's tick, so a FAIL is a warning
  here and does not stop the build. The A4 BEAG reports between 14 and 46 MHz on the 20K, depending on placement and
  the toolchain's version, and works at 14; on the 9K it reports 36 to 53 MHz.
- A circuit that needs more of something (LUTs, flip-flops, block RAMs) than the FPGA has stops at place & route with
  a message that says what it needs and what there is, as nextpnr by itself can go on trying for a long time. The 9K
  has 8640 LUTs, the 20K 20736: on the 9K the Week 6 RV32I takes 3352 of them (38%) and the A4 BEAG 2077 (24%).
- The Gowin flow reads Verilog: Logisim switches the HDL type to Verilog when it builds for this board.
- A flip-flop whose Preset is wired and whose Reset is not starts at 1, not 0: a Gowin flip-flop starts at the value
  of its asynchronous set or reset. From its first clock or reset on, it behaves as in Logisim. With Preset and Reset
  both wired, it is built from two flip-flops and a latch, and starts at 0 as in Logisim.
- **Write to flash** keeps the circuit after power-off. It replaces the demo that Sipeed ships in the flash. On the
  20K, to keep a copy, save the flash first with `openFPGALoader -b tangnano20k --dump-flash --file-size 8388608
  demo.bin` (about a minute), and put it back later with `openFPGALoader -b tangnano20k -f demo.bin` (about two
  minutes). On the 9K the circuit goes into the FPGA's own (internal) flash; the board's SPI flash chip is left alone.
- The tool path is **Preferences → Software → Gowin** (default `~/gowin/bin/`).

The serial port for a TTY and a Keyboard is the second port of the board's USB-C (115200 baud): on macOS
`/dev/cu.usbserial-<serial>1`, on Linux usually `/dev/ttyUSB1`. `support/openxc7/uart_terminal.sh` finds it.
The board's USB chip keeps what the FPGA sent while no terminal was open, so a terminal can start with old text,
such as the boot log of Sipeed's demo.

## 3. The Tang Nano 20K

These are on the board picture:

- **S1** (pin 88) and **S2** (pin 87), buttons that read 1 while pressed
- **LED0**–**LED5** (pins 15–20), lit when their pin is low; the board file says so, so a Logisim LED that is on
  lights them
- **UART_TX** (pin 69) and **UART_RX** (pin 70), the serial port, for a TTY and a Keyboard
- the header pins, **P** and the FPGA pin number (e.g. **P73**), for hardware wired to the board
- **TF_CLK**, **TF_CMD**, **TF_D0** and **TF_D3** on the TF card slot, and **LCD_G7**…**LCD_G2**, **LCD_R7**…**LCD_R5**
  on the 40-pin LCD connector: the lines of those two that are not header pins

Most header pins are wired to something on the board as well, listed below. A circuit can use any of them as an
ordinary pin; what is on the board stays connected. Pins 15–20 are on the board picture twice, as LEDs and as header
pins (**P15**–**P20**), and a circuit can use each pin under one of its names only: Logisim stops with a message if
both are mapped. As outputs they work as usual, with the LED lit while the pin is low. As inputs they need something
that drives both levels, or a switch to ground: the LED and its 510 Ω resistor to 3.3 V hold an open pin at about 1.5 V.

| Header pin | Also wired to |
| --- | --- |
| P15, P16, P17, P18 | LED0–LED3, and the LCD connector's touch-panel lines XR, YD, XL, YU |
| P19, P20 | LED4, LED5 |
| P25 | LCD HSYNC; HDMI CEC (through 100 Ω) |
| P26 | LCD VSYNC; HDMI hot-plug detect (through 2.2 kΩ) |
| P27, P28, P29, P30, P31 | LCD B7, B6, B5, B4, B3 |
| P41, P42 | LCD R4, R3 |
| P48 | LCD DE |
| P49 | LCD backlight on |
| P51 | speaker amplifier on (MAX98357A SD_MODE) |
| P52, P53 | HDMI DDC data and clock (through level shifters) |
| P54, P55, P56 | speaker I2S data (DIN), word clock (LRCLK), bit clock (BCLK) |
| P75, P76, P86 | the board's USB chip (BL616 GPIO 30, 27, 0; *SPI_MISO*, *SPI_MOSI*, *SPI_DIR* in the schematic) |
| P77 | LCD pixel clock |
| P79 | the WS2812 RGB LED's data input |
| P80, P85 | TF card D2, D1 |
| P71, P72, P73, P74 | nothing else |

The TF card's other lines: **TF_CLK** (pin 83), **TF_CMD** (82), **TF_D0** (84) and **TF_D3** (81), which are the
four that SPI mode uses (CS = D3, MOSI = CMD, SCLK = CLK, MISO = D0). CMD and D0–D3 have 10 kΩ pull-ups to 3.3 V on
the board.

The LCD connector's other lines: **LCD_G7**…**LCD_G2** (pins 32–37) and **LCD_R7**…**LCD_R5** (pins 38–40). R0–R2,
G0, G1 and B0–B2 are tied to ground, so a panel gets 5 bits of red, 6 of green and 5 of blue.

Two devices on the board need more than a pin can give:

- **HDMI** uses pins 33–40 (through 100 nF capacitors), which are also the LCD lines G6–G2 and R7–R5. It needs data
  at ten times the pixel clock, at least 250 Mbit/s on each pair, from serializers and a PLL; Logisim's circuits
  run from the 27 MHz clock on ordinary pins, so no circuit gets a picture onto HDMI.
- The **speaker** is driven by a MAX98357A, an amplifier with an I2S input: it needs P51 high and a stream of I2S
  frames on P54–P56. A Buzzer's square wave on a pin does not reach it; a circuit that sends I2S does.

Pins 52–56 are also the FPGA's slave-SPI configuration port. Logisim's flow hands them to the circuit
(`gowin_pack --sspi_as_gpio`); left a configuration port, they take what a circuit drives on them for commands,
and the next load stops at `Erase SRAM`.

The pin numbers above are from Sipeed's schematic of the board (version 3923) and its example designs. The board
picture is from photographs of a Tang Nano 20K.

## 4. The Tang Nano 9K

These are on the board picture:

- **S1** (pin 4) and **S2** (pin 3), buttons that read 1 while pressed: the button pulls its pin to ground against a
  10 kΩ pull-up, and the board file says so
- **LED0**–**LED5** (pins 10, 11, 13, 14, 15, 16, LED0 nearest S1), lit when their pin is low; the board file says
  so, so a Logisim LED that is on lights them
- **UART_TX** (pin 17) and **UART_RX** (pin 18), the serial port, for a TTY and a Keyboard
- the header pins, **P** and the FPGA pin number (e.g. **P25**), for hardware wired to the board
- **TF_CLK**, **TF_CMD**, **TF_D0** and **TF_D3** on the TF card slot, which are also the header pins P36, P37, P39
  and P38
- **LCD_YU** (pin 50) on the 40-pin LCD connector and **SPILCD_RESET** (pin 47) on the 8-pin connector of Sipeed's
  1.14-inch screen: the lines of those two that no header pin reaches

**1.8 V pins.** The FPGA's bank 3 runs at 1.8 V: S1, S2, the LEDs and the header pins **P79**–**P86**. The board file
gives them the 1.8 V standard (LVCMOS18), so a 1 there is 1.8 V, and hardware wired to P79–P86 must work with 1.8 V
levels and not drive them higher (Sipeed asks that no pin goes above the voltage its schematic gives it). The other
pins are 3.3 V.

Most header pins are wired to something on the board as well, listed below. A circuit can use any of them as an
ordinary pin; what is on the board stays connected. Pins 36–39 are on the board picture twice, as TF card lines and
as header pins (P36–P39), and a circuit can use each pin under one of its names only: Logisim stops with a message if
both are mapped.

| Header pin | Also wired to |
| --- | --- |
| P31, P32, P63 | the LCD connector's touch-panel lines YD, XR, XL |
| P33, P34, P35, P40 | LCD DE, VSYNC, pixel clock, HSYNC |
| P36, P37, P38, P39 | TF card CLK, CMD, D3, D0 (**TF_CLK**, **TF_CMD**, **TF_D3**, **TF_D0**) |
| P41, P42, P51, P53, P54 | LCD B7, B6, B5, B4, B3 |
| P55, P56, P57 | LCD G7, G6, G5 |
| P68, P69, P70 | LCD G4, G3, G2; HDMI clock −, clock +, D0 − (through 100 nF) |
| P71, P72, P73, P74, P75 | LCD R7, R6, R5, R4, R3; HDMI D0 +, D1 −, D1 +, D2 −, D2 + (through 100 nF) |
| P48, P49, P76, P77 | the 1.14-inch screen's CS, RS (data/command), SCL and SDA |
| P25–P30, P79–P86 | nothing else (P79–P86 are 1.8 V pins) |

The schematic names P86 as the LCD backlight's PWM, but the resistor that would connect it (R24) is not fitted: the
backlight of a 40-pin panel is always on.

The TF card's lines are the four that SPI mode uses (CS = D3, MOSI = CMD, SCLK = CLK, MISO = D0); D1 and D2 are not
connected, and CMD and D0 have 10 kΩ pull-ups to 3.3 V. They carry the names that a Block Storage's SD card is mapped
to, as on the 20K.

The 40-pin LCD connector: R0–R2, G0, G1 and B0–B2 are tied to ground, so a panel gets 5 bits of red (R3–R7), 6 of
green (G2–G7) and 5 of blue (B3–B7), and DISP is tied to 3.3 V. Its touch-panel lines are P32 (XR), P31 (YD), P63
(XL) and **LCD_YU** (pin 50). The 8-pin connector of the 1.14-inch screen: CS on P48, RS on P49, SCL on P76, SDA on
P77 and RESET on **SPILCD_RESET** (pin 47, with a 10 kΩ pull-up); its backlight is always on.

**HDMI** uses pins 68–75 (through 100 nF capacitors), which are also LCD lines; its DDC, CEC and hot-plug lines are
not wired to the FPGA. As on the 20K, no circuit gets a picture onto it.

The 9K has no SDRAM: its FPGA holds 8 MB of PSRAM, which has another interface, so a RAM's **FPGA memory: The
board's SDRAM** is for the 20K only. On the 9K a RAM is in the FPGA's block RAM, 26 blocks of 18 kbit.

Pins 53–57 have configuration functions too (55 and 56 are the slave-SPI port's chip select and data out), and
Logisim's flow hands them to the circuit as on the 20K (`gowin_pack --sspi_as_gpio`): a circuit that toggles P53–P57
loads again every time. S1's pin 4 is also the
FPGA's JTAGSEL_N, which matters only to a design that turns the JTAG pins into I/O; Logisim's flow does not.

The pin numbers above are from Sipeed's schematic of the board (Tang_nano_9K_3674) and its example designs. The
schematic's net names have the buttons the other way round (BUTTON_S1 on pin 3); the pins here are those of the
printed labels, as the switches are named in the schematic, and were checked on a board with "3674" on its back.
Sipeed's revision history has "Fixed the silkscreen of S1 and S2" (October 2022), so on an older board the two labels
may be swapped: the board picture goes by position, and S1 is the button next to header pin 63. The board picture is
from photographs of a Tang Nano 9K.

## 5. Troubleshooting

- **The load stops at `Erase SRAM`** and does not go on: reset the FPGA from its flash with
  `openFPGALoader -b tangnano20k -r` (`-b tangnano9k` for the 9K, or unplug the board), then load again.
- **`Re-definition of module`** should not appear any more: Yosys reads Gowin's cell library after the circuit, and a
  circuit with the name of one of its cells (ALU, DFF, MUX2, ROM and others) clashes with it. Logisim adds
  `_logisim` to such names in the HDL it generates, so the circuit called ALU becomes the module `ALU_logisim`.
- **`No nextpnr-himbaechel chip database for …`**: nextpnr was built without that chip family; run
  `setup_toolchain.sh` again with it in `GOWIN_DEVICES`.

## 6. Tests

`support/openxc7/tests/board/boardtest.py --board SIPEED_TANG_NANO_20K` (or `SIPEED_TANG_NANO_9K`) runs the on-board
test of [the openXC7 guide](../openxc7/README.md) on that board, with the Gowin toolchain in `~/gowin` (or
`$GOWIN_DIR`), and `support/openxc7/tests/run_course_test.py <folder> --board …` builds a folder of circuits for it
without touching the board.
