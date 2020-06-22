package Group5.Agent;

import java.util.ArrayList;
import java.util.Deque;
import java.util.Random;

import Group5.GameController.AgentController;
import Group5.GameController.MapInfo;
import Group5.GameController.Vision;
import Group5.MainNewController;
import Interop.Geometry.Direction;
import Interop.Action.Action;
import Interop.Action.DropPheromone;
import Interop.Action.IntruderAction;
import Interop.Action.Move;
import Interop.Action.NoAction;
import Interop.Action.Rotate;
import Interop.Action.Sprint;
import Interop.Action.Yell;
import Interop.Geometry.Angle;
import Interop.Geometry.Distance;
import Interop.Geometry.Point;
import Interop.Percept.GuardPercepts;
import Interop.Percept.IntruderPercepts;
import Interop.Percept.Smell.SmellPercept;
import Interop.Percept.Smell.SmellPerceptType;
import Interop.Percept.Sound.SoundPercept;
import Interop.Percept.Sound.SoundPerceptType;
import Interop.Percept.Vision.ObjectPercept;
import Interop.Percept.Vision.ObjectPerceptType;
import Interop.Percept.Vision.ObjectPercepts;
import Interop.Percept.Vision.VisionPrecepts;
import Interop.Percept.Scenario.ScenarioIntruderPercepts;

import java.math.BigDecimal;
import java.math.RoundingMode;

import java.util.*;


public class IntruderAgent implements Interop.Agent.Intruder {
    
    private Deque<IntruderAction> actionQueue;
    private ObjectPercept visionState;
    private SmellPercept smellState;
    private SoundPercept soundState;
    
    // Add primitive memory system checking for already seen rounded X and Y coordinates
    private Map<ObjectPerceptType,List> seenObjects;
    
    // Constants for scenario with more than 1 Intruder (please uncomment and comment out block of code below for testing)
//    private static final int NUMBER_OF_POSSIBLE_ACTIONS = 12;
//    private static final int NUMBER_OF_POSSIBLE_SINGLE_STATES = 17;
    
    // Constants for scenario with one Intruder
    private static final int NUMBER_OF_POSSIBLE_ACTIONS = 17;
    private static final int NUMBER_OF_POSSIBLE_SINGLE_STATES = 12;
    
    // Parameters for Q-learning
    private QLearning intruderQL;
    float gamma = 0.92f;
    float epsilon = 0.1f;
    float alpha = 0.1f;
    
    public IntruderAgent() {
        this.actionQueue = new LinkedList<>();
        this.seenObjects = new HashMap<>();
        intruderQL = new QLearning(gamma, epsilon, alpha, NUMBER_OF_POSSIBLE_ACTIONS, NUMBER_OF_POSSIBLE_SINGLE_STATES);
    }
    
    /**
     * Enumerate all possible actions
     */
    public enum Actions {
        RotateTowards(0),
        MoveTowards(1),
        NoAction(2),
        MoveStraight(3),
        RandomRotate(4),
        RandomMove(5),
        Sprint(6),
        RotateRight(7),
        RotateLeft(8),
        MoveToArea(9),
        RotateRight20(10),
        RotateLeft20(11),
        RotateAway(12),
        RotateAwaySound(13),
        RotateParallelToWall(14),
        RotateRight10(15),
        RotateLeft10(16);
        
        // Smell is used only when intruders are more than 1 - if so, uncomment block of code below
//        DropPheromone1(7),
//        DropPheromone2(8),
//        DropPheromone3(9),
//        DropPheromone4(10),
//        DropPheromone5(11);
        
        private int value;
        
        Actions(int value) {
            
            this.value = value;
        }
    }
    
    @Override
    public IntruderAction getAction(IntruderPercepts percepts) {
        explore(percepts);
        return actionQueue.pop();
        
    }
    
    protected Rotate randomRotate() {
        Random rd = new Random();
        return new Rotate(Angle.fromDegrees(1 + (44) * rd.nextDouble()));
    }
    
    protected Move randomWalk(IntruderPercepts intruderPercepts) {
        Random rd = new Random();
        return new Move(new Distance(
                intruderPercepts.getScenarioIntruderPercepts().getMaxMoveDistanceIntruder().getValue() * rd
                        .nextDouble()));
    }
    
