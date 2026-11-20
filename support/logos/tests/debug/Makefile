# make sim: the CPU with DEBUG in Icarus Verilog, OpenOCD through remote_bitbang, and gdb_test.gdb
HDL     = ../../../../src/main/resources/resources/logisim/hdl/riscv
CROSS  ?= riscv64-unknown-elf-
GDB    ?= gdb-multiarch

sim: tb rbb_vpi.vpi prog.hex
	vvp -n -M . -mrbb_vpi tb > vvp.log 2>&1 & sleep 1; \
	openocd -f sim.cfg -c init > openocd.log 2>&1 & sleep 3; \
	$(GDB) -q -batch -x gdb_test.gdb prog.elf; status=$$?; kill %2 2>/dev/null; exit $$status

tb: tb_debug.v gw_jtag_model.v $(HDL)/riscvCpu.v $(HDL)/riscvDebug.v
	iverilog -g2012 -o $@ $^

rbb_vpi.vpi: rbb_vpi.c
	iverilog-vpi $<

prog.elf: start.S prog.c prog.ld
	$(CROSS)gcc -march=rv32im -mabi=ilp32 -Og -g -nostdlib -ffreestanding -T prog.ld -o $@ start.S prog.c

prog.hex: prog.elf
	$(CROSS)objcopy -O binary $< prog.bin
	python3 -c "import struct; d = open('prog.bin', 'rb').read(); d += bytes(-len(d) % 4); \
	open('$@', 'w').write(''.join('%08x\n' % w for w in struct.unpack('<%dI' % (len(d) // 4), d)))"

clean:
	rm -f tb rbb_vpi.vpi rbb_vpi.o prog.elf prog.bin prog.hex vvp.log openocd.log

.PHONY: sim clean
