#!/usr/bin/env python3
"""Turn LogOS's computer.circ into one whose bus is built from multiplexers, so that it can go onto an FPGA.

    support/logos/fpga_bus.py logisim.jar computer.circ out.circ

The input is the computer.circ of LogOS 3.0 (branch v3.0-glendalough, circuit of 26 Mar 2026). What changes, in
that circuit's coordinates (everything but the CPU then moves 150 to the right, to make room for its tunnels):
- The CPU, Block Storage and Timer are wired to their separate Data In and Data Out pins (where their INOUT pin
  was, Data Out), and the RAM gets separate data in (D) and out.
- The Controlled Buffers, the Pull Resistors and the two AND gates that only enabled buffers go.
- Every pin of the old shared nets gets a tunnel of its own: the CPU's bus outputs (CPUAddress, CPUData,
  CPUMemRead, CPUMemWrite, CPUBE) and the Block Storage's DMA outputs (DMAAddress, BSData, DMAMemRead,
  DMAMemWrite, DMABE).
- Five multiplexers, selected by BusAck, choose who drives Address, WData, MemRead, MemWrite and BE: the CPU, or
  the Block Storage while it has the bus. The decoders, the devices and their tunnels keep these names.
- What the CPU reads, and the DMA reads from memory, RData, comes from one multiplexer whose select is the number
  of the device whose address comparator gives 1, from a priority encoder: 0 ROM, 1 RAM, 2 keyboard character,
  3 keyboard ready, 4 timer, 5 block storage. No device selected reads the ROM, as in COMP20020's A4 memory.

It then checks the result with Logisim: no net may have more than one output on it.
"""
import os
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
HARNESS = os.path.join(HERE, '..', 'openxc7', 'tests', 'harness')
SHIFT = 150


def P(s):
    return tuple(int(v) for v in s.strip('()').split(','))


def S(p):
    return f'({p[0]},{p[1]})'


def attrs_of(c):
    return {a.get('name'): a.get('val') for a in c.findall('a')}


def set_attr(c, name, val):
    for a in c.findall('a'):
        if a.get('name') == name:
            a.set('val', val)
            return
    children = list(c)
    new = ET.SubElement(c, 'a', {'name': name, 'val': val})
    if children:                                  # on a line of its own, as the others
        new.tail = children[-1].tail
        children[-1].tail = c.text


def main_circuit(tree):
    return next(c for c in tree.getroot().findall('circuit') if c.get('name') == 'main')


def find_comp(main, name, loc, label=None):
    found = [c for c in main.findall('comp') if c.get('name') == name and P(c.get('loc')) == loc
             and (label is None or attrs_of(c).get('label') == label)]
    assert len(found) == 1, (name, loc, label, len(found))
    return found[0]


def separate_pins(main):
    """The RAM's data bus made separate (the CPU, Block Storage and Timer have no other), the RAM in the SDRAM on
    the FPGA, and the CPU's TLB of 16 entries: only attributes change."""
    set_attr(find_comp(main, 'RV32IM CPU', (220, 590)), 'tlbEntries', '16')   # what fits an FPGA, also in Logisim
    ram = find_comp(main, 'RAM', (750, 810))
    set_attr(ram, 'databus', 'bibus')
    set_attr(ram, 'fpgaMemory', 'sdram')             # on the FPGA, the RAM is in the board's SDRAM


def net_dump(jar, classes, circ):
    return subprocess.run(['java', '-Djava.awt.headless=true', '-cp', classes + os.pathsep + jar, 'lstest.NetDump',
                           circ, 'main'], check=True, capture_output=True, text=True).stdout


