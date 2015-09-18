package edu.monash.infotech.marvl.cola;

import java.util.ArrayList;
import java.util.Collection;

public class GridPath<T> extends ArrayList<T> {

    public boolean reversed;

    /**
     * Constructs an empty GridPath with the specified initial capacity.
     *
     * @param  initialCapacity  the initial capacity of the list
     * @throws IllegalArgumentException if the specified initial capacity
     *         is negative
     */
    public GridPath(final int initialCapacity) {
        super(initialCapacity);
    }

    /**
     * Constructs an empty GridPath with an initial capacity of ten.
     */
    public GridPath() {
        super();
    }

    /**
     * Constructs a GridPath containing the elements of the specified
     * collection, in the order they are returned by the collection's
     * iterator.
     *
     * @param c the collection whose elements are to be placed into this GridPath
     * @throws NullPointerException if the specified collection is null
     */
    public GridPath(final Collection<? extends T> c) {
        super(c);
    }
}
