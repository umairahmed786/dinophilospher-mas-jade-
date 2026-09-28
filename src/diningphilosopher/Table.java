package diningphilosopher;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;


public class Table extends Agent {

    // Table contains the number of philosphers and the forks that are equal to the number of philosopher.
    private int numPhilosophers;
    private boolean[] forks;

    // Defined setup method that gets the args and initialize the table 
    protected void setup() {
        Object[] args = getArguments();
        numPhilosophers = (int) args[0];
        forks = new boolean[numPhilosophers];

        // Initially all the forks are available;
        for (int i = 0; i < numPhilosophers; i++) {
            forks[i] = true;
        }

        // Cyclic behaviour because table is always looking if anyone wants to get or relaese forks.
        addBehaviour(new TableBehaviour());
        System.out.println("Table agent initialized");
    }
    
    // Returns the position of the Philosopher by parsing the name of the agent (phil1 -> 1) 
    private int getPositionOf(ACLMessage message){
        String agentName = message.getSender().getLocalName();
        return Integer.parseInt(agentName.replace("phil", ""));
    }

    // Returns the position of the Philosopher right fork
    private int rightForkPosition(int philosopherPosition) {
        return philosopherPosition;
    }

    // Returns the position of the Philosopher left fork
    private int leftForkPosition(int philosopherPosition) {
        return (philosopherPosition + numPhilosophers - 1) % numPhilosophers;
    }

    // Grants the requested fork (AGREE) if it is free, otherwise denies it (REFUSE). Also returnn the fork position.
    private void handleRequest(ACLMessage message, int philosopherPosition, String fork) {
        int forkPos = fork.equals("right")
                ? rightForkPosition(philosopherPosition)
                : leftForkPosition(philosopherPosition);

        ACLMessage reply = message.createReply();
        if (forks[forkPos]) {
            forks[forkPos] = false;
            reply.setPerformative(ACLMessage.AGREE);
        } else {
            reply.setPerformative(ACLMessage.REFUSE);
        }
        reply.setContent(String.valueOf(forkPos));
        send(reply);
    }
    
    // Marks the given fork (left or right of the philosopher) as available again
    private void handleRelease(int philosopherPosition, String fork) {
        int forkPos = fork.equals("right")
                ? rightForkPosition(philosopherPosition)
                : leftForkPosition(philosopherPosition);
        forks[forkPos] = true;
    }

    // Cyclically receives fork requests(CFP) and release(INFORM).
    // Basically there are two type of messages one to get the fork and the second to release the fork
    private class TableBehaviour extends CyclicBehaviour {
        public void action(){
            MessageTemplate template = MessageTemplate.or(MessageTemplate.MatchPerformative(ACLMessage.CFP), MessageTemplate.MatchPerformative(ACLMessage.INFORM));
            ACLMessage message= receive(template);
            if(message != null){
                if(message.getPerformative() == ACLMessage.CFP){
                    int philosopherPosition = getPositionOf(message);
                    String fork = message.getContent();
                    handleRequest(message, philosopherPosition, fork);
                } else if(message.getPerformative() == ACLMessage.INFORM){
                    int philosopherPosition = getPositionOf(message);
                    String fork = message.getContent();
                    handleRelease(philosopherPosition, fork);
                } else {
                    System.out.println("Table agent received unknown message");    
                    block();
                }   
            } else {
                System.out.println("Table agent received no message");
                block();
            }
        }
    }
}