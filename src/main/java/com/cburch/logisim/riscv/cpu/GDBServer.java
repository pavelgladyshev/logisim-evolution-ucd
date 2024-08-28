package com.cburch.logisim.riscv.cpu;

import com.cburch.logisim.riscv.cpu.gdb.Packet;
import com.cburch.logisim.riscv.cpu.gdb.MemoryAccessRequest;
import com.cburch.logisim.riscv.cpu.gdb.Request;
import com.cburch.logisim.riscv.cpu.gdb.SingleStepRequest;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static com.cburch.logisim.riscv.cpu.gdb.MemoryAccessRequest.TYPE.MEMREAD;
import static com.cburch.logisim.riscv.cpu.gdb.MemoryAccessRequest.TYPE.MEMWRITE;

public class GDBServer implements Runnable{


    private ServerSocket serverSocket;
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private Thread gdbserver;
    private rv32imData cpu;
    private Request request;
    private Object monitor;

    public GDBServer(int port, rv32imData cpuData, Object monitor) {
        try {
            this.monitor = monitor;
            serverSocket = new ServerSocket(port);
            cpu = cpuData;
            gdbserver = new Thread(this);
                gdbserver.start();

        }
        catch(IOException ex){
            //TODO inform user / log error info
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                synchronized (monitor) {
                    System.out.println("Waiting for TCP connection");
                    socket = serverSocket.accept();  // wait for incoming connection
                    System.out.println("Accepted incoming TCP connection");

                    in = socket.getInputStream();
                    out = socket.getOutputStream();

                    PrintStream printer = new PrintStream(out, true);
                    Packet packetResponse = new Packet("");

                    while (socket.isConnected()) {

                        byte[] data = new byte[65536];
                        int len;

                        // in.read();
                        len = in.read(data);

                        if (Thread.interrupted()) {
                            terminate();
                            return;
                        }

                        Packet packetReceived = new Packet(data, len);
                        //System.out.println("received : " + packetReceived.getPacketData());

                        if (packetReceived.isValid()) {
                            printer.print("+");
                            // analyse data and manipulate cpu object accordingly
                            String response = handle(packetReceived.getPacketData());
                            // send response;
                            packetResponse = new Packet(response);
                            printer.print(packetResponse.wrapped());
                        }
                        //if NACK ("-") is received retransmit response
                        else if (packetReceived.isNACK()) {
                            printer.print(packetResponse.wrapped());
                        }
                        //else if checksum is invalid transmit NACK ("-")
                        else if (!packetReceived.isACK()) {
                            printer.print("-");
                        }
                    }
                }
            } catch (IOException e) {
                //TODO inform user / log error info
                continue; // try again (?)
            }
        }
    }

    public void terminate() {
        // do any cleanup required
        if(gdbserver != null) gdbserver.interrupt();
    }

    public String handle(String command) {
        String[] fields = command.split("[:,;,,]");
        StringBuilder response = new StringBuilder();

        String field1 = fields[0];

        System.out.println(command);
        System.out.println(Arrays.toString(fields));

        if(field1.startsWith("q")){
            switch (field1.substring(1)) {
                case "Supported" -> {
                    response.append("PacketSize=65536;qXfer:features:read+");
                }
                case "Xfer" -> {
                    response.append("l<target version=\"1.0\"><architecture>riscv:rv32</architecture></target>");
                }
                case "Attached" -> {
                    response.append("1");
                }
                case "Symbol" -> {
                    response.append("OK");
                }
            }
        }
        else if(field1.startsWith("Q")){
            switch(field1.substring(1)){

            }
        }
        else if(field1.startsWith("v")){
            if (field1.substring(1).equals("MustReplyEmpty")) {
            }
        }
        else if(field1.startsWith("m")){
                parseMemoryAccessRequest(MEMREAD, fields);
                if (request.isSuccess()) {
                    response.append(((MemoryAccessRequest)request).getDataBuffer());
                }
        }
        else if(field1.startsWith("M")){
            parseMemoryAccessRequest(MEMWRITE, fields);
            if (request.isSuccess()) response = new StringBuilder("OK");
            //else send error message
        }
        else switch (field1) {
                case "?" -> {
                    response = new StringBuilder("S05");
                }
                case "g" -> {
                    for (int i = 0; i < 32; i++) {
                        response.append(bigToLittleEndian4(cpu.getX(i)));
                    }
                    response.append(bigToLittleEndian4(cpu.getPC().get()));
                }
                case "s" -> {
                    request = new SingleStepRequest();
                    try{
                        monitor.wait();
                        if (request.isSuccess()) response.append("S05");
                    }
                    catch(InterruptedException ex){
                        ex.printStackTrace();
                    }
                }
            }
        return response.toString();
    }

    public Boolean isRequestPending(){
        if(request == null) return false;
        else return request.isPending();
    }
    public Request getRequest(){
        return request;
    }

    private void parseMemoryAccessRequest(MemoryAccessRequest.TYPE type, String[] fields) {
        long address = Long.parseLong(fields[0].substring(1), 16);
        int bytes = (int) Long.parseLong(fields[1], 16);
        //create request
        if(type.equals(MEMWRITE)) {
            String data = fields[2];
            request = new MemoryAccessRequest(MEMWRITE, address, bytes, data);
        }
        else request = new MemoryAccessRequest(MEMREAD, address, bytes);
        try{
            monitor.wait();
        }
        catch(InterruptedException ex){
            ex.printStackTrace();
        }
    }

    public String bigToLittleEndian4(long x){
        ByteBuffer buffer = bigToLittleEndian(x, 4);
        return String.format("%08x",buffer.asIntBuffer().get());
    }

    private ByteBuffer bigToLittleEndian(long x, int bytes){
        int value = (int) x;
        ByteBuffer buffer = ByteBuffer.allocate(bytes);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.asIntBuffer().put(value);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        return buffer;
    }
}
