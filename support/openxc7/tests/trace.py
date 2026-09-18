#!/usr/bin/env python3
"""trace.py <run dir> <component name> [rows]: per step, the component's inputs and Logisim/HDL outputs."""
import re, sys
d, name = sys.argv[1], sys.argv[2]
n = int(sys.argv[3]) if len(sys.argv) > 3 else 12
tb = open(f"{d}/tb.v").read()
sl = {m.group(1): (int(m.group(2)), int(m.group(3))) for m in re.finditer(r"assign (\w+) = row\[(\d+):(\d+)\];", tb)}
rows = [l.strip() for l in open(f"{d}/inputs.hex")]
cols = [l.split()[0] for l in open(f"{d}/columns.txt")]
exp = [l.split() for l in open(f"{d}/expected.txt")]
act = [l.split() for l in open(f"{d}/actual.txt")]
ins = sorted(k for k in sl if k.startswith(name + "_"))
outs = [c for c in cols if c.startswith(name + "_")]
print("step  " + " ".join(f"{k[len(name)+1:]:>4}" for k in ins) + "   |  " + "  ".join(f"{o[len(name)+1:]}(L/H)" for o in outs))
for r in range(n):
    v = int(rows[r], 16)
    vals = [(v >> sl[k][1]) & ((1 << (sl[k][0] - sl[k][1] + 1)) - 1) for k in ins]
    o = [f"{exp[r][cols.index(c)]}/{act[r][cols.index(c)]}" + ("" if exp[r][cols.index(c)] == act[r][cols.index(c)] else " <<") for c in outs]
    print(f"{r:4}  " + " ".join(f"{x:>4x}" for x in vals) + "   |  " + "  ".join(o))
