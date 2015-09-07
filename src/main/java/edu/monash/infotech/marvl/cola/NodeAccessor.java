package edu.monash.infotech.marvl.cola;

import edu.monash.infotech.marvl.cola.vpsc.Rectangle;

import java.util.ArrayList;

public interface NodeAccessor<T> {

    ArrayList<Integer> getChildren(final T v);

    Rectangle getBounds(final T v);
}
