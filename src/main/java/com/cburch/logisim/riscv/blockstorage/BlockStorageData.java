package com.cburch.logisim.riscv.blockstorage;

import com.cburch.logisim.data.*;
import com.cburch.logisim.instance.InstanceData;
import java.io.*;
import java.util.Arrays;

public class BlockStorageData implements InstanceData, Cloneable, AutoCloseable {
    public static final int BLOCK_SIZE = 512;

    // High-impedance values for tri-state outputs
    public static final Value HI_Z = Value.createUnknown(BitWidth.create(1));
    public static final Value HI_Z_32 = Value.createUnknown(BitWidth.create(32));

    // Cached BitWidth to avoid repeated creation
    private static final BitWidth BIT_WIDTH_32 = BitWidth.create(32);

    private String filePath;
    private String errorMessage;
    public static final int REG_COMMAND = 0x0;
    public static final int REG_MEM_ADDR = 0x4;
    public static final int REG_BLOCK_ADDR = 0x8;
    public static final int REG_STATUS = 0xC;
    public static final int REG_RESULT = 0x10;

    // Command values
    public static final int CMD_NOP = 0;
    public static final int CMD_READ_BLOCK = 1;
    public static final int CMD_WRITE_BLOCK = 2;

    // Status flags
    public static final int STATUS_BUSY = 0x1;
    public static final int STATUS_ERROR = 0x2;

    private RandomAccessFile file;
    private byte[] buffer = new byte[BLOCK_SIZE];
    private int currentBlock = -1;
    boolean prevClock = false;

    // DMA Control
    public enum DmaState { IDLE, READING, WRITING, BUS_REQUEST_READING, BUS_REQUEST_WRITING }
    public DmaState dmaState = DmaState.IDLE;
    private byte[] dmaBuffer = new byte[BLOCK_SIZE];
    private int dmaPosition = 0;
    private int dmaMemoryAddress = 0;

    private int command = CMD_NOP;
    private int memoryAddress = 0;
    private int blockAddress = 0;
    private int status = 0;
    private int result = 0;
    private int numBlocks = 0;
    private long fileLength = 0;  // Cached file length to avoid repeated I/O

