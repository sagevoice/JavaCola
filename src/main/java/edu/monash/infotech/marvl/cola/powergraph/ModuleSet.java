package edu.monash.infotech.marvl.cola.powergraph;

import edu.monash.infotech.marvl.cola.vpsc.ValueHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class ModuleSet {

    public Map<Integer, Module> table;

    public ModuleSet() {
        this.table = new HashMap<>();
    }

    public int count() {
        return this.table.keySet().size();
    }

    private Map<Integer, Module> intersection(final Map<Integer, Module> m, final Map<Integer, Module> n) {
        final Map<Integer, Module> i = new HashMap<>();
        for (final Integer v : m.keySet()) {
            if (n.containsKey(v)) {
                i.put(v, m.get(v));
            }
        }
        return i;
    }

    public ModuleSet intersection(final ModuleSet other) {
        final ModuleSet result = new ModuleSet();
        result.table = intersection(this.table, other.table);
        return result;
    }

    public int intersectionCount(final ModuleSet other) {
        return this.intersection(other).count();
    }

    public boolean contains(final Integer id) {
        return this.table.containsKey(id);
    }

    public void add(final Module m) {
        this.table.put(m.id, m);
    }

    public void remove(final Module m) {
        this.table.remove(m.id);
    }

    public void forAll(final BiConsumer<Module, ValueHolder> f, final ValueHolder value) {
        for (final Integer mid : this.table.keySet()) {
            f.accept(this.table.get(mid), value);
        }
    }

    public List<Module> modules() {
        final List<Module> vs = new ArrayList<>();
        this.forAll((m, value) -> {
            if (!m.isPredefined()) {
                vs.add(m);
            }
        }, null);
        return vs;
    }
}
