package Group5.Agent;

import java.util.Deque;
import java.util.LinkedList;
import java.util.Random;

import Group5.GameController.AgentController;
import Interop.Action.Move;
import Interop.Action.Rotate;
import Interop.Agent.Intruder;
import Interop.Geometry.Angle;
import Interop.Geometry.Distance;
import Interop.Geometry.Point;
import Interop.Action.Action;
import Interop.Percept.Smell.SmellPercept;
import Interop.Percept.Smell.SmellPerceptType;
import Interop.Percept.Sound.SoundPerceptType;
import Interop.Percept.Vision.ObjectPercept;
import Interop.Percept.Vision.ObjectPerceptType;
import Interop.Percept.Vision.ObjectPercepts;

import java.util.Arrays;
import java.util.Scanner;
import java.io.*;


public class QLearning implements Serializable {
    
    public enum State {
        
        Guard(0),
        Intruder(1),
        Wall(2),
        Window(3),
        Door(4),
        Teleport(5),
        SentryTower(6),
        ShadedArea(7),
        TargetArea(8),
        EmptySpace(9),
        Noise(10),
        Yell(11);
    
        // Smell is used only when intruders are more than 1 - if so, uncomment block of code below
//        Pheromone1(12),
//        Pheromone2(13),
//        Pheromone3(14),
//        Pheromone4(15),
//        Pheromone5(16);
    
        private int value;
        State(int value) {
            this.value = value;
        }
    }
    
    private State prevState1;
    private State prevState2;
    private State currState1;
    private State currState2;
    private int prevAction = 0;
    private int currAction;
    private static float epsilon;
    private float gamma;
    private float alpha;
    private int numActions;
    private int numStates;
    private double[][][] qTable;
    private int[][][] alphan;
    
    
    private Point agentPosition;
    private ObjectPercept state;
    private ObjectPercepts objectsInVision;
    private Random rn;
//    private double EPSILON_DECAY = 0.0000001;
    private Deque<Action> actionQueue;
    
    /**
     *@gamma   : The discount factor for future rewards
     *@epsilon : The probability the agent makes a random move
     *@alpha   : The learning rate for the agent
     *@board   : A reference to a game object for the agent to interact with
     */
    public QLearning(float gamma, float epsilon, float alpha,
            int numActions, int numStates) {
        this.epsilon = epsilon;
        this.gamma = gamma;
        this.alpha = alpha;
        this.numActions = numActions;
        this.numStates = numStates;
//        this.qTable = new double[numStates][numStates][numActions];
//        this.alphan = new int[numStates][numStates][numActions];
        deserializeQTable3D();
        deserializeAlphan();
        this.rn = new Random();
        this.actionQueue = new LinkedList<>();
    }
    
    /**
     *
     * @param Combined visual and sound states
     * @return index with maximum value action from Q-Table
     */
    public int getMaxValueAction(ObjectPerceptType visionState, SoundPerceptType soundState) {
        State[] allStates = State.class.getEnumConstants();
        int stateIndex1 = 0;
        int stateIndex2 = 0;
        for (State tempState : allStates) {
            if (visionState.name().equals(tempState.name())) {
                stateIndex1 = tempState.value;
            }
            if (soundState.name().equals(tempState.name())) {
                stateIndex2 = tempState.value;
            }
        }
        double[] qTablePart = qTable[stateIndex1][stateIndex2];
        int maxIndex = getMaxValue(qTablePart);
        double chance = Math.random();
        Random rand = new Random();

        //update epsilon value
        epsilon = (float) (1.0/(1+Math.log(1+Arrays.stream(alphan[stateIndex1][stateIndex2]).sum())));

        // Perform random action if chance is smaller than epsilon value
        // Otherwise return maxValue action index from Q-table
        if (chance < epsilon) {
            return rand.nextInt(this.numActions);
        } else {
            return maxIndex;
        }
    }
    
