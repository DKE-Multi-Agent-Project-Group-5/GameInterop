package Group5;

import Group5.Agent.Guard.GraphExplorer;
import Group5.Agent.Guard.GuardExplorer;
import Group5.factories.AgentFactoryGroup5;
import Group9.gui2.Gui;
import Group9.Game;
import Interop.Agent.Guard;
import javafx.application.Application;
import Group9.map.parser.Parser;

public class MainNewController {

    /**
     * Use this class to call the new GUI
     * The agents try to walk out of the map, so if you see no movemnt this is no bug
     * The agents only walk in one certain direction, actually for inspiration look at the randomAgent class from group 9 it is actually already a decent implementation
     * @param args
     */

    private static String path = "C:\\Users\\Esra\\Documents\\Uni\\DataScience\\Year2\\Project22\\Group 5 fork\\GameInterop\\src\\main\\java\\Group5\\Maps\\simple.map";
    
//    public static void main(String[] args) {
//        new Thread(() -> Application.launch(Gui.class)).start();
//    }

    public static void main(String[] args) {
        int epochs = 100;
        int guardWins = 0;
        int totalturns = 0;
        for (int i=0; i<epochs; i++){
            GuardExplorer.currentTime = 0;
            Game game = new Game(Parser.parseFile(path), new AgentFactoryGroup5(), false);
            game.run();
            if (game.getWinner().toString().equals("GUARDS")){
                guardWins++;
                totalturns = totalturns + GuardExplorer.currentTime/2;
            }
            System.out.printf("The winner is: %s\n", game.getWinner());
        }
        System.out.println("The guards won " + (guardWins*100/epochs) + "% of " + epochs + " matches.");
        System.out.println("Average amount of turns to win " + (totalturns/epochs));
    }
    public static String getPath(){
        return path;
    }
}
