package edu.monash.infotech.marvl.cola;

import edu.monash.infotech.marvl.cola.geom.Point;
import edu.monash.infotech.marvl.cola.vpsc.GraphNode;

import java.util.*;

public class HandleDisconnected {

    protected static final double PADDING        = 10;
    protected static final double GOLDEN_SECTION = (1 + Math.sqrt(5)) / 2;
    protected static final double FLOAT_EPSILON  = 0.0001;
    protected static final int    MAX_ITERATIONS = 100;

    private double      init_x;
    private double      init_y;
    private double      svg_width;
    private double      svg_height;
    private double      real_width;
    private double      real_height;
    private double      min_width;
    private double      global_bottom;
    private List<Graph> line;


    // get bounding boxes for all separate graphs
    private void calculate_bb(final List<Graph> graphs, final double node_size) {
        graphs.forEach(g -> calculate_single_bb(g, node_size));
    }

    private void calculate_single_bb(final Graph graph, final double node_size) {
        double min_x = Double.MAX_VALUE, min_y = Double.MAX_VALUE,
                max_x = 0, max_y = 0;

        for (final GraphNode v : graph.array) {
            double w = 0 < v.width ? v.width : node_size;
            double h = 0 < v.height ? v.height : node_size;
            w /= 2;
            h /= 2;
            max_x = Math.max(v.x + w, max_x);
            min_x = Math.min(v.x - w, min_x);
            max_y = Math.max(v.y + h, max_y);
            min_y = Math.min(v.y - h, min_y);
        }

        graph.width = max_x - min_x;
        graph.height = max_y - min_y;
    }

    // starts box packing algorithm
    // desired ratio is 1 by default
    private void apply(final List<Graph> data, final double desired_ratio) {
        double curr_best_f = Double.POSITIVE_INFINITY;
        double curr_best = 0;
        data.sort((a, b) -> {
            //noinspection NumericCastThatLosesPrecision
            return (int)(b.height - a.height);
        });

        Graph minGraph = new Graph();
        minGraph.width = Double.MAX_VALUE;
        minGraph = data.stream().reduce(minGraph, (a, b) -> {
            return a.width < b.width ? a : b;
        });
        min_width = minGraph.width;

        double x1 = min_width;
        double left = x1;
        double x2 = get_entire_width(data);
        double right = x2;
        int iterationCounter = 0;

        double f_x1 = Double.MAX_VALUE;
        double f_x2 = Double.MAX_VALUE;
        int flag = -1; // determines which among f_x1 and f_x2 to recompute

        double dx = Double.MAX_VALUE;
        double df = Double.MAX_VALUE;

        while ((dx > min_width) || FLOAT_EPSILON < df) {

            if (1 != flag) {
                x1 = right - (right - left) / GOLDEN_SECTION;
                f_x1 = step(data, x1, desired_ratio);
            }
            if (0 != flag) {
                x2 = left + (right - left) / GOLDEN_SECTION;
                f_x2 = step(data, x2, desired_ratio);
            }

            dx = Math.abs(x1 - x2);
            df = Math.abs(f_x1 - f_x2);

            if (f_x1 < curr_best_f) {
                curr_best_f = f_x1;
                curr_best = x1;
            }

            if (f_x2 < curr_best_f) {
                curr_best_f = f_x2;
                curr_best = x2;
            }

            if (f_x1 > f_x2) {
                left = x1;
                x1 = x2;
                f_x1 = f_x2;
                flag = 1;
            } else {
                right = x2;
                x2 = x1;
                f_x2 = f_x1;
                flag = 0;
            }

            if (MAX_ITERATIONS < iterationCounter++) {
                break;
            }
        }

        step(data, curr_best, desired_ratio);
    }

    private double get_entire_width(final List<Graph> data) {
        double width = 0;
        for (final Graph d : data) {
            width += d.width + PADDING;
        }
        return width;
    }

    // one iteration of the optimization method
    // (gives a proper, but not necessarily optimal packing)
    private double step(final List<Graph> data, final double max_width, final double desired_ratio) {
        line = new ArrayList<>();
        real_width = 0;
        real_height = 0;
        global_bottom = init_y;

        for (final Graph o : data) {
            put_rect(o, max_width);
        }

        return Math.abs(get_real_ratio() - desired_ratio);
    }

