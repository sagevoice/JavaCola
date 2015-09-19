package edu.monash.infotech.marvl.cola.geom;

public class VisibilityVertex {

    public int      id;
    public TVGPoint p;

    VisibilityVertex(final int id, final TVGPoint p) {
        this.id = id;
        this.p = p;
        this.p.vv = this;
    }
}
