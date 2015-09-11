package edu.monash.infotech.marvl.cola.vpsc;

import edu.monash.infotech.marvl.cola.TriConsumer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class Block {

    public List<Variable> vars = new ArrayList<>();
    public double        posn;
    public PositionStats ps;
    public int           blockInd;

    public Block(final Variable v) {
        v.offset = 0;
        this.ps = new PositionStats(v.scale);
        this.addVariable(v);
    }

    private void addVariable(final Variable v) {
        v.block = this;
        this.vars.add(v);
        this.ps.addVariable(v);
        this.posn = this.ps.getPosn();
    }

    // move the block where it needs to be to minimize cost
    public void updateWeightedPosition() {
        this.ps.AB = 0;
        this.ps.AD = 0;
        this.ps.A2 = 0;
        for (final Variable var : this.vars) {
            this.ps.addVariable(var);
        }
        this.posn = this.ps.getPosn();
    }

    private double compute_lm(final Variable v, final Variable u, final BiConsumer<Constraint, ValueHolder> postAction,
                              final ValueHolder postValue)
    {
        final ValueHolder<Double> valueHolder = new ValueHolder<>(v.dfdv());
        v.visitNeighbours(u, (c, next, value) -> {
            double _dfdv = this.compute_lm(next, v, postAction, postValue);
            final ValueHolder<Double> dfdv = value;
            if (next == c.right) {
                dfdv.set(dfdv.get() + _dfdv * c.left.scale);
                c.lm = _dfdv;
            } else {
                dfdv.set(dfdv.get() + _dfdv * c.right.scale);
                c.lm = -_dfdv;
            }
            postAction.accept(c, postValue);
        }, valueHolder);
        return valueHolder.get() / v.scale;
    }

    private void populateSplitBlock(final Variable v, final Variable prev) {
        v.visitNeighbours(prev, (c, next, value) -> {
            next.offset = v.offset + (next == c.right ? c.gap : -c.gap);
            this.addVariable(next);
            this.populateSplitBlock(next, v);
        }, null);
    }

    // calculate lagrangian multipliers on constraints and
    // find the active constraint in this block with the smallest lagrangian.
    // if the lagrangian is negative, then the constraint is a split candidate.
    public Constraint findMinLM() {
        final ValueHolder<Constraint> valueHolder = new ValueHolder<>(null);
        this.compute_lm(this.vars.get(0), null, (c, value) -> {
            final ValueHolder<Constraint> m = value;
            if (!c.equality && (null == m.get() || c.lm < m.get().lm)) {
                m.set(c);
            }
        }, valueHolder);
        return valueHolder.get();
    }

    private Constraint findMinLMBetween(final Variable lv, final Variable rv) {
        this.compute_lm(lv, null, (c, v) -> {}, null);
        final ValueHolder<Constraint> valueHolder = new ValueHolder<>(null);
        this.findPath(lv, null, rv, (c, next, value) -> {
            final ValueHolder<Constraint> m = value;
            if (!c.equality && c.right == next && (null == m.get() || c.lm < m.get().lm)) {
                m.set(c);
            }
        }, valueHolder);
        return valueHolder.get();
    }

    private Boolean findPath(final Variable v, final Variable prev, final Variable to,
                             final TriConsumer<Constraint, Variable, ValueHolder> visit, final ValueHolder visitValue)
    {
        final ValueHolder<Boolean> valueHolder = new ValueHolder<>(false);
        v.visitNeighbours(prev, (c, next, value) -> {
            final ValueHolder<Boolean> endFound = value;
            if (!endFound.get() && (next == to || this.findPath(next, v, to, visit, visitValue))) {
                endFound.set(true);
                visit.accept(c, next, visitValue);
            }
        }, valueHolder);
        return valueHolder.get();
    }

    // Search active constraint tree from u to see if there is a directed path to v.
    // Returns true if path is found.
    public boolean isActiveDirectedPathBetween(final Variable u, final Variable v) {
        if (u == v) {
            return true;
        }
        int i = u.cOut.size();
        while (0 < i--) {
            Constraint c = u.cOut.get(i);
            if (c.active && this.isActiveDirectedPathBetween(c.right, v)) { return true; }
        }
        return false;
    }

    // split the block into two by deactivating the specified constraint
    public static Block[] split(final Constraint c) {
        c.active = false;
        return new Block[] {Block.createSplitBlock(c.left), Block.createSplitBlock(c.right)};
    }

    private static Block createSplitBlock(final Variable startVar) {
        final Block b = new Block(startVar);
        b.populateSplitBlock(startVar, null);
        return b;
    }

    // find a split point somewhere between the specified variables
    public BlockSplit splitBetween(final Variable vl, final Variable vr) {
        final Constraint c = this.findMinLMBetween(vl, vr);
        if (null != c) {
            final Block[] bs = Block.split(c);
            return new BlockSplit(c, bs[0], bs[1]);
        }
        // couldn't find a split point - for example the active path is all equality constraints
        return null;
    }

    public void mergeAcross(final Block b, final Constraint c, final double dist) {
        c.active = true;
        for (int i = 0, n = b.vars.size(); i < n; ++i) {
            final Variable v = b.vars.get(i);
            v.offset += dist;
            this.addVariable(v);
        }
        this.posn = this.ps.getPosn();
    }

    public double cost() {
        double sum = 0;
        int i = this.vars.size();
        while (0 < i--) {
            final Variable v = this.vars.get(i);
            final double d = v.position() - v.desiredPosition;
            sum += d * d * v.weight;
        }
        return sum;
    }
}
