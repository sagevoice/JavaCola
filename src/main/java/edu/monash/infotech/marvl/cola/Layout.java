package edu.monash.infotech.marvl.cola;

import edu.monash.infotech.marvl.cola.geom.*;
import edu.monash.infotech.marvl.cola.powergraph.Groups;
import edu.monash.infotech.marvl.cola.powergraph.LinkTypeAccessor;
import edu.monash.infotech.marvl.cola.powergraph.PowerGraph;
import edu.monash.infotech.marvl.cola.shortestpaths.Calculator;
import edu.monash.infotech.marvl.cola.vpsc.*;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Main interface to cola layout.
 *
 */
public class Layout {

    private double[]       _canvasSize           = {1, 1};
    private Object         _linkDistance         = new Double(20);
    private double         _defaultNodeSize      = 10;
    private Consumer<Void> _linkLengthCalculator = null;
    private boolean        _avoidOverlaps        = false;
    private boolean        _handleDisconnected   = true;
    private double _alpha;
    private double                  _lastStress              = Double.NaN;
    private boolean                 _running                 = false;
    private List<GraphNode>         _nodes                   = new ArrayList<>();
    private List<Group>             _groups                  = new ArrayList<>();
    private Group                   _rootGroup               = null;
    private List<Link>              _links                   = new ArrayList<>();
    private List<Constraint>        _constraints             = new ArrayList<>();
    private double[][]              _distanceMatrix          = null;
    private Descent                 _descent                 = null;
    private DirectedLinkConstraints _directedLinkConstraints = null;
    private double                  _threshold               = 0.01;
    private TangentVisibilityGraph  _visibilityGraph         = null;
    private double                  _groupCompactness        = 1e-6;
    private ToIntFunction<Link>     _linkType                = null;

    // sub-class and override this property to replace with a more sophisticated eventing mechanism
    protected Map<EventType, Consumer<Event>> event = null;

    public Layout on(final String e, final Consumer<Event> listener) {
        return on(EventType.valueOf(e), listener);
    }

    // subscribe a listener to an event
    // sub-class and override this method to replace with a more sophisticated eventing mechanism
    public Layout on(final EventType e, final Consumer<Event> listener) {
        // override me!
        if (null == this.event) {
            this.event = new EnumMap<>(EventType.class);
        }
        this.event.put(e, listener);
        return this;
    }

    // a function that is notified of events like "tick"
    // sub-class and override this method to replace with a more sophisticated eventing mechanism
    protected void trigger(final Event e) {
        if (null != this.event && this.event.containsKey(e.type)) {
            this.event.get(e.type).accept(e);
        }
    }

    // a function that kicks off the iteration tick loop
    // it calls tick() repeatedly until tick returns true (is converged)
    // subclass and override it with something fancier (e.g. dispatch tick on a timer)
    protected void kick() {
        while (!this.tick()) {}
    }

    /** iterate the layout.  Returns true when layout converged. */
    protected boolean tick() {
        if (this._alpha < this._threshold) {
            this._running = false;
            this._alpha = 0;
            this.trigger(new Event(EventType.end, this._alpha, this._lastStress));
            return true;
        }
        final int n = this._nodes.size(),
                m = this._links.size();
        GraphNode o;
        int i;

        this._descent.locks.clear();
        for (i = 0; i < n; ++i) {
            o = this._nodes.get(i);
            if (0 < (o.fixed & 1)) {
                if (Double.isNaN(o.px) || Double.isNaN(o.py)) {
                    o.px = o.x;
                    o.py = o.y;
                }
                final double[] p = new double[] {o.px, o.py};
                this._descent.locks.add(i, p);
            }
        }

        final double s1 = this._descent.rungeKutta();
        //var s1 = descent.reduceStress();
        if (s1 == 0) {
            this._alpha = 0;
        } else if (!Double.isNaN(this._lastStress)) {
            this._alpha = s1; //Math.abs(Math.abs(this._lastStress / s1) - 1);
        }
        this._lastStress = s1;

        final double[] x = this._descent.x[0], y = this._descent.x[1];
        for (i = 0; i < n; ++i) {
            o = this._nodes.get(i);
            o.x = x[i];
            o.y = y[i];
        }

        this.trigger(new Event(EventType.tick, this._alpha, this._lastStress));
        return false;
    }

