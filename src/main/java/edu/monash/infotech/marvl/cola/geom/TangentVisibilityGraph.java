package edu.monash.infotech.marvl.cola.geom;

import java.util.ArrayList;
import java.util.List;

public class TangentVisibilityGraph extends VisibilityGraph {
    public List<List<TVGPoint>> P;

    public TangentVisibilityGraph(final List<List<TVGPoint>> P) {
        super();
        this.P = new ArrayList<>(P);
        final int n = P.size();
        for (int i = 0; i < n; i++) {
            final List<TVGPoint> p = P.get(i);
            for (int j = 0; j < p.size(); ++j) {
                final TVGPoint pj = p.get(j);
                VisibilityVertex vv = new VisibilityVertex(this.V.size(), i, j, pj);
                this.V.add(vv);
                if (0 < j) {
                    this.E.add(new VisibilityEdge(p.get(j - 1).vv, vv));
                }
            }
        }
        for (int i = 0; i < n - 1; i++) {
            final List<TVGPoint> Pi = P.get(i);
            for (int j = i + 1; j < n; j++) {
                final List<TVGPoint> Pj = P.get(j);
                final BiTangents t = Geom.tangents(Pi, Pj);
                for (final BiTangent c : t.values()) {
                    final TVGPoint source = Pi.get(c.t1), target = Pj.get(c.t2);
                    this.addEdgeIfVisible(source, target, i, j);
                }
            }
        }
    }

    public TangentVisibilityGraph(final List<List<TVGPoint>> P, final VisibilityGraph g0) {
        super(new ArrayList<>(g0.V), new ArrayList<>(g0.E));
        this.P = P;
    }

    public void addEdgeIfVisible(TVGPoint u, TVGPoint v, int i1, int i2) {
        if (!this.intersectsPolys(new LineSegment(u.x, u.y, v.x, v.y), i1, i2)) {
            this.E.add(new VisibilityEdge(u.vv, v.vv));
        }
    }

    public VisibilityVertex addPoint(final TVGPoint p, final int i1) {
        final int n = this.P.size();
        this.V.add(new VisibilityVertex(this.V.size(), n, 0, p));
        for (int i = 0; i < n; ++i) {
            if (i == i1) {
                continue;
            }
            final List<TVGPoint> poly = this.P.get(i);
            final LRTangent t = Geom.tangent_PointPolyC(p, poly);
            this.addEdgeIfVisible(p, poly.get(t.ltan), i1, i);
            this.addEdgeIfVisible(p, poly.get(t.rtan), i1, i);
        }
        return p.vv;
    }

    private boolean intersectsPolys(LineSegment l, int i1, int i2) {
        for (int i = 0, n = this.P.size(); i < n; ++i) {
            if (i != i1 && i != i2 && 0 < Geom.intersects(l, this.P.get(i)).size()) {
                return true;
            }
        }
        return false;
    }
}
