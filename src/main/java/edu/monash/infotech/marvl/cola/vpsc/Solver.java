package edu.monash.infotech.marvl.cola.vpsc;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Solver {

    public static final double LAGRANGIAN_TOLERANCE = -1e-4;
    public static final double ZERO_UPPERBOUND      = -1e-10;

    public Blocks           bs;
    public List<Constraint> inactive;
    public List<Variable>   vs;
    public List<Constraint> cs;

    public Solver(final List<Variable> vs, final List<Constraint> cs) {
        //noinspection AssignmentToCollectionOrArrayFieldFromParameter
        this.vs = vs;
        vs.forEach(v -> {
            v.cIn = new ArrayList<>();
            v.cOut = new ArrayList<>();
        });
        //noinspection AssignmentToCollectionOrArrayFieldFromParameter
        this.cs = cs;
        cs.forEach(c -> {
            c.left.cOut.add(c);
            c.right.cIn.add(c);
        });
        this.inactive = cs.stream().map(c -> {
            c.active = false;
            return c;
        }).collect(Collectors.toList());
        this.bs = null;
    }

    public double cost() {
        return this.bs.cost();
    }

    // set starting positions without changing desired positions.
    // Note: it throws away any previous block structure.
    public void setStartingPositions(final double[] ps) {
        this.inactive = this.cs.stream().map(c -> {
            c.active = false;
            return c;
        }).collect(Collectors.toList());
        this.bs = new Blocks(this.vs);

        for (int i = 0, n = bs.list.size(); i < n; ++i) {
            final Block b = bs.list.get(i);
            b.posn = ps[i];
        }
    }

    public void setDesiredPositions(final double[] ps) {
        for (int i = 0; i < this.vs.size(); ++i) {
            this.vs.get(i).desiredPosition = ps[i];
        }
    }

    private Constraint mostViolated() {
        double minSlack = Double.MAX_VALUE;
        Constraint v = null;
        List<Constraint> l = this.inactive;
        final int n = l.size();
        int deletePoint = n;
        for (int i = 0; i < n; ++i) {
            final Constraint c = l.get(i);
            if (c.unsatisfiable) {
                continue;
            }
            final double slack = c.slack();
            if (c.equality || slack < minSlack) {
                minSlack = slack;
                v = c;
                deletePoint = i;
                if (c.equality) {
                    break;
                }
            }
        }
        if (deletePoint != n &&
            (Solver.ZERO_UPPERBOUND > minSlack && !v.active || v.equality)) {
            l.set(deletePoint, l.get(n - 1));
            l.remove(n - 1);
        }
        return v;
    }

    // satisfy constraints by building block structure over violated constraints
    // and moving the blocks to their desired positions
    public void satisfy() {
        if (null == this.bs) {
            this.bs = new Blocks(this.vs);
        }
        this.bs.split(this.inactive);
        Constraint v = null;
        while (null != (v = this.mostViolated()) && (v.equality || Solver.ZERO_UPPERBOUND > v.slack() && !v.active)) {
            final Block lb = v.left.block, rb = v.right.block;
            if (lb != rb) {
                this.bs.merge(v);
            } else {
                if (lb.isActiveDirectedPathBetween(v.right, v.left)) {
                    // cycle found!
                    v.unsatisfiable = true;
                    continue;
                }
                // constraint is within block, need to split first
                BlockSplit split = lb.splitBetween(v.left, v.right);
                if (null != split) {
                    this.bs.insert(split.lb);
                    this.bs.insert(split.rb);
                    this.bs.remove(lb);
                    this.inactive.add(split.constraint);
                } else {
                    v.unsatisfiable = true;
                    continue;
                }
                if (0 <= v.slack()) {
                    // v was satisfied by the above split!
                    this.inactive.add(v);
                } else {
                    this.bs.merge(v);
                }
            }
        }
    }

    // repeatedly build and split block structure until we converge to an optimal solution
    public double solve() {
        this.satisfy();
        double lastcost = Double.MAX_VALUE, cost = this.bs.cost();
        while (0.0001 < Math.abs(lastcost - cost)) {
            this.satisfy();
            lastcost = cost;
            cost = this.bs.cost();
        }
        return cost;
    }
}
