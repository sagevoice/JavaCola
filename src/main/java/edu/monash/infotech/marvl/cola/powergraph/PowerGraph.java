package edu.monash.infotech.marvl.cola.powergraph;

import edu.monash.infotech.marvl.cola.vpsc.GraphNode;
import edu.monash.infotech.marvl.cola.vpsc.Group;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class PowerGraph<T> {

    public Groups getGroups(final List<GraphNode> nodes, final List<T> links, final LinkTypeAccessor<T> la, final Group rootGroup) {
        final int n = nodes.size();
        final Configuration<T> c = new Configuration<>(n, links, la, rootGroup);
        while (c.greedyMerge()) {}
        final List<PowerEdge> powerEdges = new ArrayList<>();
        final List<Group> g = c.getGroupHierarchy(powerEdges);
        powerEdges.forEach(e -> {
            final Consumer<String> f = (end) -> {
                Object v = e.get(end);
                if (v instanceof Number) {
                    e.set(end, nodes.get(((Number)v).intValue()));
                }
            };
            f.accept("source");
            f.accept("target");
        });
        return new Groups(g, powerEdges);
    }
}