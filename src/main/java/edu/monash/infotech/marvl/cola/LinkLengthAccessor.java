package edu.monash.infotech.marvl.cola;

public interface LinkLengthAccessor<T> extends LinkAccessor<T> {

    public void setLength(final T l, final double value);
}
