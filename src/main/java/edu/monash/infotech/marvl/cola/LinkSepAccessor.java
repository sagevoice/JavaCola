package edu.monash.infotech.marvl.cola;

public interface LinkSepAccessor<T> extends LinkAccessor<T> {

    public double getMinSeparation(final T l);

}
