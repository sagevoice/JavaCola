package edu.monash.infotech.marvl.cola;

import edu.monash.infotech.marvl.cola.vpsc.Rectangle;

import java.util.List;

public interface NodeAccessor<T> {

    List<Integer> getChildren(final T v);

    Rectangle getBounds(final T v);
}
