package Group5.GameController;

public class Door extends Area {


    private boolean closed;
    public Door(int x1, int y1, int x2, int y2, int x3, int y3, int x4, int y4, int targetX, int targetY){
        super(x1, y1, x2, y2,x3, y3, x4, y4);
        closed = true;
    }

    public boolean doorClosed(){
        return  closed;
    }

    public void openDoor(){
        closed = false;
    }

    public void closeDoor(){
        closed = true;
    }
}