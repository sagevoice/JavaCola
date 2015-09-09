package edu.monash.infotech.marvl.cola.vpsc;

import edu.monash.infotech.marvl.cola.TriConsumer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

public class Projection {
    private Constraint[] xConstraints;
    private Constraint[] yConstraints;
    private Variable[] variables;
    private GraphNode[] nodes;
    private Group[] groups;
    private Group rootGroup;
    private boolean avoidOverlaps;

    public Projection(final GraphNode[] nodes, final Group[] groups) {
        this(nodes, groups, null);
    }

    public Projection(final GraphNode[] nodes, final Group[] groups, final Group rootGroup) {
        this(nodes, groups, rootGroup, null);
    }

    public Projection(final GraphNode[] nodes, final Group[] groups, final Group rootGroup, final Constraint[] constraints) {
        this(nodes, groups, rootGroup, constraints, false);
    }

    public Projection(final GraphNode[] nodes, final Group[] groups, final Group rootGroup, final Constraint[] constraints, final boolean avoidOverlaps) {
        this.nodes = nodes;
        this.groups = groups;
        this.rootGroup = rootGroup;
        this.avoidOverlaps = avoidOverlaps;

        int vlen = nodes.length;
        if (avoidOverlaps && null != rootGroup && null != rootGroup.groups) {
            vlen += 2 * groups.length;
        }
        this.variables = new Variable[vlen];
        for(int i=0; i<nodes.length; i++) {
            final GraphNode v = nodes[i];
            v.variable = new IndexedVariable(i, 1.0);
            this.variables[i] = v.variable;
        }

        if (null != constraints) {
            this.createConstraints(constraints);
        }

        if (avoidOverlaps && null != rootGroup && null != rootGroup.groups) {
            Arrays.stream(nodes).forEach(v -> {
                if (0 == v.width || 0 == v.height) {
                    //If undefined, default to nothing
                    v.bounds = new Rectangle(v.x, v.x, v.y, v.y);
                    return;
                }
                double w2 = v.width / 2, h2 = v.height / 2;
                v.bounds = new Rectangle(v.x - w2, v.x + w2, v.y - h2, v.y + h2);
            });
            VPSC.computeGroupBounds(rootGroup);
            int i = nodes.length;
                for(int j=0; j<groups.length; j++) {
                    final Group g = groups[j];

                this.variables[i] = g.minVar = new IndexedVariable(i++, 0 < g.stiffness ? g.stiffness : 0.01);
                this.variables[i] = g.maxVar = new IndexedVariable(i++, 0 < g.stiffness ? g.stiffness : 0.01);
            }
        }
    }

    private Constraint createSeparation(Constraint c) {
        return new Constraint(
            this.nodes[c.left].variable,
            this.nodes[c.right].variable,
            c.gap,
            c.equality);
    }

    private void makeFeasible(Constraint c) {
        if (!this.avoidOverlaps) {
            return;
        }
        String axis = "x", dim = "width";
        if (c.axis.equals("x")) {
            axis = "y";
            dim = "height";
        }
        GraphNode[] vs = c.offsets.map(o -> this.nodes[o.node]).sort((a, b) -> a[axis] - b[axis]);
        GraphNode p = null;
        vs.forEach(v -> {
            if (null != p) v[axis] = p[axis] + p[dim] + 1;
            p = v;
        });
    }

    private createAlignment(Constraint c) {
        Variable u = this.nodes[c.offsets[0].node].variable;
        this.makeFeasible(c);
        Constraint[] cs = c.axis.equals("x") ? this.xConstraints : this.yConstraints;
        c.offsets.slice(1).forEach(o -> {
                Variable v = this.nodes[o.node].variable;
            cs.push(new Constraint(u, v, o.offset, true));
        });
    }

    private void createConstraints(Constraint[] constraints) {
        var isSep = c -> typeof c.type === 'undefined' || c.type === 'separation';
        this.xConstraints = constraints
            .filter(c -> c.axis === "x" && isSep(c))
            .map(c => this.createSeparation(c));
        this.yConstraints = constraints
            .filter(c -> c.axis === "y" && isSep(c))
            .map(c => this.createSeparation(c));
        constraints
            .filter(c -> c.type === 'alignment')
            .forEach(c -> this.createAlignment(c));
    }

