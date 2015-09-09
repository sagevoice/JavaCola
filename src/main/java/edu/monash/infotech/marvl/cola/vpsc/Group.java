package edu.monash.infotech.marvl.cola.vpsc;

public class Group {

    public Rectangle bounds;
    public double    padding;
    public double    stiffness;
    public Leaf[]    leaves;
    public Group[]   groups;
    public IndexedVariable  minVar;
    public IndexedVariable  maxVar;

    public Group(final Rectangle bounds) {
        this.bounds = bounds;
    }
}
