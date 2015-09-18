package edu.monash.infotech.marvl.cola;

import edu.monash.infotech.marvl.cola.vpsc.Rectangle;

import java.util.List;

public class NodeWrapper {

    public boolean       leaf;
    public NodeWrapper   parent;
    public List<Vert>    ports;
    public int           id;
    public Rectangle     rect;
    public List<Integer> children;

    public NodeWrapper(final int id, final Rectangle rect, final List<Integer> children) {
        this.id = id;
        this.rect = rect;
        //noinspection AssignmentToCollectionOrArrayFieldFromParameter
        this.children = children;
        this.leaf = (null == children) || (0 == children.size());
    }
}
