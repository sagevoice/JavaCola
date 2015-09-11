package edu.monash.infotech.marvl.cola.powergraph;

import edu.monash.infotech.marvl.cola.Node;
import edu.monash.infotech.marvl.cola.vpsc.Group;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class PowerGraph<T> {

    public Groups getGroups(final Node[] nodes, final T[] links, final LinkTypeAccessor<T> la, final Group rootGroup) {
        final int n = nodes.length;
        final Configuration<T> c = new Configuration<>(n, Arrays.asList(links), la, rootGroup);
        while (c.greedyMerge()) {}
        final List<PowerEdge> powerEdges = new ArrayList<>();
        final List<Group> g = c.getGroupHierarchy(powerEdges);
        powerEdges.forEach(e -> {
            final Consumer<String> f = (end) -> {
                Object v = e.get(end);
                if (v instanceof Number) {
                    e.set(end, nodes[((Number)v).intValue()]);
                }
            };
            f.accept("source");
            f.accept("target");
        });
        return new Groups(g, powerEdges);
    }
}