    private void setupVariablesAndBounds(final double[] x0, final double[] y0, final double[] desired, final ToDoubleFunction<GraphNode> getDesired) {
        for (int i=0; i < nodes.length; i++) {
            final GraphNode v = nodes[i];
            if (v.fixed) {
                v.variable.weight = 1000;
                desired[i] = getDesired.applyAsDouble(v);
            } else {
                v.variable.weight = 1;
            }
            final double w = v.width / 2, h = v.height / 2;
            final double ix = x0[i], iy = y0[i];
            v.bounds = new Rectangle(ix - w, ix + w, iy - h, iy + h);
        }
    }

    private void xProject(final double[] x0, final double[] y0, final double[] x) {
        if ( null == this.rootGroup && !(this.avoidOverlaps || null != this.xConstraints)) {
            return;
        }
        this.project(x0, y0, x0, x, v-> v.px, this.xConstraints, g -> (Constraint[])VPSC.generateXGroupConstraints(g).toArray(),
            v -> v.bounds.setXCentre(x[((IndexedVariable)v.variable).index] = v.variable.position()),
            g -> {
                double xmin = x[((IndexedVariable)g.minVar).index] = g.minVar.position();
                double xmax = x[((IndexedVariable)g.maxVar).index] = g.maxVar.position();
                double p2 = g.padding / 2;
                g.bounds.x = xmin - p2;
                g.bounds.X = xmax + p2;
            });
    }

    private void yProject(final double[] x0, final double[] y0, final double[] y) {
        if (null == this.rootGroup && null == this.yConstraints) {
            return;
        }
        this.project(x0, y0, y0, y, v -> v.py, this.yConstraints, g -> (Constraint[])VPSC.generateYGroupConstraints(g).toArray(),
            v -> v.bounds.setYCentre(y[((IndexedVariable)v.variable).index] = v.variable.position()),
            g -> {
                double ymin = y[((IndexedVariable)g.minVar).index] = g.minVar.position();
                double ymax = y[((IndexedVariable)g.maxVar).index] = g.maxVar.position();
                double p2 = g.padding / 2;
                g.bounds.y = ymin - p2;
                g.bounds.Y = ymax + p2;
            });
    }

    public ArrayList<TriConsumer<double[], double[], double[]>> projectFunctions(){
        ArrayList<TriConsumer<double[], double[], double[]>> result = new ArrayList<>(2);
        result.add((x0, y0, x) -> this.xProject(x0, y0, x));
        result.add((x0, y0, y) -> this.yProject(x0, y0, y));
        return result;
    }

    private void project(final double[] x0, final double[] y0, final double[] start, final double[] desired,
                    ToDoubleFunction<GraphNode> getDesired,
                    Constraint[] cs,
       Function<Group, Constraint[]> generateConstraints,
    Consumer<GraphNode> updateNodeBounds,
    Consumer<Group> updateGroupBounds)

    {
        this.setupVariablesAndBounds(x0, y0, desired, getDesired);
        if (null != this.rootGroup && this.avoidOverlaps) {
            VPSC.computeGroupBounds(this.rootGroup);
            cs = (Constraint[])Stream.concat(Arrays.stream(cs), Arrays.stream(generateConstraints.apply(this.rootGroup))).toArray();
        }
        this.solve(this.variables, cs, start, desired);
        Arrays.stream(this.nodes).forEach(updateNodeBounds);
        if (null != this.rootGroup && this.avoidOverlaps) {
            Arrays.stream(this.groups).forEach(updateGroupBounds);
        }
    }

    private void solve(Variable[] vs, Constraint[] cs, double[] starting, double[] desired) {
        Solver solver = new Solver(vs, cs);
        solver.setStartingPositions(starting);
        solver.setDesiredPositions(desired);
        solver.solve();
    }
}
