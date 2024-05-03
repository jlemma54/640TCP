import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Timer;
import java.util.Map;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;



public class Sender extends Communication {
    public int port; 
    public InetAddress remoteIP; 
    public int remotePort; 
    public String fileName;
    public int mtu; 
    public int sws;
    public int sw_availability; 
    public DatagramSocket serverSocket; 
    public int ISN; 
    public long ERTT; //previous estimated round trip
    public long estimatedDeviation; 
    public long timeOut;
    public HashSet<TCPTask> taskSet;
    public int seqNum;
    public int base;
    public TreeMap<Integer, AckBufferElement> buffer = new TreeMap<Integer, AckBufferElement>();
    public BufferedReader fis;
    public FileReader fileReader;
    


    double alpha = 0.875;
    double beta = 0.75;
    
    public Sender(String[] args) throws Exception{
        extractArgs(args);
        this.sw_availability = this.sws * this.mtu;
        // System.out.println("INITIAL SW_AVAILABILITY: "+ this.sw_availability);
        this.taskSet = new HashSet<TCPTask>();
        this.OpenConnections = new Hashtable<String, AckBufferElement[]>();
        this.timeOut = 5000;
        this.base = 1; 
        this.seqNum = 1;
        this.timeStarted = System.nanoTime();
        
        fileReader = new FileReader(fileName);
        fis = new BufferedReader(fileReader);
        

        try{
            serverSocket = new DatagramSocket(port);
        } catch (Exception e) {
            e.printStackTrace();
        }

        ISN = 0;
        
    }

    public long updateTimeout(TCPpack pack) throws Exception{
        if (pack.getSequenceNumber() == 0) {
            //First calculation with no prior data, initializing values
            this.ERTT = System.nanoTime() - pack.getTimeStamp();
            this.estimatedDeviation = 0;
            this.timeOut = 2*ERTT;
        } else {
            long SRTT = System.nanoTime() - pack.getTimeStamp();
            long SDEV = (long) (Math.abs(SRTT - this.ERTT));
            this.ERTT =  (long) ((alpha * this.ERTT) + ((1-alpha)*SRTT));
            this.estimatedDeviation = (long)((beta*this.estimatedDeviation) + (1-beta)*SDEV);
            this.timeOut = ERTT + 4*this.estimatedDeviation;
        }

        return this.timeOut;
    }

    public void insertTaskAndSchedule(AckBufferElement elem) {
        if(elem.getPacket().getSequenceNumber()==1){
            // System.out.println("We are scheduling task 1 NOW!!!====>" );
        }
        TCPTask task = new TCPTask(elem, this);
        this.scheduTimer.schedule(task, 1000);

        taskSet.add(task);
    }

    public synchronized void clientSend(TCPpack packet) throws Exception {
        int seqNum = packet.getSequenceNumber();
        //check if packet to send is in sliding window
        // System.out.println("GLUG Seq Num: " + seqNum + " First Byte after Window: "  + (base+ (sws * mtu)) + "SW: " + sw_availability + "Base: " + base);
        
        //if the packet we try to send is in the window that the sender believes to be true
        if (sw_availability > 0 && seqNum >= base && seqNum <= base+ (sws * mtu)) {

            //putting packet(buffer element) into the buffer
            AckBufferElement packElement = new AckBufferElement(packet);
            this.buffer.put(seqNum, packElement);

            //sending message
            tcpSendMessage(serverSocket, packet, remoteIP, remotePort);
            // System.out.println("Packet Sent\nSeq Num: " + this.seqNum + "\n");

            int packetLength = packet.getTrueLength();

            //decreasing sw_availability
            sw_availability -= packetLength;

            //increasing base and currentSeqNum of sender
            // this.base += packetLength;
            this.seqNum += packetLength;

            

            insertTaskAndSchedule(packElement);

            
        } else {
            // System.out.println("Window is full or packet out of range");
        }
    }

