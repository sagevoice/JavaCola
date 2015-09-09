package edu.monash.infotech.marvl.cola;

import java.util.function.BiConsumer;

/** a min priority queue backed by a pairing heap */
public class PriorityQueue<T> {

    private PairingHeap<T> root;
    private LessThan<T>    lessThan;

    public PriorityQueue(final LessThan<T> lessThan) {
        this.lessThan = lessThan;
    }

    /** @return the top element (the min element as defined by lessThan) */
    public T top() {
        if (this.empty()) {
            return null;
        }
        return this.root.elem;
    }

    /** put things on the heap */
    public PairingHeap<T> push(final T... args) {
        PairingHeap<T> pairingNode = null;
        for (final T arg : args) {
            pairingNode = new PairingHeap<>(arg);
            this.root = this.empty() ?
                        pairingNode : this.root.merge(pairingNode, this.lessThan);
        }
        return pairingNode;
    }

    /** @return true if no more elements in queue */
    public boolean empty() {
        return (null == this.root) || (null == this.root.elem);
    }

    /**
     * check heap condition (for testing)
     *
     * @return true if queue is in valid state
     */
    public boolean isHeap() {
        return this.root.isHeap(this.lessThan);
    }

    /**
     * apply f to each element of the queue
     *
     * @param f function to apply
     */
    public void forEach(final BiConsumer<T, PairingHeap<T>> f) {
        this.root.forEach(f);
    }

    /** remove and return the min element from the queue */
    public T pop() {
        if (this.empty()) {
            return null;
        }
        final T obj = this.root.min();
        this.root = this.root.removeMin(this.lessThan);
        return obj;
    }

    public void reduceKey(final PairingHeap<T> heapNode, final T newKey) {
        this.reduceKey(heapNode, newKey, null);
    }

    /** reduce the key value of the specified heap node */
    public void reduceKey(final PairingHeap<T> heapNode, final T newKey, final BiConsumer<T, PairingHeap<T>> setHeapNode) {
        this.root = this.root.decreaseKey(heapNode, newKey, setHeapNode, this.lessThan);
    }

    public String toString() {
        return this.root.toString();
    }

    /** @return number of elements in queue */
    public int count() {
        return this.root.count();
    }
}