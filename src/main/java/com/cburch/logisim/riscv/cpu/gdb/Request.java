package com.cburch.logisim.riscv.cpu.gdb;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

public abstract class Request {

    private STATUS status;
    private final PipedInputStream pis;
    private final PipedOutputStream pos;

    protected Request() throws IOException {
        this.pos = new PipedOutputStream();
        this.pis = new PipedInputStream(pos);
        this.status = STATUS.WAITING;
    }

    public enum STATUS {
        WAITING(0),
        SUCCESS(1),
        FAILURE(2);

        final int value;

        STATUS(int x){
            value = x;
        }

        public int getValue() {
            return value;
        }
    }

    public STATUS getStatus() {return this.status;}

    public Boolean isSuccess(){
        return status == STATUS.SUCCESS;
    }

    public Boolean isFailure(){
        return status == STATUS.FAILURE;
    }

    public Boolean isPending(){
        return status == STATUS.WAITING;
    }

    private PipedInputStream getInputStream() {
        return pis;
    }

    private PipedOutputStream getOutputStream() {
        return pos;
    }

    private void setStatus(int i) {
        if(i == STATUS.WAITING.getValue()) status = STATUS.WAITING;
        if(i == STATUS.SUCCESS.getValue()) status = STATUS.SUCCESS;
        if(i == STATUS.FAILURE.getValue()) status = STATUS.FAILURE;
    }

    public void waitForAcknowledgement() {
        try {
            getInputStream().read();
            getInputStream().read();
            getInputStream().read();
            setStatus(getInputStream().read());
        }catch (IOException ex){
            ex.printStackTrace();
        }
    }

    public void acknowledgeRequest(Request.STATUS status){
        try {
            getOutputStream().write(status.getValue());
        } catch (IOException ex){
            ex.printStackTrace();
        }
    }

    public static Boolean isSingleStepRequest(Object o){
        return o.getClass().equals(SingleStepRequest.class);
    }

    public static Boolean isMemoryAccessRequest(Object o){
        return o.getClass().equals(MemoryAccessRequest.class);
    }


}
