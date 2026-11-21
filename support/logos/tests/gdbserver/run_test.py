#!/usr/bin/env python3
"""The GDB server's integration test.

usage: python3 run_test.py --jar JAR [--gdb GDB] [--objcopy OBJCOPY]

It puts prog.elf into the ROM of a copy of computer.circ (build/computer-test.circ), starts Logisim on it without
a window (--tty tty, stdin empty; the CPU has Debug Enable set and TCP port 3340, so it waits for gdb), runs the
sessions of SESSIONS one after the other (gdb in batch mode on gdb_test.gdb and the others, and packets sent
without gdb), stops Logisim and compares what they printed (build/gdb.log) with expected.txt.

expected.txt has one regular expression per line, which must match a line of the output (re.search), in the
order of the file; lines in between are skipped, except before an expression that starts with "+ ": that one must
match the line right after the previous match. Empty lines and lines starting with "#" are not expressions.
It also checks that the server wrote nothing to the simulated console (Logisim's output, build/logisim.log).

The exit status is 0 if everything matched, 1 otherwise.
"""
import argparse
import os
import re
import socket
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
BUILD = os.path.join(HERE, "build")
PORT = 3340


def rom_contents(elf, objcopy):
    """The ROM's contents attribute for the program: its words, in Logisim's "addr/data" format."""
    binfile = os.path.join(BUILD, "prog.bin")
    subprocess.run([objcopy, "-O", "binary", "--only-section", ".text", elf, binfile], check=True)
    data = open(binfile, "rb").read()
    data += bytes(-len(data) % 4)
    words = ["%x" % int.from_bytes(data[i:i + 4], "little") for i in range(0, len(data), 4)]
    lines = [" ".join(words[i:i + 8]) for i in range(0, len(words), 8)]
    return "addr/data: 14 32\n" + "\n".join(lines) + "\n"


def make_circuit(template, out, contents):
    circ = open(template).read()
    rom = re.compile(r'(name="ROM">.*?<a name="contents">)(.*?)(</a>)', re.S)
    circ, n = rom.subn(lambda m: m.group(1) + contents + m.group(3), circ, count=1)
    if n != 1:
        sys.exit("run_test.py: no ROM with contents in " + template)
    open(out, "w").write(circ)


def compare(output, expected):
    """The failures: each expected expression that did not match, with the place where it was looked for."""
    lines = output.splitlines()
    pos = 0
    failures = []
    for exp in expected:
        exp = exp.rstrip("\n")
        if not exp.strip() or exp.startswith("#"):
            continue
        if exp.startswith("+ "):
            pattern = re.compile(exp[2:])
            if pos < len(lines) and pattern.search(lines[pos]):
                pos += 1
            else:
                got = lines[pos] if pos < len(lines) else "(end of output)"
                failures.append("expected the next line to match %r, got %r" % (exp[2:], got))
                pos += 1
            continue
        pattern = re.compile(exp)
        for i in range(pos, len(lines)):
            if pattern.search(lines[i]):
                pos = i + 1
                break
        else:
            failures.append("no line matches %r after line %d" % (exp, pos))
    return failures


# The sessions, in order: gdb_test.gdb (it ends with a detach); raw_session's packets (it ends with the connection
# closed, the CPU stopped); gdb_crash.gdb (gdb dies at a breakpoint); then, after the CPU has run a while,
# gdb_after_crash.gdb (it ends with kill) and gdb_after_kill.gdb. The server must take each next gdb.
SESSIONS = ["gdb_test.gdb", "raw", "gdb_crash.gdb", "sleep 2", "gdb_after_crash.gdb", "gdb_after_kill.gdb"]


def gdb_session(gdb, script, elf, env):
    """gdb in batch mode on a script: what it printed, stdout and stderr in one pipe (as gdb interleaves them)."""
    try:
        run = subprocess.run([gdb, "-q", "-nx", "-batch", "-x", os.path.join(HERE, script), elf], cwd=HERE, env=env,
                             stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=300)
        return run.stdout
    except subprocess.TimeoutExpired as e:
        return (e.stdout or b"").decode() + "\n(gdb timed out)\n"