    /**
     * the list of nodes. If nodes has not been set, but links has, then we instantiate a nodes list here, of the correct size, before
     * returning it.
     *
     * @property nodes {Array}
     * @default empty list
     */
    public List<GraphNode> nodes() {
        if (0 == this._nodes.size() && 0 < this._links.size()) {
            // if we have links but no nodes, create the nodes array now with empty objects for the links to point at.
            int n = 0;
            for (final Link l : this._links) {
                n = Math.max(n, Math.max((Integer)l.source, (Integer)l.target));
            }
            this._nodes = new ArrayList<>(++n);
            for (int i = 0; i < n; ++i) {
                this._nodes.set(i, new GraphNode());
            }
        }
        return this._nodes;
    }

    public Layout nodes(final List<GraphNode> v) {
        this._nodes = v;
        return this;
    }

    /**
     * a list of hierarchical groups defined over nodes
     *
     * @property groups {Array}
     * @default empty list
     */
    public List<Group> groups() {
        return this._groups;
    }

    public Layout groups(final List<Group> x) {
        this._groups = x;
        this._rootGroup = new Group();
        this._groups.forEach(g -> {
            if (Double.isNaN(g.padding)) {
                g.padding = 1;
            }
            if (null != g.leaves) {
                for (int i=0; i<g.leaves.size(); i++) {
                    final GraphNode v = g.leaves.get(i);
                    final GraphNode u = this._nodes.get(v.id);
                    u.parent = g;
                    g.leaves.set(i, u);
                }
            }
            if (null != g.groups) {
                for (int i=0; i<g.groups.size(); i++) {
                    final Group gi = g.groups.get(i);
                    final Group u = _groups.get(gi.id);
                    u.parent = g;
                    g.groups.set(i, u);
                }
            }
        });
        this._rootGroup.leaves = this._nodes.stream().filter(v -> null == v.parent).collect(Collectors.toList());
        this._rootGroup.groups = this._groups.stream().filter(g -> null == g.parent).collect(Collectors.toList());
        return this;
    }

    public Layout powerGraphGroups(final Consumer<Groups> f) {
        Groups g = (new PowerGraph<Link>()).getGroups(this._nodes, this._links, this.linkAccessor, this._rootGroup);
        this.groups(g.groups);
        f.accept(g);
        return this;
    }

    /**
     * if true, the layout will not permit overlaps of the node bounding boxes (defined by the width and height properties on nodes)
     *
     * @property avoidOverlaps
     * @type bool
     * @default false
     */
    public boolean avoidOverlaps() {
        return this._avoidOverlaps;
    }

    public Layout avoidOverlaps(final boolean v) {
        this._avoidOverlaps = v;
        return this;
    }

    /**
     * if true, the final step of the start method will be to nicely pack connected components of the graph. works best if start() is called
     * with a reasonable number of iterations specified and each node has a bounding box (defined by the width and height properties on
     * nodes).
     *
     * @property handleDisconnected
     * @type bool
     * @default true
     */
    public boolean handleDisconnected() {
        return this._handleDisconnected;
    }

    public Layout handleDisconnected(final boolean v) {
        this._handleDisconnected = v;
        return this;
    }

    public Layout flowLayout() {
        return flowLayout("y", 0);
    }

    public Layout flowLayout(final String axis, final double minSeparation) {
        this._directedLinkConstraints = new DirectedLinkConstraints(axis, (l) -> {return minSeparation;});
        return this;
    }

    /**
     * causes constraints to be generated such that directed graphs are laid out either from left-to-right or top-to-bottom. a separation
     * constraint is generated in the selected axis for each edge that is not involved in a cycle (part of a strongly connected component)
     *
     * @param axis          {string} 'x' for left-to-right, 'y' for top-to-bottom
     * @param minSeparation {number|link=>number} either a number specifying a minimum spacing required across all links or a function to
     *                      return the minimum spacing for each link
     */
    public Layout flowLayout(final String axis, ToDoubleFunction<Link> minSeparation) {
        this._directedLinkConstraints = new DirectedLinkConstraints(axis, minSeparation);
        return this;
    }

