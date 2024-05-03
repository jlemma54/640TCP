import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Hashtable;
import java.net.DatagramPacket;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Queue;
import java.util.TimerTask;
import java.util.ArrayDeque;
import java.util.TreeMap;

import javax.xml.crypto.Data;

import java.nio.file.Files;


public class Receiver extends Communication {
    public int port; 
    public int mtu; 
    public int sws;
    public int sws_availability;
    public String fileName;
    public DatagramSocket clientSocket; 
    public int ISN;
    public int expectedSeqNum;
    public int seqNum; 
    public String application;
    public FileWriter myWriter;
    public Queue<TCPpack> packetQueue = new ArrayDeque<>();
    public TreeMap<Integer, TCPpack> packetBuffer = new TreeMap<>();
    public int base;
    public int dataPacketCounter = 0;
    public boolean closeConn = false;

    public Receiver(String[] args) {
        extractArgs(args);
        this.expectedSeqNum = 1;
        this.seqNum = 1; 
        this.sws_availability = sws;
        this.base = this.seqNum;
        try{
            myWriter = new FileWriter(fileName);
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.OpenConnections = new Hashtable<String, AckBufferElement[]>();
         try {
            clientSocket = new DatagramSocket(port);
        } catch (Exception e) {
            e.printStackTrace();
        }
        ISN = 0;
        this.timeStarted = System.nanoTime();
    }


    public void receivePacket() throws Exception {

        dataPacketCounter++;
        
        DatagramPacket udpDatagram = udpRecvMessage(clientSocket);
        TCPpack tcpPacket = tcpRecvMessage(udpDatagram);
        int seqNum = tcpPacket.getSequenceNumber();

        
        
        if(tcpPacket.getFinBit() || tcpPacket.getSequenceNumber()==-2){
            sendConnClose(udpDatagram);
            return;
        }

        // if(tcpPacket.getSequenceNumber()==-1){

        // }

        // Process the packet directly if it matches the expected sequence number

        if (seqNum == expectedSeqNum) {
            processPacket(tcpPacket);
            expectedSeqNum += tcpPacket.getTrueLength();
            
            // Check and process any buffered packets that can now be processed
            processBuffer();
        } else if (seqNum > expectedSeqNum) {
            // Store out-of-order packets
            packetBuffer.put(seqNum, tcpPacket);
            this.outOfOrder++;
        }
        
        sendAck(udpDatagram.getAddress(), udpDatagram.getPort(), expectedSeqNum);

    }

    private void processPacket(TCPpack packet) {
        // Process the packet, for example, writing data to a file or passing to an application layer
        String payload = new String(packet.getData());
        System.out.println("Processed packet: " + payload);
        try {
            myWriter.write(payload);
            myWriter.flush();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        // Assume packet processing frees up space equivalent to packet length
    }

    private void processBuffer() {
        while (!packetBuffer.isEmpty() && packetBuffer.firstKey() <= expectedSeqNum) {
            TCPpack nextPacket = packetBuffer.pollFirstEntry().getValue();
            processPacket(nextPacket);
            expectedSeqNum += nextPacket.getTrueLength();
        }
    }

    private void sendAck(InetAddress ip, int port, int ackNumber) throws Exception {
        // Create and send an ACK packet
        TCPpack ackPacket = new TCPpack(ackNumber, new byte[0], false, false, true, ackNumber);

        
        tcpSendMessage(clientSocket, ackPacket, ip, port);
    }


    public void receiverHandshake() throws Exception{

        DatagramPacket udpPacketSyn = udpRecvMessage(clientSocket);
        

        TCPpack receivedSyn = tcpRecvMessage(udpPacketSyn);



        TCPpack sendSynAck = new TCPpack(0, new byte[0], true, false, true, 1);

        //sending synack
        tcpSendMessage(clientSocket, sendSynAck, udpPacketSyn.getAddress(), udpPacketSyn.getPort());


        TCPpack receivedAck = tcpRecvMessage(udpRecvMessage(clientSocket));

        // System.out.println("Receiver has gotten ack");
        if (receivedAck.getSequenceNumber() == this.expectedSeqNum) {

        }

    }

    public void sendConnClose(DatagramPacket packet) throws Exception{

        TCPpack sendFinAck = new TCPpack(0, new byte[0], false, true, true, 0);

        tcpSendMessage(clientSocket, sendFinAck, packet.getAddress(), packet.getPort());

        // System.out.println("Receiver has sent FINACK");

        TCPpack receivedAck = tcpRecvMessage(udpRecvMessage(clientSocket));

        // System.out.println("Receiver has gotten ack");
        closeConn = true;
        myWriter.close();

        this.clientSocket.close();
    }

    // private void deliverToApplication(TCPpack packet) {
    //     // Handle data delivery to the application
    //     String data =  new String(packet.getData());
    //     System.out.println("Delivered to application: " + data);
    //     application += data;
    //     // try{
    //     //     this.myWriter.write(data);
    //     // } catch (Exception e) {
    //     //     e.printStackTrace();
    //     // }
        

    // }

    public void dataControlLoop() throws Exception{
        
        while (true) {
            receivePacket();
            if (closeConn){
                //Closed Conn
                // System.out.println("Closed Connection");
                return;
            }
            // System.out.println("APPLICATION STRING: " + application);


        }
        
        
    }

    public void listen() throws Exception{
        receiverHandshake();
        dataControlLoop();
        // System.out.println("Listening on Port: " + port);
        // TCPpack testPacket = tcpRecvMessage(udpRecvMessage(clientSocket));
        // System.out.println("SEQ NUMBER OF INITIALIZED PACKET: " + testPacket.getSequenceNumber());
        // System.out.println("ACK NUMBER OF INITIALIZED PACKET: " + testPacket.getAckNumber());
        // System.out.println("TIMESTAMP OF INIALIZAED PACKET: " + testPacket.getTimeStamp());
        // System.out.println("LENGTH (WITH FLAGS): " + testPacket.getLengthAndFlags());
        // System.out.println("CHECKSUM: " + testPacket.getCheckSum());
        
        // String testPacketData = new String(testPacket.getData());
        // System.out.println("REAL DATA IN REAL WORLD: " + testPacketData);

    }

    public void eventLoop() {
        //todo TCP handshake

        //Datatransfer




    }

    public void printStats() {
        System.out.println("NUMBER OF BYTES SENT: " + bytesSent);
        System.out.println("NUMBER OF BYTES RECEIVED: " + bytesReceived); 
        System.out.println("NUMBER OF OUT OF ORDER PACKETS " + outOfOrder);
    }



    private void extractArgs(String[] args) {
        this.port = Integer.parseInt(args[1]);
        this.mtu = Integer.parseInt(args[3]);
        this.sws = Integer.parseInt(args[5]);
        this.fileName = args[7];

    }
}