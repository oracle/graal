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

package org.graalvm.visualizer.source.impl;

import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;
import org.graalvm.visualizer.data.services.InputGraphProvider;
import org.graalvm.visualizer.graph.Figure;
import org.graalvm.visualizer.source.GraphSource;
import org.graalvm.visualizer.source.Location;
import org.graalvm.visualizer.source.NodeLocationContext;
import org.graalvm.visualizer.source.NodeLocationEvent;
import org.graalvm.visualizer.source.NodeLocationListener;
import org.graalvm.visualizer.source.SourceUtils;
import org.graalvm.visualizer.view.api.DiagramViewer;
import org.graalvm.visualizer.view.api.DiagramViewerAdapter;
import org.graalvm.visualizer.view.api.DiagramViewerEvent;
import org.graalvm.visualizer.view.api.DiagramViewerListener;
import org.graalvm.visualizer.view.api.DiagramViewerLocator;
import org.openide.cookies.EditorCookie;
import org.openide.modules.OnStart;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.util.RequestProcessor;
import org.openide.util.Utilities;
import org.openide.util.WeakListeners;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Synchronizes {@link NodeLocationContext} between the graph viewer and editors.
 *
 * @author sdedic
 */
@OnStart
public class LocContextSynchronizer implements PropertyChangeListener, Runnable, LookupListener, NodeLocationListener {
    private final NodeLocationContext locContext;
    private final TopComponent.Registry registry;
    private Reference<TopComponent> lastGraphComponentRef = new WeakReference<>(null);

    private boolean inSync;

    public LocContextSynchronizer() {
        locContext = Lookup.getDefault().lookup(NodeLocationContext.class);
        registry = WindowManager.getDefault().getRegistry();
        registry.addPropertyChangeListener(WeakListeners.propertyChange(this, registry));
        locContext.addNodeLocationListener(WeakListeners.create(NodeLocationListener.class, this, locContext));
    }

    @Override
    public void run() {
    }

    class R extends WeakReference<TopComponent> implements Runnable {
        public R(TopComponent referent) {
            super(referent, Utilities.activeReferenceQueue());
        }

        @Override
        public void run() {
            cleanupIfLast(this);
        }
    }

    private void cleanupIfLast(Reference<TopComponent> ref) {
        synchronized (this) {
            if (this.lastGraphComponentRef != ref) {
                return;
            }
        }
        locContext.setGraphContext(null, Collections.emptyList());
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String p = evt.getPropertyName();
        if (TopComponent.Registry.PROP_TC_CLOSED.equals(p)) {
            TopComponent cl = (TopComponent) evt.getNewValue();
            TopComponent last = lastGraphComponentRef.get();
            if (cl == last) {
                synchronized (this) {
                    lastGraphComponentRef = new WeakReference(null);
                }
                locContext.setGraphContext(null, Collections.emptyList());
            }
            return;
        }
        if (!TopComponent.Registry.PROP_ACTIVATED_NODES.equals(p)) {
            return;
        }
        if (inSync) {
            return;
        }
        TopComponent tc = registry.getActivated();
        if (tc == null || !tc.isOpened()) {
            return;
        }
        InputGraph graph = findGraph(tc);
        TopComponent last = lastGraphComponentRef.get();
        Collection<InputNode> graphNodes = findNodes(tc, graph);

        if (graphNodes.isEmpty()) {
            EditorCookie ck = tc.getLookup().lookup(EditorCookie.class);
            if (ck != null) {
                // check that a graph viewer is available
                DiagramViewerLocator loc = Lookup.getDefault().lookup(DiagramViewerLocator.class);
                if (loc != null && loc.getActiveViewer() != null) {
                    JEditorPane[] panes = ck.getOpenedPanes();
                    if (panes != null) {
                        for (JEditorPane ep : panes) {
                            if (SwingUtilities.isDescendingFrom(ep, tc)) {
                                updateContextFromEditor(ep);
                                return;
                            }
                        }
                    }
                }
            } else {
                if (evListener != null) {
                    evListener.detach();
                    evListener = null;
                }
            }
        } else {
            synchronized (this) {
                if (tc != last) {
                    lastGraphComponentRef = new R(tc);
                }
                last = tc;
            }
        }
        if (graph == null && last != null) {
            return;
        }
        inSync = true;
        try {
            locContext.setGraphContext(graph, graphNodes);
        } finally {
            inSync = false;
        }
    }

