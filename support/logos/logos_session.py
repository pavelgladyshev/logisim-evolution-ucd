#!/usr/bin/env python3
"""Boot LogOS in Logisim without its window and type commands at its shell; print the session.

    support/logos/logos_session.py logisim.jar dir [command ...]

dir holds computer.circ and block_storage.bin. Logisim runs there (-tty raw), and the disk image it changes is
that copy, so run it on copies. Each command is typed after the shell's prompt ("$ ") appears, and the session
ends after the last prompt. The last line says whether every prompt came, and when.

To check a change against the original circuit, run both on fresh copies of the disk image and compare the
sessions and the disk images, e.g. with LogOS's test programs: fs_test pipe_test redir_test fd_test proc_test
link_test shm_test sem_test stress_test script_test (each prints "Results: N passed, M failed").
"""
import os
import select
import subprocess
import sys
import time

jar, where, commands = sys.argv[1], sys.argv[2], sys.argv[3:]
proc = subprocess.Popen(['java', '-Djava.awt.headless=true', '-jar', os.path.abspath(jar), 'computer.circ',
                         '-tty', 'raw'], cwd=where, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                        stderr=subprocess.DEVNULL)
out = bytearray()
start = time.time()


def until_prompt(limit):
    while time.time() < limit:
        ready, _, _ = select.select([proc.stdout], [], [], 1.0)
        if ready:
            chunk = os.read(proc.stdout.fileno(), 4096)
            if not chunk:
                return False
            out.extend(chunk)
            if out.endswith(b'$ '):
                return True
    return False


ok = until_prompt(start + 900)
marks = [f'boot {time.time() - start:.0f}s']
for cmd in commands:
    if not ok:
        break
    proc.stdin.write(cmd.encode() + b'\r')
    proc.stdin.flush()
    ok = until_prompt(time.time() + 900)
    marks.append(f'{cmd!r} {time.time() - start:.0f}s')
proc.kill()
sys.stdout.write(out.decode('latin-1'))
sys.stdout.write('\n--- ' + ('ok' if ok else 'NO PROMPT') + ': ' + ', '.join(marks) + '\n')
