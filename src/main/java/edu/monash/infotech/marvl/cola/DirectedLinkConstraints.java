package edu.monash.infotech.marvl.cola;

import lombok.AllArgsConstructor;

import java.util.function.ToDoubleFunction;

@AllArgsConstructor
public class DirectedLinkConstraints {
    public String axis;
    public ToDoubleFunction<Link> getMinSeparation;
}
