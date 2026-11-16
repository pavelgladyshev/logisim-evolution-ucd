package com.cburch.logisim.riscv.cpu.gdb;

import java.util.Objects;

public class Packet {

    private final String packetData;
    private int checksum;
    private int processedBytes;
    // true when a whole $...#xx packet was read, checksum digits included. A checksum of 00 is a
    // checksum like any other (gdb sends $m104428,4#00), so it cannot mark a packet as incomplete.
    private boolean complete;

    // constructor for pre-existing packets to be decoded
    public Packet(byte[] data, int len, int startIndex) {
        packetData = decodePacket(data, len, startIndex);
    }

    // constructor for new packets to be encoded
    public Packet(String packetData) {
        this.packetData = packetData;
        this.checksum = computeChecksum();
    }

    public Boolean isValid(){
        return complete && checksum == computeChecksum();
    }

    public Boolean isNACK() {
        return packetData.equals("-");
    }

    public Boolean isACK() {
        return packetData.equals("+");
    }

    public Boolean isCtrlC() {
        return packetData.equals("\u0003");
    }

    public String getPacketData() {
        return packetData;
    }

    public int getChecksum() {
        return checksum;
    }

    public String getChecksumAsHexString(){
        return String.format("%02X", getChecksum());
    }

    public String wrapped() {
        return String.format("$%s#%s", getPacketData(), getChecksumAsHexString());
    }

    private String decodePacket(byte[] data, int len, int startIndex) {
        StringBuilder packetDataBuilder = new StringBuilder();
        int i = startIndex;
        boolean framed = false;
        
        // A packet starts with '$'. Anything else (an ACK, a NACK, Ctrl-C) is one byte on its own,
        // so a Ctrl-C followed by a packet in the same read is not skipped over.
        if (i < len && (char) data[i] == '$') {
            framed = true;
            i++; // Move past the '$'
            while (i < len && (char) data[i] != '#') {
                packetDataBuilder.append((char) data[i]);
                i++;
            }
            
            // Process checksum if available
            if (i + 2 < len) {
                try {
                    String checksum = "" + (char) data[i + 1] + (char) data[i + 2];
                    this.checksum = Integer.parseUnsignedInt(checksum, 16);
                    this.complete = true;
                    i += 3; // Move past '#' and two checksum characters
                } catch (Exception ex) {
                    this.checksum = 0;
                    i += 3; // a malformed checksum: skip it, and the packet is not complete
                }
            } else {
                this.checksum = 0;
            }
        }
        
        this.processedBytes = i - startIndex;
        
        // If no packet was found, return the first character (an ACK, a NACK or Ctrl-C).
        // An empty packet, $#00, is a packet and is returned as such.
        if (!framed && len > startIndex) {
            this.processedBytes = 1;
            return String.valueOf((char) data[startIndex]);
        }
        
        return packetDataBuilder.toString();
    }

    public int getProcessedBytes() {
        return processedBytes;
    }

    private int computeChecksum(){
        int sum = 0;
        for(int i = 0; i < packetData.length(); i++){
            sum += packetData.charAt(i);
        }
        return Math.floorMod(sum, 256) & 0xff;
    }
}
