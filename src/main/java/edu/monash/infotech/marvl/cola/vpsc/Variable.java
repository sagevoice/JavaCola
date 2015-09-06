package edu.monash.infotech.marvl.cola.vpsc;

import java.util.ArrayList;
import java.util.function.BiConsumer;

public class Variable {

    public double                offset = 0;
    public Block                 block;
    public ArrayList<Constraint> cIn;
    public ArrayList<Constraint> cOut;
    public double                desiredPosition;
    public double                weight;
    public double                scale;

    Variable(final double desiredPosition) {
        this(desiredPosition, 1.0);
    }

    Variable(final double desiredPosition, final double weight) {
        this(desiredPosition, weight, 1.0);
    }

    Variable(final double desiredPosition, final double weight, final double scale) {
        this.desiredPosition = desiredPosition;
        this.weight = weight;
        this.scale = scale;
    }

    public double dfdv() {
        return 2.0 * this.weight * (this.position() - this.desiredPosition);
    }

    public double position() {
        return (this.block.ps.scale * this.block.posn + this.offset) / this.scale;
    }

    // visit neighbours by active constraints within the same block
    public void visitNeighbours(final Variable prev, final BiConsumer<Constraint, Variable> f) {
        this.cOut.forEach(c -> {if (c.active && prev != c.right) { f.accept(c, c.right); }});
        this.cIn.forEach(c -> {if (c.active && prev != c.left) { f.accept(c, c.left); }});
    }
}
