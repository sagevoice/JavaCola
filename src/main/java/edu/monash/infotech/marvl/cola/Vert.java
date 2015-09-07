package edu.monash.infotech.marvl.cola;

public class Vert {

    public int             id;
    public double          x;
    public double          y;
    public NodeWrapper     node;
    public GridLineSegment line;

    Vert(final int id, final double x, final double y) {
        this(id, x, y, null);
    }

    Vert(final int id, final double x, final double y, final NodeWrapper node) {
        this(id, x, y, node, null);
    }

    Vert(final int id, final double x, final double y, final NodeWrapper node, final GridLineSegment line) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.node = node;
        this.line = line;
    }
}
