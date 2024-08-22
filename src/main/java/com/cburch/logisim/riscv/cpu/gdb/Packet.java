package com.cburch.logisim.riscv.cpu.gdb;

public class Packet {

    private final byte[] data;
    private final int len;
    private final String packetData;
    private int checksum;

    public Packet(byte[] data, int len){
        this.data = data;
        this.len = len;
        packetData = getPacketData();
    }

    public String getPacketData() {
            StringBuilder packetData = new StringBuilder();
            int i;
            for (i = 0; i < len; i++) {
                char x = (char) this.data[i];
                if (x == '#') {
                    break;
                } else if (x != '$') {
                    packetData.append(x);
                }
            }
            try {
                String checksum = "" + (char) data[i + 1] + (char) data[i + 2];
                this.checksum = Integer.parseUnsignedInt(checksum, 16);
            } catch( Exception ex){
                this.checksum = 0;
            }
            return packetData.toString();
    }

    public Boolean isACK(){
        return checksum == 0;
    }

    public Boolean isValidPacketData(){
        return this.checksum == computeChecksum(packetData);
    }

    //return ascii
    public String getChecksumAsHexString(){
        return Integer.toHexString(checksum);
    }

    //return checksum as int
    public static int computeChecksum(String s){
        int sum = 0;
        for(int i = 0; i < s.length(); i++){
            sum += s.charAt(i);
        }
        return Math.floorMod(sum, 256) & 0xff;
    }
}
