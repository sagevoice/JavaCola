package edu.monash.infotech.marvl.cola.geom;

public class TangentVisibilityGraph extends VisibilityGraph {
    public TVGPoint[][] P;

    TangentVisibilityGraph(final TVGPoint[][] P) {
        this.P = P;
        int n = P.length;
        for (int i = 0; i < n; i++) {
            TVGPoint[] p = P[i];
            for (int j = 0; j < p.length; ++j) {
                TVGPoint pj = p[j];
                VisibilityVertex vv = new VisibilityVertex(this.V.length, i, j, pj);
                this.V.push(vv);
                if (0 < j) {
                    this.E.push(new VisibilityEdge(p[j - 1].vv, vv));
                }
            }
        }
        for (int i = 0; i < n - 1; i++) {
            TVGPoint[] Pi = P[i];
            for (int j = i + 1; j < n; j++) {
                TVGPoint[] Pj = P[j];
                BiTangents t = Geom.tangents(Pi, Pj);
                for (var q in t) {
                    BiTangent c = t[q];
                    TVGPoint source = Pi[c.t1], target = Pj[c.t2];
                    this.addEdgeIfVisible(source, target, i, j);
                }
            }
        }
    }

    TangentVisibilityGraph(final TVGPoint[][] P, final VisibilityGraph g0) {
        this.P = P;
        this.V = g0.V.clone();
        this.E = g0.E.clone();
    }

    public void addEdgeIfVisible(TVGPoint u, TVGPoint v, int i1, int i2) {
        if (!this.intersectsPolys(new LineSegment(u.x, u.y, v.x, v.y), i1, i2)) {
            this.E.push(new VisibilityEdge(u.vv, v.vv));
        }
    }

    public VisibilityVertex addPoint(TVGPoint p, int i1) {
        int n = this.P.length;
        this.V.push(new VisibilityVertex(this.V.length, n, 0, p));
        for (int i = 0; i < n; ++i) {
            if (i == i1) continue;
            TVGPoint[] poly = this.P[i];
            LRTangent t = Geom.tangent_PointPolyC(p, poly);
            this.addEdgeIfVisible(p, poly[t.ltan], i1, i);
            this.addEdgeIfVisible(p, poly[t.rtan], i1, i);
        }
        return p.vv;
    }

    private boolean intersectsPolys(LineSegment l, int i1, int i2) {
        for (int i = 0, n = this.P.length; i < n; ++i) {
            if (i != i1 && i != i2 && Geom.intersects(l, this.P[i]).length > 0) {
                return true;
            }
        }
        return false;
    }
}