    public void killTask(AckBufferElement elem) {
        // System.out.println("In kill task");
        Iterator<TCPTask> iterator = taskSet.iterator();

        //Go through the list of tasks that we have
        while (iterator.hasNext()) {
            TCPTask task = iterator.next();
            //For each task, we check if the tasks buffer element's seq number is equal to the seq number of 
            //the task we wish to kill
            if (task.bufferElement.getPacket().getSequenceNumber() == elem.getPacket().getSequenceNumber()) {
                task.cancel();
                iterator.remove();  // Safe way to remove elements while iterating
                // System.out.println("successfully killed task with seq number: " + elem.getPacket().getSequenceNumber());
                break;
            }
        }
    }

    public synchronized void handleAck(int ackNumber) throws Exception{

        // System.out.println("handling ack!");

        //we just remove an element from our window (buffer) when the packets in our buffer have a
        //sequence number strictly less than ack number
        // System.out.println("Buffer Size: " + buffer.size() + "First Key: " + buffer.firstKey() + "Ack Number: " + ackNumber);
        while (buffer.size() > 0 && buffer.firstKey() < ackNumber) { //issue here
            AckBufferElement element = buffer.remove(buffer.firstKey());
            //we have just removed the lowest valued packet (that is in contigous order) in the window, must increment base
            this.base += element.getPacket().getTrueLength();

            //increment the sw_availability since the amount of contigous space has increase
            this.sw_availability += element.getPacket().getTrueLength();
            // System.out.println("INCREMENTED WINDOW SIZE");

            //kill the respective task in the queue
            killTask(element);
        }

        //checking for duplicate acks in the window

        //flag to see if retransmit has happened (to ensure that we don't retransmit THREE times.)
        HashSet<Integer> retransmit = new HashSet<Integer>();

        

        //Go through every element in the window (buffer) and check if the seq number is equal to the ack given 
        for (Map.Entry<Integer, AckBufferElement> entry: buffer.entrySet()) {
            AckBufferElement element = entry.getValue();
            if (element.getPacket().getSequenceNumber() == ackNumber-1-element.getPacket().getTrueLength()) {
                // System.out.println("going through element with seq:") ;
                element.incrementAckCounter(); //keep track for triple duplicate ack check
                killTask(element); //remove the timer for timeout, because its been acked

                if (element.getAckCounter() > 1) {
                    this.duplicateAcks++;
                }

                //check if packet has been triple duplicate acked
                if (element.getAckCounter() == 4) {
                    // retransmit.add(ackNumber);

                    //begin retransmission

                    //NOTE: during retransmission, we don't manipulate base of the sliding window,
                    //or the most recent sequence number the sender is aware of being sent  
                    tcpSendMessage(serverSocket, element.getPacket(), remoteIP, remotePort);

                    element.incrementRetransmission(); //keep track for infinite retransmit threshold

                    insertTaskAndSchedule(element); //insert task into data struct, and then start the task timer

                }
            }


        }
    }
    /**
     * We call this to listen for an ack from the reciever (called in data control loop)
     * @param packet
     * @throws Exception
     */
    
    public synchronized int clientRecv(DatagramPacket packet) throws Exception{
        // System.out.println("IS THIS HAPPENING?");
        TCPpack tcpPack = tcpRecvMessage(packet);

        updateTimeout(tcpPack);

        //if this packet is in ack we need to go through buffer and initiate removal and possible retransmission
        handleAck(tcpPack.getAckNumber());

        return tcpPack.getAckNumber();
        //check to clear buffer (situation in which every entry in the buffer has been acked)

    }

    public boolean canSend() {
        return this.sw_availability > 0;
    }

    
    

    public byte[][] addJunk(byte[][] data) {
        
        byte[][] newData = new byte[data.length+1][mtu];

        for (int i = 0; i < data.length; i++) {
            
            for (int j = 0; j < mtu; j++) {
                newData[i][j] = data[i][j];
            }
        }

        for (int i = 0; i < mtu; i++) {
            newData[data.length] = "___".getBytes();
        }

        return newData;
    }



    

