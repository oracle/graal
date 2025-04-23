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

package org.graalvm.visualizer.view.impl;

import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import org.graalvm.visualizer.filter.Filter;
import org.graalvm.visualizer.graph.Diagram;
import org.graalvm.visualizer.settings.layout.LayoutSettings.LayoutSettingBean;
import org.graalvm.visualizer.view.DiagramViewModel;
import org.graalvm.visualizer.view.impl.DiagramCacheUpdater.Phase;
import org.openide.util.Utilities;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author odouda
 */
public class DiagramCache implements DiagramCacheBase {
    private static final Logger LOG = Logger.getLogger(DiagramCache.class.getName());

    private final Map<GraphTextKey, InputGraphDiagramCache> baseDiagramMap = new HashMap<>();
    private final Map<DiagramViewModel, DiagramCacheUpdater> updaters = new WeakHashMap<>();

    /**
     * Flushes all caches. For testing only. Must be called between test cases,
     * so that all caches are populated again, otherwise tests may interfere
     * with each other, since some diagrams or layouts may be cached from
     * previous testcase.
     */
    public static void flush() {
        DiagramCache inst = getInstance();
        synchronized (inst) {
            inst.baseDiagramMap.clear();
            inst.updaters.clear();
        }
    }

    private DiagramCache() {
    }

    @Override
    public Phase nextPhase(DiagramViewModel model) {
        return Phase.BUILD;
    }

    @Override
    public Diagram get() {
        return null;
    }

    @Override
    public Map getMap() {
        return baseDiagramMap;
    }

    //Bill Pugh Singleton Implementation
    private static class DiagramCacheHolder {
        private static final DiagramCache INSTANCE = new DiagramCache();
    }

    public static DiagramCache getInstance() {
        return DiagramCacheHolder.INSTANCE;
    }

    private DiagramCacheUpdater getUpdater(DiagramViewModel model) {
        return updaters.computeIfAbsent(model, m -> new DiagramCacheUpdater(m));
    }

    @Override
    public synchronized Diagram getDiagram(DiagramViewModel model, Consumer<Diagram> diagramReadyCallback) {
        GraphTextKey key = new GraphTextKey(model.getGraphToView(), model.getNodeText());
        InputGraphDiagramCache inputGraphDiagramCache = baseDiagramMap.get(key);
        Diagram tmp = null;
        if (inputGraphDiagramCache != null) {
            tmp = inputGraphDiagramCache.get();
        }
        if (tmp == null) {
            LOG.log(Level.FINE, "Scheduling building of Diagram for model: {0}", model);
            getUpdater(model).scheduleUpdate(this, diagramReadyCallback);
            return null;
        }
        LOG.log(Level.FINE, "Obtaining cached Diagram from: {0}", inputGraphDiagramCache);
        return inputGraphDiagramCache.getDiagram(model, diagramReadyCallback);
    }

    @Override
    public synchronized DiagramCacheBase makeCache(DiagramViewModel model, Diagram baseDiagram) {
        GraphTextKey key = new GraphTextKey(model.getGraphToView(), model.getNodeText());
        InputGraphDiagramCache inputGraphDiagramCache = baseDiagramMap.get(key);
        Diagram tmp = null;
        if (inputGraphDiagramCache != null) {
            tmp = inputGraphDiagramCache.get();
        }
        if (tmp == null) {
            baseDiagramMap.put(key, inputGraphDiagramCache = new InputGraphDiagramCache(baseDiagram, new SoftReference<>(key), this));
        }
        return inputGraphDiagramCache;
    }

    private final static class GraphTextKey {
        final InputGraph graph;
        final String nodeText;
        final int hash;

        public GraphTextKey(InputGraph graph, String nodeText) {
            this.graph = graph;
            this.nodeText = nodeText;
            this.hash = makeHash();
        }

