#!/bin/bash
# Build the open-source openXC7 toolchain for Logisim's "openXC7" FPGA flow (Xilinx 7-series, e.g. the
# Digilent Cmod A7-35T): nextpnr-xilinx with a chip database, Project X-Ray's fasm2frames / xc7frames2bit,
# plus links to Yosys and openFPGALoader - all in one folder, <dir>/bin, which is the tool path Logisim uses
# (Preferences > Software > openXC7; ~/openxc7/bin is found automatically).
#
#   support/openxc7/setup_toolchain.sh [dir]            default dir: ~/openxc7   (about 0.5 GB, a few minutes)
#
# Prerequisites
#   macOS (Homebrew):   brew install yosys boost eigen openfpgaloader cmake python@3.13 git
#   Kubuntu/Ubuntu 24.04 (Yosys is built here, as Ubuntu's 0.33 is too old):
#     sudo apt install git cmake build-essential pkg-config python3 python3-venv openfpgaloader \
#                      libboost-filesystem-dev libboost-thread-dev libboost-program-options-dev \
#                      libboost-iostreams-dev libboost-system-dev libeigen3-dev \
#                      bison flex gawk libreadline-dev tcl-dev libffi-dev zlib1g-dev openjdk-21-jre
#     then unplug and plug in the board once (the openfpgaloader package's udev rule gives access to it)
#
# Running it again only does what is missing. Other parts than the Cmod A7-35T's: <dir>/bin/openxc7-chipdb <part>.
set -euo pipefail

NEXTPNR_TAG=0.9.5                                          # openXC7/nextpnr-xilinx
# A chip database is tied to the nextpnr that reads it. One downloaded rather than made here must come
# from a release built against this revision, or the bitstream is wrong rather than the run failing.
PRJXRAY_SHA=c9f02d8576042325425824647ab5555b1bc77833      # f4pga/prjxray
YOSYS_TAG=v0.68                                            # built when the system has none or an old one
YOSYS_MIN=40                                               # oldest system Yosys used: 0.40
DEFAULT_PART=xc7a35tcpg236-1                              # Digilent Cmod A7-35T

DIR="${1:-$HOME/openxc7}"
mkdir -p "$DIR"
DIR="$(cd "$DIR" && pwd)"
JOBS="$(getconf _NPROCESSORS_ONLN)"

find_tool() {   # the first $1 on PATH that is not one of our own wrappers in <dir>/bin
  local d IFS=:
  for d in $PATH; do
    [ "$d" = "$DIR/bin" ] || [ "$d/$1" -ef "$DIR/bin/$1" ] && continue
    [ -f "$d/$1" ] && [ -x "$d/$1" ] && { echo "$d/$1"; return 0; }
  done
  return 1
}

yosys_ok() {     # a system Yosys that is recent enough
  local v
  v="$("$1" -V 2>/dev/null | sed -n 's/^Yosys 0\.\([0-9]*\).*/\1/p')"
  [ -n "$v" ] && [ "$v" -ge "$YOSYS_MIN" ]
}
SYSTEM_YOSYS="$(find_tool yosys || true)"
[ -n "$SYSTEM_YOSYS" ] && ! yosys_ok "$SYSTEM_YOSYS" && SYSTEM_YOSYS=""

missing=()
for tool in git cmake python3 openFPGALoader; do
  find_tool "$tool" > /dev/null || missing+=("$tool")
