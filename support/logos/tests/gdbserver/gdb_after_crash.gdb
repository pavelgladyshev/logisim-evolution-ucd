# The third session, after the crash: the CPU ran on (counter has grown from 0), the dead session's breakpoint
# in square is gone (the first stop is in do_ecall, after 8 calls of square), and kill (the packet k) ends it.
set pagination off
set confirm off
set tcp connect-timeout 60
target remote :3340
p counter > 0
break do_ecall
continue
kill
