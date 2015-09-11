package edu.monash.infotech.marvl.cola;

import edu.monash.infotech.marvl.cola.geom.LineSegment;

import java.util.List;

public class GridLineSegment extends LineSegment {

    public List<Vert> verts;

    public GridLineSegment(final double x1, final double y1, final double x2, final double y2) {
        super(x1, y1, x2, y2);
    }
}
