class AckBufferElement { 
    private int retransmitCount; 
    private TCPpack packet;
    private int ackCounter;


    public AckBufferElement(TCPpack packet) {
        this.packet = packet;
        this.ackCounter = 0; 
        this.retransmitCount = 0;
    }


    public void incrementAckCounter() {this.ackCounter += 1;}

    public void incrementRetransmission() {this.retransmitCount+= 1;}

    public int getAckCounter() {return this.ackCounter;}

    public int getRetransmitCount() {return this.retransmitCount;} 

    public TCPpack getPacket() {return this.packet;}

    public boolean isAcked() {return ackCounter > 0;}



    
}