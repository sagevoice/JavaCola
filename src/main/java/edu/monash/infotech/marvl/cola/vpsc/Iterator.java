package edu.monash.infotech.marvl.cola.vpsc;

import java.util.ArrayList;
import java.util.List;

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
public class Iterator<T> {

    public TreeBase<T>     _tree;
    public List<RBNode<T>> _ancestors;
    public RBNode<T>       _cursor;

    public Iterator(final TreeBase<T> tree) {
        this._tree = tree;
        this._ancestors = new ArrayList<>();
        this._cursor = null;
    }

    public T data() {
        return (null != this._cursor) ? this._cursor.data : null;
    }

    // if null-iterator, returns first node
    // otherwise, returns next node
    T next() {
        if (null == this._cursor) {
            RBNode<T> root = this._tree._root;
            if (null != root) {
                this._minNode(root);
            }
        } else {
            if (null == this._cursor.right) {
                // no greater node in subtree, go up to parent
                // if coming from a right child, continue up the stack
                RBNode<T> save;
                do {
                    save = this._cursor;
                    if (0 < this._ancestors.size()) {
                        this._cursor = this._ancestors.remove(this._ancestors.size() - 1);
                    } else {
                        this._cursor = null;
                        break;
                    }
                } while (save.equals(this._cursor.right));
            } else {
                // get the next node from the subtree
                this._ancestors.add(this._cursor);
                this._minNode(this._cursor.right);
            }
        }
        return (null != this._cursor) ? this._cursor.data : null;
    }

    // if null-iterator, returns last node
    // otherwise, returns previous node
    public T prev() {
        if (null == this._cursor) {
            RBNode<T> root = this._tree._root;
            if (null != root) {
                this._maxNode(root);
            }
        } else {
            if (null == this._cursor.left) {
                RBNode<T> save;
                do {
                    save = this._cursor;
                    if (0 < this._ancestors.size()) {
                        this._cursor = this._ancestors.remove(this._ancestors.size() - 1);
                    } else {
                        this._cursor = null;
                        break;
                    }
                } while (save.equals(this._cursor.left));
            } else {
                this._ancestors.add(this._cursor);
                this._maxNode(this._cursor.left);
            }
        }
        return (null != this._cursor) ? this._cursor.data : null;
    }

    public void _minNode(RBNode<T> start) {
        while (null != start.left) {
            this._ancestors.add(start);
            start = start.left;
        }
        this._cursor = start;
    }

    public void _maxNode(RBNode<T> start) {
        while (null != start.right) {
            this._ancestors.add(start);
            start = start.right;
        }
        this._cursor = start;
    }
}
