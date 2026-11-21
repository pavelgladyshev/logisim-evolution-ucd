package com.cburch.logisim.riscv.cpu;

import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.riscv.cpu.csrs.MMCSR;
import com.cburch.logisim.riscv.cpu.csrs.SCSR;
import com.cburch.logisim.util.GraphicsUtil;

import java.awt.*;

import static com.cburch.logisim.riscv.Strings.S;

public class CpuDrawSupport {

    public static Bounds getBounds(int x, int y, int width, int height, boolean scale) {
        if (scale)
            return Bounds.create(
                    AppPreferences.getScaled(x),
                    AppPreferences.getScaled(y),
                    AppPreferences.getScaled(width),
                    AppPreferences.getScaled(height));
        return Bounds.create(x, y, width, height);
    }
    public static int getBlockWidth(Graphics2D g2, boolean scale) {
        FontMetrics f = g2.getFontMetrics();
        int StrWidth = f.stringWidth("0x00000000") + (scale ? AppPreferences.getScaled(2) : 2);
        int blkPrefWidth = scale ? AppPreferences.getScaled(80) : 80;
        return Math.max(StrWidth, blkPrefWidth);
    }

    public static void drawHexReg(
            Graphics2D g, int x, int y, boolean scale, int value, String Name, boolean valid) {
        Graphics2D g2 = (Graphics2D) g.create();
        Bounds bds;
        if (scale) g2.setFont(AppPreferences.getScaledFont(g.getFont()));
        bds = getBounds(x, y, 0, 0, scale);
        g2.translate(bds.getX(), bds.getY());
        int blockWidth = getBlockWidth(g2, scale);
        if (scale) blockWidth = AppPreferences.getDownScaled(blockWidth);
        g2.setColor(Color.YELLOW);
        bds = getBounds(0, 0, blockWidth, 30, scale);
        g2.fillRect(bds.getX(), bds.getY(), bds.getWidth(), bds.getHeight());
        g2.setColor(Color.BLUE);
        bds = getBounds(0, 0, blockWidth, 15, scale);
        g2.fillRect(bds.getX(), bds.getY(), bds.getWidth(), bds.getHeight());
        g2.setColor(Color.YELLOW);
        bds = getBounds(blockWidth / 2, 6, 0, 0, scale);
        GraphicsUtil.drawCenteredText(g2, Name, bds.getX(), bds.getY());
        g2.setColor(Color.BLACK);
        bds = getBounds(0, 0, blockWidth, 30, scale);
        g2.drawRect(bds.getX(), bds.getY(), bds.getWidth(), bds.getHeight());
        g2.setColor(Color.WHITE);
        bds = getBounds(1, 16, blockWidth - 2, 13, scale);
        g2.fillRect(bds.getX(), bds.getY(), bds.getWidth(), bds.getHeight());
        g2.setColor(Color.BLACK);
        g2.drawRect(bds.getX(), bds.getY(), bds.getWidth(), bds.getHeight());
        g2.setColor(Color.MAGENTA);
        bds = getBounds(blockWidth / 2, 21, 0, 0, scale);
        GraphicsUtil.drawCenteredText(
                g2, valid ? String.format("0x%08X", value) : "??????????", bds.getX(), bds.getY());
        g2.dispose();
    }

    /** The highlight of what the last instruction wrote: the row behind the names, and the value's box. */
    static final Color CHANGED_ROW = new Color(255, 140, 0);
    static final Color CHANGED_VALUE = new Color(255, 214, 153);
    private static final Font SMALL_FONT = new Font("SansSerif", Font.PLAIN, 10);
    private static final Font CODE_FONT = new Font("Monospaced", Font.PLAIN, 11);

    /** The width of the register panel and of the CSR panel. */
    public static final int PANEL_WIDTH = 160;
    /** The height of one row of a panel, and of its header. */
    public static final int ROW_HEIGHT = 15;

