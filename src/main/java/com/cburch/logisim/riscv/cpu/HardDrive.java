// File: HardDrive.java
package com.cburch.logisim.riscv.cpu;

import com.cburch.logisim.data.*;
import com.cburch.logisim.instance.*;
import com.cburch.logisim.util.GraphicsUtil;
import static com.cburch.logisim.riscv.Strings.S;
import static com.cburch.logisim.riscv.cpu.CpuDrawSupport.*;
import static com.cburch.logisim.riscv.cpu.rv32imData.HiZ;
import static com.cburch.logisim.riscv.cpu.rv32imData.HiZ32;

import java.awt.*;

public class HardDrive extends InstanceFactory {

    public static final String _ID = "Hard Drive DMA";

    // Port indices
    public static final int ADDR = 0;
    public static final int DATA = 1;
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


    private static final long SECTOR_SIZE = 512;

    public HardDrive() {
        super(_ID);
        setOffsetBounds(Bounds.create(-50, -50, 180, 280));

        Port[] ports = new Port[15];
        ports[ADDR]  = new Port(-50,   0, Port.INPUT,  32);
        ports[READ_ENABLE]  = new Port(-50,  20, Port.INPUT,   1);
        ports[WRITE_ENABLE] = new Port(-50,  40, Port.INPUT,   1);
        ports[BE0]   = new Port(-50,  60, Port.INOUT,   1);
        ports[BE1]   = new Port(-50,  80, Port.INOUT,   1);
        ports[BE2]   = new Port(-50, 100, Port.INOUT,   1);
        ports[BE3]   = new Port(-50, 120, Port.INOUT,   1);
        ports[RESET] = new Port(-50, 140, Port.INPUT,   1);
        ports[BUS_REQUEST] = new Port(130, 160, Port.OUTPUT, 1);
        ports[BUS_ACK] = new Port(-50, 180, Port.INPUT, 1);
        ports[CLOCK] = new Port(-50, 200, Port.INPUT,   1);

        ports[DATA]  = new Port( 130,   0, Port.INOUT,  32);
        ports[MEM_READ] = new Port(130, 100, Port.OUTPUT, 1);
        ports[MEM_WRITE] = new Port(130, 120, Port.OUTPUT, 1);
        ports[DMA_ADDR] = new Port(130, 140, Port.OUTPUT, 32);



        // tooltips
        ports[ADDR].setToolTip(    S.getter("Sector Address"));
        ports[DATA].setToolTip(    S.getter("Data Bus"));
        ports[READ_ENABLE].setToolTip(    S.getter("Read Enable"));
        ports[WRITE_ENABLE].setToolTip(   S.getter("Write Enable"));
        ports[BE0].setToolTip(     S.getter("Byte Enable 0"));
        ports[BE1].setToolTip(     S.getter("Byte Enable 1"));
        ports[BE2].setToolTip(     S.getter("Byte Enable 2"));
        ports[BE3].setToolTip(     S.getter("Byte Enable 3"));
        ports[RESET].setToolTip(   S.getter("Reset Pointer"));
        ports[CLOCK].setToolTip(   S.getter("Clock"));


        ports[MEM_READ].setToolTip(S.getter("Memory Read"));
        ports[MEM_WRITE].setToolTip(S.getter("Memory Write"));

        ports[BUS_REQUEST].setToolTip(S.getter("Bus Request Out"));
        ports[BUS_ACK].setToolTip(S.getter("Bus Acknowledgement In"));
        ports[DMA_ADDR].setToolTip(S.getter("DMA Memory Address"));

        setPorts(ports);
        setAttributes(
                new Attribute[]{ StdAttr.LABEL, StdAttr.LABEL_FONT },
                new Object[]{    "",         StdAttr.DEFAULT_LABEL_FONT }
        );
    }

