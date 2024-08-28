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

    public STATUS getStatus() {return this.status;}

    public Boolean isSuccess(){
        return status == STATUS.SUCCESS;
    }

    public Boolean isPending(){
        return status == STATUS.WAITING;
    }

    public static Boolean isSingleStepRequest(Object o){
        return o.getClass().equals(SingleStepRequest.class);
    }

    public static Boolean isMemoryAccessRequest(Object o){
        return o.getClass().equals(MemoryAccessRequest.class);
    }

}
