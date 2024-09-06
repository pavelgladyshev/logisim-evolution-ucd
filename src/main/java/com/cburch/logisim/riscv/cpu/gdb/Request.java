package com.cburch.logisim.riscv.cpu.gdb;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

public abstract class Request {

    private STATUS status;
    private final PipedInputStream pis;
    private final PipedOutputStream pos;
    private final long startTime;
    private static final long timeoutNano = 5_000_000_000L;

    protected Request() throws IOException {
        this.pos = new PipedOutputStream();
        this.pis = new PipedInputStream(pos);
        this.status = STATUS.WAITING;
        this.startTime = System.nanoTime();
    }

    public enum STATUS {
        WAITING,
        SUCCESS,
        FAILURE
    }

    public abstract boolean isComplete();

    public STATUS getStatus() {return this.status;}

    public Boolean isSuccess(){
        return status == STATUS.SUCCESS;
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

    public void setStatus(STATUS status) {
        this.status = status;
    }

    public void waitForAcknowledgement() {
        try {
            getInputStream().read();
        }catch (IOException ex){
            ex.printStackTrace();
        }
    }

    public void acknowledgeRequest(Request.STATUS status){
        try {
            getOutputStream().write(1);
            setStatus(status);
        } catch (IOException ex){
            ex.printStackTrace();
        }
    }

    public static Boolean isSingleStepRequest(Object o){
        return o.getClass().equals(StepRequest.class);
    }

    public static Boolean isContinueRequest(Object o){
        return o.getClass().equals(ContinueRequest.class);
    }

    public static Boolean isMemoryAccessRequest(Object o){
        return o.getClass().equals(MemoryAccessRequest.class);
    }

    public boolean isStale(){
        return System.nanoTime() > (startTime + timeoutNano);
    }


}