    protected Rotate doNothing() {
        return new Rotate(Angle.fromDegrees(0));
    }
    
    protected Move moveStraight(IntruderPercepts intruderPercepts) {
        return new Move(intruderPercepts.getScenarioIntruderPercepts().getMaxMoveDistanceIntruder());
    }
    
    protected Sprint sprintTowards(IntruderPercepts intruderPercepts) {
        return new Sprint(intruderPercepts.getScenarioIntruderPercepts().getMaxSprintDistanceIntruder());
    }
    
    protected Rotate rotateTowards(Point point) {
        return new Rotate(Angle.fromRadians(point.getClockDirection().getRadians()));
    }
    
    protected Rotate rotateTowards(double radians) {
        
        return new Rotate(Angle.fromRadians(radians));
    }
    
    protected Move walkTowards(Point point) {
        
        return new Move(new Distance(point.getDistanceFromOrigin().getValue()));
    }
    
    protected Rotate rotateRight() {
        return new Rotate(Angle.fromDegrees(45));
    }
    
    protected Rotate rotateLeft() {
        return new Rotate(Angle.fromDegrees(-45));
    }
    
    protected Rotate rotateRight20() {
        return new Rotate(Angle.fromDegrees(20));
    }
    
    protected Rotate rotateLeft20() {
        return new Rotate(Angle.fromDegrees(-20));
    }
    
    protected Rotate rotateAway(Point point) {
        return new Rotate(Angle.fromRadians(Math.PI+point.getClockDirection().getRadians()));
    }
    protected Rotate rotateAwaySound(Direction direction) {
        return new Rotate(Angle.fromRadians(Math.PI+direction.getRadians()));
    }
    protected Rotate rotateParallelToWall(IntruderPercepts intruderPercepts){
        Set<ObjectPercept> vision = intruderPercepts.getVision().getObjects().getAll();
        if (!intruderPercepts.wasLastActionExecuted() && vision.size() > 0) {
            double angleToWallsDegrees = 0;
            int count = 0;
            for (ObjectPercept e : vision) {
                //prevents to turn away from a intruder
                if (!(e.getType() == ObjectPerceptType.Intruder)) {
                    if (Angle.fromDegrees(0).getDistance(e.getPoint().getClockDirection()).getDegrees() > 180) {
                        angleToWallsDegrees = angleToWallsDegrees + e.getPoint().getClockDirection().getDegrees() - 360;
                    } else {
                        angleToWallsDegrees = angleToWallsDegrees + Angle.fromDegrees(0).getDistance(e.getPoint().getClockDirection()).getDegrees();
                    }
                    count++;
                    
                }
            }
            if (angleToWallsDegrees != 0) {
                return new Rotate(Angle.fromDegrees(angleToWallsDegrees / count));
            }
        }
        
        if (!intruderPercepts.wasLastActionExecuted()) {
            
            Angle randomAngle = Angle.fromDegrees(intruderPercepts.getScenarioIntruderPercepts().getScenarioPercepts().getMaxRotationAngle().getDegrees() * Math.random());
            return new Rotate(randomAngle);
        }
        return new Rotate(Angle.fromRadians(0));
    }
    
    protected Rotate rotateRight10() {
        return new Rotate(Angle.fromDegrees(10));
    }
    
    protected Rotate rotateLeft10() {
        return new Rotate(Angle.fromDegrees(-10));
    }
    
    // Smell is used only when intruders are more than 1 - if so, uncomment block of code below
//    protected DropPheromone dropPheromone1() {
//        return new DropPheromone(SmellPerceptType.Pheromone1);
//    }
//
//    protected DropPheromone dropPheromone2() {
//        return new DropPheromone(SmellPerceptType.Pheromone2);
//    }
//
//    protected DropPheromone dropPheromone3() {
//        return new DropPheromone(SmellPerceptType.Pheromone3);
//    }
//
//    protected DropPheromone dropPheromone4() {
//        return new DropPheromone(SmellPerceptType.Pheromone4);
//    }
//
//    protected DropPheromone dropPheromone5() {
//        return new DropPheromone(SmellPerceptType.Pheromone5);
//    }
    