    /**
     * links defined as source, target pairs over nodes
     *
     * @property links {array}
     * @default empty list
     */
    public List<Link> links() {
        return this._links;
    }

    public Layout links(final List<Link> x) {
        this._links = x;
        return this;
    }

    /**
     * list of constraints of various types
     *
     * @property constraints
     * @type {array}
     * @default empty list
     */
    public List<Constraint> constraints() {
        return this._constraints;
    }

    public Layout constraints(final List<Constraint> c) {
        this._constraints = c;
        return this;
    }

    /**
     * Matrix of ideal distances between all pairs of nodes. If unspecified, the ideal distances for pairs of nodes will be based on the
     * shortest path distance between them.
     *
     * @property distanceMatrix
     * @type {Array of Array of Number}
     * @default null
     */
    public double[][] distanceMatrix() {
        return this._distanceMatrix;
    }

    public Layout distanceMatrix(final double[][] d) {
        this._distanceMatrix = d;
        return this;
    }

    /**
     * Size of the layout canvas dimensions [x,y]. Currently only used to determine the midpoint which is taken as the starting position for
     * nodes with no preassigned x and y.
     *
     * @property size
     * @type {Array of Number}
     */
    public double[] size() {
        return this._canvasSize;
    }

    public Layout size(final double[] x) {
        this._canvasSize = x;
        return this;
    }

    /**
     * Default size (assume nodes are square so both width and height) to use in packing if node width/height are not specified.
     *
     * @property defaultNodeSize
     * @type {Number}
     */
    public double defaultNodeSize() {
        return this._defaultNodeSize;
    }

    public Layout defaultNodeSize(final double x) {
        this._defaultNodeSize = x;
        return this;
    }

    /**
     * The strength of attraction between the group boundaries to each other.
     *
     * @property defaultNodeSize
     * @type {Number}
     */
    public double groupCompactness() {
        return this._groupCompactness;
    }

    public Layout groupCompactness(final double x) {
        this._groupCompactness = x;
        return this;
    }

    /**
     * links have an ideal distance, The automatic layout will compute layout that tries to keep links (AKA edges) as close as possible to
     * this length.
     */
    public Object linkDistance() {
        return this._linkDistance;
    }

    public Layout linkDistance(final ToDoubleFunction<Link> x) {
        this._linkDistance = x;
        this._linkLengthCalculator = null;
        return this;
    }

    public Layout linkDistance(final Double x) {
        this._linkDistance = x;
        this._linkLengthCalculator = null;
        return this;
    }

    public Layout linkType(final ToIntFunction<Link> f) {
        this._linkType = f;
        return this;
    }

    public double convergenceThreshold() {
        return this._threshold;
    }

    public Layout convergenceThreshold(final double x) {
        this._threshold = x;
        return this;
    }

    public double alpha() {
        return this._alpha;
    }

    public Layout alpha(final double x) {
        if (0 != this._alpha) { // if we're already running
            if (0 < x) {
                this._alpha = x; // we might keep it hot
            } else {
                this._alpha = 0; // or, next tick will dispatch "end"
            }
        } else if (0 < x) { // otherwise, fire it up!
            if (!this._running) {
                this._running = true;
                this._alpha = x;
                this.trigger(new Event(EventType.start, this._alpha));
                this.kick();
            }
        }
        return this;
    }

    public double getLinkLength(final Link link) {
        return this._linkDistance instanceof ToDoubleFunction ? (((ToDoubleFunction<Link>)this._linkDistance).applyAsDouble(link))
                                                              : (Double)this._linkDistance;
    }

    static void setLinkLength(final Link link, final double length) {
        link.length = length;
    }

    public int getLinkType(final Link link) {
        return null != this._linkType ? this._linkType.applyAsInt(link) : 0;
    }

    private class LinkAccessor implements LinkLengthAccessor<Link>, LinkTypeAccessor<Link>, LinkSepAccessor<Link> {

        private Layout layout;

        protected LinkAccessor(final Layout layout) {
            this.layout = layout;
        }

        @Override
        public int getSourceIndex(Link l) {
            return Layout.getSourceIndex(l);
        }