    /** A panel's yellow background, its outline and its blue header with a title. */
    private static void drawPanel(Graphics2D g, int x, int y, int height, String title) {
        g.setColor(Color.YELLOW);
        g.fillRect(x, y, PANEL_WIDTH, height);
        drawHeader(g, x, y, title);
        g.setColor(Color.BLACK);
        g.drawRect(x, y, PANEL_WIDTH, height);
    }

    private static void drawHeader(Graphics2D g, int x, int y, String title) {
        g.setColor(Color.BLUE);
        g.fillRect(x, y, PANEL_WIDTH, ROW_HEIGHT);
        g.setColor(Color.YELLOW);
        GraphicsUtil.drawCenteredText(g, title, x + PANEL_WIDTH / 2, y + 6);
    }

    /**
     * Draws text at (x, y), aligned as halign says and centred vertically, in a smaller font if it is wider than
     * width.
     */
    private static void drawFitted(Graphics2D g, Font font, String text, int x, int y, int width, int halign) {
        var f = font;
        while (f.getSize2D() > 6 && g.getFontMetrics(f).stringWidth(text) > width) f = f.deriveFont(f.getSize2D() - 0.5f);
        final var old = g.getFont();
        g.setFont(f);
        GraphicsUtil.drawText(g, text, x, y, halign, GraphicsUtil.V_CENTER);
        g.setFont(old);
    }

    private static void drawFitted(Graphics2D g, Font font, String text, int x, int y, int width) {
        drawFitted(g, font, text, x, y, width, GraphicsUtil.H_LEFT);
    }

    /**
     * One row of a panel: the name (or names) on the left and the value in a box on the right, the row and the box
     * highlighted when the last instruction wrote it.
     */
    private static void drawRow(Graphics2D g, int x, int y, String name, String alias, int valueX, String value,
            boolean changed, boolean fixed) {
        if (changed) {
            g.setColor(CHANGED_ROW);
            g.fillRect(x + 1, y, PANEL_WIDTH - 1, ROW_HEIGHT);
        }
        g.setColor(Color.BLACK);
        GraphicsUtil.drawText(g, name, x + 4, y + 6, GraphicsUtil.H_LEFT, GraphicsUtil.V_CENTER);
        if (alias != null) {
            g.setColor(changed ? Color.BLACK : Color.DARK_GRAY);
            GraphicsUtil.drawText(g, alias, x + 29, y + 6, GraphicsUtil.H_LEFT, GraphicsUtil.V_CENTER);
        }
        final int boxWidth = PANEL_WIDTH - 4 - valueX;
        g.setColor(fixed ? Color.BLUE : changed ? CHANGED_VALUE : Color.WHITE);
        g.fillRect(x + valueX, y + 1, boxWidth, 13);
        g.setColor(Color.BLACK);
        g.drawRect(x + valueX, y + 1, boxWidth, 13);
        g.setColor(fixed ? Color.WHITE : Color.BLUE);
        GraphicsUtil.drawCenteredText(g, value, x + valueX + boxWidth / 2, y + 6);
    }

    /**
     * The register panel, PANEL_WIDTH by 33 rows: each row the register's two names (x5 t0) and its value, in hex or
     * in decimal; x0 in blue, as it is fixed; what the last instruction wrote highlighted.
     */
    public static void drawRegisters(
            Graphics2D graphics, int posX, int posY, boolean scale, rv32imData state, boolean hex) {
        drawPanel(graphics, posX, posY, ROW_HEIGHT * 33, "Registers");
        for (int i = 0; i < 32; i++) {
            final long value = state.getX(i);
            final String text = registerText(value, hex);
            drawRow(graphics, posX, posY + ROW_HEIGHT * (i + 1), "x" + i, IntegerRegisters.registerABINames[i], 60,
                    text, state.wasRegisterWritten(i), i == 0);
        }
        graphics.setColor(Color.BLACK);
    }

