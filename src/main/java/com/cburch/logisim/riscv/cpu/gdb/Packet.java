package com.cburch.logisim.riscv.cpu.gdb;

import java.util.Arrays;
import java.util.Objects;

public class Packet {

    private final String packetData;
    private int checksum;

    // constructor for pre-existing packets to be decoded
    public Packet(byte[] data, int len){
        packetData = decodePacket(data, len);
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
        return Objects.equals(packetData, "-");
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
    private String decodePacket(byte[] data, int len) {
        StringBuilder packetData = new StringBuilder();
        int i = 0;
        while(i < len && (char) data[i] != '$') i++;
        while(++i < len && (char) data[i] != '#') {
            packetData.append((char) data[i]);
        }
        try {
            String checksum = "" + (char) data[i + 1] + (char) data[i + 2];
            this.checksum = Integer.parseUnsignedInt(checksum, 16);
        } catch( Exception ex){
            this.checksum = 0;
            return (char) data[0] + "";
        }
        return packetData.toString();
    }

    private int computeChecksum(){
        int sum = 0;
        for(int i = 0; i < packetData.length(); i++){
            sum += packetData.charAt(i);
        }
        return Math.floorMod(sum, 256) & 0xff;
    }
}
