package com.cburch.logisim.riscv.cpu.gdb;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;

public class SingleStepRequest extends Request {
    public SingleStepRequest() {
        setStatus(STATUS.WAITING);
    }
}
