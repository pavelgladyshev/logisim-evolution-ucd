package com.cburch.logisim.riscv.cpu.gdb;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;

import java.io.IOException;

public class MemoryAccessRequest extends Request {

    private final TYPE type;
    private final long address;
    private final int bytes;
    private int bytesAccessed;
    private String data;
    private StringBuffer dataBuffer;

    public static long RAM_START = 0x10010000L;

    public enum TYPE {
        MEMREAD,
        MEMWRITE,
    }

    public MemoryAccessRequest(TYPE requestType, long address, int bytes) throws IOException {
        super();
        this.type = requestType;
        this.bytes = bytes;
        this.bytesAccessed = 0;
        this.dataBuffer = new StringBuffer();
        this.address = address;
    }

    public MemoryAccessRequest(TYPE requestType, long address, int bytes, String data) throws IOException {
        super();
        this.type = requestType;
        this.bytes = bytes;
        this.bytesAccessed = 0;
        this.data = data;
        this.address = address;
    }

    public Boolean isAccessComplete(){
        return bytes == bytesAccessed;
    }

    public TYPE getType(){
        return type;
    }

    public Value getNextAddress() {
        return Value.createKnown(BitWidth.create(32), RAM_START + address + bytesAccessed);
    }

    public long getBytes() {
        return bytes;
    }

    public int getBytesAccessed() {return bytesAccessed;}

    public Value getNextDataByte() {
        long nextDataByte = Long.parseLong(data.substring(0,1), 16);
        data = data.substring(2);
        return Value.createKnown(BitWidth.create(32), nextDataByte);
    }

    public StringBuffer getDataBuffer(){
        return dataBuffer;
    }

    public void incrementAccessed(){
        this.bytesAccessed++;
    }
}