    protected ObjectPercept getVisionState(IntruderPercepts intruderPercepts) {
        Set<ObjectPercept> vision = intruderPercepts.getVision().getObjects().getAll();
        ArrayList<ObjectPerceptType> visionPerceptTypes = new ArrayList<>();
        List<ObjectPercept> visionObjectsSorted = new ArrayList<>(vision);
        for (ObjectPercept e : vision) {
            visionPerceptTypes.add(e.getType());
        }
        
        if (visionPerceptTypes.contains(ObjectPerceptType.Guard)) {
            for (ObjectPercept s : vision) {
                if (s.getType() == ObjectPerceptType.Guard) {
                    return s;
                }
            }
        } else {
            int n = visionObjectsSorted.size();
            for (int i = 0; i < n - 1; i++) {
                for (int j = 0; j < n - i - 1; j++) {
                    //Bubble Sorting for nearest element in vision
                    double tempDistance1 =
                            intruderPercepts.getVision().getFieldOfView().getRange().getValue() - visionObjectsSorted
                                    .get(j).getPoint().getDistanceFromOrigin().getValue();
                    double tempDistance2 =
                            intruderPercepts.getVision().getFieldOfView().getRange().getValue() - visionObjectsSorted
                                    .get(j + 1).getPoint().getDistanceFromOrigin().getValue();
                    if (tempDistance1 > tempDistance2) {
                        ObjectPercept temp = visionObjectsSorted.get(j);
                        visionObjectsSorted.set(j, visionObjectsSorted.get(j + 1));
                        visionObjectsSorted.set(j + 1, temp);
                    }
                }
            }
            if (!visionObjectsSorted.isEmpty()) {
                return visionObjectsSorted.get(0);
            }
        }
        return null;
    }
    
    protected SoundPercept getSoundState(IntruderPercepts intruderPercepts) {
        Set<SoundPercept> sounds = intruderPercepts.getSounds().getAll();
        ArrayList<SoundPerceptType> soundPerceptTypes = new ArrayList<>();
        for (SoundPercept s : sounds) {
            soundPerceptTypes.add((s.getType()));
        }
        
        // Check if a Guard's Yell is in the Set and if yes, return it because it is more important than simple Noise
        if (soundPerceptTypes.contains(SoundPerceptType.Yell) && (soundPerceptTypes.contains(SoundPerceptType.Noise)
                || !soundPerceptTypes.contains(SoundPerceptType.Noise))) {
            for (SoundPercept s : sounds) {
                if (s.getType() == SoundPerceptType.Yell) {
                    return s;
                }
            }
        } else if (sounds.size() > 0) {
            for (SoundPercept sound : sounds) {
                return sound;
            }
        }
        return null;
    }
    
    protected SmellPercept getSmellState(IntruderPercepts intruderPercepts) {
        List<SmellPercept> smells = new ArrayList<SmellPercept>(intruderPercepts.getSmells().getAll());
        
        // Return just the first element from the smells List (for simplicity)
        if (!smells.isEmpty()) {
            return smells.get(0);
        }
        
        return null;
    }
    
