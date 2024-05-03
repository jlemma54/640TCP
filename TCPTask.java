import java.util.TimerTask;

public class TCPTask extends TimerTask {

    public AckBufferElement bufferElement;
    public Sender sender;
  

    public TCPTask(AckBufferElement bufferElement,Sender sender) {
        this.sender = sender;
        this.bufferElement = bufferElement;

    }



    @Override
    public void run() {

        // System.out.println("here?");

        // System.out.println("Timer Task is running for this seq number: "+ this.bufferElement.getPacket().getSequenceNumber());
        if (this.bufferElement.getRetransmitCount() == 17) {
            System.exit(1);
        }

        this.bufferElement.incrementRetransmission();
        sender.retransmission++;

        //try to resend a message
        // System.out.println("RESENDING!!!!");
        try{
            if (this.bufferElement.getPacket() == null) {
                System.out.println("sender is null");
            }

            this.sender.tcpSendMessage(sender.serverSocket, this.bufferElement.getPacket(), sender.remoteIP, sender.remotePort);

            if (this.bufferElement.getPacket().getSequenceNumber()==1){
                // System.out.println("ALLAHU AKBAR ARE RETRANSMITTING 1");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }


        //we now need to create a new task to see if it gets resent
        TCPTask tryagain = new TCPTask(this.bufferElement, sender);
        // if (tasksSet == null) {System.out.println("DEEZ");}
        sender.scheduTimer.schedule(tryagain, 1000);
        sender.taskSet.add(tryagain);



        sender.taskSet.remove(this);


    }
    
    
}
