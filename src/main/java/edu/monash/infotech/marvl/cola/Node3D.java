package edu.monash.infotech.marvl.cola;

import edu.monash.infotech.marvl.cola.vpsc.GraphNode;

public class Node3D extends GraphNode {

    public double z = Double.NaN;

    public Node3D() {
    }

    public Node3D(final double x, final double y, final double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public double get(final String key) {
        if ("z".equals(key)) {
            return this.z;
        } else {
            return super.get(key);
        }
    }

    @Override
    public void set(final String key, final double value) {
        if ("z".equals(key)) {
            this.z = value;
        } else {
            super.set(key, value);
        }
    }
}
