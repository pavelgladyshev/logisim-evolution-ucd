# Install the toolchain for Logisim's Gowin FPGA flow on Windows (Sipeed Tang Nano boards): YosysHQ's OSS CAD
# Suite, which has every program the flow runs - Yosys, nextpnr-himbaechel with the Gowin chip databases, Project
# Apicula's gowin_pack and openFPGALoader. It goes to %USERPROFILE%\gowin, whose bin folder is the tool path that
# Logisim finds by itself (Preferences > Software > Gowin).
#
#   powershell -ExecutionPolicy Bypass -File support\gowin\setup_windows.ps1 [folder]    default: %USERPROFILE%\gowin
#
# About 600 MB to download and 2.1 GB on disk; no administrator rights needed. Run again, it does nothing if the
# folder is in place. The board needs one more step, once: see support/gowin/README.md.
param([string]$Dir = (Join-Path $env:USERPROFILE "gowin"))
$ErrorActionPreference = "Stop"

# The release Logisim was tested with: its Yosys, nextpnr and Apicula are built together. The checksum is the one
# GitHub publishes for the file.
$Release = "2026-10-26"
$Archive = "oss-cad-suite-windows-x64-20261026.tgz"
$Sha256 = "8d0467cbd6d2c8fb41e99d3cd8a727109b8a3f08a9161c33a11ae6e731b998e3"
$Url = "https://github.com/YosysHQ/oss-cad-suite-build/releases/download/$Release/$Archive"

$Dir = [System.IO.Path]::GetFullPath($Dir)
if (Test-Path (Join-Path $Dir "bin\nextpnr-himbaechel.exe")) {
  "OSS CAD Suite is already in $Dir"
  exit 0
}
if (Test-Path $Dir) { throw "$Dir exists but holds no OSS CAD Suite: move it away first" }
$parent = Split-Path $Dir -Parent
# the archive unpacks into a folder of this name, which is then renamed
$unpacked = Join-Path $parent "oss-cad-suite"
if (Test-Path $unpacked) { throw "$unpacked is in the way: move it away first" }

$download = Join-Path $env:TEMP $Archive
if (-not (Test-Path $download) -or (Get-FileHash $download -Algorithm SHA256).Hash -ne $Sha256) {
  "downloading $Url (about 600 MB)"
  # Windows' own curl: Invoke-WebRequest is many times slower on a file this size
  curl.exe -L --fail -o $download $Url
  if ($LASTEXITCODE -ne 0) { throw "the download failed" }
}
$hash = (Get-FileHash $download -Algorithm SHA256).Hash.ToLower()
if ($hash -ne $Sha256) { throw "the download is not the release tested with Logisim (SHA-256 $hash)" }

"unpacking into $parent"
tar.exe -xzf $download -C $parent
if ($LASTEXITCODE -ne 0) { throw "unpacking failed" }
Rename-Item $unpacked (Split-Path $Dir -Leaf)
Remove-Item $download

"OSS CAD Suite $Release is in $Dir"
"Logisim's Gowin tool path: $Dir\bin\ (found by itself when the folder is %USERPROFILE%\gowin)"
