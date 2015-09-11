package edu.monash.infotech.marvl.cola.geom;

import java.util.ArrayList;

public class TangentVisibilityGraph extends VisibilityGraph {
    public TVGPoint[][] P;

    public TangentVisibilityGraph(final TVGPoint[][] P) {
        super();
        this.P = P.clone();
        final int n = P.length;
        for (int i = 0; i < n; i++) {
            final TVGPoint[] p = P[i];
            for (int j = 0; j < p.length; ++j) {
                final TVGPoint pj = p[j];
                VisibilityVertex vv = new VisibilityVertex(this.V.size(), i, j, pj);
                this.V.add(vv);
                if (0 < j) {
                    this.E.add(new VisibilityEdge(p[j - 1].vv, vv));
                }
            }
        }
        for (int i = 0; i < n - 1; i++) {
            final TVGPoint[] Pi = P[i];
            for (int j = i + 1; j < n; j++) {
                final TVGPoint[] Pj = P[j];
                final BiTangents t = Geom.tangents(Pi, Pj);
                for (final BiTangent c : t.values()) {
                    final TVGPoint source = Pi[c.t1], target = Pj[c.t2];
                    this.addEdgeIfVisible(source, target, i, j);
                }
            }
        }
    }

    public TangentVisibilityGraph(final TVGPoint[][] P, final VisibilityGraph g0) {
        super(new ArrayList<>(g0.V), new ArrayList<>(g0.E));
        this.P = P;
    }

    public void addEdgeIfVisible(TVGPoint u, TVGPoint v, int i1, int i2) {
        if (!this.intersectsPolys(new LineSegment(u.x, u.y, v.x, v.y), i1, i2)) {
            this.E.add(new VisibilityEdge(u.vv, v.vv));
        }
    }

    public VisibilityVertex addPoint(final TVGPoint p, final int i1) {
        final int n = this.P.length;
        this.V.add(new VisibilityVertex(this.V.size(), n, 0, p));
        for (int i = 0; i < n; ++i) {
            if (i == i1) {
                continue;
            }
            final TVGPoint[] poly = this.P[i];
            final LRTangent t = Geom.tangent_PointPolyC(p, poly);
            this.addEdgeIfVisible(p, poly[t.ltan], i1, i);
            this.addEdgeIfVisible(p, poly[t.rtan], i1, i);
        }
        return p.vv;
    }

    private boolean intersectsPolys(LineSegment l, int i1, int i2) {
        for (int i = 0, n = this.P.length; i < n; ++i) {
            if (i != i1 && i != i2 && 0 < Geom.intersects(l, this.P[i]).size()) {
                return true;
            }
        }
        return false;
    }
}
