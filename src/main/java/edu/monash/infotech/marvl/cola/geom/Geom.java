package edu.monash.infotech.marvl.cola.geom;

import edu.monash.infotech.marvl.cola.vpsc.Rectangle;
import edu.monash.infotech.marvl.cola.TriFunction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public class Geom {

    /** tests if a point is Left|On|Right of an infinite line.
     * @param  P0, P1, and P2
     * @return >0 for P2 left of the line through P0 and P1
     *            =0 for P2 on the line
     *            <0 for P2 right of the line
     */
    public static double isLeft(Point P0, Point P1, Point P2) {
        return (P1.x - P0.x) * (P2.y - P0.y) - (P2.x - P0.x) * (P1.y - P0.y);
    }

    public static boolean above(Point p, Point vi, Point vj) {
        return 0 < isLeft(p, vi, vj);
    }

    public static boolean below(Point p, Point vi, Point vj) {
        return 0 > isLeft(p, vi, vj);
    }


    /**
     * returns the convex hull of a set of points using Andrew's monotone chain algorithm
     * see: http://geomalgorithms.com/a10-_hull-1.html#Monotone%20Chain
     * @param S array of points
     * @return the convex hull as an array of points
     */
    public static List<Point> ConvexHull(Point[] S) {
        Point[] P = (Point[])Arrays.stream(S.clone()).sorted((a, b) -> a.x != b.x ? (int)(b.x - a.x) : (int)(b.y - a.y)).toArray();
        int n = S.length, i;
        int minmin = 0;
        double xmin = P[0].x;
        for (i = 1; i < n; ++i) {
            if (P[i].x != xmin) {
                break;
            }
        }
        int minmax = i - 1;
        List<Point> H = new ArrayList<>();
        H.add(P[minmin]); // push minmin point onto stack
        if (minmax == n - 1) { // degenerate case: all x-coords == xmin
            if (P[minmax].y != P[minmin].y) // a  nontrivial segment
                H.add(P[minmax]);
        } else {
            // Get the indices of points with max x-coord and min|max y-coord
            int maxmin, maxmax = n - 1;
            double xmax = P[n - 1].x;
            for (i = n - 2; i >= 0; i--)
                if (P[i].x != xmax) break;
            maxmin = i + 1;

            // Compute the lower hull on the stack H
            i = minmax;
            while (++i <= maxmin) {
                // the lower line joins P[minmin]  with P[maxmin]
                if (0 <= isLeft(P[minmin], P[maxmin], P[i]) && i < maxmin)
                    continue; // ignore P[i] above or on the lower line

                while (1 < H.size()) // there are at least 2 points on the stack
                {
                    // test if  P[i] is left of the line at the stack top
                    if (0 < isLeft(H.get(H.size() - 2), H.get(H.size() - 1), P[i])) {
                        break; // P[i] is a new hull  vertex
                    } else {
                        H.remove(H.size() -1); // pop top point off  stack
                    }
                }
                if (i != minmin) {
                    H.add(P[i]);
                }
            }

            // Next, compute the upper hull on the stack H above the bottom hull
            if (maxmax != maxmin) // if  distinct xmax points
                H.add(P[maxmax]); // push maxmax point onto stack
            int bot = H.size(); // the bottom point of the upper hull stack
            i = maxmin;
            while (--i >= minmax) {
                // the upper line joins P[maxmax]  with P[minmax]
                if (isLeft(P[maxmax], P[minmax], P[i]) >= 0 && i > minmax)
                    continue; // ignore P[i] below or on the upper line

                while (H.size() > bot) // at least 2 points on the upper stack
                {
                    // test if  P[i] is left of the line at the stack top
                    if (isLeft(H.get(H.size() - 2), H.get(H.size() - 1), P[i]) > 0) {
                        break; // P[i] is a new hull  vertex
                    } else {
                        H.remove(H.size() - 1); // pop top point off  stack
                    }
                }
                if (i != minmin) {
                    H.add(P[i]); // push P[i] onto stack
                }
            }
        }
        return H;
    }

    // apply f to the points in P in clockwise order around the point p
    public static void clockwiseRadialSweep(final Point p, final Point[] P, final Function f) {
        Arrays.stream(P.clone()).sorted(
                (a, b) -> (int)(Math.atan2(a.y - p.y, a.x - p.x) - Math.atan2(b.y - p.y, b.x - p.x))
        ).forEach((a) -> f.apply(a));
    }

    public static PolyPoint nextPolyPoint(PolyPoint p, PolyPoint[] ps) {
        if (p.polyIndex == ps.length - 1) {
            return ps[0];
        }
        return ps[p.polyIndex + 1];
    }

    public static PolyPoint prevPolyPoint(PolyPoint p, PolyPoint[] ps) {
        if (p.polyIndex == 0) {
            return ps[ps.length - 1];
        }
        return ps[p.polyIndex - 1];
    }

    // tangent_PointPolyC(): fast binary search for tangents to a convex polygon
    //    Input:  P = a 2D point (exterior to the polygon)
    //            n = number of polygon vertices
    //            V = array of vertices for a 2D convex polygon with V[n] = V[0]
    //    Output: rtan = index of rightmost tangent point V[rtan]
    //            ltan = index of leftmost tangent point V[ltan]
    public static LRTangent tangent_PointPolyC(Point P, Point[] V) {
        return new LRTangent(Ltangent_PointPolyC(P, V), Rtangent_PointPolyC(P, V));
    }

    // Rtangent_PointPolyC(): binary search for convex polygon right tangent
    //    Input:  P = a 2D point (exterior to the polygon)
    //            n = number of polygon vertices
    //            V = array of vertices for a 2D convex polygon with V[n] = V[0]
    //    Return: index "i" of rightmost tangent point V[i]
    public static int Rtangent_PointPolyC(Point P, Point[] V) {
        int n = V.length - 1;

        // use binary search for large convex polygons
        int a, b, c;            // indices for edge chain endpoints
        boolean upA, dnC;       // test for up direction of edges a and c

        // rightmost tangent = maximum for the isLeft() ordering
        // test if V[0] is a local maximum
        if (below(P, V[1], V[0]) && !above(P, V[n - 1], V[0]))
            return 0;               // V[0] is the maximum tangent point

        for (a = 0, b = n; ;) {          // start chain = [0,n] with V[n]=V[0]
            if (b - a == 1) {
                if (above(P, V[a], V[b])) {
                    return a;
                } else {
                    return b;
                }
            }
            c = (int)Math.floor((a + b) / 2);        // midpoint of [a,b], and 0<c<n
            dnC = below(P, V[c + 1], V[c]);
            if (dnC && !above(P, V[c - 1], V[c])) {
                return c;          // V[c] is the maximum tangent point
            }

            // no max yet, so continue with the binary search
            // pick one of the two subchains [a,c] or [c,b]
            upA = above(P, V[a + 1], V[a]);
            if (upA) {                       // edge a points up
                if (dnC) {                         // edge c points down
                    b = c;                           // select [a,c]
                } else {                           // edge c points up
                    if (above(P, V[a], V[c])) {     // V[a] above V[c]
                        b = c;                       // select [a,c]
                    } else {                          // V[a] below V[c]
                        a = c;                       // select [c,b]
                    }
                }
            } else {                           // edge a points down
                if (!dnC) {                       // edge c points up
                    a = c;                           // select [c,b]
                } else {                           // edge c points down
                    if (below(P, V[a], V[c])) {     // V[a] below V[c]
                        b = c;                       // select [a,c]
                    } else {                         // V[a] above V[c]
                        a = c;                       // select [c,b]
                    }
                }
            }
        }
    }

    // Ltangent_PointPolyC(): binary search for convex polygon left tangent
    //    Input:  P = a 2D point (exterior to the polygon)
    //            n = number of polygon vertices
    //            V = array of vertices for a 2D convex polygon with V[n]=V[0]
    //    Return: index "i" of leftmost tangent point V[i]
    public static int Ltangent_PointPolyC(Point P, Point[] V) {
        int n = V.length - 1;
        // use binary search for large convex polygons
        int a, b, c;             // indices for edge chain endpoints
        boolean dnA, dnC;           // test for down direction of edges a and c

        // leftmost tangent = minimum for the isLeft() ordering
        // test if V[0] is a local minimum
        if (above(P, V[n - 1], V[0]) && !below(P, V[1], V[0])) {
            return 0;               // V[0] is the minimum tangent point
        }

        for (a = 0, b = n; ;) {          // start chain = [0,n] with V[n] = V[0]
            if (b - a == 1) {
                if (below(P, V[a], V[b])) {
                    return a;
                } else {
                    return b;
                }
            }

            c = (int)Math.floor((a + b) / 2);        // midpoint of [a,b], and 0<c<n
            dnC = below(P, V[c + 1], V[c]);
            if (above(P, V[c - 1], V[c]) && !dnC) {
                return c;          // V[c] is the minimum tangent point
            }

            // no min yet, so continue with the binary search
            // pick one of the two subchains [a,c] or [c,b]
            dnA = below(P, V[a + 1], V[a]);
            if (dnA) {                       // edge a points down
                if (!dnC) {                       // edge c points up
                    b = c;                           // select [a,c]
                } else {                           // edge c points down
                    if (below(P, V[a], V[c])) {     // V[a] below V[c]
                        b = c;                       // select [a,c]
                    } else {                         // V[a] above V[c]
                        a = c;                       // select [c,b]
                    }
                }
            }
            else {                           // edge a points up
                if (dnC) {                        // edge c points down
                    a = c;                           // select [c,b]
                } else {                           // edge c points up
                    if (above(P, V[a], V[c])) {    // V[a] above V[c]
                        b = c;                       // select [a,c]
                    } else {                         // V[a] below V[c]
                        a = c;                       // select [c,b]
                    }
                }
            }
        }
    }

    // RLtangent_PolyPolyC(): get the RL tangent between two convex polygons
    //    Input:  m = number of vertices in polygon 1
    //            V = array of vertices for convex polygon 1 with V[m]=V[0]
    //            n = number of vertices in polygon 2
    //            W = array of vertices for convex polygon 2 with W[n]=W[0]
    //    Output: *t1 = index of tangent point V[t1] for polygon 1
    //            *t2 = index of tangent point W[t2] for polygon 2
    public static BiTangent tangent_PolyPolyC(Point[] V, Point[] W, BiFunction<Point, Point[], Integer> t1, BiFunction<Point, Point[], Integer> t2, TriFunction<Point, Point, Point, Boolean> cmp1, TriFunction<Point, Point, Point, Boolean> cmp2) {
        int ix1, ix2;      // search indices for polygons 1 and 2

        // first get the initial vertex on each polygon
        ix1 = t1.apply(W[0], V);   // right tangent from W[0] to V
        ix2 = t2.apply(V[ix1], W); // left tangent from V[ix1] to W

        // ping-pong linear search until it stabilizes
        boolean done = false;                    // flag when done
        while (!done) {
            done = true;                     // assume done until...
            while (true) {
                if (ix1 == V.length - 1) {
                    ix1 = 0;
                }
                if (cmp1.apply(W[ix2], V[ix1], V[ix1 + 1])) {
                    break;
                }
                ++ix1;                       // get Rtangent from W[ix2] to V
            }
            while (true) {
                if (ix2 == 0) {
                    ix2 = W.length - 1;
                }
                if (cmp2.apply(V[ix1], W[ix2], W[ix2 - 1])) {
                    break;
                }
                --ix2;                       // get Ltangent from V[ix1] to W
                done = false;                // not done if had to adjust this
            }
        }
        return new BiTangent(ix1, ix2);
    }

    public static BiTangent LRtangent_PolyPolyC(Point[] V, Point[] W) {
        BiTangent rl = RLtangent_PolyPolyC(W, V);
        return new BiTangent(rl.t2, rl.t1);
    }

    public static BiTangent RLtangent_PolyPolyC(Point[] V, Point[] W) {
        return tangent_PolyPolyC(V, W, (a, b) -> Rtangent_PointPolyC(a, b), (a, b) -> Ltangent_PointPolyC(a, b), (a, b, c) -> above(a, b, c), (a, b, c) -> below(a, b, c));
    }

    public static BiTangent LLtangent_PolyPolyC(Point[] V, Point[] W) {
        return tangent_PolyPolyC(V, W, (a, b) -> Ltangent_PointPolyC(a, b), (a, b) -> Ltangent_PointPolyC(a, b), (a, b, c) -> below(a, b, c), (a, b, c) -> below(a, b, c));
    }

    public static BiTangent RRtangent_PolyPolyC(Point[] V, Point[] W) {
        return tangent_PolyPolyC(V, W, (a, b) -> Rtangent_PointPolyC(a, b), (a, b) -> Rtangent_PointPolyC(a, b), (a, b, c) -> above(a, b, c), (a, b, c) -> above(a, b, c));
    }


    public static List<Point> intersects(final LineSegment l, final Point[] P) {
        final List<Point> ints = new ArrayList<>();
        for (int i = 1, n = P.length; i < n; ++i) {
            final Point intersection = Rectangle.lineIntersection(
                l.x1, l.y1,
                l.x2, l.y2,
                P[i - 1].x, P[i - 1].y,
                P[i].x, P[i].y
                );
            if (null != intersection) {
                ints.add(intersection);
            }
        }
        return ints;
    }

    public static BiTangents tangents(Point[] V, Point[] W) {
        int m = V.length - 1, n = W.length - 1;
        BiTangents bt = new BiTangents();
        for (int i = 0; i < m; ++i) {
            for (int j = 0; j < n; ++j) {
                Point v1 = V[i == 0 ? m - 1 : i - 1];
                Point v2 = V[i];
                Point v3 = V[i + 1];
                Point w1 = W[j == 0 ? n - 1 : j - 1];
                Point w2 = W[j];
                Point w3 = W[j + 1];
                double v1v2w2 = isLeft(v1, v2, w2);
                double v2w1w2 = isLeft(v2, w1, w2);
                double v2w2w3 = isLeft(v2, w2, w3);
                double w1w2v2 = isLeft(w1, w2, v2);
                double w2v1v2 = isLeft(w2, v1, v2);
                double w2v2v3 = isLeft(w2, v2, v3);
                if (v1v2w2 >= 0 && v2w1w2 >= 0 && v2w2w3 < 0
                    && w1w2v2 >= 0 && w2v1v2 >= 0 && w2v2v3 < 0) {
                        bt.ll = new BiTangent(i, j);
                } else if (v1v2w2 <= 0 && v2w1w2 <= 0 && v2w2w3 > 0
                    && w1w2v2 <= 0 && w2v1v2 <= 0 && w2v2v3 > 0) {
                        bt.rr = new BiTangent(i, j);
                } else if (v1v2w2 <= 0 && v2w1w2 > 0 && v2w2w3 <= 0
                    && w1w2v2 >= 0 && w2v1v2 < 0 && w2v2v3 >= 0) {
                        bt.rl = new BiTangent(i, j);
                } else if (v1v2w2 >= 0 && v2w1w2 < 0 && v2w2w3 >= 0
                    && w1w2v2 <= 0 && w2v1v2 > 0 && w2v2v3 <= 0) {
                        bt.lr = new BiTangent(i, j);
                }
            }
        }
        return bt;
    }

    public static boolean isPointInsidePoly(Point p, Point[] poly) {
        for (int i = 1, n = poly.length; i < n; ++i)
            if (below(poly[i - 1], poly[i], p)) return false;
        return true;
    }

    public static boolean isAnyPInQ(final Point[] p, final Point[] q) {
        return !Arrays.stream(p).allMatch(v -> !isPointInsidePoly(v, q));
    }

    public static boolean polysOverlap(Point[] p, Point[] q) {
        if (isAnyPInQ(p, q)) {
            return true;
        }
        if (isAnyPInQ(q, p)) {
            return true;
        }
        for (int i = 1, n = p.length; i < n; ++i) {
            Point v = p[i], u = p[i - 1];
            if (intersects(new LineSegment(u.x, u.y, v.x, v.y), q).size() > 0) {
                return true;
            }
        }
        return false;
    }
}