package edu.monash.infotech.marvl.cola.vpsc;

import edu.monash.infotech.marvl.cola.geom.Point;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class VPSC {
    public static Rectangle computeGroupBounds(final Group g) {
        g.bounds = (null != g.leaves) ?
            Arrays.stream(g.leaves).reduce((Rectangle r, c) -> c.bounds.union(r), Rectangle.empty()) :
            Rectangle.empty();

        if (null != g.groups) {
            g.bounds = Arrays.stream(g.groups).reduce((Rectangle r, c) -> computeGroupBounds(c).union(r), g.bounds);
        }
        g.bounds = g.bounds.inflate(g.padding);
        return g.bounds;
    }

public static void makeEdgeBetween(Link link, Rectangle source, Rectangle target, double ah) {
    Point si = source.rayIntersection(target.cx(), target.cy());
    if (null == si) {
        si = new Point(source.cx(), source.cy());
    }
    Point ti = target.rayIntersection(source.cx(), source.cy());
    if (null == ti) {
        ti = new Point(target.cx(), target.cy());
    }
    final double dx = ti.x - si.x,
        dy = ti.y - si.y,
        l = Math.sqrt(dx * dx + dy * dy), al = l - ah;
    link.sourceIntersection = si;
    link.targetIntersection = ti;
    link.arrowStart = new Point(si.x + al * dx / l, si.y + al * dy / l);
}

    public static Point makeEdgeTo(Point s, Rectangle target, double ah) {
    Point ti = target.rayIntersection(s.x, s.y);
    if (null == ti) {
        ti = new Point(target.cx(), target.cy());
    }
    final double dx = ti.x - s.x,
        dy = ti.y - s.y,
        l = Math.sqrt(dx * dx + dy * dy);
    return new Point(ti.x - ah * dx / l, ti.y - ah * dy / l);
}

    public static int compareEvents(final Event a, final Event b) {
    if (a.pos > b.pos) {
        return 1;
    }
    if (a.pos < b.pos) {
        return -1;
    }
    if (a.isOpen) {
        // open must come before close
        return -1;
    }
    return 0;
}

    public static RBTree<Node> makeRBTree() {
    return new RBTree<>((a, b) -> (int)(a.pos - b.pos));
}


public static class xRect implements RectAccessors {

    @Override
    public double getCentre(Rectangle r) {
        return r.cx();
    }

    @Override
    public double getOpen(Rectangle r) {
        return r.y;
    }

    @Override
    public double getClose(Rectangle r) {
        return r.Y;
    }

    @Override
    public double getSize(Rectangle r) {
        return r.width();
    }

    @Override
    public Rectangle makeRect(double open, double close, double center, double size) {
        return new Rectangle(center - size / 2, center + size / 2, open, close);
    }

    @Override
    public void findNeighbours(Node v, RBTree<Node> scanline) {
        findXNeighbours(v, scanline);
    }
};
    public static xRect xRect = new xRect();

    public static class yRect implements RectAccessors {

        @Override
        public double getCentre(Rectangle r) {
            return r.cy();
        }

        @Override
        public double getOpen(Rectangle r) {
            return r.x;
        }

        @Override
        public double getClose(Rectangle r) {
            return r.X;
        }

        @Override
        public double getSize(Rectangle r) {
            return r.height();
        }

        @Override
        public Rectangle makeRect(double open, double close, double center, double size) {
            return new Rectangle(open, close, center - size / 2, center + size / 2);
        }

        @Override
        public void findNeighbours(Node v, RBTree<Node> scanline) {
            findYNeighbours(v, scanline);
        }
    }
    public static yRect yRect = new yRect();

    public static ArrayList<Constraint> generateGroupConstraints(final Group root, final RectAccessors f, final double minSep) {
        return generateGroupConstraints(root, f, minSep, false);

    }

    public static ArrayList<Constraint> generateGroupConstraints(Group root, RectAccessors f, double minSep, boolean isContained) {
    double padding = root.padding;
       int gn = (null != root.groups) ? root.groups.length : 0;
        int ln = (null != root.leaves) ? root.leaves.length : 0;
        ArrayList<Constraint> childConstraints = (0 == gn) ? new ArrayList<>()
        : Arrays.stream(root.groups).reduce((ArrayList<Constraint> ccs, g) -> ccs.concat(generateGroupConstraints(g, f, minSep, true)), []);
        int n = (isContained ? 2 : 0) + ln + gn;
        Variable[] vs = new Variable[n];
        Rectangle[] rs = new Rectangle[n];
        int i = 0;
        BiConsumer<Rectangle, Variable> add = (r, v) -> { rs[i] = r; vs[i++] = v; };
    if (isContained) {
        // if this group is contained by another, then we add two dummy vars and rectangles for the borders
        Rectangle b = root.bounds;
        double c = f.getCentre(b), s = f.getSize(b) / 2,
            open = f.getOpen(b), close = f.getClose(b),
            min = c - s + padding / 2, max = c + s - padding / 2;
        root.minVar.desiredPosition = min;
        add.accept(f.makeRect(open, close, min, padding), root.minVar);
        root.maxVar.desiredPosition = max;
        add.accept(f.makeRect(open, close, max, padding), root.maxVar);
    }
    if (0 < ln) {
        Arrays.stream(root.leaves).forEach(l -> add.accept(l.bounds, l.variable));
    }
    if (0 < gn) { Arrays.stream(root.groups).forEach(g -> {
        Rectangle b = g.bounds;
        add.accept(f.makeRect(f.getOpen(b), f.getClose(b), f.getCentre(b), f.getSize(b)), g.minVar);
    }); }
        ArrayList<Constraint> cs = generateConstraints(rs, vs, f, minSep);
    if (0 < gn) {
        Arrays.stream(vs).forEach(v -> { v.cOut = new ArrayList<>(); v.cIn = new ArrayList<>(); });
        cs.forEach(c -> { c.left.cOut.add(c); c.right.cIn.add(c); });
        Arrays.stream(root.groups).forEach(g -> {
            final double gapAdjustment = (g.padding - f.getSize(g.bounds)) / 2;
            g.minVar.cIn.forEach(c -> c.gap += gapAdjustment);
            g.minVar.cOut.forEach(c -> { c.left = g.maxVar; c.gap += gapAdjustment; });
        });
    }
    childConstraints.addAll(cs);
    return childConstraints;
}

    public static ArrayList<Constraint> generateConstraints(Rectangle[] rs, Variable[] vars, RectAccessors rect, double minSep) {
    int i, n = rs.length;
    int N = 2 * n;
        Event[] events = new Event[N];
    for (i = 0; i < n; ++i) {
        Rectangle r = rs[i];
        Node v = new Node(vars[i], r, rect.getCentre(r));
        events[i] = new Event(true, v, rect.getOpen(r));
        events[i + n] = new Event(false, v, rect.getClose(r));
    }
        events = (Event[])Arrays.stream(events).sorted((a, b) -> compareEvents(a, b)).collect(Collectors.toCollection(ArrayList::new)).toArray();
        ArrayList<Constraint> cs = new ArrayList<>();
    RBTree<Node> scanline = makeRBTree();
    for (i = 0; i < N; ++i) {
        Event e = events[i];
        Node v = e.v;
        if (e.isOpen) {
            scanline.insert(v);
            rect.findNeighbours(v, scanline);
        } else {
            // close event
            scanline.remove(v);
            BiConsumer<Node, Node> makeConstraint = (l, r) -> {
                final double sep = (rect.getSize(l.r) + rect.getSize(r.r)) / 2 + minSep;
                cs.add(new Constraint(l.v, r.v, sep));
            };
            final Consumer<BiConsumer<Node, Node>> reverseVisitNeighbours = (mkcon) -> {
                Node u;
                final Iterator<Node> it = v.prev.iterator();
                while (null != (u = it.prev())) {
                    mkcon.accept(u, v);
                    u.next.remove(v);
                }
            };

            final Consumer<BiConsumer<Node, Node>> forwardVisitNeighbours = (mkcon) -> {
                Node u;
                final Iterator<Node> it = v.next.iterator();
                while (null != (u = it.next())) {
                    mkcon.accept(u, v);
                    u.prev.remove(v);
                }
            };
            reverseVisitNeighbours.accept(makeConstraint);
            forwardVisitNeighbours.accept(makeConstraint);
        }
    }
    return cs;
}

    public static void findXNeighbours(Node v, RBTree<Node> scanline) {
        Iterator<Node> it = scanline.findIter(v);
        Node u;
        while ((u = it.next()) != null) {
            final double uovervX = u.r.overlapX(v.r);
            if (uovervX <= 0 || uovervX <= u.r.overlapY(v.r)) {
                v.next.insert(u);
                u.prev.insert(v);
            }
            if (uovervX <= 0) {
                break;
            }
        }

         it = scanline.findIter(v);
            while ((u = it.prev()) != null) {
                final double uovervX = u.r.overlapX(v.r);
                if (uovervX <= 0 || uovervX <= u.r.overlapY(v.r)) {
                    v.prev.insert(u);
                    u.next.insert(v);
                }
                if (uovervX <= 0) {
                    break;
                }
            }
    }


public static void findYNeighbours(Node v, RBTree<Node> scanline) {
        Node u = scanline.findIter(v).next();
        if (u != null && u.r.overlapX(v.r) > 0) {
            v.next.insert(u);
            u.prev.insert(v);
        }
         u = scanline.findIter(v).prev();
        if (u != null && u.r.overlapX(v.r) > 0) {
            v.prev.insert(u);
            u.next.insert(v);
        }

}

    public static ArrayList<Constraint> generateXConstraints(Rectangle[] rs, Variable[] vars) {
    return generateConstraints(rs, vars, xRect, 1e-6);
}

    public static ArrayList<Constraint> generateYConstraints(Rectangle[] rs, Variable[] vars) {
    return generateConstraints(rs, vars, yRect, 1e-6);
}

    public static ArrayList<Constraint> generateXGroupConstraints(Group root) {
    return generateGroupConstraints(root, xRect, 1e-6);
}

    public static ArrayList<Constraint> generateYGroupConstraints(Group root) {
    return generateGroupConstraints(root, yRect, 1e-6);
}

    public static void removeOverlaps(Rectangle[] rs) {
        Variable[] vs = (Variable[])Arrays.stream(rs).map(r -> new Variable(r.cx())).collect(Collectors.toCollection(ArrayList::new)).toArray();
        ArrayList<Constraint> cs = VPSC.generateXConstraints(rs, vs);
        Solver solver = new Solver(vs, (Constraint[])cs.toArray());
    solver.solve();
        for (int i=0; i < vs.length; i++) {
            rs[i].setXCentre(vs[i].position());
        }
    vs = (Variable[])Arrays.stream(rs).map(r -> new Variable(r.cy())).collect(Collectors.toCollection(ArrayList::new)).toArray();
    cs = VPSC.generateYConstraints(rs, vs);
    solver = new Solver(vs, (Constraint[])cs.toArray());
    solver.solve();
        for (int i=0; i < vs.length; i++) {
            rs[i].setYCentre(vs[i].position());
        }
}

}
