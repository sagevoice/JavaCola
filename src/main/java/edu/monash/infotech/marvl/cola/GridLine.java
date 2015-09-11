package edu.monash.infotech.marvl.cola;

import lombok.AllArgsConstructor;

import java.util.List;

// a horizontal or vertical line of nodes
@AllArgsConstructor
public class GridLine {

    public List<NodeWrapper> nodes;
    public double            pos;

}
