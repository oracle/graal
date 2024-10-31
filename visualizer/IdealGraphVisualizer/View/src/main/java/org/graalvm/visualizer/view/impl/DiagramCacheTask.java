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

import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputBlock;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;
import org.graalvm.visualizer.filter.CustomFilter;
import org.graalvm.visualizer.filter.FilterCanceledException;
import org.graalvm.visualizer.filter.FilterChain;
import org.graalvm.visualizer.filter.FilterExecution;
import org.graalvm.visualizer.filter.FilterExecutionService;
import org.graalvm.visualizer.graph.Block;
import org.graalvm.visualizer.graph.Connection;
import org.graalvm.visualizer.graph.Diagram;
import org.graalvm.visualizer.graph.Figure;
import org.graalvm.visualizer.hierarchicallayout.HierarchicalClusterLayoutManager;
import org.graalvm.visualizer.hierarchicallayout.HierarchicalLayoutManager;
import org.graalvm.visualizer.layout.LayoutGraph;
import org.graalvm.visualizer.settings.layout.LayoutSettings.LayoutSettingBean;
import org.graalvm.visualizer.view.DiagramViewModel;
import org.graalvm.visualizer.view.impl.DiagramCacheUpdater.Phase;
import org.openide.util.Exceptions;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.graalvm.visualizer.settings.layout.LayoutSettings.DEFAULT_LAYOUT;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.MAX_BLOCK_LAYER_LENGTH;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.MAX_LAYER_LENGTH;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.STABILIZED_LAYOUT;
import static org.graalvm.visualizer.view.DiagramScene.doesIntersect;

/**
 * @author odouda
 */
public abstract class DiagramCacheTask implements Runnable {

    private static final Logger LOG = Logger.getLogger(DiagramCacheTask.class.getName());

    protected final DiagramViewModel model;
    protected final Diagram baseDiagram;
    private final DiagramCacheUpdater updater;
    protected Diagram outputDiagram;
    private final AtomicBoolean cancelled;

    static DiagramCacheTask makeTask(Diagram diagram, DiagramViewModel model, DiagramCacheUpdater updater, Phase phase) {
        switch (phase) {
            case BUILD:
                return new Building(model, updater);
            case EXTRACT:
                return new Extraction(diagram, model, updater);
            case FILTER:
                return new Filtering(diagram, model, updater);
            case LAYOUT:
                return new Layouting(diagram, model, updater);
        }
        return null;
    }

    private DiagramCacheTask(Diagram baseDiagram, DiagramViewModel model, DiagramCacheUpdater updater) {
        this.baseDiagram = baseDiagram;
        cancelled = new AtomicBoolean(false);
        this.model = model;
        this.updater = updater;
    }

    public void cancel() {
        LOG.log(Level.FINE, "Cancelled task: {0}", this);
        cancelled.set(true);
    }

    public boolean cancelled() {
        return cancelled.get();
    }

    protected abstract void execute();

    @Override
    public final void run() {
        if (baseDiagram != null && !cancelled()) {
            outputDiagram = baseDiagram.copy();
        }
        if (!cancelled()) {
            execute();
        }
        if (!cancelled()) {
            updater.afterTask(this);
        }
    }

    private static class Building extends DiagramCacheTask {

        public Building(DiagramViewModel model, DiagramCacheUpdater updater) {
            super(null, model, updater);
        }

