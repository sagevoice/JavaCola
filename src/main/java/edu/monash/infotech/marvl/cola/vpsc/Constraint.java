package edu.monash.infotech.marvl.cola.vpsc;

public class Constraint {

    public double       lm;
    public boolean      active;
    public boolean      unsatisfiable;
    public Variable     left;
    public Variable     right;
    public int          leftIndex;
    public int          rightIndex;
    public double       gap;
    public boolean      equality;
    public String       type;
    public String       axis;

    public Constraint(final Variable left, final Variable right, final double gap) {
        this(left, right, gap, false);
    }

    public Constraint(final String axis, final int leftIndex, final int rightIndex, final double gap) {
        this.axis = axis;
        this.leftIndex = leftIndex;
        this.rightIndex = rightIndex;
        this.gap = gap;
    }

    public Constraint(final Variable left, final Variable right, final double gap, final boolean equality) {
        this.left = left;
        this.right = right;
        this.gap = gap;
        this.equality = equality;
    }

    public Constraint(final String type, final String axis, final int leftIndex, final int rightIndex, final double gap, final boolean equality) {
        this.type = type;
        this.axis = axis;
        this.leftIndex = leftIndex;
        this.rightIndex = rightIndex;
        this.gap = gap;
        this.equality = equality;
    }

    public double slack() {
        return this.unsatisfiable ? Double.MAX_VALUE
                                  : this.right.scale * this.right.position() - this.gap
                                    - this.left.scale * this.left.position();
    }
}
