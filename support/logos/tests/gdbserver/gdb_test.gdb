# The GDB server's test session (run_test.py runs it, and checks its output against expected.txt)
set pagination off
set confirm off
set tcp connect-timeout 60
target remote :3340
echo \n=== stopped at the reset address\n
info registers pc
p $priv
echo \n=== info registers\n
info registers
echo \n=== info registers csr\n
info registers csr
echo \n=== an unimplemented CSR, mhartid (65 + 0xf14 = 0xf55), is unavailable\n
maint packet pf55
echo \n=== in the trap handler, after the ecall in do_ecall\n
break trap_handler
continue
p $mepc == (unsigned) &do_ecall
p/x $mepc
p $mcause
p/x $mtval
p/x $mstatus
p/x $mie
p/x $mip
p $mtvec == (unsigned) &trap_handler
p/x $mscratch
p $priv
delete
echo \n=== registers written by gdb\n
set $mscratch = 0x1234
p/x $mscratch
set $a0 = 77
p $a0
python
try:
    gdb.execute("set $priv = 1")
    print("priv write: accepted")
except gdb.error as e:
    print("priv write: refused")
end
p $priv
echo \n=== break, continue, finish\n
break square
continue
p x
finish
delete
echo \n=== stepi\n
set $before = $pc
stepi
p $pc - $before
echo \n=== memory\n
x/4xw &squares
x/s greeting
echo \n=== a device register, the Timer's mtimecmp: x/xw is lw, set {int} is sw\n
x/xw 0x02004000
set {int}0x02004000 = 0x89abcdef
x/xw 0x02004000
x/4xb 0x02004000
echo \n=== watch counter (a write watchpoint), twice; then a read watchpoint and an access watchpoint\n
watch counter
continue
continue
delete
rwatch counter
continue
delete
awatch squares[2]
continue
delete
echo \n=== monitor help, monitor reset\n
monitor help
monitor reset
maintenance flush register-cache
p $pc
p $priv
p $mcause
echo \n=== detach\n
detach
echo \n=== DONE\n
