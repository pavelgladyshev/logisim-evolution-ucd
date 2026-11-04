#!/usr/bin/env python3
"""Build sdram_board_test for a Tang Nano 20K, load it (into SRAM) and print what it sends over the USB serial port.

    support/logos/tests/sdram/board/run.py [seconds]        the SDRAM's pins placed by IO_LOC, as Logisim does
    support/logos/tests/sdram/board/run.py --sip [seconds]  ports named O_sdram_*, which nextpnr places by itself
    support/logos/tests/sdram/board/run.py --compare        build both, and compare where the pins went

Needs the Gowin toolchain in ~/gowin (support/gowin) and the board on USB. A pass over the 8 MB takes 4 seconds.
"""
import glob
import json
import os
import select
import subprocess
import sys
import tempfile
import termios
import time

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..', '..'))
MODULE = os.path.join(REPO, 'src/main/resources/resources/logisim/hdl/memory/logisimSdram.v')
TOOLS = os.path.expanduser(os.environ.get('GOWIN_DIR', '~/gowin') + '/bin')

# The GW2AR-18's SDRAM pins in the QN88 package: (row, column, IO) from apicula's chip database (sip_cst), as IO_LOC
# names: IO, the side (L for column 0, R for the last), the row from 1, and the IO.
SIP = [('O_sdram_clk', 10, 55, 'B'), ('O_sdram_cke', 12, 0, 'A'), ('O_sdram_cs_n', 13, 0, 'B'),
       ('O_sdram_cas_n', 13, 0, 'A'), ('O_sdram_ras_n', 12, 0, 'B'), ('O_sdram_wen_n', 11, 0, 'B'),
       ('O_sdram_dqm[0]', 11, 0, 'A'), ('O_sdram_dqm[1]', 10, 55, 'A'), ('O_sdram_dqm[2]', 17, 0, 'A'),
       ('O_sdram_dqm[3]', 14, 55, 'B'), ('O_sdram_addr[0]', 13, 55, 'A'), ('O_sdram_addr[1]', 12, 55, 'B'),
       ('O_sdram_addr[2]', 13, 55, 'B'), ('O_sdram_addr[3]', 14, 55, 'A'), ('O_sdram_addr[4]', 15, 0, 'B'),
       ('O_sdram_addr[5]', 16, 0, 'B'), ('O_sdram_addr[6]', 15, 0, 'A'), ('O_sdram_addr[7]', 16, 0, 'A'),
       ('O_sdram_addr[8]', 14, 0, 'B'), ('O_sdram_addr[9]', 14, 0, 'A'), ('O_sdram_addr[10]', 11, 55, 'B'),
       ('O_sdram_ba[0]', 12, 55, 'A'), ('O_sdram_ba[1]', 11, 55, 'A'), ('IO_sdram_dq[0]', 2, 0, 'A'),
       ('IO_sdram_dq[1]', 2, 0, 'B'), ('IO_sdram_dq[2]', 7, 0, 'A'), ('IO_sdram_dq[3]', 7, 0, 'B'),
       ('IO_sdram_dq[4]', 8, 0, 'A'), ('IO_sdram_dq[5]', 8, 0, 'B'), ('IO_sdram_dq[6]', 10, 0, 'A'),
       ('IO_sdram_dq[7]', 10, 0, 'B'), ('IO_sdram_dq[8]', 8, 55, 'B'), ('IO_sdram_dq[9]', 8, 55, 'A'),
       ('IO_sdram_dq[10]', 4, 55, 'B'), ('IO_sdram_dq[11]', 5, 55, 'A'), ('IO_sdram_dq[12]', 4, 55, 'A'),
       ('IO_sdram_dq[13]', 3, 55, 'B'), ('IO_sdram_dq[14]', 2, 55, 'B'), ('IO_sdram_dq[15]', 2, 55, 'A'),
       ('IO_sdram_dq[16]', 38, 0, 'B'), ('IO_sdram_dq[17]', 38, 0, 'A'), ('IO_sdram_dq[18]', 34, 0, 'B'),
       ('IO_sdram_dq[19]', 34, 0, 'A'), ('IO_sdram_dq[20]', 29, 0, 'B'), ('IO_sdram_dq[21]', 29, 0, 'A'),
       ('IO_sdram_dq[22]', 19, 0, 'A'), ('IO_sdram_dq[23]', 17, 0, 'B'), ('IO_sdram_dq[24]', 16, 55, 'A'),
       ('IO_sdram_dq[25]', 15, 55, 'A'), ('IO_sdram_dq[26]', 15, 55, 'B'), ('IO_sdram_dq[27]', 16, 55, 'B'),
       ('IO_sdram_dq[28]', 17, 55, 'A'), ('IO_sdram_dq[29]', 17, 55, 'B'), ('IO_sdram_dq[30]', 43, 55, 'A'),
       ('IO_sdram_dq[31]', 43, 55, 'B')]
BOARD = ['IO_LOC "clk" 4;', 'IO_PORT "clk" IO_TYPE=LVCMOS33;', 'IO_LOC "uart_tx" 69;',
         'IO_PORT "uart_tx" IO_TYPE=LVCMOS33;'] + \
        [f'IO_LOC "led[{i}]" {15 + i};\nIO_PORT "led[{i}]" IO_TYPE=LVCMOS33;' for i in range(6)]