    public void explore(IntruderPercepts intruderPercepts) {
        
        this.visionState = this.getVisionState(intruderPercepts);
        this.soundState = this.getSoundState(intruderPercepts);
        
        if (this.visionState != null) {
            int maxValueAction = intruderQL.getMaxValueAction(this.visionState.getType());
            for (IntruderAgent.Actions currAction : IntruderAgent.Actions.class.getEnumConstants()) {
                if (currAction.value == maxValueAction) {
                    if (currAction.value == 0) {
                        actionQueue.add(rotateTowards(this.visionState.getPoint()));
                        float reward = getReward(this.visionState);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState);
                        intruderQL.updateQTable(this.visionState.getType(), currAction.value, reward);
                    } else if (currAction.value == 1) {
                        actionQueue.add(walkTowards(this.visionState.getPoint()));
                        float reward = getReward(this.visionState);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState);
                        intruderQL.updateQTable(this.visionState.getType(),  currAction.value, reward);
                    } else if (currAction.value == 2) {
                        actionQueue.add(doNothing());
                        float reward = getReward(this.visionState);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState);
                        intruderQL.updateQTable(this.visionState.getType(),  currAction.value, reward);
                    } else if (currAction.value == 3) {
                        actionQueue.add(moveStraight(intruderPercepts));
                        float reward = getReward(this.visionState);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState);
                        intruderQL.updateQTable(this.visionState.getType(),  currAction.value, reward);
                    } else if (currAction.value == 4) {
                        actionQueue.add(randomRotate());
                        float reward = getReward(this.visionState);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState);
                        intruderQL.updateQTable(this.visionState.getType(),  currAction.value, reward);
                    } else if (currAction.value == 5) {
                        actionQueue.add(randomWalk(intruderPercepts));
                        float reward = getReward(this.visionState);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState);
                        intruderQL.updateQTable(this.visionState.getType(),  currAction.value, reward);
                    } else if (currAction.value == 6) {
                        actionQueue.add(sprintTowards(intruderPercepts));
                        float reward = getReward(this.visionState);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState);
                        intruderQL.updateQTable(this.visionState.getType(),  currAction.value, reward);
                    } else if (currAction.value == 7) {
                        actionQueue.add(rotateRight());
                        float reward = getReward(this.visionState);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState);
                        intruderQL.updateQTable(this.visionState.getType(),  currAction.value, reward);
                    } else if (currAction.value == 8) {
                        actionQueue.add(rotateLeft());
                        float reward = getReward(this.visionState);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState);
                        intruderQL.updateQTable(this.visionState.getType(),  currAction.value, reward);
                    } else if (currAction.value == 9) {
                        if (visionState.getType() == ObjectPerceptType.Door || visionState.getType() == ObjectPerceptType.Window) {
                            if (Math.abs(visionState.getPoint().getClockDirection().getDegrees()) < 10) {
                                actionQueue.add(walkTowards(visionState.getPoint()));
                            } else {
                                actionQueue.add(rotateTowards(visionState.getPoint()));
                            }
                        } else{
                            actionQueue.add(doNothing());
                        }
    
                        float reward = getReward(this.visionState);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState);
                        intruderQL.updateQTable(this.visionState.getType(),  currAction.value, reward);
    
                        // Smell is used only when intruders are more than 1 - if so, uncomment block of code below
