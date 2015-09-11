package edu.monash.infotech.marvl.cola;

import edu.monash.infotech.marvl.cola.geom.Point;
import edu.monash.infotech.marvl.cola.vpsc.GraphNode;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class Link {

    public GraphNode source;
    public GraphNode target;
    public double    length;

    // set by VPSC.makeEdgeBetween
    public Point     sourceIntersection;
    public Point     targetIntersection;
    public Point     arrowStart;
}
