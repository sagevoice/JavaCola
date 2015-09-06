package edu.monash.infotech.marvl.cola.geom;

import lombok.AllArgsConstructor;

import java.util.ArrayList;

@AllArgsConstructor
class VisibilityGraph {

    public ArrayList<VisibilityVertex> V = new ArrayList<>();
    public ArrayList<VisibilityEdge>   E = new ArrayList<>();

}
