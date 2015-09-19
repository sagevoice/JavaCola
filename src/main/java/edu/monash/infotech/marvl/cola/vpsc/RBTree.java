package edu.monash.infotech.marvl.cola.vpsc;

//Based on js_es:
//
//https://github.com/vadimg/js_bintrees
//
//Copyright (C) 2011 by Vadim Graboys
//
//Permission is hereby granted, free of charge, to any person obtaining a copy
//of this software and associated documentation files (the "Software"), to deal
//in the Software without restriction, including without limitation the rights
//to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
//copies of the Software, and to permit persons to whom the Software is
//furnished to do so, subject to the following conditions:
//
//The above copyright notice and this permission notice shall be included in
//all copies or substantial portions of the Software.
//
//THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
//IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
//FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
//AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
//LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
//OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
//THE SOFTWARE.

import java.util.Comparator;

public class RBTree<T> extends TreeBase<T> {

    public RBTree(final Comparator<T> comparator) {
        super(comparator);
    }

    // returns true if inserted, false if duplicate
    public boolean insert(final T data) {
        boolean ret = false;

        if (null == this._root) {
            // empty tree
            this._root = new RBNode<>(data);
            ret = true;
            this.size++;
        } else {
            final RBNode<T> head = new RBNode<>(null); // fake tree root

            boolean dir = false;
            boolean last = false;

            // setup
            RBNode<T> gp = null; // grandparent
            RBNode<T> ggp = head; // grand-grand-parent
            RBNode<T> p = null; // parent
            RBNode<T> node = this._root;
            ggp.right = this._root;

            // search down
            while (true) {
                if (null == node) {
                    // insert new node at the bottom
                    node = new RBNode<>(data);
                    p.set_child(dir, node);
                    ret = true;
                    this.size++;
                } else if (this.is_red(node.left) && this.is_red(node.right)) {
                    // color flip
                    node.red = true;
                    node.left.red = false;
                    node.right.red = false;
                }

                // fix red violation
                if (this.is_red(node) && this.is_red(p)) {
                    final boolean dir2 = ggp.right.equals(gp);

                    if (node.equals(p.get_child(last))) {
                        ggp.set_child(dir2, this.single_rotate(gp, !last));
                    } else {
                        ggp.set_child(dir2, this.double_rotate(gp, !last));
                    }
                }

                int cmp = this._comparator.compare(node.data, data);

                // stop if found
                if (cmp == 0) {
                    break;
                }

                last = dir;
                dir = 0 > cmp;

                // update helpers
                if (gp != null) {
                    ggp = gp;
                }
                gp = p;
                p = node;
                node = node.get_child(dir);
            }

            // update root
            this._root = head.right;
        }

        // make root black
        this._root.red = false;

        return ret;
    }

    // returns true if removed, false if not found
    public boolean remove(T data) {
        if (null == this._root) {
            return false;
        }

        RBNode<T> head = new RBNode<>(null); // fake tree root
        RBNode<T> node = head;
        node.right = this._root;
        RBNode<T> p = null; // parent
        RBNode<T> gp = null; // grand parent
        RBNode<T> found = null; // found item
        boolean dir = true;

        while (node.get_child(dir) != null) {
            boolean last = dir;

            // update helpers
            gp = p;
            p = node;
            node = node.get_child(dir);

            int cmp = this._comparator.compare(data, node.data);

            dir = 0 < cmp;

            // save found node
            if (cmp == 0) {
                found = node;
            }

            // push the red node down
            if (!this.is_red(node) && !this.is_red(node.get_child(dir))) {
                if (this.is_red(node.get_child(!dir))) {
                    RBNode<T> sr = this.single_rotate(node, dir);
                    p.set_child(last, sr);
                    p = sr;
                } else if (!this.is_red(node.get_child(!dir))) {
                    RBNode<T> sibling = p.get_child(!last);
                    if (null != sibling) {
                        if (!this.is_red(sibling.get_child(!last)) && !this.is_red(sibling.get_child(last))) {
                            // color flip
                            p.red = false;
                            sibling.red = true;
                            node.red = true;
                        } else {
                            boolean dir2 = p.equals(gp.right);

                            if (this.is_red(sibling.get_child(last))) {
                                gp.set_child(dir2, this.double_rotate(p, last));
                            } else if (this.is_red(sibling.get_child(!last))) {
                                gp.set_child(dir2, this.single_rotate(p, last));
                            }

                            // ensure correct coloring
                            RBNode<T> gpc = gp.get_child(dir2);
                            gpc.red = true;
                            node.red = true;
                            gpc.left.red = false;
                            gpc.right.red = false;
                        }
                    }
                }
            }
        }

        // replace and remove if found
        if (null != found) {
            found.data = node.data;
            p.set_child(node.equals(p.right), node.get_child(node.left == null));
            this.size--;
        }

        // update root and make it black
        this._root = head.right;
        if (this._root != null) {
            this._root.red = false;
        }

        return found != null;
    }

    public boolean is_red(final RBNode<T> node) {
        return null != node && node.red;
    }

    public RBNode<T> single_rotate(RBNode<T> root, boolean dir) {
        RBNode<T> save = root.get_child(!dir);

        root.set_child(!dir, save.get_child(dir));
        save.set_child(dir, root);

        root.red = true;
        save.red = false;

        return save;
    }

    public RBNode<T> double_rotate(RBNode<T> root, boolean dir) {
        root.set_child(!dir, this.single_rotate(root.get_child(!dir), !dir));
        return this.single_rotate(root, dir);
    }
}
