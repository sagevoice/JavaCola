package edu.monash.infotech.marvl.cola.powergraph;

import edu.monash.infotech.marvl.cola.vpsc.ValueHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class LinkSets {

    public Map<Integer, ModuleSet> sets = new HashMap<>();
    public int                     n    = 0;

    public int count() {
        return this.n;
    }

    public boolean contains(final int id) {
        final ValueHolder<Boolean> valueHolder = new ValueHolder<>(false);
        this.forAllModules((m, value) -> {
            final ValueHolder<Boolean> result = value;
            if (!result.get() && m.id == id) {
                result.set(true);
            }
        }, valueHolder);
        return valueHolder.get();
    }

    public void add(final int linktype, final Module m) {
        if (!this.sets.containsKey(linktype)) {
            this.sets.put(linktype, new ModuleSet());
        }
        final ModuleSet s = this.sets.get(linktype);
        s.add(m);
        ++this.n;
    }

    public void remove(final int linktype, final Module m) {
        final ModuleSet ms = this.sets.get(linktype);
        ms.remove(m);
        if (0 == ms.count()) {
            this.sets.remove(linktype);
        }
        --this.n;
    }

    public void forAll(final BiConsumer<ModuleSet, Integer> f) {
        for (final Integer linktype : this.sets.keySet()) {
            f.accept(this.sets.get(linktype), linktype);
        }
    }

    public void forAllModules(final BiConsumer<Module, ValueHolder> f, final ValueHolder value) {
        this.forAll((ms, lt) -> ms.forAll(f, value));
    }

    public LinkSets intersection(final LinkSets other) {
        final LinkSets result = new LinkSets();
        this.forAll((ms, lt) -> {
            if (other.sets.containsKey(lt)) {
                final ModuleSet i = ms.intersection(other.sets.get(lt));
                final int count = i.count();
                if (0 < count) {
                    result.sets.put(lt, i);
                    result.n += count;
                }
            }
        });
        return result;
    }
}
