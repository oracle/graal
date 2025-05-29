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
package org.graalvm.visualizer.view;

import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.PROPNAME_DUPLICATE;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.NODE_TEXT;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.SHOW_BLOCKS;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.swing.*;

import org.graalvm.visualizer.data.Source.Provider;
import org.graalvm.visualizer.difference.Difference;
import org.graalvm.visualizer.filter.DiagramFilters;
import org.graalvm.visualizer.filter.Filter;
import org.graalvm.visualizer.filter.FilterChain;
import org.graalvm.visualizer.filter.FilterSequence;
import org.graalvm.visualizer.graph.Diagram;
import org.graalvm.visualizer.graph.Figure;
import org.graalvm.visualizer.graph.Slot;
import org.graalvm.visualizer.script.ScriptEnvironment;
import org.graalvm.visualizer.settings.layout.LayoutSettings.LayoutSettingBean;
import org.graalvm.visualizer.util.RangeSliderModel;
import org.graalvm.visualizer.view.api.DiagramEvent;
import org.graalvm.visualizer.view.api.DiagramListener;
import org.graalvm.visualizer.view.api.DiagramModel;
import org.graalvm.visualizer.view.api.TimelineModel;
import org.graalvm.visualizer.view.impl.DiagramCache;
import org.graalvm.visualizer.view.impl.Filters;
import org.openide.util.Lookup;
import org.openide.util.Pair;
import org.openide.util.Utilities;

import jdk.graal.compiler.graphio.parsing.model.*;

/**
 * Captures the model of several diagrams (phases) within a single group.
 * Maintains the 'current' diagram. When switching diagrams, the current diagram
 * returned may be a stub. Another diagram change event will be fired when the
 * stub is replaced by the actual diagram. Stubs are served during filtering the
 * original graph into the displayable diagram.
 *
 * @author sdedic
 */
public class DiagramViewModel implements ChangedListener<RangeSliderModel>, DiagramModel, DiagramFilters {

    private static final Logger LOG = Logger.getLogger(DiagramViewModel.class.getName());

    private Lookup lookup = Lookup.EMPTY;

    // Warning: Update setData method if fields are added
    private GraphContainer graphContainer;

    /**
     * Ids of nodes, nodes are hidden/shown across all <!--displayed -->graphs
     */
    private Set<Integer> hiddenNodes;

    /**
     * Currently selected nodes; nodes from the current graph are selected, but
     * the selection is remembered using node IDs, so it can be adapted when the
     * graph display changes.
     */
    private Set<InputNode> selectedNodes;
    private final Filters filters;
    private volatile Diagram diagram;
    private volatile Reference<Diagram> previousDiagram = new WeakReference<>(null);
    private InputGraph inputGraph;

    private LayoutSettingBean layoutSetting;

    private final ChangedEvent<DiagramViewModel> diagramChangedEvent;

    private final Object sync = new Object();

    // View properties are accessed from layouting / concurrent processing
    private volatile boolean showNodeHull;
    private boolean hideDuplicates;

    /**
     * The diagram's own group model. Represents peer diagrams in the group.
     */
    private final RangeSliderModel graphPeerModel;

    private final PropertyChangeSupport propSupport = new PropertyChangeSupport(this);
    private final List<DiagramListener> listeners = new ArrayList<>();

    public static final String PROP_SHOW_BLOCKS = "showBlocks"; // NOI18N
    public static final String PROP_SHOW_NODE_HULL = "showNodeHull"; // NOI18N
    public static final String PROP_HIDE_DUPLICATES = "hideDuplicates"; // NOI18N
    public static final String PROP_FILTERS = "filters"; // NOI18N
    public static final String PROP_HIDDEN_NODES = "hiddenNodes"; // NOI18N
    public static final String PROP_SELECTED_GRAPH = "selectedGraph"; // NOI18N
    public static final String PROP_LAYOUT_SETTING = "layoutSetting"; // NOI18N
    public static final String PROP_SELECTED_NODES = "selectedNodes"; // NOI18N
    public static final String PROP_SCRIPTS_CLEARED = "scriptsCleared"; // NOI18N
    public static final String PROP_CONTAINER_CHANGED = "graphContainerChanged"; // NOI18N

    private List<W> tasks = new ArrayList<>();

    private final ChangedEvent<DiagramViewModel> changedEvent = new ChangedEvent<>(this);

    private final ChangedListener<Filters> filtersChanged = (s) -> {
        LOG.log(Level.FINE, "Filters changed.");
        propSupport.firePropertyChange(PROP_FILTERS, null, null);
        diagramChanged(true);
    };