    private static final RequestProcessor EDITSYNC_RP = new RequestProcessor();

    private RequestProcessor.Task delayedSync;

    /**
     * Attempts to update the shared context based on the editor caret position.
     */
    class EditorAndViewerListener extends DiagramViewerAdapter implements CaretListener, Runnable {
        private final Reference<DiagramViewer> refViewer;
        private final DiagramViewerListener viewerL;
        private final CaretListener caretL;
        private final Reference<JEditorPane> refPane;

        public EditorAndViewerListener(DiagramViewer viewer, JEditorPane pane) {
            this.refViewer = new WeakReference<>(viewer);
            this.refPane = new WeakReference<>(pane);

            caretL = WeakListeners.create(CaretListener.class, this, pane);
            pane.addCaretListener(caretL);
            viewerL = WeakListeners.create(DiagramViewerListener.class, this, viewer);
            viewer.addDiagramViewerListener(viewerL);
        }

        @Override
        public void caretUpdate(CaretEvent e) {
            updateContextFromEditor((JEditorPane) e.getSource());
        }

        @Override
        public void diagramChanged(DiagramViewerEvent ev) {
            JEditorPane pane = refPane.get();
            if (pane == null) {
                detach();
            }
            updateContextFromEditor(pane);
        }

        void detach() {
            JEditorPane p = refPane.get();
            DiagramViewer v = refViewer.get();
            if (p != null && caretL != null) {
                p.removeCaretListener(caretL);
            }
            if (v != null && viewerL != null) {
                v.removeDiagramViewerListener(viewerL);
            }
            refPane.clear();
        }

        boolean matches(DiagramViewer v, JEditorPane p) {
            return refViewer.get() == v && refPane.get() == p;
        }

        @Override
        public void run() {
            if (!SwingUtilities.isEventDispatchThread()) {
                SwingUtilities.invokeLater(this);
                return;
            }
            JEditorPane pane = refPane.get();
            if (pane == null || !SwingUtilities.isDescendingFrom(pane, registry.getActivated())) {
                return;
            }

            DiagramViewerLocator locator = Lookup.getDefault().lookup(DiagramViewerLocator.class);
            DiagramViewer viewer = locator.getActiveViewer();
            DiagramViewer cached = refViewer.get();
            if (cached != viewer) {
                return;
            }
            InputGraph graph = viewer.getModel().getGraphToView();
            GraphSource gs = GraphSource.getGraphSource(graph);
            List<Location> lineLocs = new ArrayList<>();
            Collection<InputNode> nodes = SourceUtils.findLineNodes(pane, gs, lineLocs, true);
            if (nodes == null || nodes.isEmpty()) {
                // take a little help: from "traversing" nodes, find such ones that were active
                // in the graph
                nodes = new HashSet<>(SourceUtils.findLineNodes(pane, gs, lineLocs, false));
                nodes.retainAll(locContext.getContextNodes());
            }
            SourceUtils.resolveSelectableNodes(nodes, viewer, (nn) -> {
                if (!nn.isEmpty()) {
                    inSync = true;
//                    locContext.setSelectedNodes(nn);
                    inSync = false;
                }
            }, true);
        }
    }

    private EditorAndViewerListener evListener;

