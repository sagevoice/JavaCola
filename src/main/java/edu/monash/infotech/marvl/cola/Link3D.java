package edu.monash.infotech.marvl.cola;

public class Link3D {
    public int source;
    public int target;
    public double length;

    public Link3D (final int source, final int target) {
        this.source = source;
        this.target = target;
    }

    public double actualLength(final double[][] x) {
        double c = 0;
        for (final double[] v : x ) {
            final Double dx = v[this.target] - v[this.source];
            c += dx * dx;
        }
        return Math.sqrt(c);
    }
}
