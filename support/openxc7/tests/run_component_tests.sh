#!/bin/bash
# Differential tests of Logisim's HDL generators: for each list in specs/, the components are placed in a circuit
# with a pin on every port, and random inputs (and clock edges) go both through Logisim's simulator and through the
# HDL that Logisim generates for them; every output must be the same. Then a unit test of the UART TTY and
# Keyboard.
#
#   support/openxc7/tests/run_component_tests.sh [--vhdl] [spec ...]      default: arith comb seq io
#
# The Verilog runs in Icarus Verilog (iverilog, vvp). With --vhdl, the VHDL that Logisim generates is tested
# instead, in GHDL (ghdl on the PATH, $GHDL, or ~/openxc7/ghdl/bin/ghdl).
# Needs the built jar (./gradlew shadowJar), a JDK (javac) and python3. Results go to support/openxc7/tests/build/.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../../.." && pwd)"
JAR="$(ls "$REPO"/build/libs/logisim-evolution-*-all.jar 2>/dev/null | head -n 1)"
[ -n "$JAR" ] || { echo "build Logisim first: ./gradlew shadowJar" >&2; exit 1; }
LANGUAGE=verilog
if [ "${1:-}" = --vhdl ]; then
  LANGUAGE=vhdl
  shift
fi
OUT="$HERE/build"
BOARD=DIGILENT_CMOD_A7_35T
mkdir -p "$OUT/classes"
javac -nowarn -d "$OUT/classes" -cp "$JAR" "$HERE"/harness/*/*.java

# the tests keep their own Logisim preferences (FPGA workspace, HDL type)
if [ "$LANGUAGE" = vhdl ]; then
  GHDL="${GHDL:-$(command -v ghdl || echo "$HOME/openxc7/ghdl/bin/ghdl")}"
  [ -x "$GHDL" ] || { echo "GHDL not found (put ghdl on the PATH or set GHDL)" >&2; exit 1; }
  PREFS="$OUT/prefs_vhdl.properties"
  WS="$OUT/ws_vhdl"
  # with openXC7, Logisim would switch to Verilog: generate for the Vivado flow instead (no tools needed)
  printf '/com/cburch/logisim|FPGAWorkspace=%s\n/com/cburch/logisim|hdlType=VHDL\n' "$WS" > "$PREFS"
  printf '/com/cburch/logisim|OpenXc7ForXilinx=false\n' >> "$PREFS"
else
  PREFS="$OUT/prefs.properties"
  WS="$OUT/ws"
  printf '/com/cburch/logisim|FPGAWorkspace=%s\n/com/cburch/logisim|hdlType=Verilog\n' "$WS" > "$PREFS"
fi

logisim() {
  java -Djava.util.prefs.PreferencesFactory=isoprefs.FilePrefsFactory "-Disoprefs.file=$PREFS" \
    -Djava.awt.headless=true -cp "$OUT/classes:$JAR" "$@"
}

ghdl_run() {   # <dir> <top unit> <files ...>: analyse in dependency order, elaborate and run in GHDL
  local dir="$1" top="$2"
  shift 2
  (cd "$dir" && rm -rf ghdlwork && mkdir ghdlwork \
    && "$GHDL" -i --std=08 -frelaxed --workdir=ghdlwork "$@" \
    && "$GHDL" -m --std=08 -frelaxed --workdir=ghdlwork "$top" > ghdl_make.log 2>&1 \
    && "$GHDL" -r --std=08 -frelaxed --workdir=ghdlwork "$top" --ieee-asserts=disable) \
    || { echo "GHDL failed, see $dir/ghdl_make.log"; return 1; }
}

failures=0
specs=("$@")
[ ${#specs[@]} -gt 0 ] || specs=(arith comb seq io)
for spec in "${specs[@]}"; do
  if [ "$spec" = io ]; then
    echo "== TTY and Keyboard (UART) unit test, $LANGUAGE"
    logisim lstest.HdlDiff build "$HERE/template.circ" "$HERE/specs/io.txt" "$OUT/io.circ" > /dev/null
    logisim lstest.AutoMap "$OUT/io.circ" main "$BOARD" "$OUT/io_mapped.circ" > "$OUT/io_automap.log" 2>&1
    rm -rf "$WS/io_mapped"
    logisim com.cburch.logisim.Main --test-fpga "$OUT/io_mapped.circ" main "$BOARD" HDLONLY 1000000 \
      > "$OUT/io_hdl_$LANGUAGE.log" 2>&1
    if [ "$LANGUAGE" = vhdl ]; then
      hdl="$WS/io_mapped/main/vhdl/io"
      mkdir -p "$OUT/io_vhdl"
      { ghdl_run "$OUT/io_vhdl" tb_io "$HERE/tb_io.vhd" "$hdl"/TTY_*.vhd "$hdl"/Keyboard_*.vhd || true; } \
        | sed -n 's/.*(report [a-z]*): //p' | tee "$OUT/io_vhdl.txt"
      grep -q '^0 error' "$OUT/io_vhdl.txt" || failures=$((failures + 1))
    else
      hdl="$WS/io_mapped/main/verilog/io"
      (cd "$OUT" && iverilog -g2005 -o tb_io.vvp "$HERE/tb_io.v" "$hdl/TTY.v" "$hdl/Keyboard.v" \
        && vvp -n tb_io.vvp | grep -v '\$finish') | tee "$OUT/io.txt"
      grep -q '^0 error' "$OUT/io.txt" || failures=$((failures + 1))
    fi
    continue
  fi
  echo "== $spec, $LANGUAGE"
  circ="$OUT/$spec.circ"
  logisim lstest.HdlDiff build "$HERE/template.circ" "$HERE/specs/$spec.txt" "$circ"
  rm -rf "$WS/$spec"
  logisim lstest.HdlDiff hdl "$circ" "$BOARD" > "$OUT/hdl_${spec}_$LANGUAGE.log" 2>&1 \
    || { echo "HDL generation failed, see $OUT/hdl_${spec}_$LANGUAGE.log"; failures=$((failures + 1)); continue; }
  if [ "$LANGUAGE" = vhdl ]; then
    run="$OUT/run_${spec}_vhdl"
    rm -rf "$run"
    hdl="$WS/$spec/main/vhdl"
    logisim lstest.HdlDiff vectors "$circ" 4000 1 "$run" "$hdl/circuit/dut_entity.vhd"
    logisim lstest.HdlDiff tbvhdl "$circ" "$run" "$hdl/circuit/dut_entity.vhd"
    ghdl_run "$run" tb tb.vhd $(ls "$hdl"/*/*.vhd | grep -v /toplevel/) > /dev/null \
      || { failures=$((failures + 1)); continue; }
    python3 "$HERE/compare.py" "$run" actual_vhdl.txt || failures=$((failures + 1))
  else
    run="$OUT/run_$spec"
    rm -rf "$run"
    hdl="$WS/$spec/main/verilog"
    logisim lstest.HdlDiff vectors "$circ" 4000 1 "$run" "$hdl/circuit/dut.v"
    (cd "$run" && iverilog -g2005 -o sim.vvp tb.v $(ls "$hdl"/*/*.v | grep -v /toplevel/) && vvp -n sim.vvp > /dev/null)
    python3 "$HERE/compare.py" "$run" || failures=$((failures + 1))
  fi
done
echo
[ "$failures" -eq 0 ] && echo "all passed" || { echo "$failures failed"; exit 1; }
