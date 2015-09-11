package edu.monash.infotech.marvl.cola.vpsc;

import java.util.List;

public class Group {

    public Rectangle       bounds;
    public double          padding;
    public double          stiffness;
    public List<Leaf>      leaves;
    public List<Group>     groups;
    public IndexedVariable minVar;
    public IndexedVariable maxVar;
    public int             id;

    public Group() {
    }

    public Group(final int id) {
        this.id = id;
    }

    public Group(final Rectangle bounds) {
        this.bounds = bounds;
    }
}
