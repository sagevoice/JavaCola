package edu.monash.infotech.marvl.cola.shortestpaths;

import edu.monash.infotech.marvl.cola.PairingHeap;

import java.util.ArrayList;
import java.util.List;

public class Node {

    public int               id;
    public List<Neighbour>   neighbours;
    public double            d;
    public Node              prev;
    public PairingHeap<Node> q;

    public Node(final int id) {
        this.id = id;
        this.neighbours = new ArrayList<>();
    }
}
