// File: BlockStorage.java
package com.cburch.logisim.riscv.blockstorage;

import com.cburch.logisim.data.*;
import com.cburch.logisim.fpga.data.ComponentMapInformationContainer;
import com.cburch.logisim.instance.*;
import com.cburch.logisim.riscv.DataBus;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.StringUtil;
import static com.cburch.logisim.riscv.Strings.S;
import static com.cburch.logisim.riscv.cpu.CpuDrawSupport.*;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BlockStorage extends InstanceFactory {

    public static final String _ID = "Block Storage";
    private static final Value ZERO_32 = Value.createKnown(32, 0);

    // Cached Font objects to avoid creating new ones on every paint
    private static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 20);
    private static final Font STATUS_FONT = new Font("SansSerif", Font.PLAIN, 12);
    public static final Attribute<String> FILE_NAME_ATTR =
            Attributes.forString("file_name", S.getter("blockStorageFileName"));
    // Port indices
    public static final int ADDR = 0;
    public static final int DATA_OUT = 1;
    public static final int CLOCK = 2;
    public static final int READ_ENABLE = 3;
    public static final int WRITE_ENABLE = 4;
    public static final int BE0 = 5;
    public static final int BE1 = 6;
    public static final int BE2 = 7;
    public static final int BE3 = 8;
    public static final int RESET = 9;
    public static final int MEM_READ = 10;
    public static final int MEM_WRITE = 11;
    public static final int BUS_REQUEST = 12;
    public static final int BUS_ACK = 13;
    public static final int DMA_ADDR = 14;
    public static final int DATA_IN = 15;      // register writes
    public static final int MEM_DATA_IN = 16;  // words that DMA reads from memory

    // The Block Storage's box: the title at the top, the ports with their labels beside them, and below them the
    // registers and the file
    private static final int REGISTERS_Y = 222;   // below the lowest port, BE0 (210)
    private static final int BOTTOM = REGISTERS_Y + 66 + 36;
    private static final Font PORT_FONT = new Font("SansSerif", Font.PLAIN, 10);
    /** The ports' labels, beside them: register access on the left, the DMA on the right. */
    private static final String[] PORT_LABELS = {
            "Address", "Data out", "Clock", "Read", "Write", "BE0", "BE1", "BE2", "BE3", "Reset", "Mem read",
            "Mem write", "Bus req", "Bus ack", "Mem addr", "Data in", "Mem data"};


    public BlockStorage() {
        // On an FPGA (Verilog) it keeps its blocks on a microSD card, on four mappable pins: see
        // BlockStorageHdlGeneratorFactory.
        super(_ID, StringUtil.constantGetter(_ID), new BlockStorageHdlGeneratorFactory(), true, false);
        setOffsetBounds(Bounds.create(-50, -50, 180, BOTTOM + 50));
        setPorts(ports());
        setAttributes(
                new Attribute[]{
                        StdAttr.LABEL,
                        StdAttr.LABEL_FONT,
                        FILE_NAME_ATTR,
                        StdAttr.MAPINFO
                },
                new Object[]{
                        "",
                        StdAttr.DEFAULT_LABEL_FONT,
                        "block_storage.bin",
                        new ComponentMapInformationContainer(1, 3, 0, new ArrayList<>(List.of("SD_MISO")),
                                new ArrayList<>(List.of("SD_SCK", "SD_MOSI", "SD_CS")), null)
                }
        );
    }

    /**
     * The Block Storage reads on Data In and writes on Data Out, and its DMA reads memory on Memory Data In; its outputs
     * are 0 while they are not in use (DataBus).
     */
    private static Port[] ports() {
        Port[] ports = new Port[17];
        ports[ADDR]  = new Port(-50,   0, Port.INPUT,  3);
        ports[READ_ENABLE]  = new Port(-50,  20, Port.INPUT,   1);
        ports[WRITE_ENABLE] = new Port(-50,  40, Port.INPUT,   1);
        // with the other outputs of the DMA, below Bus Request
        ports[BE0]   = new Port(130, 210, Port.OUTPUT,  1);
        ports[BE1]   = new Port(130, 200, Port.OUTPUT,  1);
        ports[BE2]   = new Port(130, 190, Port.OUTPUT,  1);
        ports[BE3]   = new Port(130, 180, Port.OUTPUT,  1);
        ports[RESET] = new Port(-50, 140, Port.INPUT,   1);
        ports[BUS_REQUEST] = new Port(130, 160, Port.OUTPUT, 1);
        ports[BUS_ACK] = new Port(-50, 180, Port.INPUT, 1);
        ports[CLOCK] = new Port(-50, 200, Port.INPUT,   1);

        ports[DATA_OUT] = new Port(130, 0, Port.OUTPUT, 32);
        ports[DATA_IN] = new Port(-50, 60, Port.INPUT, 32);
        ports[MEM_DATA_IN] = new Port(130, 20, Port.INPUT, 32);
        ports[MEM_READ] = new Port(130, 100, Port.OUTPUT, 1);
        ports[MEM_WRITE] = new Port(130, 120, Port.OUTPUT, 1);
        ports[DMA_ADDR] = new Port(130, 140, Port.OUTPUT, 32);



        // tooltips
        ports[ADDR].setToolTip(    S.getter("Address"));
        ports[DATA_OUT].setToolTip(S.getter("Data Out"));
        ports[DATA_IN].setToolTip(S.getter("Data In"));
        ports[MEM_DATA_IN].setToolTip(S.getter("Memory Data In"));
        ports[READ_ENABLE].setToolTip(    S.getter("Load: if 1 load from memory to data bus"));
        ports[WRITE_ENABLE].setToolTip(   S.getter("Store: if 1 store from data bus to memory"));
        ports[BE0].setToolTip(     S.getter("Byte Enable 0"));
        ports[BE1].setToolTip(     S.getter("Byte Enable 1"));
        ports[BE2].setToolTip(     S.getter("Byte Enable 2"));
        ports[BE3].setToolTip(     S.getter("Byte Enable 3"));
        ports[RESET].setToolTip(   S.getter("Reset"));
        ports[CLOCK].setToolTip(   S.getter("Clock"));


        ports[MEM_READ].setToolTip(S.getter("Memory Read"));
        ports[MEM_WRITE].setToolTip(S.getter("Memory Write"));

        ports[BUS_REQUEST].setToolTip(S.getter("Bus Request Out"));
        ports[BUS_ACK].setToolTip(S.getter("Bus Acknowledgement In"));
        ports[DMA_ADDR].setToolTip(S.getter("DMA Memory Address"));

        return ports;
    }

    @Override
    protected void configureNewInstance(Instance instance) {
        instance.addAttributeListener();
        Bounds b = instance.getBounds();
        instance.setTextField(
                StdAttr.LABEL, StdAttr.LABEL_FONT,
                b.getX() + b.getWidth()/2, b.getY() - 5,
                GraphicsUtil.H_CENTER, GraphicsUtil.V_BASELINE
        );
    }

    @Override
    protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
        if (attr == FILE_NAME_ATTR) {

            //instance.setData(null);
            instance.fireInvalidated();
        }
    }

    @Override
    public void paintInstance(InstancePainter painter) {
        painter.drawBounds();
        painter.drawLabel();
        painter.drawClock(CLOCK, Direction.EAST);

        Graphics2D graphics = (Graphics2D) painter.getGraphics();
        Bounds bds = painter.getBounds();
        final var loc = painter.getLocation();
        final var font = graphics.getFont();
        graphics.setFont(PORT_FONT);
        graphics.setColor(Color.BLACK);
        GraphicsUtil.drawText(graphics, PORT_LABELS[CLOCK], loc.getX() - 42, loc.getY() + 200, GraphicsUtil.H_LEFT,
                GraphicsUtil.V_CENTER);
        for (int i = 0; i < PORT_LABELS.length; i++) {
            if (i == CLOCK) continue;
            final var left = i == ADDR || i == READ_ENABLE || i == WRITE_ENABLE || i == DATA_IN || i == RESET
                    || i == BUS_ACK;
            painter.drawPort(i, PORT_LABELS[i], left ? Direction.EAST : Direction.WEST);
        }
        graphics.setFont(font);

        int posX = bds.getX() + 10;
        int posY = loc.getY() + REGISTERS_Y;

        GraphicsUtil.drawText(
                graphics,
                TITLE_FONT,
                "Block Storage",
                posX + 80,
                loc.getY() - 28,
                GraphicsUtil.H_CENTER,
                GraphicsUtil.V_CENTER,
                Color.BLACK,
                Color.WHITE
        );


        String fileName = painter.getAttributeValue(FILE_NAME_ATTR);
        FileStatus status = getFileStatus(fileName);

        Color statusColor = Color.BLACK;
        String statusText1 = fileName;
        String statusText2 = "";


        if (status.isValid()) {
            if (status.willBeCreated()) {
                statusText2 = "(Will be created)";
                statusColor = Color.BLUE;
            } else {
                statusText2 = "(" + status.getBlockCount() + " blocks)";
                statusColor = Color.BLACK;
            }
        } else {
            statusText2 = status.getErrorMessage();
            statusColor = Color.RED;
        }

        int statusX = bds.getX() + bds.getWidth()/2;
        int statusY = loc.getY() + BOTTOM - 15;
        GraphicsUtil.drawText(
                graphics,
                STATUS_FONT,
                statusText1,
                statusX,
                statusY,
                GraphicsUtil.H_CENTER,
                GraphicsUtil.V_BOTTOM,
                statusColor,
                Color.GREEN
        );
        GraphicsUtil.drawText(
                graphics,
                STATUS_FONT,
                statusText2,
                statusX,
                statusY+STATUS_FONT.getSize(),
                GraphicsUtil.H_CENTER,
                GraphicsUtil.V_BOTTOM,
                statusColor,
                Color.GREEN
        );

        if (painter.getShowState()) {
            BlockStorageData data = (BlockStorageData) painter.getData();
            if (data != null) {
                drawHexReg(graphics, posX,       posY, false,
                        (int) data.readRegister(BlockStorageData.REG_COMMAND).toLongValue(),
                        "Command", true);

                drawHexReg(graphics, posX + 80, posY, false,
                        (int) data.readRegister(BlockStorageData.REG_STATUS).toLongValue(),
                        "Status", true);

                drawHexReg(graphics, posX, posY + 36, false,
                        (int) data.readRegister(BlockStorageData.REG_MEM_ADDR).toLongValue(),
                        "Mem Addr", true);

                drawHexReg(graphics, posX + 80, posY + 36, false,
                        (int) data.readRegister(BlockStorageData.REG_BLOCK_ADDR).toLongValue(),
                        "Block Addr", true);
            }
        }
    }

    private FileStatus getFileStatus(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return new FileStatus("No file selected");
        }

        File file = new File(fileName);

        if (file.exists()) {
            if (!file.canRead()) {
                return new FileStatus("File not readable");
            }
            if (!file.canWrite()) {
                return new FileStatus("File not writable");
            }
            long length = file.length();
            if (length % BlockStorageData.BLOCK_SIZE != 0) {
                return new FileStatus("Size not multiple of 512");
            }
            int blockCount = (int) (length / BlockStorageData.BLOCK_SIZE);
            return new FileStatus(blockCount);
        } else {
            File parent = file.getParentFile();
            if (parent == null) {
                parent = file.getAbsoluteFile().getParentFile();
            }

            if (parent != null && parent.exists() && parent.isDirectory() && parent.canWrite()) {
                return new FileStatus(true);
            } else {
                return new FileStatus("Cannot create file (no write permission)");
            }
        }
    }

    private static class FileStatus {
        private final boolean valid;
        private final boolean willBeCreated;
        private final String errorMessage;
        private final int blockCount;

        public FileStatus(int blockCount) {
            this.valid = true;
            this.willBeCreated = false;
            this.errorMessage = null;
            this.blockCount = blockCount;
        }

        public FileStatus(boolean willBeCreated) {
            this.valid = true;
            this.willBeCreated = willBeCreated;
            this.errorMessage = null;
            this.blockCount = 0;
        }

        public FileStatus(String errorMessage) {
            this.valid = false;
            this.willBeCreated = false;
            this.errorMessage = errorMessage;
            this.blockCount = 0;
        }

        public boolean isValid() {
            return valid;
        }

        public boolean willBeCreated() {
            return willBeCreated;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public int getBlockCount() {
            return blockCount;
        }
    }

    @Override
    public void propagate(InstanceState state) {
        String fileName = state.getAttributeValue(FILE_NAME_ATTR);
        BlockStorageData data = (BlockStorageData) state.getData();

        if (data == null) {
            data = new BlockStorageData(fileName);
            state.setData(data);
        } else if (!data.getFilePath().equals(fileName)) {
            data.setFilePath(fileName);
        }
        Value reset = state.getPortValue(RESET);
        if (reset == Value.TRUE) {
            data.reset();
            updateControlSignals(state, data);
            return;
        }

        Value clockVal = state.getPortValue(CLOCK);
        boolean clockRisingEdge = (clockVal == Value.TRUE) && !data.prevClock;
        data.setPrevClock((clockVal == Value.TRUE));


        long address = state.getPortValue(ADDR).toLongValue();
        handleRegisterRead(state, data, address);
        Value memData;
        if (clockRisingEdge) {
            handleRegisterWrite(state, data, address);

            switch (data.dmaState) {
                case IDLE:
                    break;

                case BUS_REQUEST_READING:
                    if (state.getPortValue(BUS_ACK) == Value.TRUE) {
                        data.dmaState = BlockStorageData.DmaState.READING;
                    }
                    break;

                case BUS_REQUEST_WRITING:
                    if (state.getPortValue(BUS_ACK) == Value.TRUE) {
                        data.dmaState = BlockStorageData.DmaState.WRITING;
                    }
                    break;

                case READING:
                    memData = data.getCurrentDmaWord();
                    state.setPort(DATA_OUT, DataBus.known(memData), 1);
                    if (data.advanceDma()) {
                        data.finishTransfer();
                    }
                    break;

                case WRITING:
                    memData = state.getPortValue(MEM_DATA_IN);
                    data.writeNextDmaWord(memData);
                    break;
            }
        }

        updateControlSignals(state, data);
    }

    private void updateControlSignals(InstanceState state, BlockStorageData data) {
        // No output floats: where a shared bus would let go, they drive 0
        final Value free = Value.FALSE;

        switch (data.dmaState) {
            case IDLE:
                state.setPort(BE0, free, 1);
                state.setPort(BE1, free, 1);
                state.setPort(BE2, free, 1);
                state.setPort(BE3, free, 1);
                //state.setPort(DATA, HI_Z_32, 1);
                state.setPort(BUS_REQUEST, free, 1);
                state.setPort(MEM_READ, free, 1);
                state.setPort(MEM_WRITE, free, 1);
                state.setPort(DMA_ADDR, ZERO_32, 1);
                break;

            case BUS_REQUEST_READING:
            case BUS_REQUEST_WRITING:
                state.setPort(BUS_REQUEST, Value.TRUE, 1);
                break;

            case READING:
                state.setPort(BE0, Value.TRUE, 1);
                state.setPort(BE1, Value.TRUE, 1);
                state.setPort(BE2, Value.TRUE, 1);
                state.setPort(BE3, Value.TRUE, 1);
                final Value word = data.getCurrentDmaWord();
                state.setPort(DATA_OUT, DataBus.known(word), 1);
                state.setPort(MEM_WRITE, Value.TRUE, 1);
                state.setPort(MEM_READ, Value.FALSE, 1);
                state.setPort(DMA_ADDR, Value.createKnown(32, data.getDmaMemoryAddress()), 1);
                break;

            case WRITING:
                state.setPort(BE0, Value.TRUE, 1);
                state.setPort(BE1, Value.TRUE, 1);
                state.setPort(BE2, Value.TRUE, 1);
                state.setPort(BE3, Value.TRUE, 1);
                state.setPort(MEM_WRITE, Value.FALSE, 1);
                state.setPort(MEM_READ, Value.TRUE, 1);
                state.setPort(DMA_ADDR, Value.createKnown(32, data.getDmaMemoryAddress()), 1);
                break;
        }
    }



    private void handleRegisterWrite(InstanceState state, BlockStorageData data, long address) {
        int regOffset = ((int) (address & 0xFF)) << 2;
        Value dataBus = state.getPortValue(DATA_IN);
        Value writeEnable = state.getPortValue(WRITE_ENABLE);
        Value readEnable = state.getPortValue(READ_ENABLE);

        if (writeEnable == Value.TRUE) {
            data.writeRegister(regOffset, dataBus.toLongValue());
        }
    }

    private void handleRegisterRead(InstanceState state, BlockStorageData data, long address) {
        int regOffset = ((int) (address & 0xFF)) << 2;
        Value readEnable = state.getPortValue(READ_ENABLE);

        if (readEnable == Value.TRUE) {
            Value regValue = data.readRegister(regOffset);
            state.setPort(DATA_OUT, DataBus.known(regValue), 1);
        } else {
            state.setPort(DATA_OUT, ZERO_32, 1);
        }
    }
}