        private int makeHash() {
            return (nodeText.hashCode() * 53 + graph.hashCode() * 17) * 31;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof GraphTextKey)) {
                return false;
            }
            GraphTextKey other = (GraphTextKey) obj;
            return nodeText.equals(other.nodeText) && graph == other.graph;
        }

    }

    public final class InputGraphDiagramCache extends DiagramCacheValue<GraphTextKey> {
        public final Map<List<Filter>, FilterDiagramCache> filteredDiagramMap = new HashMap<>();

        protected InputGraphDiagramCache(Diagram referent, Reference<GraphTextKey> key, DiagramCacheBase parent) {
            super(referent, key, parent);
        }

        @Override
        protected Map<GraphTextKey, InputGraphDiagramCache> getParentMap() {
            return baseDiagramMap;
        }

        @Override
        public synchronized Diagram getDiagram(DiagramViewModel model, Consumer<Diagram> diagramReadyCallback) {
            List<Filter> filters = model.getFilters();
            FilterDiagramCache filterDiagramCache = filteredDiagramMap.get(filters);
            Diagram tmp = null;
            if (filterDiagramCache != null) {
                tmp = filterDiagramCache.get();
            }
            if (tmp == null) {
                LOG.log(Level.FINE, "Scheduling filtering of Diagram for model: {0}", model);
                getUpdater(model).scheduleUpdate(this, diagramReadyCallback);
                return null;
            }
            LOG.log(Level.FINE, "Obtaining cached Diagram from: {0}", filterDiagramCache);
            return filterDiagramCache.getDiagram(model, diagramReadyCallback);
        }

        @Override
        public synchronized DiagramCacheBase makeCache(DiagramViewModel model, Diagram baseDiagram) {
            List<Filter> filters = model.getFilters();
            FilterDiagramCache filterDiagramCache = filteredDiagramMap.get(filters);
            Diagram tmp = null;
            if (filterDiagramCache != null) {
                tmp = filterDiagramCache.get();
            }
            if (tmp == null) {
                filteredDiagramMap.put(filters, filterDiagramCache = new FilterDiagramCache(baseDiagram, new WeakReference<>(filters), this));
            }
            return filterDiagramCache;
        }

        @Override
        public Phase nextPhase(DiagramViewModel model) {
            return Phase.FILTER;
        }

        @Override
        public Map getMap() {
            return filteredDiagramMap;
        }
    }

    public final class FilterDiagramCache extends DiagramCacheValue<List<Filter>> {
        public final Map<LayoutSettingBean, LayoutDiagramCache> laidOutDiagramMap = new HashMap<>();

        protected FilterDiagramCache(Diagram referent, Reference<List<Filter>> key, DiagramCacheBase parent) {
            super(referent, key, parent);
        }

        @Override
        public synchronized Diagram getDiagram(DiagramViewModel model, Consumer<Diagram> diagramReadyCallback) {
            LayoutSettingBean layoutSetting = model.getLayoutSetting();
            LayoutDiagramCache layoutDiagramCache = laidOutDiagramMap.get(layoutSetting);
            Diagram tmp = null;
            if (layoutDiagramCache != null) {
                tmp = layoutDiagramCache.get();
            }
            if (tmp == null || tmp.getSize() == null) {
                LOG.log(Level.FINE, "Scheduling layouting of Diagram for model: {0}", model);
                getUpdater(model).scheduleUpdate(this, diagramReadyCallback);
                return null;
            }
            LOG.log(Level.FINE, "Obtaining cached Diagram from: {0}", layoutDiagramCache);
            return layoutDiagramCache.getDiagram(model, diagramReadyCallback);
        }

        @Override
        public synchronized DiagramCacheBase makeCache(DiagramViewModel model, Diagram baseDiagram) {
            LayoutSettingBean layoutSetting = model.getLayoutSetting();
            LayoutDiagramCache layoutDiagramCache = laidOutDiagramMap.get(layoutSetting);
            Diagram tmp = null;
            if (layoutDiagramCache != null) {
                tmp = layoutDiagramCache.get();
            }
            if (tmp != null && tmp.getSize() == null) {
                // size of Diagram is null, so we have cached only extracted Diagrams after this stub.
                LOG.log(Level.FINE, "Exchanging stub Diagram: {0} for its laid out counterpart: {1}", new Object[]{tmp, baseDiagram});
                LayoutDiagramCache layoutDiagramCacheNew = new LayoutDiagramCache(baseDiagram, new WeakReference<>(layoutSetting), this);
                laidOutDiagramMap.put(layoutSetting, layoutDiagramCacheNew);
                for (ExtractedDiagramCache c : layoutDiagramCache.extractedDiagramMap.values()) {
                    Set<Integer> key = c.key.get();
                    Diagram d = c.get();
                    if (d != null && key != null) {
                        layoutDiagramCacheNew.extractedDiagramMap.put(key, new ExtractedDiagramCache(d, c.key, layoutDiagramCacheNew));
                    }
                }
                layoutDiagramCache = layoutDiagramCacheNew;
            } else if (tmp == null) {
                laidOutDiagramMap.put(layoutSetting, layoutDiagramCache = new LayoutDiagramCache(baseDiagram, new WeakReference<>(layoutSetting), this));
            }
            return layoutDiagramCache;
        }

        @Override
        public Phase nextPhase(DiagramViewModel model) {
            return Phase.LAYOUT;
        }

        @Override
        public Map getMap() {
            return laidOutDiagramMap;
        }
    }

    public final class LayoutDiagramCache extends DiagramCacheValue<LayoutSettingBean> {
        public final Map<Set<Integer>, ExtractedDiagramCache> extractedDiagramMap = new HashMap<>();

        protected LayoutDiagramCache(Diagram referent, Reference<LayoutSettingBean> key, DiagramCacheBase parent) {
            super(referent, key, parent);
        }

        @Override
        public synchronized Diagram getDiagram(DiagramViewModel model, Consumer<Diagram> diagramReadyCallback) {
            Set<Integer> hidNodes = model.getHiddenNodes();
            if (hidNodes.isEmpty()) {
                Diagram fin = get();
                assert fin != null;
                assert fin.getSize() != null : "Diagram wasn't laid out, but would be returned as finished.";
                LOG.log(Level.FINE, "Loaded cached unextracted Diagram: {0}.", fin);
                return fin;
            }
            HiddenNodesSet hiddenNodes = new HiddenNodesSet(hidNodes, model.getShowNodeHull());
            ExtractedDiagramCache extractedDiagramCache = extractedDiagramMap.get(hiddenNodes);
            Diagram tmp = null;
            if (extractedDiagramCache != null) {
                tmp = extractedDiagramCache.get();
            }
            if (tmp == null) {
                LOG.log(Level.FINE, "Scheduling extraction of Diagram for model: {0}", model);
                getUpdater(model).scheduleUpdate(this, diagramReadyCallback);
                return null;
            }
            LOG.log(Level.FINE, "Obtaining cached Diagram from: {0}", extractedDiagramCache);
            return extractedDiagramCache.getDiagram(model, diagramReadyCallback);
        }

        @Override
        public synchronized DiagramCacheBase makeCache(DiagramViewModel model, Diagram baseDiagram) {
            HiddenNodesSet hiddenNodes = new HiddenNodesSet(model.getHiddenNodes(), model.getShowNodeHull());
            ExtractedDiagramCache extractedDiagramCache = extractedDiagramMap.get(hiddenNodes);
            Diagram tmp = null;
            if (extractedDiagramCache != null) {
                tmp = extractedDiagramCache.get();
            }
            if (tmp == null) {
                extractedDiagramMap.put(hiddenNodes, extractedDiagramCache = new ExtractedDiagramCache(baseDiagram, new SoftReference<>(hiddenNodes), this));
            }
            return extractedDiagramCache;
        }

        @Override
        public Phase nextPhase(DiagramViewModel model) {
            if (model.getHiddenNodes().isEmpty()) {
                return Phase.DONE;
            }
            return Phase.EXTRACT;
        }

        @Override
        public Map getMap() {
            return extractedDiagramMap;
        }
    }

    public final class ExtractedDiagramCache extends DiagramCacheValue<Set<Integer>> {
        protected ExtractedDiagramCache(Diagram referent, Reference<Set<Integer>> key, DiagramCacheBase parent) {
            super(referent, key, parent);
        }

        @Override
        public synchronized Diagram getDiagram(DiagramViewModel model, Consumer<Diagram> diagramReadyCallback) {
            Diagram fin = get();
            assert fin != null;
            LOG.log(Level.FINE, "Loaded cached Diagram: {0}.", fin);
            return fin;
        }

        @Override
        public Phase nextPhase(DiagramViewModel model) {
            return Phase.DONE;
        }

        @Override
        public DiagramCacheBase makeCache(DiagramViewModel model, Diagram baseDiagram) {
            return null;
        }

        @Override
        public Map getMap() {
            return null;
        }
    }

    private abstract static class DiagramCacheValue<TKey> extends WeakReference<Diagram> implements DiagramCacheBase, Runnable {
        protected final Reference<TKey> key;
        protected final Diagram parentDiagram;//GC prevention
        protected final DiagramCacheBase parent;

        protected DiagramCacheValue(Diagram referent, Reference<TKey> key, DiagramCacheBase parent) {
            super(referent, Utilities.activeReferenceQueue());
            assert parent != null;
            this.key = key;
            this.parentDiagram = parent.get();
            this.parent = parent;
        }

        public Diagram getParentDiagram() {
            return parentDiagram;
        }

        private DiagramCacheBase getParent() {
            return parent;
        }

        protected Map getParentMap() {
            return getParent().getMap();
        }

        @Override
        public void run() {
            TKey g = key.get();
            if (g != null) {
                LOG.log(Level.FINE, "Diagram GCed, removing cache: {0}", this);
                synchronized (getParent()) {
                    getParentMap().remove(g, this);
                }
            } else {
                LOG.log(Level.FINE, "Key GCed, removed cache: {0}", this);
            }
        }
    }

    private final static class HiddenNodesSet extends HashSet<Integer> {
        final int hash;
        final boolean showNodeHull;

        public HiddenNodesSet(Set<Integer> ids, boolean showNodeHull) {
            super(ids);
            this.showNodeHull = showNodeHull;
            hash = makeHash();
        }

        private int makeHash() {
            int hash = 0;
            for (Integer i : this) {
                hash += i;
            }
            return showNodeHull ? hash * 7 : hash;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof HiddenNodesSet)) {
                return false;
            }
            HiddenNodesSet other = (HiddenNodesSet) o;
            if (other.size() != this.size() || (showNodeHull != other.showNodeHull)) {
                return false;
            }
            return containsAll(other);
        }
    }
}