        @Override
        public int getTargetIndex(Link l) {
            return Layout.getTargetIndex(l);
        }

        @Override
        public void setLength(Link l, double value) {
            Layout.setLinkLength(l, value);
        }

        @Override
        public int getType(Link l) {
            return layout.getLinkType(l);
        }

        @Override
        public double getMinSeparation(Link l) {
            return layout._directedLinkConstraints.getMinSeparation.applyAsDouble(l);
        }
    }


    private LinkAccessor linkAccessor = new LinkAccessor(this);

    /**
     * compute an ideal length for each link based on the graph structure around that link. you can use this (for example) to create extra
     * space around hub-nodes in dense graphs. In particular this calculation is based on the "symmetric difference" in the neighbour sets
     * of the source and target: i.e. if neighbours of source is a and neighbours of target are b then calculation is: sqrt(|a union b| - |a
     * intersection b|) Actual computation based on inspection of link structure occurs in start(), so links themselves don't have to have
     * been assigned before invoking this function.
     *
     * @param {number} [idealLength] the base length for an edge when its source and start have no other common neighbours (e.g. 40)
     * @param {number} [w] a multiplier for the effect of the length adjustment (e.g. 0.7)
     */
    public Layout symmetricDiffLinkLengths(final double idealLength) {
        return symmetricDiffLinkLengths(idealLength, 1);
    }

    public Layout symmetricDiffLinkLengths(final double idealLength, final double w) {
        this.linkDistance(l -> idealLength * l.length);
        this._linkLengthCalculator = (a) -> LinkLengths.symmetricDiffLinkLengths(this._links, this.linkAccessor, w);
        return this;
    }

    /**
     * compute an ideal length for each link based on the graph structure around that link. you can use this (for example) to create extra
     * space around hub-nodes in dense graphs. In particular this calculation is based on the "symmetric difference" in the neighbour sets
     * of the source and target: i.e. if neighbours of source is a and neighbours of target are b then calculation is: |a intersection b|/|a
     * union b| Actual computation based on inspection of link structure occurs in start(), so links themselves don't have to have been
     * assigned before invoking this function.
     *
     * @param {number} [idealLength] the base length for an edge when its source and start have no other common neighbours (e.g. 40)
     * @param {number} [w] a multiplier for the effect of the length adjustment (e.g. 0.7)
     */
    public Layout jaccardLinkLengths(final double idealLength) {
        return jaccardLinkLengths(idealLength, 1);
    }

    public Layout jaccardLinkLengths(final double idealLength, final double w) {
        this.linkDistance(l -> idealLength * l.length);
        this._linkLengthCalculator = (a) -> LinkLengths.jaccardLinkLengths(this._links, this.linkAccessor, w);
        return this;
    }

    public Layout start() {
        return start(0);
    }

    public Layout start(final int initialUnconstrainedIterations) {
        return start(initialUnconstrainedIterations, 0);
    }

    public Layout start(final int initialUnconstrainedIterations, final int initialUserConstraintIterations) {
        return start(initialUnconstrainedIterations, initialUserConstraintIterations, 0);
    }

    public Layout start(final int initialUnconstrainedIterations, final int initialUserConstraintIterations,
                        final int initialAllConstraintsIterations)
    {
        return start(initialUnconstrainedIterations, initialUserConstraintIterations, initialAllConstraintsIterations, 0);
    }

    public Layout start(final int initialUnconstrainedIterations, final int initialUserConstraintIterations,
                        final int initialAllConstraintsIterations, final int gridSnapIterations)
    {
        return start(initialUnconstrainedIterations, initialUserConstraintIterations, initialAllConstraintsIterations, gridSnapIterations,
                     true);
    }

