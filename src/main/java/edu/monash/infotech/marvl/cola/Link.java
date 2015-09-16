package edu.monash.infotech.marvl.cola;

import edu.monash.infotech.marvl.cola.geom.Point;

public class Link {

    public Object source;
    public Object target;
    public double length;

    // set by VPSC.makeEdgeBetween
    public Point sourceIntersection;
    public Point targetIntersection;
    public Point arrowStart;

    public Link(final Object source, final Object target) {
        this.source = source;
        this.target = target;
    }

    public Link( final int source, final int target) {
        this.source = Integer.valueOf(source);
        this.target = Integer.valueOf(target);
    }
}
