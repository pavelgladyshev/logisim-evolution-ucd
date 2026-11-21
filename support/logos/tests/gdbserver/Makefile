# make test: the program in the circuit's ROM, the simulator without a window, gdb_test.gdb in gdb-multiarch, and
# its output checked against expected.txt (run_test.py). The jar is Logisim's own, built by ./gradlew shadowJar.
CROSS ?= riscv64-unknown-elf-
GDB   ?= gdb-multiarch
LOGISIM_JAR ?= $(firstword $(wildcard ../../../../build/libs/logisim-evolution-ucd-*-all.jar))

test: prog.elf
	python3 run_test.py --jar "$(LOGISIM_JAR)" --gdb "$(GDB)" --objcopy $(CROSS)objcopy

prog.elf: start.S prog.c prog.ld
	$(CROSS)gcc -march=rv32im_zicsr -mabi=ilp32 -Og -g -nostdlib -ffreestanding -T prog.ld -o $@ start.S prog.c

clean:
	rm -rf prog.elf build

.PHONY: test clean
