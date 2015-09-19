package edu.monash.infotech.marvl.cola;

import edu.monash.infotech.marvl.cola.geom.Geom;
import edu.monash.infotech.marvl.cola.geom.TVGPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class PolyUtils {

    private static int nextInt(final PseudoRandom rand, final int r) {
        return (int)Math.round(rand.getNext() * r);
    }

    private static TVGPoint makePoint(final double x, final double y) {
        return new TVGPoint(x, y);
    }

    private static TVGPoint makeRandPoint(final PseudoRandom rand, final int width, final int height) {
        return makePoint(nextInt(rand, width), nextInt(rand, height));
    }

    public static double length(final TVGPoint p, final TVGPoint q) {
        final double dx = p.x - q.x, dy = p.y - q.y;
        return dx * dx + dy * dy;
    }

    public static List<TVGPoint> makePoly(final PseudoRandom rand) {
        return makePoly(rand, 10, 10);
    }

    public static List<TVGPoint> makePoly(final PseudoRandom rand, final int width, final int height) {
        final int n = nextInt(rand, 7) + 3;
        List<TVGPoint> P = new ArrayList<>();
        loop:
        for (int i = 0; i < n; ++i) {
            TVGPoint p = makeRandPoint(rand, width, height);
            int ctr = 0;
            while (0 < i && 1 > length(P.get(i - 1), p) // min segment length is 1
                   || 1 < i && ( // new point must keep poly convex
                    0 >= Geom.isLeft(P.get(i - 2), P.get(i - 1), p)
                    || 0 >= Geom.isLeft(P.get(i - 1), p, P.get(0))
                    || 0 >= Geom.isLeft(p, P.get(0), P.get(1)))) {
                if (10 < ctr++) {
                    break loop; // give up after ten tries (maybe not enough space left for another convex point)
                }
                p = makeRandPoint(rand, width, height);
            }
            P.add(p);
        }
        if (2 < P.size()) { // must be at least triangular
            P.add(makePoint(P.get(0).x, P.get(0).y));
            return P;
        }
        return makePoly(rand, width, height);
    }

    public static List<List<TVGPoint>> makeNonoverlappingPolys(final PseudoRandom rand, final int n) {
        List<List<TVGPoint>> P = new ArrayList<>();
        final Predicate<List<TVGPoint>> overlaps = (p) -> {
            for (int i = 0; i < P.size(); i++) {
                final List<TVGPoint> q = P.get(i);
                if (Geom.polysOverlap(p, q)) { return true; }
            }
            return false;
        };
        for (int i = 0; i < n; i++) {
            List<TVGPoint> p = makePoly(rand);
            while (overlaps.test(p)) {
                final double dx = nextInt(rand, 10) - 5;
                double dy = nextInt(rand, 10) - 5;
                p.forEach((pt) -> { pt.x += dx; pt.y += dy; });
            }
            P.add(p);
        }
        List<TVGPoint> minPoly = new ArrayList<>();
        minPoly.add(makePoint(Double.MAX_VALUE, Double.MAX_VALUE));
        minPoly = P.stream().reduce(minPoly, (poly0, poly1) -> {
            final TVGPoint minPt1 = poly1.stream()
                                      .reduce(makePoint(Double.MAX_VALUE, Double.MAX_VALUE), (pt0, pt1) -> {
                                          pt0.x = Math.min(pt0.x, pt1.x);
                                          pt0.y = Math.min(pt0.y, pt1.y);
                                          return pt0;
                                      });
            final TVGPoint minPt0 = poly0.get(0);
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

    public static TVGPoint midPoint(final List<TVGPoint> p) {
        double mx = 0, my = 0;
        final int n = p.size() - 1;
        for (int i = 0; i < n; i++) {
            final TVGPoint q = p.get(i);
            mx += q.x;
            my += q.y;
        }
        return makePoint(mx / n, my / n);
    }
}
