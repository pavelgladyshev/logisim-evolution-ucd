# The fourth session, after kill: gdb attaches again, and detaches
set pagination off
set confirm off
set tcp connect-timeout 60
target remote :3340
p $priv
p counter > 0
detach
echo \n=== DONE\n
