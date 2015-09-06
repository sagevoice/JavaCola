package edu.monash.infotech.marvl.cola.vpsc;

import java.util.ArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class Block {

    public ArrayList<Variable> vars = new ArrayList<>();
    public double        posn;
    public PositionStats ps;
    public int           blockInd;

    Block(final Variable v) {
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
        this.ps.AB = this.ps.AD = this.ps.A2 = 0;
        for (int i = 0, n = this.vars.size(); i < n; ++i) {
            this.ps.addVariable(this.vars.get(i));
        }
        this.posn = this.ps.getPosn();
    }

    private double compute_lm(final Variable v, final Variable u, final Consumer<Constraint> postAction) {
        double dfdv = v.dfdv();
        BiConsumer<Constraint, Variable> f =
        v.visitNeighbours(u, (c, next) -> {
            double _dfdv = this.compute_lm(next, v, postAction);
            if (next == c.right) {
                dfdv += _dfdv * c.left.scale;
                c.lm = _dfdv;
            } else {
                dfdv += _dfdv * c.right.scale;
                c.lm = -_dfdv;
            }
            postAction.accept(c);
        });
        return dfdv / v.scale;
    }

    private void populateSplitBlock(final Variable v, final Variable prev) {
        v.visitNeighbours(prev, (c, next) -> {
            next.offset = v.offset + (next == c.right ? c.gap : -c.gap);
            this.addVariable(next);
            this.populateSplitBlock(next, v);
        });
    }

    // calculate lagrangian multipliers on constraints and
    // find the active constraint in this block with the smallest lagrangian.
    // if the lagrangian is negative, then the constraint is a split candidate.
    public Constraint findMinLM() {
        Constraint m = null;
        this.compute_lm(this.vars.get(0), null, c -> {
            if (!c.equality && (null == m || c.lm < m.lm)) {
                m = c;
            }
        });
        return m;
    }

    private Constraint findMinLMBetween(final Variable lv, final Variable rv) {
        this.compute_lm(lv, null, (c) -> {});
        Constraint m = null;
        this.findPath(lv, null, rv, (c, next) -> {
            if (!c.equality && c.right == next && (m == null || c.lm < m.lm)) {
                m = c;
            }
        });
        return m;
    }

    private boolean findPath(final Variable v, final Variable prev, final Variable to, final BiConsumer<Constraint, Variable> visit) {
        boolean endFound = false;
        v.visitNeighbours(prev, (c, next) -> {
            if (!endFound && (next == to || this.findPath(next, v, to, visit))) {
                endFound = true;
                visit.accept(c, next);
            }
        });
        return endFound;
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
            Block[] bs = Block.split(c);
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
