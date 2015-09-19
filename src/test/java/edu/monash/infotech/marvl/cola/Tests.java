package edu.monash.infotech.marvl.cola;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import edu.monash.infotech.marvl.cola.geom.*;
import edu.monash.infotech.marvl.cola.powergraph.Configuration;
import edu.monash.infotech.marvl.cola.powergraph.LinkTypeAccessor;
import edu.monash.infotech.marvl.cola.powergraph.Module;
import edu.monash.infotech.marvl.cola.powergraph.PowerEdge;
import edu.monash.infotech.marvl.cola.shortestpaths.Calculator;
import edu.monash.infotech.marvl.cola.vpsc.*;

import edu.monash.infotech.marvl.cola.vpsc.Iterator;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.*;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

    @Test(description = "shortest path with bends")
    public void shortestPathWithBendsTest() {
        //  0 - 1 - 2
        //      |   |
        //      3 - 4
        final List<int[]> nodes = Arrays.asList(new int[] {0, 0}, new int[] {1, 0}, new int[] {2, 0}, new int[] {1, 1}, new int[] {2, 1});
        final List<int[]> edges = Arrays
                .asList(new int[] {0, 1, 1}, new int[] {1, 2, 2}, new int[] {1, 3, 1}, new int[] {3, 4, 1}, new int[] {2, 4, 2});
        final ToIntFunction<int[]> source = (e) -> { return e[0];};
        final ToIntFunction<int[]> target = (e) -> { return e[1];};
        final ToDoubleFunction<int[]> length = (e) -> { return e[2];};
        final TriFunction<Integer, Integer, Integer, Double> prevCost = (u, v, w) -> {
            final int[] a = nodes.get(u), b = nodes.get(v), c = nodes.get(w);
            final double dx = Math.abs(c[0] - a[0]), dy = Math.abs(c[1] - a[1]);
            return dx > 0.01 && dy > 0.01
                   ? Double.valueOf(1000)
                   : Double.valueOf(0);
        };
        Calculator<int[]> sp = new Calculator<>(nodes.size(), edges, source, target, length);
        List<Integer> path = sp.PathFromNodeToNodeWithPrevCost(0, 4, prevCost);
        Assert.assertTrue(true);
    }

    @Test(description = "tangent visibility graph")
    public void tangentVisibilityGraphTest() {
        for (int tt = 0; tt < 100; tt++) {
            final PseudoRandom rand = new PseudoRandom(tt);
            final int n = 10;
            final List<List<TVGPoint>> P = Poly.makeNonoverlappingPolys(rand, n);
            final TVGPoint port1 = Poly.midPoint(P.get(8));
            final TVGPoint port2 = Poly.midPoint(P.get(9));
            final TangentVisibilityGraph g = new TangentVisibilityGraph(P);
            final VisibilityVertex start = g.addPoint(port1, 8);
            final VisibilityVertex end = g.addPoint(port2, 9);
            g.addEdgeIfVisible(port1, port2, 8, 9);
            final ToIntFunction<VisibilityEdge> getSource = (e) -> { return e.source.id; };
            final ToIntFunction<VisibilityEdge> getTarget = (e) -> { return e.target.id; };
            final ToDoubleFunction<VisibilityEdge> getLength = (e) -> { return e.length(); };
            final double[] shortestPath = (new Calculator<>(g.V.size(), g.E, getSource, getTarget, getLength))
                    .PathFromNodeToNode(start.id, end.id);
            Assert.assertTrue(0 < shortestPath.length);
        }
        Assert.assertTrue(true);
    }

    @Test(description = "tangents")
    public void tangentsTest() {
        final PseudoRandom rand = new PseudoRandom();
        final List<Point> rect = Arrays.asList(new Point(10, 10), new Point(20, 10), new Point(10, 20), new Point(20, 20));
        final List<Point> pnt = Arrays.asList(new Point(0, 0));
        final BiTangents t1 = Geom.tangents(pnt, rect);
        for (int j = 0; j < 100; j++) {
            final List<TVGPoint> A = Poly.makePoly(rand), B = Poly.makePoly(rand);
            B.forEach((p) -> { p.x += 11; });
            //if (j !== 207) continue;
            final BiTangents t = Geom.tangents(A, B);
            // ok(t.length === 4, t.length + " tangents found at j="+j);
        }
        Assert.assertTrue(true);
    }

    @Test(description = "pseudo random number test")
    public void pseudoRandomNumberTest() {
        final PseudoRandom rand = new PseudoRandom();
        for (int i = 0; i < 100; ++i) {
            double r = rand.getNext();
            Assert.assertTrue(1 >= r, "r=" + r);
            Assert.assertTrue(0 <= r, "r=" + r);
            r = rand.getNextBetween(5, 10);
            Assert.assertTrue(10 >= r, "r=" + r);
            Assert.assertTrue(5 <= r, "r=" + r);
            r = rand.getNextBetween(-5, 0);
            Assert.assertTrue(0 >= r, "r=" + r);
            Assert.assertTrue(-5 <= r, "r=" + r);
            //console.log(r);
        }
    }

    @Test(description = "rectangle intersections")
    public void rectangleIntersectionsTest() {
        final Rectangle r = new Rectangle(2, 4, 0, 2);
        Point p = r.rayIntersection(0, 1);
        Assert.assertEquals(p.x, 2.0);
        Assert.assertEquals(p.y, 1.0);
        p = r.rayIntersection(0, 0);
        Assert.assertEquals(p.x, 2.0);
    }

    @Test(description = "matrix perf test")
    public void matrixPerfTest() {
        Assert.assertTrue(true);// return; // disable

        log.debug("Array test:");
        Instant startTime = Instant.now();
        Duration totalRegularArrayTime = Duration.ofNanos(0);
        final int repeats = 1000;
        final int n = 100;
        List<List<Double>> M = new ArrayList<>(n);
        for (int k = 0; k < repeats; ++k) {
            M = new ArrayList<>(n);
            for (int i = 0; i < n; ++i) {
                M.add(new ArrayList<>(n));
                for (int j = 0; j < n; ++j) {
                    M.get(i).add(0.0);
                }
            }
        }

        long t = ChronoUnit.MILLIS.between(startTime, Instant.now());
        log.debug("init = " + t);
        totalRegularArrayTime = totalRegularArrayTime.plus(t, ChronoUnit.MILLIS);
        startTime = Instant.now();
        for (int k = 0; k < repeats; ++k) {
            for (int i = 0; i < n; ++i) {
                for (int j = 0; j < n; ++j) {
                    M.get(i).set(j, 1.0);
                }
            }
        }

        t = ChronoUnit.MILLIS.between(startTime, Instant.now());
        log.debug("write array = " + t);
        totalRegularArrayTime = totalRegularArrayTime.plus(t, ChronoUnit.MILLIS);
        startTime = Instant.now();
        for (int k = 0; k < repeats; ++k) {
            double sum = 0;
            for (int i = 0; i < n; ++i) {
                for (int j = 0; j < n; ++j) {
                    sum += M.get(i).get(j);
                }
            }
            //Assert.assertEquals(sum, n * n);
        }
        t = ChronoUnit.MILLIS.between(startTime, Instant.now());
        log.debug("read array = " + t);
        totalRegularArrayTime = totalRegularArrayTime.plus(t, ChronoUnit.MILLIS);
        Assert.assertTrue(true);

        Duration totalTypedArrayTime = Duration.ofNanos(0);
        log.debug("Typed Array test:");
        startTime = Instant.now();
        double[][] MT = new double[n][0];
        for (int k = 0; k < repeats; ++k) {
            MT = new double[n][0];
            for (int i = 0; i < n; ++i) {
                MT[i] = new double[n];
            }
        }

        t = ChronoUnit.MILLIS.between(startTime, Instant.now());
        log.debug("init = " + t);
        totalTypedArrayTime = totalTypedArrayTime.plus(t, ChronoUnit.MILLIS);
        startTime = Instant.now();
        for (int k = 0; k < repeats; ++k) {
            for (int i = 0; i < n; ++i) {
                for (int j = 0; j < n; ++j) {
                    MT[i][j] = 1.0;
                }
            }
        }
        t = ChronoUnit.MILLIS.between(startTime, Instant.now());
        log.debug("write array = " + t);
        totalTypedArrayTime = totalTypedArrayTime.plus(t, ChronoUnit.MILLIS);
        startTime = Instant.now();
        for (int k = 0; k < repeats; ++k) {
            double sum = 0;
            for (int i = 0; i < n; ++i) {
                for (int j = 0; j < n; ++j) {
                    sum += MT[i][j];
                }
            }
            //Assert.assertEquals(sum, n * n);
        }
        t = ChronoUnit.MILLIS.between(startTime, Instant.now());
        log.debug("read array = " + t);
        totalTypedArrayTime = totalTypedArrayTime.plus(t, ChronoUnit.MILLIS);
        Assert.assertTrue(0 < totalRegularArrayTime.compareTo(totalTypedArrayTime),
                          "totalRegularArrayTime=" + totalRegularArrayTime.toString() + " totalTypedArrayTime=" + totalTypedArrayTime
                                  .toString()
                          + " - if this consistently fails then maybe we should switch to typed arrays");
    }

    @Test(description = "priority queue test")
    public void priorityQueueTest() {
        PriorityQueue<Double> q = new PriorityQueue<>((a, b) -> { return a <= b; });
        q.push(42.0, 5.0, 23.0, 5.0, Math.PI);
        double u = Math.PI;
        Double v;
        Assert.assertEquals(u, q.top());
        int cnt = 0;
        while ((v = q.pop()) != null) {
            Assert.assertTrue(u <= v);
            u = v;
            ++cnt;
        }
        Assert.assertEquals(cnt, 5);
        q.push(42.0, 5.0, 23.0, 5.0, Math.PI);
        PairingHeap<Double> k = q.push(13.0);
        Assert.assertEquals(Math.PI, q.top());
        q.reduceKey(k, 2.0);
        u = q.top();
        Assert.assertEquals(u, Double.valueOf(2));
        cnt = 0;
        while ((v = q.pop()) != null) {
            Assert.assertTrue(u <= v);
            u = v;
            ++cnt;
        }
        Assert.assertEquals(cnt, 6);
    }

    @Test(description = "dijkstra")
    public void dijkstraTest() {
        // 0  4-3
        //  \/ /
        //  1-2
        final int n = 5;
        final List<int[]> links = Arrays.asList(new int[] {0, 1}, new int[] {1, 2}, new int[] {2, 3}, new int[] {3, 4}, new int[] {4, 1});
        final ToIntFunction<int[]> getSource = (l) -> { return l[0]; };
        final ToIntFunction<int[]> getTarget = (l) -> { return l[1]; };
        final ToDoubleFunction<int[]> getLength = (l) -> { return 1.0; };
        final Calculator<int[]> calc = new Calculator<>(n, links, getSource, getTarget, getLength);
        final double[] d = calc.DistancesFromNode(0);
        Assert.assertEquals(d, new double[] {0, 1, 2, 3, 2});
        final double[][] D = calc.DistanceMatrix();
        Assert.assertEquals(D, new double[][] {
                {0, 1, 2, 3, 2},
                {1, 0, 1, 2, 1},
                {2, 1, 0, 1, 2},
                {3, 2, 1, 0, 1},
                {2, 1, 2, 1, 0}
        });
    }

    @Test(description = "vpsc")
    public void vpscTest() {
        final BiFunction<Double, Integer, Double> round = (v, p) -> {
            final double m = Math.pow(10, p);
            return Math.round(v * m) / m;
        };
        final BiFunction<List<Double>, Integer, List<Double>> rnd = (a, p) -> {
            return a.stream().map((v) -> { return round.apply(v, p); }).collect(Collectors.toList());
        };
        final BiFunction<List<Variable>, Integer, List<Double>> res = (a, p) -> {
            return a.stream().map((v) -> { return round.apply(v.position(), p); }).collect(Collectors.toList());
        };

        try (final InputStream stream = getClass().getResourceAsStream("/vpsctests.json")) {
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode graph = mapper.readTree(stream);
            final JsonNode jsonNodes = graph.get("vpsctestcases");
            for (final JsonNode jsonNode : jsonNodes) {
                final List<Variable> vs;
                final List<Constraint> cs;
                final JsonNode variables = jsonNode.get("variables");
                if (null != variables) {
                    vs = new ArrayList<>(variables.size());
                    for (final JsonNode var : variables) {
                        final Variable v;
                        if (var.isInt()) {
                            v = new Variable(var.asInt());
                        } else {
                            v = new Variable(var.get("desiredPosition").asDouble(),
                                             var.path("weight").asDouble(1.0),
                                             var.path("scale").asDouble(1.0));
                        }
                        vs.add(v);
                    }
                } else {
                    vs = new ArrayList<>();
                }
                final JsonNode constraints = jsonNode.get("constraints");
                if (null != constraints) {
                    cs = new ArrayList<>(constraints.size());
                    for (final JsonNode con : constraints) {
                        final Constraint c;
                        c = new Constraint(vs.get(con.get("left").asInt()),
                                           vs.get(con.get("right").asInt()),
                                           con.get("gap").asDouble());
                        cs.add(c);
                    }
                } else {
                    cs = new ArrayList<>();
                }
                final Solver solver = new Solver(vs, cs);
                solver.solve();
                final JsonNode expected = jsonNode.get("expected");
                if (null != expected) {
                    final int precision = jsonNode.path("precision").asInt(4);
                    final String description = jsonNode.path("description").asText();
                    final List<Double> ex = new ArrayList<>(expected.size());
                    for (final JsonNode e : expected) {
                        if (e.isNumber()) {
                            ex.add(e.asDouble());
                        }
                    }
                    Assert.assertEquals(rnd.apply(ex, precision), res.apply(vs, precision), description);
                }
            }
        } catch (IOException e) {
            log.error("IOException in vpscTest", e);
            throw new RuntimeException(e);
        }
    }

    @Test(description="rbtree")
    public void rbtreeTest() {
        final RBTree<Integer> tree = new RBTree<>( (a, b) -> { return a - b; });
        final List<Integer> data = Arrays.asList(5, 8, 3, 1, 7, 6, 2);
        data.forEach((d) -> { tree.insert(d); });
        final Iterator<Integer> it = tree.iterator();
        Integer item;
        Integer prev = 0;
        while (null != (item = it.next())) {
            Assert.assertTrue(prev < item);
            prev = item;
        }

        final Iterator<Integer> m = tree.findIter(5);
        Assert.assertEquals(m.data(), Integer.valueOf(5));
        Assert.assertEquals(m.prev(), Integer.valueOf(3));
        m.next(); //advance forward once (to item 5) to undo the m.prev() above
        Assert.assertEquals(m.next(), Integer.valueOf(6));
    }

    /*
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
