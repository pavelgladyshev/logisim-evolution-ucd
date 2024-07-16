package com.cburch.logisim.riscv;

public enum UCSR {
    FFLAGS(0x001),
    FRM(0x002),
    FCSR(0x003),
    CYCLE(0xC00),
    TIME(0xC01),
    INSTRET(0xC02),
    HPMCOUNTER3(0xC03),
    HPMCOUNTER4(0xC04),
    HPMCOUNTER5(0xC05),
    HPMCOUNTER6(0xC06),
    HPMCOUNTER7(0xC07),
    HPMCOUNTER8(0xC08),
    HPMCOUNTER9(0xC09),
    HPMCOUNTER10(0xC0A),
    HPMCOUNTER11(0xC0B),
    HPMCOUNTER12(0xC0C),
    HPMCOUNTER13(0xC0D),
    HPMCOUNTER14(0xC0E),
    HPMCOUNTER15(0xC0F),
    HPMCOUNTER16(0xC10),
    HPMCOUNTER17(0xC11),
    HPMCOUNTER18(0xC12),
    HPMCOUNTER19(0xC13),
    HPMCOUNTER20(0xC14),
    HPMCOUNTER21(0xC15),
    HPMCOUNTER22(0xC16),
    HPMCOUNTER23(0xC17),
    HPMCOUNTER24(0xC18),
    HPMCOUNTER25(0xC19),
    HPMCOUNTER26(0xC1A),
    HPMCOUNTER27(0xC1B),
    HPMCOUNTER28(0xC1C),
    HPMCOUNTER29(0xC1D),
    HPMCOUNTER30(0xC1E),
    HPMCOUNTER31(0xC1F),
    CYCLEH(0xC80),
    TIMEH(0xC81),
    INSTRETH(0xC82),
    HPMCOUNTER3H(0xC83),
    HPMCOUNTER4H(0xC84),
    HPMCOUNTER5H(0xC85),
    HPMCOUNTER6H(0xC86),
    HPMCOUNTER7H(0xC87),
    HPMCOUNTER8H(0xC88),
    HPMCOUNTER9H(0xC89),
    HPMCOUNTER10H(0xC8A),
    HPMCOUNTER11H(0xC8B),
    HPMCOUNTER12H(0xC8C),
    HPMCOUNTER13H(0xC8D),
    HPMCOUNTER14H(0xC8E),
    HPMCOUNTER15H(0xC8F),
    HPMCOUNTER16H(0xC90),
    HPMCOUNTER17H(0xC91),
    HPMCOUNTER18H(0xC92),
    HPMCOUNTER19H(0xC93),
    HPMCOUNTER20H(0xC94),
    HPMCOUNTER21H(0xC95),
    HPMCOUNTER22H(0xC96),
    HPMCOUNTER23H(0xC97),
    HPMCOUNTER24H(0xC98),
    HPMCOUNTER25H(0xC99),
    HPMCOUNTER26H(0xC9A),
    HPMCOUNTER27H(0xC9B),
    HPMCOUNTER28H(0xC9C),
    HPMCOUNTER29H(0xC9D),
    HPMCOUNTER30H(0xC9E),
    HPMCOUNTER31H(0xC9F);

    private final int address;

    UCSR(int address) {
        this.address = address;
    }

    public int getAddress() {
        return address;
    }

    public static long getValue(rv32imData hartData, UCSR csr) {
        return hartData.getCSRValue(csr.getAddress());
    }

}
