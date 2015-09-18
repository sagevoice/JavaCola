package edu.monash.infotech.marvl.cola;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import edu.monash.infotech.marvl.cola.geom.Geom;
import edu.monash.infotech.marvl.cola.geom.Point;
import edu.monash.infotech.marvl.cola.powergraph.Configuration;
import edu.monash.infotech.marvl.cola.powergraph.LinkTypeAccessor;
import edu.monash.infotech.marvl.cola.powergraph.Module;
import edu.monash.infotech.marvl.cola.powergraph.PowerEdge;
import edu.monash.infotech.marvl.cola.shortestpaths.Calculator;
import edu.monash.infotech.marvl.cola.vpsc.*;

import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;

@Slf4j
public class Tests {

    private double nodeDistance(final GraphNode u, final GraphNode v) {
        final double dx = u.x - v.x, dy = u.y - v.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private boolean approxEquals(final double actual, final double expected, final double threshold) {
        return Math.abs(actual - expected) <= threshold;
    }

    private List<Link> mapJsonArrayToLinkList(final List<Map<String, Object>> jsonArray) {
        final List<Link> result = jsonArray.stream().map(jsonObj -> {
            return new Link(jsonObj.get("source"), jsonObj.get("target"));
        }).collect(Collectors.toList());
        return result;
    }

    private List<GraphNode> mapJsonArrayToNodeList(final List<Map<String, Object>> jsonArray) {
        final List<GraphNode> result = jsonArray.stream().map(jsonObj -> {
            return new GraphNode();
        }).collect(Collectors.toList());
        return result;
    }

    @Test(description = "small power-graph")
    public void smallPowerGraphTest() {
        try (final InputStream stream = getClass().getResourceAsStream("/n7e23.json")) {
            final ObjectMapper mapper = new ObjectMapper();
            final MapType type = mapper.getTypeFactory().constructMapType(
                    Map.class, String.class, Object.class);
            final Map<String, Object> graph = mapper.readValue(stream, type);

            final int n = ((List)graph.get("nodes")).size();
            Assert.assertEquals(n, 7);
            List<Link> links = mapJsonArrayToLinkList((List<Map<String, Object>>)graph.get("links"));
            LinkTypeAccessor<Link> linkAccessor = new IntegerLinkAccessor();
            Configuration<Link> c = new Configuration<>(n, links, linkAccessor);
            Assert.assertEquals(c.modules.size(), 7);
            List<PowerEdge> es = c.allEdges();
            Assert.assertEquals(c.R, es.size(), "c.R=" + c.R + ", actual edges in c=" + es.size());
            Module m = c.merge(c.modules.get(0), c.modules.get(4));
            Assert.assertTrue(m.children.contains(0));
            Assert.assertTrue(m.children.contains(4));
            Assert.assertTrue(m.outgoing.contains(1));
            Assert.assertTrue(m.outgoing.contains(3));
            Assert.assertTrue(m.outgoing.contains(5));
            Assert.assertTrue(m.outgoing.contains(6));
            Assert.assertTrue(m.incoming.contains(2));
            Assert.assertTrue(m.incoming.contains(5));
            es = c.allEdges();
            Assert.assertEquals(c.R, es.size(), "c.R=" + c.R + ", actual edges in c=" + es.size());
            m = c.merge(c.modules.get(2), c.modules.get(3));
            es = c.allEdges();
            Assert.assertEquals(c.R, es.size(), "c.R=" + c.R + ", actual edges in c=" + es.size());

            c = new Configuration<>(n, links, linkAccessor);
            int lastR = c.R;
            while (c.greedyMerge()) {
                Assert.assertTrue(c.R < lastR);
                lastR = c.R;
            }
            List<PowerEdge> finalEdges = new ArrayList<>();
            List<PowerEdge> powerEdges = c.allEdges();
            Assert.assertEquals(powerEdges.size(), 7);
            List<Group> groups = c.getGroupHierarchy(finalEdges);
            Assert.assertEquals(groups.size(), 4);
        } catch (IOException e) {
            log.error("IOException in smallPowerGraphTest", e);
            throw new RuntimeException(e);
        }
        Assert.assertTrue(true);
    }

    @Test(description = "all-pairs shortest paths")
    public void allPairsShortestPathsTest() {
        final LayoutAdaptor d3cola = CoLa.adaptor();

        try (final InputStream stream = getClass().getResourceAsStream("/triangle.json")) {
            final ObjectMapper mapper = new ObjectMapper();
            final MapType type = mapper.getTypeFactory().constructMapType(
                    Map.class, String.class, Object.class);
            final Map<String, Object> graph = mapper.readValue(stream, type);
            List<Link> links = mapJsonArrayToLinkList((List<Map<String, Object>>)graph.get("links"));
            List<GraphNode> nodes = mapJsonArrayToNodeList((List<Map<String, Object>>)graph.get("nodes"));

            d3cola
                    .nodes(nodes)
                    .links(links)
                    .linkDistance(Double.valueOf(1));
            final int n = d3cola.nodes().size();
            Assert.assertEquals(n, 4);

            final ToIntFunction<Link> getSourceIndex = (e) -> {
                return ((Integer)e.source).intValue();
            };
            final ToIntFunction<Link> getTargetIndex = (e) -> {
                return ((Integer)e.target).intValue();
            };
            final ToDoubleFunction<Link> getLength = (e) -> {
                return 1;
            };
            final double[][] D = (new Calculator<>(n, d3cola.links(), getSourceIndex, getTargetIndex, getLength)).DistanceMatrix();
            Assert.assertEquals(D, new double[][] {
                    {0, 1, 1, 2},
                    {1, 0, 1, 2},
                    {1, 1, 0, 1},
                    {2, 2, 1, 0},
            });
            final double[] x = new double[] {0, 0, 1, 1};
            final double[] y = new double[] {1, 0, 0, 1};
            final Descent descent = new Descent(new double[][] {x, y}, D);
            final double s0 = descent.reduceStress();
            final double s1 = descent.reduceStress();
            Assert.assertTrue(s1 < s0);
            final double s2 = descent.reduceStress();
            Assert.assertTrue(s2 < s1);
            d3cola.start(0, 0, 10);
            final List<Double> lengths = d3cola.links().stream().map(l -> {
                GraphNode u = (GraphNode)l.source, v = (GraphNode)l.target;
                double dx = u.x - v.x, dy = u.y - v.y;
                return Math.sqrt(dx * dx + dy * dy);
            }).collect(Collectors.toList());
            final Function<List<Double>, Double> avg = (a) -> {
                return a.stream().reduce(new Double(0), (u, v) -> { return u + v; }) / a.size();
            };
            final double mean = avg.apply(lengths);
            final double variance = avg
                    .apply(lengths.stream().map((l) -> { double d = mean - l; return d * d; }).collect(Collectors.toList()));
            Assert.assertTrue(variance < 0.1);
        } catch (IOException e) {
            log.error("IOException in allPairsShortestPathsTest", e);
            throw new RuntimeException(e);
        }
        Assert.assertTrue(true);
    }

    @Test(description = "edge lengths")
    public void edgeLengthsTest() {
        final LayoutAdaptor d3cola = CoLa.adaptor();

        try (final InputStream stream = getClass().getResourceAsStream("/triangle.json")) {
            final ObjectMapper mapper = new ObjectMapper();
            final MapType type = mapper.getTypeFactory().constructMapType(
                    Map.class, String.class, Object.class);
            final Map<String, Object> graph = mapper.readValue(stream, type);
            List<Link> links = mapJsonArrayToLinkList((List<Map<String, Object>>)graph.get("links"));
            List<GraphNode> nodes = mapJsonArrayToNodeList((List<Map<String, Object>>)graph.get("nodes"));

            final ToDoubleFunction<Link> length = (l) -> {
                return ("2-3").equals(Layout.linkId(l)) ? 2 : 1;
            };
            d3cola
                    .linkDistance(length)
                    .nodes(nodes)
                    .links(links);
            d3cola.start(100);
            List<Double> errors = d3cola.links().stream().map((e) -> {
                double l = nodeDistance((GraphNode)e.source, (GraphNode)e.target);
                return Math.abs(l - length.applyAsDouble(e));
            }).collect(Collectors.toList());
            final Function<List<Double>, Double> listMax = (a) -> {
                return a.stream().reduce(new Double(0), (u, v) -> { return Math.max(u, v); });
            };
            double max = listMax.apply(errors);
            Assert.assertTrue(0.1 > max, "max = " + max);
        } catch (IOException e) {
            log.error("IOException in edgeLengthsTest", e);
            throw new RuntimeException(e);
        }
        Assert.assertTrue(true);
    }

    @Test(description = "group")
    public void groupTest() {
        LayoutAdaptor d3cola = CoLa.adaptor();

        final ToDoubleFunction<Link> length = (l) -> {
            return ("2-3").equals(d3cola.linkId(l)) ? 2 : 1;
        };
        final List<GraphNode> nodes = new ArrayList<>();
        final GraphNode u = new GraphNode(-5, 0, 10, 10);
        final GraphNode v = new GraphNode(5, 0, 10, 10);
        final Group g = new Group(10, Arrays.asList(new GraphNode(0)));

        d3cola
                .linkDistance(length)
                .avoidOverlaps(true)
                .nodes(Arrays.asList(u, v))
                .groups(Arrays.asList(g));
        d3cola.start(10, 10, 10);

        Assert.assertEquals(g.bounds.width(), 30, 0.1);
        Assert.assertEquals(g.bounds.height(), 30, 0.1);

        Assert.assertEquals(Math.abs(u.y - v.y), 20, 0.1);
    }

    @Test(description = "equality constraints")
    public void equalityConstraintsTest() {
        LayoutAdaptor d3cola = CoLa.adaptor();

        try (final InputStream stream = getClass().getResourceAsStream("/triangle.json")) {
            final ObjectMapper mapper = new ObjectMapper();
            final MapType type = mapper.getTypeFactory().constructMapType(
                    Map.class, String.class, Object.class);
            final Map<String, Object> graph = mapper.readValue(stream, type);
            List<Link> links = mapJsonArrayToLinkList((List<Map<String, Object>>)graph.get("links"));
            List<GraphNode> nodes = mapJsonArrayToNodeList((List<Map<String, Object>>)graph.get("nodes"));

            d3cola
                    .nodes(nodes)
                    .links(links)
                    .constraints(Arrays.asList(new Constraint("separation", "x", 0, 1, 0, true),
                                               new Constraint("separation", "y", 0, 2, 0, true)));
            d3cola.start(20, 20, 20);
            Assert.assertTrue(0.001 > Math.abs(nodes.get(0).x - nodes.get(1).x));
            Assert.assertTrue(0.001 > Math.abs(nodes.get(0).y - nodes.get(2).y));
        } catch (IOException e) {
            log.error("IOException in equalityConstraintsTest", e);
            throw new RuntimeException(e);
        }
        Assert.assertTrue(true);
    }

    private int nextInt(final PseudoRandom rand, final int r) {
        return (int)Math.round(rand.getNext() * r);
    }

    @Test(description = "convex hulls")
    public void convexHullsTest() {
        final PseudoRandom rand = new PseudoRandom();
        final int width = 100, height = 100;

        for (int k = 0; 10 > k; ++k) {
            List<Point> P = new ArrayList<>(5);
            for (int i = 0; 5 > i; ++i) {
                final Point p = new Point(nextInt(rand, width), nextInt(rand, height));
                P.add(p);
            }
            List<Point> h = Geom.ConvexHull(P);

            for (int i = 2; i < h.size(); ++i) {
                final Point p = h.get(i - 2), q = h.get(i - 1), r = h.get(i);
                Assert.assertTrue(0 <= Geom.isLeft(p, q, r), "clockwise hull " + i);
                for (int j = 0; j < P.size(); ++j) {
                    Assert.assertTrue(0 <= Geom.isLeft(p, q, P.get(j)), "" + j);
                }
            }
            Assert.assertNotEquals(h.get(0), h.get(h.size() - 1), "first and last point of hull are different " + k);
        }
    }

    @Test(description = "radial sort")
    public void radialSortTest() {
        final int n = 100;
        final int width = 400, height = 400;
        List<Point> P = new ArrayList<>(n);
        double x = 0, y = 0;
        PseudoRandom rand = new PseudoRandom(5);
        for (int i = 0; i < n; ++i) {
            final Point p = new Point(nextInt(rand, width), nextInt(rand, height));
            P.add(p);
            x += p.x; y += p.y;
        }
        final Point q = new Point(x / n, y / n);
        //console.log(q);
        ValueHolder<Point> valueHolder = new ValueHolder<>(null);
        Geom.clockwiseRadialSweep(q, P, (p, value) -> {
            final ValueHolder<Point> p0 = value;
            if (null != p0.get()) {
                double il = Geom.isLeft(q, p0.get(), p);
                Assert.assertTrue(0 <= il);
            }
            p0.set(p);
        }, valueHolder);
    }

    private double length(final Point p, final Point q) {
        final double dx = p.x - q.x, dy = p.y - q.y;
        return dx * dx + dy * dy;
    }

    private List<Point> makePoly(final PseudoRandom rand) {
        return makePoly(rand, 10, 10);
    }

    private List<Point> makePoly(final PseudoRandom rand, final int width, final int height) {
        final int n = nextInt(rand, 7) + 3;
        List<Point> P = new ArrayList<>();
        loop:
        for (int i = 0; i < n; ++i) {
            Point p = new Point(nextInt(rand, width), nextInt(rand, height));
            int ctr = 0;
            while (0 < i && 1 > length(P.get(i - 1), p) // min segment length is 1
                   || 1 < i && ( // new point must keep poly convex
                    0 >= Geom.isLeft(P.get(i - 2), P.get(i - 1), p)
                    || 0 >= Geom.isLeft(P.get(i - 1), p, P.get(0))
                    || 0 >= Geom.isLeft(p, P.get(0), P.get(1)))) {
                if (10 < ctr++) {
                    break loop; // give up after ten tries (maybe not enough space left for another convex point)
                }
                p = new Point(nextInt(rand, width), nextInt(rand, height));
            }
            P.add(p);
        }
        if (2 < P.size()) { // must be at least triangular
            P.add(new Point(P.get(0).x, P.get(0).y));
            return P;
        }
        return makePoly(rand, width, height);
    }

    private List<List<Point>> makeNonoverlappingPolys(final PseudoRandom rand, final int n) {
        List<List<Point>> P = new ArrayList<>();
        Predicate<List<Point>> overlaps = (p) -> {
            for (int i = 0; i < P.size(); i++) {
                final List<Point> q = P.get(i);
                if (Geom.polysOverlap(p, q)) { return true; }
            }
            return false;
        };
        for (int i = 0; i < n; i++) {
            List<Point> p = makePoly(rand);
            while (overlaps.test(p)) {
                double dx = nextInt(rand, 10) - 5, dy = nextInt(rand, 10) - 5;
                p.forEach((pt) -> { pt.x += dx; pt.y += dy; });
            }
            P.add(p);
        }
        List<Point> minPoly = new ArrayList<>();
        minPoly.add(new Point(Double.MAX_VALUE, Double.MAX_VALUE));
        minPoly = P.stream().reduce(minPoly, (poly0, poly1) -> {
            final Point minPt1 = poly1.stream()
                                      .reduce(new Point(Double.valueOf(Double.MAX_VALUE), Double.valueOf(Double.MAX_VALUE)), (pt0, pt1) -> {
                                          pt0.x = Math.min(pt0.x, pt1.x);
                                          pt0.y = Math.min(pt0.y, pt1.y);
                                          return pt0;
                                      });
            final Point minPt0 = poly0.get(0);
            minPt0.x = Math.min(minPt0.x, minPt1.x);
            minPt0.y = Math.min(minPt0.y, minPt1.y);
            return poly0;
        });
        final double minX = minPoly.get(0).x, minY = minPoly.get(0).y;
        P.forEach((p) -> {
            p.forEach((pt) -> { pt.x -= minX; pt.y -= minY; });
        });
        return P;
    }

    private Point midPoint(final List<Point> p) {
        double mx = 0, my = 0;
        final int n = p.size() - 1;
        for (int i = 0; i < n; i++) {
            final Point q = p.get(i);
            mx += q.x;
            my += q.y;
        }
        return new Point(mx / n, my / n);
    }

    private int countRouteIntersections(final List<List<Segment>> routes) {
        final List<Point> ints = new ArrayList<>();
        for (int i = 0; i < routes.size() - 1; i++) {
            for (int j = i + 1; j < routes.size(); j++) {
                final List<Segment> r1 = routes.get(i), r2 = routes.get(j);
                r1.forEach((s1) -> {
                    r2.forEach((s2) -> {
                        final Point intersection = Rectangle
                                .lineIntersection(s1.p0.x, s1.p0.y, s1.p1.x, s1.p1.y, s2.p0.x, s2.p0.y, s2.p1.x, s2.p1.y);
                        if (null != intersection) {
                            ints.add(intersection);
                        }
                    });
                });
            }
        }
        return ints.size();
    }

    @Test(description = "metro crossing min")
    public void metroCrossingMinTest() {
        final List<Vert> verts = new ArrayList<>();
        final List<GridPath<Vert>> edges = new ArrayList<>();
        final ValueHolder<ToBooleanBiFunction<Integer, Integer>> order = new ValueHolder<>(null);
        final List<List<Segment>> routes = new ArrayList<>();
        final VoidConsumer makeInstance = () -> {
            for (int i = 0; i < verts.size(); i++) {
                final Vert v = verts.get(i);
                v.id = i;
            }
        };
        final VoidConsumer twoParallelSegments = () -> {
            verts.clear();
            verts.addAll(Arrays.asList(
                    new Vert(0, 10),
                    new Vert(10, 10)
            ));
            edges.clear();
            edges.addAll(Arrays.asList(
                    new GridPath<>(Arrays.asList(verts.get(0), verts.get(1))),
                    new GridPath<>(Arrays.asList(verts.get(0), verts.get(1)))
            ));
            makeInstance.accept();
        };
        final VoidConsumer threeByThreeSegments = () -> {
            verts.clear();
            verts.addAll(Arrays.asList(
                    new Vert(0, 10),
                    new Vert(10, 10),
                    new Vert(10, 20),
                    new Vert(10, 30),
                    new Vert(20, 20),
                    new Vert(10, 0),
                    new Vert(0, 20)
            ));
            edges.clear();
            edges.addAll(Arrays.asList(
                    new GridPath<>(Arrays.asList(verts.get(0), verts.get(1), verts.get(2), verts.get(3))),
                    new GridPath<>(Arrays.asList(verts.get(0), verts.get(1), verts.get(2), verts.get(4))),
                    new GridPath<>(Arrays.asList(verts.get(5), verts.get(1), verts.get(2), verts.get(6)))
            ));
            makeInstance.accept();
        };
        final VoidConsumer regression1 = () -> {
            verts.clear();
            verts.addAll(Arrays.asList(
                    new Vert(430.79999999999995, 202.5),
                    new Vert(464.4, 202.5),
                    new Vert(464.4, 261.6666666666667),
                    new Vert(464.4, 320.83333333333337),
                    new Vert(474, 320.83333333333337),
                    new Vert(486, 320.83333333333337),
                    new Vert(498.0000000000001, 202.5),
                    new Vert(474, 202.5)
            ));
            verts.forEach((v) -> {
                v.x -= 400;
                v.y -= 160;
                v.x /= 4;
                v.y /= 8;
            });
            edges.clear();
            edges.addAll(Arrays.asList(
                    new GridPath<>(Arrays.asList(verts.get(0), verts.get(1), verts.get(2), verts.get(3), verts.get(4), verts.get(5))),
                    new GridPath<>(Arrays.asList(verts.get(6), verts.get(7), verts.get(1), verts.get(0)))
            ));
            makeInstance.accept();
        };
        final VoidConsumer nudge = () -> {
            order.set(GridRouter.orderEdges(edges));
            routes.clear();
            final List<List<Segment>> newRoutes = edges.stream().map((e) -> { return GridRouter.makeSegments(e); })
                                                       .collect(Collectors.toList());
            routes.addAll(newRoutes);
            GridRouter.nudgeSegments(routes, "x", "y", order.get(), 2);
            GridRouter.nudgeSegments(routes, "y", "x", order.get(), 2);
            GridRouter.unreverseEdges(routes, edges);
        };

        // trivial case
        twoParallelSegments.accept();
        nudge.accept();
        // two segments, one reversed
        Collections.reverse(edges.get(1));
        nudge.accept();

        threeByThreeSegments.accept();
        LongestCommonSubsequence<String> lcsString = new LongestCommonSubsequence<>(Arrays.asList("ABAB".split("")),
                                                                                    Arrays.asList("DABA".split("")));
        Assert.assertEquals(lcsString.getSequence(), Arrays.asList("ABA".split("")));
        LongestCommonSubsequence<Vert> lcs = new LongestCommonSubsequence<>(edges.get(0), edges.get(1));
        Assert.assertEquals(lcs.length, 3);
        Assert.assertEquals(lcs.getSequence().stream().map((v) -> { return v.id; }).collect(Collectors.toList()),
                            Arrays.asList(Integer.valueOf(0), Integer.valueOf(1), Integer.valueOf(2)));
        final GridPath<Vert> e0reversed = new GridPath<>(edges.get(0));
        Collections.reverse(e0reversed);
        lcs = new LongestCommonSubsequence(e0reversed, edges.get(1));
        Assert.assertEquals(lcs.getSequence().stream().map((v) -> { return v.id; }).collect(Collectors.toList()),
                            Arrays.asList(Integer.valueOf(2), Integer.valueOf(1), Integer.valueOf(0)));
        Assert.assertTrue(lcs.reversed);

        nudge.accept();
        Assert.assertEquals(routes.get(0).size(), 2);
        Assert.assertEquals(routes.get(1).size(), 3);
        Assert.assertEquals(routes.get(2).size(), 2);

        Assert.assertEquals(countRouteIntersections(routes), 2);

        // flip it in y and try again
        threeByThreeSegments.accept();
        verts.forEach((v) -> { v.y = 30 - v.y; });
        nudge.accept();
        Assert.assertEquals(countRouteIntersections(routes), 2);

        // reverse the first edge path and see what happens
        threeByThreeSegments.accept();
        Collections.reverse(edges.get(0));
        nudge.accept();
        Assert.assertEquals(countRouteIntersections(routes), 2);

        // reverse the second edge path
        threeByThreeSegments.accept();
        Collections.reverse(edges.get(1));
        nudge.accept();
        Assert.assertEquals(countRouteIntersections(routes), 2);

        // reverse the first 2 edge paths
        threeByThreeSegments.accept();
        Collections.reverse(edges.get(0));
        Collections.reverse(edges.get(1));
        nudge.accept();
        Assert.assertEquals(countRouteIntersections(routes), 2);

        regression1.accept();
        nudge.accept();
        Assert.assertEquals(countRouteIntersections(routes), 0);
    }

    public class TetrisBounds {

        private double x;
        private double X;
        private double y;
        private double Y;
    }


    public class TetrisNode {

        public int           id;
        public String        name;
        public TetrisBounds  bounds;
        public List<Integer> children;
    }


    public class TetrisEdge {

        public int source;
        public int target;
    }


    public class TetrisGraph {

        public List<TetrisNode> nodes;
        public List<TetrisEdge> edges;
    }


    public class TetrisNodeAccessor implements NodeAccessor<TetrisNode> {

        @Override
        public List<Integer> getChildren(final TetrisNode v) {
            return v.children;
        }

        @Override
        public Rectangle getBounds(final TetrisNode v) {
            return null != v.bounds
                   ? new Rectangle(v.bounds.x, v.bounds.X, v.bounds.y, v.bounds.Y)
                   : null;
        }
    }

    // next steps:
    //  o label node and group centre and boundary vertices
    //  - non-traversable regions (obstacles) are determined by finding the highest common ancestors of the source and target nodes
    //  - to route each edge the weights of the edges are adjusted such that those inside obstacles
    //    have infinite weight while those inside the source and target node have zero weight
    //  - augment dijkstra with a cost for bends
    @Test(description = "grid router")
    public void gridRouterTest() {

        try (final InputStream stream = getClass().getResourceAsStream("/tetrisbugmultiedgeslayout.json")) {
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode graph = mapper.readTree(stream);
            final JsonNode jsonNodes = graph.get("nodes");
            final List<TetrisNode> nodes = new ArrayList<>(jsonNodes.size());
            for (final JsonNode jsonNode : jsonNodes) {
                final TetrisNode tetrisNode = new TetrisNode();
                tetrisNode.id = jsonNode.path("id").intValue();
                tetrisNode.name = jsonNode.path("name").textValue();
                tetrisNode.bounds = new TetrisBounds();
                tetrisNode.bounds.x = jsonNode.path("bounds").path("x").doubleValue();
                tetrisNode.bounds.X = jsonNode.path("bounds").path("X").doubleValue();
                tetrisNode.bounds.y = jsonNode.path("bounds").path("y").doubleValue();
                tetrisNode.bounds.Y = jsonNode.path("bounds").path("Y").doubleValue();
                tetrisNode.children = new ArrayList<>();
                final JsonNode children = jsonNode.get("children");
                if (null != children) {
                    for (final JsonNode child : children) {
                        tetrisNode.children.add(child.intValue());
                    }
                }

                nodes.add(tetrisNode);
            }

            final GridRouter<TetrisNode> gridrouter = new GridRouter<>(nodes, new TetrisNodeAccessor());
            final TriConsumer<Integer, Integer, Integer> check = (expected, source, target) -> {
                Assert.assertEquals(gridrouter.obstacles.size(), expected.intValue());
                final List<Integer> obstacleIds = gridrouter.obstacles.stream().map((v) -> { return Integer.valueOf(v.id); })
                                                            .collect(Collectors.toList());
                Assert.assertTrue(0 > obstacleIds.indexOf(source));
                Assert.assertTrue(0 > obstacleIds.indexOf(target));
            };

            int source = 1, target = 2;
            GridPath<Vert> shortestPath = gridrouter.route(source, target);
            check.accept(8, source, target);

            source = 0; target = 7;
            shortestPath = gridrouter.route(source, target);
            check.accept(6, source, target);

            source = 4; target = 5;
            shortestPath = gridrouter.route(source, target);
            check.accept(8, source, target);

            source = 11; target = 2;
            shortestPath = gridrouter.route(source, target);
            check.accept(13, source, target);

            // group to node
            source = 16; target = 5;
            shortestPath = gridrouter.route(source, target);
            check.accept(7, source, target);

            // bend minimal?
            source = 1; target = 2;
            shortestPath = gridrouter.route(source, target);
        } catch (IOException e) {
            log.error("IOException in gridRouterTest", e);
            throw new RuntimeException(e);
        }
        Assert.assertTrue(true);
    }

  /*
    @Test(description="shortest path with bends")
    public void shortestPathWithBendsTest() {
        //  0 - 1 - 2
        //      |   |
        //      3 - 4
        var nodes = [[0,0],[1,0],[2,0],[1,1],[2,1]];
        var edges = [[0,1,1],[1,2,2],[1,3,1],[3,4,1],[2,4,2]];
        function source(e) { return e[0]};
        function target(e) { return e[1]};
        function length(e) { return e[2]};
        Calculator sp = new Calculator(nodes.length, edges, source, target, length);
        var path = sp.PathFromNodeToNodeWithPrevCost(0, 4,
        function (u,v,w){
            var a = nodes[u], b = nodes[v], c = nodes[w];
            var dx = Math.abs(c[0] - a[0]), dy = Math.abs(c[1] - a[1]);
            return dx > 0.01 && dy > 0.01
                ? 1000
                : 0;
        });
        Assert.assertTrue(true);
    }

    @Test(description="tangent visibility graph")
    public void tangentVisibilityGraphTest() {
        for (var tt = 0; tt < 100; tt++) {
            PseudoRandom rand = new PseudoRandom(tt);
                nextInt = function (r) { return Math.round(rand.getNext() * r) },
                n = 10,
                P = makeNonoverlappingPolys(rand, n),
                port1 = midPoint(P[8]),
                port2 = midPoint(P[9]);
                        TangentVisibilityGraph g = new TangentVisibilityGraph(P);
                start = g.addPoint(port1, 8),
                end = g.addPoint(port2, 9);
            g.addEdgeIfVisible(port1, port2, 8, 9);
            var getSource = function (e) { return e.source.id }, getTarget = function(e) { return e.target.id}, getLength = function(e) { return e.length() }
            shortestPath = (new Calculator(g.V.length, g.E, getSource, getTarget, getLength)).PathFromNodeToNode(start.id, end.id);
            Assert.assertTrue(shortestPath.length > 0);
        }
        Assert.assertTrue(true);
    }

    @Test(description="tangents")
    public void tangentsTest() {
        PseudoRandom rand = new PseudoRandom();
        var rect = [{ x: 10, y: 10 }, { x: 20, y: 10 }, { x: 10, y: 20 }, { x: 20, y: 20 }];
        var pnt = [{ x: 0, y: 0 }];
        var t1 = Geom.tangents(pnt, rect);
        for (var j = 0; j < 100; j++) {
            var A = makePoly(rand), B = makePoly(rand);
            B.forEach((p) -> { p.x += 11 });
            //if (j !== 207) continue;
            var t = Geom.tangents(A, B);
            // ok(t.length === 4, t.length + " tangents found at j="+j);
        }
        Assert.assertTrue(true);
    }

    function intersects(l, P) {
        var ints = [];
        for (var i = 1; i < P.length; ++i) {
            var int = Rectangle.lineIntersection(
                l.x1, l.y1,
                l.x2, l.y2,
                P[i-1].x, P[i-1].y,
                P[i].x, P[i].y
                );
            if (int) ints.push(int);
        }
        return ints;
    }

    @Test(description="pseudo random number test")
    public void pseudoRandomNumberTest() {
        PseudoRandom rand = new PseudoRandom();
        for (var i = 0; i < 100; ++i) {
            var r = rand.getNext();
            Assert.assertTrue(r <= 1, "r=" + r);
            Assert.assertTrue(r >= 0, "r=" + r);
            r = rand.getNextBetween(5, 10);
            Assert.assertTrue(r <= 10, "r=" + r);
            Assert.assertTrue(r >= 5, "r=" + r);
            r = rand.getNextBetween(-5, 0);
            Assert.assertTrue(r <= 0, "r=" + r);
            Assert.assertTrue(r >= -5, "r=" + r);
            //console.log(r);
        }
    }

    @Test(description="rectangle intersections")
    public void rectangleIntersectionsTest() {
        var r = new Rectangle(2, 4, 0, 2);
        var p = r.rayIntersection(0, 1);
        Assert.assertTrue(p.x == 2);
        Assert.assertTrue(p.y == 1);
        p = r.rayIntersection(0, 0);
        Assert.assertTrue(p.x == 2);
    }

    @Test(description="matrix perf test")
    public void matrixPerfTest() {
        Assert.assertTrue(true); return; // disable

        var now = window.performance ? function () { return window.performance.now(); } : function () { };
        console.log("Array test:");
        var startTime = now();
        var totalRegularArrayTime = 0;
        var repeats = 1000;
        var n = 100;
        var M;
        for (var k = 0; k < repeats; ++k) {
            M = new Array(n);
            for (var i = 0; i < n; ++i) {
                M[i] = new Array(n);
            }
        }

        var t = now() - startTime;
        console.log("init = " + t);
        totalRegularArrayTime += t;
        startTime = now();
        for (var k = 0; k < repeats; ++k) {
            for (var i = 0; i < n; ++i) {
                for (var j = 0; j < n; ++j) {
                    M[i][j] = 1;
                }
            }
        }

        var t = now() - startTime;
        console.log("write array = " + t);
        totalRegularArrayTime += t;
        startTime = now();
        for (var k = 0; k < repeats; ++k) {
            var sum = 0;
            for (var i = 0; i < n; ++i) {
                for (var j = 0; j < n; ++j) {
                    sum += M[i][j];
                }
            }
            //Assert.assertEquals(sum, n * n);
        }
        var t = now() - startTime;
        console.log("read array = " + t);
        totalRegularArrayTime += t;
        startTime = now();
        Assert.assertTrue(true);

        var totalTypedArrayTime = 0;
        console.log("Typed Array test:");
        var startTime = now();
        for (var k = 0; k < repeats; ++k) {
            MT = new Float32Array(n * n);
        }

        var t = now() - startTime;
        console.log("init = " + t);
        totalTypedArrayTime += t;
        startTime = now();
        for (var k = 0; k < repeats; ++k) {
            for (var i = 0; i < n * n; ++i) {
                MT[i] = 1;
            }
        }
        var t = now() - startTime;
        console.log("write array = " + t);
        totalTypedArrayTime += t;
        startTime = now();
        for (var k = 0; k < repeats; ++k) {
            var sum = 0;
            for (var i = 0; i < n * n; ++i) {
                sum += MT[i];
            }
            //Assert.assertEquals(sum, n * n);
        }
        var t = now() - startTime;
        console.log("read array = " + t);
        totalTypedArrayTime += t;
        startTime = now();
        Assert.assertTrue(isNaN(totalRegularArrayTime) || totalRegularArrayTime < totalTypedArrayTime,
                          "totalRegularArrayTime=" + totalRegularArrayTime + " totalTypedArrayTime=" + totalTypedArrayTime
                          + " - if this consistently fails then maybe we should switch to typed arrays");
    }

    @Test(description="priority queue test")
    public void priorityQueueTest() {
        var q = new PriorityQueue((a, b) -> { return a <= b; });
        q.push(42, 5, 23, 5, Math.PI);
        var u = Math.PI, v;
        strictEqual(u, q.top());
        var cnt = 0;
        while ((v = q.pop()) !== null) {
            Assert.assertTrue(u <= v);
            u = v;
            ++cnt;
        }
        Assert.assertEquals(cnt, 5);
        q.push(42, 5, 23, 5, Math.PI);
        var k = q.push(13);
        strictEqual(Math.PI, q.top());
        q.reduceKey(k, 2);
        u = q.top();
        strictEqual(u, 2);
        cnt = 0;
        while ((v = q.pop()) !== null) {
            Assert.assertTrue(u <= v);
            u = v;
            ++cnt;
        }
        Assert.assertEquals(cnt, 6);
    }

    @Test(description="dijkstra")
    public void dijkstraTest() {
        // 0  4-3
        //  \/ /
        //  1-2
        var n = 5;
        var links = [[0, 1], [1, 2], [2, 3], [3, 4], [4, 1]],
            getSource = function (l) { return l[0] }, getTarget = function (l) { return l[1] }, getLength = function(l) { return 1 }
        var calc = new Calculator(n, links, getSource, getTarget, getLength);
        var d = calc.DistancesFromNode(0);
        deepEqual(d, [0, 1, 2, 3, 2]);
        var D = calc.DistanceMatrix();
        deepEqual(D, [
            [0, 1, 2, 3, 2],
            [1, 0, 1, 2, 1],
            [2, 1, 0, 1, 2],
            [3, 2, 1, 0, 1],
            [2, 1, 2, 1, 0]
        ]);
    }

    @Test(description="vpsc")
    public void vpscTest() {
        var round = function (v, p) {
            var m = Math.pow(10, p);
            return Math.round(v * m) / m;
        };
        var rnd = function (a, p) {
            if (typeof p === "undefined") { p = 4; }
            return a.map(function (v) { return round(v, p) })
        };
        var res = function (a, p) {
            if (typeof p === "undefined") { p = 4; }
            return a.map(function (v) { return round(v.position(), p) })
        };
        vpsctestcases.forEach(function (t) {
            var vs = t.variables.map(function (u, i) {
                var v;
                if (typeof u === "number") {
                    v = new Variable(u);
                } else {
                    v = new Variable(u.desiredPosition, u.weight, u.scale);
                }
                v.id = i;
                return v;
            });
            var cs = t.constraints.map(function (c) {
                return new Constraint(vs[c.left], vs[c.right], c.gap);
            });
            var solver = new Solver(vs, cs);
            solver.solve();
            if (typeof t.expected !== "undefined") {
                deepEqual(rnd(t.expected, t.precision), res(vs, t.precision), t.description);
            }
        });
    }

    @Test(description="rbtree")
    public void rbtreeTest() {
        var tree = new RBTree<Integer>( (a, b) -> { return a - b; });
        var data = [5, 8, 3, 1, 7, 6, 2];
        data.forEach(function (d) { tree.insert(d); });
        var it = tree.iterator(), item;
        var prev = 0;
        while ((item = it.next()) !== null) {
            Assert.assertTrue(prev < item);
            prev = item;
        }

        var m = tree.findIter(5);
        Assert.assertTrue(m.prev(3));
        Assert.assertTrue(m.next(6));
    }

    @Test(description="overlap removal")
    public void overlapRemovalTest() {
        var rs = [
            new Rectangle(0, 2, 0, 1),
            new Rectangle(1, 3, 0, 1)
        ];
        Assert.assertEquals(rs[0].overlapX(rs[1]), 1);
        Assert.assertEquals(rs[0].overlapY(rs[1]), 1);
        var vs = rs.map((r) -> {
            return new Variable(r.cx());
        });
        var cs = VPSC.generateXConstraints(rs, vs);
        Assert.assertEquals(cs.length, 1);
        Solver solver = new Solver(vs, cs);
        solver.solve();
        vs.forEach((v, i) -> {
            rs[i].setXCentre(v.position());
        });
        Assert.assertEquals(rs[0].overlapX(rs[1]), 0);
        Assert.assertEquals(rs[0].overlapY(rs[1]), 1);

        vs = rs.map( (r) -> {
            return new Variable(r.cy());
        });
        cs = VPSC.generateYConstraints(rs, vs);
        Assert.assertEquals(cs.length, 0);
    }

    private int overlaps(final Rectangle[] rs) {
        var cnt = 0;
        for (var i = 0, n = rs.length; i < n - 1; ++i) {
            var r1 = rs[i];
            for (var j = i + 1; j < n; ++j) {
                var r2 = rs[j];
                if (r1.overlapX(r2) > 0 && r1.overlapY(r2) > 0) {
                    cnt++;
                }
            }
        }
        return cnt;
    }

    @Test(description="cola.vpsc.removeOverlaps")
    public void removeOverlapsTest() {
        var rs = [
            new Rectangle(0, 4, 0, 4),
            new Rectangle(3, 5, 1, 2),
            new Rectangle(1, 3, 3, 5)
        ];
        Assert.assertEquals(overlaps(rs), 2);
        VPSC.removeOverlaps(rs);
        Assert.assertEquals(overlaps(rs), 0);
        Assert.assertEquals(rs[1].y, 1);
        Assert.assertEquals(rs[1].Y, 2);

        rs = [
            new Rectangle(148.314,303.923,94.4755,161.84969999999998),
            new Rectangle(251.725,326.6396,20.0193,69.68379999999999),
            new Rectangle(201.235,263.6349,117.221,236.923),
            new Rectangle(127.445,193.7047,46.5891,186.5991),
            new Rectangle(194.259,285.7201,204.182,259.13239999999996)
        ];
        VPSC.removeOverlaps(rs);
        Assert.assertEquals(overlaps(rs), 0);
    }

    @Test(description="packing")
    public void packingTest() {
        var nodes = []
        for (var i = 0; i < 9; i++) { nodes.push({width: 10, height: 10}) }
        CoLa.adaptor().nodes(nodes).start();
        var check = function (aspectRatioThreshold) {
            var dim = nodes.reduce(function (p, v) {
                return {
                    x: Math.min(v.x - v.width / 2, p.x),
                    y: Math.min(v.y - v.height / 2, p.y),
                    X: Math.max(v.x + v.width / 2, p.X),
                    Y: Math.max(v.y + v.height / 2, p.Y)
                };
            }, { x: Double.POSITIVE_INFINITY, X: Double.NEGATIVE_INFINITY, y: Double.POSITIVE_INFINITY, Y: Double.NEGATIVE_INFINITY });
            var width = dim.X - dim.x, height = dim.Y - dim.y;
            Assert.assertTrue(Math.abs(width / height - 1) < aspectRatioThreshold);
        }
        check(0.001);

        // regression test, used to cause infinite loop
        nodes = [{ width: 24, height: 35 }, { width: 24, height: 35 }, { width: 32, height: 35 }];
        CoLa.adaptor().nodes(nodes).start();
        check(0.3);

        // for some reason the first rectangle is offset by the following - no assertion for this yet.
        PseudoRandom rand = new PseudoRandom(51);
        for (var i = 0; i < 19; i++) { nodes.push({ width: rand.getNextBetween(5, 30), height: rand.getNextBetween(5, 30) }) }
        CoLa.adaptor().nodes(nodes).avoidOverlaps(false).start();
        check(0.1);
    }
    */
}
