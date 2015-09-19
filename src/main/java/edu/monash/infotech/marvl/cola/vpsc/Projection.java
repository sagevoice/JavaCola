package edu.monash.infotech.marvl.cola.vpsc;

import edu.monash.infotech.marvl.cola.TriConsumer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Projection {

    private List<Constraint> xConstraints;
    private List<Constraint> yConstraints;
    private List<Variable>   variables;
    private List<GraphNode>  nodes;
    private List<Group>      groups;
    private Group            rootGroup;
    private boolean          avoidOverlaps;

    public Projection(final List<GraphNode> nodes, final List<Group> groups) {
        this(nodes, groups, null);
    }

    public Projection(final List<GraphNode> nodes, final List<Group> groups, final Group rootGroup) {
        this(nodes, groups, rootGroup, null);
    }

    public Projection(final List<GraphNode> nodes, final List<Group> groups, final Group rootGroup, final List<Constraint> constraints) {
        this(nodes, groups, rootGroup, constraints, false);
    }

    public Projection(final List<GraphNode> nodes, final List<Group> groups, final Group rootGroup, final List<Constraint> constraints,
                      final boolean avoidOverlaps)
    {
        //noinspection AssignmentToCollectionOrArrayFieldFromParameter
        this.nodes = nodes;
        //noinspection AssignmentToCollectionOrArrayFieldFromParameter
        this.groups = groups;
        this.rootGroup = rootGroup;
        this.avoidOverlaps = avoidOverlaps;

        int vlen = nodes.size();
        if (avoidOverlaps && null != rootGroup && null != rootGroup.groups) {
            vlen += 2 * groups.size();
        }
        this.variables = new ArrayList<>(vlen);
        for (int i = 0; i < nodes.size(); i++) {
            final GraphNode v = nodes.get(i);
            v.variable = new IndexedVariable(i, 1.0);
            this.variables.add(v.variable);
        }

        if (null != constraints) {
            this.createConstraints(constraints);
        }

        if (avoidOverlaps && null != rootGroup && null != rootGroup.groups) {
            nodes.forEach(v -> {
                if (0 == v.width || 0 == v.height) {
                    //If undefined, default to nothing
                    v.bounds = new Rectangle(v.x, v.x, v.y, v.y);
                    return;
                }
                double w2 = v.width / 2, h2 = v.height / 2;
                v.bounds = new Rectangle(v.x - w2, v.x + w2, v.y - h2, v.y + h2);
            });
            VPSC.computeGroupBounds(rootGroup);
            int i = nodes.size();
            for (int j = 0; j < groups.size(); j++) {
                final Group g = groups.get(j);

                g.minVar = new IndexedVariable(i++, 0 < g.stiffness ? g.stiffness : 0.01);
                this.variables.add(g.minVar);
                g.maxVar = new IndexedVariable(i++, 0 < g.stiffness ? g.stiffness : 0.01);
                this.variables.add(g.maxVar);
            }
        }
    }

    private Constraint createSeparation(Constraint c) {
        return new Constraint(
                this.nodes.get(c.leftIndex).variable,
                this.nodes.get(c.rightIndex).variable,
                c.gap,
                c.equality);
    }

    private void makeFeasible(final Constraint c) {
        if (!this.avoidOverlaps) {
            return;
        }
        final String axis, dim;
        if ("x".equals(c.axis)) {
            axis = "y";
            dim = "height";
        } else {
            axis = "x";
            dim = "width";
        }
        final GraphNode[] vs = c.offsets.stream().map(o -> this.nodes.get(o.node)).sorted((a, b) -> (int)Math.signum(a.get(axis) - b.get(axis)))
                                .collect(Collectors.toList()).toArray(new GraphNode[c.offsets.size()]);
        GraphNode p = null;
        for (int i = 0; i < vs.length; i++) {
            final GraphNode v = vs[i];
            if (null != p) {
                v.set(axis, p.get(axis) + p.get(dim) + 1);
            }
            p = v;
        }
    }

    private void createAlignment(final Constraint c) {
        Variable u = this.nodes.get(c.offsets.get(0).node).variable;
        this.makeFeasible(c);
        final List<Constraint> cs = "x".equals(c.axis) ? this.xConstraints : this.yConstraints;
        c.offsets.subList(1, c.offsets.size()).forEach(o -> {
            Variable v = this.nodes.get(o.node).variable;
            cs.add(new Constraint(u, v, o.offset, true));
        });
    }

    private void createConstraints(final List<Constraint> constraints) {
        final Function<Constraint, Boolean> isSep = c -> null == c.type || "separation".equals(c.type);
        this.xConstraints = constraints.stream()
                                       .filter(c -> "x".equals(c.axis) && isSep.apply(c))
                                       .map(c -> this.createSeparation(c)).collect(Collectors.toList());
        this.yConstraints = constraints.stream()
                                       .filter(c -> "y".equals(c.axis) && isSep.apply(c))
                                       .map(c -> this.createSeparation(c)).collect(Collectors.toList());
        constraints.stream()
                   .filter(c -> "alignment".equals(c.type))
                   .forEach(c -> this.createAlignment(c));
    }

    private void setupVariablesAndBounds(final double[] x0, final double[] y0, final double[] desired,
                                         final ToDoubleFunction<GraphNode> getDesired)
    {
        for (int i = 0; i < nodes.size(); i++) {
            final GraphNode v = nodes.get(i);
            if (v.fixed) {
                v.variable.weight = 1000;
                desired[i] = getDesired.applyAsDouble(v);
            } else {
                v.variable.weight = 1;
            }
            final double w = v.width / 2.0, h = v.height / 2.0;
            final double ix = x0[i], iy = y0[i];
            v.bounds = new Rectangle(ix - w, ix + w, iy - h, iy + h);
        }
    }

    private void xProject(final double[] x0, final double[] y0, final double[] x) {
        if (null == this.rootGroup && !(this.avoidOverlaps || null != this.xConstraints)) {
            return;
        }
        this.project(x0, y0, x0, x, v -> v.px, this.xConstraints, g -> VPSC.generateXGroupConstraints(g).toArray(new Constraint[1]),
                     v -> {
                         final double cx = v.variable.position();
                         x[v.variable.index] = cx;
                         v.bounds.setXCentre(cx);
                     },
                     g -> {
                         double xmin = g.minVar.position();
                         x[g.minVar.index] = xmin;
                         double xmax = g.maxVar.position();
                         x[g.maxVar.index] = xmax;
                         double p2 = g.padding / 2;
                         g.bounds.x = xmin - p2;
                         g.bounds.X = xmax + p2;
                     });
    }

    private void yProject(final double[] x0, final double[] y0, final double[] y) {
        if (null == this.rootGroup && null == this.yConstraints) {
            return;
        }
        this.project(x0, y0, y0, y, v -> v.py, this.yConstraints, g -> VPSC.generateYGroupConstraints(g).toArray(new Constraint[1]),
                     v -> {
                         final double cy = v.variable.position();
                         y[v.variable.index] = cy;
                         v.bounds.setYCentre(cy);
                     },
                     g -> {
                         double ymin = g.minVar.position();
                         y[g.minVar.index] = ymin;
                         double ymax = g.maxVar.position();
                         y[g.maxVar.index] = ymax;
                         double p2 = g.padding / 2;
                         g.bounds.y = ymin - p2;
                         g.bounds.Y = ymax + p2;
                     }
        );
    }

    public List<TriConsumer<double[], double[], double[]>> projectFunctions() {
        final List<TriConsumer<double[], double[], double[]>> result = new ArrayList<>(2);
        result.add((x0, y0, x) -> this.xProject(x0, y0, x));
        result.add((x0, y0, y) -> this.yProject(x0, y0, y));
        return result;
    }

    private void project(final double[] x0, final double[] y0, final double[] start, final double[] desired,
                         ToDoubleFunction<GraphNode> getDesired,
                         List<Constraint> cs,
                         Function<Group, Constraint[]> generateConstraints,
                         Consumer<GraphNode> updateNodeBounds,
                         Consumer<Group> updateGroupBounds)

    {
        this.setupVariablesAndBounds(x0, y0, desired, getDesired);
        if (null != this.rootGroup && this.avoidOverlaps) {
            VPSC.computeGroupBounds(this.rootGroup);
            cs = Stream.concat(cs.stream(), Arrays.stream(generateConstraints.apply(this.rootGroup))).collect(Collectors.toList());
        }
        this.solve(this.variables, cs, start, desired);
        this.nodes.forEach(updateNodeBounds);
        if (null != this.rootGroup && this.avoidOverlaps) {
            this.groups.forEach(updateGroupBounds);
        }
    }

    private void solve(List<Variable> vs, List<Constraint> cs, double[] starting, double[] desired) {
        final Solver solver = new Solver(vs, cs);
        solver.setStartingPositions(starting);
        solver.setDesiredPositions(desired);
        solver.solve();
    }
}
