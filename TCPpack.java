import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class TCPpack implements Serializable {
    private int sequenceNumber;
    private int ackNumber;
    private byte[] data;
    private int length;
    private long timeStamp;
    private int checksum; 

    public TCPpack(){}


    public TCPpack(int sequenceNumber, byte[] data, boolean synBit, boolean finBit, boolean ackBit, int ackNumber) throws Exception{
        this.sequenceNumber = sequenceNumber;

        this.ackNumber = ackBit ? ackNumber:0;

        this.data = data;

        int mask = ((synBit ? 0b100 : 0) | (finBit ? 0b010 : 0) | (ackBit ? 0b001 : 0)); 

        this.length = (data.length << 3) | mask;
        this.timeStamp = System.nanoTime();

        this.checksum = calculateCheckSum(this.serializePacket());
    }
    


    public TCPpack(int sequenceNumber, byte[] data, boolean synBit, boolean finBit, boolean ackBit, int ackNumber, long timeStamp, int checksum) throws Exception{
        this.sequenceNumber = sequenceNumber;

        this.ackNumber = ackBit ? ackNumber:0;

        this.data = data;

        this.length = (data.length << 3) & ((synBit ? 0b100 : 0) | (finBit ? 0b010 : 0) | (ackBit ? 0b001 : 0));

        this.checksum = calculateCheckSum(this.serializePacket());
        this.timeStamp = timeStamp;
        this.checksum = checksum;
    }

    //getters
    public int getLengthAndFlags() {return this.length;}
    public int getTrueLength() {return this.length >> 3;}
    public int getSequenceNumber() {return this.sequenceNumber;}
    public int getAckNumber() {return this.ackNumber;}
    public boolean getSynBit() {return (this.length & 0x4000) == 1;}
    public boolean getFinBit() {return (this.length & 0x2000) == 1;}
    public boolean getAckBit() {return (this.length & 0x1000) == 1;}
    public byte[] getData() {return this.data;}
    public long getTimeStamp() {return this.timeStamp;}
    public int getPaddedCheckSum() {return this.checksum;}
    public short getCheckSum() {return (short) (this.checksum & 0xFFFF);}

    //setters
    public void setSequenceNum(int sequenceNumber) {this.sequenceNumber = sequenceNumber;}
    public void setackNumber(int ackNumber) {this.ackNumber = ackNumber;}
    public void setData(byte[] data) {this.data = data;}
    public void setLength(int length) {this.length = length;}
    public void setTimeStamp(long timeStamp) {this.timeStamp = timeStamp;}
    public void setChecksum(int checksum) {this.checksum = checksum;}

    public short extractLeastSignificant16Bits() {return (short) (this.checksum & 0xFFFF);}
    
    public void setAllFields(int sequenceNumber, int ackNumber, byte[] data, int length, long timeStamp, int checksum) {
        setSequenceNum(sequenceNumber);
        setackNumber(ackNumber);
        setData(data);
        setLength(length);
        setTimeStamp(timeStamp);
        setChecksum(checksum);
    }

    public void writeObject(ObjectOutputStream out) throws IOException {
        // Serialize fields manually
        out.writeInt(sequenceNumber);
        out.writeInt(ackNumber);
        out.writeLong(timeStamp);

        // Handle the Boolean flags together if needed, as per the diagram format
        // Assuming flags can be represented in 3 bits, with synBit as the MSB, ackBit as the middle, and finBit as the LSB
        out.writeInt(this.length);
        out.writeInt(checksum);
        out.write(this.data);
    }

    public static TCPpack readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        // Deserialize fields manually in the same order as written
        int packet_sequenceNumber = in.readInt();
        // System.out.println("Deserialized packet sequence num: " + packet_sequenceNumber );
        int packet_ackNumber = in.readInt();
        long packet_timeStamp = in.readLong();
        int packet_length = in.readInt();
        int packet_checksum = in.readInt();

        // Read data length and data
        byte[] dataBuffer = new byte[packet_length >> 3];
        in.readFully(dataBuffer);
        // String packet_data = new String(dataBuffer);

        TCPpack packet = new TCPpack();
        packet.setAllFields(packet_sequenceNumber, packet_ackNumber, dataBuffer, packet_length, packet_timeStamp, packet_checksum);



        return packet;

        
    }

    public byte[] serializePacket() throws Exception{
        ByteArrayOutputStream baso = new ByteArrayOutputStream();

        ObjectOutputStream oos = new ObjectOutputStream(baso);

        this.writeObject(oos);


        oos.flush(); 

        return baso.toByteArray();
    }

    public static TCPpack deserializePacket(byte[] data) throws Exception{

        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        ObjectInputStream ois = new ObjectInputStream(bais);

        TCPpack extractedPacket = (TCPpack) TCPpack.readObject(ois);

        return extractedPacket;
    }

    

    

    

    public int calculateCheckSum(byte[] sendData) throws Exception{


        int sum = 0;
        int length = sendData.length;
        int i = 0;

        // Process each 16-bit segment
        while (i + 1 < length) {
            int word = ((sendData[i] << 8) & 0xFF00) | (sendData[i + 1] & 0xFF); // Combine two bytes to form a 16-bit word
            sum += word;
            if ((sum & 0xFFFF0000) > 0) { // Check for carry
                sum &= 0xFFFF; // Keep only the lower 16 bits
                sum++;          // Add carry to the least significant bit
            }
            i += 2;
        }

        // If there's a single byte left
        if (i < length) {
            int word = (sendData[i] << 8) & 0xFF00; // Shift left and pad with zero
            sum += word;
            if ((sum & 0xFFFF0000) > 0) {
                sum &= 0xFFFF;
                sum++;
            }
        }

        // Bitwise NOT of the lower 16 bits and zero out the upper 16 bits
        sum = ~sum & 0xFFFF;

        // The checksum is an int with the upper 16 bits zeroed out
        return sum;
    }

}