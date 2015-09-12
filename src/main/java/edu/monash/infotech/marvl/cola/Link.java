package edu.monash.infotech.marvl.cola;

import edu.monash.infotech.marvl.cola.geom.Point;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class Link {

    public Object    source;
    public Object    target;
    public double    length;

    // set by VPSC.makeEdgeBetween
    public Point     sourceIntersection;
    public Point     targetIntersection;
    public Point     arrowStart;
}
