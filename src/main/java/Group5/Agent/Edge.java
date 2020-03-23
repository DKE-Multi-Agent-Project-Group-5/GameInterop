package Group5.Agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Edge implements Comparable<Edge> {
    private String id;
    private List<Vertex> verticies;
    private final double distance; //distance between the two vertices
    private Edge mirror;

    public Edge(String id, Vertex source, Vertex destination, double weight) {
        this.id = id;
        this.verticies.add(source);
        this.verticies.add(destination);
        this.distance = weight;
    }

    public Edge(Vertex v1, Vertex v2, double distance) {
        this.verticies.add(v1);
        this.verticies.add(v2);
        this.distance = distance;
    }

    @Override
    public int compareTo(Edge o) {
        if (this.distance < o.getDistance())
            return -1;
        else
            return 1;
    }

    public String getId() {
        return id;
    }

    public List<Vertex> getVerticies() {
        return this.verticies;
    }

    public double getDistance() {
        return distance;
    }

    public Edge getMirror() {
        return mirror;
    }

    public void setMirror(Edge mirror) {
        this.mirror = mirror;
        mirror.setMirrorNoRecursion(this);
    }

    private void setMirrorNoRecursion(Edge mirror){
        this.mirror = mirror;
    }

    /*
    @Override
    public String toString() {
        return source + " " + destination;
    }

     */


}