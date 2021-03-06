package edu.monash.infotech.marvl.cola;

import edu.monash.infotech.marvl.cola.geom.Point;
import edu.monash.infotech.marvl.cola.shortestpaths.Calculator;
import edu.monash.infotech.marvl.cola.vpsc.Constraint;
import edu.monash.infotech.marvl.cola.vpsc.Rectangle;
import edu.monash.infotech.marvl.cola.vpsc.Solver;
import edu.monash.infotech.marvl.cola.vpsc.Variable;

import java.util.*;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GridRouter<T> {

    public List<NodeWrapper> leaves;
    public List<NodeWrapper> groups;
    public List<NodeWrapper> nodes;
    public List<GridLine>    cols;
    public List<GridLine>    rows;
    public NodeWrapper       root;
    public List<Vert>        verts;
    public List<LinkWrapper> edges;
    public List<NodeWrapper> backToFront;
    public List<NodeWrapper> obstacles;
    public List<LinkWrapper> passableEdges;
    public double            groupPadding;

    public GridRouter(final List<T> originalnodes, final NodeAccessor<T> accessor) {
        this(originalnodes, accessor, 12.0);
    }

    public GridRouter(final List<T> originalnodes, final NodeAccessor<T> accessor, final double groupPadding) {
        this.groupPadding = groupPadding;
        this.nodes = new ArrayList<>(originalnodes.size());
        for (int i = 0; i < originalnodes.size(); i++) {
            final T v = originalnodes.get(i);
            this.nodes.add(new NodeWrapper(i, accessor.getBounds(v), accessor.getChildren(v)));
        }
        this.leaves = this.nodes.stream().filter(v -> v.leaf).collect(Collectors.toList());
        this.groups = this.nodes.stream().filter(g -> !g.leaf).collect(Collectors.toList());

        this.cols = this.getGridLines("x");
        this.rows = this.getGridLines("y");

        // create parents for each node or group that is a member of another's children
        this.groups.forEach(v -> v.children.forEach(c -> {
            this.nodes.get(c).parent = v;
        }));

        // root claims the remaining orphans
        this.root = new NodeWrapper(-1, Rectangle.empty(), new ArrayList<>());
        this.nodes.forEach(v -> {
            if (null == v.parent) {
                v.parent = this.root;
                this.root.children.add(v.id);
            }

            // each node will have grid vertices associated with it,
            // some inside the node and some on the boundary
            // leaf nodes will have exactly one internal node at the center
            // and four boundary nodes
            // groups will have potentially many of each
            v.ports = new ArrayList<>();
        });

        // nodes ordered by their position in the group hierarchy
        this.backToFront = this.nodes.stream().sorted((x, y) -> this.getDepth(x) - this.getDepth(y))
                                     .collect(Collectors.toList());

        // compute boundary rectangles for each group
        // has to be done from front to back, i.e. inside groups to outside groups
        // such that each can be made large enough to enclose its interior
        final List<NodeWrapper> list = new ArrayList<>(this.backToFront);
        Collections.reverse(list);
        final Stream<NodeWrapper> frontToBackGroups = list.stream().filter(g -> !g.leaf);
        frontToBackGroups.forEach(v -> {
            Rectangle r = Rectangle.empty();
            for (int i = 0, n = v.children.size(); i < n; i++) {
                int c = v.children.get(i);
                r = r.union(this.nodes.get(c).rect);
            }
            v.rect = r.inflate(this.groupPadding);
        });

        final List<Double> colMids = this.midPoints(this.cols.stream().map(r -> r.pos).collect(Collectors.toList()));
        final List<Double> rowMids = this.midPoints(this.rows.stream().map(r -> r.pos).collect(Collectors.toList()));

        // setup extents of lines
        final double rowx = colMids.get(0), rowX = colMids.get(colMids.size() - 1);
        final double coly = rowMids.get(0), colY = rowMids.get(rowMids.size() - 1);

        // horizontal lines
        final List<GridLineSegment> hlines = Stream.concat(this.rows.stream().map(r -> new GridLineSegment(rowx, r.pos, rowX, r.pos)),
                                                           rowMids.stream().map(m -> new GridLineSegment(rowx, m, rowX, m)))
                                                   .collect(Collectors.toList());

        // vertical lines
        final List<GridLineSegment> vlines = Stream.concat(this.cols.stream().map(c -> new GridLineSegment(c.pos, coly, c.pos, colY)),
                                                           colMids.stream().map(m -> new GridLineSegment(m, coly, m, colY))).collect(
                Collectors.toList());
        // the full set of lines
        final List<GridLineSegment> lines = Stream.concat(hlines.stream(), vlines.stream()).collect(Collectors.toList());

        // we record the vertices associated with each line
        lines.forEach(l -> {l.verts = new ArrayList<>();});

        // the routing graph
        this.verts = new ArrayList<>();
        this.edges = new ArrayList<>();

        // create vertices at the crossings of horizontal and vertical grid-lines
        hlines.forEach(h -> vlines.forEach(v -> {
            final Vert p = new Vert(this.verts.size(), v.x1, h.y1);
            h.verts.add(p);
            v.verts.add(p);
            this.verts.add(p);

            // assign vertices to the nodes immediately under them
            int i = this.backToFront.size();
            while (0 < i--) {
                NodeWrapper node = this.backToFront.get(i);
                Rectangle r = node.rect;
                double dx = Math.abs(p.x - r.cx()),
                        dy = Math.abs(p.y - r.cy());
                if (dx < r.width() / 2 && dy < r.height() / 2) {
                    p.node = node;
                    break;
                }
            }
        }));

        lines.forEach(l -> {
            // create vertices at the intersections of nodes and lines
            this.nodes.forEach(v -> {
                v.rect.lineIntersections(l.x1, l.y1, l.x2, l.y2).forEach(intersect -> {
                    Vert p = new Vert(this.verts.size(), intersect.x, intersect.y, v);
                    this.verts.add(p);
                    l.verts.add(p);
                    v.ports.add(p);
                });
            });

            // split lines into edges joining vertices
            boolean isHoriz = 0.1 > Math.abs(l.y1 - l.y2);
            //noinspection NumericCastThatLosesPrecision
            Comparator<Vert> delta = (a, b) -> isHoriz ? (int)Math.signum(b.x - a.x) : (int)Math.signum(b.y - a.y);
            l.verts.sort(delta);
            for (int i = 1; i < l.verts.size(); i++) {
                Vert u = l.verts.get(i - 1), v = l.verts.get(i);
                if (null != u.node && u.node.equals(v.node) && u.node.leaf) {
                    continue;
                }
                this.edges.add(new LinkWrapper(u.id, v.id, Math.abs(isHoriz ? (v.x - u.x) : (v.y - u.y))));
            }
        });

    }

    private double avg(final List<Double> a) {
        final Double result = a.stream().reduce(new Double(0.0), (x, y) -> x + y);
        return result / a.size();
    }

    /** in the given axis, find sets of leaves overlapping in that axis center of each GridLine is average of all nodes in column */
    private List<GridLine> getGridLines(final String axis) {
        final List<GridLine> columns = new ArrayList<>();
        final List<NodeWrapper> ls = new ArrayList<>(this.leaves);
        if ("x".equals(axis)) {
            while (0 < ls.size()) {
                // find a column of all leaves overlapping in axis with the first leaf
                final List<NodeWrapper> overlapping = ls.stream().filter(v -> (0 != v.rect.overlapX(ls.get(0).rect)))
                                                        .collect(Collectors.toList());
                final GridLine col = new GridLine(overlapping,
                                                  this.avg(overlapping.stream().map(v -> v.rect.cx()).collect(Collectors.toList())));
                columns.add(col);
                col.nodes.forEach(v -> ls.remove(v));
            }
        } else if ("y".equals(axis)) {
            while (0 < ls.size()) {
                // find a column of all leaves overlapping in axis with the first leaf
                final List<NodeWrapper> overlapping = ls.stream().filter(v -> (0 != v.rect.overlapY(ls.get(0).rect)))
                                                        .collect(Collectors.toList());
                final GridLine col = new GridLine(overlapping,
                                                  this.avg(overlapping.stream().map(v -> v.rect.cy()).collect(Collectors.toList())));
                columns.add(col);
                col.nodes.forEach(v -> ls.remove(v));
            }
        }
        //noinspection NumericCastThatLosesPrecision
        columns.sort((a, b) -> (int)Math.signum(a.pos - b.pos));
        return columns;
    }

    /** get the depth of the given node in the group hierarchy */
    private int getDepth(final NodeWrapper v) {
        int depth = 0;
        NodeWrapper u = v;
        while (u.parent != this.root) {
            depth++;
            u = u.parent;
        }
        return depth;
    }

    /** medial axes between node centres and also boundary lines for the grid */
    private List<Double> midPoints(final List<Double> a) {
        final double gap = a.get(1) - a.get(0);
        final List<Double> mids = new ArrayList<>();
        mids.add(a.get(0) - gap / 2);
        for (int i = 1; i < a.size(); i++) {
            mids.add((a.get(i) + a.get(i - 1)) / 2);
        }
        mids.add(a.get(a.size() - 1) + gap / 2);
        return mids;
    }

    /** find path from v to root including both v and root */
    private List<NodeWrapper> findLineage(final NodeWrapper v) {
        NodeWrapper u = v;
        final List<NodeWrapper> lineage = new ArrayList<>();
        lineage.add(u);
        do {
            u = u.parent;
            lineage.add(u);
        } while (u != this.root);
        Collections.reverse(lineage);
        return lineage;
    }

    /** find path connecting a and b through their lowest common ancestor */
    private AncestorPath findAncestorPathBetween(final NodeWrapper a, final NodeWrapper b) {
        final List<NodeWrapper> aa = this.findLineage(a), ba = this.findLineage(b);
        int i = 0;
        while (aa.get(i).equals(ba.get(i))) {
            i++;
        }
        // i-1 to include common ancestor only once (as first element)
        return new AncestorPath(aa.get(i - 1), Stream.concat(aa.subList(i, aa.size()).stream(), ba.subList(i, ba.size()).stream())
                                                     .collect(Collectors.toList()));
    }

    /**
     * when finding a path between two nodes a and b, siblings of a and b on the paths from a and b to their least common ancestor are
     * obstacles
     */
    private List<NodeWrapper> siblingObstacles(final NodeWrapper a, final NodeWrapper b) {
        final AncestorPath path = this.findAncestorPathBetween(a, b);
        final Map<Integer, Boolean> lineageLookup = new HashMap<>();
        path.lineages.forEach(v -> lineageLookup.put(v.id, true));
        Stream<Integer> obstaclesStream = path.commonAncestor.children.stream().filter(v -> !lineageLookup.containsKey(v));

        final List<NodeWrapper> filteredLineage = path.lineages.stream().filter(v -> v.parent != path.commonAncestor)
                                                      .collect(Collectors.toList());
        for (int i = 0, n = filteredLineage.size(); i < n; i++) {
            final NodeWrapper v = filteredLineage.get(i);
            obstaclesStream = Stream.concat(obstaclesStream, v.parent.children.stream().filter(c -> c != v.id));
        }

        return obstaclesStream.map(v -> this.nodes.get(v)).collect(Collectors.toList());
    }

    /** for the given routes, extract all the segments orthogonal to the axis x and return all them grouped by x position */
    private static List<SegmentSet> getSegmentSets(final List<List<Segment>> routes, final String x) {
        // vsegments is a list of vertical segments sorted by x position
        final List<Segment> vsegments = new ArrayList<>();
        for (int ei = 0; ei < routes.size(); ei++) {
            final List<Segment> route = routes.get(ei);
            for (int si = 0; si < route.size(); si++) {
                Segment s = route.get(si);
                s.edgeid = ei;
                s.i = si;
                final double sdx = s.get(1).get(x) - s.get(0).get(x);
                if (0.1 > Math.abs(sdx)) {
                    vsegments.add(s);
                }
            }
        }
        //noinspection NumericCastThatLosesPrecision
        vsegments.sort((a, b) -> (int)Math.signum(a.get(0).get(x) - b.get(0).get(x)));

        // vsegmentsets is a set of sets of segments grouped by x position
        final List<SegmentSet> vsegmentsets = new ArrayList<>();
        SegmentSet segmentset = null;
        for (int i = 0; i < vsegments.size(); i++) {
            final Segment s = vsegments.get(i);
            if (null == segmentset || 0.1 < Math.abs(s.get(0).get(x) - segmentset.pos)) {
                segmentset = new SegmentSet(s.get(0).get(x), new ArrayList<>());
                vsegmentsets.add(segmentset);
            }
            segmentset.segments.add(s);
        }
        return vsegmentsets;
    }

    /**
     * for all segments in this bundle create a vpsc problem such that each segment's x position is a variable and separation constraints
     * are given by the partial order over the edges to which the segments belong for each pair s1,s2 of segments in the open set: e1 = edge
     * of s1, e2 = edge of s2 if leftOf(e1,e2) create constraint s1.x + gap <= s2.x else if leftOf(e2,e1) create cons. s2.x + gap <= s1.x
     */
    private static void nudgeSegs(final String x, final String y, List<List<Segment>> routes, final List<Segment> segments,
                                  ToBooleanBiFunction<Integer, Integer> leftOf, double gap)
    {
        final int n = segments.size();
        if (1 >= n) {
            return;
        }
        final List<Variable> vs = segments.stream().map(s -> new Variable(s.get(0).get(x)))
                                          .collect(Collectors.toList());
        List<Constraint> cs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    continue;
                }
                final Segment s1 = segments.get(i),
                        s2 = segments.get(j);
                final int e1 = s1.edgeid,
                        e2 = s2.edgeid;
                int lind = -1,
                        rind = -1;
                // in page coordinates (not cartesian) the notion of 'leftof' is flipped in the horizontal axis from the vertical axis
                // that is, when nudging vertical segments, if they increase in the y(conj) direction the segment belonging to the
                // 'left' edge actually needs to be nudged to the right
                // when nudging horizontal segments, if the segments increase in the x direction
                // then the 'left' segment needs to go higher, i.e. to have y pos less than that of the right
                if ("x".equals(x)) {
                    if (leftOf.applyAsBoolean(e1, e2)) {
                        if (s1.get(0).get(y) < s1.get(1).get(y)) {
                            lind = j;
                            rind = i;
                        } else {
                            lind = i;
                            rind = j;
                        }
                    }
                } else {
                    if (leftOf.applyAsBoolean(e1, e2)) {
                        if (s1.get(0).get(y) < s1.get(1).get(y)) {
                            lind = i;
                            rind = j;
                        } else {
                            lind = j;
                            rind = i;
                        }
                    }
                }
                if (0 <= lind) {
                    cs.add(new Constraint(vs.get(lind), vs.get(rind), gap));
                }
            }
        }
        final Solver solver = new Solver(vs, cs);
        solver.solve();
        for (int i = 0; i < vs.size(); i++) {
            final Variable v = vs.get(i);
            final Segment s = segments.get(i);
            final double pos = v.position();
            s.get(0).set(x, pos);
            s.get(1).set(x, pos);
            final List<Segment> route = routes.get(s.edgeid);
            if (0 < s.i) {
                route.get(s.i - 1).get(1).set(x, pos);
            }
            if (s.i < route.size() - 1) {
                route.get(s.i + 1).get(0).set(x, pos);
            }
        }
    }

    public static void nudgeSegments(final List<List<Segment>> routes, final String x, final String y,
                                     final ToBooleanBiFunction<Integer, Integer> leftOf, final double gap)
    {
        final List<SegmentSet> vsegmentsets = GridRouter.getSegmentSets(routes, x);
        // scan the grouped (by x) segment sets to find co-linear bundles
        for (int i = 0; i < vsegmentsets.size(); i++) {
            final SegmentSet ss = vsegmentsets.get(i);
            final List<GridEvent> events = new ArrayList<>();
            for (int j = 0; j < ss.segments.size(); j++) {
                final Segment s = ss.segments.get(j);
                events.add(new GridEvent(0, s, Math.min(s.get(0).get(y), s.get(1).get(y))));
                events.add(new GridEvent(1, s, Math.max(s.get(0).get(y), s.get(1).get(y))));
            }
            //noinspection NumericCastThatLosesPrecision
            events.sort((a, b) -> (int)Math.signum(a.pos - b.pos) + a.type - b.type);
            List<Segment> open = new ArrayList<>();
            int openCount = 0;
            for (int k = 0; k < events.size(); k++) {
                final GridEvent e = events.get(k);
                if (0 == e.type) {
                    open.add(e.s);
                    openCount++;
                } else {
                    openCount--;
                }
                if (0 == openCount) {
                    GridRouter.nudgeSegs(x, y, routes, open, leftOf, gap);
                    open = new ArrayList<>();
                }
            }
        }
    }

    /**
     * obtain routes for the specified edges, nicely nudged apart warning: edge paths may be reversed such that common paths are ordered
     * consistently within bundles!
     *
     * @param rEdges   list of edges
     * @param nudgeGap how much to space parallel edge segments
     * @param source   function to retrieve the index of the source node for a given edge
     * @param target   function to retrieve the index of the target node for a given edge
     * @return an array giving, for each edge, an array of segments, each segment a pair of points in an array
     */
    public List<List<Segment>> routeEdges(final List<LinkWrapper> rEdges, final double nudgeGap, final ToIntFunction<LinkWrapper> source,
                                          final ToIntFunction<LinkWrapper> target)
    {
        final List<GridPath<Vert>> routePaths = rEdges.stream().map(e -> this.route(source.applyAsInt(e), target.applyAsInt(e)))
                                                      .collect(Collectors.toList());
        final ToBooleanBiFunction<Integer, Integer> order = GridRouter.orderEdges(routePaths);
        final List<List<Segment>> routes = routePaths.stream().map(e -> GridRouter.makeSegments(e))
                                                     .collect(Collectors.toList());
        GridRouter.nudgeSegments(routes, "x", "y", order, nudgeGap);
        GridRouter.nudgeSegments(routes, "y", "x", order, nudgeGap);
        GridRouter.unreverseEdges(routes, routePaths);
        return routes;
    }

    /** path may have been reversed by the subsequence processing in orderEdges so now we need to restore the original order */
    public static void unreverseEdges(final List<List<Segment>> routes, final List<GridPath<Vert>> routePaths) {
        for (int i = 0, n = routes.size(); i < n; i++) {
            final List<Segment> segments = routes.get(i);
            final GridPath<Vert> path = routePaths.get(i);
            if (path.reversed) {
                Collections.reverse(segments); // reverse order of segments
                segments.forEach(segment -> {
                    final Point pTemp = segment.p0;
                    segment.p0 = segment.p1;
                    segment.p1 = pTemp;
                });  // reverse each segment
            }
        }
    }

    private static double angleBetween2Lines(final Point[] line1, final Point[] line2) {
        final double angle1 = Math.atan2(line1[0].y - line1[1].y,
                                         line1[0].x - line1[1].x);
        final double angle2 = Math.atan2(line2[0].y - line2[1].y,
                                         line2[0].x - line2[1].x);
        double diff = angle1 - angle2;
        if (Math.PI < diff || -Math.PI > diff) {
            diff = angle2 - angle1;
        }
        return diff;
    }

    /** does the path a-b-c describe a left turn? */
    private static boolean isLeft(final Vert a, final Vert b, final Vert c) {
        return 0 >= ((b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x));
    }

    /** for the given list of ordered pairs, returns a function that (efficiently) looks-up a specific pair to see if it exists in the list */
    private static ToBooleanBiFunction<Integer, Integer> getOrder(final List<Pair> pairs) {
        final Map<Integer, Map<Integer, Boolean>> outgoing = new HashMap<>();
        for (int i = 0, n = pairs.size(); i < n; i++) {
            final Pair p = pairs.get(i);
            if (!outgoing.containsKey(p.l)) {
                outgoing.put(p.l, new HashMap<>());
            }
            outgoing.get(p.l).put(p.r, true);
        }
        return (l, r) -> outgoing.containsKey(l) && null != outgoing.get(l).get(r);
    }

    /** returns an ordering (a lookup function) that determines the correct order to nudge the edge paths apart to minimize crossings */
    public static ToBooleanBiFunction<Integer, Integer> orderEdges(final List<GridPath<Vert>> routePaths) {
        final List<Pair> edgeOrder = new ArrayList<>();
        final int n = routePaths.size();
        for (int i = 0; i < n - 1; i++) {
            for (int j = i + 1; j < n; j++) {
                final GridPath<Vert> e = routePaths.get(i),
                        f = routePaths.get(j);
                LongestCommonSubsequence<Vert> lcs = new LongestCommonSubsequence<>(e, f);
                final Vert u, vi, vj;
                if (0 == lcs.length) {
                    continue; // no common subpath
                }
                if (lcs.reversed) {
                    // if we found a common subpath but one of the edges runs the wrong way,
                    // then reverse f.
                    Collections.reverse(f);
                    f.reversed = true;
                    lcs = new LongestCommonSubsequence<>(e, f);
                }
                if ((0 >= lcs.si || 0 >= lcs.ti) &&
                    (lcs.si + lcs.length >= e.size() || lcs.ti + lcs.length >= f.size())) {
                    // the paths do not diverge, so make an arbitrary ordering decision
                    edgeOrder.add(new Pair(i, j));
                    continue;
                }
                if (lcs.si + lcs.length >= e.size() || lcs.ti + lcs.length >= f.size()) {
                    // if the common subsequence of the
                    // two edges being considered goes all the way to the
                    // end of one (or both) of the lines then we have to
                    // base our ordering decision on the other end of the
                    // common subsequence
                    u = e.get(lcs.si + 1);
                    vj = e.get(lcs.si - 1);
                    vi = f.get(lcs.ti - 1);
                } else {
                    u = e.get(lcs.si + lcs.length - 2);
                    vi = e.get(lcs.si + lcs.length);
                    vj = f.get(lcs.ti + lcs.length);
                }
                if (GridRouter.isLeft(u, vi, vj)) {
                    edgeOrder.add(new Pair(j, i));
                } else {
                    edgeOrder.add(new Pair(i, j));
                }
            }
        }
        return GridRouter.getOrder(edgeOrder);
    }

    private static Point copyPoint(final Vert v) {
        return new Point(v.x, v.y);
    }

    /**
     * for an orthogonal path described by a sequence of points, create a list of segments if consecutive segments would make a straight
     * line they are merged into a single segment segments are over cloned points, not the original vertices
     */
    public static List<Segment> makeSegments(final GridPath<Vert> path) {
        final TriFunction<Point, Point, Point, Boolean> isStraight = (a, b, c) -> 0.001 >
                                                                                  Math.abs((b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x
                                                                                                                                      - a.x));
        final List<Segment> segments = new ArrayList<>();
        Point a = copyPoint(path.get(0));
        for (int i = 1; i < path.size(); i++) {
            final Point b = copyPoint(path.get(i));
            final Point c = i < path.size() - 1 ? copyPoint(path.get(i + 1)) : null;
            if (null == c || !isStraight.apply(a, b, c)) {
                segments.add(new Segment(a, b));
                a = b;
            }
        }
        return segments;
    }

    /** find a route between node s and node t returns an array of indices to verts */
    public GridPath<Vert> route(final int s, final int t) {
        final NodeWrapper source = this.nodes.get(s), target = this.nodes.get(t);
        this.obstacles = this.siblingObstacles(source, target);

        final Map<Integer, NodeWrapper> obstacleLookup = new HashMap<>();
        this.obstacles.forEach(o -> obstacleLookup.put(o.id, o));
        this.passableEdges = this.edges.stream().filter(e -> {
            final Vert u = this.verts.get(e.source),
                    v = this.verts.get(e.target);
            return !(null != u.node && obstacleLookup.containsKey(u.node.id)
                     || null != v.node && obstacleLookup.containsKey(v.node.id));
        }).collect(Collectors.toList());

        // add dummy segments linking ports inside source and target
        for (int i = 1, n = source.ports.size(); i < n; i++) {
            final int u = source.ports.get(0).id;
            final int v = source.ports.get(i).id;
            this.passableEdges.add(new LinkWrapper(u, v, 0));
        }
        for (int i = 1, n = target.ports.size(); i < n; i++) {
            final int u = target.ports.get(0).id;
            final int v = target.ports.get(i).id;
            this.passableEdges.add(new LinkWrapper(u, v, 0));
        }

        final ToIntFunction<LinkWrapper> getSource = e -> e.source,
                getTarget = e -> e.target;
        final ToDoubleFunction<LinkWrapper> getLength = e -> e.length;

        final Calculator<LinkWrapper> shortestPathCalculator = new Calculator<>(verts.size(), passableEdges, getSource, getTarget,
                                                                                getLength);
        final TriFunction<Integer, Integer, Integer, Double> bendPenalty = (u, v, w) -> {
            final Vert a = this.verts.get(u), b = this.verts.get(v), c = this.verts.get(w);
            final double dx = Math.abs(c.x - a.x), dy = Math.abs(c.y - a.y);
            // don't count bends from internal node edges
            if (source.equals(a.node) && a.node.equals(b.node) || target.equals(b.node) && b.node.equals(c.node)) {
                return 0.0;
            }
            return 1 < dx && 1 < dy ? 1000.0 : 0.0;
        };

        // get shortest path
        final List<Integer> shortestPath = shortestPathCalculator.PathFromNodeToNodeWithPrevCost(
                source.ports.get(0).id, target.ports.get(0).id, bendPenalty);

        // shortest path is reversed and does not include the target port
        Collections.reverse(shortestPath);
        final List<Vert> pathPoints = shortestPath.stream().map(vi -> this.verts.get(vi)).collect(Collectors.toList());
        pathPoints.add(this.nodes.get(target.id).ports.get(0));

        // filter out any extra end points that are inside the source or target (i.e. the dummy segments above)

        return pathPoints.stream().filter((v) -> {
            int i = pathPoints.indexOf(v);
            return !(i < pathPoints.size() - 1 && source.equals(pathPoints.get(i + 1).node) && source.equals(v.node)
                     || 0 < i && target.equals(v.node) && target.equals(pathPoints.get(i - 1).node));
        }).collect(Collectors.toCollection(GridPath::new));
    }
}
