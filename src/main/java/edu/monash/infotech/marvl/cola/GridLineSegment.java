package edu.monash.infotech.marvl.cola;

import edu.monash.infotech.marvl.cola.geom.LineSegment;

import java.util.ArrayList;

public class GridLineSegment extends LineSegment {

    public ArrayList<Vert> verts;

    public GridLineSegment(final double x1, final double y1, final double x2, final double y2) {
        super(x1, y1, x2, y2);
    }
}