    /**
     * start the layout process
     *
     * @param {number}    [initialUnconstrainedIterations=0] unconstrained initial layout iterations
     * @param {number}    [initialUserConstraintIterations=0] initial layout iterations with user-specified constraints
     * @param {number}    [initialAllConstraintsIterations=0] initial layout iterations with all constraints including non-overlap
     * @param {number}    [gridSnapIterations=0] iterations of "grid snap", which pulls nodes towards grid cell centers - grid of size
     *                    node[0].width - only really makes sense if all nodes have the same width and height
     * @param keepRunning keep iterating asynchronously via the tick method
     * @method start
     */
    public Layout start(final int initialUnconstrainedIterations, final int initialUserConstraintIterations,
                        final int initialAllConstraintsIterations, final int gridSnapIterations, final boolean keepRunning)
    {
        final int n = this.nodes().size(),
                N = n + 2 * this._groups.size(),
                m = this._links.size();
        final double w = this._canvasSize[0],
                h = this._canvasSize[1];

        if (null != this._linkLengthCalculator) {
            this._linkLengthCalculator.accept(null);
        }

        double[] x = new double[N], y = new double[N];

        final double[][] G;

        final boolean ao = this._avoidOverlaps;

        for (int i = 0; i < _nodes.size(); i++) {
            final GraphNode v = _nodes.get(i);
            v.index = i;
            if (Double.isNaN(v.x)) {
                v.x = w / 2;
                v.y = h / 2;
            }
            x[i] = v.x;
            y[i] = v.y;
        }

        //should we do this to clearly label groups?
        //this._groups.forEach((g, i) => g.groupIndex = i);

        double[][] distances;
        if (null != this._distanceMatrix) {
            // use the user specified distanceMatrix
            distances = this._distanceMatrix;
            G = null;
        } else {
            // construct an n X n distance matrix based on shortest paths through graph (with respect to edge.length).
            distances = (new Calculator<>(N, this._links, (l) -> Layout.getSourceIndex(l), (l) -> Layout.getTargetIndex(l),
                                          l -> this.getLinkLength(l))).DistanceMatrix();

            // G is a square matrix with G[i][j] = 1 iff there exists an edge between node i and node j
            // otherwise 2. (
            G = Descent.createSquareMatrix(N, (a, b) -> 2);
            this._links.forEach(e -> {
                final int u = Layout.getSourceIndex(e), v = Layout.getTargetIndex(e);
                G[u][v] = 1;
                G[v][u] = 1;
            });
        }

        double[][] D = Descent.createSquareMatrix(N, (i, j) -> {
            return distances[i][j];
        });

        if (null != this._rootGroup && null != this._rootGroup.groups) {
            final double strength = this._groupCompactness;
            final double idealDistance = 0.1;
            final BiConsumer<Integer, Integer> addAttraction = (i, j) -> {
                G[i][j] = G[j][i] = strength;
                D[i][j] = D[j][i] = idealDistance;
            };
            int i = n;
            for (final Group g : this._groups) {
                addAttraction.accept(i, i + 1);

                // todo: add terms here attracting children of the group to the group dummy nodes
                //if (typeof g.leaves !== 'undefined')
                //    g.leaves.forEach(l => {
                //        addAttraction(l.index, i, 1e-4, 0.1);
                //        addAttraction(l.index, i + 1, 1e-4, 0.1);
                //    });
                //if (typeof g.groups !== 'undefined')
                //    g.groups.forEach(g => {
                //        var gid = n + g.groupIndex * 2;
                //        addAttraction(gid, i, 0.1, 0.1);
                //        addAttraction(gid + 1, i, 0.1, 0.1);
                //        addAttraction(gid, i + 1, 0.1, 0.1);
                //        addAttraction(gid + 1, i + 1, 0.1, 0.1);
                //    });

                x[i] = 0;
                y[i++] = 0;
                x[i] = 0;
                y[i++] = 0;
            }
        } else {
            this._rootGroup = new Group(_nodes, new ArrayList<>());
        }

        final List<Constraint> curConstraints = null != this._constraints ? this._constraints : new ArrayList<>();
        if (null != this._directedLinkConstraints) {
            curConstraints.addAll(LinkLengths.generateDirectedEdgeConstraints(n, this._links, this._directedLinkConstraints.axis,
                                                                              this.linkAccessor));

            // todo: add containment constraints between group dummy nodes and their children
        }

        this.avoidOverlaps(false);
        this._descent = new Descent(new double[][] {x, y}, D);

        this._descent.locks.clear();
        for (int i = 0; i < n; ++i) {
            final GraphNode o = this._nodes.get(i);
            if (0 < (o.fixed & 1)) {
                o.px = o.x;
                o.py = o.y;
                double[] p = new double[] {o.x, o.y};
                this._descent.locks.add(i, p);
            }
        }
        this._descent.threshold = this._threshold;

        // apply initialIterations without user constraints or nonoverlap constraints
        this._descent.run(initialUnconstrainedIterations);

        // apply initialIterations with user constraints but no nonoverlap constraints
        if (curConstraints.size() > 0) {
            this._descent.project = new Projection(this._nodes, this._groups, this._rootGroup, curConstraints).projectFunctions();
        }
        this._descent.run(initialUserConstraintIterations);

        // subsequent iterations will apply all constraints
        this.avoidOverlaps(ao);
        if (ao) {
            for (int i=0; i<_nodes.size(); i++) {
                final GraphNode v = _nodes.get(i);
                v.x = x[i];
                v.y = y[i];
            }
            this._descent.project = new Projection(this._nodes, this._groups, this._rootGroup, curConstraints, true).projectFunctions();
            for (int i=0; i<_nodes.size(); i++) {
                final GraphNode v = _nodes.get(i);
                x[i] = v.x;
                y[i] = v.y;
            }
        }

        // allow not immediately connected nodes to relax apart (p-stress)
        this._descent.G = G;
        this._descent.run(initialAllConstraintsIterations);

        if (0 < gridSnapIterations) {
            this._descent.snapStrength = 1000;
            this._descent.snapGridSize = this._nodes.get(0).width;
            this._descent.numGridSnapNodes = n;
            this._descent.scaleSnapByMaxH = n != N; // if we have groups then need to scale hessian so grid forces still apply
            double[][] G0 = Descent.createSquareMatrix(N, (i, j) -> {
                if (i >= n || j >= n) {
                    return G[i][j];
                }
                return 0;
            });
            this._descent.G = G0;
            this._descent.run(gridSnapIterations);
        }

        this._links.forEach(l -> {
            if (l.source instanceof Number) {
                l.source = this._nodes.get((Integer)l.source);
            }
            if (l.target instanceof Number) {
                l.target = this._nodes.get((Integer)l.target);
            }
        });
        for (int i = 0; i < _nodes.size(); i++) {
            final GraphNode v = _nodes.get(i);
            v.x = x[i];
            v.y = y[i];
        }

        // recalculate nodes position for disconnected graphs
        if (null == this._distanceMatrix && this._handleDisconnected) {
            final HandleDisconnected handleDisconnected = new HandleDisconnected();
            List<Graph> graphs = handleDisconnected.separateGraphs(this._nodes, this._links);
            handleDisconnected.applyPacking(graphs, w, h, this._defaultNodeSize);

            for (int i = 0; i < _nodes.size(); i++) {
                final GraphNode v = _nodes.get(i);
                this._descent.x[0][i] = v.x;
                this._descent.x[1][i] = v.y;
            }
        }
        return keepRunning ? this.resume() : this;
    }

