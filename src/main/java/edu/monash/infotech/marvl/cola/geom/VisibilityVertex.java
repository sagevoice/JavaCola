package edu.monash.infotech.marvl.cola.geom;

public class VisibilityVertex {

    public int      id;
    public int      polyid;
    public int      polyvertid;
    public TVGPoint p;

    VisibilityVertex(final int id, final int polyid, final int polyvertid, final TVGPoint p) {
        this.id = id;
        this.polyid = polyid;
        this.polyvertid = polyvertid;
        this.p = p;
        this.p.vv = this;
    }
}
