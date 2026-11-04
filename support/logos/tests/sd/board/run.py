#!/usr/bin/env python3
"""Build sd_board_test for a Tang Nano 20K, load it (into SRAM) and print what it sends over the USB serial port.

    support/logos/tests/sd/board/run.py [block [seconds]]

block is the card's block to read (0, the partition table, unless given); the report comes for 8 seconds.

Needs the Gowin toolchain in ~/gowin (support/gowin) and the board on USB; the microSD card goes in its TF slot.
"""
import glob
import os
import select
import subprocess
import sys
import tempfile
import termios
import time

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..', '..'))
MODULE = os.path.join(REPO, 'src/main/resources/resources/logisim/hdl/riscv/riscvSdBlockStorage.v')
TOOLS = os.path.expanduser(os.environ.get('GOWIN_DIR', '~/gowin') + '/bin')
block = int(sys.argv[1], 0) if len(sys.argv) > 1 else 0
seconds = float(sys.argv[2]) if len(sys.argv) > 2 else 8


def port():
    found = sorted(glob.glob('/dev/cu.usbserial-*1')) or sorted(glob.glob('/dev/ttyUSB1'))
    if not found:
        sys.exit('no serial port of the board')
    return found[0]


with tempfile.TemporaryDirectory() as tmp:
    run = lambda *cmd: subprocess.run(cmd, check=True, capture_output=True, text=True)
    run(f'{TOOLS}/yosys', '-q', '-p', f'read_verilog {MODULE} {HERE}/sd_board_test.v; chparam -set BLOCK {block} sd_board_test; '
        f'synth_gowin -top sd_board_test -json {tmp}/top.json')
    run(f'{TOOLS}/nextpnr-himbaechel', '--json', f'{tmp}/top.json', '--write', f'{tmp}/pnr.json',
        '--device', 'GW2AR-LV18QN88C8/I7', '--vopt', 'family=GW2A-18C', '--vopt', f'cst={HERE}/tangnano20k.cst',
        '--freq', '27', '-q')
    run(f'{TOOLS}/gowin_pack', '-d', 'GW2A-18C', '-o', f'{tmp}/top.fs', f'{tmp}/pnr.json')
    fd = os.open(port(), os.O_RDWR | os.O_NOCTTY | os.O_NONBLOCK)
    attrs = termios.tcgetattr(fd)
    attrs[0] = 0; attrs[1] = 0; attrs[3] = 0
    attrs[2] = termios.CS8 | termios.CREAD | termios.CLOCAL
    attrs[4] = attrs[5] = termios.B115200
    termios.tcsetattr(fd, termios.TCSANOW, attrs)
    time.sleep(0.3)
    termios.tcflush(fd, termios.TCIFLUSH)
    while select.select([fd], [], [], 0.2)[0]:     # what the board's USB chip kept from before
        os.read(fd, 4096)
    run(f'{TOOLS}/openFPGALoader', '-b', 'tangnano20k', f'{tmp}/top.fs')
    out = bytearray()
    end = time.time() + seconds
    while time.time() < end:
        if select.select([fd], [], [], 0.2)[0]:
            out.extend(os.read(fd, 4096))
    os.close(fd)
    sys.stdout.write(out.decode('latin-1'))