def test_name(sip_name):
    return sip_name.split('_', 1)[1]          # O_sdram_addr[3] -> sdram_addr[3]


def io_name(row, column, io):
    return f'IO{"L" if column == 0 else "R"}{row + 1}{io}'


def build(tmp, sip):
    cst = list(BOARD)
    sources = [MODULE, os.path.join(HERE, 'sdram_board_test.v')]
    top = 'sdram_board_test'
    if sip:
        # a top whose SDRAM ports have the names nextpnr knows
        ports = ', '.join(['input clk', 'output uart_tx', 'output [5:0] led', 'output O_sdram_clk',
                           'output O_sdram_cke', 'output O_sdram_cs_n', 'output O_sdram_cas_n',
                           'output O_sdram_ras_n', 'output O_sdram_wen_n', 'output [3:0] O_sdram_dqm',
                           'output [10:0] O_sdram_addr', 'output [1:0] O_sdram_ba', 'inout [31:0] IO_sdram_dq'])
        names = ['clk', 'uart_tx', 'led'] + [p.split()[-1] for p in ports.split(', ')[3:]]
        wiring = ', '.join(f'.{n if n in ("clk", "uart_tx", "led") else test_name(n)}({n})' for n in names)
        with open(os.path.join(tmp, 'sip_top.v'), 'w') as f:
            f.write(f'module sip_top ({ports});\n   sdram_board_test test ({wiring});\nendmodule\n')
        sources.append(os.path.join(tmp, 'sip_top.v'))
        top = 'sip_top'
    else:
        for net, row, column, io in SIP:
            cst.append(f'IO_LOC "{test_name(net)}" {io_name(row, column, io)};')
            cst.append(f'IO_PORT "{test_name(net)}" IO_TYPE=LVCMOS33;')
    with open(os.path.join(tmp, 'top.cst'), 'w') as f:
        f.write('\n'.join(cst) + '\n')
    run = lambda *cmd: subprocess.run(cmd, check=True, capture_output=True, text=True)
    run(f'{TOOLS}/yosys', '-q', '-p', f'read_verilog {" ".join(sources)}; synth_gowin -top {top} -json {tmp}/top.json')
    pnr = run(f'{TOOLS}/nextpnr-himbaechel', '--json', f'{tmp}/top.json', '--write', f'{tmp}/pnr.json',
              '--device', 'GW2AR-LV18QN88C8/I7', '--vopt', 'family=GW2A-18C', '--vopt', f'cst={tmp}/top.cst',
              '--freq', '27')
    for line in pnr.stderr.splitlines():
        if 'Max frequency' in line or 'ERROR' in line or 'Warning' in line and 'sdram' in line.lower():
            print(line)
    run(f'{TOOLS}/gowin_pack', '-d', 'GW2A-18C', '-o', f'{tmp}/top.fs', f'{tmp}/pnr.json')
    return os.path.join(tmp, 'top.fs')


def placement(tmp):
    """The BEL of the IO cell at each of the top module's SDRAM ports, by the port's name in sdram_board_test."""
    design = json.load(open(os.path.join(tmp, 'pnr.json')))
    module = design['modules'][next(iter(design['modules']))]
    ports = {}
    for name, port in module['ports'].items():
        if 'sdram' in name:
            for index, bit in enumerate(port['bits']):
                key = name if len(port['bits']) == 1 else f'{name}[{index}]'
                ports[bit] = test_name(key) if key.startswith(('O_', 'IO_')) else key
    result = {}
    for cell in module['cells'].values():
        bel = cell['attributes'].get('NEXTPNR_BEL')
        if bel is None:
            continue
        for bits in cell['connections'].values():
            for bit in bits:
                if bit in ports and '/IOB' in bel:
                    result.setdefault(ports[bit], set()).add(bel)
    return result


def port():
    found = sorted(glob.glob('/dev/cu.usbserial-*1')) or sorted(glob.glob('/dev/ttyUSB1'))
    if not found:
        sys.exit('no serial port of the board')
    return found[0]


args = [a for a in sys.argv[1:] if not a.startswith('--')]
seconds = float(args[0]) if args else 20
with tempfile.TemporaryDirectory() as tmp:
    if '--compare' in sys.argv:
        os.mkdir(os.path.join(tmp, 'sip'))
        os.mkdir(os.path.join(tmp, 'cst'))
        build(os.path.join(tmp, 'sip'), True)
        build(os.path.join(tmp, 'cst'), False)
        a, b = placement(os.path.join(tmp, 'sip')), placement(os.path.join(tmp, 'cst'))
        same = sum(1 for k in a if a[k] == b.get(k))
        print(f'{len(a)} SDRAM pins placed by name, {len(b)} by IO_LOC, {same} on the same IO')
        for k in sorted(set(a) | set(b)):
            if a.get(k) != b.get(k):
                print(f'  {k}: {a.get(k)} by name, {b.get(k)} by IO_LOC')
        sys.exit(0)
    bitstream = build(tmp, '--sip' in sys.argv)
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
    subprocess.run([f'{TOOLS}/openFPGALoader', '-b', 'tangnano20k', bitstream], check=True, capture_output=True)
    end = time.time() + seconds
    while time.time() < end:
        if select.select([fd], [], [], 0.2)[0]:
            sys.stdout.write(os.read(fd, 4096).decode('latin-1'))
            sys.stdout.flush()
    os.close(fd)
