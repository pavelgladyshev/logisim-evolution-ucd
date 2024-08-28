package com.cburch.logisim.riscv.cpu.gdb;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;

public class MemoryAccessRequest extends Request {

    private final TYPE type;
    private final long address;
    private final long bytes;
    private long bytesAccessed;
    private String data;
    private StringBuilder dataBuffer;

    public enum TYPE {
        MEMREAD,
        MEMWRITE,
    }

    public MemoryAccessRequest(TYPE requestType, long address, long bytes){
        this.type = requestType;
        this.bytes = bytes;
        this.bytesAccessed = 0;
        this.dataBuffer = new StringBuilder();
        this.address = address;
        setStatus(STATUS.WAITING);
    }

    public MemoryAccessRequest(TYPE requestType, long address, long bytes, String data){
        this.type = requestType;
        this.bytes = bytes;
        this.bytesAccessed = 0;
        this.data = data;
        this.address = address;
        setStatus(STATUS.WAITING);
    }

    public Boolean isAccessComplete(){
        return bytes == bytesAccessed;
    }

    public TYPE getType(){
        return type;
    }

    public Value getNextAddress() {
        return Value.createKnown(BitWidth.create(32), address + bytesAccessed);
    }

    public long getBytes() {
        return bytes;
    }

    public Value getNextDataByte() {
        long nextDataByte = Long.parseLong(data.substring(0,1), 16);
        data = data.substring(2);
        return Value.createKnown(BitWidth.create(32), nextDataByte);
    }

    public StringBuilder getDataBuffer(){
        return dataBuffer;
    }

    public void incrementAccessed(){
        this.bytesAccessed++;
    }
}
