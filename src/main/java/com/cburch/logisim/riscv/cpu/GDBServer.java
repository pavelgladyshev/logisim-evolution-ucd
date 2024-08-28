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

    public GDBServer(int port, rv32imData cpuData) {
        try {
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
                System.out.println("Waiting for TCP connection");
                socket = serverSocket.accept();  // wait for incoming connection
                System.out.println("Accepted incoming TCP connection");

                in = socket.getInputStream();
                out = socket.getOutputStream();

                PrintStream printer = new PrintStream(out,true);
                Packet packetResponse = new Packet("");

                while (socket.isConnected()) {

                    byte[] data = new byte[65536];
                    int len;

                    // in.read();
                    len = in.read(data);

                    if(Thread.interrupted()) {
                        terminate();
                        return;
                    }

                    Packet packetReceived = new Packet(data, len);
                    //System.out.println("received : " + packetReceived.getPacketData());

                    if(packetReceived.isValid()) {
                            printer.print("+");
                            // analyse data and manipulate cpu object accordingly
                            String response = handle(packetReceived.getPacketData());
                            // send response;
                            packetResponse = new Packet(response);
                            printer.print(packetResponse.wrapped());
                    }
                    //if NACK ("-") is received retransmit response
                    else if(packetReceived.isNACK()){
                        printer.print(packetResponse.wrapped());
                    }
                    //else if checksum is invalid transmit NACK ("-")
                    else if(!packetReceived.isACK()){
                        printer.print("-");
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
        String response = "";

        String field1 = fields[0];

        System.out.println(command);
        System.out.println(Arrays.toString(fields));

        if(field1.startsWith("q")){
            switch (field1.substring(1)) {
                case "Supported" -> {
                    response = "PacketSize=65536;qXfer:features:read+";
                }
                case "Xfer" -> {
                    response = "l<target version=\"1.0\"><architecture>riscv:rv32</architecture></target>";
                }
                case "Attached" -> {
                    response = "1";
                }
                case "Symbol" -> {
                    response = "OK";
                }
            }
        }
        else if(field1.startsWith("Q")){
            switch(field1.substring(1)){

            }
        }
        else if(field1.startsWith("v")){
            if (field1.substring(1).equals("MustReplyEmpty")) {
                response = "";
            }
        }
        else if(field1.startsWith("m")){
            parseMemoryAccessRequest(MEMREAD, fields);
            System.out.println(request.isSuccess());
            if (request.isSuccess()) response = ((MemoryAccessRequest) request).getDataBuffer().toString();
        }
        else if(field1.startsWith("M")){
            parseMemoryAccessRequest(MEMWRITE, fields);
            if (request.isSuccess()) response = "OK";
            //else send error message
        }
        else switch (field1) {
                case "?" -> {
                    response = "S05";
                }
                case "g" -> {
                    for (int i = 0; i < 32; i++) {
                        response += bigToLittleEndian(cpu.getX(i));
                    }
                    response += bigToLittleEndian(cpu.getPC().get());
                }
                case "s" -> {
                    long address;
                    if (fields.length == 1) address = cpu.getPC().get();
                    else address = Long.parseLong(field1.substring(1), 16);
                    request = new SingleStepRequest(address);
                    System.out.println("Waiting for CPU to finish stepping through.");
                    while (request.isPending()) {
                    }
                    if (request.isSuccess()) response = "S13";
                }
            }
        return response;
    }

    public Boolean isRequestPending(){
        if(request == null) return false;
        else return request.isPending();
    }
    public Request getRequest(){
        return request;
    }

    private void parseMemoryAccessRequest(MemoryAccessRequest.TYPE type, String[] fields){
        long address = Long.parseLong(fields[0].substring(1), 16);
        System.out.println(address);
        long bytes = Long.parseLong(fields[1], 16);
        System.out.println(bytes);
        //create request
        if(type.equals(MEMWRITE)) {
            String data = fields[2];
            request = new MemoryAccessRequest(MEMWRITE, address, bytes, data);
        }
        else request = new MemoryAccessRequest(MEMREAD, address, bytes);
        System.out.println("Waiting for CPU to finish memory access operation.");
        while(request.isPending()){
        }
    }

    public String bigToLittleEndian(long x){
        int value = (int) x;
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.asIntBuffer().put(value);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        return String.format("%08x",buffer.asIntBuffer().get());
    }
}
