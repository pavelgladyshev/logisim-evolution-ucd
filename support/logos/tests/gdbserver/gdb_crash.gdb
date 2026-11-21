# The second session: gdb stops in square, sets counter to 0 and dies there (SIGKILL), without a detach: its
# connection closes with the CPU stopped and a breakpoint in.
set pagination off
set confirm off
set tcp connect-timeout 60
target remote :3340
break square
continue
set var counter = 0
p counter
python
import os, signal
gdb.flush()
os.kill(os.getpid(), signal.SIGKILL)
end
