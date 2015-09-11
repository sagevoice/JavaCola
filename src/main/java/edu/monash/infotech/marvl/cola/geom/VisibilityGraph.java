package edu.monash.infotech.marvl.cola.geom;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
public class VisibilityGraph {

    public List<VisibilityVertex> V = new ArrayList<>();
    public List<VisibilityEdge>   E = new ArrayList<>();

}
