package edu.monash.infotech.marvl.cola;

import lombok.AllArgsConstructor;

import java.util.List;

@AllArgsConstructor
public class AncestorPath {

    public NodeWrapper       commonAncestor;
    public List<NodeWrapper> lineages;
}
