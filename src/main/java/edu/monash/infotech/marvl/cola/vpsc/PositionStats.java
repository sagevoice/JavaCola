package edu.monash.infotech.marvl.cola.vpsc;

public class PositionStats {

    public double AB = 0;
    public double AD = 0;
    public double A2 = 0;
    public double scale;

    PositionStats(final double scale) {
        this.scale = scale;
    }

    public void addVariable(final Variable v) {
        final double ai = this.scale / v.scale;
        final double bi = v.offset / v.scale;
        final double wi = v.weight;
        this.AB += wi * ai * bi;
        this.AD += wi * ai * v.desiredPosition;
        this.A2 += wi * ai * ai;
    }

    public double getPosn() {
        return (this.AD - this.AB) / this.A2;
    }
}
