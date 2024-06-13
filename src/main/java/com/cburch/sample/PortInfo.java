package com.cburch.sample;

public class PortInfo {
    public final String name;
    public final String type;
    public final String exclusive;
    public final int bitWidth;

    private PortInfo(String var1, String var2, String var3, int var4) {
        this.name = var1;
        this.type = var2;
        this.exclusive = var3;
        this.bitWidth = var4;
    }

    public static PortInfo simpleOutput(String var0, int var1) {
        return new PortInfo(var0, "output", "exclusive", var1);
    }

    public static PortInfo sharedOutput(String var0, int var1) {
        return new PortInfo(var0, "output", "shared", var1);
    }

    public static PortInfo simpleInput(String var0, int var1) {
        return new PortInfo(var0, "input", "shared", var1);
    }

    public static PortInfo simpleOutput(String var0) {
        return new PortInfo(var0, "output", "exclusive", 1);
    }

    public static PortInfo sharedOutput(String var0) {
        return new PortInfo(var0, "output", "shared", 1);
    }

    public static PortInfo simpleInput(String var0) {
        return new PortInfo(var0, "input", "shared", 1);
    }
}
