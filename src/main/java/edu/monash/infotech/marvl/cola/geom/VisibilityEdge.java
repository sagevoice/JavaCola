package edu.monash.infotech.marvl.cola.geom;


public class VisibilityEdge {

    public VisibilityVertex source;
    public VisibilityVertex target;

    public VisibilityEdge(final VisibilityVertex source, final VisibilityVertex target) {
        this.source = source;
        this.target = target;
    }

    public double length() {
        final double dx = this.source.p.x - this.target.p.x;
        final double dy = this.source.p.y - this.target.p.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