def convert(tree, pins):
    main = main_circuit(tree)
    separate_pins(main)

    # ------------------------------------------------------------ what goes
    dead = [('Controlled Buffer', (670, 300)), ('Controlled Buffer', (670, 380)), ('Controlled Buffer', (1030, 700)),
            ('Pull Resistor', (410, 500)), ('Pull Resistor', (410, 530)),
            ('AND Gate', (570, 420)), ('AND Gate', (650, 700)),        # enables of the keyboard-ready and ROM buffers
            ('Tunnel', (510, 590)), ('Tunnel', (700, 330)),            # Data, on the CPU's data wires and the buffers
            ('Tunnel', (520, 440)),                                    # MemRead, into the keyboard-ready AND gate
            ('Tunnel', (990, 1170)), ('Tunnel', (990, 1200)),          # the Block Storage's DMA outputs, laid out
            ('Tunnel', (990, 1230)), ('Tunnel', (990, 1260)),          # again below
            ('Clock', (740, 930))]                                     # the RAM's clock, moved to make room for D
    for name, loc in dead:
        main.remove(find_comp(main, name, loc))

    cut = {
        ((340, 590), (480, 590)),                                                       # CPU -> Address decoders
        ((340, 650), (450, 650)), ((410, 530), (450, 530)),                             # CPU, resistor -> MemRead
        ((340, 680), (420, 680)), ((410, 500), (420, 500)),                             # CPU, resistor -> MemWrite
        ((340, 710), (360, 710)), ((360, 710), (360, 1260)), ((360, 1260), (780, 1260)),  # BusAck
        ((740, 920), (740, 930)), ((740, 920), (750, 920)),                             # the RAM's clock
        # the data bus: CPU, RAM, Block Storage and the ROM's buffer on one wire
        ((340, 620), (500, 620)), ((500, 590), (500, 620)), ((500, 590), (510, 590)), ((500, 620), (1050, 620)),
        ((960, 1080), (1050, 1080)), ((990, 940), (1050, 940)), ((1030, 700), (1050, 700)),
        ((1050, 620), (1050, 700)), ((1050, 700), (1050, 940)), ((1050, 940), (1050, 1080)),
    }
    for k, (y, x, xv) in enumerate([(740, 400, 710), (750, 390, 700), (760, 380, 690), (770, 370, 680)]):
        yb, yr = 1170 + 10 * k, 880 + 10 * k                   # BE3..BE0: to the Block Storage, to the RAM
        cut |= {((340, y), (x, y)), ((x, y), (x, yb)), ((x, yb), (780, yb)), ((x, y), (xv, y)),
                ((xv, y), (xv, yr)), ((xv, yr), (750, yr))}
    wires = {(P(w.get('from')), P(w.get('to'))): w for w in main.findall('wire')}
    for key in cut:
        assert key in wires, key
        main.remove(wires.pop(key))

    # prune the wire stubs that led to what went: an end that touches no pin, no tunnel and no other wire
    live = set()
    comp = None
    for line in pins.splitlines():
        m = re.match(r'COMP (\S+) at \((\d+),(\d+)\)', line)
        if m:
            name = m.group(1).split('#')[0].replace('_', ' ')
            comp = None if (name, (int(m.group(2)), int(m.group(3)))) in dead else name
            continue
        m = re.match(r'\s+p\d+ \((\d+),(\d+)\)', line)
        if m and comp:
            live.add((int(m.group(1)), int(m.group(2))))
    live |= {P(c.get('loc')) for c in main.findall('comp') if c.get('name') == 'Tunnel'}
    while True:
        ends = {}
        for a, b in wires:
            ends[a] = ends.get(a, 0) + 1
            ends[b] = ends.get(b, 0) + 1
        loose = [k for k in wires if any(ends[p] == 1 and p not in live for p in k)]
        if not loose:
            break
        for k in loose:
            main.remove(wires.pop(k))

    # ------------------------------------------------------------ room for the CPU's tunnels
    cpu_group = {('RV32IM CPU', (220, 590)), ('Clock', (150, 580)), ('Tunnel', (130, 730)), ('Tunnel', (140, 780)),
                 ('Tunnel', (140, 810))}
    cpu_wires = {((150, 580), (160, 580)), ((130, 730), (160, 730)), ((140, 780), (160, 780)),
                 ((140, 810), (160, 810))}
    for c in main.findall('comp'):
        if (c.get('name'), P(c.get('loc'))) not in cpu_group:
            x, y = P(c.get('loc'))
            c.set('loc', S((x + SHIFT, y)))
    for (a, b), w in wires.items():
        if (a, b) not in cpu_wires:
            w.set('from', S((a[0] + SHIFT, a[1])))
            w.set('to', S((b[0] + SHIFT, b[1])))

    def mv(p):
        return (p[0] + SHIFT, p[1])

    # the tunnels that stay but change: the TTY and the Timer now take and give their own data
    set_attr(find_comp(main, 'Tunnel', mv((140, 130)), 'Data'), 'label', 'WData')
    set_attr(find_comp(main, 'Tunnel', mv((940, 460)), 'Data'), 'label', 'TimerData')

    # ------------------------------------------------------------ what comes
    lib = {'Tunnel': '0', 'Splitter': '0', 'Constant': '0', 'Clock': '0', 'Multiplexer': '2',
           'Priority Encoder': '2', 'Comparator': '3', 'Text': '8'}

    def add(name, loc, **a):
        c = ET.SubElement(main, 'comp', {'lib': lib[name], 'loc': S(loc), 'name': name})
        for k, v in a.items():
            ET.SubElement(c, 'a', {'name': k, 'val': str(v)})

    def wire(*pts):
        for a, b in zip(pts, pts[1:]):
            ET.SubElement(main, 'wire', {'from': S(a), 'to': S(b)})

    def tunnel(loc, label, width=1, facing='west'):
        a = {'label': label}
        if facing != 'west':
            a['facing'] = facing
        if width != 1:
            a['width'] = width
        add('Tunnel', loc, **a)

    def out_tunnel(pin, label, width=1, length=20):   # a pin on a right edge: stub, tunnel pointing back at it
        end = (pin[0] + length, pin[1])
        wire(pin, end)
        tunnel(end, label, width)

    def in_tunnel(pin, label, width=1, length=20):    # a pin on a left edge
        end = (pin[0] - length, pin[1])
        wire(end, pin)
        tunnel(end, label, width, 'east')

    be_bits = {'bit0': 3, 'bit1': 2, 'bit2': 1, 'bit3': 0}   # one-bit lines BE3..BE0 top to bottom, a 4-bit bus

    # the CPU (not moved)
    for y, label, width in [(590, 'CPUAddress', 32), (620, 'CPUData', 32), (650, 'CPUMemRead', 1),
                            (680, 'CPUMemWrite', 1), (710, 'BusAck', 1)]:
        out_tunnel((340, y), label, width)
    in_tunnel((160, 620), 'RData', 32)
    for y in (740, 750, 760, 770):
        wire((340, y), (360, y))
    add('Splitter', (380, 780), facing='west', appear='right', fanout=4, incoming=4, **be_bits)
    out_tunnel((380, 780), 'CPUBE', 4)
    # BE back out to the RAM's byte enables, along the old wires' paths
    add('Splitter', (530, 780), fanout=4, incoming=4, **be_bits)
    in_tunnel((530, 780), 'BE', 4)
    for y, xv, yr in [(740, 860, 880), (750, 850, 890), (760, 840, 900), (770, 830, 910)]:
        wire((550, y), (xv, y), (xv, yr), (900, yr))

    # the ROM, RAM and keyboard give their data to the read multiplexer
    out_tunnel(mv((990, 700)), 'ROMData', 32)
    out_tunnel(mv((990, 940)), 'RAMData', 32)
    wire(mv((750, 940)), mv((670, 940)))                 # the RAM's D, across its clock's wire
    tunnel(mv((670, 940)), 'WData', 32, 'east')
    add('Clock', mv((700, 990)))
    wire(mv((700, 990)), mv((730, 990)), mv((730, 920)), mv((750, 920)))
    out_tunnel(mv((630, 300)), 'KbdData', 32)
    out_tunnel(mv((630, 380)), 'KbdRdy', 32)

    # the Timer and the Block Storage
    wire(mv((910, 530)), mv((910, 600)))                 # Timer Data In, on its bottom edge, below its clock
    tunnel(mv((910, 600)), 'WData', 32, 'north')
    out_tunnel(mv((960, 1080)), 'BSData', 32)
    out_tunnel(mv((960, 1100)), 'RData', 32, 100)
    out_tunnel(mv((960, 1180)), 'DMAMemRead')
    out_tunnel(mv((960, 1200)), 'DMAMemWrite', 1, 140)
    out_tunnel(mv((960, 1220)), 'DMAAddress', 32)
    out_tunnel(mv((960, 1240)), 'BusReq', 1, 140)
    add('Splitter', mv((980, 1300)), facing='west', appear='right', fanout=4, incoming=4, **be_bits)
    out_tunnel(mv((980, 1300)), 'DMABE', 4)
    in_tunnel(mv((780, 1140)), 'WData', 32)
    wire(mv((780, 1260)), mv((770, 1260)), mv((770, 1240)), mv((750, 1240)))
    tunnel(mv((750, 1240)), 'BusAck', 1, 'east')

    # the address comparators' outputs, for the read multiplexer's encoder
    wire(mv((360, 320)), mv((360, 280)))                 # keyboard character, 0xffff0008
    tunnel(mv((360, 280)), 'selKbd', 1, 'south')
    wire(mv((520, 400)), mv((530, 400)), mv((530, 440)), mv((550, 440)))   # keyboard ready, 0xffff0004
    tunnel(mv((550, 440)), 'selKbdRdy')
    out_tunnel(mv((600, 680)), 'selROM')                 # ROM, 0x000000-0x00ffff
    wire(mv((600, 900)), mv((600, 970)))                 # RAM, 0x100000-0x1fffff
    tunnel(mv((600, 970)), 'selRAM', 1, 'north')
    wire(mv((600, 1110)), mv((600, 1150)))               # Block Storage, 0x200000-0x2000ff
    tunnel(mv((600, 1150)), 'selBS', 1, 'north')

    # ------------------------------------------------------------ the bus
    def fan_in(inputs, tunnels, tx, labels, width):
        """Wires from tunnels (on a 30 pitch, pointing east at x = tx) to inputs (on a 10 pitch): each on a
        vertical track of its own, as in the A4 memory, so that no wire crosses another."""
        ix = inputs[0][0]
        down = [j for j in range(len(inputs)) if tunnels[j] <= inputs[j][1]]
        for k, ((_, iy), ty, label) in enumerate(zip(inputs, tunnels, labels)):
            rank = down.index(k) if k in down else (len(inputs) - 1 - k)
            track = ix - 10 * (rank + 1)
            wire((tx, ty), (track, ty), (track, iy), (ix, iy))
            tunnel((tx, ty), label, width, 'east')

    x = 1700                                             # right of the Block Storage and the RAM
    add('Text', (x - 20, 400), text='Who drives the bus: the CPU, or the Block Storage (DMA) while BusAck = 1')
    for k, (what, width) in enumerate([('Address', 32), ('WData', 32), ('MemRead', 1), ('MemWrite', 1),
                                       ('BE', 4)]):
        lx, ly = x, 470 + 130 * k
        cpu, dma = ('CPUData', 'BSData') if what == 'WData' else ('CPU' + what, 'DMA' + what)
        add('Multiplexer', (lx, ly), **({'width': width} if width != 1 else {}))
        in_tunnel((lx - 30, ly - 10), cpu, width)
        wire((lx - 30, ly + 10), (lx - 40, ly + 10), (lx - 40, ly + 30), (lx - 50, ly + 30))
        tunnel((lx - 50, ly + 30), dma, width, 'east')
        wire((lx - 20, ly + 20), (lx - 20, ly + 40))
        tunnel((lx - 20, ly + 40), 'BusAck', 1, 'north')
        out_tunnel((lx, ly), what, width)

    # the timer's comparator: 0x2000000-0x200ffff (mtimecmp 0x2004000, mtime 0x200bff8)
    cx, cy = x, 1170
    add('Comparator', (cx, cy), mode='unsigned', width=16)
    add('Splitter', (cx - 60, cy - 20), appear='right', fanout=1, incoming=32,
        **{f'bit{i}': ('0' if i >= 16 else 'none') for i in range(32)})     # Address[31:16]
    in_tunnel((cx - 60, cy - 20), 'Address', 32)
    add('Constant', (cx - 50, cy + 10), value='0x200', width=16)
    wire((cx - 50, cy + 10), (cx - 40, cy + 10))
    out_tunnel((cx, cy), 'selTimer')

    # the read multiplexer and its encoder
    mx, my = x + 140, 1330
    add('Text', (mx - 120, 1210), text='What the CPU reads: the data of the device whose address comparator gives 1')
    add('Multiplexer', (mx, my), select=3, width=32)
    fan_in([(mx - 40, my - 40 + 10 * i) for i in range(6)], [my - 90 + 30 * i for i in range(6)], mx - 100,
           ['ROMData', 'RAMData', 'KbdData', 'KbdRdy', 'TimerData', 'BSData'], 32)
    out_tunnel((mx, my), 'RData', 32)
    ex, ey = mx - 20, my + 180
    add('Priority Encoder', (ex, ey))
    wire((ex, ey), (ex, my + 40))                        # encoder output -> multiplexer select
    fan_in([(ex - 40, ey - 30 + 10 * i) for i in range(6)], [ey - 80 + 30 * i for i in range(6)], ex - 100,
           ['selROM', 'selRAM', 'selKbd', 'selKbdRdy', 'selTimer', 'selBS'], 1)
    add('Text', (mx - 120, ey + 110), text='0 ROM, 1 RAM, 2 keyboard character, 3 keyboard ready, 4 timer, '
                                           '5 block storage')


