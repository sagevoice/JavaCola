package edu.monash.infotech.marvl.cola;

@FunctionalInterface
public interface ToBooleanBiFunction<T, U> {

    /**
     * Applies this function to the given arguments.
     *
     * @param t the first function argument
     * @param u the second function argument
     * @return the function result
     */
    boolean applyAsBoolean(T t, U u);
}
