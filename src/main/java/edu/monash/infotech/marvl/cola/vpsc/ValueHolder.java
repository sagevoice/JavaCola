package edu.monash.infotech.marvl.cola.vpsc;

public class ValueHolder<T> {
    private T value;

    public ValueHolder(final T value) {
        this.value = value;
    }

    public T get() {
        return value;
    }

    public void set(final T value) {
        this.value = value;
    }
}
