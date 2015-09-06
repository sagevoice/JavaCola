package edu.monash.infotech.marvl.cola.geom;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class VisibilityEdge {
    public VisibilityVertex source;
    public VisibilityVertex target;

    public double length() {
        double dx = this.source.p.x - this.target.p.x;
        double dy = this.source.p.y - this.target.p.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
