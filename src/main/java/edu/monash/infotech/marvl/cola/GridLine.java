package edu.monash.infotech.marvl.cola;

import lombok.AllArgsConstructor;

import java.util.ArrayList;

// a horizontal or vertical line of nodes
@AllArgsConstructor
public class GridLine {

    public ArrayList<NodeWrapper> nodes;
    public double                 pos;

}
