package com.cburch.logisim.riscv.cpu;

import com.cburch.logisim.riscv.cpu.gdb.Packet;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

public class GDBServer implements Runnable{

    private ServerSocket serverSocket;
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private Thread gdbserver;
    private rv32imData cpuData;

    public GDBServer(int port, rv32imData cpuData) {
        try {
            serverSocket = new ServerSocket(port);
            this.cpuData = cpuData;
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
                            String response = handle(packetReceived.getPacketData(), cpuData);
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
        gdbserver.interrupt();
    }

    public static String handle(String command, rv32imData cpu) {
        String[] fields = command.split("[:,;,,]");
        String response = "";

        String field1 = fields[0];

        System.out.println(command);
        System.out.println(Arrays.toString(fields));

        if(field1.startsWith("q")){
            switch(field1.substring(1)){
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
        else if(field1.startsWith("Q")){
            switch(field1.substring(1)){

            }
        }
        else if(field1.startsWith("v")){
            switch(field1.substring(1)){
                case "MustReplyEmpty" : {
                    response = "";
                    break;
                }
                case "Cont?" : {
                    response = "vCont;s;c;";
                    break;
                }
            }
        }
        else if(field1.startsWith("g")){
            response = cpu.getIntegerRegisters().readAllRegisters();
        }
        else if(field1.startsWith("p")){
            response = String.format("%08x", cpu.getX(Integer.valueOf(field1.substring(2))));
        }
        else if(field1.startsWith("P")){
            writeRegister(field1.substring(1), cpu);
        }
        else if(field1.startsWith("s")){
            // inst = memRead(pc);
            // cpu.update(inst);
            response = "S05";
        }
        else switch (field1) {
                case "?" : {
                    response = "S05";
                    break;
                }
        }
        return response;
    }

    private static void writeRegister(String command, rv32imData cpu) {
        String[] parts = command.split("=");
        int regNum = Integer.parseInt(parts[0], 16);
        int regValue = (int) Long.parseLong(parts[1], 16);
        cpu.setX(regNum, regValue);
    }
}
