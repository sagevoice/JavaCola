package edu.monash.infotech.marvl.cola.powergraph;

import java.util.List;

public class Module {

    public Integer    gid;
    public int        id;
    public LinkSets   outgoing;
    public LinkSets   incoming;
    public ModuleSet  children;
    public Definition definition;

    public Module(final int id) {
        this(id, null, null, null);
    }

    public Module(final int id, final LinkSets outgoing, final LinkSets incoming, final ModuleSet children) {
        this(id, outgoing, incoming, children, null);
    }

    public Module(final int id, final LinkSets outgoing, final LinkSets incoming, final ModuleSet children, final Definition definition) {
        this.id = id;
        this.outgoing = null == outgoing ? new LinkSets() : outgoing;
        this.incoming = null == incoming ? new LinkSets() : incoming;
        this.children = null == children ? new ModuleSet() : children;
        this.definition = definition;
    }

    public void getEdges(final List<PowerEdge> es) {
        this.outgoing.forAll((ms, edgetype) -> {
            ms.forAll((target, value) -> {
                es.add(new PowerEdge(this.id, target.id, edgetype));
            }, null);
        });
    }

    public boolean isLeaf() {
        return 0 == this.children.count();
    }

    public boolean isIsland() {
        return 0 == this.outgoing.count() && 0 == this.incoming.count();
    }

    public boolean isPredefined() {
        return null != this.definition;
    }

    public LinkSets get(final String key) {
        if ("outgoing".equals(key)) {
            return this.outgoing;
        } else if ("incoming".equals(key)) {
            return this.incoming;
        }
        return null;
    }
}
