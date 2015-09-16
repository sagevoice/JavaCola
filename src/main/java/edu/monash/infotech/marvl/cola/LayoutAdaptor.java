package edu.monash.infotech.marvl.cola;

import java.util.function.BiFunction;
import java.util.function.Consumer;

public class LayoutAdaptor extends Layout {

    public Consumer<Event>                                triggerCallback;
    public VoidConsumer                                   kickCallback;
    public BiFunction<EventType, Consumer<Event>, Layout> onCallback;

    public LayoutAdaptor(final Options options) {
        triggerCallback = options.trigger;
        kickCallback = options.kick;
        onCallback = options.on;
    }

    @Override
    protected void trigger(final Event e) {
        if (null != triggerCallback) {
            triggerCallback.accept(e);
        } else {
            super.trigger(e);
        }
    }

    @Override
    protected void kick() {
        if (null != kickCallback) {
            kickCallback.accept();
        } else {
            super.kick();
        }
    }

    @Override
    public Layout on(final EventType e, final Consumer<Event> listener) {
        if (null != onCallback) {
            return onCallback.apply(e, listener);
        } else {
            return super.on(e, listener);
        }
    }

}