        @Override
        protected void execute() {
            InputGraph g = model.getGraphToView();
            Object lc;
            if (g instanceof Group.LazyContent) {
                Group.LazyContent lg = (Group.LazyContent) g;
                if (!lg.isComplete()) {
                    try {
                        lc = lg.completeContents(null).get();
                    } catch (InterruptedException | ExecutionException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            }
            outputDiagram = Diagram.createDiagram(g, model.getNodeText());
        }
    }

    private static class Filtering extends DiagramCacheTask {

        private static final CustomFilter DIFF_FILTER = new CustomFilter(
                "difference", "colorize('state', 'same', white);"
                + "colorize('state', 'changed', orange);"
                + "colorize('state', 'new', green);"
                + "colorize('state', 'deleted', red);");
        private final FilterView viewApi;
        private FilterExecution chainControl;

        public Filtering(Diagram baseDiagram, DiagramViewModel model, DiagramCacheUpdater updater) {
            super(baseDiagram, model, updater);
            viewApi = new FilterView(model);
        }

        @Override
        public void cancel() {
            super.cancel();
            if (chainControl != null) {
                chainControl.cancel();
            }
        }

        @Override
        protected void execute() {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "Processing diagram {0} of graph {1} with filters", new Object[]{outputDiagram, outputDiagram.getGraph().getName()});
            }
            FilterChain filterChain = new FilterChain(model.getFilterChain());
            if (model.getDiagramPeers().getFirstPosition() != model.getDiagramPeers().getSecondPosition()) {
                filterChain.addFilter(DIFF_FILTER);
            }
            model.getScriptFilters().forEach((f) -> filterChain.addFilter(f));

            boolean error = true;
            FilterExecutionService exec = FilterExecution.getExecutionService();

            try {
                chainControl = configure(exec.createExecution(filterChain, null, outputDiagram));
                LOG.log(Level.FINE, "Using filterchain control {0}", chainControl);
                chainControl.process();
                if (cancelled()) {
                    LOG.log(Level.FINE, "Processing cancelled");
                    return;
                }
                LOG.log(Level.FINE, "Scheduling viewApi execution.");
                SwingUtilities.invokeAndWait(() -> {
                    if (!cancelled()) {
                        // FIXME: the diagram to view may be a stub -- viewApi actions will be lost!
                        model.withDiagramToView((d) -> viewApi.perform());
                    }
                });
                error = false;
            } catch (FilterCanceledException ex) {
                // expected, bail out
                LOG.log(Level.FINE, "Aborted with Cancelled exception from control {0}", ex.getEnvironment());
                cancel();
                error = false;
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "Error during processing", ex);
            } catch (InterruptedException | InvocationTargetException ex) {
                LOG.log(Level.WARNING, "Error during processing", ex);
                Exceptions.printStackTrace(ex);
            } finally {
                if (error) {
                    cancel();
                }
            }
            LOG.log(Level.FINE, "outputDiagram figure size: {0}", outputDiagram.getFigures().size());
        }