//                    } else if (currAction.value == 7) {
//                        actionQueue.add(dropPheromone1());
//                        float reward = getReward(this.visionState);
//                        // Add rounded X and Y coordinates of Points to the seen objects
//                        this.seenObjects.add(Arrays.asList(round(this.visionState.getPoint().getX(), 2),
//                                round(this.visionState.getPoint().getY(), 2)));
//                        intruderQL.updateQTable(this.visionState.getType(), currAction.value, reward);
//                    } else if (currAction.value == 8) {
//                        actionQueue.add(dropPheromone2());
//                        float reward = getReward(this.visionState);
//                        // Add rounded X and Y coordinates of Points to the seen objects
//                        this.seenObjects.add(Arrays.asList(round(this.visionState.getPoint().getX(), 2),
//                                round(this.visionState.getPoint().getY(), 2)));
//                        intruderQL.updateQTable(this.visionState.getType(), currAction.value, reward);
//                    } else if (currAction.value == 9) {
//                        actionQueue.add(dropPheromone3());
//                        float reward = getReward(this.visionState);
//                        // Add rounded X and Y coordinates of Points to the seen objects
//                        this.seenObjects.add(Arrays.asList(round(this.visionState.getPoint().getX(), 2),
//                                round(this.visionState.getPoint().getY(), 2)));
//                        intruderQL.updateQTable(this.visionState.getType(), currAction.value, reward);
//                    } else if (currAction.value == 10) {
//                        actionQueue.add(dropPheromone4());
//                        float reward = getReward(this.visionState);
//                        // Add rounded X and Y coordinates of Points to the seen objects
//                        this.seenObjects.add(Arrays.asList(round(this.visionState.getPoint().getX(), 2),
//                                round(this.visionState.getPoint().getY(), 2)));
//                        intruderQL.updateQTable(this.visionState.getType(), currAction.value, reward);
//                    } else if (currAction.value == 11) {
//                        actionQueue.add(dropPheromone5());
//                        float reward = getReward(this.visionState);
//                        // Add rounded X and Y coordinates of Points to the seen objects
//                        this.seenObjects.add(Arrays.asList(round(this.visionState.getPoint().getX(), 2),
//                                round(this.visionState.getPoint().getY(), 2)));
//                        intruderQL.updateQTable(this.visionState.getType(), currAction.value, reward);
                    }
                } else if (currAction.value == 10) {
                    actionQueue.add(rotateRight20());
                    float reward = getReward(this.visionState);
                    // Add rounded X and Y coordinates of Points to the seen objects
                    addToMemory(this.visionState.getType(), this.visionState);
                    intruderQL.updateQTable(this.visionState.getType(),  currAction.value, reward);
                } else if (currAction.value == 11) {
                    actionQueue.add(rotateLeft20());
                    float reward = getReward(this.visionState);
                    // Add rounded X and Y coordinates of Points to the seen objects
                    addToMemory(this.visionState.getType(), this.visionState);
                    intruderQL.updateQTable(this.visionState.getType(), currAction.value, reward);
                } else if(currAction.value == 12) {
                    actionQueue.add(rotateAway(this.visionState.getPoint()));
                    float reward = getReward(this.visionState);
                    // Add rounded X and Y coordinates of Points to the seen objects
                    addToMemory(this.visionState.getType(), this.visionState);
                    intruderQL.updateQTable(this.visionState.getType(),  currAction.value, reward);
                } else if(currAction.value == 13) {
                    actionQueue.add(doNothing());
                    float reward = getReward(this.visionState);
                    // Add rounded X and Y coordinates of Points to the seen objects
                    addToMemory(this.visionState.getType(), this.visionState);
                    intruderQL.updateQTable(this.visionState.getType(),  currAction.value, reward);
                } else if(currAction.value ==14) {
                    actionQueue.add(rotateParallelToWall(intruderPercepts));
                    float reward = getReward(this.visionState);
                    // Add rounded X and Y coordinates of Points to the seen objects
                    addToMemory(this.visionState.getType(), this.visionState);
                    intruderQL.updateQTable(this.visionState.getType(),  currAction.value, reward);
                } else if (currAction.value == 15) {
                    actionQueue.add(rotateRight10());
                    float reward = getReward(this.visionState);
                    // Add rounded X and Y coordinates of Points to the seen objects
                    addToMemory(this.visionState.getType(), this.visionState);
                    intruderQL.updateQTable(this.visionState.getType(),  currAction.value, reward);
                } else if (currAction.value == 16) {
                    actionQueue.add(rotateLeft10());
                    float reward = getReward(this.visionState);
                    // Add rounded X and Y coordinates of Points to the seen objects
                    addToMemory(this.visionState.getType(), this.visionState);
                    intruderQL.updateQTable(this.visionState.getType(),  currAction.value, reward);
                }
            }
            intruderQL.writeTableToFile();
            intruderQL.serializeAlphan();
        }
    
        else {
            actionQueue.add(doNothing());
        }
    
        if (intruderPercepts.wasLastActionExecuted() && this.soundState != null) {
            int maxValueAction = intruderQL.getMaxValueAction(this.soundState.getType());
            for (IntruderAgent.Actions currAction : IntruderAgent.Actions.class.getEnumConstants()) {
                if (currAction.value == maxValueAction) {
                    if (currAction.value == 0) {
                        actionQueue.add(rotateTowards(this.soundState.getDirection().getRadians()));
                        float reward = getReward(this.soundState);
                        intruderQL.updateQTable(this.soundState.getType(), currAction.value, reward);
                    } else if (currAction.value == 1) {
                        actionQueue.add(moveStraight(intruderPercepts));
                        float reward = getReward(this.soundState);
                        intruderQL.updateQTable(this.soundState.getType(), currAction.value, reward);
                    } else if (currAction.value == 2) {
                        actionQueue.add(doNothing());
                        float reward = getReward(this.soundState);
                        intruderQL.updateQTable(this.soundState.getType(), currAction.value, reward);
                    } else if (currAction.value == 3) {
                        actionQueue.add(moveStraight(intruderPercepts));
                        float reward = getReward(this.soundState);
                        intruderQL.updateQTable(this.soundState.getType(), currAction.value, reward);
                    } else if (currAction.value == 4) {
                        actionQueue.add(randomRotate());
                        float reward = getReward(this.soundState);
                        intruderQL.updateQTable(this.soundState.getType(), currAction.value, reward);
                    } else if (currAction.value == 5) {
                        actionQueue.add(randomWalk(intruderPercepts));
                        float reward = getReward(this.soundState);
                        intruderQL.updateQTable(this.soundState.getType(), currAction.value, reward);
                    } else if (currAction.value == 6) {
                        actionQueue.add(sprintTowards(intruderPercepts));
                        float reward = getReward(this.soundState);
                        intruderQL.updateQTable(this.soundState.getType(), currAction.value, reward);
    
                        // Smell is used only when intruders are more than 1 - if so, uncomment block of code below
//                    } else if (currAction.value == 7) {
//                        actionQueue.add(dropPheromone1());
//                        float reward = getReward(this.soundState);
//                        intruderQL.updateQTable(this.soundState.getType(), currAction.value, reward);
//                    } else if (currAction.value == 8) {
//                        actionQueue.add(dropPheromone2());
//                        float reward = getReward(this.soundState);
//                        intruderQL.updateQTable(this.soundState.getType(), currAction.value, reward);
//                    } else if (currAction.value == 9) {
//                        actionQueue.add(dropPheromone3());
//                        float reward = getReward(this.soundState);
//                        intruderQL.updateQTable(this.soundState.getType(), currAction.value, reward);
//                    } else if (currAction.value == 10) {
//                        actionQueue.add(dropPheromone4());
//                        float reward = getReward(this.soundState);
//                        intruderQL.updateQTable(this.soundState.getType(), currAction.value, reward);
//                    } else if (currAction.value == 11) {
//                        actionQueue.add(dropPheromone5());
//                        float reward = getReward(this.soundState);
//                        intruderQL.updateQTable(this.soundState.getType(), currAction.value, reward);
                    }
                }
            }
            intruderQL.writeTableToFile();
        }
        
        // Smell is used only when intruders are more than 1 - if so, uncomment block of code below
