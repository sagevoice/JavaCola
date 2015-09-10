package edu.monash.infotech.marvl.cola;

public interface LinkAccessor<T> {

    public int getSourceIndex(final T l);

    public int getTargetIndex(final T l);
}
