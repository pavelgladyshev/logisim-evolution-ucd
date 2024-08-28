package com.cburch.logisim.riscv.cpu.gdb;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;

public class SingleStepRequest extends Request {

    private final long address;

    public SingleStepRequest(long address) {
        this.address = address;
        setStatus(STATUS.WAITING);
    }

    public long getAddress() {
        return address;
    }

}
