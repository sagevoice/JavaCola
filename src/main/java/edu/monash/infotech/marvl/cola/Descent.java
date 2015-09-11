package edu.monash.infotech.marvl.cola;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.ToDoubleBiFunction;

/**
 * Descent respects a collection of locks over nodes that should not move
 *
 * Uses a gradient descent approach to reduce a stress or p-stress goal function over a graph with specified ideal edge lengths or a square matrix of dissimilarities.
 * The standard stress function over a graph nodes with position vectors x,y,z is (mathematica input):
 *   stress[x_,y_,z_,D_,w_]:=Sum[w[[i,j]] (length[x[[i]],y[[i]],z[[i]],x[[j]],y[[j]],z[[j]]]-d[[i,j]])^2,{i,Length[x]-1},{j,i+1,Length[x]}]
 * where: D is a square matrix of ideal separations between nodes, w is matrix of weights for those separations
 *        length[x1_, y1_, z1_, x2_, y2_, z2_] = Sqrt[(x1 - x2)^2 + (y1 - y2)^2 + (z1 - z2)^2]
 * below, we use wij = 1/(Dij^2)
 *
 */
@Slf4j
public class Descent {

    public double threshold = 0.0001;
    /** Hessian Matrix
     */
    public double[][][] H;
    /** gradient vector
     */
    public double[][]   g;
    /** positions vector
     */
    public double[][]   x;
    /**
     * dimensionality
     */
    public int          k;
    /**
     * number of data-points / nodes / size of vectors/matrices
     */
    public int          n;

    public Locks locks;

    private static final double zeroDistance = 1e-10;
    private double minD;

    // pool of arrays of size n used internally, allocated in constructor
    private double[][] Hd;
    private double[][] a;
    private double[][] b;
    private double[][] c;
    private double[][] d;
    private double[][] e;
    private double[][] ia;
    private double[][] ib;
    private double[][] xtmp;


    // Parameters for grid snap stress.
    // TODO: Make a pluggable "StressTerm" class instead of this
    // mess.
    public int     numGridSnapNodes = 0;
    public double  snapGridSize     = 100;
    public double  snapStrength     = 1000;
    public boolean scaleSnapByMaxH  = false;

    private PseudoRandom random = new PseudoRandom();

    public ArrayList<TriConsumer<double[], double[], double[]>> project = null;

    public double[][] D;
    public double[][] G;

    public Descent(final double[][] x, final double[][] D) {
        this(x, D, null);
    }

    /**
     * @param x {number[][]} initial coordinates for nodes
     * @param D {number[][]} matrix of desired distances between pairs of nodes
     * @param G {number[][]} [default=null] if specified, G is a matrix of weights for goal terms between pairs of nodes.
     * If G[i][j] > 1 and the separation between nodes i and j is greater than their ideal distance, then there is no contribution for this pair to the goal
     * If G[i][j] <= 1 then it is used as a weighting on the contribution of the variance between ideal and actual separation between i and j to the goal function
     */
    public Descent(final double[][] x, final double[][] D, final double[][] G) {
        this.x = x;
        this.D = D;
        this.G = G;
        this.k = x.length; // dimensionality
        int n = this.n = x[0].length; // number of nodes
        this.H = new double[this.k][0][0];
        this.g = new double[this.k][0];
        this.Hd = new double[this.k][0];
        this.a = new double[this.k][0];
        this.b = new double[this.k][0];
        this.c = new double[this.k][0];
        this.d = new double[this.k][0];
        this.e = new double[this.k][0];
        this.ia = new double[this.k][0];
        this.ib = new double[this.k][0];
        this.xtmp = new double[this.k][0];
        this.locks = new Locks();
        this.minD = Double.MAX_VALUE;
        int i = n, j;
        while (0 < i--) {
            j = n;
            while (--j > i) {
                double d = D[i][j];
                if (0 < d && d < this.minD) {
                    this.minD = d;
                }
            }
        }
        if (Double.MAX_VALUE == this.minD) {
            this.minD = 1;
        }
        i = this.k;
        while (0 < i--) {
            this.g[i] = new double[n];
            this.H[i] = new double[n][0];
            j = n;
            while (0 < j--) {
                this.H[i][j] = new double[n];
            }
            this.Hd[i] = new double[n];
            this.a[i] = new double[n];
            this.b[i] = new double[n];
            this.c[i] = new double[n];
            this.d[i] = new double[n];
            this.e[i] = new double[n];
            this.ia[i] = new double[n];
            this.ib[i] = new double[n];
            this.xtmp[i] = new double[n];
        }
    }

    public static double[][] createSquareMatrix(int n, ToDoubleBiFunction<Integer, Integer> f) {
        double[][] M = new double[n][0];
        for (int i = 0; i < n; ++i) {
            M[i] = new double[n];
            for (int j = 0; j < n; ++j) {
                M[i][j] = f.applyAsDouble(i, j);
            }
        }
        return M;
    }

