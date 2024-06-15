package com.cburch.logisim.riscvim32cpu;

import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;

import java.util.Arrays;
import java.util.List;

public class RV32imComponents extends Library {
    public static final String _ID = "RVIM32";

    private final List<AddTool> tools;

    public RV32imComponents() {
        tools = Arrays.asList(new AddTool(new RV32imCPU()));
    }

    /** Returns the name of the library that the user will see. */
    @Override
    public String getDisplayName() {
        return "RISCV_IM32";
    }

    /** Returns a list of all the tools available in this library. */
    @Override
    public List<AddTool> getTools() {
        return tools;
    }
}
