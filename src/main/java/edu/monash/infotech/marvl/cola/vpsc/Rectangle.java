package edu.monash.infotech.marvl.cola.vpsc;


import edu.monash.infotech.marvl.cola.geom.Point;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
public class Rectangle {

    public double x;
    public double X;
    public double y;
    public double Y;

    public static Rectangle empty() {
        return new Rectangle(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
    }

    public double cx() {
        return (this.x + this.X) / 2;
    }

    public double cy() {
        return (this.y + this.Y) / 2;
    }

    public double overlapX(final Rectangle r) {
        final double ux = this.cx(), vx = r.cx();
        if (ux <= vx && r.x < this.X) {
            return this.X - r.x;
        }
        if (vx <= ux && this.x < r.X) {
            return r.X - this.x;
        }
        return 0;
    }

    public double overlapY(final Rectangle r) {
        final double uy = this.cy(), vy = r.cy();
        if (uy <= vy && r.y < this.Y) {
            return this.Y - r.y;
        }
        if (vy <= uy && this.y < r.Y) {
            return r.Y - this.y;
        }
        return 0;
    }

    public void setXCentre(final double cx) {
        final double dx = cx - this.cx();
        this.x += dx;
        this.X += dx;
    }

    public void setYCentre(final double cy) {
        final double dy = cy - this.cy();
        this.y += dy;
        this.Y += dy;
    }

    public double width() {
        return this.X - this.x;
    }

    public double height() {
        return this.Y - this.y;
    }

    public Rectangle union(final Rectangle r) {
        return new Rectangle(Math.min(this.x, r.x), Math.max(this.X, r.X), Math.min(this.y, r.y), Math.max(this.Y, r.Y));
    }

    /**
     * return any intersection points between the given line and the sides of this rectangle
     *
     * @param x1 number first x coord of line
     * @param y1 number first y coord of line
     * @param x2 number second x coord of line
     * @param y2 number second y coord of line
     * @return any intersection points found
     */
    public List<Point> lineIntersections(final double x1, final double y1, final double x2, final double y2) {
        final double[][] sides = {{this.x, this.y, this.X, this.y},
                            {this.X, this.y, this.X, this.Y},
                            {this.X, this.Y, this.x, this.Y},
                            {this.x, this.Y, this.x, this.y}};
        final List<Point> intersections = new ArrayList<>();
        for (int i = 0; 4 > i; ++i) {
            final Point r = Rectangle.lineIntersection(x1, y1, x2, y2, sides[i][0], sides[i][1], sides[i][2], sides[i][3]);
            if (null != r) {
                intersections.add(r);
            }
        }
        return intersections;
    }

    /**
     * return any intersection points between a line extending from the centre of this rectangle to the given point, and the sides of this
     * rectangle
     *
     * @param x2 number second x coord of line
     * @param y2 number second y coord of line
     * @return any intersection points found
     *
     * @method lineIntersection
     */
    public Point rayIntersection(final double x2, final double y2) {
        final List<Point> ints = this.lineIntersections(this.cx(), this.cy(), x2, y2);
        return 0 < ints.size() ? ints.get(0) : null;
    }

    public Point[] vertices() {
        return new Point[] {
                new Point(this.x, this.y),
                new Point(this.X, this.y),
                new Point(this.X, this.Y),
                new Point(this.x, this.Y),
                new Point(this.x, this.y)};
    }

    public static Point lineIntersection(
            final double x1, final double y1,
            final double x2, final double y2,
            final double x3, final double y3,
            final double x4, final double y4)
    {
        final double dx12 = x2 - x1, dx34 = x4 - x3,
                dy12 = y2 - y1, dy34 = y4 - y3,
                denominator = dy34 * dx12 - dx34 * dy12;
        if (0 == denominator) {
            return null;
        }
        final double dx31 = x1 - x3, dy31 = y1 - y3,
                numa = dx34 * dy31 - dy34 * dx31,
                a = numa / denominator,
                numb = dx12 * dy31 - dy12 * dx31,
                b = numb / denominator;
        if (0 <= a && 1 >= a && 0 <= b && 1 >= b) {
            return new Point(x1 + a * dx12, y1 + a * dy12);
        }
        return null;
    }

    public Rectangle inflate(final double pad) {
        return new Rectangle(this.x - pad, this.X + pad, this.y - pad, this.Y + pad);
    }
}