    private double[] offsetDir() {
        double[] u = new double[this.k];
        double l = 0;
        for (int i = 0; i < this.k; ++i) {
            double x = u[i] = this.random.getNextBetween(0.01, 1) - 0.5;
            l += x * x;
        }
        final double l2 = Math.sqrt(l);
        return Arrays.stream(u).map(x -> x *= minD / l2).toArray();
    }

    // compute first and second derivative information storing results in this.g and this.H
    public void computeDerivatives(double[][] x) {
        int n = this.n;
        if (1 > n) {
            return;
        }
        int i;
        double[] d = new double[this.k];
        double[] d2 = new double[this.k];
        double[] Huu = new double[this.k];
        double maxH = 0;
        for (int u = 0; u < n; ++u) {
            for (i = 0; i < this.k; ++i) {
                Huu[i] = g[i][u] = 0;
            }
            for (int v = 0; v < n; ++v) {
                if (u == v) continue;

                // The following loop randomly displaces nodes that are at identical positions
                int maxDisplaces = n; // avoid infinite loop in the case of numerical issues, such as huge values
                double sd2 = 0;
                while (0 < maxDisplaces--) {
                    sd2 = 0;
                    for (i = 0; i < this.k; ++i) {
                        double dx = d[i] = x[i][u] - x[i][v];
                        sd2 += d2[i] = dx * dx;
                    }
                    if (1e-9 < sd2) {
                        break;
                    }
                    double[] rd = this.offsetDir();
                    for (i = 0; i < this.k; ++i) {
                        x[i][v] += rd[i];
                    }
                }
                double l = Math.sqrt(sd2);
                double D = this.D[u][v];
                double weight = null != this.G ? this.G[u][v] : 1;
                if (1 < weight && l > D || !Double.isFinite(D)) {
                    for (i = 0; i < this.k; ++i) {
                        this.H[i][u][v] = 0;
                    }
                    continue;
                }
                if (1 < weight) {
                    weight = 1;
                }
                double D2 = D * D;
                double gs = 2 * weight * (l - D) / (D2 * l);
                double l3 = l * l * l;
                double hs = 2 * -weight / (D2 * l3);
                if (!Double.isFinite(gs)) {
                    log.debug("computeDerivatives got infinite value for gs: " + gs);
                }
                for (i = 0; i < this.k; ++i) {
                    this.g[i][u] += d[i] * gs;
                    Huu[i] -= this.H[i][u][v] = hs * (l3 + D * (d2[i] - sd2) + l * sd2);
                }
            }
            for (i = 0; i < this.k; ++i) {
                maxH = Math.max(maxH, this.H[i][u][u] = Huu[i]);
            }
        }
        // Grid snap forces
        double r = this.snapGridSize/2;
        double g = this.snapGridSize;
        double w = this.snapStrength;
        double k = w / (r * r);
        int numNodes = this.numGridSnapNodes;
        for (int u = 0; u < numNodes; ++u) {
            for (i = 0; i < this.k; ++i) {
                double xiu = this.x[i][u];
                double m = xiu / g;
                double f = m % 1;
                double q = m - f;
                double a = Math.abs(f);
                double dx = (0.5 >= a) ? xiu - q * g :
                    (0 < xiu) ? xiu - (q + 1) * g : xiu - (q - 1) * g;
                if (-r < dx && dx <= r) {
                    if (this.scaleSnapByMaxH) {
                        this.g[i][u] += maxH * k * dx;
                        this.H[i][u][u] += maxH * k;
                    } else {
                        this.g[i][u] += k * dx;
                        this.H[i][u][u] += k;
                    }
                }
            }
        }
        final double _maxH = maxH;
        if (!this.locks.isEmpty()) {
            this.locks.apply((u, p) -> {
                for (int j = 0; j < this.k; ++j) {
                    this.H[j][u][u] += _maxH;
                    this.g[j][u] -= _maxH * (p[j] - x[j][u]);
                }
            });
        }
    }

    private static double dotProd(double[] a, double[] b) {
        double x = 0;
        int i = a.length;
        while (0 < i--) {
            x += a[i] * b[i];
        }
        return x;
    }

    // result r = matrix m * vector v
    private static void rightMultiply(double[][] m, double[] v, double[] r) {
        int i = m.length;
        while (0 < i--) {
            r[i] = Descent.dotProd(m[i], v);
        }
    }

