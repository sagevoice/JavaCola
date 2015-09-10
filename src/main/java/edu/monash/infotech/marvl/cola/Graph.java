package edu.monash.infotech.marvl.cola;

import java.util.ArrayList;
import java.util.List;

public class Graph {

    public List<Node> array;
    public double x;
    public double y;
    public double width;
    public double height;
    public double space_left;
    public double bottom;

    public Graph() {
        this.array = new ArrayList<>();
    }
}