    /**
     *
     * @param Combined visual and sound states
     * @return index with maximum value action from Q-Table
     */
    public int getMaxValueAction(ObjectPerceptType visionState, ObjectPerceptType visionState2) {
        State[] allStates = State.class.getEnumConstants();
        int stateIndex1 = 0;
        int stateIndex2 = 0;
        for (State tempState : allStates) {
            if (visionState.name().equals(tempState.name())) {
                stateIndex1 = tempState.value;
            }
            if (visionState2.name().equals(tempState.name())) {
                stateIndex2 = tempState.value;
            }
        }
        
        double[] qTablePart = qTable[stateIndex1][stateIndex2];
        int maxIndex = getMaxValue(qTablePart);
        double chance = Math.random();
        Random rand = new Random();

        //update epsilon value
        epsilon = (float) (1.0/(1+Math.log(1+Arrays.stream(alphan[stateIndex1][stateIndex2]).sum())));
        // Perform random action if chance is smaller than epsilon value
        // Otherwise return maxValue action index from Q-table
        if (chance < epsilon) {
            return rand.nextInt(this.numActions);
        } else {
            return maxIndex;
        }
    }
    
    /**
     *
     * @param visionState Objects in vision
     * @param soundState Sounds detected
     * @param currentAction maxValue action to be executed
     * @param reward
     */
    protected void updateQTable(ObjectPerceptType visionState, SoundPerceptType soundState, int currentAction, float reward) {
        State[] allStates = State.class.getEnumConstants();
        if (this.prevState1 == null && this.prevState2 == null) {
            this.prevState1 = State.EmptySpace;
            this.prevState2 = State.EmptySpace;
        }
        else if (this.prevState1 == null && this.prevState2 != null){
            this.prevState1 = State.EmptySpace;
        }
        else if (this.prevState1 != null && this.prevState2 == null){
            this.prevState2 = State.EmptySpace;
        }
        
        this.currAction = currentAction;
        
        for (State cState : allStates) {
            if (visionState.name().equals(cState.name())) {
                this.currState1 = cState;
            }
            if (soundState.name().equals(cState.name())){
                this.currState2 = cState;
            }
        }

        //Adaptive alpha
        alphan[currState1.value][currState2.value][currentAction]++;
        alpha = (float) (1.0/Math.log(1+alphan[currState1.value][currState2.value][currentAction]));

        //Bellman Equation to update Q-table
        double update = reward + gamma * findMaxQState(this.currState1.value, this.currState2.value) - qTable[this.prevState1.value][this.prevState2.value][this.prevAction];
        qTable[this.prevState1.value][this.prevState2.value][this.prevAction] = qTable[this.prevState1.value][this.prevState2.value][this.prevAction] + alpha * update;
        
        this.prevAction = this.currAction;
        this.prevState1 = this.currState1;
        this.prevState2 = this.currState2;
    }
    
    /**
     *
     * @param visionState1, visionState2 Objects in vision
     * @param currentAction maxValue action to be executed
     * @param reward
     */
    protected void updateQTable(ObjectPerceptType visionState1, ObjectPerceptType visionState2, int currentAction, float reward) {
        State[] allStates = State.class.getEnumConstants();
        if (this.prevState1 == null && this.prevState2 == null) {
            this.prevState1 = State.EmptySpace;
            this.prevState2 = State.EmptySpace;
        }
        else if (this.prevState1 == null && this.prevState2 != null){
            this.prevState1 = State.EmptySpace;
        }
        else if (this.prevState1 != null && this.prevState2 == null){
            this.prevState2 = State.EmptySpace;
        }
        
        this.currAction = currentAction;
        
        for (State cState : allStates) {
            if (visionState1.name().equals(cState.name())) {
                this.currState1 = cState;
            }
            if (visionState2.name().equals(cState.name())){
                this.currState2 = cState;
            }
        }

        //Adaptive alpha
        alphan[currState1.value][currState2.value][currentAction]++;
        alpha = (float) (1.0/Math.log(1+alphan[currState1.value][currState2.value][currentAction]));

        //Bellman Equation to update Q-table
        double update = reward + gamma * findMaxQState(this.currState1.value, this.currState2.value) - qTable[this.prevState1.value][this.prevState2.value][this.prevAction];
        qTable[this.prevState1.value][this.prevState2.value][this.prevAction] = qTable[this.prevState1.value][this.prevState2.value][this.prevAction] + alpha * update;
        
        this.prevAction = this.currAction;
        this.prevState1 = this.currState1;
        this.prevState2 = this.currState2;
    
        // For printing the Q-table
//        for (int i = 0; i < numStates; i++) {
//            for (int j = 0; j < numStates; j++) {
//                for (int k = 0; k < numActions; k++){
//                    System.out.print("" + qTable[i][j][k] + ",");
//                }
//                System.out.println();
//            }
//            System.out.println();
//        }
//        System.out.println();
    }
    
