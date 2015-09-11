package edu.monash.infotech.marvl.cola.powergraph;

import edu.monash.infotech.marvl.cola.vpsc.Group;
import edu.monash.infotech.marvl.cola.vpsc.IndexedVariable;
import edu.monash.infotech.marvl.cola.vpsc.Rectangle;

public class Definition {

    public Rectangle       bounds;
    public double          padding;
    public double          stiffness;
    public IndexedVariable minVar;
    public IndexedVariable maxVar;

    public Definition(final Group group) {
        this.bounds = group.bounds;
        this.padding = group.padding;
        this.stiffness = group.stiffness;
        this.minVar = group.minVar;
        this.maxVar = group.maxVar;
    }

    public void setupGroup(final Group group) {
        group.bounds = this.bounds;
        group.padding = this.padding;
        group.stiffness = this.stiffness;
        group.minVar = this.minVar;
        group.maxVar = this.maxVar;
    }
}