    // computes the optimal step size to take in direction d using the
    // derivative information in this.g and this.H
    // returns the scalar multiplier to apply to d to get the optimal step
    public double computeStepSize(double[][] d) {
        double numerator = 0, denominator = 0;
        for (int i = 0; i < this.k; ++i) {
            numerator += Descent.dotProd(this.g[i], d[i]);
            Descent.rightMultiply(this.H[i], d[i], this.Hd[i]);
            denominator += Descent.dotProd(d[i], this.Hd[i]);
        }
        if (0 == denominator || !Double.isFinite(denominator)) {
            return 0;
        }
        return numerator / denominator;
    }

    public double reduceStress() {
        this.computeDerivatives(this.x);
        final double alpha = this.computeStepSize(this.g);
        for (int i = 0; i < this.k; ++i) {
            this.takeDescentStep(this.x[i], this.g[i], alpha);
        }
        return this.computeStress();
    }

    private static void copy(double[][] a, double[][] b) {
        final int m = a.length;
        for (int i = 0; i < m; ++i) {
            System.arraycopy(a[i], 0, b[i], 0, a[i].length);
        }
    }

    // takes a step of stepSize * d from x0, and then project against any constraints.
    // result is returned in r.
    // x0: starting positions
    // r: result positions will be returned here
    // d: unconstrained descent vector
    // stepSize: amount to step along d
    private void stepAndProject(double[][] x0, double[][] r, double[][] d, double stepSize) {
        Descent.copy(x0, r);
        this.takeDescentStep(r[0], d[0], stepSize);
        if (null != this.project) {
            this.project.get(0).accept(x0[0], x0[1], r[0]);
        }
        this.takeDescentStep(r[1], d[1], stepSize);
        if (null != this.project) {
            this.project.get(1).accept(r[0], x0[1], r[1]);
        }

        // todo: allow projection against constraints in higher dimensions
        for (int i = 2; i < this.k; i++) {
            this.takeDescentStep(r[i], d[i], stepSize);
        }
    }

    private static void mApply(final int m, final int n, final BiConsumer<Integer, Integer> f) {
        int i = m;
        while (0 < i--) {
            int j = n;
            while (0 < j--) {
                f.accept(i, j);
            }
        }
    }

    private void matrixApply(final BiConsumer<Integer, Integer> f) {
        Descent.mApply(this.k, this.n, f);
    }

    private void computeNextPosition(double[][] x0, double[][] r) {
        this.computeDerivatives(x0);
        double alpha = this.computeStepSize(this.g);
        this.stepAndProject(x0, r, this.g, alpha);
        if (null != this.project) {
            this.matrixApply((i, j) -> this.e[i][j] = x0[i][j] - r[i][j]);
            double beta = this.computeStepSize(this.e);
            beta = Math.max(0.2, Math.min(beta, 1));
            this.stepAndProject(x0, r, this.e, beta);
        }
    }

    public double run(int iterations) {
        double stress = Double.MAX_VALUE;
        boolean converged = false;
        while (!converged && 0 < iterations--) {
            double s = this.rungeKutta();
            converged = Math.abs(stress / s - 1) < this.threshold;
            stress = s;
        }
        return stress;
    }

    public double rungeKutta() {
        this.computeNextPosition(this.x, this.a);
        Descent.mid(this.x, this.a, this.ia);
        this.computeNextPosition(this.ia, this.b);
        Descent.mid(this.x, this.b, this.ib);
        this.computeNextPosition(this.ib, this.c);
        this.computeNextPosition(this.c, this.d);
        double disp = 0;
        int i = this.k;
        while (0 < i--) {
            int j = this.n;
            while (0 < j--) {
                double x = (this.a[i][j] + 2.0 * this.b[i][j] + 2.0 * this.c[i][j] + this.d[i][j]) / 6.0,
                    d = this.x[i][j] - x;
                disp += d * d;
                this.x[i][j] = x;
            }
        }
        return disp;
    }

    private static void mid(final double[][] a, final double[][] b, final double[][] m) {
        Descent.mApply(a.length, a[0].length, (i, j) ->
            m[i][j] = a[i][j] + (b[i][j] - a[i][j]) / 2.0);
    }

    public void takeDescentStep(final double[] x, final double[] d, final double stepSize) {
        for (int i = 0; i < this.n; ++i) {
            x[i] -= stepSize * d[i];
        }
    }

    public double computeStress() {
        double stress = 0;
        final int nMinus1 = this.n - 1;
        for (int u = 0; u < nMinus1; ++u) {
            final int n = this.n;
            for (int v = u + 1; v < n; ++v) {
                double l = 0;
                for (int i = 0; i < this.k; ++i) {
                    double dx = this.x[i][u] - this.x[i][v];
                    l += dx * dx;
                }
                l = Math.sqrt(l);
                final double d = this.D[u][v];
                if (!Double.isFinite(d)) {
                    continue;
                }
                final double rl = d - l;
                final double d2 = d * d;
                stress += rl * rl / d2;
            }
        }
        return stress;
    }
}
