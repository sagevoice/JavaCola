package edu.monash.infotech.marvl.cola.vpsc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.Consumer;

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
public class TreeBase<T> {

    protected RBNode<T>  _root;
    protected int        size;
    protected Comparator<T> _comparator;

    TreeBase(final Comparator<T> comparator) {
        this._root = null;
        this.size = 0;
        this._comparator = comparator;
    }

    // removes all nodes from the tree
    public void clear() {
        this._root = null;
        this.size = 0;
    }

    // returns node data if found, null otherwise
    public RBNode<T> find(final T data) {
        RBNode<T> res = this._root;

        while (null != res) {
            int c = this._comparator.compare(data, res.data);
            if (0 == c) {
                return res;
            } else {
                res = res.get_child(0 < c);
            }
        }

        return null;
    }

    // returns iterator to node if found, null otherwise
    public Iterator<T> findIter(T data) {
        RBNode<T> res = this._root;
        final Iterator<T> iter = this.iterator();

        while (null != res) {
            final int c = this._comparator.compare(data, res.data);
            if (0 == c) {
                iter._cursor = res;
                return iter;
            } else {
                iter._ancestors.add(res);
                res = res.get_child(0 < c);
            }
        }

        return null;
    }

    // Returns an interator to the tree node immediately before (or at) the element
    public Iterator<T> lowerBound(final T data) {
        return this._bound(data, this._comparator);
    }

    // Returns an interator to the tree node immediately after (or at) the element
    public Iterator<T> upperBound(final T data) {

        final Comparator<T> reverse_cmp = (a, b) -> {
            return this._comparator.compare(b, a);
        };

        return this._bound(data, reverse_cmp);
    }

    // returns null if tree is empty
    public T min() {
        RBNode<T> res = this._root;
        if (null == res) {
            return null;
        }

        while (null != res.left) {
            res = res.left;
        }

        return res.data;
    }

    // returns null if tree is empty
    public T max() {
        RBNode<T> res = this._root;
        if (null == res) {
            return null;
        }

        while (null != res.right) {
            res = res.right;
        }

        return res.data;
    }

    // returns a null iterator
    // call next() or prev() to point to an element
    public Iterator<T> iterator() {
        return new Iterator<>(this);
    }

    // calls cb on each node's data, in order
    public void each(Consumer<T> cb) {
        final Iterator<T> it = this.iterator();
        T data;
        while (null != (data = it.next())) {
            cb.accept(data);
        }
    }

    // calls cb on each node's data, in reverse order
    public void reach(final Consumer<T> cb) {
        final Iterator<T> it = this.iterator();
        T data;
        while (null != (data = it.prev())) {
            cb.accept(data);
        }
    }

    // used for lowerBound and upperBound
    private Iterator<T> _bound(final T data, final Comparator<T> cmp) {
        RBNode<T> cur = this._root;
        final Iterator<T> iter = this.iterator();

        while (null != cur) {
            final int c = this._comparator.compare(data, cur.data);
            if (0 == c) {
                iter._cursor = cur;
                return iter;
            }
            iter._ancestors.add(cur);
            cur = cur.get_child(0 < c);
        }

        for (int i = iter._ancestors.size() - 1; 0 <= i; --i) {
            cur = iter._ancestors.get(i);
            if (0 < cmp.compare(data, cur.data)) {
                iter._cursor = cur;
                iter._ancestors = new ArrayList<>(iter._ancestors.subList(0, i));
                return iter;
            }
        }

        iter._ancestors.clear();
        return iter;
    }
}