    public DiagramViewModel copy() {
        DiagramViewModel result = new DiagramViewModel(graphContainer, filters.getFilterChain(), layoutSetting);
        result.setDataInternal(this);
        return result;
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        propSupport.addPropertyChangeListener(l);
    }

    public void addPropertyChangeListener(String p, PropertyChangeListener l) {
        propSupport.addPropertyChangeListener(p, l);
    }

    public void removePropertyChangeListener(PropertyChangeListener l) {
        propSupport.removePropertyChangeListener(l);
    }

    public void removePropertyChangeListener(String p, PropertyChangeListener l) {
        propSupport.removePropertyChangeListener(p, l);
    }

    @Override
    public void addDiagramListener(DiagramListener l) {
        synchronized (this) {
            listeners.add(l);
        }
    }

    @Override
    public void removeDiagramListener(DiagramListener l) {
        synchronized (this) {
            listeners.remove(l);
        }
    }

    private void fireDiagramEvent(Supplier<DiagramEvent> eventFactory, BiConsumer<DiagramListener, DiagramEvent> fn) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> fireDiagramEvent(eventFactory, fn));
            return;
        }

        Collection<DiagramListener> ll;
        synchronized (this) {
            if (listeners.isEmpty()) {
                return;
            }
            ll = new ArrayList<>(listeners);
        }
        DiagramEvent event = eventFactory.get();
        ll.stream().forEach(l -> {
            try {
                fn.accept(l, event);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    @Override
    public GraphContainer getContainer() {
        return graphContainer;
    }

    public RangeSliderModel getDiagramPeers() {
        return graphPeerModel;
    }

    public ChangedEvent<DiagramViewModel> getChangedEvent() {
        return changedEvent;
    }

    public ChangedEvent<RangeSliderModel> getColorChangedEvent() {
        return graphPeerModel.getColorChangedEvent();
    }

    private boolean getPositionsDiffer(RangeSliderModel rsm) {
        return graphPeerModel.getFirstPosition() != rsm.getFirstPosition() || graphPeerModel.getSecondPosition() != rsm.getSecondPosition();
    }

    private boolean getColorsDiffer(RangeSliderModel rsm) {
        return !graphPeerModel.getColors().equals(rsm.getColors());
    }

    public void setData(DiagramViewModel newModel) {
        RangeSliderModel newPeerModel = newModel.getDiagramPeers();
        boolean changed = getPositionsDiffer(newPeerModel);
        boolean colorChanged = getColorsDiffer(newPeerModel);
        boolean groupChanged = getGroupsDiffers(newModel);
        boolean diagramChanged = getDiagramsDiffers(newModel);

        InputGraph oldInputGraph = inputGraph;
        Set<Integer> oldHidden = hiddenNodes;
        Set<InputNode> oldSelected = selectedNodes;
        boolean oldShowBlocks = layoutSetting.get(Boolean.class, SHOW_BLOCKS);
        boolean oldShowNodeHull = showNodeHull;
        boolean oldHideDuplicates = hideDuplicates;
        GraphContainer oldContainer = graphContainer;
        LayoutSettingBean oldLayoutSetting = layoutSetting;
        List<Filter> oldFilters = getFilters();

        LOG.log(Level.FINE, "Changed data to {0}, changed part = [ change:{1}, color:{2}, group:{3}, diagram:{4} ]", new Object[]{
                newModel, changed, colorChanged, groupChanged, diagramChanged
        });
        boolean[] alreadyFired = new boolean[1];
        ChangedListener<DiagramViewModel> cl = (e) -> {
            alreadyFired[0] = true;
        };
        getChangedEvent().addListener(cl);
        try {
            setDataInternal(newModel);
        } finally {
            getChangedEvent().removeListener(cl);
        }

        if (changed && !alreadyFired[0]) {
            getChangedEvent().fire();
        }
        if (timeline != null) {
            timeline.setHideDuplicates(newModel.hideDuplicates);
        }
        propSupport.firePropertyChange(PROP_CONTAINER_CHANGED, oldContainer, graphContainer);
        propSupport.firePropertyChange(PROP_FILTERS, oldFilters, getFilters());
        propSupport.firePropertyChange(PROP_HIDDEN_NODES, oldHidden, hiddenNodes);
        propSupport.firePropertyChange(PROP_SELECTED_GRAPH, oldInputGraph, inputGraph);
        propSupport.firePropertyChange(PROP_SHOW_BLOCKS, oldShowBlocks, layoutSetting.get(Boolean.class, SHOW_BLOCKS).booleanValue());
        propSupport.firePropertyChange(PROP_HIDE_DUPLICATES, oldHideDuplicates, hideDuplicates);
        propSupport.firePropertyChange(PROP_SELECTED_NODES, oldSelected, selectedNodes);
        propSupport.firePropertyChange(PROP_SHOW_NODE_HULL, oldShowNodeHull, showNodeHull);
        propSupport.firePropertyChange(PROP_LAYOUT_SETTING, oldLayoutSetting, layoutSetting);
        if (diagramChanged) {
            fireDiagramChanged();
        }
    }

    protected final void setDataInternal(DiagramViewModel newModel) {
        synchronized (sync) {
            this.graphContainer = newModel.graphContainer;
            this.inputGraph = newModel.inputGraph;
            this.filters.setDataInternal(newModel.filters);
            this.layoutSetting = newModel.layoutSetting.copy();
            this.diagram = null;
            setHiddenNodes0(newModel.hiddenNodes);
            this.selectedNodes = new HashSet<>(newModel.selectedNodes);
            this.showNodeHull = newModel.showNodeHull;
            this.hideDuplicates = newModel.hideDuplicates;
        }
        graphPeerModel.setData(newModel.getDiagramPeers());
    }

    protected boolean getGroupsDiffers(DiagramViewModel newModel) {
        return !graphContainer.equals(newModel.graphContainer);
    }

    protected boolean getDiagramsDiffers(DiagramViewModel newModel) {
        Diagram d1;
        Diagram d2;
        synchronized (sync) {
            d1 = diagram;
        }
        synchronized (newModel.sync) {
            d2 = newModel.diagram;
        }
        return (d1 != d2);
    }

    public boolean getShowBlocks() {
        return layoutSetting.get(Boolean.class, SHOW_BLOCKS);
    }

    public void setShowBlocks(boolean b) {
        synchronized (sync) {
            if (getShowBlocks() == b) {
                return;
            }
            layoutSetting.set(SHOW_BLOCKS, b);
        }
        LOG.log(Level.OFF, "ShowBlocks changed: {0}", b);
        propSupport.firePropertyChange(PROP_SHOW_BLOCKS, !b, b);
        diagramChanged();
    }

    public boolean getShowNodeHull() {
        return showNodeHull;
    }

    public void setShowNodeHull(boolean b) {
        synchronized (sync) {
            if (showNodeHull == b) {
                return;
            }
            showNodeHull = b;
        }
        LOG.log(Level.FINE, "Show node hull changed: {0}", b);
        propSupport.firePropertyChange(PROP_SHOW_NODE_HULL, !b, b);
        diagramChanged();
    }

    public String getNodeText() {
        return layoutSetting.get(String.class, NODE_TEXT);
    }

    public LayoutSettingBean getLayoutSetting() {
        return layoutSetting;
    }

    public void setLayoutSetting(LayoutSettingBean newLayoutSetting) {
        if (layoutSetting.equals(newLayoutSetting)) {
            return;
        }
        LOG.log(Level.FINE, "LayoutSetting changed.");
        LayoutSettingBean oldLayoutSetting = layoutSetting;
        layoutSetting = newLayoutSetting;
        propSupport.firePropertyChange(PROP_LAYOUT_SETTING, oldLayoutSetting, newLayoutSetting);
        diagramChanged(true);
    }

    public boolean getHideDuplicates() {
        return timeline != null && timeline.isHideDuplicates();
    }

    public void setHideDuplicates(boolean b) {
        synchronized (this) {
            // just save locally for Undo
            if (timeline != null) {
                this.hideDuplicates = timeline.isHideDuplicates();
            }
            if (b == hideDuplicates) {
                return;
            }
            LOG.log(Level.FINE, "Hide duplicates changed: {0}", b);
        }
        if (timeline != null) {
            timeline.setHideDuplicates(b);
        } else {
            InputGraph currentGraph = getFirstGraph();
            if (b) {
                List<InputGraph> graphs = graphContainer.getGraphs();
                // Back up to the unhidden equivalent graph
                int start = graphs.indexOf(currentGraph);
                int index = start;
                while (index >= 0 && graphs.get(index).getProperties().get(PROPNAME_DUPLICATE) != null) {
                    index--;
                }
                if (index < 0) {
                    while (index < graphs.size() && graphs.get(index).getProperties().get(PROPNAME_DUPLICATE) != null) {
                        index++;
                    }
                }
                if (index >= 0 && index < graphs.size()) {
                    currentGraph = graphs.get(index);
                }
                LOG.log(Level.FINE, "Hide duplicates graph selection: {0}", currentGraph.getName());
            }
            selectGraph(currentGraph);
        }
        propSupport.firePropertyChange(PROP_HIDE_DUPLICATES, !b, b);
    }

    private final TimelineModel timeline;

    public DiagramViewModel(TimelineModel timeline, FilterSequence<FilterChain> filterChain, LayoutSettingBean layoutSettingBean) {
        this(timeline, null, filterChain, layoutSettingBean);
    }

    public DiagramViewModel(GraphContainer g, FilterSequence<FilterChain> filterChain, LayoutSettingBean layoutSettingBean) {
        this(null, g, filterChain, layoutSettingBean);
    }

    @SuppressWarnings("LeakingThisInConstructor")
    private DiagramViewModel(TimelineModel time, GraphContainer g, FilterSequence<FilterChain> filterChain, LayoutSettingBean layoutSettingBean) {
        assert layoutSettingBean != null;
        if (time != null) {
            g = time.getPrimaryPartition();
        }
        this.timeline = time;
        this.filters = new Filters(filterChain);
        this.filters.getFiltersChangedEvent().addListener(filtersChanged);

        this.layoutSetting = layoutSettingBean;
        this.showNodeHull = true;
        this.graphContainer = g;

        hiddenNodes = new HashSet<>();
        selectedNodes = new HashSet<>();

        if (time == null) {
            this.graphPeerModel = initGraphs();

        } else {
            this.graphPeerModel = time.getPrimaryRange();
        }
        graphPeerModel.getChangedEvent().addListener(this);

        diagramChangedEvent = new ChangedEvent<>(this);

        graphPeerModel.addPropertyChangeListener(PROP_SELECTED_GRAPH, (evt) -> {
            if (DiagramViewModel.this.hasScriptFilter()) {
                LOG.log(Level.FINE, "Graph changed, clearing Scripts.");
                filters.setScriptFilter(null, null, false);
                propSupport.firePropertyChange(PROP_SCRIPTS_CLEARED, null, null);
            }
        });
        // refire certain events from the main model:
        graphPeerModel.addPropertyChangeListener(RangeSliderModel.PROP_POSITIONS, (evt)
                -> propSupport.firePropertyChange(RangeSliderModel.PROP_POSITIONS, evt.getOldValue(), evt.getNewValue()));
    }

    public void setLookup(Lookup l) {
        this.lookup = l;
    }

    @Override
    public Lookup getLookup() {
        return this.lookup;
    }

    public ChangedEvent<DiagramViewModel> getDiagramChangedEvent() {
        return diagramChangedEvent;
    }

    @Override
    public Collection<InputNode> getSelectedNodes() {
        return Collections.unmodifiableSet(selectedNodes);
    }

    @Override
    public Set<Integer> getHiddenNodes() {
        return Collections.unmodifiableSet(hiddenNodes);
    }

    private Set<InputNode> hiddenCurrentGraphNodes;

    public Set<InputNode> getHiddenGraphNodes() {
        if (hiddenCurrentGraphNodes != null) {
            return Collections.unmodifiableSet(hiddenCurrentGraphNodes);
        }
        final InputGraph currentGraph = getGraphToView();
        return hiddenCurrentGraphNodes = hiddenNodes.stream().map((id) -> currentGraph.getNode(id)).collect(Collectors.toSet());
    }

    @Override
    public void setSelectedNodes(Collection<InputNode> nodes) {
        if (selectedNodes.equals(nodes)) {
            return;
        }
        Set<InputNode> oldNodes = selectedNodes;
        this.selectedNodes = new HashSet<>(nodes);
        propSupport.firePropertyChange(PROP_SELECTED_NODES, oldNodes, getSelectedNodes());
        fireDiagramEvent(() -> new DiagramEvent(this), DiagramListener::stateChanged);
    }

    @Override
    public void showNot(final Collection<Integer> nodes) {
        setHiddenNodes(nodes);
    }

    public void showFigures(Collection<Figure> f) {
        HashSet<Integer> newHiddenNodes = new HashSet<>(getHiddenNodes());
        HashSet<Integer> existingNodes = new HashSet<>(f.size());
        for (Figure fig : f) {
            fig.getSource().collectIds(existingNodes);
        }
        newHiddenNodes.removeAll(existingNodes);
        setHiddenNodes(newHiddenNodes);
    }

    public Collection<Figure> getSelectedFigures() {
        Set<Figure> result = new HashSet<>();
        Diagram dg = getDiagramToView();
        for (Figure f : dg.getFigures()) {
            for (InputNode node : f.getSource().getSourceNodes()) {
                if (selectedNodes.contains(node)) {
                    result.add(f);
                }
            }
        }
        return result;
    }

    public void showAll(final Collection<Figure> f) {
        showFigures(f);
    }

    void showOnlyNodes(final Collection<InputNode> nodes) {
        showOnly(nodes.stream().map(n -> n.getId()).collect(Collectors.toList()));
    }

    @Override
    public void showOnly(final Collection<Integer> nodes) {
        final HashSet<Integer> allNodes = new HashSet<>(getGraphToView().getGroup().getChildNodeIds());
        allNodes.removeAll(nodes);
//        allNodes.removeAll(nodes.stream().map(n -> n.getId()).collect(Collectors.toList()));
        Diagram d = getDiagramToView();

        Collection<Integer> nonRepresentedNodes = nodes.stream().filter((n) -> !d.getFigure(n).isPresent()).collect(Collectors.toList());
        InputGraph g = getGraphToView();
        for (Integer id : nonRepresentedNodes) {
            InputNode in = g.getNode(id);
            Collection<Provider> provs = d.forSource(id);
            for (Provider p : provs) {
                if (!(p instanceof Slot)) {
                    continue;
                }
                Slot s = (Slot) p;
                InputNode an = s.getAssociatedNode();
                allNodes.remove(an.getId());
            }
        }

        Set<Integer> aggregatedNodes = nodes.stream().filter((n) -> !d.getFigure(n).isPresent()).flatMap((in) -> {
            return d.forSource(in).stream().filter((f) -> f instanceof Slot).flatMap((s) -> ((Slot) s).getFigure().getSource().getSourceNodes().stream().map(n -> n.getId()));
        }).collect(Collectors.toSet());
        allNodes.removeAll(aggregatedNodes);
        setHiddenNodes(allNodes);
    }

    public void setHiddenNodes(Collection<Integer> nodes) {
        if (this.hiddenNodes.equals(nodes)) {
            LOG.log(Level.FINER, "Hidden nodes unchanged size: {0}, skipping.", hiddenNodes.size());
            return;
        }
        LOG.log(Level.FINER, "Hidden nodes changed, size: {0}.", nodes.size());
        Set<Integer> oldNodes = hiddenNodes;
        setHiddenNodes0(nodes);
        propSupport.firePropertyChange(PROP_HIDDEN_NODES, oldNodes, nodes);
        diagramChanged();
    }

    private void setHiddenNodes0(Collection<Integer> nodes) {
        this.hiddenNodes = new HashSet<>(nodes);
        this.hiddenCurrentGraphNodes = null;
        fireDiagramEvent(() -> new DiagramEvent(this), DiagramListener::stateChanged);
    }

    private void diagramChanged() {
        diagramChanged(false);
    }

    private void diagramChanged(boolean force) {
        boolean relevant;
        synchronized (sync) {
            relevant = force || !isStubDiagram(diagram) || (diagram != null && diagram.getGraph() != inputGraph);
            if (relevant) {
                LOG.log(Level.FINE, "DiagramChanged old Diagram: {0}.", diagram);
                diagram = null;
            }
        }
        if (relevant) {
            fireDiagramChanged();
        }
    }

    private void fireDiagramChanged() {
        if (SwingUtilities.isEventDispatchThread()) {
            diagramChangedEvent.fire();
            // FIXME: supply a valid previous diagram
            fireDiagramEvent(() -> new DiagramEvent(this, null), DiagramListener::diagramChanged);
        } else {
            SwingUtilities.invokeLater(this::fireDiagramChanged);
        }
    }

    @Override
    public FilterSequence getFilterSequence() {
        return filters.getFilterChain();
    }

    /*
     * Obtain all filters and scripts in execution order.
     */
    @Override
    public List<Filter> getFilters() {
        return filters.getFiltersSnapshot();
    }

    /*
     * Obtain all script filters.
     */
    @Override
    public List<Filter> getScriptFilters() {
        return filters.getScriptFilters();
    }

    /**
     * @return {@code null} if default environment is used.
     */
    @Override
    public ScriptEnvironment getScriptEnvironment() {
        return filters.getScriptEnvironment();
    }

    public FilterSequence getFilterChain() {
        return filters.getFilterChain();
    }

    public void setFilterChain(FilterSequence chain) {
        filters.setFilterChain(chain);
    }

    private RangeSliderModel initGraphs() {
        List<String> positions = new ArrayList<>();
        graphContainer.getGraphs().forEach(graph -> {
            positions.add(graph.getName());
        });
        if (positions.isEmpty()) {
            InputGraph ig = createEmptyGraph();
            positions.add(ig.getName());
        }
        return new RangeSliderAccess(positions);
    }

    private List<InputGraph> graphs() {
        if (getHideDuplicates()) {
            List<InputGraph> graphs = new ArrayList<>(graphContainer.getGraphs());
            for (Iterator<InputGraph> it = graphs.iterator(); it.hasNext(); ) {
                if (it.next().isDuplicate()) {
                    it.remove();
                }
            }
            return graphs;
        } else {
            return graphContainer.getGraphs();
        }
    }

    public boolean isValid() {
        List<InputGraph> lg = graphContainer.getGraphs();
        return lg.size() > 1 || lg.get(0) != emptyGraph;
    }

    public InputGraph getFirstGraph() {
        int fp = graphPeerModel.getFirstPosition();
        if (fp < graphs().size()) {
            return getGraphAtPos(fp);
        }
        return getGraphAtPos(graphs().size() - 1);
    }

    private synchronized InputGraph createEmptyGraph() {
        if (emptyGraph == null) {
            InputGraph ig = InputGraph.createTestGraph("");
            ig.setParent(graphContainer.getContentOwner());
            emptyGraph = ig;
        }
        return emptyGraph;
    }

    // @GuardedBy(this)
    private InputGraph emptyGraph;

    private InputGraph getGraphAtPos(int i) {
        InputGraph g = null;
        // avoid hidden duplicates, faster than graphs().
        if (timeline != null) {
            g = timeline.findGraph(graphPeerModel, i);
        } else {
            if (i < graphs().size() && i >= 0) {
                g = graphs().get(i);
            }
        }
        if (g != null) {
            return g;
        } else {
            return createEmptyGraph();
        }
    }

    public InputGraph getSecondGraph() {
        if (graphPeerModel.getSecondPosition() < graphs().size()) {
            return getGraphAtPos(graphPeerModel.getSecondPosition());
        }
        return getFirstGraph();
    }

    @Override
    public boolean selectGraph(InputGraph g) {
        int index = graphs().indexOf(g);
        if (index == -1 && getHideDuplicates()) {
            // A graph was selected that's currently hidden, so unhide and select it.
            timeline.setHideDuplicates(false);
            // retry when the timeline refreshes.
            timeline.whenStable().execute(() -> selectGraph(g));
            return false;
        }
        assert index != -1;
        graphPeerModel.setPositions(index, index);
        return true;
    }

    @Override
    public boolean isStubDiagram(Diagram dg) {
        return dg == null || (dg.getFigures().isEmpty() && "".equals(dg.getNodeText()));
    }

    @Override
    public Future<Diagram> applyScriptFilter(Filter filterToApply, ScriptEnvironment env, boolean append, Consumer<Diagram> callback) {
        filters.setScriptFilter(filterToApply, env, append);
        return withDiagramToView(callback);
    }

    /**
     * Executes a task with a fully initialized diagram. The task may run
     * immediately, or may be deferred to a later time, when the diagram becomes
     * ready. The Future can be used to wait on the initialization and to get
     * the diagram.
     * <p/>
     * Threading note: if this method is called in EDT, the delayed task will be
     * also called in EDT. Otherwise the thread executing the task is
     * unspecified.
     *
     * @param task the task to execute
     * @return Future that produces the diagram instance
     */
    @Override
    public Future<Diagram> withDiagramToView(Consumer<Diagram> task) {
        Diagram dg = getDiagramToView();
        return addCompletionTask(dg, task);
    }

    private Future<Diagram> addCompletionTask(Diagram dg, Consumer<Diagram> task) {
        synchronized (sync) {
            if (isStubDiagram(diagram)) {
                LOG.log(Level.FINE, "Diagram not ready scheduling completion task.");
                W r = new W(task, SwingUtilities.isEventDispatchThread());
                tasks.add(r);
                return r;
            }
        }
        // execute immediately
        LOG.log(Level.FINE, "Diagram ready, executing completion task.");
        if (task != null) {
            task.accept(dg);
        }
        return CompletableFuture.completedFuture(dg);
    }

    void removeTask(W r) {
        synchronized (sync) {
            tasks.remove(r);
        }
    }

    class W extends CompletableFuture {

        final boolean edt;
        final Consumer<Diagram> callback;

        void cancelled() {
            super.cancel(true);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            removeTask(this);
            boolean r = super.cancel(mayInterruptIfRunning);
            return r;
        }

        public W(Consumer<Diagram> callback, boolean edt) {
            this.callback = callback;
            this.edt = edt;
        }

        public void run(Diagram dg) {
            if (callback != null) {
                callback.accept(dg);
            }
        }
    }

    /**
     * Returns the currently selected diagram. During diagram initialization,
     * the returned diagram may be an empty <b>stub</b> ({@link #isStubDiagram}
     * will return true for it). After the diagram fully initializes, it will be
     * published by the model and another change event will be fired.
     * <p/>
     * Unless checking just the diagram instance, {@link withDiagramToView} is
     * preferred.
     *
     * @return
     */
    @Override
    public Diagram getDiagramToView() {
        synchronized (sync) {
            if (diagram != null) {
                LOG.log(Level.FINE, "Saved stub=={1} Diagram: {0}.", new Object[]{diagram, isStubDiagram(diagram)});
                return diagram;
            }
        }
        Diagram dg = getCachedDiagram();
        if (dg == null) {
            synchronized (sync) {
                if (diagram == null || diagram.getGraph() != inputGraph) {
                    Diagram prev = previousDiagram.get();
                    if (prev != null) {
                        previousDiagram = new WeakReference<Diagram>(prev) {
                            private final Diagram keep = prev;
                        };
                    }
                    diagram = Diagram.createEmptyDiagram(inputGraph);
                    LOG.log(Level.FINE, "Diagram isn't ready yet, returning stub: " + diagram);
                }
                dg = diagram;
            }
        } else {
            LOG.log(Level.FINE, "Cached Diagram: {0}.", dg);
            List<W> tsks;
            synchronized (sync) {
                diagram = dg;
                tsks = tasks;
                tasks = new ArrayList<>();
            }
            finalizeTasks(tsks, dg, false);
        }
        assert dg != null;
        return dg;
    }

    private Diagram getCachedDiagram() {
        return DiagramCache.getInstance().getDiagram(this, this::diagramFinished);
    }

    private void diagramFinished(Diagram diagram) {
        assert diagram != null;
        if (diagram.getGraph() == inputGraph) {
            assert isStubDiagram(this.diagram);
            assert !SwingUtilities.isEventDispatchThread();
            boolean changed;
            List<W> tsks;
            synchronized (sync) {
                changed = this.diagram != diagram;
                if (changed) {
                    LOG.log(Level.FINE, "Diagram updated from: {0} to: {1}.", new Object[]{this.diagram, diagram});
                    this.diagram = diagram;
                }
                tsks = tasks;
                tasks = new ArrayList<>();
            }
            finalizeTasks(tsks, diagram, changed);
        }
    }

    private void finalizeTasks(final List<W> tasks, Diagram finishedDiagram, boolean changed) {
        if (changed) {
            tasks.add(new W((d) -> fireDiagramChanged(), true));
        }
        if (tasks.isEmpty()) {
            LOG.log(Level.FINE, "No Diagram tasks.");
            return;
        }
        LOG.log(Level.FINE, "Finalizing Diagram tasks.");
        boolean needSwing = false;
        for (W r : tasks) {
            if (r.edt) {
                needSwing = true;
            } else {
                r.run(finishedDiagram);
            }
        }
        if (needSwing) {
            SwingUtilities.invokeLater(() -> {
                boolean cancel;
                synchronized (sync) {
                    cancel = finishedDiagram != diagram;
                }
                for (W r : tasks) {
                    if (r.edt) {
                        if (cancel) {
                            r.cancel(false);
                        } else {
                            r.run(finishedDiagram);
                        }
                    }
                }
                finishDiagramTasks(tasks, finishedDiagram);
            });
        } else {
            finishDiagramTasks(tasks, finishedDiagram);
        }
    }

    private void finishDiagramTasks(List<W> tasks, Diagram finishedDiagram) {
        if (!SwingUtilities.isEventDispatchThread()) {
            LOG.log(Level.FINE, "Finished Diagram tasks.");
            SwingUtilities.invokeLater(() -> finishDiagramTasks(tasks, finishedDiagram));
            return;
        }
        for (W t : tasks) {
            t.complete(finishedDiagram);
        }
        Diagram previous;
        synchronized (this) {
            previous = previousDiagram.get();
            if (this.diagram == finishedDiagram) {
                previousDiagram = new WeakReference<>(null);
            }
        }
        fireDiagramEvent(() -> new DiagramEvent(this, previous), DiagramListener::diagramReady);
        LOG.log(Level.FINE, "Completed Diagram tasks.");
    }

    class DiffGraphRef extends WeakReference<InputGraph> implements Runnable {

        private final Pair<InputGraph, InputGraph> key;

        public DiffGraphRef(Pair<InputGraph, InputGraph> key, InputGraph referent) {
            super(referent, Utilities.activeReferenceQueue());
            this.key = key;
        }

        @Override
        public void run() {
            synchronized (diffCache) {
                diffCache.remove(key);
            }
        }
    }

    /**
     * Cached differences between graphs
     */
    private final Map<Pair<InputGraph, InputGraph>, Reference<InputGraph>> diffCache = new HashMap<>();

    private InputGraph makeDifference(InputGraph first, InputGraph second) {
        Pair<InputGraph, InputGraph> key = Pair.of(first, second);
        InputGraph g;
        synchronized (diffCache) {
            Reference<InputGraph> r = diffCache.get(key);
            if (r != null) {
                g = r.get();
                if (g != null) {
                    return g;
                }
            }
        }
        g = Difference.createDiffGraph(first, second);
        synchronized (diffCache) {
            Reference<InputGraph> r = diffCache.get(key);
            if (r != null) {
                InputGraph g2 = r.get();
                if (g2 != null) {
                    return g2;
                }
            }
            diffCache.put(key, new DiffGraphRef(key, g));
        }
        return g;
    }

    @Override
    public InputGraph getGraphToView() {
        if (inputGraph == null) {
            inputGraph = getFirstGraph();
            InputGraph scnd = getSecondGraph();
            if (inputGraph != scnd) {
                inputGraph = makeDifference(inputGraph, scnd);
            }
            LOG.log(Level.FINE, "New graph to view: {0}", inputGraph.getName());
        }
        return inputGraph;
    }

    @Override
    public void changed(RangeSliderModel source) {
        InputGraph oldGraph = this.inputGraph;
        inputGraph = null;
        InputGraph curGraph = getGraphToView();
        if (curGraph == oldGraph) {
            // the range slider may change, but the current input graph / diagram may be still the same.
            // No need to throw away the current diagram, but should refire general change event from Slider.
            getChangedEvent().fire();
            return;
        }
        hiddenCurrentGraphNodes = null;
        propSupport.firePropertyChange(PROP_SELECTED_GRAPH, oldGraph, curGraph);
        diagramChanged();
        getChangedEvent().fire();
    }

    public void setSelectedFigures(Collection<Figure> list) {
        Set<InputNode> newSelectedNodes = new HashSet<>();
        for (Figure f : list) {
            newSelectedNodes.addAll(f.getSource().getSourceNodes());
        }
        this.setSelectedNodes(newSelectedNodes);
    }

    void close() {
        filters.close();
    }

    Iterable<InputGraph> getGraphsForward() {
        return () -> new Iterator<InputGraph>() {
            int index = graphPeerModel.getFirstPosition();

            @Override
            public boolean hasNext() {
                return index + 1 < graphs().size();
            }

            @Override
            public InputGraph next() {
                return graphs().get(++index);
            }
        };
    }

    Iterable<InputGraph> getGraphsBackward() {
        return () -> new Iterator<InputGraph>() {
            int index = graphPeerModel.getFirstPosition();

            @Override
            public boolean hasNext() {
                return index > 0;
            }

            @Override
            public InputGraph next() {
                return graphs().get(--index);
            }
        };
    }

    /**
     * Returns if script filter is applied on this diagram. Returns
     * {@code false} if no custom filter is applied (i.e. only normal
     * FilterChain processed the diagram).
     *
     * @return
     */
    public boolean hasScriptFilter() {
        return !getScriptFilters().isEmpty();
    }

    public List<String> getPositions() {
        return graphPeerModel.getPositions();
    }

    @Override
    public TimelineModel getTimeline() {
        return timeline;
    }

    /**
     * Provides access to internals of RangeSliderModel
     */
    static class RangeSliderAccess extends RangeSliderModel {

        public RangeSliderAccess(List<String> positions) {
            super(positions);
        }

        public RangeSliderAccess(RangeSliderModel model) {
            super(model);
        }

        @Override
        public void setPositions(List<String> positions) {
            super.setPositions(positions);
        }

        protected boolean getPositionsDiffers0(RangeSliderModel model) {
            return super.getPositionsDiffers(model);
        }

        protected boolean getColorsDiffers0(RangeSliderModel model) {
            return super.getColorsDiffers(model);
        }
    }
}
