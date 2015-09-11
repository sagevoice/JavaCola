package edu.monash.infotech.marvl.cola.geom;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
public class Point {

    public double x = Double.NaN;
    public double y = Double.NaN;

    public double get(final String key) {
        if ("x".equals(key)) {
            return this.x;
        } else if ("y".equals(key)) {
            return this.y;
        }
        return 0.0;
    }

    public void set(final String key, final double value) {
        if ("x".equals(key)) {
            this.x = value;
        } else if ("y".equals(key)) {
            this.y = value;
        }
    }
}
