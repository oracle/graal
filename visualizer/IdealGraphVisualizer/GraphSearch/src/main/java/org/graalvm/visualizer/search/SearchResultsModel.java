/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.visualizer.search;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * @author sdedic
 */
public class SearchResultsModel {
    /**
     * All properties encountered in the search.
     */
    private Set<String> allPropertyNames = new HashSet<>();

    private Map<GraphItem, List<NodeResultItem>> owners = new HashMap<>();
    private List<NodeResultItem> items = new ArrayList<>();
    private List<SearchResultsListener> listeners = new ArrayList<>();
    private Map<GraphItem, List<SearchResultsListener>> graphListeners = new HashMap<>();

    public void addSearchResultsListener(SearchResultsListener l) {
        listeners.add(l);
    }

    public void removeSearchResultsListener(SearchResultsListener l) {
        listeners.add(l);
    }

    public void addParentListener(GraphItem item, SearchResultsListener l) {
        graphListeners.computeIfAbsent(item, (i) -> new ArrayList<>()).add(l);
    }

    public void removeParentListener(GraphItem item, SearchResultsListener l) {
        List<SearchResultsListener> ll = graphListeners.get(item);
        if (ll == null) {
            return;
        }
        ll.remove(l);
    }

    public void clear() {
        items.clear();
        owners.clear();
    }

    public int getSize() {
        return items.size();
    }

    public void add(NodeResultItem item) {
        addAll(Collections.singleton(item));
    }

    public Set<String> getAllPropertyNames() {
        return Collections.unmodifiableSet(allPropertyNames);
    }

    private void fireSingleEvent(GraphItem owner, NodeResultItem item, boolean oChanged, BiConsumer<SearchResultsListener, SearchResultsEvent> callback) {
        SearchResultsEvent f = new SearchResultsEvent(Collections.singleton(owner), Collections.singleton(item), this);
        List<SearchResultsListener> ll = new ArrayList<>(listeners);
        List<SearchResultsListener> ol = graphListeners.get(owner);
        List<SearchResultsListener> gl;

        if (ol == null) {
            ol = Collections.emptyList();
        } else {
            ol = new ArrayList<>(ol);
        }
        if (oChanged) {
            gl = graphListeners.get(GraphItem.ROOT);
            if (gl == null) {
                gl = Collections.emptyList();
            } else {
                gl = new ArrayList<>(gl);
            }
        } else {
            gl = Collections.emptyList();
        }
        ll.forEach(l -> callback.accept(l, f));
        ol.forEach(l -> callback.accept(l, f));
        gl.forEach(l -> callback.accept(l, f));
    }

    public void addAll(Collection<NodeResultItem> toAdd) {
        if (toAdd.isEmpty()) {
            return;
        }
        List<NodeResultItem> newItems = new ArrayList<>(items);
        Set<String> names = new HashSet<>();
        Set<String> newNames = new HashSet<>(allPropertyNames);
        Map<GraphItem, SearchResultsEvent> added = new HashMap<>();
        List<GraphItem> newOwners = new ArrayList<>();

        newItems.addAll(toAdd);

        for (NodeResultItem item : toAdd) {
            GraphItem owner = item.getOwner();
            if (owner == null) {
                throw new IllegalArgumentException("not supported yet");
            }
            List<NodeResultItem> children = owners.get(owner);
            if (children == null) {
                newOwners.add(owner);
                children = new ArrayList<>();
                owners.put(owner, children);
            }
            children.add(item);
            item.getItemProperties().forEach(p -> names.add(p.getName()));
            SearchResultsEvent e = added.get(owner);
            if (e == null) {
                e = new SearchResultsEvent(Collections.singleton(owner), new ArrayList<>(), this);
                added.put(owner, e);
            }
            e.getItems().add(item);
        }
        this.items = newItems;

        boolean namesChanged = newNames.addAll(names);

        if (namesChanged) {
            this.allPropertyNames = newNames;
        }

        SearchResultsEvent f = new SearchResultsEvent(added.keySet(), toAdd, this);
        SearchResultsEvent g = null;
        List<SearchResultsListener> ll = new ArrayList<>(listeners);
        List<SearchResultsListener> gl;

        if (!newOwners.isEmpty()) {
            gl = graphListeners.get(GraphItem.ROOT);
            if (gl == null) {
                gl = Collections.emptyList();
            } else {
                gl = new ArrayList<>(gl);
                g = new SearchResultsEvent(newOwners, toAdd, this);
            }
        } else {
            gl = Collections.emptyList();
        }

        ll.forEach(l -> l.itemsAdded(f));
        final SearchResultsEvent fg = g;
        gl.forEach(l -> l.parentsChanged(fg));

        if (!newOwners.isEmpty()) {
            ll.forEach(l -> l.parentsChanged(fg));
        }

        for (GraphItem parent : added.keySet()) {
            gl = graphListeners.get(parent);
            if (gl == null) {
                continue;
            }
            SearchResultsEvent f2 = added.get(parent);
            gl.forEach(l -> l.itemsAdded(f2));
        }

        if (namesChanged) {
            SearchResultsEvent e = new SearchResultsEvent(this, names);
            ll.forEach(l -> l.propertiesChanged(e));
        }
    }

