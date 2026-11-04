#!/usr/bin/env python3
"""LogOS on a Tang Nano 20K: load a bitstream (into the FPGA's SRAM), type commands at LogOS's shell over the
board's USB serial port, and print the session, as logos_session.py does in Logisim.

    support/logos/board_session.py bitstream.fs [command ...]

The bitstream is Logisim's for computer.circ (fpga branch) on the Tang Nano 20K, and the microSD card in the TF
slot has LogOS's disk image from its block 0 (block_storage.bin). Each command is typed after the shell's prompt
("$ ") appears, and the session ends after the last prompt. Needs openFPGALoader (~/gowin/bin, or GOWIN_DIR).
"""
import glob
import os
import select
import subprocess
import sys
import termios
import time

TOOLS = os.path.expanduser(os.environ.get('GOWIN_DIR', '~/gowin') + '/bin')
bitstream, commands = sys.argv[1], sys.argv[2:]


def port():
    found = sorted(glob.glob('/dev/cu.usbserial-*1')) or sorted(glob.glob('/dev/ttyUSB1'))
    if not found:
        sys.exit('no serial port of the board')
    return found[0]


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

start = time.time()
subprocess.run([f'{TOOLS}/openFPGALoader', '-b', 'tangnano20k', bitstream], check=True, capture_output=True)
loaded = time.time()
out = bytearray()


def until_prompt(limit):
    while time.time() < limit:
        if select.select([fd], [], [], 0.2)[0]:
            out.extend(os.read(fd, 4096))
            if out.endswith(b'$ '):
                return True
    return False


ok = until_prompt(loaded + 120)
marks = [f'loaded in {loaded - start:.0f}s, boot {time.time() - loaded:.1f}s']
for cmd in commands:
    if not ok:
        break
    os.write(fd, cmd.encode() + b'\r')
    ok = until_prompt(time.time() + 600)
    marks.append(f'{cmd!r} {time.time() - loaded:.0f}s')
os.close(fd)
sys.stdout.write(out.decode('latin-1'))
sys.stdout.write('\n--- ' + ('ok' if ok else 'NO PROMPT') + ': ' + ', '.join(marks) + '\n')
