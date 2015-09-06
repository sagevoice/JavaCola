package edu.monash.infotech.marvl.cola.vpsc;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class BlockSplit {

    public Constraint constraint;
    public Block      lb;
    public Block      rb;
}
