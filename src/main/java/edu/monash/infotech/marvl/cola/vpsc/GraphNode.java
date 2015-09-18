package edu.monash.infotech.marvl.cola.vpsc;

import edu.monash.infotech.marvl.cola.geom.Point;

public class GraphNode extends Point {

    public Rectangle       bounds;
    public IndexedVariable variable;
    // if fixed, layout will not move the node from its specified starting position
    public boolean         fixed;
    public double          width;
    public double          height;
    public double          px = Double.NaN;
    public double          py = Double.NaN;
    public int             index;
    public Group           parent;

    public Rectangle       innerBounds;
    public int             id;

    public GraphNode() {
    }

    public GraphNode(final int id) {
        this.id = id;
    }

    public GraphNode(final Rectangle bounds) {
        this.bounds = bounds;
    }

    public GraphNode(final double x, final double y, final double width, final double height) {
        super(x, y);
        this.width = width;
        this.height = height;
    }



    @Override
    public double get(final String key) {
        if ("width".equals(key)) {
            return this.width;
        } else if ("height".equals(key)) {
            return this.height;
        }
        return super.get(key);
    }

    @Override
    public void set(final String key, final double value) {
        if ("width".equals(key)) {
            this.width = value;
        } else if ("height".equals(key)) {
            this.height = value;
        } else {
            super.set(key, value);
        }
    }

}
