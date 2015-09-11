package edu.monash.infotech.marvl.cola.powergraph;

import edu.monash.infotech.marvl.cola.LinkAccessor;

public interface LinkTypeAccessor<T> extends LinkAccessor<T> {

    // return a unique identifier for the type of the link
    public int getType(final T l);
}
