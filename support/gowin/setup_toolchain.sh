#!/bin/bash
# Build the open-source toolchain for Logisim's Gowin FPGA flow (Sipeed Tang Nano boards): nextpnr-himbaechel with
# its Gowin chip databases and Project Apicula's gowin_pack, plus links to Yosys and openFPGALoader - all in one
# folder, <dir>/bin, which is the tool path Logisim uses (Preferences > Software > Gowin; ~/gowin/bin is found
# automatically).
#
#   support/gowin/setup_toolchain.sh [dir]            default dir: ~/gowin   (about 0.5 GB, a few minutes)
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
# Running it again only does what is missing. The chips it makes databases for are the Tang Nano 9K's and 20K's;
# for others, name them all: GOWIN_DEVICES="GW1N-9C;GW2A-18C;GW1NZ-1" support/gowin/setup_toolchain.sh
set -euo pipefail

NEXTPNR_SHA=7ba06cae8698fb2d6e96b77927d1f41a463c29fd       # YosysHQ/nextpnr
# The chip databases are made from Apicula's data when nextpnr is built, and gowin_pack must read what that
# nextpnr writes: the two come from the same Apicula, and both stay at the versions tested together.
APICULA_VERSION=0.34
GOWIN_DEVICES="${GOWIN_DEVICES:-GW1N-9C;GW2A-18C}"          # Tang Nano 9K, Tang Nano 20K
YOSYS_TAG=v0.68                                            # built when the system has none or an old one
YOSYS_MIN=40                                               # oldest system Yosys used: 0.40

DIR="${1:-$HOME/gowin}"
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
# the one the openXC7 setup builds on Linux will do as well
[ -z "$SYSTEM_YOSYS" ] && yosys_ok "$HOME/openxc7/yosys/bin/yosys" && SYSTEM_YOSYS="$HOME/openxc7/yosys/bin/yosys"

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
  link="${TMPDIR:-/tmp}"; link="${link%/}/gowin-build-$$"
  ln -sfn "$DIR" "$link"; cd "$link"
  trap 'rm -f "$link"' EXIT
else
  cd "$DIR"
fi

if [ -z "$SYSTEM_YOSYS" ] && [ ! -x yosys/bin/yosys ]; then
  echo "== Yosys $YOSYS_TAG (the system has none, or one older than 0.$YOSYS_MIN)"
  [ -d yosys-src ] || git clone -q --depth 1 --branch "$YOSYS_TAG" --recurse-submodules --shallow-submodules \
    https://github.com/YosysHQ/yosys.git yosys-src
  # a static libyosys keeps the installed yosys self-contained (its CMake sets no RPATH)
  cmake -S yosys-src -B yosys-src/build -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=OFF \
    -DCMAKE_INSTALL_PREFIX="$PWD/yosys" > yosys-cmake.log
  cmake --build yosys-src/build -j"$JOBS" > yosys-build.log 2>&1
  cmake --install yosys-src/build --strip > yosys-install.log 2>&1
fi

echo "== Project Apicula $APICULA_VERSION (gowin_pack, and the chip data nextpnr is built from)"
[ -x pyenv/bin/python ] || python3 -m venv pyenv
pyenv/bin/pip install -q --disable-pip-version-check "apycula==$APICULA_VERSION"

echo "== nextpnr-himbaechel for $GOWIN_DEVICES"
if [ ! -d nextpnr-src ]; then
  mkdir nextpnr-src
  git -C nextpnr-src init -q
  git -C nextpnr-src remote add origin https://github.com/YosysHQ/nextpnr.git
  git -C nextpnr-src fetch -q --depth 1 origin "$NEXTPNR_SHA"
  git -C nextpnr-src -c advice.detachedHead=false checkout -q FETCH_HEAD
fi
built=yes
IFS=';' read -r -a devices <<< "$GOWIN_DEVICES"
for device in "${devices[@]}"; do
  [ -f "share/nextpnr/himbaechel/gowin/chipdb-$device.bin" ] || built=no
done
if [ ! -x bin/nextpnr-himbaechel ] || [ "$built" = no ]; then
  # It finds its chip databases in ../share/nextpnr next to itself, so the folder can move afterwards.
  cmake -S nextpnr-src -B nextpnr-src/build -DARCH=himbaechel -DHIMBAECHEL_UARCH=gowin \
    -DHIMBAECHEL_GOWIN_DEVICES="$GOWIN_DEVICES" -DPython3_EXECUTABLE="$PWD/pyenv/bin/python" -DBUILD_PYTHON=OFF \
    -DBUILD_GUI=OFF -DUSE_OPENMP=OFF -DCMAKE_BUILD_TYPE=Release -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
    -DCMAKE_INSTALL_PREFIX="$PWD" > nextpnr-cmake.log
  cmake --build nextpnr-src/build -j"$JOBS" > nextpnr-build.log 2>&1
  cmake --install nextpnr-src/build > nextpnr-install.log 2>&1
fi

echo "== bin/ (the tool path for Logisim)"
mkdir -p bin
# Yosys and openFPGALoader from the system, through wrappers: through a symlink, Yosys on macOS would look
# for its share/ directory next to the link. (rm first: never write through an old symlink.)
for tool in yosys openFPGALoader; do
  if [ "$tool" = yosys ] && [ -z "$SYSTEM_YOSYS" ]; then real="$PWD/yosys/bin/yosys"
  elif [ "$tool" = yosys ]; then real="$SYSTEM_YOSYS"
  else real="$(find_tool "$tool")"; fi
  rm -f "bin/$tool"
  printf '#!/bin/sh\nexec "%s" "$@"\n' "$real" > "bin/$tool"
  chmod +x "bin/$tool"
done
ln -sfn ../pyenv/bin/gowin_pack bin/gowin_pack

echo "== check"
bin/nextpnr-himbaechel --version
bin/yosys -V
bin/openFPGALoader --Version 2>&1 | head -1 || true
bin/gowin_pack -h > /dev/null && echo "gowin_pack ok"
ls -la share/nextpnr/himbaechel/gowin/
echo
echo "toolchain ready: in Logisim, Preferences > Software > Gowin tool path = $DIR/bin/"
[ "$(uname)" = Linux ] && echo "Linux: unplug and plug in the board once, so that it gets the openfpgaloader udev rule"
exit 0