done
[ -z "$SYSTEM_YOSYS" ] && [ "$(uname)" = Darwin ] && missing+=("yosys (0.$YOSYS_MIN or newer)")
if [ ${#missing[@]} -gt 0 ]; then
  echo "missing: ${missing[*]} - install the prerequisites listed at the top of this script first." >&2
  exit 1
fi

if [[ "$DIR" == *" "* ]]; then          # some build steps dislike spaces in paths: build through a symlink
  link="${TMPDIR:-/tmp}"; link="${link%/}/openxc7-build-$$"
  ln -sfn "$DIR" "$link"; cd "$link"
  trap 'rm -f "$link"' EXIT
else
  cd "$DIR"
fi

if [ -z "$SYSTEM_YOSYS" ] && [ ! -x yosys/bin/yosys ]; then
  echo "== Yosys $YOSYS_TAG (the system has none, or one older than 0.$YOSYS_MIN)"
  [ -d yosys-src ] || git clone -q --depth 1 --branch "$YOSYS_TAG" --recurse-submodules --shallow-submodules \
    https://github.com/YosysHQ/yosys.git yosys-src
  # Yosys builds with CMake from 0.6x on (it had a Makefile with a config-gcc target before). A static
  # libyosys keeps the installed yosys self-contained: its CMake sets no RPATH, so a shared one in
  # <dir>/yosys/lib would not be found.
  cmake -S yosys-src -B yosys-src/build -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=OFF \
    -DCMAKE_INSTALL_PREFIX="$PWD/yosys" > yosys-cmake.log
  cmake --build yosys-src/build -j"$JOBS" > yosys-build.log 2>&1
  cmake --install yosys-src/build --strip > yosys-install.log 2>&1
fi

echo "== nextpnr-xilinx $NEXTPNR_TAG"
[ -d nextpnr-xilinx ] || git clone -q --depth 1 --branch "$NEXTPNR_TAG" https://github.com/openXC7/nextpnr-xilinx.git
git -C nextpnr-xilinx -c advice.detachedHead=false submodule update -q --init --depth 1 xilinx/external/nextpnr-xilinx-meta
if [ ! -x nextpnr-xilinx/build/nextpnr-xilinx ]; then
  cmake -S nextpnr-xilinx -B nextpnr-xilinx/build -DARCH=xilinx -DBUILD_PYTHON=OFF -DBUILD_GUI=OFF -DUSE_OPENMP=OFF \
    -DCMAKE_BUILD_TYPE=Release > nextpnr-cmake.log
  cmake --build nextpnr-xilinx/build -j"$JOBS" > nextpnr-build.log
fi

echo "== Project X-Ray database (fetched part by part)"
DB=nextpnr-xilinx/xilinx/external/prjxray-db
if [ ! -d "$DB/.git" ]; then
  DB_SHA="$(git -C nextpnr-xilinx ls-tree HEAD xilinx/external/prjxray-db | awk '{print $3}')"
  rm -rf "$DB"; mkdir -p "$DB"
  git -C "$DB" init -q
  git -C "$DB" remote add origin https://github.com/SymbiFlow/prjxray-db.git
  git -C "$DB" config core.sparseCheckout true
  git -C "$DB" config remote.origin.promisor true
  git -C "$DB" config remote.origin.partialclonefilter blob:none
  printf '%s\n' '/artix7/*' '!/artix7/*/' '/artix7/timings/' '/artix7/mapping/' > "$DB/.git/info/sparse-checkout"
  git -C "$DB" fetch -q --depth 1 --filter=blob:none origin "$DB_SHA"
  git -C "$DB" -c advice.detachedHead=false checkout -q FETCH_HEAD
fi
ln -sfn nextpnr-xilinx/xilinx/external/prjxray-db prjxray-db

echo "== Project X-Ray tools"
if [ ! -d prjxray ]; then
  mkdir prjxray
  git -C prjxray init -q
  git -C prjxray remote add origin https://github.com/f4pga/prjxray.git
  git -C prjxray fetch -q --depth 1 origin "$PRJXRAY_SHA"
  git -C prjxray -c advice.detachedHead=false checkout -q FETCH_HEAD
fi
if [ ! -x prjxray/build/tools/xc7frames2bit ]; then
  git -C prjxray -c advice.detachedHead=false submodule update -q --init --depth 1 third_party/abseil-cpp \
    third_party/cctz third_party/gflags third_party/googletest third_party/yaml-cpp third_party/sanitizers-cmake \
    third_party/fasm
  # GCC 13 and later no longer include <cstdint> on the way (openXC7's own prjxray fork has the same fix)
  python3 - prjxray/lib/include/prjxray/memory_mapped_file.h <<'PY'
import sys
path = sys.argv[1]
text = open(path).read()
if "<cstdint>" not in text:
    open(path, "w").write(text.replace("#include <memory>", "#include <cstdint>\n#include <memory>", 1))
PY
  cmake -S prjxray -B prjxray/build -DCMAKE_BUILD_TYPE=Release -DCMAKE_POLICY_VERSION_MINIMUM=3.5 > prjxray-cmake.log
  cmake --build prjxray/build --target xc7frames2bit -j"$JOBS" > prjxray-build.log
fi

echo "== Python environment for fasm2frames and the chip database export"
[ -x pyenv/bin/python ] || python3 -m venv pyenv
pyenv/bin/pip install -q --disable-pip-version-check textx simplejson pyyaml intervaltree

echo "== bin/ (the tool path for Logisim)"
mkdir -p bin chipdb
# Yosys and openFPGALoader from the system, through wrappers: through a symlink, Yosys on macOS would look
# for its share/ directory next to the link. (rm first: never write through an old symlink.)
for tool in yosys openFPGALoader; do
  if [ "$tool" = yosys ] && [ -z "$SYSTEM_YOSYS" ]; then real="$PWD/yosys/bin/yosys"; else real="$(find_tool "$tool")"; fi
  rm -f "bin/$tool"
  printf '#!/bin/sh\nexec "%s" "$@"\n' "$real" > "bin/$tool"
  chmod +x "bin/$tool"
done
ln -sfn ../nextpnr-xilinx/build/nextpnr-xilinx bin/nextpnr-xilinx
ln -sfn ../prjxray/build/tools/xc7frames2bit bin/xc7frames2bit
cat > bin/fasm2frames <<'EOF'
#!/bin/sh
# Project X-Ray fasm2frames with its Python environment
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PYTHONPATH="$ROOT/prjxray:$ROOT/prjxray/third_party/fasm${PYTHONPATH:+:$PYTHONPATH}" \
  exec "$ROOT/pyenv/bin/python" -W ignore "$ROOT/prjxray/utils/fasm2frames.py" "$@"
EOF
cat > bin/openxc7-chipdb <<'EOF'
#!/bin/sh
# Make the nextpnr-xilinx chip database for a part, e.g.: openxc7-chipdb xc7a35tcpg236-1
# (fetches that part's Project X-Ray data first; takes a few minutes)
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PART="$1"
[ -n "$PART" ] || { echo "usage: $0 <part, e.g. xc7a35tcpg236-1>" >&2; exit 1; }
case "$PART" in
  xc7a*) FAMILY=artix7 ;; xc7k*) FAMILY=kintex7 ;; xc7s*) FAMILY=spartan7 ;; xc7z*) FAMILY=zynq7 ;;
  *) echo "unsupported part $PART" >&2; exit 1 ;;
