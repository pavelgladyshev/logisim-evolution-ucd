package com.cburch.logisim.riscv.cpu;

import com.cburch.logisim.data.Value;
import com.cburch.logisim.riscv.cpu.gdb.DebuggerRequest;
import com.cburch.logisim.riscv.cpu.gdb.Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.SynchronousQueue;

public class GDBServer implements Runnable {

    private ServerSocket serverSocket;
    private Socket socket;
    private rv32imData cpuData;
    public SynchronousQueue<String> debuggerResponse;
    private boolean shouldRun;
    private Thread thread;

    static final Logger logger = LoggerFactory.getLogger(GDBServer.class);

    public void startGDBServer(int port, rv32imData cpuData) throws IOException {
        this.serverSocket = new ServerSocket(port);
        this.debuggerResponse = new SynchronousQueue<>();
        this.cpuData = cpuData;
        this.cpuData.setGDBServer(this);
        this.thread = new Thread(this);
        this.shouldRun = true;
        this.thread.start();
    }

    public void stopGDBServer() {
        shouldRun = false;
        if (null != serverSocket) {
            try {
                serverSocket.close();
            } catch (IOException ex) {
                //TODO inform user / log error info
                logger.info(ex.toString());
            }
        }
        if (null != thread) {
            while (thread.isAlive()) {
                try {
                    Thread.sleep(10);
                } catch(InterruptedException ex) {
                    continue;
                }
            }
        };
    }

    public void setDebuggerResponse(String response) {
        try {
            debuggerResponse.put(response);
        } catch (InterruptedException e) {
            logger.error("Interrupted while adding response", e);
        }
    }

    @Override
    public void run() {
        byte[] data = new byte[65536];
        while (shouldRun) {
            try {
                logger.info("Waiting for incoming TCP connection on port " + serverSocket.getLocalPort());
                socket = serverSocket.accept();  // wait for incoming connection
                logger.info("Accepted incoming TCP connection");
                socket.setKeepAlive(true);

                InputStream in = socket.getInputStream();
                PrintStream out = new PrintStream(socket.getOutputStream(), true);
                Packet packetResponse = new Packet("");

                // stop CPU if currently running (as if GDB user pressed Ctrl-C)
                handle("\u0003",cpuData);

                while (shouldRun) {
                    // Check if there's incoming data
                    if (in.available() > 0) {
                        int len = in.read(data);
                        if (len < 0) break; // socket has closed

                        int processedBytes = 0;
                        while (processedBytes < len) {
                            Packet packetReceived = new Packet(data, len, processedBytes);
                            processedBytes += packetReceived.getProcessedBytes();

                            if (packetReceived.isValid()) {
                                out.print("+");
                                // analyse data and generate response string, send debugger requests to cpu if necessary
                                String response = handle(packetReceived.getPacketData(), cpuData);
                                // send response;
                                if (null != response) {
                                    packetResponse = new Packet(response);
                                    out.print(packetResponse.wrapped());
                                }
                                // if this was a debugging termination request "D",
                                // close the socket and go back to listening for incoming connections
                                if (packetReceived.getPacketData().equals("D")) {
                                    out.flush();
                                    socket.close();
                                    break;
                                }
                            } else if (packetReceived.isNACK()) {
                                out.print(packetResponse.wrapped());
                            } else if (packetReceived.isCtrlC()) {
                                handle(packetReceived.getPacketData(), cpuData);
                                packetResponse = new Packet("OK");
                                out.print(packetResponse.wrapped());
                            } else if (!packetReceived.isACK()) {
                                out.print("-");
                            }
                        }
                    }

                    // Check for debugger responses (including breakpoint hits)
                    String debuggerResponse = this.debuggerResponse.poll();
                    if (debuggerResponse != null) {
                        Packet responsePacket = new Packet(debuggerResponse);
                        out.print(responsePacket.wrapped());
                    }

                    Thread.sleep(10); // Small delay to avoid busy waiting
                }
            } catch (Exception e) {
                //TODO improve exception handling
                if (serverSocket.isClosed()) {
                    // gdbServer is being stopped
                    logger.info("Stopping");
                    return;
                }
                logger.info("Incoming TCP connection closed");
                continue; // try again
            }
        }
    }

