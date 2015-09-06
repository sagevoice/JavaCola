package edu.monash.infotech.marvl.cola.vpsc;

public interface RectAccessors {

    double getCentre(Rectangle r);

    double getOpen(Rectangle r);

    double getClose(Rectangle r);

    double getSize(Rectangle r);

    Rectangle makeRect(double open, double close, double center, double size);

    void findNeighbours(Node v, RBTree<Node> scanline);
}
