package com.cburch.sample;

import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Components extends Library
{
    private ArrayList<Tool> tools;

    public Components() {
        try {
            this.tools = new ArrayList<>();
            this.tools.add(new AddTool(new Cpu()));
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        }
    }

    public String getName() {
        return "Sample CPU";
    }

    @Override
    public List<? extends Tool> getTools() {
        return this.tools;
    }

    public boolean removeLibrary(String var1) {
        return true;
    }
}