    public BlockStorageData(String filePath) {
        this.filePath = filePath;
        try {
            File f = new File(filePath);

            if (!f.exists()) {
                int defaultNumBlocks = 1024;
                long totalBytes = (long) defaultNumBlocks * BLOCK_SIZE;

                try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
                    raf.setLength(totalBytes);
                }
            }

            file = new RandomAccessFile(f, "rw");
            fileLength = file.length();
            numBlocks = (int) (fileLength / BLOCK_SIZE);
        } catch (IOException e) {
            throw new RuntimeException("Cannot open storage file", e);
        }
    }

    public synchronized void reset() {
        dmaState = DmaState.IDLE;
        dmaPosition = 0;
        command = CMD_NOP;
        memoryAddress = 0;
        blockAddress = 0;
        status = 0;
        result = 0;
        currentBlock = -1;
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

            case REG_BLOCK_ADDR:
                blockAddress = (int) value;
                break;
        }
    }

    public Value readRegister(int regOffset) {
        switch (regOffset) {
            case REG_COMMAND:
                return Value.createKnown(32, command);

            case REG_MEM_ADDR:
                return Value.createKnown(32, memoryAddress);

            case REG_BLOCK_ADDR:
                return Value.createKnown(32, blockAddress);

            case REG_STATUS:
                return Value.createKnown(32, status);

            case REG_RESULT:
                return Value.createKnown(32, result);

            default:
                return Value.createUnknown(BIT_WIDTH_32);
        }
    }

    private void handleCommand() {
        status |= STATUS_BUSY;
        result = 0;

        try {
            switch (command) {
                case CMD_READ_BLOCK:
                    if (blockAddress >= numBlocks) throw new IOException("Invalid block");
                    loadBlock(blockAddress);
                    System.arraycopy(buffer, 0, dmaBuffer, 0, BLOCK_SIZE);
                    dmaState = DmaState.BUS_REQUEST_READING;
                    dmaPosition = 0;
                    dmaMemoryAddress = memoryAddress;
                    break;

                case CMD_WRITE_BLOCK:
                    if (blockAddress >= numBlocks) throw new IOException("Invalid block");
                    loadBlock(blockAddress);
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
        }
    }

    public Value getCurrentDmaWord() {
        if (dmaPosition >= BLOCK_SIZE - 3) {
            return Value.createError(BIT_WIDTH_32);
        }

        int word = ((dmaBuffer[dmaPosition] & 0xFF))
                | ((dmaBuffer[dmaPosition+1] & 0xFF) << 8)
                | ((dmaBuffer[dmaPosition+2] & 0xFF) << 16)
                | ((dmaBuffer[dmaPosition+3] & 0xFF) << 24);

        return Value.createKnown(BIT_WIDTH_32, word);
    }

    public void writeNextDmaWord(Value data) {
        if (dmaPosition >= BLOCK_SIZE) return;

        long word = data.toLongValue();
        dmaBuffer[dmaPosition] = (byte) (word);
        dmaBuffer[dmaPosition+1] = (byte) (word >> 8);
        dmaBuffer[dmaPosition+2] = (byte) (word >> 16);
        dmaBuffer[dmaPosition+3] = (byte) (word >> 24);


        dmaPosition += 4;
        dmaMemoryAddress += 4;

        if (dmaPosition >= BLOCK_SIZE) {
            System.arraycopy(dmaBuffer, 0, buffer, 0, BLOCK_SIZE);
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



    private void loadBlock(int block) throws IOException {
        if (file == null) {
            throw new IOException("File not open");
        }
        if (block == currentBlock) return;

        long pos = (long) block * BLOCK_SIZE;
        if (pos >= fileLength) {
            Arrays.fill(buffer, (byte) 0);
        } else {
            file.seek(pos);
            file.readFully(buffer);
        }
        currentBlock = block;
    }

    private void flushBuffer() {
        try {
            long pos = (long) currentBlock * BLOCK_SIZE;
            file.seek(pos);
            file.write(buffer);
            long newLength = pos + BLOCK_SIZE;
            if (newLength > fileLength) {
                file.setLength(newLength);
                fileLength = newLength;
                numBlocks = (int) (fileLength / BLOCK_SIZE);
            }
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

    public Value readBlockWord(int block, int offset, boolean[] byteEnables) {
        try {
            if (block != currentBlock) {
                loadBlock(block);
            }

            int value = 0;
            for (int i = 0; i < 4; i++) {
                if (byteEnables[i]) {
                    value |= (buffer[offset + i] & 0xFF) << (24 - (i * 8));
                }
            }
            return Value.createKnown(32, value);
        } catch (IOException e) {
            return Value.createError(BIT_WIDTH_32);
        }
    }




    public boolean advanceDma() {
        dmaPosition += 4;
        dmaMemoryAddress += 4;
        return dmaPosition >= BLOCK_SIZE;
    }




    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
        try {
            // Close existing file if open
            if (file != null) {
                file.close();
            }

            File f = new File(filePath);
            if (!f.exists()) {
                int defaultNumBlocks = 32;
                long totalBytes = (long) defaultNumBlocks * BLOCK_SIZE;

                try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
                    raf.setLength(totalBytes);
                }
            }

            file = new RandomAccessFile(f, "rw");
            fileLength = file.length();
            numBlocks = (int) (fileLength / BLOCK_SIZE);
            currentBlock = -1;  // Invalidate block cache
            errorMessage = null;
        } catch (IOException e) {
            errorMessage = e.getMessage();
            numBlocks = 0;
            fileLength = 0;
            file = null;
        }
    }


    /**
     * Closes the underlying file. Called when the component is removed or simulation ends.
     */
    @Override
    public void close() {
        if (file != null) {
            try {
                file.close();
            } catch (IOException e) {
                // Ignore close errors
            }
            file = null;
        }
    }

    @Override
    public BlockStorageData clone() {
        try {
            BlockStorageData copy = (BlockStorageData) super.clone();
            copy.buffer = Arrays.copyOf(buffer, buffer.length);
            copy.dmaBuffer = Arrays.copyOf(dmaBuffer, dmaBuffer.length);
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}