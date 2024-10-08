package com.cburch.logisim.riscv.cpu.gdb;

import java.util.Objects;

public class Packet {

    private final String packetData;
    private int checksum;
    private int processedBytes;

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
        return !(isACK()) && !(isNACK()) && checksum != 0 && checksum == computeChecksum();
    }

    public Boolean isNACK() {
        return packetData.equals("-");
    }

    public Boolean isACK() {
        return Objects.equals(packetData, "+");
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
        
        // Find the start of the packet
        while (i < len && (char) data[i] != '$') i++;
        
        if (i < len) {
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
                    i += 3; // Move past '#' and two checksum characters
                } catch (Exception ex) {
                    this.checksum = 0;
                }
            } else {
                this.checksum = 0;
            }
        }
        
        this.processedBytes = i - startIndex;
        
        // If no valid packet was found, return the first character
        if (packetDataBuilder.length() == 0 && len > startIndex) {
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
