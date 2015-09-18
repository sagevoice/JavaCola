package edu.monash.infotech.marvl.cola;

import edu.monash.infotech.marvl.cola.vpsc.Constraint;
import edu.monash.infotech.marvl.cola.vpsc.ValueHolder;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.ToDoubleBiFunction;

public class LinkLengths {

    // compute the size of the union of two sets a and b
    public static int unionCount(final Map<Integer, Boolean> a, final Map<Integer, Boolean> b) {
        final Map<Integer, Boolean> u = new HashMap<>();
        for (final Integer i : a.keySet()) {
            u.put(i, true);
        }
        for (final Integer i : b.keySet()) {
            u.put(i, true);
        }
        return u.keySet().size();
    }

    // compute the size of the intersection of two sets a and b
    public static int intersectionCount(final Map<Integer, Boolean> a, final Map<Integer, Boolean> b) {
        int n = 0;
        for (final Integer i : a.keySet()) {
            if (b.containsKey(i)) {
                ++n;
            }
        }
        return n;
    }

    public static <T> Map<Integer, Map<Integer, Boolean>> getNeighbours(final List<T> links, final LinkAccessor<T> la) {
        final Map<Integer, Map<Integer, Boolean>> neighbours = new HashMap<>();
        final BiConsumer<Integer, Integer> addNeighbours = (u, v) -> {
            if (!neighbours.containsKey(u)) {
                neighbours.put(u, new HashMap<>());
            }
            neighbours.get(u).put(v, true);
        };
        links.forEach(e -> {
            final int u = la.getSourceIndex(e), v = la.getTargetIndex(e);
            addNeighbours.accept(u, v);
            addNeighbours.accept(v, u);
        });
        return neighbours;
    }

    // modify the lengths of the specified links by the result of function f weighted by w
    public static <T> void computeLinkLengths(final List<T> links, final double w,
                                              ToDoubleBiFunction<Map<Integer, Boolean>, Map<Integer, Boolean>> f, LinkLengthAccessor<T> la)
    {
        final Map<Integer, Map<Integer, Boolean>> neighbours = getNeighbours(links, la);
        links.forEach(l -> {
            final Map<Integer, Boolean> a = neighbours.get(la.getSourceIndex(l));
            final Map<Integer, Boolean> b = neighbours.get(la.getTargetIndex(l));
            la.setLength(l, 1 + w * f.applyAsDouble(a, b));
        });
    }

    public static <T> void symmetricDiffLinkLengths(final List<T> links, final LinkLengthAccessor<T> la) {
        LinkLengths.symmetricDiffLinkLengths(links, la, 1);
    }

    /**
     * modify the specified link lengths based on the symmetric difference of their neighbours
     *
     */
    public static <T> void symmetricDiffLinkLengths(final List<T> links, final LinkLengthAccessor<T> la, final double w) {
        computeLinkLengths(links, w, (a, b) -> Math.sqrt(unionCount(a, b) - intersectionCount(a, b)), la);
    }

    public static <T> void jaccardLinkLengths(final List<T> links, final LinkLengthAccessor<T> la) {
        jaccardLinkLengths(links, la, 1);
    }

    /** modify the specified links lengths based on the jaccard difference between their neighbours */
    public static <T> void jaccardLinkLengths(final List<T> links, final LinkLengthAccessor<T> la, final double w) {
        computeLinkLengths(links, w, (a, b) ->
                1.1 > Math.min(a.keySet().size(), b.keySet().size()) ? 0 : intersectionCount(a, b) / unionCount(a, b)
                , la);
    }

    private static class Node {

        protected int index = -1;
        protected int        lowlink;
        protected boolean    onStack;
        protected List<Node> out;
        protected int        id;

        protected Node(final int id) {
            this.id = id;
            this.out = new ArrayList<>();
        }
    }

    /** generate separation constraints for all edges unless both their source and sink are in the same strongly connected component */
    public static <T> List<Constraint> generateDirectedEdgeConstraints(final int n, final List<T> links, final String axis,
                                                                       final LinkSepAccessor<T> la)
    {
        final List<List<Integer>> components = stronglyConnectedComponents(n, links, la);
        final List<Integer> nodes = new ArrayList<>(n);
        for (int i=0; i < n; i++) {
            nodes.add(Integer.valueOf(0));
        }
        for (int i = 0; i < components.size(); i++) {
            final List<Integer> c = components.get(i);
            final int j = i;
            c.forEach(v -> nodes.set(v, j));
        }
        final List<Constraint> constraints = new ArrayList<>();
        links.forEach(l -> {
            final int ui = la.getSourceIndex(l), vi = la.getTargetIndex(l);
            final int u = nodes.get(ui), v = nodes.get(vi);
            if (u != v) {
                constraints.add(new Constraint(axis, ui, vi, la.getMinSeparation(l)));
            }
        });
        return constraints;
    }

    /**
     * Tarjan's strongly connected components algorithm for directed graphs returns an array of arrays of node indicies in each of the
     * strongly connected components. a vertex not in a SCC of two or more nodes is it's own SCC. adaptation of
     * https://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm
     */
    public static <T> List<List<Integer>> stronglyConnectedComponents(final int numVertices, final List<T> edges, final LinkAccessor<T> la) {
        final List<Node> nodes = new ArrayList<>();
        final ValueHolder<Integer> valueHolder = new ValueHolder<>(0);
        final List<Node> stack = new ArrayList<>();
        final List<List<Integer>> components = new ArrayList<>();
        final TriConsumer<Node, ValueHolder<Integer>, TriConsumer> strongConnect = (v, index, callback) -> {
            // Set the depth index for v to the smallest unused index
            v.lowlink = index.get();
            index.set(index.get() + 1);
            v.index = v.lowlink;
            stack.add(v);
            v.onStack = true;

            // Consider successors of v
            for (final Node w : v.out) {
                if (0 > w.index) {
                    // Successor w has not yet been visited; recurse on it
                    callback.accept(w, index, callback);
                    v.lowlink = Math.min(v.lowlink, w.lowlink);
                } else if (w.onStack) {
                    // Successor w is in stack S and hence in the current SCC
                    v.lowlink = Math.min(v.lowlink, w.index);
                }
            }

            // If v is a root node, pop the stack and generate an SCC
            if (v.lowlink == v.index) {
                // start a new strongly connected component
                List<Integer> component = new ArrayList<>();
                while (0 < stack.size()) {
                    final Node w = stack.remove(stack.size() - 1);
                    w.onStack = false;
                    //add w to current strongly connected component
                    component.add(w.id);
                    if (w.equals(v)) { break; }
                }
                // output the current strongly connected component
                components.add(component);
            }
        };

        for (int i = 0; i < numVertices; i++) {
            nodes.add(new Node(i));
        }
        for (final T e : edges) {
            final Node v = nodes.get(la.getSourceIndex(e)),
                    w = nodes.get(la.getTargetIndex(e));
            v.out.add(w);
        }
        for (final Node v : nodes) {
            if (0 > v.index) {
                strongConnect.accept(v, valueHolder, strongConnect);
            }
        }
        return components;
    }

}