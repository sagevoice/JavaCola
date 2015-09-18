package edu.monash.infotech.marvl.cola;

import edu.monash.infotech.marvl.cola.geom.Geom;
import edu.monash.infotech.marvl.cola.geom.Point;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class Poly {

    private static int nextInt(final PseudoRandom rand, final int r) {
        return (int)Math.round(rand.getNext() * r);
    }

    public static double length(final Point p, final Point q) {
        final double dx = p.x - q.x, dy = p.y - q.y;
        return dx * dx + dy * dy;
    }

    public static List<Point> makePoly(final PseudoRandom rand) {
        return makePoly(rand, 10, 10);
    }

    public static List<Point> makePoly(final PseudoRandom rand, final int width, final int height) {
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

    public static List<List<Point>> makeNonoverlappingPolys(final PseudoRandom rand, final int n) {
        List<List<Point>> P = new ArrayList<>();
        final Predicate<List<Point>> overlaps = (p) -> {
            for (int i = 0; i < P.size(); i++) {
                final List<Point> q = P.get(i);
                if (Geom.polysOverlap(p, q)) { return true; }
            }
            return false;
        };
        for (int i = 0; i < n; i++) {
            List<Point> p = makePoly(rand);
            while (overlaps.test(p)) {
                final double dx = nextInt(rand, 10) - 5;
                double dy = nextInt(rand, 10) - 5;
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

    public static Point midPoint(final List<Point> p) {
        double mx = 0, my = 0;
        final int n = p.size() - 1;
        for (int i = 0; i < n; i++) {
            final Point q = p.get(i);
            mx += q.x;
            my += q.y;
        }
        return new Point(mx / n, my / n);
    }
}