def check(dump):
    """No net with two outputs on it, and no bidirectional pin but a splitter's: what the FPGA flow cannot build."""
    direction, comp = {}, None
    for line in dump.splitlines():
        m = re.match(r'COMP (\S+) at', line)
        if m:
            comp = m.group(1)
            continue
        m = re.match(r'\s+(p\d+) \S+ w\d+ (\w+)', line)
        if m:
            direction[f'{comp}.{m.group(1)}'] = m.group(2)
    bad = []
    for line in dump.splitlines():
        if not line.startswith('NET '):
            continue
        pins = [p for p in line[4:].split() if not p.startswith('~')]
        outs = [p for p in pins if direction.get(p) == 'out']
        inouts = [p for p in pins if direction.get(p) == 'inout' and not p.startswith('Splitter')]
        if len(outs) > 1 or inouts:
            bad.append(line)
    return bad


def main():
    jar, src, dst = sys.argv[1:4]
    with tempfile.TemporaryDirectory() as tmp:
        classes = os.path.join(tmp, 'classes')
        subprocess.run(['javac', '-nowarn', '-d', classes, '-cp', jar,
                        os.path.join(HARNESS, 'lstest', 'NetDump.java')], check=True)
        tree = ET.parse(src)
        separate_pins(main_circuit(tree))
        tree.write(os.path.join(tmp, 'pins.circ'), encoding='UTF-8', xml_declaration=True)
        pins = net_dump(jar, classes, os.path.join(tmp, 'pins.circ'))
        tree = ET.parse(src)
        convert(tree, pins)
        tree.write(dst, encoding='UTF-8', xml_declaration=True)
        bad = check(net_dump(jar, classes, dst))
    for line in bad:
        print('more than one driver:', line)
    print(f'{dst}: {"FAILED" if bad else "every net has one driver"}')
    sys.exit(1 if bad else 0)


if __name__ == '__main__':
    main()
