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
public class RBNode<T> {

    public T         data;
    public RBNode<T> left;
    public RBNode<T> right;
    public boolean   red;

    RBNode(final T data) {
        this.data = data;
        this.left = null;
        this.right = null;
        this.red = true;
    }

    public RBNode<T> get_child(final boolean dir) {
        return dir ? this.right : this.left;
    }

    public void set_child(final boolean dir, final RBNode<T> val) {
        if (dir) {
            this.right = val;
        } else {
            this.left = val;
        }
    }
}
