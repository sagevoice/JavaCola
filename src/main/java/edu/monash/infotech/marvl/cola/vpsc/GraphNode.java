package edu.monash.infotech.marvl.cola.vpsc;

import edu.monash.infotech.marvl.cola.geom.Point;

public class GraphNode extends Point {

    public Rectangle bounds;
    public IndexedVariable  variable;
    public boolean   fixed;
    public double    width;
    public double    height;
    public double    px;
    public double    py;

    public double d(final String s) {
        if ("x".equals(s)) {
            return this.width;
        } else if ("y".equals(s)) {
            return this.height;
        }
        return 0.0;
    }

    public void d(final String s, final double v) {
        if ("x".equals(s)) {
            this.width = v;
        } else if ("y".equals(s)) {
            this.height = v;
        }
    }

}
