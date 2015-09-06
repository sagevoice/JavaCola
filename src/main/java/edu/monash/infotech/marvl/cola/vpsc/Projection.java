package edu.monash.infotech.marvl.cola.vpsc;

import edu.monash.infotech.marvl.cola.Constraint;

import java.util.Arrays;

public class Projection {
    private Constraint[] xConstraints;
    private Constraint[] yConstraints;
    private Variable[] variables;
    private GraphNode[] nodes;
    private Group[] groups;
    private Group rootGroup;
    private boolean avoidOverlaps;

    Projection(final GraphNode[] nodes, final Group[] groups) {
        this(nodes, groups, null);
    }

    Projection(final GraphNode[] nodes, final Group[] groups, final Group rootGroup) {
        this(nodes, groups, rootGroup, null);
    }

    Projection(final GraphNode[] nodes, final Group[] groups, final Group rootGroup, final Constraint[] constraints) {
        this(nodes, groups, rootGroup, constraints, false);
    }

    Projection(final GraphNode[] nodes, final Group[] groups, final Group rootGroup, final Constraint[] constraints, final boolean avoidOverlaps) {
        this.variables = Arrays.stream(nodes).map((v, i) -> {
            return v.variable = new IndexedVariable(i, 1.0);
        });

        if (null != constraints) this.createConstraints(constraints);

        if (avoidOverlaps && null != rootGroup && null != rootGroup.groups) {
            Arrays.stream(nodes).forEach(v -> {
                if (!v.width || !v.height) {
                    //If undefined, default to nothing
                    v.bounds = new Rectangle(v.x, v.x, v.y, v.y);
                    return;
                }
                double w2 = v.width / 2, h2 = v.height / 2;
                v.bounds = new Rectangle(v.x - w2, v.x + w2, v.y - h2, v.y + h2);
            });
            VPSC.computeGroupBounds(rootGroup);
            int i = nodes.length;
            Arrays.stream(groups).forEach(g -> {
                this.variables[i] = g.minVar = new IndexedVariable(i++, typeof g.stiffness !== "undefined" ? g.stiffness : 0.01);
                this.variables[i] = g.maxVar = new IndexedVariable(i++, typeof g.stiffness !== "undefined" ? g.stiffness : 0.01);
            });
        }
    }


    private Constraint createSeparation(Constraint c) {
        return new Constraint(
            this.nodes[c.left].variable,
            this.nodes[c.right].variable,
            c.gap,
            typeof c.equality !== "undefined" ? c.equality : false);
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
        GraphNode[] vs = c.offsets.map(o => this.nodes[o.node]).sort((a, b) => a[axis] - b[axis]);
        var p: GraphNode = null;
        vs.forEach(v => {
            if (p) v[axis] = p[axis] + p[dim] + 1
            p = v;
        });
    }

    private createAlignment(c: any) {
        var u = this.nodes[c.offsets[0].node].variable;
        this.makeFeasible(c);
        var cs = c.axis === 'x' ? this.xConstraints : this.yConstraints;
        c.offsets.slice(1).forEach(o => {
            var v = this.nodes[o.node].variable;
            cs.push(new Constraint(u, v, o.offset, true));
        });
    }

    private void createConstraints(constraints: any[]) {
        var isSep = c => typeof c.type === 'undefined' || c.type === 'separation';
        this.xConstraints = constraints
            .filter(c => c.axis === "x" && isSep(c))
            .map(c => this.createSeparation(c));
        this.yConstraints = constraints
            .filter(c => c.axis === "y" && isSep(c))
            .map(c => this.createSeparation(c));
        constraints
            .filter(c => c.type === 'alignment')
            .forEach(c => this.createAlignment(c));
    }

    private setupVariablesAndBounds(x0: number[], y0: number[], desired: number[], getDesired: (v: GraphNode) => number) {
        this.nodes.forEach((v, i) => {
            if (v.fixed) {
                v.variable.weight = 1000;
                desired[i] = getDesired(v);
            } else {
                v.variable.weight = 1;
            }
            var w = (v.width || 0) / 2, h = (v.height || 0) / 2;
            var ix = x0[i], iy = y0[i];
            v.bounds = new Rectangle(ix - w, ix + w, iy - h, iy + h);
        });
    }

    xProject(x0: number[], y0: number[], x: number[]) {
        if (!this.rootGroup && !(this.avoidOverlaps || this.xConstraints)) return;
        this.project(x0, y0, x0, x, v=> v.px, this.xConstraints, generateXGroupConstraints,
            v => v.bounds.setXCentre(x[(<IndexedVariable>v.variable).index] = v.variable.position()),
            g => {
                var xmin = x[(<IndexedVariable>g.minVar).index] = g.minVar.position();
                var xmax = x[(<IndexedVariable>g.maxVar).index] = g.maxVar.position();
                var p2 = g.padding / 2;
                g.bounds.x = xmin - p2;
                g.bounds.X = xmax + p2;
            });
    }

    yProject(x0: number[], y0: number[], y: number[]) {
        if (!this.rootGroup && !this.yConstraints) return;
        this.project(x0, y0, y0, y, v=> v.py, this.yConstraints, generateYGroupConstraints,
            v => v.bounds.setYCentre(y[(<IndexedVariable>v.variable).index] = v.variable.position()),
            g => {
                var ymin = y[(<IndexedVariable>g.minVar).index] = g.minVar.position();
                var ymax = y[(<IndexedVariable>g.maxVar).index] = g.maxVar.position();
                var p2 = g.padding / 2;
                g.bounds.y = ymin - p2;;
                g.bounds.Y = ymax + p2;
            });
    }

    projectFunctions(): { (x0: number[], y0: number[], r: number[]): void }[]{
        return [
            (x0, y0, x) => this.xProject(x0, y0, x),
            (x0, y0, y) => this.yProject(x0, y0, y)
        ];
    }

    private project(x0: number[], y0: number[], start: number[], desired: number[],
        getDesired: (v: GraphNode) => number,
        cs: Constraint[],
        generateConstraints: (g: Group) => Constraint[],
        updateNodeBounds: (v: GraphNode) => any,
        updateGroupBounds: (g: Group) => any)
    {
        this.setupVariablesAndBounds(x0, y0, desired, getDesired);
        if (this.rootGroup && this.avoidOverlaps) {
            VPSC.computeGroupBounds(this.rootGroup);
            cs = cs.concat(generateConstraints(this.rootGroup));
        }
        this.solve(this.variables, cs, start, desired);
        this.nodes.forEach(updateNodeBounds);
        if (this.rootGroup && this.avoidOverlaps) {
            this.groups.forEach(updateGroupBounds);
        }
    }

    private solve(vs: Variable[], cs: Constraint[], starting: number[], desired: number[]) {
        var solver = new vpsc.Solver(vs, cs);
        solver.setStartingPositions(starting);
        solver.setDesiredPositions(desired);
        solver.solve();
    }
}
