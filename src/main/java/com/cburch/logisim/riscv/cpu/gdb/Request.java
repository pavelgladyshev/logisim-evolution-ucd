package com.cburch.logisim.riscv.cpu.gdb;

public abstract class Request {

    private STATUS status;

    public enum STATUS {
        WAITING,
        SUCCESS,
        FAILURE
    }

    public void setStatus(STATUS status) {
        this.status = status;
    }

    public Boolean isSuccess(){
        return status.equals(STATUS.SUCCESS);
    }

    public Boolean isPending(){
        return status.equals(STATUS.WAITING);
    }

}
