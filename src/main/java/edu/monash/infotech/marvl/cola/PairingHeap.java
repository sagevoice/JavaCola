package edu.monash.infotech.marvl.cola;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

// from: https://gist.github.com/nervoussystem
public class PairingHeap<T> {

    private List<PairingHeap<T>> subheaps;
    public  T                    elem;

    PairingHeap(final T elem) {
        this.elem = elem;
        this.subheaps = new ArrayList<>();
    }

    public String toString() {
        String str = "";
        boolean needComma = false;
        for (int i = 0; i < this.subheaps.size(); ++i) {
            PairingHeap<T> subheap = this.subheaps.get(i);
            if (null == subheap.elem) {
                needComma = false;
                continue;
            }
            if (needComma) {
                str += ",";
            }
            str += subheap.toString();
            needComma = true;
        }
        if (!str.isEmpty()) {
            str = "(" + str + ")";
        }
        return ((null != this.elem) ? this.elem.toString() : "") + str;
    }

    public void forEach(final BiConsumer<T, PairingHeap<T>> f) {
        if (!this.empty()) {
            f.accept(this.elem, this);
            this.subheaps.forEach(s -> s.forEach(f));
        }
    }

    public int count() {
        int n = 0;
        if (!this.empty()) {
            n += 1;
            for (final PairingHeap<T> h : this.subheaps) {
                n += h.count();
            }
        }
        return n;
    }

    public T min() {
        return this.elem;
    }

    public boolean empty() {
        return (null == this.elem);
    }

    public boolean contains(final PairingHeap<T> h) {
        if (this == h) {
            return true;
        }
        for (int i = 0; i < this.subheaps.size(); i++) {
            if (this.subheaps.get(i).contains(h)) {
                return true;
            }
        }
        return false;
    }

    public boolean isHeap(final LessThan<T> lessThan) {
        return this.subheaps.stream().allMatch(h -> lessThan.compare(this.elem, h.elem) && h.isHeap(lessThan));
    }

    public PairingHeap<T> insert(final T obj, final LessThan<T> lessThan) {
        return this.merge(new PairingHeap<>(obj), lessThan);
    }

    public PairingHeap<T> merge(final PairingHeap<T> heap2, final LessThan<T> lessThan) {
        if (this.empty()) {
            return heap2;
        } else if (heap2.empty()) {
            return this;
        } else if (lessThan.compare(this.elem, heap2.elem)) {
            this.subheaps.add(heap2);
            return this;
        } else {
            heap2.subheaps.add(this);
            return heap2;
        }
    }

    public PairingHeap<T> removeMin(final LessThan<T> lessThan) {
        if (this.empty()) {
            return null;
        } else {
            return this.mergePairs(lessThan);
        }
    }

    public PairingHeap<T> mergePairs(final LessThan<T> lessThan)  {
        if (0 == this.subheaps.size()) {
            return new PairingHeap<>(null);
        } else if (1 == this.subheaps.size()) {
            return this.subheaps.get(0);
        } else {
            final PairingHeap<T> firstPair = this.subheaps.remove(this.subheaps.size() - 1).merge(this.subheaps.remove(this.subheaps.size() - 1), lessThan);
            final PairingHeap<T> remaining = this.mergePairs(lessThan);
            return firstPair.merge(remaining, lessThan);
        }
    }

    public PairingHeap<T> decreaseKey(final PairingHeap<T> subheap, final T newValue, final BiConsumer<T, PairingHeap<T>> setHeapNode, final LessThan<T> lessThan) {
        final PairingHeap<T> newHeap = subheap.removeMin(lessThan);
        //reassign subheap values to preserve tree
        subheap.elem = newHeap.elem;
        subheap.subheaps = newHeap.subheaps;
        if (null != setHeapNode && null != newHeap.elem) {
            setHeapNode.accept(subheap.elem, subheap);
        }
        final PairingHeap<T> pairingNode = new PairingHeap<>(newValue);
        if (null != setHeapNode) {
            setHeapNode.accept(newValue, pairingNode);
        }
        return this.merge(pairingNode, lessThan);
    }
}
