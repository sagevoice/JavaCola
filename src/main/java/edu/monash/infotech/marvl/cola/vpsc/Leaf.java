package edu.monash.infotech.marvl.cola.vpsc;

public class Leaf {

    public Rectangle bounds;
    public Variable  variable;
    public int       id;

    public Leaf(final int id) {
        this.id = id;
    }

    public Leaf(final Rectangle bounds, final Variable variable) {
        this.bounds = bounds;
        this.variable = variable;
    }
}