    @Override
    protected void configureNewInstance(Instance instance) {
        Bounds b = instance.getBounds();
        instance.setTextField(
                StdAttr.LABEL, StdAttr.LABEL_FONT,
                b.getX() + b.getWidth()/2, b.getY() - 5,
                GraphicsUtil.H_CENTER, GraphicsUtil.V_BASELINE
        );
    }
    @Override
    public void paintInstance(InstancePainter painter) {
        painter.drawBounds();
        painter.drawLabel();
        for (int i = 0; i < 14; i++) painter.drawPort(i);

        if (painter.getShowState()) {
            Graphics2D graphics = (Graphics2D) painter.getGraphics();
            Bounds bds         = painter.getBounds();
            int posX           = bds.getX() + 10;
            int posY           = bds.getY() + 170;

            Font font = new Font("SansSerif", Font.BOLD, 20);
            GraphicsUtil.drawText(
                    graphics, font,
                    "Hard Drive DMA",
                    posX + 80, posY - 127,
                    0, 0,
                    Color.BLACK, Color.WHITE
            );

            HardDriveData data = (HardDriveData) painter.getData();
            drawHexReg(graphics, posX,       posY - 80, false,
                    (int) data.readRegister(HardDriveData.REG_COMMAND).toLongValue(),
                    "Command", true);

            drawHexReg(graphics, posX + 80, posY - 80, false,
                    (int) data.readRegister(HardDriveData.REG_STATUS).toLongValue(),
                    "Status", true);

            drawHexReg(graphics, posX,posY - 40, false,
                    (int) data.readRegister(HardDriveData.REG_MEM_ADDR).toLongValue(),
                    "Mem Addr", true);

            drawHexReg(graphics, posX + 80, posY - 40, false,
                    (int) data.readRegister(HardDriveData.REG_SECTOR_ADDR).toLongValue(),
                    "Sector Addr", true);
        }
    }

    @Override
    public void propagate(InstanceState state) {
        HardDriveData data = (HardDriveData) state.getData();
        if (data == null) {
            data = new HardDriveData();
            state.setData(data);
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



        if (clockRisingEdge) {
            long address = state.getPortValue(ADDR).toLongValue();
            handleRegisterAccess(state, data, address);


            switch (data.dmaState) {
                case IDLE:
                    break;

                case BUS_REQUEST_READING:
                    if (state.getPortValue(BUS_ACK) == Value.TRUE) {
                        data.dmaState = HardDriveData.DmaState.READING;
                    }
                    break;

                case BUS_REQUEST_WRITING:
                    if (state.getPortValue(BUS_ACK) == Value.TRUE) {
                        data.dmaState = HardDriveData.DmaState.WRITING;
                    }
                    break;

                case READING:
                    if (data.advanceDma()) {
                        data.finishTransfer();
                    }
                    break;

                case WRITING:
                    Value memData = state.getPortValue(DATA);
                    data.writeNextDmaWord(memData);
                    break;
            }
        }

        updateControlSignals(state, data);
    }

    private void updateControlSignals(InstanceState state, HardDriveData data) {


        Value writeActive = state.getPortValue(WRITE_ENABLE);
        Value readActive = state.getPortValue(READ_ENABLE);
        Value byteEnable = (writeActive == Value.TRUE || readActive == Value.TRUE)
                ? Value.TRUE
                : HiZ;

        state.setPort(BE0, byteEnable, 1);
        state.setPort(BE1, byteEnable, 1);
        state.setPort(BE2, byteEnable, 1);
        state.setPort(BE3, byteEnable, 1);

        switch (data.dmaState) {
            case IDLE:
                state.setPort(DATA, HiZ32, 1);
                state.setPort(BUS_REQUEST, HiZ, 1);
                state.setPort(MEM_READ, HiZ, 1);
                state.setPort(MEM_WRITE, HiZ, 1);
                state.setPort(DMA_ADDR, HiZ32, 1);
                break;
            case BUS_REQUEST_READING:
            case BUS_REQUEST_WRITING:
                state.setPort(BUS_REQUEST, Value.TRUE, 1);
                break;

            case READING:
                state.setPort(DATA, data.getCurrentDmaWord(), 1);
                state.setPort(MEM_WRITE, Value.TRUE, 1);
                state.setPort(MEM_READ, Value.FALSE, 1);
                state.setPort(DMA_ADDR, Value.createKnown(32, data.getDmaMemoryAddress()), 1);
                break;

            case WRITING:
                state.setPort(MEM_WRITE, Value.FALSE, 1);
                state.setPort(MEM_READ, Value.TRUE, 1);
                state.setPort(DMA_ADDR, Value.createKnown(32, data.getDmaMemoryAddress()), 1);
                break;
        }
    }



    private void handleRegisterAccess(InstanceState state, HardDriveData data, long address) {
        int regOffset = (int) (address & 0xFF);
        Value dataBus = state.getPortValue(DATA);
        Value writeEnable = state.getPortValue(WRITE_ENABLE);
        Value readEnable = state.getPortValue(READ_ENABLE);

        if (writeEnable == Value.TRUE) {
            data.writeRegister(regOffset, dataBus.toLongValue());
        } else if (readEnable == Value.TRUE) {
            Value regValue = data.readRegister(regOffset);
            state.setPort(DATA, regValue, 1);
        }
    }


    @Override
    public HardDriveData clone() {
        return new HardDriveData();
    }
}