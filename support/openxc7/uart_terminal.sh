#!/bin/sh
# A terminal on the Cmod A7's USB-UART (115200 baud, 8N1): what a circuit's TTY prints on the FPGA appears here,
# and what you type goes to its Keyboard (Enter and Backspace work as in Logisim).
#
#   support/openxc7/uart_terminal.sh [port]
#
# The port is found automatically: on macOS the board's second USB channel, /dev/cu.usbserial-<serial>1; on
# Linux /dev/serial/by-id/...Digilent...-if01-port0 (usually /dev/ttyUSB1).
# Uses picocom if installed (quit: Ctrl-A Ctrl-X), otherwise screen (quit: Ctrl-A K y).
# Kubuntu: sudo apt install picocom
PORT="$1"
if [ -z "$PORT" ]; then
  case "$(uname)" in
    Darwin) PORT="$(ls /dev/cu.usbserial-*1 2>/dev/null | head -n 1)" ;;
    *)      PORT="$(ls /dev/serial/by-id/*Digilent*-if01-port0 2>/dev/null | head -n 1)"
            [ -n "$PORT" ] || PORT=/dev/ttyUSB1 ;;
  esac
fi
if [ ! -e "$PORT" ]; then
  echo "No serial port for the board found - is it connected? (or give the port: $0 <port>)" >&2
  exit 1
fi
echo "Terminal on $PORT at 115200 baud"
if command -v picocom > /dev/null 2>&1; then
  exec picocom --quiet --baud 115200 "$PORT"
fi
exec screen "$PORT" 115200