    // looking for a position to one box
    private void put_rect(final Graph rect, final double max_width) {

        Graph parent = null;

        for (final Graph l : line) {
            if ((l.space_left >= rect.height) && (l.x + l.width + rect.width + PADDING - max_width) <= FLOAT_EPSILON) {
                parent = l;
                break;
            }
        }

        line.add(rect);

        if (null != parent) {
            rect.x = parent.x + parent.width + PADDING;
            rect.y = parent.bottom;
            rect.space_left = rect.height;
            rect.bottom = rect.y;
            parent.space_left -= rect.height + PADDING;
            parent.bottom += rect.height + PADDING;
        } else {
            rect.y = global_bottom;
            global_bottom += rect.height + PADDING;
            rect.x = init_x;
            rect.bottom = rect.y;
            rect.space_left = rect.height;
        }

        if (-FLOAT_EPSILON < rect.y + rect.height - real_height) {
            real_height = rect.y + rect.height - init_y;
        }
        if (-FLOAT_EPSILON < rect.x + rect.width - real_width) {
            real_width = rect.x + rect.width - init_x;
        }
    }

    private double get_real_ratio() {
        return (real_width / real_height);
    }

    // actual assigning of position to nodes
    private void put_nodes_to_right_positions(final List<Graph> graphs) {
        graphs.forEach(g -> {
            // calculate current graph center:
            final Point center = new Point(0, 0);

            g.array.forEach(node -> {
                center.x += node.x;
                center.y += node.y;
            });

            center.x /= g.array.size();
            center.y /= g.array.size();

            // calculate current top left corner:
            final Point corner = new Point(center.x - g.width / 2, center.y - g.height / 2);
            final Point offset = new Point(g.x - corner.x, g.y - corner.y);

            // put nodes:
            g.array.forEach(node -> {
                node.x = node.x + offset.x + svg_width / 2 - real_width / 2;
                node.y = node.y + offset.y + svg_height / 2 - real_height / 2;
            });
        });
    }

    public void applyPacking(final List<Graph> graphs, final double w, final double h, final double node_size) {
        applyPacking(graphs, w, h, node_size, 1);
    }

    // assign x, y to nodes while using box packing algorithm for disconnected graphs
    public void applyPacking(final List<Graph> graphs, final double w, final double h, final double node_size, final double desired_ratio) {

        if (0 == graphs.size()) {
            return;
        }

        init_x = 0;
        init_y = 0;
        svg_width = w;
        svg_height = h;
        real_width = 0;
        real_height = 0;
        min_width = 0;
        global_bottom = 0;
        line = new ArrayList<>();

        calculate_bb(graphs, node_size);
        apply(graphs, desired_ratio);
        put_nodes_to_right_positions(graphs);
    }

    private int explore_node(final GraphNode n, final boolean is_new, final Map<Integer, Integer> marks, int clusters,
                             final List<Graph> graphs, final Map<Integer, List<GraphNode>> ways)
    {
        if (marks.containsKey(n.index)) {
            return clusters;
        }
        if (is_new) {
            clusters++;
            graphs.add(new Graph());
        }
        marks.put(n.index, clusters);
        graphs.get(clusters - 1).array.add(n);
        List<GraphNode> adjacent = ways.get(n.index);
        if (null == adjacent) {
            return clusters;
        }

        for (int j = 0; j < adjacent.size(); j++) {
            clusters = explore_node(adjacent.get(j), false, marks, clusters, graphs, ways);
        }

        return clusters;
    }

    /** connected components of graph returns an array of {} */
    public List<Graph> separateGraphs(final List<GraphNode> nodes, final List<Link> links) {
        final Map<Integer, Integer> marks = new HashMap<>();
        final Map<Integer, List<GraphNode>> ways = new HashMap<>();
        final List<Graph> graphs = new ArrayList<>();
        int clusters = 0;

        for (final Link link : links) {
            final GraphNode n1 = link.source;
            final GraphNode n2 = link.target;
            if (ways.containsKey(n1.index)) {
                ways.get(n1.index).add(n2);
            } else {
                ways.put(n1.index, new ArrayList<>(Arrays.asList(n2)));
            }

            if (ways.containsKey(n2.index)) {
                ways.get(n2.index).add(n1);
            } else {
                ways.put(n2.index, new ArrayList<>(Arrays.asList(n1)));
            }
        }

        for (final GraphNode node : nodes) {
            if (marks.containsKey(node.index)) {
                continue;
            }
            clusters = explore_node(node, true, marks, clusters, graphs, ways);
        }

        return graphs;
    }
}
