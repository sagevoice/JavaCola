package edu.monash.infotech.marvl.cola;

import java.util.function.BiFunction;
import java.util.function.Consumer;

public class Options {

    public Consumer<Event> trigger;
    public VoidConsumer    kick;
    public BiFunction<EventType, Consumer<Event>, Layout> on;

}
