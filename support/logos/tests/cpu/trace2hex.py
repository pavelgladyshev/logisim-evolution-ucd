#!/usr/bin/env python3
"""trace2hex.py trace hex: a record of rv32imTrace (16 bytes an edge) as a line of hex for tb_cpu_replay.v:
Data In, the inputs' flags, Address, Data Out, the outputs' flags."""
import struct
import sys

with open(sys.argv[1], 'rb') as f, open(sys.argv[2], 'w') as out:
    while True:
        record = f.read(16)
        if len(record) < 16:
            break
        data_in, in_flags, address, data_out, out_flags = struct.unpack_from('<IBIIB', record)
        out.write(f'{data_in:08x} {in_flags:02x} {address:08x} {data_out:08x} {out_flags:02x}\n')
