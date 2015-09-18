package edu.monash.infotech.marvl.cola.vpsc;

import java.util.List;

public class Group {

    public Rectangle       bounds;
    public double          padding = Double.NaN;
    public double          stiffness;
    public List<GraphNode> leaves;
    public List<Group>     groups;
    public IndexedVariable minVar;
    public IndexedVariable maxVar;
    public int             id;
    public Group           parent;

    public Group() {
    }

    public Group(final int id) {
        this.id = id;
    }

    public Group(final Rectangle bounds) {
        this.bounds = bounds;
    }

    public Group(final List<GraphNode> leaves, final List<Group> groups) {
        this.leaves = leaves;
        this.groups = groups;
    }

    public Group(final double padding, final List<GraphNode> leaves) {
        this.padding = padding;
        this.leaves = leaves;
    }

}