    /**
     * The instruction at the pc (rv32imData.instructionWordAtPc): its word in the header, and its disassembly below,
     * in a box of width by 30; when the word is not known (-1), why.
     */
    public static void drawInstruction(Graphics2D g, int x, int y, int width, long word, rv32imData state) {
        final long pc = state.getPC().get();
        final String unknown = state.isBusGranted() ? "(bus granted)"
                : !state.getPtw().isIdle() ? "(translating the pc)" : "?";
        g.setColor(Color.BLUE);
        g.fillRect(x, y, width, ROW_HEIGHT);
        g.setColor(Color.YELLOW);
        drawFitted(g, g.getFont(), word < 0 ? "Instruction at PC" : String.format("Instruction 0x%08X", word),
                x + width / 2, y + 7, width - 6, GraphicsUtil.H_CENTER);
        g.setColor(Color.WHITE);
        g.fillRect(x + 1, y + 16, width - 2, 13);
        g.setColor(Color.BLACK);
        g.drawRect(x + 1, y + 16, width - 2, 13);
        g.drawRect(x, y, width, 30);
        g.setColor(Color.BLUE);
        drawFitted(g, CODE_FONT, word < 0 ? unknown : RiscvDisassembler.disassemble(word, pc), x + 4, y + 22, width - 8);
    }

    /** An integer register's value as the panel shows it: eight hex digits, or signed decimal. */
    public static String registerText(long value, boolean hex) {
        return hex ? hex32(value) : String.valueOf(value);
    }

    /**
     * The line under mcause (or scause, for supervisor): its cause spelled out, once a trap has been taken into that
     * mode since the reset; before, the register holds only its reset value, and the line says so.
     */
    public static String causeLine(rv32imData state, boolean supervisor) {
        if (!state.wasTrapTaken(supervisor)) return "no trap yet";
        return causeText(state.getCSRValue(supervisor ? SCSR.SCAUSE.getAddress() : MMCSR.MCAUSE.getAddress()));
    }

    private static String hex32(long value) {
        return String.format("0x%08x", value & 0xffffffffL);
    }

    private static String modeLetter(long mode) {
        return switch ((int) mode) {
            case 3 -> "M";
            case 1 -> "S";
            case 0 -> "U";
            default -> "?";
        };
    }

    private static final String[] EXCEPTIONS = {
        "instr. address misaligned", "instr. access fault", "illegal instruction", "breakpoint",
        "load address misaligned", "load access fault", "store address misaligned", "store access fault",
        "ecall from U-mode", "ecall from S-mode", null, "ecall from M-mode", "instr. page fault", "load page fault",
        null, "store page fault"};
    private static final String[] INTERRUPTS = {
        null, "S software", null, "M software", null, "S timer", null, "M timer", null, "S external", null,
        "M external"};

    /** A cause register's value spelled out: interrupt or exception, its number and its name. */
    static String causeText(long cause) {
        final boolean interrupt = (cause & 0x80000000L) != 0;
        final int code = (int) (cause & 0x7fffffffL);
        final String[] names = interrupt ? INTERRUPTS : EXCEPTIONS;
        final String name = code < names.length ? names[code] : null;
        return (interrupt ? "interrupt " : "exception ") + code + (name != null ? ": " + name : "");
    }

    /** Rows in the CSR panel: machine CSRs, and the supervisor's after them. */
    public static final int MACHINE_ROWS = 12;
    public static final int SUPERVISOR_ROWS = 15;

    /** The CSR panel's height: machine CSRs, or machine and supervisor CSRs. */
    public static int csrPanelHeight(boolean supervisor) {
        return ROW_HEIGHT * (MACHINE_ROWS + (supervisor ? SUPERVISOR_ROWS : 0));
    }

    private static final int CSR_VALUE_X = 62;

