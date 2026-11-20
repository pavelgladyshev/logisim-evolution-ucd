# tb_debug's gdb session: software and hardware breakpoints, steps, variables, memory
set pagination off
set confirm off
target extended-remote :3333
echo \n=== where it stopped\n
info registers pc
echo \n=== software breakpoint at square, continue\n
break square
continue
bt
print x
print counter
echo \n=== finish, then a step at source level\n
finish
next
info line *$pc
echo \n=== variables written by gdb\n
delete
set var counter = 100
print counter
echo \n=== hardware breakpoint at square\n
hbreak square
continue
print x
info breakpoints
delete
echo \n=== stepi twice\n
stepi
info registers pc
stepi
info registers pc
echo \n=== memory: squares and the code at main\n
x/8dw &squares
x/3i main
echo \n=== registers\n
info registers ra sp a0 a1
detach
echo \n=== DONE\n
