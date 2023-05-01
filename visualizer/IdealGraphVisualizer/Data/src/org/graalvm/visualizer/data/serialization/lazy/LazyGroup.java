/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.data.serialization.lazy;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.graalvm.visualizer.data.ChangedEvent;
import org.graalvm.visualizer.data.ChangedEventProvider;
import org.graalvm.visualizer.data.Folder;
import org.graalvm.visualizer.data.FolderElement;
import org.graalvm.visualizer.data.Group;
import org.graalvm.visualizer.data.InputGraph;
import java.util.HashSet;
import java.util.Set;

/**
 * Lazy implementation of Group, which fetches its contents lazily, using {@link GroupCompleter}.
 * The implementation overrides {@link #getElements} in a way that the contents is loaded, if they
 * are not yet present through the {@link #completer}. The exact same data is then provided until
 * all the loaded items become unreachable; then they are GCed, and can be loaded again.
 */
final class LazyGroup extends Group implements Group.LazyContent<List<? extends FolderElement>> {
    private static final Logger LOG = Logger.getLogger(LazyGroup.class.getName());

    private static final Reference EMPTY = new WeakReference(null);

    /**
     * Filtered list of completed elements
     */
    private volatile Reference<List<InputGraph>> graphs = EMPTY;

    private final LoadSupport<List<? extends FolderElement>> cSupport;

    /**
     * Remembered element IDs which will be excluded from possible future reloads.
     */
    private final Set<String> excludedNames = new HashSet<>();

    private final GroupCompleter completer;

    public LazyGroup(Folder parent, GroupCompleter completer, StreamEntry entry) {
        super(parent, entry);
        this.completer = completer;
        this.cSupport = new LoadSupport<List<? extends FolderElement>>(completer) {
            @Override
            protected List<? extends FolderElement> emptyData() {
                return new ArrayList<>();
            }
        };
    }

    /**
     * Informs that more data were received. Caches should be flushed.
     */
    void dataReceived() {
        synchronized (LazyGroup.this) {
            graphs = EMPTY;
        }
    }
    
    @Override
    public boolean isComplete() {
        return cSupport.isComplete();
    }

    @Override
    public List<FolderElement> getElements() {
        // do not synchronize around getElementsInternal().
        return (List<FolderElement>)cSupport.getContents();
    }

    @Override
    protected List<? extends FolderElement> getElementsInternal() {
        return cSupport.getContents();
    }

    @Override
    public List<? extends FolderElement> partialData() {
        return cSupport.partialData();
    }

    @Override
    public void removeElement(FolderElement element) {
        if (element == null) {
            return;
        }
        List<? extends FolderElement> els = getElementsInternal();
        if (!els.contains(element)) {
            return;
        }
        completer.removeData(element);
        synchronized (this) {
            graphs = EMPTY;
            excludedNames.add(element.getName());
        }
        fireChangedEvent();
        notifyContentRemoved(Collections.singletonList(element));
    }

    @Override
    public void removeAll() {
        List<? extends FolderElement> els = getElementsInternal();
        for (FolderElement e : els) {
            completer.removeData(e);
        }
        synchronized (this) {
            graphs = EMPTY;
            els.forEach(e -> excludedNames.add(e.getName()));
        }
        fireChangedEvent();
        notifyContentRemoved(els);
    }
    
    

    synchronized Set<String> getExcludedNames() {
        return new HashSet<>(excludedNames);
    }
    
    @Override
    public int getGraphsCount() {
        return getGraphs().size();
    }
    
    @Override
    public List<InputGraph> getGraphs() {
        Reference<List<InputGraph>> rg;
        List<InputGraph> l;
        synchronized (this) {
            rg = graphs;
            l = rg.get();
        }
        if (l != null) {
            return l;
        }
        List<FolderElement> fl = (List<FolderElement>) getElementsInternal();
        l = Collections.unmodifiableList((List) fl.stream().filter((e) -> e instanceof InputGraph).collect(
                        Collectors.toList()));
        graphs = new WeakReference(l);
        return l;
    }

    @Override
    public synchronized Future<List<? extends FolderElement>> completeContents(Feedback feedback) {
        return cSupport.completeContents(feedback);
    }

    @Override
    public void addElement(FolderElement element) {
    }

    @Override
    public void addElements(List<? extends FolderElement> newElements) {
    }

    /**
     * Speicalized version, which just allows hooking of the list of graphs and folders. The hook
     * ensures that a single graph keeps all its siblings in memory - LazyGroup content is discarded
     * as a whole.
     */
    static class LoadedGraph extends InputGraph implements ChangedEventProvider<Object> {
        private final ChangedEvent ev = new ChangedEvent(this);
        private final GraphMetadata meta;

        public LoadedGraph(StreamEntry graphEntry, GraphMetadata meta, int dumpId, String format, Object[] args) {
            super(graphEntry, dumpId, format, args);
            this.meta = meta;
        }

        @Override
        public ChangedEvent<Object> getChangedEvent() {
            return ev;
        }

        /**
         * Checks change status of a node. Avoids expansion of the preceding graph
         * 
         * @param nodeId node ID
         * @return true, if the node has changed from the preceding graph.
         */
        @Override
        public boolean isNodeChanged(int nodeId) {
            if (meta != null) {
                return meta.changedNodeIds.get(nodeId);
            } else {
                return super.isNodeChanged(nodeId);
            }
        }
    }

    @Override
    public String toString() {
        if (isComplete()) {
            return super.toString();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Group ").append(getProperties()).append("\n");
        return sb.toString();
    }

    // testing only
    static StreamEntry lazyGroupEntry(LazyGroup g) {
        return ((BaseCompleter) g.cSupport.completer).entry;
    }
}
