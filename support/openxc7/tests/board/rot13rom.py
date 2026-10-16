#!/usr/bin/env python3
"""rot13rom.py <in.circ> <out.circ>: fill the 7-bit ROM with ROT13 of the ASCII code (the A2 exercise)."""
import sys, xml.etree.ElementTree as ET
def rot13(c):
    if ord('a') <= c <= ord('z'): return (c - 97 + 13) % 26 + 97
    if ord('A') <= c <= ord('Z'): return (c - 65 + 13) % 26 + 65
    return c
tree = ET.parse(sys.argv[1])
for comp in tree.getroot().iter("comp"):
    if comp.get("name") == "ROM":
        attrs = {a.get("name"): a for a in comp.findall("a")}
        attrs["contents"].text = "addr/data: 7 7\n" + " ".join(f"{rot13(a):x}" for a in range(128)) + "\n"
tree.write(sys.argv[2], encoding="UTF-8", xml_declaration=True)
