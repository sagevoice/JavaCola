package edu.monash.infotech.marvl.cola;

import edu.monash.infotech.marvl.cola.vpsc.GraphNode;
import org.testng.annotations.*;
import org.testng.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ApiTests {

    protected class IntLinkAccessor implements LinkAccessor<int[]> {

        @Override
        public int getSourceIndex(int[] l) {
            return l[0];
        }

        @Override
        public int getTargetIndex(int[] l) {
            return l[1];
        }
    }

    @Test(groups = {"Headless API"}, description = "strongly connected components")
    public void StronglyConnectedComponentsTest() {
        final IntLinkAccessor la = new IntLinkAccessor();

        final List<int[]> links1 = new ArrayList<>();
        links1.add(new int[] {0, 1});
        final List<List<Integer>> components1 = LinkLengths.stronglyConnectedComponents(2, links1, la);
        Assert.assertEquals(components1.size(), 2);

        final List<int[]> links2 = Arrays.asList(new int[] {0, 1}, new int[] {1, 2}, new int[] {2, 0});
        final List<List<Integer>> components2 = LinkLengths.stronglyConnectedComponents(3, links2, la);
        Assert.assertEquals(components2.size(), 1);

        final List<int[]> links3 = Arrays
                .asList(new int[] {0, 1}, new int[] {1, 2}, new int[] {2, 0}, new int[] {2, 3}, new int[] {3, 4}, new int[] {4, 5},
                        new int[] {5, 3});
        final List<List<Integer>> components3 = LinkLengths.stronglyConnectedComponents(6, links3, la);
        Assert.assertEquals(components3.size(), 2);

        final List<int[]> links4 = Arrays
                .asList(new int[] {0, 1}, new int[] {1, 2}, new int[] {2, 0}, new int[] {2, 3}, new int[] {3, 4}, new int[] {4, 2});
        final List<List<Integer>> components4 = LinkLengths.stronglyConnectedComponents(5, links4, la);
        Assert.assertEquals(components4.size(), 1);
    }

    @Test(groups = {"Headless API"}, description = "Basic headless layout")
    public void BasicHeadlessLayoutTest() {
        // layout a triangular graph
        // should have no trouble finding an arrangement with all lengths close to the ideal length
        final Layout layout = new Layout()
                .links(Arrays.asList(new Link(0, 1), new Link(1, 2), new Link(2, 0)))
                .start(10);
        // that's it!

        final List<GraphNode> vs = layout.nodes();
        Assert.assertEquals(vs.size(), 3, "node array created");
        Assert.assertTrue(layout.alpha() <= layout.convergenceThreshold(), "converged to alpha=" + layout.alpha());
        final Consumer<Double> checkLengths = idealLength ->
                layout.links().forEach(e -> {
                    final GraphNode source = (GraphNode)e.source;
                    final GraphNode target = (GraphNode)e.target;
                    final double dx = source.x - target.x,
                            dy = source.y - target.y;
                    final double length = Math.sqrt(dx * dx + dy * dy);
                    Assert.assertTrue(0.01 > Math.abs(length - idealLength), "correct link length: " + length);
                });
        checkLengths.accept((Double)layout.linkDistance());

        // rerun layout with a new ideal link length
        layout.linkDistance(Double.valueOf(10.0)).start(10);
        checkLengths.accept(Double.valueOf(10.0));
    }

    @Test(groups = {"Headless API"}, description = "Layout events")
    public void LayoutEventsTest() {
        // layout a little star graph with listeners counting the different events
        final int start = 0, tick = 1, end = 2;
        final int[] values = new int[] {0, 0, 0};

        final Layout layout = new Layout()
                .links(Arrays.asList(new Link(0, 1), new Link(1, 2), new Link(1, 3)))
                .on(EventType.start, e -> values[start]++)
                .on(EventType.tick, e -> values[tick]++)
                .on(EventType.end, e -> values[end]++)
                .start();

        Assert.assertTrue(layout.alpha() <= layout.convergenceThreshold(), "converged to alpha=" + layout.alpha());
        Assert.assertEquals(values[start], 1, "started once");
        Assert.assertTrue(1 <= values[tick] && 50 > values[tick], String.format("ticked %d times", values[tick]));
        Assert.assertEquals(values[end], 1, "ended once");
    }

    @Test(groups = {"3D Layout"}, description = "single link")
    public void SingleLinkTest() {
        // single link with non-zero coords only in z-axis.
        // should relax to ideal length, nodes moving only in z-axis
        final List<Node3D> nodes = Arrays.asList(new Node3D(0, 0, -1), new Node3D(0, 0, 1));
        final List<Link3D> links = Arrays.asList(new Link3D(0, 1));
        final double desiredLength = 10;
        Layout3D layout = new Layout3D(nodes, links, desiredLength).start();
        double linkLength = layout.linkLength(links.get(0));
        nodes.forEach(v -> Assert.assertTrue(1e-5 > Math.abs(v.x) && 1e-5 > Math.abs(v.y)));
        Assert.assertTrue(1e-4 > Math.abs(linkLength - desiredLength), "length = " + linkLength);

        // test per-link desiredLength:
        final double smallerLength = 5;
        links.get(0).length = smallerLength;
        layout = new Layout3D(nodes, links);
        layout.useJaccardLinkLengths = false;
        layout.start();
        linkLength = layout.linkLength(links.get(0));
        Assert.assertTrue(1e-4 > Math.abs(linkLength - smallerLength), "length = " + linkLength);

    }

    protected class Graph3D {

        protected List<Node3D> nodes;
        protected List<Link3D> links;

        protected Graph3D(final int[][] links) {
            int n = -1;
            for (final int[] link : links) {
                n = Math.max(n, Math.max(link[0], link[1]));
            }
            final int N = n + 1;
            this.nodes = new ArrayList<>(N);
            for (int i=0; i < N; i++) {
                this.nodes.add(new Node3D());
            }
            this.links = Arrays.stream(links).map((link) -> new Link3D(link[0], link[1])).collect(Collectors.toList());
        }

    }

    @Test(groups = {"3D Layout"}, description = "Pyramid")
    public void PyramidTest() {
        // k4 should relax to a 3D pyramid with all edges the same length
        final Graph3D graph = new Graph3D(new int[][] {{0, 1}, {1, 2}, {2, 0}, {0, 3}, {1, 3}, {2, 3}});
        final Layout3D layout = new Layout3D(graph.nodes, graph.links, 10).start(0);

        final Descent d = layout.descent;
        final Consumer<Double> takeDescentStep = alpha -> {
            for (int i = 0; i < 3; ++i) {
                d.takeDescentStep(d.x[i], d.g[i], alpha);
            }
        };
        final Function<Void, double[]> reduceStress = (a) -> {
            d.computeDerivatives(d.x);
            final double alpha = 2 * d.computeStepSize(d.g);
            double f = 5;
            takeDescentStep.accept(f * alpha);
            final double sOver = d.computeStress();
            takeDescentStep.accept(-f * alpha);
            f = 0.8;
            takeDescentStep.accept(f * alpha);
            final double sUnder = d.computeStress();
            takeDescentStep.accept(-f * alpha);
            takeDescentStep.accept(alpha);
            final double s = d.computeStress();
            Assert.assertTrue(sOver >= s, String.format("  overshoot=%f, s=%f", sOver, s));
            Assert.assertTrue(sUnder >= s, String.format("  undershoot=%f, s=%f", sUnder, s));
            return new double[] {s, alpha};
        };
        double s = d.computeStress();

        for (int i = 0; i < 10; i++) {
            final double[] result = reduceStress.apply(null);
            final double s2 = result[0];
            final double alpha = result[1];
            Assert.assertTrue(s2 <= s, String.format("s=%f, s=%f, alpha=%f", s2, s, alpha));
            s = s2;
        }

        final Layout3D layout2 = new Layout3D(graph.nodes, graph.links, 10).start();
        final List<Double> lengths = graph.links.stream().map(l -> layout2.linkLength(l)).collect((Collectors.toList()));
        lengths.forEach(l -> Assert.assertTrue(Math.abs(l - lengths.get(0)) < 1e-4, "length = " + l));
    }

    @Test(groups = {"3D Layout"}, description = "Fixed nodes")
    public void FixedNodesTest() {
        final Graph3D graph = new Graph3D(new int[][] {{0, 1}, {1, 2}, {2, 3}, {3, 4}});
        final List<Node3D> nodes = graph.nodes;
        final BiConsumer<Integer, Double> lock = (i, x) -> {
            nodes.get(i).fixed = true;
            nodes.get(i).x = x;
        };

        final TriFunction<Double, Double, Double, Boolean> closeEnough = (a, b, t) -> Math.abs(a - b) < t;
        final Layout3D layout = new Layout3D(graph.nodes, graph.links, 10);

        final Function<Void, Void> check = (a) -> {
            // locked nodes should be at their initial position
            for (int i = 0; i < nodes.size(); i++) {
                if (nodes.get(i).fixed) {
                    for (int j = 0; j < Layout3D.k; j++) {
                        final String d = Layout3D.dims.get(j);
                        Assert.assertTrue(closeEnough.apply(layout.result[j][i], nodes.get(i).get(d), Double.valueOf(1)),
                                          String.format("nodes[%d] lock in $s-axis at %f, actual=%f", i, d, nodes.get(i).get(d),
                                                        layout.result[j][i]));
                    }
                }
            }

            final List<Double> lengths = graph.links.stream().map(l -> layout.linkLength(l)).collect(Collectors.toList());
            final Double meanLength = lengths.stream().reduce(Double.valueOf(0), (s, l) -> s + l) / lengths.size();

            // check all edge-lengths are within 5% of the mean
            lengths.forEach(l -> Assert.assertTrue(closeEnough.apply(l, meanLength, meanLength / 20), "edge length = " + l));
            return null;
        };

        // nodes 0 and 4 will be locked at (-5,0,0) and (5,0,0) respectively
        // with these locks and ideal edge length at 10, unfixed nodes will arc around in a horse-shoe shape
        lock.accept(Integer.valueOf(0), Double.valueOf(-5));
        lock.accept(Integer.valueOf(4), Double.valueOf(5));

        layout.start();

        check.apply(null);

        // move the lock positions
        lock.accept(Integer.valueOf(0), Double.valueOf(-10));
        lock.accept(Integer.valueOf(4), Double.valueOf(10));

        // run layout incrementally
        for (int i = 0; i < 100; i++) {
            layout.tick();
        }

        check.apply(null);

    }

}
