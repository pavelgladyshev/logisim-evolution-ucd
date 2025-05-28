package com.cburch.logisim.riscv.cpu;

import com.cburch.logisim.data.*;
import com.cburch.logisim.instance.InstanceData;
import java.io.*;
import java.util.Arrays;

public class HardDriveData implements InstanceData, Cloneable {
    public static final int SECTOR_SIZE = 512;
    private static final String FILE_PATH = "harddrive.bin";

    public static final int REG_COMMAND = 0x0;
    public static final int REG_MEM_ADDR = 0x4;
    public static final int REG_SECTOR_ADDR = 0x8;
    public static final int REG_STATUS = 0xC;
    public static final int REG_RESULT = 0x10;

    // Command values
    public static final int CMD_NOP = 0;
    public static final int CMD_READ_SECTOR = 1;
    public static final int CMD_WRITE_SECTOR = 2;

    // Status flags
    public static final int STATUS_BUSY = 0x1;
    public static final int STATUS_ERROR = 0x2;

    private final RandomAccessFile file;
    private byte[] buffer = new byte[SECTOR_SIZE];
    private int currentSector = -1;
    boolean prevClock = false;

    // DMA Control
    public enum DmaState { IDLE, READING, WRITING, BUS_REQUEST_READING, BUS_REQUEST_WRITING }
    public DmaState dmaState = DmaState.IDLE;
    private byte[] dmaBuffer = new byte[SECTOR_SIZE];
    private int dmaPosition = 0;
    private int dmaMemoryAddress = 0;

    private int command = CMD_NOP;
    private int memoryAddress = 0;
    private int sectorAddress = 0;
    private int status = 0;
    private int result = 0;
    private int numSectors = 0;

    public HardDriveData() {
        try {
            file = new RandomAccessFile(FILE_PATH, "rw");
            numSectors = (int) (file.length() / SECTOR_SIZE);
        } catch (IOException e) {
            throw new RuntimeException("Cannot open disk file", e);
        }
    }

    public synchronized void reset() {
        dmaState = DmaState.IDLE;
        dmaPosition = 0;
        command = CMD_NOP;
        memoryAddress = 0;
        sectorAddress = 0;
        status = 0;
        result = 0;
        currentSector = -1;
        Arrays.fill(buffer, (byte)0);
        Arrays.fill(dmaBuffer, (byte)0);
    }

    public void writeRegister(int regOffset, long value) {
        switch (regOffset) {
            case REG_COMMAND:
                command = (int) value;
                handleCommand();
                break;

            case REG_MEM_ADDR:
                memoryAddress = (int) value;
                break;

            case REG_SECTOR_ADDR:
                sectorAddress = (int) value;
                break;
        }
    }

    public Value readRegister(int regOffset) {
        switch (regOffset) {
            case REG_COMMAND:
                return Value.createKnown(32, command);

            case REG_MEM_ADDR:
                return Value.createKnown(32, memoryAddress);

            case REG_SECTOR_ADDR:
                return Value.createKnown(32, sectorAddress);

            case REG_STATUS:
                return Value.createKnown(32, status);

            case REG_RESULT:
                return Value.createKnown(32, result);

            default:
                return Value.createUnknown(BitWidth.create(32));
        }
    }

    private void handleCommand() {
        status |= STATUS_BUSY;
        result = 0;

        try {
            switch (command) {
                case CMD_READ_SECTOR:
                    if (sectorAddress >= numSectors) throw new IOException("Invalid sector");
                    loadSector(sectorAddress);
                    dmaBuffer = Arrays.copyOf(buffer, SECTOR_SIZE);
                    dmaState = DmaState.BUS_REQUEST_READING;
                    dmaPosition = 0;
                    dmaMemoryAddress = memoryAddress;
                    break;

                case CMD_WRITE_SECTOR:
                    if (sectorAddress >= numSectors) throw new IOException("Invalid sector");
                    loadSector(sectorAddress);
                    dmaState = DmaState.BUS_REQUEST_WRITING;
                    dmaPosition = 0;
                    dmaMemoryAddress = memoryAddress;
                    break;

                default:
                    status &= ~STATUS_BUSY;
                    break;
            }
        } catch (IOException e) {
            status |= STATUS_ERROR;
            result = 1;
            //dmaState = DmaState.IDLE;
        }
    }

    public Value getCurrentDmaWord() {
        if (dmaPosition >= SECTOR_SIZE - 3) {
            return Value.createError(BitWidth.create(32));
        }


        int word = ((dmaBuffer[dmaPosition] & 0xFF) << 24)
                | ((dmaBuffer[dmaPosition+1] & 0xFF) << 16)
                | ((dmaBuffer[dmaPosition+2] & 0xFF) << 8)
                | (dmaBuffer[dmaPosition+3] & 0xFF);

        return Value.createKnown(BitWidth.create(32), word);
    }

    public void writeNextDmaWord(Value data) {
        if (dmaPosition >= SECTOR_SIZE) return;

        long word = data.toLongValue();
        dmaBuffer[dmaPosition] = (byte) (word >> 24);
        dmaBuffer[dmaPosition+1] = (byte) (word >> 16);
        dmaBuffer[dmaPosition+2] = (byte) (word >> 8);
        dmaBuffer[dmaPosition+3] = (byte) word;

        dmaPosition += 4;
        dmaMemoryAddress += 4;

        if (dmaPosition >= SECTOR_SIZE) {
            System.arraycopy(dmaBuffer, 0, buffer, 0, SECTOR_SIZE);
            flushBuffer();
            finishTransfer();
        }
    }

    void finishTransfer() {
        status &= ~STATUS_BUSY;
        command = CMD_NOP;
        dmaState = DmaState.IDLE;
        dmaPosition = 0;
    }

    public int getDmaMemoryAddress() {
        return dmaMemoryAddress;
    }

    public int getDmaPosition() {
        return dmaPosition;
    }



    private void loadSector(int sector) throws IOException {
        if (sector == currentSector) return;

        file.seek(sector * SECTOR_SIZE);
        file.readFully(buffer);
        currentSector = sector;
    }

    private void flushBuffer() {
        try {
            file.seek(currentSector * SECTOR_SIZE);
            file.write(buffer);
        } catch (IOException e) {
            status |= STATUS_ERROR;
            result = 1;
        }
    }

    public boolean getPrevClock() {
        return prevClock;
    }

    public void setPrevClock(boolean clockState) {
        prevClock = clockState;
    }

    public Value readSectorWord(int sector, int offset, boolean[] byteEnables) {
        try {
            if (sector != currentSector) {
                loadSector(sector);
            }

            int value = 0;
            for (int i = 0; i < 4; i++) {
                if (byteEnables[i]) {
                    value |= (buffer[offset + i] & 0xFF) << (24 - (i * 8));
                }
            }
            return Value.createKnown(32, value);
        } catch (IOException e) {
            return Value.createError(BitWidth.create(32));
        }
    }




    public boolean advanceDma() {
        dmaPosition += 4;
        dmaMemoryAddress += 4;
        return dmaPosition >= SECTOR_SIZE;
    }








    @Override
    public HardDriveData clone() {
        try {
            HardDriveData clone = (HardDriveData) super.clone();
            clone.buffer = Arrays.copyOf(buffer, buffer.length);
            clone.dmaBuffer = Arrays.copyOf(dmaBuffer, dmaBuffer.length);
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}