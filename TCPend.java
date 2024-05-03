public class TCPend {


    public static void main(String[] args) {

        


        switch (args.length) {
            case 8:
                Receiver recv = new Receiver(args);

                try{
                    recv.listen();
                    recv.printStats();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                
                break;
            case 12:
                try{
                    Sender send = new Sender(args);
                    send.testSend();
                    send.printStats();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
                // System.out.println("send");
                break;

            default:
                throw new IllegalArgumentException("Incorrect number of arguments: "+ args.length);

        }
        

        


    }
}


