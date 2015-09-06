package edu.monash.infotech.marvl.cola.vpsc;

public class IndexedVariable extends Variable {

    public int index;

    IndexedVariable(final Integer index, final double w) {
        super(0, w);
        this.index = index;
    }
}