    private void updateContextFromEditor(JEditorPane pane) {
        if (!SwingUtilities.isDescendingFrom(pane, registry.getActivated())) {
            return;
        }
        DiagramViewerLocator locator = Lookup.getDefault().lookup(DiagramViewerLocator.class);
        DiagramViewer viewer = locator.getActiveViewer();
        boolean immediate = false;
        if (evListener != null) {
            if (!evListener.matches(viewer, pane)) {
                evListener.detach();
                evListener = null;
                delayedSync = null;
            }
        }
        if (evListener == null) {
            if (pane == null || viewer == null) {
                return;
            }
            evListener = new EditorAndViewerListener(viewer, pane);
            immediate = true;
        }
        if (immediate) {
            evListener.run();
        } else {
            if (delayedSync == null) {
                delayedSync = EDITSYNC_RP.create(evListener);
            }
            delayedSync.cancel();
            delayedSync.schedule(200);
        }
    }

    private InputGraph findGraph(TopComponent tc) {
        if (tc == null) {
            return null;
        }
        InputGraphProvider provider = tc.getLookup().lookup(InputGraphProvider.class);
        if (provider == null) {
            return null;
        }
        return provider.getGraph();
    }

    private Collection<InputNode> findNodes(TopComponent tc) {
        return findNodes(tc, findGraph(tc));
    }

    private Collection<InputNode> findNodes(TopComponent tc, InputGraph graph) {
        if (graph == null) {
            return Collections.emptyList();
        }
        Node[] nodes = tc.getActivatedNodes();
        if (nodes == null) {
            return Collections.emptyList();
        }
        List<InputNode> graphNodes = new ArrayList<>(nodes.length);
        DiagramViewer vwr = tc.getLookup().lookup(DiagramViewer.class);
        for (Node n : nodes) {
            InputNode in = n.getLookup().lookup(InputNode.class);
            if (in != null) {
                InputGraph g = n.getLookup().lookup(InputGraph.class);
                if (g != null) {
                    if (graph != null && graph != g) {
                        // do not change context if nodes from more graphs are selected (unlikely)
                        return Collections.emptyList();
                    }
                }
                if (vwr != null) {
                    Collection<Figure> figs = vwr.figuresForNodes(Collections.singletonList(in));
                    if (figs.size() != 1) {
                        continue;
                    }
                }
                graphNodes.add(in);
            }
        }
        return graphNodes;
    }

    @Override
    public void resultChanged(LookupEvent ev) {
        TopComponent tc = lastGraphComponentRef.get();

        Collection<InputNode> nodes = findNodes(tc);
        if (!nodes.isEmpty()) {
            InputGraph graph = tc.getLookup().lookup(InputGraph.class);
            locContext.setGraphContext(graph, nodes);
        }
    }

    @Override
    public void selectedNodeChanged(NodeLocationEvent evt) {
    }

    @Override
    public void nodesChanged(NodeLocationEvent evt) {
        if (inSync) {
            return;
        }
        Collection<InputNode> nodes = evt.getNodes();
        InputGraph graph = evt.getContext().getGraph();
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        DiagramViewerLocator loc = Lookup.getDefault().lookup(DiagramViewerLocator.class);
        List<DiagramViewer> vwrList = loc.find(graph);
        if (vwrList == null || vwrList.isEmpty()) {
            return;
        }
        InputNode selNode = evt.getSelectedNode();
        if (selNode != null) {
            nodes = Collections.singletonList(selNode);
        }
        Collection<InputNode> fNodes = new ArrayList<>(nodes);
        DiagramViewer vwr = vwrList.iterator().next();

        SwingUtilities.invokeLater(() -> {
            Collection<Figure> figs = vwr.figuresForNodes(fNodes);
            if (!figs.isEmpty()) {
                vwr.centerFigures(Collections.singletonList(figs.iterator().next()));
            }
        });
    }

    @Override
    public void locationsResolved(NodeLocationEvent evt) {
    }

    @Override
    public void selectedLocationChanged(NodeLocationEvent evt) {
    }

}
