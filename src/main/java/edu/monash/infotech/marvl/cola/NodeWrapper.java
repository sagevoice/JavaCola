package edu.monash.infotech.marvl.cola;

import edu.monash.infotech.marvl.cola.vpsc.Rectangle;

import java.util.ArrayList;

public class NodeWrapper {

    public boolean            leaf;
    public NodeWrapper        parent;
    public ArrayList<Vert>    ports;
    public int                id;
    public Rectangle          rect;
    public ArrayList<Integer> children;

    NodeWrapper(final int id, final Rectangle rect, final ArrayList<Integer> children) {
        this.id = id;
        this.rect = rect;
        //noinspection AssignmentToCollectionOrArrayFieldFromParameter
        this.children = children;
        this.leaf = (null == children) || (0 == children.size());
    }
}
