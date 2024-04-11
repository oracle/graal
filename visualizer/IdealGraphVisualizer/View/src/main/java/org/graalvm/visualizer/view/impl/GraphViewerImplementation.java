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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.graalvm.visualizer.data.services.GraphViewer;
import org.graalvm.visualizer.data.services.InputGraphProvider;
import org.graalvm.visualizer.graph.Figure;
import org.graalvm.visualizer.view.EditorTopComponent;
import org.graalvm.visualizer.view.api.DiagramViewer;
import org.graalvm.visualizer.view.api.DiagramViewerLocator;
import org.graalvm.visualizer.view.api.TimelineModel;
import org.netbeans.api.progress.BaseProgressUtils;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.Cancellable;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;

import jdk.graal.compiler.graphio.parsing.model.*;
import jdk.graal.compiler.graphio.parsing.model.Group.Feedback;

@ServiceProviders({
        @ServiceProvider(service = DiagramViewerLocator.class),
        @ServiceProvider(service = GraphViewer.class)
})
public class GraphViewerImplementation implements DiagramViewerLocator, PropertyChangeListener {
    private final List<ChangeListener> listeners = new ArrayList<>(2);
    private List<Reference<TopComponent>> viewers = new ArrayList<>(2);
    private Reference<InputGraphProvider> lastActive = new WeakReference<>(null);
    private final PropertyChangeListener listener = (PropertyChangeEvent evt) -> {
        if (TopComponent.Registry.PROP_ACTIVATED_NODES.equals(evt.getPropertyName())
                || Mode.PROP_DISPLAY_NAME.equals(evt.getPropertyName())) {
            fireChangeEvent();
        }
    };

    public GraphViewerImplementation() {
        TopComponent.getRegistry().addPropertyChangeListener(WeakListeners.propertyChange(this, TopComponent.getRegistry()));
        lastActive = new WeakReference(null);
        if (SwingUtilities.isEventDispatchThread()) {
            updateLastActiveViewer();
        } else {
            SwingUtilities.invokeLater(this::updateLastActiveViewer);
        }
    }

    private void updateViewers(TopComponent toFront) {
        TopComponent.Registry reg = TopComponent.getRegistry();
        TopComponent active = filter(reg.getActivated());
        Set<TopComponent> all = new HashSet<>();

        Reference<TopComponent> refHead = null;
        boolean changed = false;
        for (TopComponent tc : reg.getOpened()) {
            tc = filter(tc);
            if (tc != null) {
                all.add(tc);
            }
        }
        synchronized (this) {
            for (Iterator<Reference<TopComponent>> itr = viewers.iterator(); itr.hasNext(); ) {
                Reference<TopComponent> obj = itr.next();
                TopComponent tc = obj.get();
                if (tc == null || !all.contains(tc)) {
                    itr.remove();
                    changed = true;
                    continue;
                }
                if (tc == active && refHead == null) {
                    refHead = obj;
                } else if (tc == toFront) {
                    refHead = new WeakReference<>(tc);
                }
                all.remove(tc);
            }
            for (TopComponent tc : all) {
                Reference<TopComponent> rtc = new WeakReference<>(tc);
                if (tc == active || tc == toFront) {
                    refHead = rtc;
                } else {
                    viewers.add(0, rtc);
                }
                changed = true;
            }
            if (refHead != null) {
                if (!viewers.isEmpty() && viewers.get(0) != refHead) {
                    changed = true;
                }
                viewers.remove(refHead);
                viewers.add(0, refHead);
            }
        }
        if (changed) {
            fireChangeEvent();
        }
    }

    private TopComponent filter(TopComponent tc) {
        if (tc == null) {
            return null;
        }
        InputGraphProvider p = tc.getLookup().lookup(InputGraphProvider.class);
        return p != null ? tc : null;
    }

    private void updateLastActiveViewer() {
        updateViewers(TopComponent.getRegistry().getActivated());
        /*
        InputGraphProvider a = lastActive.get();
        EditorTopComponent ecomp = EditorTopComponent.getActive();
        InputGraphProvider now = null;
        if (ecomp != null) {
            now = ecomp.getLookup().lookup(InputGraphProvider.class);
        } else {
            ecomp = null;
        }
        if (now != a) {
            lastActive = new WeakReference<>(now);
            fireChangeEvent();
        }
        if (ecomp != null) {
            updateViewers(ecomp);
        }
        */
    }

