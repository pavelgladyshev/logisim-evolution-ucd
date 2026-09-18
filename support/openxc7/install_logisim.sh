#!/bin/bash
# Install this Logisim-evolution (with the openXC7 FPGA flow) for the current user:
#
#   support/openxc7/install_logisim.sh [logisim jar]     default: the jar next to this script, else build/libs
#
# Copies the jar to ~/openxc7/logisim/ and adds a launcher:
#   Linux: "Logisim-evolution (openXC7)" in the applications menu (and the logisim-openxc7 command)
#   macOS: ~/Applications/Logisim-evolution openXC7.app
# Needs Java 21 or newer (Kubuntu: sudo apt install openjdk-21-jre).
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
JAR="${1:-}"
if [ -z "$JAR" ]; then
  for candidate in "$HERE"/logisim-evolution*.jar "$HERE"/../../build/libs/logisim-evolution-*-all.jar; do
    [ -f "$candidate" ] && JAR="$candidate" && break
  done
fi
[ -n "$JAR" ] && [ -f "$JAR" ] || { echo "no Logisim jar found; give its path: $0 <jar>" >&2; exit 1; }

JAVA="$(command -v java || true)"
if [ "$(uname)" = Darwin ] && [ -x /usr/libexec/java_home ]; then
  JAVA_HOME_21="$(/usr/libexec/java_home -v 21+ 2>/dev/null || true)"
  [ -n "$JAVA_HOME_21" ] && JAVA="$JAVA_HOME_21/bin/java"
fi
version="$("${JAVA:-java}" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -n 1)"
if [ -z "$version" ] || [ "$version" -lt 21 ]; then
  echo "Java 21 or newer is needed (Kubuntu: sudo apt install openjdk-21-jre)" >&2
  exit 1
fi

DEST="$HOME/openxc7/logisim"
mkdir -p "$DEST"
cp "$JAR" "$DEST/logisim-evolution-openxc7.jar"
icon() {   # an icon next to this script (as in the zip for another computer), else in the repository
  for f in "$HERE/$1" "$HERE/../jpackage/$2/$1"; do [ -f "$f" ] && { echo "$f"; return; }; done
}

case "$(uname)" in
  Darwin)
    APP="$HOME/Applications/Logisim-evolution openXC7.app"
    rm -rf "$APP"
    mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
    cat > "$APP/Contents/MacOS/logisim" <<EOF
#!/bin/sh
exec "$JAVA" -Xdock:name="Logisim-evolution openXC7" -Xdock:icon="$APP/Contents/Resources/Logisim-evolution.icns" \\
  -jar "$DEST/logisim-evolution-openxc7.jar" "\$@"
EOF
    chmod +x "$APP/Contents/MacOS/logisim"
    ICNS="$(icon Logisim-evolution.icns macos)"
    [ -n "$ICNS" ] && cp "$ICNS" "$APP/Contents/Resources/"
    cat > "$APP/Contents/Info.plist" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleName</key><string>Logisim-evolution openXC7</string>
  <key>CFBundleIdentifier</key><string>com.cburch.logisim.openxc7</string>
  <key>CFBundleExecutable</key><string>logisim</string>
  <key>CFBundleIconFile</key><string>Logisim-evolution.icns</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>NSHighResolutionCapable</key><true/>
</dict>
</plist>
EOF
    echo "installed: $APP"
    ;;
  *)
    mkdir -p "$HOME/.local/bin" "$HOME/.local/share/applications"
    cat > "$HOME/.local/bin/logisim-openxc7" <<EOF
#!/bin/sh
exec java -jar "$DEST/logisim-evolution-openxc7.jar" "\$@"
EOF
    chmod +x "$HOME/.local/bin/logisim-openxc7"
    PNG="$(icon logisim-icon-128.png linux)"
    [ -n "$PNG" ] && cp "$PNG" "$DEST/logisim.png"
    cat > "$HOME/.local/share/applications/logisim-evolution-openxc7.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=Logisim-evolution (openXC7)
Comment=Digital logic simulator, with circuits built and loaded into Xilinx 7-series FPGAs
Exec=$HOME/.local/bin/logisim-openxc7 %f
Icon=$DEST/logisim.png
Terminal=false
Categories=Education;Electronics;
MimeType=application/x-logisim-circuit;
EOF
    command -v update-desktop-database > /dev/null 2>&1 && update-desktop-database "$HOME/.local/share/applications" || true
    echo "installed: 'Logisim-evolution (openXC7)' in the applications menu, and the command logisim-openxc7"
    ;;
esac
