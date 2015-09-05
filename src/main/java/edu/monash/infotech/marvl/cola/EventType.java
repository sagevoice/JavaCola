package edu.monash.infotech.marvl.cola;

/**
 * The layout process fires three events:
 *  - start: layout iterations started
 *  - tick: fired once per iteration, listen to this to animate
 *  - end: layout converged, you might like to zoom-to-fit or something at notification of this event
 */
public enum EventType {
    start,
    tick,
    end;

}
