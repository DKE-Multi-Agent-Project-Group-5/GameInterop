package Group5.Agent;

import java.util.ArrayList;
import java.util.Deque;
import java.util.Random;

import Group5.GameController.AgentController;
import Group5.GameController.MapInfo;
import Group5.GameController.Vision;
import Group5.MainNewController;
import Interop.Action.Action;
import Interop.Action.DropPheromone;
import Interop.Action.IntruderAction;
import Interop.Action.Move;
import Interop.Action.NoAction;
import Interop.Action.Rotate;
import Interop.Action.Sprint;
import Interop.Action.Yell;
import Interop.Geometry.Angle;
import Interop.Geometry.Direction;
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
    private ObjectPercept visionState2;
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
    
    Random rand = new Random();
    
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
    
    protected void getVisionState(IntruderPercepts intruderPercepts) {
        Set<ObjectPercept> vision = intruderPercepts.getVision().getObjects().getAll();
        ArrayList<ObjectPerceptType> visionPerceptTypes = new ArrayList<>();
        List<ObjectPercept> visionObjectsSorted = new ArrayList<>(vision);
        for (ObjectPercept e : vision) {
            visionPerceptTypes.add(e.getType());
        }
        
        int n = visionObjectsSorted.size();
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - i - 1; j++) {
                //Bubble Sorting for nearest element in vision
                if (visionObjectsSorted.get(j).getType() != ObjectPerceptType.EmptySpace
                || visionObjectsSorted.get(j+1).getType() != ObjectPerceptType.EmptySpace) {
                    double tempDistance1 = intruderPercepts.getVision().getFieldOfView().getRange().getValue() - visionObjectsSorted.get(j).getPoint().getDistanceFromOrigin().getValue();
                    double tempDistance2 = intruderPercepts.getVision().getFieldOfView().getRange().getValue() - visionObjectsSorted.get(j + 1).getPoint().getDistanceFromOrigin().getValue();
                    if (tempDistance1 > tempDistance2 && visionObjectsSorted.get(j+1).getType() != ObjectPerceptType.EmptySpace) {
                        ObjectPercept temp = visionObjectsSorted.get(j);
                        visionObjectsSorted.set(j, visionObjectsSorted.get(j + 1));
                        visionObjectsSorted.set(j + 1, temp);
                    }
                }
            }
        }
        if (visionPerceptTypes.contains(ObjectPerceptType.Guard) && !visionObjectsSorted.isEmpty()) {
            for (ObjectPercept s : vision) {
                if (s.getType() == ObjectPerceptType.Guard) {
                    this.visionState = s;
                    this.visionState2 = visionObjectsSorted.get(0);
                    break;
                }
            }
        }
        else if (!visionPerceptTypes.contains(ObjectPerceptType.Guard) && visionObjectsSorted.size() > 1) {
            this.visionState = visionObjectsSorted.get(0);
            this.visionState2 = visionObjectsSorted.get(1);
        }
        else if (!visionObjectsSorted.isEmpty() && visionObjectsSorted.size() == 1){
            this.visionState = visionObjectsSorted.get(0);
        }
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
    
    // Smell is used only when intruders are more than 1 - if so, uncomment block of code below
