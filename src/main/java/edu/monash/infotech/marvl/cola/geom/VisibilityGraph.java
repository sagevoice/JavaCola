package edu.monash.infotech.marvl.cola.geom;

import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
class VisibilityGraph {

    public List<VisibilityVertex> V = new ArrayList<>();
    public List<VisibilityEdge>   E = new ArrayList<>();

}
