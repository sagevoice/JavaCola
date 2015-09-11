package edu.monash.infotech.marvl.cola.powergraph;

import edu.monash.infotech.marvl.cola.TriConsumer;
import edu.monash.infotech.marvl.cola.vpsc.Group;
import edu.monash.infotech.marvl.cola.vpsc.Leaf;

import java.util.ArrayList;
import java.util.List;

public class Configuration<T> {

    // canonical list of modules.
    // Initialized to a module for each leaf node, such that the ids and indexes of the module in the array match the indexes of the nodes in links
    // Modules created through merges are appended to the end of this.
    public  List<Module>        modules;
    // top level modules and candidates for merges
    public  List<ModuleSet>     roots;
    // remaining edge count
    public  int                 R;
    private LinkTypeAccessor<T> linkAccessor;

    public Configuration(final int n, final List<T> edges, final LinkTypeAccessor<T> linkAccessor) {
        this(n, edges, linkAccessor, null);
    }

    public Configuration(final int n, final List<T> edges, final LinkTypeAccessor<T> linkAccessor, final Group rootGroup) {
        this.linkAccessor = linkAccessor;
        this.modules = new ArrayList<>(n);
        this.roots = new ArrayList<>();
        if (null != rootGroup) {
            this.initModulesFromGroup(rootGroup);
        } else {
            final ModuleSet root = new ModuleSet();
            for (int i = 0; i < n; ++i) {
                final Module m = new Module(i);
                this.modules.set(i, m);
                root.add(m);
            }
            this.roots.add(root);
        }
        this.R = edges.size();
        edges.forEach(e -> {
            final Module s = this.modules.get(linkAccessor.getSourceIndex(e)),
                    t = this.modules.get(linkAccessor.getTargetIndex(e));
            final int type = linkAccessor.getType(e);
            s.outgoing.add(type, t);
            t.incoming.add(type, s);
        });
    }

    private ModuleSet initModulesFromGroup(final Group group) {
        final ModuleSet moduleSet = new ModuleSet();
        this.roots.add(moduleSet);
        for (int i = 0; i < group.leaves.size(); ++i) {
            final Leaf node = group.leaves.get(i);
            final Module module = new Module(node.id);
            this.modules.set(node.id, module);
            moduleSet.add(module);
        }
        if (null != group.groups) {
            for (int j = 0; j < group.groups.size(); ++j) {
                final Group child = group.groups.get(j);
                // Propagate group properties (like padding, stiffness, ...) as module definition so that the generated power graph group will inherit it
                final Definition definition = new Definition(child);
                // Use negative module id to avoid clashes between predefined and generated modules
                moduleSet.add(new Module(-1 - j, new LinkSets(), new LinkSets(), this.initModulesFromGroup(child), definition));
            }
        }
        return moduleSet;
    }

    public Module merge(final Module a, final Module b) {
        return merge(a, b, 0);
    }

    // merge modules a and b keeping track of their power edges and removing the from roots
    public Module merge(final Module a, final Module b, final int k) {
        final LinkSets inInt = a.incoming.intersection(b.incoming),
                outInt = a.outgoing.intersection(b.outgoing);
        final ModuleSet children = new ModuleSet();
        children.add(a);
        children.add(b);
        final Module m = new Module(this.modules.size(), outInt, inInt, children);
        this.modules.add(m);
        final TriConsumer<LinkSets, String, String> update = (s, i, o) -> {
            s.forAll((ms, linktype) -> {
                ms.forAll((n, value) -> {
                    final LinkSets nls = n.get(i);
                    nls.add(linktype, m);
                    nls.remove(linktype, a);
                    nls.remove(linktype, b);
                    a.get(o).remove(linktype, n);
                    b.get(o).remove(linktype, n);
                }, null);
            });
        };
        update.accept(outInt, "incoming", "outgoing");
        update.accept(inInt, "outgoing", "incoming");
        this.R -= inInt.count() + outInt.count();
        this.roots.get(k).remove(a);
        this.roots.get(k).remove(b);
        this.roots.get(k).add(m);
        return m;
    }

    private List<Merge> rootMerges() {
        return this.rootMerges(0);
    }

    private List<Merge> rootMerges(final int k) {
        final List<Module> rs = this.roots.get(k).modules();
        final int n = rs.size();
        final List<Merge> merges = new ArrayList<>(n * (n - 1));
        int ctr = 0;
        for (int i = 0, i_ = n - 1; i < i_; ++i) {
            for (int j = i + 1; j < n; ++j) {
                final Module a = rs.get(i), b = rs.get(j);
                merges.set(ctr, new Merge(ctr, this.nEdges(a, b), a, b));
                ctr++;
            }
        }
        return merges;
    }

    public boolean greedyMerge() {
        for (int i = 0; i < this.roots.size(); ++i) {
            // Handle single nested module case
            if (2 > this.roots.get(i).modules().size()) {
                continue;
            }

            // find the merge that allows for the most edges to be removed.  secondary ordering based on arbitrary id (for predictability)
            final List<Merge> ms = this.rootMerges(i);
            ms.sort((a, b) -> a.nEdges == b.nEdges ? a.id - b.id : a.nEdges - b.nEdges);
            final Merge m = ms.get(0);
            if (m.nEdges >= this.R) {
                continue;
            }
            this.merge(m.a, m.b, i);
            return true;
        }
        return false;
    }

    private int nEdges(final Module a, final Module b) {
        final LinkSets inInt = a.incoming.intersection(b.incoming),
                outInt = a.outgoing.intersection(b.outgoing);
        return this.R - inInt.count() - outInt.count();
    }

    private void toGroups(final ModuleSet set, final Group group, final List<Group> groups) {
        set.forAll((m, value) -> {
            if (m.isLeaf()) {
                if (null == group.leaves) {
                    group.leaves = new ArrayList<>();
                }
                group.leaves.add(new Leaf(m.id));
            } else {
                Group g = group;
                m.gid = groups.size();
                if (!m.isIsland() || m.isPredefined()) {
                    g = new Group(m.gid);
                    if (m.isPredefined()) {
                        // Apply original group properties
                        m.definition.setupGroup(g);
                    }
                    if (null == group.groups) {
                        group.groups = new ArrayList<>();
                    }
                    group.groups.add(new Group(m.gid));
                    groups.add(g);
                }
                toGroups(m.children, g, groups);
            }
        }, null);
    }


    public List<Group> getGroupHierarchy(final List<PowerEdge> retargetedEdges) {
        final List<Group> groups = new ArrayList<>();
        final Group root = new Group();
        toGroups(this.roots.get(0), root, groups);
        final List<PowerEdge> es = this.allEdges();
        es.forEach(e -> {
            final Module a = this.modules.get((Integer)e.source);
            final Module b = this.modules.get((Integer)e.target);
            retargetedEdges.add(new PowerEdge(
                    null == a.gid ? e.source : groups.get(a.gid),
                    null == b.gid ? e.target : groups.get(b.gid),
                    e.type
            ));
        });
        return groups;
    }

    public List<PowerEdge> allEdges() {
        final List<PowerEdge> es = new ArrayList<>();
        Configuration.getEdges(this.roots.get(0), es);
        return es;
    }

    static void getEdges(final ModuleSet set, final List<PowerEdge> es) {
        set.forAll((m, value) -> {
            m.getEdges(es);
            Configuration.getEdges(m.children, es);
        }, null);
    }
}
