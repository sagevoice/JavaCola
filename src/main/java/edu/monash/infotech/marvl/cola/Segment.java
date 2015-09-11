package edu.monash.infotech.marvl.cola;

import edu.monash.infotech.marvl.cola.geom.Point;

public class Segment {

    public int   edgeid;
    public int   i;
    public Point p0;
    public Point p1;

    public Segment(final Point p0, final Point p1) {
        this.p0 = p0;
        this.p1 = p1;
    }

    public Point get(final int index){
        if( 0 == index) {
            return p0;
        } else if (1 == index) {
            return p1;
        }
        return null;
    }
}
