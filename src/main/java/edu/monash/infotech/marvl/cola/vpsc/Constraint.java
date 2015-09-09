package edu.monash.infotech.marvl.cola.vpsc;

import java.util.List;

public class Constraint {

    public double lm;
    public boolean active        = false;
    public boolean unsatisfiable = false;
    public Variable     left;
    public Variable     right;
    public double       gap;
    public boolean      equality;
    public String       type;
    public String       axis;
    public List<Offset> offsets;

    public Constraint(final Variable left, final Variable right, final double gap) {
        this(left, right, gap, false);
    }

    public Constraint(final Variable left, final Variable right, final double gap, final boolean equality) {
        this.left = left;
        this.right = right;
        this.gap = gap;
        this.equality = equality;
    }

    public double slack() {
        return this.unsatisfiable ? Double.MAX_VALUE
                                  : this.right.scale * this.right.position() - this.gap
                                    - this.left.scale * this.left.position();
    }
}