        private FilterExecution configure(FilterExecution ex) {
            ex.getEnvironment().globals().put("view", viewApi);
            viewApi.setExecution(ex);
            return ex;
        }
    }

    private static class Layouting extends DiagramCacheTask {

        HierarchicalLayoutManager manager;
        HierarchicalClusterLayoutManager clusterManager;

        @Override
        public void cancel() {
            super.cancel();
            if (manager != null) {
                manager.cancel();
            }
            if (clusterManager != null) {
                clusterManager.cancel();
            }
        }

        public Layouting(Diagram baseDiagram, DiagramViewModel model, DiagramCacheUpdater updater) {
            super(baseDiagram, model, updater);
        }

        @Override
        protected void execute() {
            LayoutSettingBean layoutSettings = model.getLayoutSetting();
            HashSet<Figure> figures;
            HashSet<Connection> edges;
            if (model.getHiddenGraphNodes().isEmpty() || layoutSettings.get(Boolean.class, STABILIZED_LAYOUT)) {
                for (Figure f : outputDiagram.getFigures()) {
                    f.setVisible(true);
                    f.setBoundary(false);
                }
                for (Block b : outputDiagram.getBlocks()) {
                    b.setVisible(true);
                }
                figures = new HashSet<>(outputDiagram.getFigures());
                edges = new HashSet<>(outputDiagram.getConnections());
            } else {
                return;//Will extract/layout in next phase
            }
            if (cancelled()) {
                return;
            }
            LayoutGraph graph = new LayoutGraph(edges, figures);
            if (model.getShowBlocks()) {
                clusterManager = new HierarchicalClusterLayoutManager(HierarchicalLayoutManager.Combine.SAME_OUTPUTS, layoutSettings);
                clusterManager.getManager().setMaxLayerLength(layoutSettings.get(Integer.class, MAX_BLOCK_LAYER_LENGTH));
                clusterManager.getSubManager().setMaxLayerLength(layoutSettings.get(Integer.class, MAX_LAYER_LENGTH));
                clusterManager.doLayout(graph);
            } else {
                manager = new HierarchicalLayoutManager(HierarchicalLayoutManager.Combine.SAME_OUTPUTS, layoutSettings);
                manager.setMaxLayerLength(layoutSettings.get(Integer.class, MAX_LAYER_LENGTH));
                manager.doLayout(graph);
            }
            if (cancelled()) {
                return;
            }
            outputDiagram.setSize(graph.getSize());
            LOG.log(Level.FINE, "outputDiagram figure size: {0}", outputDiagram.getFigures().size());
        }
    }

    private static class Extraction extends DiagramCacheTask {

        HierarchicalLayoutManager manager;
        HierarchicalClusterLayoutManager clusterManager;

        @Override
        public void cancel() {
            super.cancel();
            if (manager != null) {
                manager.cancel();
            }
            if (clusterManager != null) {
                clusterManager.cancel();
            }
        }

        public Extraction(Diagram baseDiagram, DiagramViewModel model, DiagramCacheUpdater updater) {
            super(baseDiagram, model, updater);
        }

        @Override
        protected void execute() {
            LayoutSettingBean layoutSettings = model.getLayoutSetting();
            Set<InputNode> hiddenNodes = model.getHiddenGraphNodes();
            assert !hiddenNodes.isEmpty() : "Diagram should never be extracted without hidden nodes.";
            updateHiddenFigures(hiddenNodes);
            if (cancelled()) {
                return;
            }
            HashSet<Figure> figures = new HashSet<>();
            HashSet<Connection> edges = new HashSet<>();
            for (Figure f : outputDiagram.getFigures()) {
                if (f.isVisible()) {
                    figures.add(f);
                }
            }
            for (Connection c : outputDiagram.getConnections()) {
                Figure f1 = c.getOutputSlot().getFigure();
                Figure f2 = c.getInputSlot().getFigure();
                if (f1.isVisible() && f2.isVisible()) {
                    edges.add(c);
                }
            }
            if (cancelled()) {
                return;
            }
            LayoutGraph graph = new LayoutGraph(edges, figures);
            if (model.getShowBlocks()) {
                clusterManager = new HierarchicalClusterLayoutManager(HierarchicalLayoutManager.Combine.SAME_OUTPUTS, layoutSettings);
                clusterManager.getManager().setMaxLayerLength(layoutSettings.get(Integer.class, MAX_BLOCK_LAYER_LENGTH));
                clusterManager.getSubManager().setMaxLayerLength(layoutSettings.get(Integer.class, MAX_LAYER_LENGTH));
                if (layoutSettings.get(Boolean.class, STABILIZED_LAYOUT) && !layoutSettings.get(Boolean.class, DEFAULT_LAYOUT)) {
                    clusterManager.doRouting(graph);
                } else {
                    clusterManager.doLayout(graph);
                }
            } else {
                manager = new HierarchicalLayoutManager(HierarchicalLayoutManager.Combine.SAME_OUTPUTS, layoutSettings);
                manager.setMaxLayerLength(layoutSettings.get(Integer.class, MAX_LAYER_LENGTH));
                if (layoutSettings.get(Boolean.class, STABILIZED_LAYOUT) && !layoutSettings.get(Boolean.class, DEFAULT_LAYOUT)) {
                    manager.doRouting(graph);
                } else {
                    manager.doLayout(graph);
                }
            }
            if (cancelled()) {
                return;
            }
            outputDiagram.setSize(graph.getSize());
            LOG.log(Level.FINE, "outputDiagram figure size: {0}", outputDiagram.getFigures().size());
        }

        private void updateHiddenFigures(Set<InputNode> newHiddenNodes) {
            assert outputDiagram != null;
            assert !SwingUtilities.isEventDispatchThread();
            InputGraph g = outputDiagram.getGraph();
            Set<InputBlock> visibleBlocks = new HashSet<>();
            int hiddenCount = 0;
            for (Figure f : outputDiagram.getFigures()) {
                boolean hiddenAfter = doesIntersect(f.getSource().getSourceNodes(), newHiddenNodes);
                f.setVisible(!hiddenAfter);
                f.setBoundary(false);
                if (hiddenAfter) {
                    hiddenCount++;
                } else {
                    for (InputNode n : f.getSource().getSourceNodes()) {
                        visibleBlocks.add(g.getBlock(n));
                    }
                }
                if (cancelled()) {
                    return;
                }
            }
            Set<Figure> boundaryFigures = new HashSet<>();
            // must execute after all figures are awarded with visible/invisible flags
            // to detect a boundary between visible and invisible.
            if (model.getShowNodeHull()) {
                for (Figure f : outputDiagram.getFigures()) {
                    if (!f.isVisible()) {
                        Set<Figure> set = new HashSet<>(f.getPredecessorSet());
                        set.addAll(f.getSuccessorSet());
                        for (Figure neighbor : set) {
                            if (neighbor.isVisible()) {
                                boundaryFigures.add(f);
                                break;
                            }
                        }
                    }
                }
                if (cancelled()) {
                    return;
                }
                for (Figure f : boundaryFigures) {
                    f.setVisible(true);
                    f.setBoundary(true);
                    for (InputNode n : f.getSource().getSourceNodes()) {
                        outputDiagram.getBlock(g.getBlock(n)).setVisible(true);
                    }
                }
                if (cancelled()) {
                    return;
                }
            }
            for (InputBlock ib : visibleBlocks) {
                outputDiagram.getBlock(ib).setVisible(true);
            }
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "updateHiddenFigures: hidden count:{0}, boundaryFigures:{1}", new Object[]{hiddenCount, boundaryFigures.size()});
            }
        }
    }
}
