package edu.monash.infotech.marvl.cola.vpsc;

import edu.monash.infotech.marvl.cola.geom.Point;

public class GraphNode extends Point {

    public Rectangle       bounds;
    public IndexedVariable variable;
    public boolean         fixed;
    public double          width;
    public double          height;
    public double          px;
    public double          py;

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