    @Override
    public void addChangeListener(ChangeListener l) {
        synchronized (this) {
            listeners.add(l);
        }
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
        synchronized (this) {
            listeners.remove(l);
        }
    }

    private void fireChangeEvent() {
        ChangeListener[] ll;
        synchronized (this) {
            if (listeners.isEmpty()) {
                return;
            }
            ll = listeners.toArray(new ChangeListener[listeners.size()]);
        }
        ChangeEvent e = new ChangeEvent(this);
        for (ChangeListener l : ll) {
            l.stateChanged(e);
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (TopComponent.Registry.PROP_TC_OPENED.equals(evt.getPropertyName()) && (evt.getNewValue() instanceof EditorTopComponent)) {
            ((EditorTopComponent) evt.getNewValue()).addPropertyChangeListener(/*Mode.PROP_DISPLAY_NAME,*/listener);
            updateViewers((TopComponent) evt.getNewValue());
        }
        if (TopComponent.Registry.PROP_TC_CLOSED.equals(evt.getPropertyName()) && (evt.getNewValue() instanceof EditorTopComponent)) {
            ((EditorTopComponent) evt.getNewValue()).removePropertyChangeListener(/*Mode.PROP_DISPLAY_NAME,*/listener);
            updateViewers(null);
        }
        if (TopComponent.Registry.PROP_ACTIVATED.equals(evt.getPropertyName())) {
            updateLastActiveViewer();
        }
    }

    @NbBundle.Messages({
            "# {0} - graph name",
            "MSG_PrepareGraph=Preparing graph {0}, please wait"
    })
    @Override
    public void view(BiConsumer<Boolean, InputGraphProvider> viewerInit, InputGraph graph, boolean clone, boolean activate, Object... parameters) {
        if (!clone) {
            List<DiagramViewer> viewers = findCompatible(graph);
            if (!viewers.isEmpty()) {
                for (DiagramViewer v : viewers) {
                    TopComponent tc = v.getLookup().lookup(TopComponent.class);
                    initAndRunWithProgress(graph, () -> {
                        v.getModel().selectGraph(graph);
                        if (tc != null) {
                            if (activate) {
                                tc.requestActive();
                            } else {
                                tc.requestVisible();
                            }
                        }
                        if (viewerInit != null) {
                            viewerInit.accept(false, v);
                        }
                    });
                    return;
                }
            }
        }
        initAndRunWithProgress(graph, () -> openSimple(graph, activate, viewerInit));
    }

    // For testing only
    static TimelineModel createTimeline(InputGraph graph) {
        String type = classifyGraphType(graph);
        TimelineModel mdl = new TimelineModelImpl(graph.getGroup(), GraphClassifier.DEFAULT_CLASSIFIER, type);
        return mdl;
    }

    private void openSimple(InputGraph graph, boolean activate, BiConsumer<Boolean, InputGraphProvider> viewerInit) {
        TimelineModel mdl = createTimeline(graph);
        EditorTopComponent tc = new EditorTopComponent(graph, mdl);
        if (viewerInit != null) {
            viewerInit.accept(true, tc.getViewer());
        }
        tc.open();
        if (activate) {
            tc.requestActive();
        }
        tc.requestVisible();

        // attempt to focus/locate the lowest numbered node; must do after the graph/diagram fully loads
        // because nodes may be removed by filters etc.
        tc.getModel().withDiagramToView((dg) -> {
            InputGraph g = dg.getGraph();
            ArrayList<Integer> ids = new ArrayList<>(g.getNodeIds());
            Collections.sort(ids);//Ids aren't sorted in graph
            for (int i : ids) {
                Figure f = dg.getFigureById(i);
                if (f != null && f.isVisible()) {
                    tc.whenReady().execute(() -> tc.setSelectedFigures(Collections.singletonList(f)));
                    break;
                }
            }
        });
    }

    static void initAndRunWithProgress(InputGraph graph, Runnable swingRunnable) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> initAndRunWithProgress(graph, swingRunnable));
            return;
        }
        if (graph instanceof Group.LazyContent) {
            processLargeGraph(graph, swingRunnable);
        } else {
            swingRunnable.run();
        }
    }

    private static void processLargeGraph(InputGraph graph, Runnable runNext) {
        final Group.LazyContent lazy = (Group.LazyContent) graph;
        String title = Bundle.MSG_PrepareGraph(graph.getName());

        class F implements Feedback, Cancellable, Runnable {
            private boolean start;
            private final ProgressHandle ph = ProgressHandle.createHandle(title, this);
            private final AtomicBoolean cancelled = new AtomicBoolean();
            private volatile Future waitFor;
            private int lastTotal;

            @Override
            public void reportProgress(int workDone, int totalWork, String description) {
                synchronized (this) {
                    if (!start) {
                        if (totalWork == -1) {
                            ph.start();
                        } else {
                            ph.start(totalWork);
                        }
                        start = true;
                    }
                }
                if (totalWork != lastTotal) {
                    ph.switchToDeterminate(totalWork);
                }
                if (description != null) {
                    if (totalWork > 0) {
                        ph.progress(description, workDone);
                    } else {
                        ph.progress(description);
                    }
                } else if (totalWork > 0) {
                    ph.progress(workDone);
                }
                lastTotal = totalWork;
            }

            @Override
            public boolean isCancelled() {
                return cancelled.get();
            }

            @Override
            public synchronized void finish() {
                ph.finish();
            }

            @Override
            public boolean cancel() {
                cancelled.set(true);
                waitFor.cancel(true);
                return true;
            }

            @Override
            public void run() {
                waitFor = lazy.completeContents(this);
                try {
                    waitFor.get();
                } catch (InterruptedException | CancellationException ex) {
                    return;
                } catch (ExecutionException ex) {
                    Exceptions.printStackTrace(ex);
                }
                if (!cancelled.get()) {
                    SwingUtilities.invokeLater(runNext);
                }
            }

            @Override
            public void reportError(List<FolderElement> parents, List<String> parentNames, String name, String errorMessage) {
                // was already reported during graph scan
            }
        }
        F feedback = new F();
        BaseProgressUtils.runOffEventThreadWithProgressDialog(feedback, title, feedback.ph, false, 200, 3000);
    }

    @Override
    public DiagramViewer getActiveViewer() {
        synchronized (this) {
            if (viewers.isEmpty()) {
                return null;
            }
            for (Reference<TopComponent> tcs : viewers) {
                TopComponent tc = tcs.get();
                if (tc != null) {
                    DiagramViewer res = tc.getLookup().lookup(DiagramViewer.class);
                    if (res != null) {
                        return res;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public InputGraph getActiveGraph() {
        DiagramViewer vwr = getActiveViewer();
        return vwr != null ? vwr.getGraph() : null;
    }

    @Override
    public List<DiagramViewer> getViewers() {
        List<DiagramViewer> result;
        boolean update = false;
        synchronized (this) {
            result = new ArrayList<>(viewers.size());
            for (Reference<TopComponent> r : viewers) {
                TopComponent tc = r.get();
                if (tc == null) {
                    update = true;
                    continue;
                }
                DiagramViewer vwr = tc.getLookup().lookup(DiagramViewer.class);
                result.add(vwr);
            }
        }
        if (update) {
            updateViewers(null);
        }
        return result;
    }

    // for tests only
    protected Stream<DiagramViewer> viewers() {
        return getViewers().stream();
        /*
        return mgr.getModes().stream().sequential().
                        flatMap((mode) -> Arrays.stream(mgr.getOpenedTopComponents(mode))).
                        filter((t) -> t instanceof EditorTopComponent).
                        map((t) -> ((EditorTopComponent) t).getViewer());
*/
    }

    @Override
    public List<DiagramViewer> find(Group g) {
        return viewers().filter((v) -> v.getModel().getContainer().getContentOwner() == g).collect(Collectors.toList());
    }

    @Override
    public List<DiagramViewer> find(InputGraph g) {
        return viewers().filter((v) -> v.getModel().getGraphToView() == g).collect(Collectors.toList());
    }

    @Override
    public List<DiagramViewer> findCompatible(InputGraph g) {
        String gtype = classifyGraphType(g);
        Group gg = g.getGroup();
        if (gtype == null) {
            return find(gg);
        }
        return viewers().filter((v) -> {
            GraphContainer c = v.getModel().getContainer();
            if (c.getContentOwner() != gg) {
                return false;
            }
            return c == c.getContentOwner() || c.accept(g);
        }).collect(Collectors.toList());
    }

    private static String classifyGraphType(InputGraph graph) {
        return graph.getGraphType();
    }
}
