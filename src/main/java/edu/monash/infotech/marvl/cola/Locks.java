package edu.monash.infotech.marvl.cola;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class Locks {

    private Map<Integer, double[]> locks = new HashMap<>();

    /**
     * add a lock on the node at index id
     * @param id index of node to be locked
     * @param x required position for node
     */
    public void add(final int id, final double[] x) {
        locks.put(id, x);
    }

    /**
     * clear all locks
     */
    public void clear() {
        locks = new HashMap<>();
    }

    /**
     * @returns false if no locks exist
     */
    public boolean isEmpty() {
        return locks.isEmpty();
    }

    /**
     * perform an operation on each lock
     */
    public void apply(final BiConsumer<Integer, double[]> f) {
        locks.forEach(f);
    }
}