    private String handle(String command, rv32imData cpu) throws InterruptedException {
        String[] fields = command.split("[:,;,,]");
        String response = "";

        String field0 = fields[0];

        //System.out.println(command);
        //System.out.println(Arrays.toString(fields));

        if(field0.startsWith("q")){
            switch(field0.substring(1)){
                case "Supported" : {
                    response = "PacketSize=65536;qXfer:features:read+";
                    break;
                }
                case "Xfer" : {
                    response = "l<target version=\"1.0\"><architecture>riscv:rv32</architecture></target>";
                    break;
                }
                case "Attached" : {
                    response = "1";
                    break;
                }
            }
        }
        else if(field0.startsWith("Q")){
            switch(field0.substring(1)){

            }
        }
        else if(field0.startsWith("v")){
            switch(field0.substring(1)){
                case "Cont?" :
                    response = "vCont;s;c;";
                    break;

                case "MustReplyEmpty" :
                    response = "";
                    break;
            }
        }
        else if(field0.startsWith("g")){
            cpu.setDebuggerRequest((long dataIn)->{
                StringBuilder resp = new StringBuilder();
                for (int i=0; i<32; i++) {
                    resp.append(formatWordString(cpu.getX(i)));
                }
                resp.append(formatWordString(cpu.getPC().get()));
                cpu.addDebuggerResponse(resp.toString());
                    return true;
            });
            response = debuggerResponse.take();
        }
        else if(field0.startsWith("p")){
            cpu.setDebuggerRequest((long dataIn)->{
                    cpu.addDebuggerResponse(formatWordString(cpu.getX(Integer.valueOf(field0.substring(2)))));
                    return true;
            });
            response = debuggerResponse.take();
        }
        else if(field0.startsWith("P")){
            String[] parts = field0.substring(1).split("=");
            int regNum = Integer.parseInt(parts[0], 16);
            int regValue = Integer.reverseBytes((int) Long.parseLong(parts[1], 16));
            cpu.setDebuggerRequest((long dataIn)->{
                    if (regNum == 32) {
                        cpu.getPC().set(regValue);
                    } else {
                        cpu.setX(regNum, regValue);
                    }
                    cpu.addDebuggerResponse("OK");
                    return true;
            });
            response = debuggerResponse.take();
        }
        else if(field0.startsWith("s")){
            cpu.setDebuggerRequest((long dataIn) -> {
                    cpu.setCpuState(rv32imData.CPUState.SINGLE_STEP);
                    cpu.fetchNextInstruction();
                    return true;
            });
            return null; // Don't send immediate response, wait for CPU to stop
        }
        else if(field0.startsWith("m")){
            cpu.setDebuggerRequest(new DebuggerRequest() {
                    long address = Long.parseLong(field0.substring(1),16);
                    long count = Long.valueOf(fields[1],16);
                    boolean addressing = false;
                    StringBuilder resp = new StringBuilder();

                    @Override
                    public boolean process(long dataIn) {
                        if (addressing) {
                            // add read byte to string.
                            long shiftBits = (address & 0x3) * 8;
                            resp.append(String.format("%02x", (dataIn >> shiftBits) & 0xff));
                            address++;
                            count--;
                        }
                        if (count == 0) {
                            cpu.addDebuggerResponse(resp.toString());
                            addressing = false;
                            cpu.setMemRead(Value.FALSE);
                            cpu.setMemWrite(Value.FALSE);
                            return true;
                        } else {
                            cpu.setAddress(Value.createKnown(32, address));
                            cpu.setMemRead(Value.TRUE);
                            cpu.setMemWrite(Value.FALSE);
                            addressing = true;
                            return false;
                        }
                    }
            });
            response = debuggerResponse.take();
        }
        else if(field0.startsWith("M")){
            cpu.setDebuggerRequest(new DebuggerRequest() {
                    long address = Long.parseLong(field0.substring(1),16);
                    long count = Long.valueOf(fields[1],16);
                    byte[] data = hexStringToByteArray(fields[2]);
                    int i=0;

                    @Override
                    public boolean process(long dataIn) {
                        if (count == i) {
                            cpu.addDebuggerResponse("OK");
                            cpu.setOutputDataWidth(0);
                            cpu.setMemRead(Value.FALSE);
                            cpu.setMemWrite(Value.FALSE);
                            return true;
                        } else {
                            cpu.setAddress(Value.createKnown(32, address));
                            cpu.setOutputData(data[i]);
                            cpu.setOutputDataWidth(1);
                            cpu.setMemRead(Value.TRUE);
                            cpu.setMemWrite(Value.TRUE);
                            i++;
                            address++;
                            return false;
                        }
                    }
            });
            response = debuggerResponse.take();
        }
        else if(field0.startsWith("Z")) {
            // Set breakpoint
            int type = Integer.parseInt(field0.substring(1, 2));
            long address = Long.parseLong(fields[1], 16);
            
            if (type == 0 || type == 1) {  // Software or hardware breakpoint
                cpu.setDebuggerRequest((long dataIn) -> {
                        cpu.setBreakpoint(address);
                        cpu.addDebuggerResponse("OK");
                        return true;
                });
                response = debuggerResponse.take();
            } else {
                response = "";  // Unsupported breakpoint type
            }
        }
        else if(field0.startsWith("z")) {
            // Remove breakpoint
            int type = Integer.parseInt(field0.substring(1, 2));
            long address = Long.parseLong(fields[1], 16);
            
            if (type == 0 || type == 1) {  // Software or hardware breakpoint
                cpu.setDebuggerRequest((long dataIn) -> {
                        cpu.removeBreakpoint(address);
                        cpu.addDebuggerResponse("OK");
                        return true;
                });
                response = debuggerResponse.take();
            } else {
                response = "";  // Unsupported breakpoint type
            }
        }
        else if(field0.equals("c")) {
            // Continue execution
            cpu.setDebuggerRequest((long dataIn) -> {
                    cpu.setBreakpointsEnabled(true);
                    cpu.setCpuState(rv32imData.CPUState.RUNNING);
                    cpu.fetchNextInstruction();
                    return true;
            });
            return null; // Don't send immediate response, wait for CPU to stop
        }
        else if(field0.startsWith("\u0003")) {
            // Ctrl-C interruption
            cpu.setDebuggerRequest((long dataIn) -> {
                    if (cpu.getCpuState() != rv32imData.CPUState.STOPPED) {
                        cpu.setCpuState(rv32imData.CPUState.SINGLE_STEP);
                    }
                    //cpu.addDebuggerResponse("T05");
                    return true;
            });
            return "OK";
        }
        else if(field0.startsWith("D")) {
            // termination of debug session by the remote: resume CPU
            cpu.setDebuggerRequest((long dataIn) -> {
                    cpu.setBreakpointsEnabled(false);
                    cpu.setCpuState(rv32imData.CPUState.RUNNING);
                    cpu.fetchNextInstruction();
                    return true;
            });
            return "OK";
        }
        else switch (field0) {
                case "?" : {
                    response = "S05";
                    break;
                }
        }
        return response;
    }

    private static String formatWordString(long val) {
        return String.format("%08x",Integer.reverseBytes((int)val));
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
}