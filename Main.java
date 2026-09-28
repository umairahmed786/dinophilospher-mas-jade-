import jade.core.Runtime;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;
import diningphilosopher.*;


public class Main{
    public static void main(String [] args) throws StaleProxyException  {
        Runtime rt = Runtime.instance();
        rt.setCloseVM(true);
        Profile pMain = new ProfileImpl("localhost", 8888, null);
        AgentContainer mc = rt.createMainContainer(pMain);

        // Initialize the number of philosphers
        int number_of_philosophers = 5;
        

        // Created the table agents and started it
        AgentController tableAgent = mc.createNewAgent(
                "table",
                Table.class.getName(),
                new Object[]{ number_of_philosophers }
        );
        tableAgent.start();
    
        // Creates and starts the one DininingPhilosopher agent for each seat named ("phil0", "phil1" .... )
        for (int i = 0; i < number_of_philosophers; i++) {
            AgentController philosopher = mc.createNewAgent(
                    "phil" + i,
                    DPAgent.class.getName(),
                    new Object[]{ i, number_of_philosophers }
            );
            philosopher.start();
        }
    }
}