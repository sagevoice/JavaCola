package edu.monash.infotech.marvl.cola;

import lombok.AllArgsConstructor;

import java.util.ArrayList;

@AllArgsConstructor
public class AncestorPath {

    public NodeWrapper            commonAncestor;
    public ArrayList<NodeWrapper> lineages;
}
