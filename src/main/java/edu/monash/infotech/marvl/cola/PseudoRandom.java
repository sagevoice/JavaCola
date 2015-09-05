package edu.monash.infotech.marvl.cola;

// Linear congruential pseudo random number generator
public class PseudoRandom {
    private static final long a = 214013;
    private static final long c = 2531011;
    private static final long m = 2147483648L;
    private static final long range = 32767;
    private long seed;

    PseudoRandom() {
        seed = 1;
    }

    PseudoRandom(final long seed) {
        this.seed = seed;
    }

    // random real between 0 and 1
    public double getNext() {
        seed = (seed * a + c) % m;
        return (seed >> 16) / range;
    }

    // random real between min and max
    public double getNextBetween(final double min, final double max) {
        return min + this.getNext() * (max - min);
    }
}
