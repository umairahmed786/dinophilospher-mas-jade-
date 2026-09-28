package diningphilosopher;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
/**
 * A philosopher agent that cycles the thinking, get forks and start eating, ask for the forks
 * and release the fork once its appetite is complete
 */
public class DPAgent extends Agent {
    public static final int maxHunger = 5;

    private int position;
    private int numberOfPhilosophers;
    private AID table;
    private AID leftNeighbor;
    private AID rightNeighbor;

    private int hunger = 0;
    private boolean left = false;
    private boolean right = false;
    private boolean eating = false;

    // True when CFP request is sent and the table has not reponded yet.
    private boolean waitingLeftReply = false;
    private boolean waitingRightReply = false;

    private boolean jointThinking = false;
    private AID thinkingPartner;
    private AID reservedFor = null;

    // Get the arguments and set the position and the neighbors of the agent. Also add cyclic behaviour 
    protected void setup() {
        Object[] args = getArguments();
        position = (Integer) args[0];
        numberOfPhilosophers = (Integer) args[1];

        table = new AID("table", AID.ISLOCALNAME);
        leftNeighbor = new AID("phil" + ((position - 1 + numberOfPhilosophers) % numberOfPhilosophers), AID.ISLOCALNAME);
        rightNeighbor = new AID("phil" + ((position + 1) % numberOfPhilosophers), AID.ISLOCALNAME);

        addBehaviour(new LifeCycle());
    }

    // Life cycle of the agent(Handle message if there is one otherwise perform some action) 
    private class LifeCycle extends CyclicBehaviour {
        public void action() {
            ACLMessage msg = receive();
            if (msg != null) {
                handleMessage(msg);
            } else {
                deliberateAndAct();
                block(200);
            }
        }
    }

    // Descision loic: see the current state and act accordingly
    private void deliberateAndAct() {
        if (waitingLeftReply || waitingRightReply) {
            return; 
        } else if (eating && hunger > 0) {
            hunger--;
            eat();
        } else if (eating && left) {
            dropLeft();
            sendJointThinkingProposals();
        } else if (eating && right) {
            eating = false;
            dropRight();
        } else if (hunger < maxHunger) {
            hunger++;
            think();
        } else if (!left) {
            takeLeft();
            breakJointThinkingPeer();
        } else if (!right) {
            takeRight();
        } else {
            eating = true;
            eat();
        }
    }

    private boolean isPartner(AID other) {
        return jointThinking && thinkingPartner != null && thinkingPartner.equals(other);
    }

    // Break the peer
    private void breakJointThinkingPeer() {
        if (!jointThinking || thinkingPartner == null) {
            return; 
        }
        ACLMessage message = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
        message.addReceiver(thinkingPartner);
        message.setContent("joint-thinking-stop");
        send(message);
        jointThinking = false;
        thinkingPartner = null;
    }

    // Proposal for the joint thinking
    private void sendJointThinkingProposals(){
        if (jointThinking || reservedFor != null) return; 
        for(int i=0; i<numberOfPhilosophers; i++){
            if(i != position){
                AID peer = new AID("phil" + i, AID.ISLOCALNAME);
                sendJointThinkingProposalTo(peer, "lets-tink-together");
            }
            
        }
    }

    private void eat()   { System.out.println(getLocalName() + " is eating."); }
    private void think(){
        if(jointThinking){
            System.out.println(getLocalName() + " is joint thinking with " + thinkingPartner.getLocalName()); 
        } else {
            System.out.println(getLocalName() + " is thinking."); 
        }
        
    }

    // Ask the table for the left fork and then wait for the reply
    private void takeLeft() {
        ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
        cfp.addReceiver(table);
        cfp.setContent("left");
        send(cfp);
        waitingLeftReply = true;
    }

    // Ask the table for the right fork and then wait for the reply
    private void takeRight() {
        ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
        cfp.addReceiver(table);
        cfp.setContent("right");
        send(cfp);
        waitingRightReply = true;
    }

    // Drop the left fork on the table
    private void dropLeft() {
        ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
        inform.addReceiver(table);
        inform.setContent("left");
        send(inform);
        left = false;
        System.out.println(getLocalName() + " dropped LEFT fork.");
    }

