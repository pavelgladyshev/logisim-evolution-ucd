package com.cburch.logisim.riscv.cpu;

import com.cburch.logisim.Main;
import com.cburch.logisim.riscv.cpu.gdb.GdbRegisters;
import com.cburch.logisim.riscv.cpu.gdb.MemoryRequest;
import com.cburch.logisim.riscv.cpu.gdb.Packet;
import com.cburch.logisim.riscv.cpu.gdb.TargetDescription;
import com.cburch.logisim.riscv.cpu.gdb.Watchpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

public class GDBServer implements Runnable {

    private ServerSocket serverSocket;
    private Socket socket;
    private rv32imData cpuData;
    public SynchronousQueue<String> debuggerResponse;
    private volatile boolean shouldRun;
    private volatile boolean connected;
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

        // Close client socket if connected
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException ex) {
                report("Error closing client socket: " + ex);
            }
        }

        // Close server socket
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ex) {
                report("Error closing server socket: " + ex);
            }
        }

        // Interrupt and wait for thread to finish
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(1000); // Wait up to 1 second
            } catch (InterruptedException ex) {
                report("Interrupted while waiting for GDB server thread to stop");
            }
        }
    }

    /**
     * The server's messages. In the --tty modes the terminal is the simulated console, and its output (System.out,
     * where the logger writes) is the program's: they are not shown there.
     */
    private static void report(String message) {
        if (!Main.headless) {
            logger.info(message);
        }
    }

    /**
     * A reply from the CPU (the simulation thread) to the server's thread. It waits for the server to take it while a
     * gdb is connected, and is dropped when none is: a stop reply then has no one to go to, and the simulation does
     * not wait for one.
     */
    public void setDebuggerResponse(String response) {
        try {
            while (!debuggerResponse.offer(response, 20, TimeUnit.MILLISECONDS)) {
                if (!connected || !shouldRun) return;
            }
        } catch (InterruptedException e) {
            if (Main.headless) {
                System.err.println("GDB server: interrupted while adding response: " + e);
            } else {
                logger.error("Interrupted while adding response", e);
            }
        }
    }

    /**
     * The largest packet the server takes (qSupported's PacketSize, in hex), its data without the $ and #xx around
     * it, which the input buffer holds too.
     */
    static final int PACKET_SIZE = 0x10000;

    @Override
    public void run() {
        while (shouldRun) {
            final Socket client;
            try {
                report("Waiting for incoming TCP connection on port " + serverSocket.getLocalPort());
                client = serverSocket.accept();  // wait for incoming connection
            } catch (IOException e) {
                if (serverSocket.isClosed() || !shouldRun) {
                    report("Stopping");   // gdbServer is being stopped
                    return;
                }
                continue;
            }
            report("Accepted incoming TCP connection");
            socket = client;
            connected = true;
            try {
                serve(client);
            } catch (InterruptedException e) {
                return;   // the server is being stopped
            } catch (IOException e) {
                // the connection broke (gdb gone): as when it closes
                report("Incoming TCP connection lost: " + e.getMessage());
                release(cpuData);
            } finally {
                connected = false;
                try {
                    client.close();
                } catch (IOException e) {
                    // closed already
                }
            }
            report("Incoming TCP connection closed");
        }
        report("Stopping");
    }

    /**
     * One gdb's session. It ends with D (detach), k (kill) or the connection closing; after k or a close without D
     * (gdb quit, crashed or its terminal closed) the CPU runs on, without the session's breakpoints and watchpoints,
     * as after a detach, and the server waits for the next gdb.
     */
    private void serve(Socket client) throws IOException, InterruptedException {
        client.setKeepAlive(true);
        client.setTcpNoDelay(true);
        client.setSoTimeout(10);   // read() waits at most 10 ms: the CPU's stop replies go out in between
        final InputStream in = client.getInputStream();
        final OutputStream out = new BufferedOutputStream(client.getOutputStream());
        final byte[] buffer = new byte[PACKET_SIZE + 4];
        int length = 0;
        String lastSent = "";

        attach(cpuData);

        while (shouldRun) {
            int n;
            try {
                n = in.read(buffer, length, buffer.length - length);
            } catch (SocketTimeoutException e) {
                n = 0;
            }
            if (n < 0) {
                release(cpuData);   // closed without D
                return;
            }
            length += n;

            int position = 0;
            while (position < length) {
                final int unit = Packet.unitLength(buffer, position, length);
                if (unit == 0) break;   // the rest of a packet is still to come
                final Packet packetReceived = new Packet(buffer, position + unit, position);
                position += unit;

                if (packetReceived.isValid()) {
                    send(out, "+");
                    final String command = packetReceived.getPacketData();
                    if (command.equals("k")) {
                        // kill: the CPU runs on, as after a close; there is no reply
                        out.flush();
                        release(cpuData);
                        return;
                    }
                    // analyse data and generate response string, send debugger requests to cpu if necessary
                    String response;
                    try {
                        response = handle(command, cpuData);
                    } catch (RuntimeException e) {
                        response = "E01";   // a malformed packet: an error, and the session goes on
                    }
                    if (null != response) {
                        lastSent = new Packet(response).wrapped();
                        send(out, lastSent);
                    }
                    // if this was a debugging termination request "D",
                    // close the socket and go back to listening for incoming connections
                    if (command.startsWith("D")) {
                        out.flush();
                        return;
                    }
                } else if (packetReceived.isNACK()) {
                    send(out, lastSent);
                } else if (packetReceived.isCtrlC()) {
                    interrupt(cpuData);   // the stop reply comes when the CPU has stopped
                } else if (!packetReceived.isACK()) {
                    send(out, "-");
                }
            }
            System.arraycopy(buffer, position, buffer, 0, length - position);
            length -= position;
            if (length == buffer.length) {
                length = 0;   // a packet longer than PacketSize: dropped
                send(out, "-");
            }

            // Check for debugger responses (including breakpoint hits)
            String stopReply = debuggerResponse.poll();
            if (stopReply != null) {
                lastSent = new Packet(stopReply).wrapped();
                send(out, lastSent);
            }
            out.flush();
        }
    }

    private static void send(OutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.ISO_8859_1));
    }

    /**
     * A gdb has connected: the CPU stops at its next fetch, if it is running, and the breakpoints and watchpoints of
     * an earlier session go (gdb puts in its own). No stop reply: gdb asks with ?.
     */
    private void attach(rv32imData cpu) throws InterruptedException {
        cpu.setDebuggerRequest((long dataIn) -> {
                cpu.setCpuState(rv32imData.CPUState.STOPPED);
                cpu.clearBreakpointsAndWatchpoints();
                cpu.addDebuggerResponse("S05");
                return true;
        });
        while (shouldRun && debuggerResponse.poll(100, TimeUnit.MILLISECONDS) == null) {
            // waiting for the CPU's next fetch
        }
    }

    /**
     * The session ended without a detach: the CPU runs on as after D, without the session's breakpoints. Not when the
     * server itself is being stopped (the CPU's reset, or the circuit closing): the CPU's own state stays then.
     */
    private void release(rv32imData cpu) {
        if (!shouldRun) return;
        cpu.setDebuggerRequest((long dataIn) -> {
                cpu.clearBreakpointsAndWatchpoints();
                cpu.setBreakpointsEnabled(false);
                cpu.setCpuState(rv32imData.CPUState.RUNNING);
                cpu.fetchNextInstruction();
                return true;
        });
    }

    /**
     * Ctrl-C: the CPU stops at its next fetch, and the stop reply is T02 (SIGINT). If it has stopped already (a
     * breakpoint as gdb sent Ctrl-C), that stop's reply is the one gdb gets.
     */
    private static void interrupt(rv32imData cpu) {
        cpu.setDebuggerRequest((long dataIn) -> {
                if (cpu.getCpuState() != rv32imData.CPUState.STOPPED) {
                    cpu.setCpuState(rv32imData.CPUState.STOPPED);
                    cpu.addDebuggerResponse("T02");
                }
                return true;
        });
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
                    response = String.format("PacketSize=%x;qXfer:features:read+", PACKET_SIZE);
                    break;
                }
                case "Xfer" : {
                    response = TargetDescription.xfer(command);
                    break;
                }
                case "Attached" : {
                    response = "1";
                    break;
                }
                case "Rcmd" : {
                    response = monitor(fields.length > 1 ? fields[1] : "", cpu);
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
                // vCont? gets the empty reply: gdb then uses c and s (it would use vCont only with C and S too)
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
            // p n: register n, in hex, numbered as GdbRegisters says
            final int regNum;
            try {
                regNum = Integer.parseInt(field0.substring(1), 16);
            } catch (NumberFormatException e) {
                return "E00";
            }
            if (!GdbRegisters.exists(regNum)) return "E00";
            cpu.setDebuggerRequest((long dataIn)->{
                    cpu.addDebuggerResponse(GdbRegisters.read(cpu, regNum));
                    return true;
            });
            response = debuggerResponse.take();
        }
        else if(field0.startsWith("P")){
            // P n=r: register n, in hex, gets the value r, its bytes in the target's order
            String[] parts = field0.substring(1).split("=");
            final int regNum;
            final long regValue;
            try {
                regNum = Integer.parseInt(parts[0], 16);
                regValue = GdbRegisters.parseValue(parts[1]);
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                return "E00";
            }
            if (!GdbRegisters.exists(regNum)) return "E00";
            cpu.setDebuggerRequest((long dataIn)->{
                    cpu.addDebuggerResponse(GdbRegisters.write(cpu, regNum, regValue));
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
            // m addr,length: the hart's addresses, virtual in S- and U-mode with Sv32 (MemoryRequest)
            final long address;
            final long count;
            try {
                address = Long.parseLong(field0.substring(1), 16);
                count = Long.parseLong(fields[1], 16);
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                return "E00";
            }
            cpu.setDebuggerRequest(MemoryRequest.read(cpu, address, count));
            response = debuggerResponse.take();
        }
        else if(field0.startsWith("M")){
            // M addr,length:XX...: written as m reads
            final long address;
            final byte[] data;
            try {
                address = Long.parseLong(field0.substring(1), 16);
                final long count = Long.parseLong(fields[1], 16);
                data = fields.length > 2 ? hexStringToByteArray(fields[2]) : new byte[0];
                if (data.length != count) return "E00";
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                return "E00";
            }
            cpu.setDebuggerRequest(MemoryRequest.write(cpu, address, data));
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
            } else if (Watchpoint.Kind.ofPacketType(type) != null && fields.length > 2) {
                // Z2, Z3, Z4 addr,length: a write, read or access watchpoint
                final Watchpoint watchpoint = watchpoint(type, address, fields[2]);
                if (watchpoint == null) return "E00";
                cpu.setDebuggerRequest((long dataIn) -> {
                        cpu.setWatchpoint(watchpoint);
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
            } else if (Watchpoint.Kind.ofPacketType(type) != null && fields.length > 2) {
                final Watchpoint watchpoint = watchpoint(type, address, fields[2]);
                if (watchpoint == null) return "E00";
                cpu.setDebuggerRequest((long dataIn) -> {
                        cpu.removeWatchpoint(watchpoint);
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

    /** The watchpoint of a Z or z packet of type 2, 3 or 4, or null if its length is not a hex number. */
    private static Watchpoint watchpoint(int type, long address, String length) {
        try {
            return new Watchpoint(Watchpoint.Kind.ofPacketType(type), address, Long.parseLong(length, 16));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static final String MONITOR_HELP = "Logisim RV32IM CPU's GDB server commands:\n"
            + "  monitor reset   reset the CPU (as its Reset pin does) and stop it at its reset address\n"
            + "  monitor help    this list\n";

    /**
     * qRcmd,command: gdb's monitor command, the command in hex. The reply is the command's output, in hex (gdb manual,
     * "General Query Packets").
     */
    private String monitor(String hexCommand, rv32imData cpu) throws InterruptedException {
        final String command;
        try {
            command = new String(hexStringToByteArray(hexCommand), StandardCharsets.ISO_8859_1).trim();
        } catch (RuntimeException e) {
            return "E00";
        }
        final String output;
        if (command.equals("reset") || command.equals("reset halt")) {
            cpu.setDebuggerRequest((long dataIn) -> {
                    cpu.resetForDebugger();
                    cpu.addDebuggerResponse(String.format(
                            "CPU reset: stopped at 0x%08x (gdb shows the new registers after "
                            + "maintenance flush register-cache)\n",
                            cpu.getPC().get()));
                    return true;
            });
            output = debuggerResponse.take();
        } else if (command.equals("help") || command.isEmpty()) {
            output = MONITOR_HELP;
        } else {
            output = "Unknown command: " + command + "\n" + MONITOR_HELP;
        }
        final StringBuilder hex = new StringBuilder();
        for (byte b : output.getBytes(StandardCharsets.ISO_8859_1)) {
            hex.append(String.format("%02x", b & 0xff));
        }
        return hex.toString();
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