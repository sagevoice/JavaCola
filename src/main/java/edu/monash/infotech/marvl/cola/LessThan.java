package edu.monash.infotech.marvl.cola;

@FunctionalInterface
public interface LessThan<T> {

    boolean compare(T o1, T o2);
}