    private static int csrRow(Graphics2D g, int x, int y, rv32imData state, String name, int address) {
        drawRow(g, x, y, name, null, CSR_VALUE_X, hex32(state.getCSRValue(address)), state.wasCsrChanged(address),
                false);
        return y + ROW_HEIGHT;
    }

    /** A line of text below a CSR's row, spelling its fields out. */
    private static int detailRow(Graphics2D g, int x, int y, String text, boolean changed) {
        if (changed) {
            g.setColor(CHANGED_VALUE);
            g.fillRect(x + 1, y, PANEL_WIDTH - 1, ROW_HEIGHT);
        }
        g.setColor(Color.BLACK);
        drawFitted(g, SMALL_FONT, text, x + 12, y + 7, PANEL_WIDTH - 16);
        return y + ROW_HEIGHT;
    }

    /**
     * The CSR panel, PANEL_WIDTH by csrPanelHeight: the privilege mode and the machine CSRs, with mstatus and mcause
     * spelled out; with supervisor, the supervisor CSRs and the delegation registers after them. What the last
     * instruction (or the entry to an interrupt's handler) changed is highlighted.
     */
    public static void drawCsrPanel(Graphics2D g, int x, int y, rv32imData state, boolean supervisor) {
        drawPanel(g, x, y, csrPanelHeight(supervisor), "Machine CSRs");
        final long mode = state.getCurrentPrivilegeMode().getValue();
        final String[] modeNames = {"U (user)", "S (supervisor)", "?", "M (machine)"};
        var row = y + ROW_HEIGHT;
        drawRow(g, x, row, "mode", null, CSR_VALUE_X, modeNames[(int) mode], state.wasModeChanged(), false);
        row += ROW_HEIGHT;
        final int mstatusAddress = MMCSR.MSTATUS.getAddress();
        final long mstatus = state.getCSRValue(mstatusAddress);
        row = csrRow(g, x, row, state, "mstatus", mstatusAddress);
        row = detailRow(g, x, row, "MIE=" + ((mstatus >> 3) & 1) + "  MPIE=" + ((mstatus >> 7) & 1) + "  MPP="
                + modeLetter((mstatus >> 11) & 3), state.wasCsrChanged(mstatusAddress));
        row = csrRow(g, x, row, state, "mie", MMCSR.MIE.getAddress());
        row = csrRow(g, x, row, state, "mip", MMCSR.MIP.getAddress());
        row = csrRow(g, x, row, state, "mtvec", MMCSR.MTVEC.getAddress());
        row = csrRow(g, x, row, state, "mepc", MMCSR.MEPC.getAddress());
        final int mcauseAddress = MMCSR.MCAUSE.getAddress();
        row = csrRow(g, x, row, state, "mcause", mcauseAddress);
        row = detailRow(g, x, row, causeLine(state, false),
                state.wasCsrChanged(mcauseAddress));
        row = csrRow(g, x, row, state, "mtval", MMCSR.MTVAL.getAddress());
        row = csrRow(g, x, row, state, "mscratch", MMCSR.MSCRATCH.getAddress());
        if (!supervisor) return;

        drawHeader(g, x, row, "Supervisor CSRs");
        g.setColor(Color.BLACK);
        g.drawLine(x, row, x + PANEL_WIDTH, row);
        row += ROW_HEIGHT;
        final int sstatusAddress = SCSR.SSTATUS.getAddress();
        final long sstatus = state.getCSRValue(sstatusAddress);
        row = csrRow(g, x, row, state, "sstatus", sstatusAddress);
        row = detailRow(g, x, row, "SIE=" + ((sstatus >> 1) & 1) + "  SPIE=" + ((sstatus >> 5) & 1) + "  SPP="
                + (((sstatus >> 8) & 1) != 0 ? "S" : "U"), state.wasCsrChanged(sstatusAddress));
        row = csrRow(g, x, row, state, "sie", SCSR.SIE.getAddress());
        row = csrRow(g, x, row, state, "sip", SCSR.SIP.getAddress());
        row = csrRow(g, x, row, state, "stvec", SCSR.STVEC.getAddress());
        row = csrRow(g, x, row, state, "sepc", SCSR.SEPC.getAddress());
        final int scauseAddress = SCSR.SCAUSE.getAddress();
        row = csrRow(g, x, row, state, "scause", scauseAddress);
        row = detailRow(g, x, row, causeLine(state, true),
                state.wasCsrChanged(scauseAddress));
        row = csrRow(g, x, row, state, "stval", SCSR.STVAL.getAddress());
        row = csrRow(g, x, row, state, "sscratch", SCSR.SSCRATCH.getAddress());
        final int satpAddress = SCSR.SATP.getAddress();
        final long satp = state.getCSRValue(satpAddress);
        row = csrRow(g, x, row, state, "satp", satpAddress);
        row = detailRow(g, x, row, (satp & 0x80000000L) == 0 ? "Bare (no translation)"
                : "Sv32  ASID=" + ((satp >> 22) & 0x1ff) + "  PPN=0x" + Long.toHexString(satp & 0x3fffff),
                state.wasCsrChanged(satpAddress));
        row = csrRow(g, x, row, state, "medeleg", MMCSR.MEDELEG.getAddress());
        csrRow(g, x, row, state, "mideleg", MMCSR.MIDELEG.getAddress());
    }

