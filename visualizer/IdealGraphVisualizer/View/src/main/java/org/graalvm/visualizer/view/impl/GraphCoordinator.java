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

import jdk.graal.compiler.graphio.parsing.model.GraphContainer;
import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import org.graalvm.visualizer.data.services.InputGraphProvider;
import org.graalvm.visualizer.util.RangeSliderModel;
import org.graalvm.visualizer.view.api.DiagramViewer;
import org.graalvm.visualizer.view.api.DiagramViewerLocator;
import org.graalvm.visualizer.view.api.TimelineEvent;
import org.graalvm.visualizer.view.api.TimelineListener;
import org.graalvm.visualizer.view.api.TimelineModel;
import org.openide.util.Lookup;
import org.openide.util.WeakListeners;
import org.openide.util.WeakSet;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.TopComponent;

import javax.swing.SwingUtilities;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Coordinates timeline changes between opened viewers.
 * Each viewer has its own timeline. Each viewer can be cloned, so there can be
 * more instances of TimelineModel for each group+type combination.
 * <p/>
 * When a non-primary Timeline is changed in one of the components, the change MAY be propagated
 * to a different view that displays diagrams from the same timeline partition. If there are
 * multiple such viewers, then the first one is selected.
 * <p/>
 * Events are only propagated from <b>active TopComponents</b>. A TopComponent has to be actively
 * registered first by calling {@link #registerSynchronizedModel}.
 * <p/>
 * Threading: all events are received and processed in EDT.
 *
 * @author sdedic
 */
@ServiceProvider(service = GraphCoordinator.class)
public class GraphCoordinator implements PropertyChangeListener, TimelineListener {
    private boolean initialized;
    private final Map<TimelineModel, Reference<TopComponent>> openedTimelines = new HashMap<>();
    private Set<TimelineModel> timelines = Collections.emptySet();
    /**
     * Guards against event recursion. Since primary range will change peer windows' secondaries
     * and a change in a secondary will be synced into a peer's primary, this flag
     * will be raised before the implied rangesliders changes. Events firedfrom the
     * secondary/primary implied change will be ignored.
     * <p/>
     * All sync operations happen in EDT.
     */
    private boolean synchronizing;


    public void registerSynchronizedModel(TimelineModel mdl, TopComponent tc) {
        assert SwingUtilities.isEventDispatchThread();
        synchronized (this) {
            if (timelines.isEmpty()) {
                timelines = new WeakSet<>();
            }
            if (timelines.add(mdl)) {
                mdl.addTimelineListener(this);
                openedTimelines.put(mdl, new WeakReference<>(tc));
            }
        }
        if (!initialized) {
            TopComponent.getRegistry().addPropertyChangeListener(
                    WeakListeners.propertyChange(this, TopComponent.getRegistry()));
            initialized = true;
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (TopComponent.Registry.PROP_OPENED.equals(evt.getPropertyName())) {
            refreshOpenedComponents();
        }
    }

    private void refreshOpenedComponents() {
        assert SwingUtilities.isEventDispatchThread();
        Set<TopComponent> tcs = TopComponent.getRegistry().getOpened();
        Set<TimelineModel> newModels = new HashSet<>();
        for (TopComponent tc : tcs) {
            TimelineModel viewerModel = tc.getLookup().lookup(TimelineModel.class);
            if (viewerModel == null) {
                continue;
            }
            newModels.add(viewerModel);
        }
        Set<TimelineModel> obsolete;
        synchronized (this) {
            obsolete = new HashSet<>(timelines);
            obsolete.removeAll(newModels);
            this.timelines.removeAll(obsolete);
            this.openedTimelines.keySet().removeAll(obsolete);
        }
        for (TimelineModel om : obsolete) {
            om.removeTimelineListener(this);
        }
    }

    /**
     * Synchronizes change in primary range to all other graphs,
     * which display the primary range in its timeline.
     *
     * @param e
     */
    @Override
    public void primaryRangeChanged(TimelineEvent e) {
        avoidRecursion(this::primaryRangeChanged0, e);
    }

    void primaryRangeChanged0(TimelineEvent e) {
        assert SwingUtilities.isEventDispatchThread();
        TimelineModel source = e.getSource();
        RangeSliderModel current = e.getSlider();

        String partition = e.getPartitionType();
        DiagramViewerLocator mgr = Lookup.getDefault().lookup(DiagramViewerLocator.class);

        Group g = source.getPrimaryPartition().getContentOwner();
        List<? extends DiagramViewer> viewers = new ArrayList<>(mgr.find(g));
        // remove this window, so that graph is not replaced.

        for (Iterator<? extends DiagramViewer> it = viewers.iterator(); it.hasNext(); ) {
            DiagramViewer vwr = it.next();
            TopComponent tc = vwr.getLookup().lookup(TopComponent.class);
            if (tc != null) {
                TimelineModel match = tc.getLookup().lookup(TimelineModel.class);
                if (Objects.equals(match.getPrimaryType(), partition)) {
                    it.remove();
                }
            }
        }
        if (viewers.isEmpty()) {
            return;
        }
        String n1 = current.getPositions().get(current.getFirstPosition());
        String n2 = current.getPositions().get(current.getSecondPosition());
        for (DiagramViewer vwr : viewers) {
            TimelineModel tm = vwr.getModel().getTimeline();
            RangeSliderModel mdl = tm.getPartitionRange(partition);
            if (mdl == null) {
                continue;
            }
            int p1 = mdl.getPositions().indexOf(n1);
            int p2 = mdl.getPositions().indexOf(n2);
            if (p1 != -1 && p2 != -1) {
                mdl.setPositions(p1, p2);
                break;
            }
        }
    }

    @Override
    public void partitionsChanged(TimelineEvent e) {
        // not interesting at the moment
    }

    private InputGraph getGraphInstance(GraphContainer c, int pos) {
        if (pos <= 0 || pos >= c.getGraphsCount()) {
            return null;
        } else {
            return c.getGraphs().get(pos);
        }
    }

    private InputGraph getGraphInstance(TimelineModel model, String type, int pos) {
        RangeSliderModel slider = model.getPartitionRange(type);
        if (pos == -1) {
            return null;
        }
        return model.findGraph(slider, pos);
    }

    /**
     * A secondary range has been changed. The Coordinator will look up a candidate whose
     * primary range type matches this one and will sync its view.
     *
     * @param e
     */
    @Override
    public void rangeChanged(TimelineEvent e) {
        avoidRecursion(this::rangeChanged0, e);
    }

    private void avoidRecursion(Consumer<TimelineEvent> c, TimelineEvent e) {
        if (synchronizing) {
            return;
        }
        synchronizing = true;
        try {
            c.accept(e);
        } finally {
            synchronizing = false;
        }
    }

    private void rangeChanged0(TimelineEvent e) {
        assert SwingUtilities.isEventDispatchThread();
        TimelineModel source = e.getSource();
        RangeSliderModel current = e.getSlider();

        String partition = e.getPartitionType();
        int oldFirstPos = e.getFirstGraph();
        int oldSecondPos = e.getSecondGraph();

        // extract graph instances from the original slider's model
        InputGraph oldFirst = getGraphInstance(source, partition, oldFirstPos);
        InputGraph oldSecond = getGraphInstance(source, partition, oldSecondPos);

        InputGraph first = getGraphInstance(source, partition, current.getFirstPosition());
        InputGraph second = getGraphInstance(source, partition, current.getSecondPosition());

        InputGraph anchor = null;

        if (oldFirst != null) {
            anchor = oldFirst;
        } else if (oldSecond != null) {
            anchor = oldSecond;
        }
        DiagramViewerLocator mgr = Lookup.getDefault().lookup(DiagramViewerLocator.class);
        List<? extends DiagramViewer> viewers = Collections.emptyList();
        DiagramViewer candidate = null;
        DiagramViewer secondCandidate = null;
        DiagramViewer thirdCandidate = null;
        RangeSliderModel myRange = source.getPrimaryRange();

        if (anchor != null) {
            // viewers that display the exact graph that WAS selected here.
            viewers = mgr.find(anchor);
        }
        if (viewers.isEmpty()) {
            // try to get viewers compatible - that is within the same group,
            // the same type with the to-be-selected graph:
            if (first != null) {
                anchor = first;
            } else if (second != null) {
                anchor = second;
            }
            if (anchor != null) {
                viewers = mgr.findCompatible(anchor);
            }
        }
        if (viewers.isEmpty()) {
            Group g = source.getPrimaryPartition().getContentOwner();
            viewers = new ArrayList<>(mgr.find(g));
            // remove this window, so that graph is not replaced.
            DiagramViewer exclude = null;
            for (DiagramViewer vwr : viewers) {
                TimelineModel match = vwr.getModel().getTimeline();
                if (match == source) {
                    exclude = vwr;
                    break;
                }
            }
            if (exclude != null) {
                viewers.remove(exclude);
            }
        }

        for (DiagramViewer v : viewers) {
            // try to select a candidate that is in sync with the old state
            TimelineModel tv = v.getModel().getTimeline();
            if (tv == null) {
                continue;
            }
            RangeSliderModel primRange = tv.getPrimaryRange();
            RangeSliderModel matchRange = tv.getPartitionRange(source.getPrimaryType());
            if (thirdCandidate == null) {
                thirdCandidate = v;
            }
            if (primRange.getFirstPosition() == oldFirstPos && primRange.getSecondPosition() == oldSecondPos) {
                if (secondCandidate == null) {
                    secondCandidate = v;
                }
                if (matchRange != null &&
                        matchRange.getFirstPosition() == myRange.getFirstPosition() &&
                        matchRange.getSecondPosition() == myRange.getSecondPosition()) {
                    candidate = v;
                    break;
                }
            }
        }
        if (candidate == null) {
            candidate = secondCandidate;
        }
        if (candidate == null) {
            candidate = thirdCandidate;
        }
        if (candidate != null) {
            GraphContainer targetC = candidate.getModel().getContainer();
            RangeSliderModel targetM = candidate.getModel().getTimeline().getPrimaryRange();

            InputGraph g1 = getGraphInstance(targetC, current.getFirstPosition());
            InputGraph g2 = getGraphInstance(targetC, current.getSecondPosition());

            int pos1 = targetC.getGraphs().indexOf(g1);
            int pos2 = targetC.getGraphs().indexOf(g2);
            if (pos1 == -1) {
                pos1 = pos2;
            }
            if (pos1 != -1) {
                targetM.setPositions(pos1, pos2);
                TopComponent tc = candidate.getLookup().lookup(TopComponent.class);
                if (tc != null) {
                    tc.requestVisible();
                }
            }
        } else {
            // open a new viewer
            class Initializer implements BiConsumer<Boolean, InputGraphProvider> {
                @Override
                public void accept(Boolean t, InputGraphProvider u) {
                    // PEDNING: synchronize the non-primary compartments with
                    // existing viewers.
                }
            }
            mgr.view(new Initializer(), first, false, false);
        }
    }

    @Override
    public void rangePropertyChanged(TimelineEvent e) {
        // not interesting now.
    }
}
