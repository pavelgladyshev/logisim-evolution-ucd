package com.cburch.logisim.riscv.cpu;

import com.cburch.logisim.data.Value;
import com.cburch.logisim.riscv.cpu.gdb.DebuggerRequest;
import com.cburch.logisim.riscv.cpu.gdb.Packet;
import com.cburch.logisim.util.UniquelyNamedThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.SynchronousQueue;

public class GDBServer extends UniquelyNamedThread {

    private ServerSocket serverSocket;
    private Socket socket;
    private rv32imData cpuData;
    public SynchronousQueue<String> responses = new SynchronousQueue<>();
    private boolean shouldRun = true;

    static final Logger logger = LoggerFactory.getLogger(GDBServer.class);

    public GDBServer(int port, rv32imData cpuData) throws IOException {
        super("GDBServer");
        serverSocket = new ServerSocket(port);
        this.cpuData = cpuData;
        this.cpuData.setGDBServer(this);
    }

    public void closeServerSocket()
    {
        try {
            serverSocket.close();
        } catch(IOException ex){
        //TODO inform user / log error info
            logger.info(ex.toString());
        }
    }

    public void addResponse(String response) {
        try {
            responses.put(response);
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

                InputStream in = socket.getInputStream();
                PrintStream out = new PrintStream(socket.getOutputStream(), true);
                Packet packetResponse = new Packet("");

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
                            } else if (packetReceived.isNACK()) {
                                out.print(packetResponse.wrapped());
                            } else if (!packetReceived.isACK()) {
                                out.print("-");
                            }
                        }
                    }

                    // Check for debugger responses (including breakpoint hits)
                    String debuggerResponse = responses.poll();
                    if (debuggerResponse != null) {
                        Packet responsePacket = new Packet(debuggerResponse);
                        out.print(responsePacket.wrapped());
                    }

                    Thread.sleep(100); // Small delay to avoid busy waiting
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

    public void stopServer() {
        shouldRun = false;
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
            response = responses.take();
        }
        else if(field0.startsWith("p")){
            cpu.setDebuggerRequest((long dataIn)->{
                cpu.addDebuggerResponse(formatWordString(cpu.getX(Integer.valueOf(field0.substring(2)))));
                return true;
            });
            response = responses.take();
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
            response = responses.take();
        }
        else if(field0.startsWith("s")){
            // inst = memRead(pc);
            // cpu.update(inst);
            response = "S05";
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
                response = responses.take();
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
            response = responses.take();
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
                response = responses.take();
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
                response = responses.take();
            } else {
                response = "";  // Unsupported breakpoint type
            }
        }
        else if(field0.equals("c")) {
            // Continue execution
            cpu.setDebuggerRequest((long dataIn) -> {
                cpu.setBreakpointsEnabled(true);
                cpu.setCpuState(rv32imData.CPUState.OPERATIONAL);
                cpu.fetchNextInstruction();
                return true;
            });
            return null; // Don't send immediate response, wait for CPU to stop
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
