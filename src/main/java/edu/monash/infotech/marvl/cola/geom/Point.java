package edu.monash.infotech.marvl.cola.geom;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
public class Point {

    public double x;
    public double y;

    public double p(final String s) {
        if ("x".equals(s)) {
            return this.x;
        } else if ("y".equals(s)) {
            return this.y;
        }
        return 0.0;
    }

    public void p(final String s, final double v) {
        if ("x".equals(s)) {
            this.x = v;
        } else if ("y".equals(s)) {
            this.y = v;
        }
    }
}
