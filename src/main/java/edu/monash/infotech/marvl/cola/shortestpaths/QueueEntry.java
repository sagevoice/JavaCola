package edu.monash.infotech.marvl.cola.shortestpaths;

import lombok.AllArgsConstructor;

@AllArgsConstructor
class QueueEntry {

    public Node       node;
    public QueueEntry prev;
    public double     d;
}