    public void remove(NodeResultItem item) {
        boolean ownersChanged = false;

        items.remove(item);
        GraphItem owner = item.getOwner();
        Collection<NodeResultItem> ch = owners.get(owner);
        if (ch != null) {
            ch.remove(item);
            if (ch.isEmpty()) {
                ownersChanged = true;
                owners.remove(owner);
            }
        }
        fireSingleEvent(owner, item, ownersChanged, SearchResultsListener::itemsRemoved);
    }

    public void removeAll(Collection<NodeResultItem> toRemove) {
        if (toRemove.isEmpty()) {
            return;
        }
        Map<GraphItem, SearchResultsEvent> removed = new HashMap<>();
        List<NodeResultItem> newItems = new ArrayList<>(this.items);
        List<GraphItem> oldOwners = new ArrayList<>();
        newItems.removeAll(toRemove);
        for (NodeResultItem item : toRemove) {
            GraphItem p = item.getOwner();
            List<NodeResultItem> children = owners.get(p);
            children.remove(item);
            if (children.isEmpty()) {
                owners.remove(p);
                oldOwners.add(p);
            }
            SearchResultsEvent e = removed.get(p);
            if (e == null) {
                List<NodeResultItem> items = new ArrayList<>();
                e = new SearchResultsEvent(Collections.singleton(p), items, this);
                removed.put(p, e);
            }
            e.getItems().add(item);
        }
        this.items = newItems;

        SearchResultsEvent f = new SearchResultsEvent(removed.keySet(), toRemove, this);
        SearchResultsEvent g = null;
        List<SearchResultsListener> ll = new ArrayList<>(listeners);
        List<SearchResultsListener> gl;

        if (!oldOwners.isEmpty()) {
            gl = graphListeners.get(GraphItem.ROOT);
            if (gl == null) {
                gl = Collections.emptyList();
            } else {
                gl = new ArrayList<>(gl);
                g = new SearchResultsEvent(oldOwners, toRemove, this);
            }
        } else {
            gl = Collections.emptyList();
        }

        ll.forEach(l -> l.itemsRemoved(f));
        final SearchResultsEvent fg = g;
        gl.forEach(l -> l.parentsChanged(fg));

        if (!oldOwners.isEmpty()) {
            ll.forEach(l -> l.parentsChanged(fg));
        }


        for (GraphItem parent : removed.keySet()) {
            gl = graphListeners.get(parent);
            if (gl == null) {
                continue;
            }
            SearchResultsEvent f2 = removed.get(parent);
            gl.forEach(l -> l.itemsRemoved(f2));
        }
    }

    public void removeParents(Collection<GraphItem> toRemove) {
        if (toRemove.isEmpty()) {
            return;
        }
        List<NodeResultItem> allItems = new ArrayList<>();
        toRemove.stream().flatMap(o -> owners.getOrDefault(o, Collections.emptyList()).stream()).
                forEach(allItems::add);
        SearchResultsEvent oe = new SearchResultsEvent(toRemove, this);

        List<SearchResultsListener> gl;
        gl = graphListeners.getOrDefault(GraphItem.ROOT, Collections.emptyList());
        gl.forEach(l -> l.parentsChanged(oe));
    }

    public Collection<NodeResultItem> getItems() {
        return new ArrayList<>(items);
    }

    public Collection<NodeResultItem> getChildren(GraphItem owner) {
        return new ArrayList<>(owners.getOrDefault(owner, Collections.emptyList()));
    }

    public Collection<GraphItem> getParents() {
        return new ArrayList<>(owners.keySet());
    }

    public String toString() {
        return "GraphSearch[nodes = " + items.size() + " graphs = " + owners.keySet().size() + "]";
    }
}
