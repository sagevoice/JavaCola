package edu.monash.infotech.marvl.cola.geom;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class Point {

    public double x;
    public double y;

    public double p(final String s) {
        if (s.equals("x")) {
            return this.x;
        } else if (s.equals("y")) {
            return this.y;
        }
        return 0.0;
    }

    public void p(final String s, final double v) {
        if (s.equals("x")) {
            this.x = v;
        } else if (s.equals("y")) {
            this.y = v;
        }
    }
}