def raw_session():
    """Packets of the remote protocol, without gdb: what the server answers to each, as "packet -> reply" lines."""
    lines = []
    sock = socket.create_connection(("localhost", PORT), timeout=60)
    sock.settimeout(30)
    received = b""

    def reply():
        nonlocal received
        while True:
            m = re.search(rb"\$([^#]*)#[0-9a-fA-F]{2}", received)
            if m:
                received = received[m.end():]
                sock.sendall(b"+")
                return m.group(1).decode("latin-1")
            chunk = sock.recv(4096)
            if not chunk:
                return "(connection closed)"
            received += chunk

    def send(data):
        sock.sendall(("$%s#%02x" % (data, sum(data.encode("latin-1")) & 0xff)).encode("latin-1"))

    def ask(data):
        send(data)
        lines.append("%s -> %s" % (data, reply() or "(empty)"))

    try:
        ask("?")
        ask("qSupported:multiprocess+;swbreak+;hwbreak+;qRelocInsn+")
        ask("vCont?")
        ask("Zx")                  # malformed: an error, and the session goes on
        ask("mzz,4")
        ask("Z0,zz,4")
        ask("qNoSuchQuery")        # unknown: the empty reply
        ask("xNoSuchPacket")
        ask("p1041")
        send("c")
        time.sleep(0.5)
        sock.sendall(b"\x03")      # Ctrl-C while the CPU runs
        lines.append("c, Ctrl-C -> %s" % reply())
        ask("p1041")
    except (OSError, socket.timeout) as e:
        lines.append("(raw session: %s)" % e)
    sock.close()                   # no D: the server must let the CPU run on and wait for the next gdb
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description="The GDB server's integration test")
    parser.add_argument("--jar", required=True, help="logisim-evolution-ucd-*-all.jar")
    parser.add_argument("--gdb", default="gdb-multiarch")
    parser.add_argument("--objcopy", default="riscv64-unknown-elf-objcopy")
    args = parser.parse_args()
    if not os.path.isfile(args.jar):
        sys.exit("run_test.py: no jar at %r (build it with ./gradlew shadowJar)" % args.jar)

    os.makedirs(BUILD, exist_ok=True)
    elf = os.path.join(HERE, "prog.elf")
    circuit = os.path.join(BUILD, "computer-test.circ")
    make_circuit(os.path.join(HERE, "computer.circ"), circuit, rom_contents(elf, args.objcopy))

    # no window: headless Java, and no display to open one on
    env = dict(os.environ)
    env.pop("DISPLAY", None)
    env.pop("WAYLAND_DISPLAY", None)
    log = open(os.path.join(BUILD, "logisim.log"), "w")
    logisim = subprocess.Popen(
        ["java", "-Djava.awt.headless=true", "-jar", os.path.abspath(args.jar), circuit, "--tty", "tty"],
        cwd=BUILD, env=env, stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT)
    output = ""
    try:
        for session in SESSIONS:
            output += "----- session %s\n" % session
            if session == "raw":
                output += raw_session()
            elif session.startswith("sleep "):
                time.sleep(float(session.split()[1]))
            else:
                output += gdb_session(args.gdb, session, elf, env)
    finally:
        logisim.terminate()
        try:
            logisim.wait(10)
        except subprocess.TimeoutExpired:
            logisim.kill()
            logisim.wait()
        log.close()
    open(os.path.join(BUILD, "gdb.log"), "w").write(output)

    failures = compare(output, open(os.path.join(HERE, "expected.txt")).readlines())
    console = open(os.path.join(BUILD, "logisim.log")).read()
    if "TCP connection" in console:
        failures.append("the GDB server wrote to the simulated console: see build/logisim.log")
    for f in failures:
        print("FAIL:", f)
    print("gdb's output is in build/gdb.log;", "PASS" if not failures else "%d failures" % len(failures))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