//        if (this.smellState != null) {
//            int maxValueAction = intruderQL.getMaxValueAction(this.smellState.getType());
//            for (IntruderAgent.Actions currAction : IntruderAgent.Actions.class.getEnumConstants()) {
//                if (currAction.value == maxValueAction) {
//                    if (currAction.value == 1) {
//                        actionQueue.add(moveStraight(intruderPercepts));
//                        float reward = getReward(this.smellState);
//                        intruderQL.updateQTable(this.smellState.getType(), currAction.value, reward);
//                    } else if (currAction.value == 2) {
//                        actionQueue.add(doNothing());
//                        float reward = getReward(this.smellState);
//                        intruderQL.updateQTable(this.smellState.getType(), currAction.value, reward);
//                    } else if (currAction.value == 3) {
//                        actionQueue.add(moveStraight(intruderPercepts));
//                        float reward = getReward(this.smellState);
//                        intruderQL.updateQTable(this.smellState.getType(), currAction.value, reward);
//                    } else if (currAction.value == 4) {
//                        actionQueue.add(randomRotate());
//                        float reward = getReward(this.smellState);
//                        intruderQL.updateQTable(this.smellState.getType(), currAction.value, reward);
//                    } else if (currAction.value == 5) {
//                        actionQueue.add(randomWalk(intruderPercepts));
//                        float reward = getReward(this.smellState);
//                        intruderQL.updateQTable(this.smellState.getType(), currAction.value, reward);
//                    } else if (currAction.value == 6) {
//                        actionQueue.add(sprintTowards(intruderPercepts));
//                        float reward = getReward(this.smellState);
//                        intruderQL.updateQTable(this.smellState.getType(), currAction.value, reward);
//                    } else if (currAction.value == 7) {
//                        actionQueue.add(dropPheromone1());
//                        float reward = getReward(this.smellState);
//                        intruderQL.updateQTable(this.smellState.getType(), currAction.value, reward);
//                    } else if (currAction.value == 8) {
//                        actionQueue.add(dropPheromone2());
//                        float reward = getReward(this.smellState);
//                        intruderQL.updateQTable(this.smellState.getType(), currAction.value, reward);
//                    } else if (currAction.value == 9) {
//                        actionQueue.add(dropPheromone3());
//                        float reward = getReward(this.smellState);
//                        intruderQL.updateQTable(this.smellState.getType(), currAction.value, reward);
//                    } else if (currAction.value == 10) {
//                        actionQueue.add(dropPheromone4());
//                        float reward = getReward(this.smellState);
//                        intruderQL.updateQTable(this.smellState.getType(), currAction.value, reward);
//                    } else if (currAction.value == 11) {
//                        actionQueue.add(dropPheromone5());
//                        float reward = getReward(this.smellState);
//                        intruderQL.updateQTable(this.smellState.getType(), currAction.value, reward);
//                    }
//                }
//            }
//            intruderQL.writeTableToFile();
//        }
    }
    
    protected float getReward(ObjectPercept state) {
        
        float reward;
    
        if (MainNewController.getPath().equals("./src/main/java/Group9/map/maps/test_2.map")){
            if (state.getType() == ObjectPerceptType.TargetArea
                    || state.getType() == ObjectPerceptType.Teleport) {
                reward = 1f;
                return reward;
            }
        }
        else{
            if (state.getType() == ObjectPerceptType.TargetArea) {
                reward = 1f;
                return reward;
            }
        }
    
        if (state.getType() == ObjectPerceptType.Guard) {
            reward = -3f;
            return reward;
        } else if (state.getType() == ObjectPerceptType.Intruder) {
            reward = -1f;
            return reward;
        } else {
            if ((!this.seenObjects
                    .containsValue(Arrays.asList(round(state.getPoint().getX(), 2), round(state.getPoint().getY(), 2)))
                    && !this.seenObjects.containsKey(state.getType()))){
                reward = -0.8f;
            } else {
                reward = -1f;
            }
        }
        return reward;
    }
    
    protected float getReward(SoundPercept state) {
        
        float reward = -1f;
        
        // Assign some basic reward for now, need to fix this later
        if (state.getType() == SoundPerceptType.Noise) {
            reward = -1f;
            return reward;
        } else {
            reward = -3f;
            return reward;
        }
    }
    
    // Smell is used only when intruders are more than 1 - if so, uncomment block of code below