    protected double [][][] getQTable(){
        return this.qTable;
    }
    
    private double findMaxQState(int stateIndex1, int stateIndex2) {
        
        double[] qTablePart = qTable[stateIndex1][stateIndex2];
        int max = getMaxValue(qTablePart);
        double maxValue = qTablePart[max];
        return maxValue;
    }
    
    /**
     * @param qTablePart
     *         whole qTable as input
     * @return the index of the max value of the part of the Qtable that references the current state
     */
    private int getMaxValue(double[] qTablePart) {
        int max = 0;
        double maxValue = qTablePart[0];
        for (int i = 0; i < qTablePart.length; i++) {
            if (qTablePart[i] > maxValue) {
                max = i;
            }
        }
        return max;
    }
    
//    /**
//     * Writing the 3-D Q-Table to a file locally
//     */
//    protected void writeTableToFile3D(){
//        StringBuilder builder = new StringBuilder();
//        for(int i = 0; i < numStates; i++){
//            for(int j = 0; j < numStates; j++){
//                for (int k = 0; k < numActions; k++){
//                    builder.append(qTable[i][j][k]+"");
//                    if(k < numActions - 1)
//                        builder.append(" ");
//                }
//                builder.append("\n");
//            }
//            builder.append("\n");
//        }
//        try {
//            BufferedWriter writer = new BufferedWriter(new FileWriter("src/main/java/Group5/QTable3D.txt"));
//            writer.write(builder.toString());
//            writer.close();
//        } catch (IOException e) {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }
//    }
//
//    /**
//     * Reading the Q-table from a file locally
//     */
//    private double[][][] readTableFromFile3D() {
//        try {
//            Scanner sc = new Scanner(new BufferedReader(new FileReader("src/main/java/Group5/QTable3D.txt")));
//            double [][][] initialTable = new double[this.numStates][this.numStates][this.numActions];
//            for (int i = 0; i < initialTable.length; i++) {
//                for (int j = 0; j < initialTable[i].length; j++) {
//                    while (sc.hasNextLine()) {
//                        String[] lines = sc.nextLine().trim().split(" ");
//                        for (int k = 0; k < lines.length; k++) {
//                            String line = lines[k];
//                            if (!lines[k].isEmpty()) {
//                                initialTable[i][j][k] = Double.parseDouble(line);
//                            }
//                        }
//                    }
//                }
//            }
//            return initialTable;
//        }
//        catch (IOException e) {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }
//        return null;
//    }
    
    
    /**
     * Make the Q-table a serializable object
     */
    protected void serializeQTable3D(){
        try {
            FileOutputStream fileOut =
                    new FileOutputStream("src/main/java/Group5/QTable3D.ser");
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(this.qTable);
            out.close();
            fileOut.close();
        } catch (IOException i) {
            i.printStackTrace();
        }
    }
    
    /**
     * Deserialize the Q-table object
     */
    private void deserializeQTable3D() {
        try {
            FileInputStream fileIn = new FileInputStream("src/main/java/Group5/QTable3D.ser");
            ObjectInputStream in = new ObjectInputStream(fileIn);
            this.qTable = (double [][][]) in.readObject();
            in.close();
            fileIn.close();
        } catch (IOException i) {
            i.printStackTrace();
            return;
        } catch (ClassNotFoundException c) {
            System.out.println("QTable not found");
            c.printStackTrace();
            return;
        }
    }

    /**
     * Make the Alphan a serializable object
     */
    protected void serializeAlphan(){
        try {
            FileOutputStream fileOut =
                    new FileOutputStream("src/main/java/Group5/Alphan.ser");
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(this.alphan);
            out.close();
            fileOut.close();
        } catch (IOException i) {
            i.printStackTrace();
        }
    }

    /**
     * Deserialize the Alphan object
     */
    private void deserializeAlphan() {
        try {
            FileInputStream fileIn = new FileInputStream("src/main/java/Group5/Alphan.ser");
            ObjectInputStream in = new ObjectInputStream(fileIn);
            this.alphan = (int [][][]) in.readObject();
            in.close();
            fileIn.close();
        } catch (IOException i) {
            i.printStackTrace();
        } catch (ClassNotFoundException c) {
            System.out.println("Alpha table not found");
            c.printStackTrace();
        }
    }
}