esac
DB="$ROOT/nextpnr-xilinx/xilinx/external/prjxray-db"
grep -q "^/$FAMILY/\*\$" "$DB/.git/info/sparse-checkout" || \
  printf '%s\n' "/$FAMILY/*" "!/$FAMILY/*/" "/$FAMILY/timings/" "/$FAMILY/mapping/" >> "$DB/.git/info/sparse-checkout"
git -C "$DB" read-tree -mu HEAD
FABRIC="$("$ROOT/pyenv/bin/python" - "$DB/$FAMILY/mapping" "$PART" <<'PY'
import sys, yaml
mapping, part = sys.argv[1], sys.argv[2]
device = yaml.safe_load(open(mapping + "/parts.yaml"))[part]["device"]
print(yaml.safe_load(open(mapping + "/devices.yaml"))[device]["fabric"])
PY
)"
for d in "/$FAMILY/$FABRIC/" "/$FAMILY/$PART/"; do
  grep -q "^$d\$" "$DB/.git/info/sparse-checkout" || echo "$d" >> "$DB/.git/info/sparse-checkout"
done
git -C "$DB" read-tree -mu HEAD
mkdir -p "$ROOT/chipdb"
"$ROOT/pyenv/bin/python" "$ROOT/nextpnr-xilinx/xilinx/python/bbaexport.py" --device "$PART" \
  --xray "$DB/$FAMILY" --bba "$ROOT/chipdb/$PART.bba"
"$ROOT/nextpnr-xilinx/build/bbasm" -l "$ROOT/chipdb/$PART.bba" "$ROOT/chipdb/$PART.bin"
rm -f "$ROOT/chipdb/$PART.bba"
echo "made $ROOT/chipdb/$PART.bin"
EOF
chmod +x bin/fasm2frames bin/openxc7-chipdb

echo "== chip database for $DEFAULT_PART"
[ -f "chipdb/$DEFAULT_PART.bin" ] || bin/openxc7-chipdb "$DEFAULT_PART"

echo "== check"
bin/nextpnr-xilinx --version
bin/yosys -V
bin/openFPGALoader --Version 2>&1 | head -1 || true
bin/fasm2frames --help > /dev/null && echo "fasm2frames ok"
ls -la "chipdb/$DEFAULT_PART.bin"
echo
echo "toolchain ready: in Logisim, Preferences > Software > openXC7 tool path = $DIR/bin/"
[ "$(uname)" = Linux ] && echo "Linux: unplug and plug in the board once, so that it gets the openfpgaloader udev rule"
exit 0