//    protected SmellPercept getSmellState(IntruderPercepts intruderPercepts) {
//        List<SmellPercept> smells = new ArrayList<SmellPercept>(intruderPercepts.getSmells().getAll());
//
//        // Return just the first element from the smells List (for simplicity)
//        if (!smells.isEmpty()) {
//            return smells.get(0);
//        }
//
//        return null;
//    }
    
    public void explore(IntruderPercepts intruderPercepts) {
        
//        this.visionState = this.getVisionState(intruderPercepts);
        this.getVisionState(intruderPercepts);
        this.soundState = this.getSoundState(intruderPercepts);
//        this.smellState = this.getSmellState(intruderPercepts);
        
        if (this.visionState != null && this.visionState2 != null && this.soundState == null) {
            
            int maxValueAction = intruderQL.getMaxValueAction(this.visionState.getType(), this.visionState2.getType());
            for (IntruderAgent.Actions currAction : IntruderAgent.Actions.class.getEnumConstants()) {
                if (currAction.value == maxValueAction) {
                    if (currAction.value == 0) {
                        actionQueue.add(rotateTowards(this.visionState.getPoint()));
                        float reward = getReward(this.visionState, this.visionState2);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.visionState2);
                        intruderQL.updateQTable(this.visionState.getType(), this.visionState2.getType(), currAction.value, reward);
                    } else if (currAction.value == 1) {
                        actionQueue.add(walkTowards(this.visionState.getPoint()));
                        float reward = getReward(this.visionState, this.visionState2);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.visionState2);
                        intruderQL.updateQTable(this.visionState.getType(), this.visionState2.getType(),  currAction.value, reward);
                    } else if (currAction.value == 2) {
                        actionQueue.add(doNothing());
                        float reward = getReward(this.visionState, this.visionState2);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.visionState2);
                        intruderQL.updateQTable(this.visionState.getType(), this.visionState2.getType(),  currAction.value, reward);
                    } else if (currAction.value == 3) {
                        actionQueue.add(moveStraight(intruderPercepts));
                        float reward = getReward(this.visionState, this.visionState2);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.visionState2);
                        intruderQL.updateQTable(this.visionState.getType(), this.visionState2.getType(),  currAction.value, reward);
                    } else if (currAction.value == 4) {
                        actionQueue.add(randomRotate());
                        float reward = getReward(this.visionState, this.visionState2);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.visionState2);
                        intruderQL.updateQTable(this.visionState.getType(), this.visionState2.getType(),  currAction.value, reward);
                    } else if (currAction.value == 5) {
                        actionQueue.add(randomWalk(intruderPercepts));
                        float reward = getReward(this.visionState, this.visionState2);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.visionState2);
                        intruderQL.updateQTable(this.visionState.getType(), this.visionState2.getType(),  currAction.value, reward);
                    } else if (currAction.value == 6) {
                        actionQueue.add(sprintTowards(intruderPercepts));
                        float reward = getReward(this.visionState, this.visionState2);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.visionState2);
                        intruderQL.updateQTable(this.visionState.getType(), this.visionState2.getType(),  currAction.value, reward);
                    } else if (currAction.value == 7) {
                        actionQueue.add(rotateRight());
                        float reward = getReward(this.visionState, this.visionState2);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.visionState2);
                        intruderQL.updateQTable(this.visionState.getType(), this.visionState2.getType(),  currAction.value, reward);
                    } else if (currAction.value == 8) {
                        actionQueue.add(rotateLeft());
                        float reward = getReward(this.visionState, this.visionState2);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.visionState2);
                        intruderQL.updateQTable(this.visionState.getType(), this.visionState2.getType(),  currAction.value, reward);
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

                        float reward = getReward(this.visionState, this.visionState2);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.visionState2);
                        intruderQL.updateQTable(this.visionState.getType(), this.visionState2.getType(),  currAction.value, reward);

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
                    } else if (currAction.value == 10) {
                        actionQueue.add(rotateRight20());
                        float reward = getReward(this.visionState, this.visionState2);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.visionState2);
                        intruderQL.updateQTable(this.visionState.getType(), this.visionState2.getType(),  currAction.value, reward);
                    } else if (currAction.value == 11) {
                        actionQueue.add(rotateLeft20());
                        float reward = getReward(this.visionState, this.visionState2);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.visionState2);
                        intruderQL.updateQTable(this.visionState.getType(), this.visionState2.getType(),  currAction.value, reward);
                    } else if(currAction.value == 12) {
                        actionQueue.add(rotateAway(this.visionState.getPoint()));
                        float reward = getReward(this.visionState, this.visionState2);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.visionState2);
                        intruderQL.updateQTable(this.visionState.getType(), this.visionState2.getType(),  currAction.value, reward);
                    } else if(currAction.value == 13) {
                        actionQueue.add(doNothing());
                        float reward = getReward(this.visionState, this.visionState2);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.visionState2);
                        intruderQL.updateQTable(this.visionState.getType(), this.visionState2.getType(),  currAction.value, reward);
                    } else if(currAction.value ==14) {
                        actionQueue.add(rotateParallelToWall(intruderPercepts));
                        float reward = getReward(this.visionState, this.visionState2);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.visionState2);
                        intruderQL.updateQTable(this.visionState.getType(), this.visionState2.getType(),  currAction.value, reward);
                    } else if (currAction.value == 15) {
                        actionQueue.add(rotateRight10());
                        float reward = getReward(this.visionState, this.visionState2);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.visionState2);
                        intruderQL.updateQTable(this.visionState.getType(), this.visionState2.getType(),  currAction.value, reward);
                    } else if (currAction.value == 16) {
                        actionQueue.add(rotateLeft10());
                        float reward = getReward(this.visionState, this.visionState2);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.visionState2);
                        intruderQL.updateQTable(this.visionState.getType(), this.visionState2.getType(),  currAction.value, reward);
                    }
                }
            }
            intruderQL.serializeQTable3D();
            intruderQL.serializeAlphan();
        }
    
        else if (this.visionState != null && this.soundState != null) {
        
            int maxValueAction = intruderQL.getMaxValueAction(this.visionState.getType(), this.soundState.getType());
            for (IntruderAgent.Actions currAction : IntruderAgent.Actions.class.getEnumConstants()) {
                if (currAction.value == maxValueAction) {
                    if (currAction.value == 0) {
                        actionQueue.add(rotateTowards(this.visionState.getPoint()));
                        float reward = getReward(this.visionState, this.soundState);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.soundState);
                        intruderQL.updateQTable(this.visionState.getType(), this.soundState.getType(), currAction.value, reward);
                    } else if (currAction.value == 1) {
                        actionQueue.add(walkTowards(this.visionState.getPoint()));
                        float reward = getReward(this.visionState, this.soundState);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.soundState);
                        intruderQL.updateQTable(this.visionState.getType(), this.soundState.getType(),  currAction.value, reward);
                    } else if (currAction.value == 2) {
                        actionQueue.add(doNothing());
                        float reward = getReward(this.visionState, this.soundState);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.soundState);
                        intruderQL.updateQTable(this.visionState.getType(), this.soundState.getType(),  currAction.value, reward);
                    } else if (currAction.value == 3) {
                        actionQueue.add(moveStraight(intruderPercepts));
                        float reward = getReward(this.visionState, this.soundState);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.soundState);
                        intruderQL.updateQTable(this.visionState.getType(), this.soundState.getType(),  currAction.value, reward);
                    } else if (currAction.value == 4) {
                        actionQueue.add(randomRotate());
                        float reward = getReward(this.visionState, this.soundState);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.soundState);
                        intruderQL.updateQTable(this.visionState.getType(), this.soundState.getType(),  currAction.value, reward);
                    } else if (currAction.value == 5) {
                        actionQueue.add(randomWalk(intruderPercepts));
                        float reward = getReward(this.visionState, this.soundState);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.soundState);
                        intruderQL.updateQTable(this.visionState.getType(), this.soundState.getType(),  currAction.value, reward);
                    } else if (currAction.value == 6) {
                        actionQueue.add(sprintTowards(intruderPercepts));
                        float reward = getReward(this.visionState, this.soundState);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.soundState);
                        intruderQL.updateQTable(this.visionState.getType(), this.soundState.getType(),  currAction.value, reward);
                    } else if (currAction.value == 7) {
                        actionQueue.add(rotateRight());
                        float reward = getReward(this.visionState, this.soundState);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.soundState);
                        intruderQL.updateQTable(this.visionState.getType(), this.soundState.getType(),  currAction.value, reward);
                    } else if (currAction.value == 8) {
                        actionQueue.add(rotateLeft());
                        float reward = getReward(this.visionState, this.soundState);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.soundState);
                        intruderQL.updateQTable(this.visionState.getType(), this.soundState.getType(),  currAction.value, reward);
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
                    
                        float reward = getReward(this.visionState, this.soundState);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.soundState);
                        intruderQL.updateQTable(this.visionState.getType(), this.soundState.getType(),  currAction.value, reward);
                    
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
                    } else if (currAction.value == 10) {
                        actionQueue.add(rotateLeft20());
                        float reward = getReward(this.visionState, this.soundState);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.soundState);
                        intruderQL.updateQTable(this.visionState.getType(), this.soundState.getType(),  currAction.value, reward);
                    } else if (currAction.value == 11) {
                        actionQueue.add(rotateRight20());
                        float reward = getReward(this.visionState, this.soundState);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.soundState);
                        intruderQL.updateQTable(this.visionState.getType(), this.soundState.getType(),  currAction.value, reward);
                    } else if(currAction.value == 12) {
                        actionQueue.add(rotateAway(this.visionState.getPoint()));
                        float reward = getReward(this.visionState, this.soundState);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.soundState);
                        intruderQL.updateQTable(this.visionState.getType(), this.soundState.getType(),  currAction.value, reward);
                    } else if(currAction.value == 13) {
                        actionQueue.add(rotateAwaySound(soundState.getDirection()));
                        float reward = getReward(this.visionState, this.soundState);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.soundState);
                        intruderQL.updateQTable(this.visionState.getType(), this.soundState.getType(),  currAction.value, reward);
                    } else if(currAction.value ==14) {
                        actionQueue.add(rotateParallelToWall(intruderPercepts));
                        float reward = getReward(this.visionState, this.soundState);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.soundState);
                        intruderQL.updateQTable(this.visionState.getType(), this.soundState.getType(),  currAction.value, reward);
                    } else if (currAction.value == 15) {
                        actionQueue.add(rotateLeft10());
                        float reward = getReward(this.visionState, this.soundState);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.soundState);
                        intruderQL.updateQTable(this.visionState.getType(), this.soundState.getType(),  currAction.value, reward);
                    } else if (currAction.value == 16) {
                        actionQueue.add(rotateRight10());
                        float reward = getReward(this.visionState, this.soundState);
                        // Add rounded X and Y coordinates of Points to the seen objects
                        addToMemory(this.visionState.getType(), this.visionState, this.soundState);
                        intruderQL.updateQTable(this.visionState.getType(), this.soundState.getType(),  currAction.value, reward);
                    }
                }
            }
            intruderQL.serializeQTable3D();
            intruderQL.serializeAlphan();
        }
        
        // For some reason the Game Controller returns an empty list of objects in vision range,
        // when the Intruder is in a Sentry Tower. This handles such a situation and returns a random action,
        // which is not registered as a Q-action for the Q-table.
        else {
            int randomAction = rand.nextInt(NUMBER_OF_POSSIBLE_ACTIONS - 2);
            if (randomAction == 0) {
                actionQueue.add(doNothing());
            } else if (randomAction == 1) {
                actionQueue.add(moveStraight(intruderPercepts));
            } else if (randomAction == 2) {
                actionQueue.add(randomRotate());
            } else if (randomAction == 3) {
                actionQueue.add(randomWalk(intruderPercepts));
            } else {
                actionQueue.add(sprintTowards(intruderPercepts));
            }
        }
    }
    
    protected float getReward(ObjectPercept state, ObjectPercept state2) {
        
        float reward;

        if (MainNewController.getPath().equals("./src/main/java/Group9/map/maps/test_2.map")){
            if (state.getType() == ObjectPerceptType.TargetArea || state2.getType() == ObjectPerceptType.TargetArea
                    || state.getType() == ObjectPerceptType.Teleport || state2.getType() == ObjectPerceptType.Teleport) {
                reward = 1f;
                return reward;
            }
        }
        else{
            if (state.getType() == ObjectPerceptType.TargetArea || state2.getType() == ObjectPerceptType.TargetArea) {
                reward = 1f;
                return reward;
            }
        }
        
        if (state.getType() == ObjectPerceptType.Guard || state2.getType() == ObjectPerceptType.Guard) {
            reward = -1.2f;
            return reward;
        } else if (state.getType() == ObjectPerceptType.Intruder) {
            reward = -1f;
            return reward;
        } else {
            if ((!this.seenObjects
                    .containsValue(Arrays.asList(round(state.getPoint().getX(), 2), round(state.getPoint().getY(), 2)))
            && !this.seenObjects.containsKey(state.getType()))
            || (!this.seenObjects
                    .containsValue(Arrays.asList(round(state2.getPoint().getX(), 2), round(state2.getPoint().getY(), 2)))
            && !this.seenObjects.containsKey(state2.getType()))) {
                reward = -0.8f;
            } else {
                reward = -1f;
            }
        }
        // The Map used for testing has the other end of a TeleportArea to be
        // the actual TargetArea. Because of this, the Agent can never "see" the
        // TargetArea, because it is instantly teleported there and wins the game.
        // This means that the agent is not rewarded properly for the current map.
        // Change the reward values for seeing a TeleportArea so that the agent is
        // rewarded as if this is the TargetArea.
        // NEEDS TO BE CHANGED IF THE MAP IS CHANGED!!!

        return reward;
    }
    
    protected float getReward(ObjectPercept state, SoundPercept state2) {
        // The Map used for testing has the other end of a TeleportArea to be
        // the actual TargetArea. Because of this, the Agent can never "see" the
        // TargetArea, because it is instantly teleported there and wins the game.
        // This means that the agent is not rewarded properly for the current map.
        // Change the reward values for seeing a TeleportArea so that the agent is
        // rewarded as if this is the TargetArea.
        // NEEDS TO BE CHANGED IF THE MAP IS CHANGED!!!
        
        float reward;
        
        if (MainNewController.getPath().equals("./src/main/java/Group9/map/maps/test_2.map")){
            if (state.getType() == ObjectPerceptType.TargetArea || state.getType() == ObjectPerceptType.Teleport) {
                reward = 1f;
                return reward;
            }
        }
        else {
            if (state.getType() == ObjectPerceptType.TargetArea) {
                reward = 1f;
                return reward;
            }
        }
        if (state.getType() == ObjectPerceptType.Guard || state2.getType() == SoundPerceptType.Yell
        || state2.getType() == SoundPerceptType.Noise) {
            reward = -3f;
            return reward;
        } else if (state.getType() == ObjectPerceptType.Intruder) {
            reward = -1f;
            return reward;
        } else {
            if (!this.seenObjects
                    .containsValue(Arrays.asList(round(state.getPoint().getX(), 2), round(state.getPoint().getY(), 2)))
            && !this.seenObjects.containsKey(state.getType())) {
                reward = -0.8f;
            } else {
                reward = -1f;
            }
        }
        return reward;
    }
    
    protected float getReward(SoundPercept state) {
        
        float reward;
        
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
    
    private void addToMemory(ObjectPerceptType objectPerceptType, ObjectPercept visionState, ObjectPercept visionState2){
        // Add rounded X and Y coordinates of Points to the seen objects
        this.seenObjects.put(objectPerceptType, Arrays.asList(round(this.visionState.getPoint().getX(), 2),
                round(this.visionState.getPoint().getY(), 2)));
        this.seenObjects.put(objectPerceptType, Arrays.asList(round(this.visionState2.getPoint().getX(), 2),
                round(this.visionState2.getPoint().getY(), 2)));
    }
    
    private void addToMemory(ObjectPerceptType objectPerceptType, ObjectPercept visionState, SoundPercept soundState){
        // Add rounded X and Y coordinates of Points to the seen objects
        this.seenObjects.put(objectPerceptType, Arrays.asList(round(this.visionState.getPoint().getX(), 2),
                round(this.visionState.getPoint().getY(), 2)));
    }
}
