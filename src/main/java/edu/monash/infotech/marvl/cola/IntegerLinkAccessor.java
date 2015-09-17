package edu.monash.infotech.marvl.cola;

import edu.monash.infotech.marvl.cola.powergraph.LinkTypeAccessor;

public class IntegerLinkAccessor implements LinkTypeAccessor<Link> {

    @Override
    public int getSourceIndex(final Link l) {
        return ((Integer)l.source).intValue();
    }

    @Override
    public int getTargetIndex(final Link l) {
        return ((Integer)l.target).intValue();
    }

    @Override
    public int getType(final Link l) {
        return 0;
    }

}