    public Layout resume() {
        return this.alpha(0.1);
    }

    public Layout stop() {
        return this.alpha(0);
    }

    public void prepareEdgeRouting() {
        prepareEdgeRouting(0);
    }

    /// find a visibility graph over the set of nodes.  assumes all nodes have a
    /// bounds property (a rectangle) and that no pair of bounds overlaps.
    public void prepareEdgeRouting(final double nodeMargin) {
        final int n = _nodes.size();
        final Stream<Point[]> stream = this._nodes.stream().map((v) -> {
                                    return v.bounds.inflate(-nodeMargin).vertices();
                                });
        final Stream<TVGPoint[]> stream2 = stream.map(pointArray -> {
            return Arrays.stream(pointArray).map(p -> {
                return new TVGPoint(p.x, p.y);
            }).collect(Collectors.toList()).toArray(new TVGPoint[1]);
        });
        final TVGPoint[][] P = stream2.collect(Collectors.toList()).toArray(new TVGPoint[n][1]);
        this._visibilityGraph = new TangentVisibilityGraph(P);
    }

    /// find a route avoiding node bounds for the given edge.
    /// assumes the visibility graph has been created (by prepareEdgeRouting method)
    /// and also assumes that nodes have an index property giving their position in the
    /// node array.  This index property is created by the start() method.
    public List<Point> routeEdge(final Link edge, Consumer<TangentVisibilityGraph> draw) {
        List<Point> lineData = new ArrayList<>();
        final GraphNode source = (GraphNode)edge.source;
        final GraphNode target = (GraphNode)edge.target;
        //if (d.source.id === 10 && d.target.id === 11) {
        //    debugger;
        //}
        final TangentVisibilityGraph vg2 = new TangentVisibilityGraph(this._visibilityGraph.P, new VisibilityGraph(this._visibilityGraph.V,
                                                                                                                   this._visibilityGraph.E));
        final TVGPoint port1 = new TVGPoint(source.x, source.y);
        final TVGPoint port2 = new TVGPoint(target.x, target.y);
        final VisibilityVertex start = vg2.addPoint(port1, source.index);
        final VisibilityVertex end = vg2.addPoint(port2, target.index);
        vg2.addEdgeIfVisible(port1, port2, source.index, target.index);
        if (null != draw) {
            draw.accept(vg2);
        }
        final Calculator<VisibilityEdge> spCalc = new Calculator<>(vg2.V.size(), vg2.E, e -> e.source.index, e -> e.target.index, e -> e.length());
        final double[] shortestPath = spCalc.PathFromNodeToNode(start.id, end.id);
        if (1 == shortestPath.length || shortestPath.length == vg2.V.size()) {
            VPSC.makeEdgeBetween(edge, source.innerBounds, target.innerBounds, 5);
            lineData.add(new Point(edge.sourceIntersection.x, edge.sourceIntersection.y));
            lineData.add(new Point(edge.arrowStart.x, edge.arrowStart.y));
        } else {
            int n = shortestPath.length - 2;
            final TVGPoint p = vg2.V.get((int)shortestPath[n]).p;
            final TVGPoint q = vg2.V.get((int)shortestPath[0]).p;
            lineData.add(source.innerBounds.rayIntersection(p.x, p.y));
            for (int i = n; i >= 0; --i) { lineData.add(vg2.V.get((int)shortestPath[i]).p); }
            lineData.add(VPSC.makeEdgeTo(q, target.innerBounds, 5));
        }
        //lineData.forEach((v, i) => {
        //    if (i > 0) {
        //        var u = lineData[i - 1];
        //        this._nodes.forEach(function (node) {
        //            if (node.id === getSourceIndex(d) || node.id === getTargetIndex(d)) return;
        //            var ints = node.innerBounds.lineIntersections(u.x, u.y, v.x, v.y);
        //            if (ints.length > 0) {
        //                debugger;
        //            }
        //        })
        //    }
        //})
        return lineData;
    }

