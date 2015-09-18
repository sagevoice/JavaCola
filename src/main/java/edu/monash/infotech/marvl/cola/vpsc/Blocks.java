package edu.monash.infotech.marvl.cola.vpsc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Blocks {

    public final List<Block>    list;
    public final List<Variable> vs;

    public Blocks(final List<Variable> vs) {
        //noinspection AssignmentToCollectionOrArrayFieldFromParameter
        this.vs = vs;
        int i = 0;
        this.list = new ArrayList<>(vs.size());
        for (final Variable v : vs) {
            final Block b = new Block(v);
            this.list.add(b);
            b.blockInd = i;
            i++;
        }
    }

    public double cost() {
        double sum = 0;
        int i = this.list.size();
        while (0 < i--) {
            sum += this.list.get(i).cost();
        }
        return sum;
    }

    public void insert(final Block b) {
        b.blockInd = this.list.size();
        this.list.add(b);
    }

    public void remove(final Block b) {
        final int last = this.list.size() - 1;
        final Block swapBlock = this.list.get(last);
        this.list.remove(last);
        if (b != swapBlock) {
            this.list.set(b.blockInd, swapBlock);
            swapBlock.blockInd = b.blockInd;
        }
    }

    // merge the blocks on either side of the specified constraint, by copying the smaller block into the larger
    // and deleting the smaller.
    public void merge(final Constraint c) {
        final Block l = c.left.block, r = c.right.block;
        final double dist = c.right.offset - c.left.offset - c.gap;
        if (l.vars.size() < r.vars.size()) {
            r.mergeAcross(l, c, dist);
            this.remove(l);
        } else {
            l.mergeAcross(r, c, -dist);
            this.remove(r);
        }
    }

    // useful, for example, after variable desired positions change.
    public void updateBlockPositions() {
        this.list.forEach(b -> b.updateWeightedPosition());
    }

    // split each block across its constraint with the minimum lagrangian
    public void split(final List<Constraint> inactive) {
        this.updateBlockPositions();
        this.list.forEach(b -> {
            final Constraint v = b.findMinLM();
            if (null != v && v.lm < Solver.LAGRANGIAN_TOLERANCE) {
                b = v.left.block;
                Arrays.stream(Block.split(v)).forEach(nb -> this.insert(nb));
                this.remove(b);
                inactive.add(v);
            }
        });
    }
}
