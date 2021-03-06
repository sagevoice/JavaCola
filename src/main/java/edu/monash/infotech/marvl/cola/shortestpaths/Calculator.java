package edu.monash.infotech.marvl.cola.shortestpaths;

import edu.monash.infotech.marvl.cola.PriorityQueue;
import edu.monash.infotech.marvl.cola.TriFunction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/** calculates all-pairs shortest paths or shortest paths from a single node */
public class Calculator<T> {

    private Node[] neighbours;
    public  int    n;
    public  List<T>    es;

    /**
     * @param n  {number} number of nodes
     * @param es {Edge[]} array of edges
     */
    public Calculator(final int n, final List<T> es, final ToIntFunction<T> getSourceIndex, final ToIntFunction<T> getTargetIndex,
                      final ToDoubleFunction<T> getLength)
    {
        this.n = n;
        //noinspection AssignmentToCollectionOrArrayFieldFromParameter
        this.es = es;
        this.neighbours = new Node[this.n];
        int i = this.n;
        while (0 < i--) {
            this.neighbours[i] = new Node(i);
        }

        i = this.es.size();
        while (0 < i--) {
            final T e = this.es.get(i);
            final int u = getSourceIndex.applyAsInt(e);
            final int v = getTargetIndex.applyAsInt(e);
            final double d = getLength.applyAsDouble(e);
            this.neighbours[u].neighbours.add(new Neighbour(v, d));
            this.neighbours[v].neighbours.add(new Neighbour(u, d));
        }
    }

    /**
     * compute shortest paths for graph over n nodes with edges an array of source/target pairs edges may optionally have a length
     * attribute.  1 is the default. Uses Johnson's algorithm.
     *
     * @return the distance matrix
     *
     */
    public double[][] DistanceMatrix() {
        final double[][] D = new double[this.n][0];
        for (int i = 0; i < this.n; ++i) {
            D[i] = this.dijkstraNeighbours(i);
        }
        return D;
    }

    /**
     * get shortest paths from a specified start node
     *
     * @param start node index
     * @return array of path lengths
     *
     */
    public double[] DistancesFromNode(final int start) {
        return this.dijkstraNeighbours(start);
    }

    public double[] PathFromNodeToNode(final int start, final int end) {
        return this.dijkstraNeighbours(start, end);
    }

    // find shortest path from start to end, with the opportunity at
    // each edge traversal to compute a custom cost based on the
    // previous edge.  For example, to penalise bends.
    public List<Integer> PathFromNodeToNodeWithPrevCost(
            final int start, final int end,
            final TriFunction<Integer, Integer, Integer, Double> prevCost)
    {
        final PriorityQueue<QueueEntry> q = new PriorityQueue<>((a, b) -> a.d <= b.d);
        Node u = this.neighbours[start];
        QueueEntry qu = new QueueEntry(u, null, 0);
        final Map<String, Double> visitedFrom = new HashMap<>();
        q.push(qu);
        while (!q.empty()) {
            qu = q.pop();
            u = qu.node;
            if (u.id == end) {
                break;
            }
            int i = u.neighbours.size();
            while (0 < i--) {
                final Neighbour neighbour = u.neighbours.get(i);
                final Node v = this.neighbours[neighbour.id];

                // don't double back
                if (null != qu.prev && v.id == qu.prev.node.id) {
                    continue;
                }

                // don't retraverse an edge if it has already been explored
                // from a lower cost route
                final String viduid = v.id + "," + u.id;
                if (visitedFrom.containsKey(viduid) && visitedFrom.get(viduid) <= qu.d) {
                    continue;
                }

                final double cc = null != qu.prev ? prevCost.apply(qu.prev.node.id, u.id, v.id) : 0;
                final double t = qu.d + neighbour.distance + cc;

                // store cost of this traversal
                visitedFrom.put(viduid, t);
                q.push(new QueueEntry(v, qu, t));
            }
        }
        final List<Integer> path = new ArrayList<>();
        while (null != qu.prev) {
            qu = qu.prev;
            path.add(qu.node.id);
        }
        return path;
    }

    private double[] dijkstraNeighbours(final int start) {
        return this.dijkstraNeighbours(start, -1);
    }


    private double[] dijkstraNeighbours(final int start, final int dest) {
        final PriorityQueue<Node> q = new PriorityQueue<>((a, b) -> a.d <= b.d);
        int i = this.neighbours.length;
        final double[] d = new double[i];
        while (0 < i--) {
            final Node node = this.neighbours[i];
            node.d = i == start ? 0 : Double.POSITIVE_INFINITY;
            node.q = q.push(node);
        }
        while (!q.empty()) {
            final Node u = q.pop();
            d[u.id] = u.d;
            if (u.id == dest) {
                final List<Double> path = new ArrayList<>();
                Node v = u;
                while (null != v.prev) {
                    path.add(Double.valueOf(v.prev.id));
                    v = v.prev;
                }
                final double[] result = new double[path.size()];
                i = 0;
                for (final Double p : path ) {
                    result[i++] = p.doubleValue();
                }
                return result;
            }
            i = u.neighbours.size();
            while (0 < i--) {
                final Neighbour neighbour = u.neighbours.get(i);
                final Node v = this.neighbours[neighbour.id];
                final double t = u.d + neighbour.distance;
                if (Double.MAX_VALUE != u.d && v.d > t) {
                    v.d = t;
                    v.prev = u;
                    q.reduceKey(v.q, v, (e, q2) -> {e.q = q2;});
                }
            }
        }
        return d;
    }
}
