#!/usr/bin/env python3
"""compare.py <dir> [actual file]: expected.txt (Logisim simulation) vs actual.txt (HDL simulation), per output
column."""
import sys, collections
d = sys.argv[1]
actual = sys.argv[2] if len(sys.argv) > 2 else "actual.txt"
cols = [l.split() for l in open(f"{d}/columns.txt")]
exp = [l.split() for l in open(f"{d}/expected.txt")]
act = [l.split() for l in open(f"{d}/{actual}")]
inputs = [l.strip() for l in open(f"{d}/inputs.hex")]
assert len(exp) == len(act), (len(exp), len(act))
bad = collections.defaultdict(list)
for r, (e, a) in enumerate(zip(exp, act)):
    for c, (ev, av) in enumerate(zip(e, a)):
        if '?' in ev:            # Logisim value not fully defined: skip
            continue
        if ev.lower() != av.lower():
            bad[cols[c][0]].append((r, ev, av))
comp_bad = collections.defaultdict(int)
for name, rows in bad.items():
    comp_bad[name.rsplit('_', 1)[0]] += len(rows)
total = len(exp) * len(cols)
print(f"{len(exp)} rows x {len(cols)} outputs: {sum(len(v) for v in bad.values())} mismatches")
for name in sorted(bad):
    r, ev, av = bad[name][0]
    print(f"  {name}: {len(bad[name])} rows differ, e.g. row {r}: logisim {ev} hdl {av}   (inputs {inputs[r]})")
sys.exit(1 if bad else 0)
