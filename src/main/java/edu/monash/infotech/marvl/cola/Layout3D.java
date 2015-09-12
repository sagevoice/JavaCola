package edu.monash.infotech.marvl.cola;

import edu.monash.infotech.marvl.cola.shortestpaths.Calculator;
import edu.monash.infotech.marvl.cola.vpsc.Constraint;
import edu.monash.infotech.marvl.cola.vpsc.GraphNode;
import edu.monash.infotech.marvl.cola.vpsc.Projection;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Use cola to do a layout in 3D!! Yay. Pretty simple for the moment. */
public class Layout3D {

    public static final List<String> dims = Arrays.asList("x", "y", "z");
    public static final int          k    = Layout3D.dims.size();
    public double[][]       result;
    public List<Constraint> constraints;
    public List<Node3D>     nodes;
    public List<Link3D>     links;
    public double           idealLinkLength;
    public boolean          useJaccardLinkLengths;
    public Descent          descent;

    Layout3D(final List<Node3D> nodes, final List<Link3D> links) {
        this(nodes, links, 1);
    }

    Layout3D(final List<Node3D> nodes, final List<Link3D> links, final double idealLinkLength) {
        this.nodes = nodes;
        this.links = links;
        this.idealLinkLength = idealLinkLength;
        this.result = new double[Layout3D.k][0];
        for (int i = 0; i < Layout3D.k; ++i) {
            this.result[i] = new double[nodes.size()];
        }
        for (int i = 0; i < nodes.size(); ++i) {
            final Node3D v = nodes.get(i);
            for (final String dim : Layout3D.dims) {
                if (Double.isNaN(v.get(dim))) {
                    v.set(dim, Math.random());
                }
            }
            this.result[0][i] = v.x;
            this.result[1][i] = v.y;
            this.result[2][i] = v.z;
        }
        this.useJaccardLinkLengths = true;
    }

    public double linkLength(final Link3D l) {
        return l.actualLength(this.result);
    }

    private class LinkAccessor implements LinkLengthAccessor<Link3D> {

        protected LinkAccessor() {
        }

        @Override
        public int getSourceIndex(final Link3D l) {
            return l.source;
        }

        @Override
        public int getTargetIndex(final Link3D l) {
            return l.target;
        }

        @Override
        public void setLength(final Link3D l, final double value) {
            l.length = value;
        }
    }

    public Layout3D start() {
        return start(100);
    }

    public Layout3D start(final int iterations) {
        final int n = this.nodes.size();

        final LinkAccessor linkAccessor = new LinkAccessor();

        if (this.useJaccardLinkLengths) {
            LinkLengths.jaccardLinkLengths(this.links, linkAccessor, 1.5);
        }

        this.links.forEach(e -> e.length *= this.idealLinkLength);

        // Create the distance matrix that Cola needs
        final double[][] distanceMatrix = (new Calculator<>(n, this.links,
                                                            e -> e.source, e -> e.target, e -> e.length)).DistanceMatrix();

        final double[][] D = Descent.createSquareMatrix(n, (i, j) -> distanceMatrix[i][j]);

        // G is a square matrix with G[i][j] = 1 iff there exists an edge between node i and node j
        // otherwise 2.
        final double[][] G = Descent.createSquareMatrix(n, (i, j) -> { return 2; });
        this.links.forEach((e) -> {G[e.source][e.target] = 1; G[e.target][e.source] = 1;});

        this.descent = new Descent(this.result, D);
        this.descent.threshold = 1e-3;
        this.descent.G = G;
        //let constraints = this.links.map(e=> <any>{
        //    axis: 'y', left: e.source, right: e.target, gap: e.length*1.5
        //});
        if (null != this.constraints) {
            final List<GraphNode> nodes2D = nodes.stream().collect(Collectors.toList());
            this.descent.project = new Projection(nodes2D, null, null, this.constraints).projectFunctions();
        }

        for (int i = 0; i < this.nodes.size(); i++) {
            final Node3D v = this.nodes.get(i);
            if (0 < (v.fixed & 1)) {
                this.descent.locks.add(i, new double[] {v.x, v.y, v.z});
            }
        }

        this.descent.run(iterations);
        return this;
    }

    public double tick() {
        this.descent.locks.clear();
        for (int i = 0; i < this.nodes.size(); i++) {
            final Node3D v = this.nodes.get(i);
            if (0 < (v.fixed & 1)) {
                this.descent.locks.add(i, new double[] {v.x, v.y, v.z});
            }
        }
        return this.descent.rungeKutta();
    }
}

