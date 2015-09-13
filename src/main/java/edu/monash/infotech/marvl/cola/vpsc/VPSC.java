package edu.monash.infotech.marvl.cola.vpsc;

import edu.monash.infotech.marvl.cola.Link;
import edu.monash.infotech.marvl.cola.geom.Point;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class VPSC {

    public static Rectangle computeGroupBounds(final Group g) {
        if (null != g.leaves) {
            final GraphNode leafUnion = g.leaves.stream().reduce(new GraphNode(Rectangle.empty()), (l1, l2) -> {
                l1.bounds = l1.bounds.union(l2.bounds);
                return l1;
            });
            g.bounds = leafUnion.bounds;
        } else {
            g.bounds = Rectangle.empty();
        }

        if (null != g.groups) {
            final Group groupUnion = g.groups.stream().reduce(new Group(g.bounds), (g1, g2) -> {
                g1.bounds = computeGroupBounds(g2).union(g1.bounds);
                return g1;
            });
            g.bounds = groupUnion.bounds;
        }
        g.bounds = g.bounds.inflate(g.padding);
        return g.bounds;
    }

    public static void makeEdgeBetween(final Link link, final Rectangle source, final Rectangle target, final double ah) {
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

    public static Point makeEdgeTo(final Point s, final Rectangle target, final double ah) {
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
        //noinspection NumericCastThatLosesPrecision
        return new RBTree<>((a, b) -> (int)(a.pos - b.pos));
    }


    private static class xRect implements RectAccessors {

        @Override
        public double getCentre(final Rectangle r) {
            return r.cx();
        }

        @Override
        public double getOpen(final Rectangle r) {
            return r.y;
        }

        @Override
        public double getClose(final Rectangle r) {
            return r.Y;
        }

        @Override
        public double getSize(final Rectangle r) {
            return r.width();
        }

        @Override
        public Rectangle makeRect(final double open, final double close, final double center, final double size) {
            return new Rectangle(center - size / 2, center + size / 2, open, close);
        }

        @Override
        public void findNeighbours(final Node v, final RBTree<Node> scanline) {
            findXNeighbours(v, scanline);
        }
    }


    public static xRect xRect = new xRect();


    private static class yRect implements RectAccessors {

        @Override
        public double getCentre(final Rectangle r) {
            return r.cy();
        }

        @Override
        public double getOpen(final Rectangle r) {
            return r.x;
        }

        @Override
        public double getClose(final Rectangle r) {
            return r.X;
        }

        @Override
        public double getSize(final Rectangle r) {
            return r.height();
        }

        @Override
        public Rectangle makeRect(final double open, final double close, final double center, final double size) {
            return new Rectangle(open, close, center - size / 2, center + size / 2);
        }

        @Override
        public void findNeighbours(final Node v, final RBTree<Node> scanline) {
            findYNeighbours(v, scanline);
        }
    }


    public static yRect yRect = new yRect();

    public static List<Constraint> generateGroupConstraints(final Group root, final RectAccessors f, final double minSep) {
        return generateGroupConstraints(root, f, minSep, false);

    }

    public static List<Constraint> generateGroupConstraints(final Group root, final RectAccessors f, final double minSep,
                                                                 final boolean isContained)
    {
        final double padding = root.padding;
        final int gn = (null != root.groups) ? root.groups.size() : 0;
        final int ln = (null != root.leaves) ? root.leaves.size() : 0;
        final List<Constraint> childConstraints = new ArrayList<>();
        for (int j = 0; j < gn; j++) {
            final Group g = root.groups.get(j);
            childConstraints.addAll(generateGroupConstraints(g, f, minSep, true));
        }

        final int n = (isContained ? 2 : 0) + ln + gn;
        final Rectangle[] rs = new Rectangle[n];
        final List<Variable> vs = new ArrayList<>(n);
        int i = 0;
        if (isContained) {
            // if this group is contained by another, then we add two dummy vars and rectangles for the borders
            final Rectangle b = root.bounds;
            final double c = f.getCentre(b), s = f.getSize(b) / 2,
                    open = f.getOpen(b), close = f.getClose(b),
                    min = c - s + padding / 2, max = c + s - padding / 2;
            root.minVar.desiredPosition = min;
            rs[i] = f.makeRect(open, close, min, padding);
            vs.set(i++, root.minVar);
            root.maxVar.desiredPosition = max;
            rs[i] = f.makeRect(open, close, max, padding);
            vs.set(i++, root.maxVar);
        }
        for (int j = 0; j < ln; j++) {
            final GraphNode l = root.leaves.get(j);
            rs[i] = l.bounds;
            vs.set(i++, l.variable);
        }
        for (int j = 0; j < gn; j++) {
            final Group g = root.groups.get(j);
            final Rectangle b = g.bounds;
            rs[i] = f.makeRect(f.getOpen(b), f.getClose(b), f.getCentre(b), f.getSize(b));
            vs.set(i++, g.minVar);
        }
        final List<Constraint> cs = generateConstraints(rs, vs, f, minSep);
        if (0 < gn) {
            vs.forEach(v -> {
                v.cOut = new ArrayList<>();
                v.cIn = new ArrayList<>();
            });
            cs.forEach(c -> {
                c.left.cOut.add(c);
                c.right.cIn.add(c);
            });
            root.groups.stream().forEach(g -> {
                final double gapAdjustment = (g.padding - f.getSize(g.bounds)) / 2;
                g.minVar.cIn.forEach(c -> {
                    c.gap += gapAdjustment;
                });
                g.minVar.cOut.forEach(c -> {
                    c.left = g.maxVar;
                    c.gap += gapAdjustment;
                });
            });
        }
        childConstraints.addAll(cs);
        return childConstraints;
    }

    public static List<Constraint> generateConstraints(final Rectangle[] rs, final List<Variable> vars, final RectAccessors rect,
                                                            final double minSep)
    {
        int i;
        final int n = rs.length;
        final int N = 2 * n;
        Event[] events = new Event[N];
        for (i = 0; i < n; ++i) {
            final Rectangle r = rs[i];
            final Node v = new Node(vars.get(i), r, rect.getCentre(r));
            events[i] = new Event(true, v, rect.getOpen(r));
            events[i + n] = new Event(false, v, rect.getClose(r));
        }
        events = Arrays.stream(events).sorted((a, b) -> compareEvents(a, b)).collect(Collectors.toList())
                       .toArray(new Event[N]);
        final List<Constraint> cs = new ArrayList<>();
        final RBTree<Node> scanline = makeRBTree();
        for (i = 0; i < N; ++i) {
            final Event e = events[i];
            final Node v = e.v;
            if (e.isOpen) {
                scanline.insert(v);
                rect.findNeighbours(v, scanline);
            } else {
                // close event
                scanline.remove(v);
                final BiConsumer<Node, Node> makeConstraint = (l, r) -> {
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

    public static void findXNeighbours(final Node v, final RBTree<Node> scanline) {
        Iterator<Node> it = scanline.findIter(v);
        Node u;
        while ((u = it.next()) != null) {
            final double uovervX = u.r.overlapX(v.r);
            if (0 >= uovervX || uovervX <= u.r.overlapY(v.r)) {
                v.next.insert(u);
                u.prev.insert(v);
            }
            if (0 >= uovervX) {
                break;
            }
        }

        it = scanline.findIter(v);
        while ((u = it.prev()) != null) {
            final double uovervX = u.r.overlapX(v.r);
            if (0 >= uovervX || uovervX <= u.r.overlapY(v.r)) {
                v.prev.insert(u);
                u.next.insert(v);
            }
            if (0 >= uovervX) {
                break;
            }
        }
    }


    public static void findYNeighbours(final Node v, final RBTree<Node> scanline) {
        Node u = scanline.findIter(v).next();
        if (null != u && 0 < u.r.overlapX(v.r)) {
            v.next.insert(u);
            u.prev.insert(v);
        }
        u = scanline.findIter(v).prev();
        if (null != u && 0 < u.r.overlapX(v.r)) {
            v.prev.insert(u);
            u.next.insert(v);
        }

    }

    public static List<Constraint> generateXConstraints(final Rectangle[] rs, final List<Variable> vars) {
        return generateConstraints(rs, vars, xRect, 1e-6);
    }

    public static List<Constraint> generateYConstraints(final Rectangle[] rs, final List<Variable> vars) {
        return generateConstraints(rs, vars, yRect, 1e-6);
    }

    public static List<Constraint> generateXGroupConstraints(final Group root) {
        return generateGroupConstraints(root, xRect, 1e-6);
    }

    public static List<Constraint> generateYGroupConstraints(final Group root) {
        return generateGroupConstraints(root, yRect, 1e-6);
    }

    public static void removeOverlaps(final Rectangle[] rs) {
        List<Variable> vs = Arrays.stream(rs).map(r -> new Variable(r.cx())).collect(Collectors.toList());
        List<Constraint> cs = VPSC.generateXConstraints(rs, vs);
        Solver solver = new Solver(vs, cs);
        solver.solve();
        for (int i = 0; i < vs.size(); i++) {
            rs[i].setXCentre(vs.get(i).position());
        }
        vs = Arrays.stream(rs).map(r -> new Variable(r.cy())).collect(Collectors.toList());
        cs = VPSC.generateYConstraints(rs, vs);
        solver = new Solver(vs, cs);
        solver.solve();
        for (int i = 0; i < vs.size(); i++) {
            rs[i].setYCentre(vs.get(i).position());
        }
    }

}
