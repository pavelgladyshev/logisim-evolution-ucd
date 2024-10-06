package com.cburch.logisim.riscv.cpu;

import com.cburch.logisim.riscv.cpu.gdb.Packet;
import com.cburch.logisim.util.UniquelyNamedThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;


public class GDBServer extends UniquelyNamedThread {

    private ServerSocket serverSocket;
    private Socket socket;
    private rv32imData cpuData;
    public SynchronousQueue<String> responses = new SynchronousQueue<>();

    static final Logger logger = LoggerFactory.getLogger(GDBServer.class);

    public GDBServer(int port, rv32imData cpuData) throws IOException {
        super("GDBServer");
        serverSocket = new ServerSocket(port);
        this.cpuData = cpuData;

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

    @Override
    public void run() {
        byte[] data = new byte[65536];
        while (true) {
            try {
                logger.info("Waiting for incoming TCP connection on port "+serverSocket.getLocalPort());
                socket = serverSocket.accept();  // wait for incoming connection
                logger.info("Accepted incoming TCP connection");

                InputStream in = socket.getInputStream();
                PrintStream out = new PrintStream(socket.getOutputStream(),true);
                Packet packetResponse = new Packet("");

                for (;;) {

                    int len = in.read(data);
                    if (len < 0) break; // socket has closed

                    Packet packetReceived = new Packet(data, len);
                    //System.out.println("received : " + packetReceived.getPacketData());

                    if(packetReceived.isValid()) {
                            out.print("+");
                            // analyse data and generate response string, send debugger requests to cpu if necessary
                            String response = handle(packetReceived.getPacketData(), cpuData);
                            // send response;
                            packetResponse = new Packet(response);
                            out.print(packetResponse.wrapped());
                    }
                    //if NACK ("-") is received retransmit response
                    else if(packetReceived.isNACK()){
                        out.print(packetResponse.wrapped());
                    }
                    //else if checksum is invalid transmit NACK ("-")
                    else if(!packetReceived.isACK()){
                        out.print("-");
                    }
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

        String field1 = fields[0];

        //System.out.println(command);
        //System.out.println(Arrays.toString(fields));

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
            cpu.setDebuggerRequest(()->{
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
        else if(field1.startsWith("p")){
            cpu.setDebuggerRequest(()->{
                cpu.addDebuggerResponse(formatWordString(cpu.getX(Integer.valueOf(field1.substring(2)))));
                return true;
            });
            response = responses.take();
        }
        else if(field1.startsWith("P")){
            String[] parts = field1.substring(1).split("=");
            int regNum = Integer.parseInt(parts[0], 16);
            int regValue = Integer.reverseBytes((int) Long.parseLong(parts[1], 16));
            cpu.setDebuggerRequest(()->{
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

    private static String formatWordString(long val) {
        return String.format("%08x",Integer.reverseBytes((int)val));
    }
}