    public void testSend() throws Exception{
        String test1 = "hello there ";
        String test2 = "world whats ";
        String test3 = "thats super ";

        String test4 = "junk junkcat";


        // System.out.println(test4.getBytes().length);

        ArrayList<byte[]> data = new ArrayList<byte[]>();
        String temp = "";
        for (int i = 0; i < test4.length(); i++) {
            if (temp.length() == mtu) {
                data.add(temp.getBytes());
                temp = "";
            }
            temp += test4.substring(i, i+1);
            
        }
        data.add(temp.getBytes());

        for (byte[] s: data) {
            System.out.println(new String(s));
        }


    byte[][] fakeData = new byte[data.size()][];

    for (int i = 0; i < data.size(); i++) {
        fakeData[i] = data.get(i);
    }

 

    // for (byte[] arr: fakeData) {
    //     System.out.println(new String(arr)); 
    // }

    senderHandshake();
    
    
    DataTransferControl(fakeData, remoteIP.getHostAddress() + remotePort);
    // DataTransferControl(addJunk(fakeData), remoteIP.getHostAddress() + remotePort);        
    }

    public synchronized byte[] generateData() throws Exception{

        char[] temp = new char[mtu]; 
        int read = fis.read(temp, 0, mtu);
        
        if (read == -1) {return null;}

        // System.out.println("DATA IN DATA: " + new String(temp));
        return new String(temp).getBytes();
            }

    


    public synchronized void DataTransferControl(byte[][] data, String ipPort) throws Exception{

        this.scheduTimer = new Timer();

        int dataIndex = 0; 

        // int[] seqNums = {1, 4, 7};
        // System.out.println("FINAL EXPECTED ACK: " + finalExpectedAck);
        int lastAckRecieved = -1;


        while (true) {
            // System.out.println("in loop");
            if (canSend()) {
                //generate a packet
                byte[] fileData = generateData();
                if (fileData != null) {
                    TCPpack packet = new TCPpack(this.seqNum, fileData, false, false, false, 0);
                    clientSend(packet);

                } else {
                    //clientRecv(udpRecvMessage(serverSocket));

                    break;
                }

            } else {
                // System.out.println("before listen");
                clientRecv(udpRecvMessage(serverSocket));
                // System.out.println("After listen");
            }
        }
        // System.out.println("out of loop");
        finAck();
        this.scheduTimer.cancel();

        return;

        
        
    }

    public void finAck() throws Exception{
        // System.out.println("IN FIN ACK");
        TCPpack finPack = new TCPpack(-2, new byte[0], false, true, false, 0);

        for (int i=0;i<25;i++){
            tcpSendMessage(serverSocket, finPack, remoteIP, remotePort);
        }

        this.serverSocket.close();
    }



    public void senderHandshake() throws Exception{

        //initializing openConnections
        this.OpenConnections.put(remoteIP.getHostAddress() + remotePort, new AckBufferElement[sws]);


        TCPpack synPack = new TCPpack(0, new byte[0], true, false, false, 0);
        
        tcpSendMessage(serverSocket, synPack, remoteIP, remotePort);

        // System.out.println("Sent syn");
        TCPpack synAck = tcpRecvMessage(udpRecvMessage(serverSocket));
        // System.out.println("Received SynAck");

        TCPpack ack = new TCPpack(0, new byte[0], false, false, true, 1);
        tcpSendMessage(serverSocket, ack, remoteIP, remotePort);
        // System.out.println("Sent Ack");

    }

    public void eventLoop() throws Exception {
        //TODO: TCP Handshake

        //TODO: DataTransfer

    }

    public void printStats() {
        System.out.println("NUMBER OF BYTES SENT: " + bytesSent);
        System.out.println("NUMBER OF BYTES RECEIVED: " + bytesReceived); 
        System.out.println("NUMBER OF RETRANSMISSIONS: " + retransmission);
        System.out.println("NUMBER OF DUPLICATE ACKS: " + duplicateAcks);
    }

    private void extractArgs(String[] args) throws Exception{
        this.port = Integer.parseInt(args[1]);
        this.remoteIP = InetAddress.getByName(args[3]);
        this.remotePort = Integer.parseInt(args[5]);
        this.fileName = args[7];
        this.mtu = Integer.parseInt(args[9]);
        this.sws = Integer.parseInt(args[11]); 
    }
}