    public static void drawCpuState(
            Graphics2D g, int x, int y, boolean scale, String Name, rv32imData.CPUState cpuState) {
        Graphics2D g2 = (Graphics2D) g.create();
        Bounds bds;
        if (scale) g2.setFont(AppPreferences.getScaledFont(g.getFont()));
        bds = getBounds(x, y, 0, 0, scale);
        g2.translate(bds.getX(), bds.getY());
        int blockWidth = getBlockWidth(g2, scale);
        if (scale) blockWidth = AppPreferences.getDownScaled(blockWidth);
        g2.setColor(Color.YELLOW);
        bds = getBounds(0, 0, blockWidth, 30, scale);
        g2.fillRect(bds.getX(), bds.getY(), bds.getWidth(), bds.getHeight());
        g2.setColor(Color.BLUE);
        bds = getBounds(0, 0, blockWidth, 15, scale);
        g2.fillRect(bds.getX(), bds.getY(), bds.getWidth(), bds.getHeight());
        g2.setColor(Color.YELLOW);
        bds = getBounds(blockWidth / 2, 6, 0, 0, scale);
        GraphicsUtil.drawCenteredText(g2, Name, bds.getX(), bds.getY());
        g2.setColor(Color.BLACK);
        bds = getBounds(0, 0, blockWidth, 30, scale);
        g2.drawRect(bds.getX(), bds.getY(), bds.getWidth(), bds.getHeight());

        switch(cpuState)
        {
            case RUNNING -> g2.setColor(Color.GREEN);
            case STOPPED -> g2.setColor(Color.RED);
        }
        bds = getBounds(1, 16, blockWidth - 2, 13, scale);
        g2.fillRect(bds.getX(), bds.getY(), bds.getWidth(), bds.getHeight());
        g2.setColor(Color.BLACK);
        g2.drawRect(bds.getX(), bds.getY(), bds.getWidth(), bds.getHeight());
        g2.setColor(Color.MAGENTA);
        bds = getBounds(blockWidth / 2, 21, 0, 0, scale);

        switch (cpuState)
        {
            case SINGLE_STEP:
            case RUNNING:
                g2.setColor(Color.black);
                GraphicsUtil.drawCenteredText(g2, S.get("rv32imCpuStateRunning"), bds.getX(), bds.getY());
                break;
            case STOPPED:
                g2.setColor(Color.cyan);
                GraphicsUtil.drawCenteredText(g2, S.get("rv32imCpuStateStopped"), bds.getX(), bds.getY());
                break;
        }
        g2.dispose();
    }

}
