package edu.monash.infotech.marvl.cola.vpsc;

public class Node {

    public RBTree<Node> prev;
    public RBTree<Node> next;
    public Variable     v;
    public Rectangle    r;
    public double       pos;

    Node(final Variable v, final Rectangle r, final double pos) {
        this.prev = VPSC.makeRBTree();
        this.next = VPSC.makeRBTree();
        this.v = v;
        this.r = r;
        this.pos = pos;
    }
}
