import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Hashtable;
import java.util.Timer;

/**
 * An abstract class that contains fields and methods that will be implemented 
 * by both the sender and receiver.
 */
abstract class Communication {
    public long timeStarted;

    //TODO, implement the concept of an ack buffer and methods to interact with it. 
    //when either Sender/Reciever send a packet they must store this in some kind of buffer
    //and when they receive a packet (that is an ack) they must go through and check the buffer for this packet to remove it

    //Todo: lowkey may need needs to change the ArrayList to a custom type that encapsulates TCPpack along with other stuff like window size info and timeout stuff 
    protected Hashtable<String, AckBufferElement[]> OpenConnections;
    protected Timer scheduTimer;
    protected int sws;
    public int bytesSent;
    public int bytesReceived;
    public int outOfOrder; 
    public int  retransmission;
    public int duplicateAcks;

    public void prettyPrint(TCPpack pack, boolean isSend) {
        if (isSend) {
            System.out.print("snd ");
        } else {
            System.out.print("rcv "); 
        }

        System.out.print((System.nanoTime() - timeStarted)/1000000000.0);
        System.out.print(" ");

        if (pack.getSynBit()) {
            System.out.print("S ");
        } else {
            System.out.print("- ");
        }

        if (pack.getAckBit() || pack.getAckNumber() != 0) {
            System.out.print("A ");
        } else {
            System.out.print("- ");
        }

        if (pack.getSequenceNumber() == -2) {
            System.out.print("F ");
        } else {
            System.out.print("- ");
        }

        if (pack.getTrueLength() > 0) {
            System.out.print("D ");
        } else {
            System.out.print("- ");
        }

        System.out.print(pack.getSequenceNumber() + " ");

        System.out.print(pack.getTrueLength() + " ");

        System.out.print(pack.getAckNumber());
        System.out.println("");

        

        
    }

    //method for a socket to receive a UDP datagram
    DatagramPacket udpRecvMessage(DatagramSocket socket) throws Exception{
        byte[] receiveData = new byte[1024];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        socket.receive(receivePacket);
        return receivePacket;
    }

    //helper method to create a string representaton of IP addr + port number from UDP datagram
    public String stringifyUDPIP(DatagramPacket packet) {
        return packet.getAddress().toString() + ":" + Integer.toString(packet.getPort());

    }

    //TODO
    

    //this method will check the IP of a UDP datagram packet to see if there is an already established connection, will insert to hashtable
    //will return True if packet causes a created connection
    //will return False if it does not
    public boolean checkToConnect(DatagramPacket packet) throws Exception{
        //peeks into the UDP datagram packet, extracts the tcp info, and checks if seqnum = 0 (seqnum of 0 means we establish a new connection)
        if (TCPpack.deserializePacket(packet.getData()).getSequenceNumber() != 0) {return false;}

        //not in open connections
        if (OpenConnections.get(stringifyUDPIP(packet)) == null) {
            OpenConnections.put(stringifyUDPIP(packet), new AckBufferElement[sws]);
            
        } else {
            //this should never happen, if packet has a seq num of 0, it should not have an open connection
            throw new Exception("Error, received packet with seq num 0 but already have an established connection with Host");
        }

        return true;
        
    }

    // public void insertAckBuffer()

    //method to receive and extract a tcp packet from a UDP datagram
    public TCPpack tcpRecvMessage(DatagramPacket message) throws Exception{

        //extracting bytes from Datagram packet
        byte[] data = message.getData();

        //deserializing bytes into a TCPpack object and returning it
        TCPpack pack = TCPpack.deserializePacket(data);

        bytesReceived += pack.getTrueLength();

        prettyPrint(pack, false);
        return pack;
    }

    //method to send a TCPPack object over a UDP datagram socket
    public void tcpSendMessage(DatagramSocket socket, TCPpack packet, InetAddress clientAddress, int clientPort) throws Exception{
        
        bytesSent += packet.getTrueLength();

        prettyPrint(packet, true);

        //serialize the TCPpack into a byte array
        byte[] serializedPacket = packet.serializePacket();

        //initialize DatagramPacket to send with serialized TCP packet info as well as the client address/port
        DatagramPacket sendPacket = new DatagramPacket(serializedPacket, serializedPacket.length, clientAddress, clientPort);

        //send UDP datagram packet over the open socket
        socket.send(sendPacket);
    }
    

    
}