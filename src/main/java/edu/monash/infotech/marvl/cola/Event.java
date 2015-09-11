package edu.monash.infotech.marvl.cola;

public class Event {

    public EventType type;
    public double    alpha;
    public double    stress;

    public Event(final EventType type, final double alpha) {
        this.type = type;
        this.alpha = alpha;
    }

    public Event(final EventType type, final double alpha, final double stress) {
        this.type = type;
        this.alpha = alpha;
        this.stress = stress;
    }
}