    //The link source and target may be just a node index, or they may be references to nodes themselves.
    static int getSourceIndex(final Link e) {
        return e.source instanceof Number ? (Integer)e.source : ((GraphNode)e.source).index;
    }

    //The link source and target may be just a node index, or they may be references to nodes themselves.
    static int getTargetIndex(final Link e) {
        return e.target instanceof Number ? (Integer)e.target : ((GraphNode)e.target).index;
    }

    // Get a string ID for a given link.
    static String linkId(final Link e) {
        return Layout.getSourceIndex(e) + "-" + Layout.getTargetIndex(e);
    }

    // The fixed property has three bits:
    // Bit 1 can be set externally (e.g., d.fixed = true) and show persist.
    // Bit 2 stores the dragging state, from mousedown to mouseup.
    // Bit 3 stores the hover state, from mouseover to mouseout.
    // Dragend is a special case: it also clears the hover state.

    static void dragStart(final GraphNode d) {
        d.fixed |= 2; // set bit 2
        d.px = d.x; d.py = d.y; // set velocity to zero
    }

    static void dragEnd(final GraphNode d) {
        d.fixed &= ~6; // unset bits 2 and 3
        //d.fixed = 0;
    }

    static void mouseOver(final GraphNode d) {
        d.fixed |= 4; // set bit 3
        d.px = d.x; d.py = d.y; // set velocity to zero
    }

    static void mouseOut(final GraphNode d) {
        d.fixed &= ~4; // unset bit 3
    }
}