    // Drop the right fork on the table
    private void dropRight() {
        ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
        inform.addReceiver(table);
        inform.setContent("right");
        send(inform);
        right = false;
        System.out.println(getLocalName() + " dropped RIGHT fork.");
    }

    // Handle message(Two types: one from the table and one from the peer)
    private void handleMessage(ACLMessage message) {
        if (message.getSender().equals(table)) {
            handleTableReply(message);
        } else {
            handlePeerMessage(message);
        }
    }

    // Process table answers for the fork request
    private void handleTableReply(ACLMessage message) {
        int perf = message.getPerformative();

        if (perf == ACLMessage.AGREE) {
            if (waitingLeftReply) {
                left = true;
                waitingLeftReply = false;
                System.out.println(getLocalName() + " got LEFT fork.");
            } else if (waitingRightReply) {
                right = true;
                waitingRightReply = false;
                System.out.println(getLocalName() + " got RIGHT fork.");
            }
        } else if (perf == ACLMessage.REFUSE) {
            // if refused then ask for the neighbor
            if (waitingLeftReply) {
                waitingLeftReply = false;
                askNeighborToRelease(leftNeighbor, "release-right");
            } else if (waitingRightReply) {
                waitingRightReply = false;
                askNeighborToRelease(rightNeighbor, "release-left");
            }
        }
    }

    // Request for the joint thinking
    private void sendJointThinkingProposalTo(AID peer, String content){
        ACLMessage proposalMessage = new ACLMessage(ACLMessage.PROPOSE);
        proposalMessage.addReceiver(peer);
        proposalMessage.setContent(content);
        send(proposalMessage);
    }

    // Request to a neighbour to release a fork
    private void askNeighborToRelease(AID neighbor, String content) {
        ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
        req.setConversationId("release-fork");
        req.addReceiver(neighbor);
        req.setContent(content);
        send(req);
    }

    private void handlePeerMessage(ACLMessage message) {
        int perf = message.getPerformative();
        AID sender = message.getSender();

        if (perf == ACLMessage.REQUEST) {
            handlePeerForkRequest(message);

        } else if (perf == ACLMessage.PROPOSE) {
            if (!jointThinking && reservedFor == null && !eating && hunger < maxHunger) {
                reservedFor = sender;
                reply(message, ACLMessage.ACCEPT_PROPOSAL, "proposal-accepted");
            } else {
                reply(message, ACLMessage.REJECT_PROPOSAL, "proposal-rejected");
            }
        } else if (perf == ACLMessage.ACCEPT_PROPOSAL) {
            boolean reservedForSameAgent = sender.equals(reservedFor);
            if (!jointThinking && (reservedFor == null || reservedForSameAgent)) {
                jointThinking = true;
                thinkingPartner = sender;
                reservedFor = null;
                reply(message, ACLMessage.CONFIRM, "confirmed");
            } else if (!isPartner(sender)) {
                reply(message, ACLMessage.REJECT_PROPOSAL, "proposal-rejected");
            }

        } else if (perf == ACLMessage.CONFIRM) {
            if (sender.equals(reservedFor) && hunger < maxHunger) {
                jointThinking = true;
                thinkingPartner = sender;
                reservedFor = null;
            } else if (!isPartner(sender)) {
                reply(message, ACLMessage.REJECT_PROPOSAL, "no-longer-available"); // release the proposer
            }

        } else if (perf == ACLMessage.REJECT_PROPOSAL) {
            if (sender.equals(reservedFor)) {
                reservedFor = null;                 
            }
            if (isPartner(sender)) {
                thinkingPartner = null;       
                jointThinking = false;
            }
        }
    }

    // Handle the neighbour request if agent hold the fork and not eating then release the fork else refused to release
    private void handlePeerForkRequest(ACLMessage message) {

        String content = message.getContent();

        if ("release-left".equals(content) && left && !eating) {
            dropLeft();
            reply(message, ACLMessage.AGREE, "released");
        } else if ("release-right".equals(content) && right && !eating) {
            dropRight();
            reply(message, ACLMessage.AGREE, "released");
        } else {
            reply(message, ACLMessage.REFUSE, "busy");
        }
    }
    // Send reply to the message
    private void reply(ACLMessage original, int performative, String content) {
        ACLMessage answer = original.createReply();
        answer.setPerformative(performative);
        answer.setContent(content);
        send(answer);
    }
}
