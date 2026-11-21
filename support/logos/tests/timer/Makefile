# make: riscvTimer.v against records of the Java Timer, in Icarus Verilog (README.md).
# The records and the logs go to build/, which git ignores.
REPO    = ../../../..
HDL     = $(REPO)/src/main/resources/resources/logisim/hdl/riscv
# Logisim's jar: the newest that ./gradlew shadowJar has built
JAR    ?= $(shell ls -t $(REPO)/build/libs/logisim-evolution-*-all.jar 2>/dev/null | head -n 1)
STEPS  ?= 300000
SEED   ?= 1

# name:mtimecmp address:mtime address. The default addresses; high words past the top of the address space and
# at 0x80000000; mtime in the word after mtimecmp, where it hides mtimecmp's high word; mtimecmp in the word
# after mtime; and both at one address, which is mtimecmp's. Each record has its own seed, from SEED on.
RECORDS = default:0x02004000:0x0200bff8 wrap:0xfffffffc:0x7ffffffc after:0x1000:0x1004 before:0x1004:0x1000 \
          same:0x2000:0x2000

test: build/TimerTrace.class
	@fail=0; seed=$(SEED); for record in $(RECORDS); do \
	   name=$${record%%:*}; addrs=$${record#*:}; cmp=$${addrs%%:*}; time=$${addrs#*:}; \
	   java -Djava.awt.headless=true -cp build:$(JAR) TimerTrace $(STEPS) $$seed build/$$name.trace $$cmp $$time \
	      > build/$$name.txt || exit 1; \
	   head -n 1 build/$$name.txt; \
	   seed=`expr $$seed + 1`; \
	   for idle in 0 1 7; do \
	      iverilog -g2005 -DTRACE="\"build/$$name.trace\"" -DIDLE=$$idle \
	         -DMTIMECMP_ADDR="32'h$${cmp#0x}" -DMTIME_ADDR="32'h$${time#0x}" \
	         -o build/tb tb_timer_replay.v $(HDL)/riscvTimer.v || exit 1; \
	      vvp -n build/tb | grep -v finish | tee build/$$name.$$idle.log | sed "s/^/   idle $$idle: /"; \
	      grep -q ', 0 differences$$' build/$$name.$$idle.log || fail=1; \
	   done; \
	done; \
	if [ $$fail = 0 ]; then echo "all records agree"; else echo "DIFFERENCES"; exit 1; fi

build/TimerTrace.class: TimerTrace.java $(JAR)
	@test -n "$(JAR)" || { echo "build Logisim first: ./gradlew shadowJar (or give JAR=)"; exit 1; }
	mkdir -p build
	javac -nowarn -d build -cp $(JAR) TimerTrace.java

.PHONY: test
