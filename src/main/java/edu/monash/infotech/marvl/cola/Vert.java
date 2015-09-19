package edu.monash.infotech.marvl.cola;

import edu.monash.infotech.marvl.cola.geom.Point;

public class Vert extends Point {

    public int             id;
    public NodeWrapper     node;

    public Vert(final double x, final double y) {
        super(x, y);
    }

    public Vert(final int id, final double x, final double y) {
        this(id, x, y, null);
    }

    public Vert(final int id, final double x, final double y, final NodeWrapper node) {
        super(x, y);
        this.id = id;
        this.node = node;
    }
}