//    protected float getReward(SmellPercept state) {
//
//        float reward = -1f;
//
//        // Assign some basic reward for now, need to fix this later
//        if (state.getType() == SmellPerceptType.Pheromone1) {
//            reward = -1f;
//            return reward;
//        } else if (state.getType() == SmellPerceptType.Pheromone2) {
//            reward = -1f;
//            return reward;
//        } else if (state.getType() == SmellPerceptType.Pheromone3) {
//            reward = -1f;
//            return reward;
//        } else if (state.getType() == SmellPerceptType.Pheromone4) {
//            reward = -1f;
//            return reward;
//        } else if (state.getType() == SmellPerceptType.Pheromone5) {
//            reward = -1f;
//            return reward;
//        }
//        return reward;
//    }
    
    private double round(double value, int places) {
        
        if (places < 0) {
            throw new IllegalArgumentException();
        }
        
        BigDecimal bigDecimal = BigDecimal.valueOf(value);
        bigDecimal = bigDecimal.setScale(places, RoundingMode.HALF_UP);
        return bigDecimal.doubleValue();
    }
    
    private void addToMemory(ObjectPerceptType objectPerceptType, ObjectPercept visionState){
        // Add rounded X and Y coordinates of Points to the seen objects
        this.seenObjects.put(objectPerceptType, Arrays.asList(round(this.visionState.getPoint().getX(), 2),
                round(this.visionState.getPoint().getY(), 2)));
    }
}
