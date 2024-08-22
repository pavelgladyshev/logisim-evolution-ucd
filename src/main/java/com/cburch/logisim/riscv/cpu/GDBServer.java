package com.cburch.logisim.riscv.cpu;

import com.cburch.logisim.riscv.cpu.gdb.Packet;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class GDBServer implements Runnable{

    private ServerSocket serverSocket;
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private Thread gdbserver;
    private rv32imData cpu;

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

    public static final String ACK = "+";
    public static final String NACK = "-";

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

                while (socket.isConnected()) {

                    byte[] packetData = new byte[65536];
                    int len;

                    // in.read();
                    len = in.read(packetData);

                    // if (Thread.interrupted()) { do termination stuff followed by return; }
                    if(Thread.interrupted()) {
                        terminate();
                        return;
                    }

                    Packet packet = new Packet(packetData, len);

                    if(packet.isValidPacketData()) {
                            printer.print("+");
                            // analyse data and manipulate cpu object accordingly

                            // send response;
                            printer.print("$#00");
                    }
                }

            } catch (IOException e) {
                //TODO inform user / log error info
                continue; // try again (?)
            }
        }
    }

    public void terminate()
    {
        // do any cleanup required
        gdbserver.interrupt();
    }
}
