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
import java.beans.PropertyChangeSupport;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.*;

import org.graalvm.visualizer.util.ListenerSupport;
import org.graalvm.visualizer.util.RangeSliderModel;
import org.graalvm.visualizer.view.api.TimelineEvent;
import org.graalvm.visualizer.view.api.TimelineListener;
import org.graalvm.visualizer.view.api.TimelineModel;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;
import org.openide.util.Task;
import org.openide.util.TaskListener;

import jdk.graal.compiler.graphio.parsing.model.*;

/**
 * Captures several "timelines" represented by different graph types
 * in a single {@link Group}. The Timeline contains several (currently 2) {@link RangeSliderModel}s
 * each representing one graph type filtered into a separate {@link GraphContainer}.
 * <p/>
 * Graphs which are not included in {@code classifier.getKnownTypes()} will be filtered out entirely
 * <p/>
 * Threading: all refreshes happen in EDT, so that events from RangeSliders are dispatched in EDT.
 * Changes to underlying {@linkplain GraphContainer GraphContainers} are pooled (200ms) and then a refresh is
 * scheduled in EDT.
 *
 * @author sdedic
 */
public class TimelineModelImpl implements TimelineModel, ChangedListener<Group>, Runnable {
    private static final Logger LOG = Logger.getLogger(TimelineModel.class.getName());

    private static final RequestProcessor LOAD_RP = new RequestProcessor(TimelineModelImpl.class.getName());

    /**
     * Unknown graph type; the 99 prefix will order it to the last timeline row
     */
    private static final String TYPE_DEFAULT_LAST = "99_default";

    /**
     * The underlying storage
     */
    private final Group storage;

    /**
     * Used to filter out graphs of an unknown type (i.e. not included in {@link GraphClassifier#knownGraphTypes()}).
     */
    private final GraphClassifier classifier;

    private final PropertyChangeSupport propSupport = new PropertyChangeSupport(this);

    /**
     * Primary type of the graph
     */
    private final String primaryType;

    /**
     * The primary container
     */
    private final GraphContainer primaryContainer;

    /**
     * The primary slider model
     */
    private RangeSliderAccess primaryModel;

    private boolean hideDuplicates;

    // @GuardedBy (this)
    private Map<String, GraphContainer> containers;

    /**
     * Slider models for specific graph types
     */
    // @GuardedBy (this)
    private Map<String, RangeSliderAccess> sliderModels;

    private RequestProcessor.Task refreshTask;

    private Set<Integer> trackedNodes = new HashSet<>();

    private List<TimelineListener> listeners = new ArrayList<>();

    private boolean suppressRefireEvents;

    /**
     * Refires events from container RangeSliders so that listeners can have a single
     * point of interest.
     */
    private class L implements PropertyChangeListener, ChangedListener<RangeSliderModel> {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            fireRangePropertyChanged(evt);
        }

        @Override
        public void changed(RangeSliderModel source) {
            if (!suppressRefireEvents) {
                fireRangeChanged(source);
            }
        }
    }

    public TimelineModelImpl(Group storage, GraphClassifier classifier, String primaryType) {
        this.storage = storage;
        this.classifier = classifier;
        this.primaryContainer = createContainer(primaryType);
        this.primaryModel = createSlider(primaryType);
        // initialize primaryType after the object creation, so model/container are
        // created at all.
        this.primaryType = primaryType;
        ListenerSupport.addWeakListener(this, storage.getChangedEvent());
        scheduleRefresh(true);
    }

    private GraphContainer createContainer(String type) {
        if (Objects.equals(primaryType, type)) {
            return primaryContainer;
        }
        partitionsChanged = true;
        GraphTypeContainer gtc = new GraphTypeContainer(storage, type, null);
        return gtc;
    }

    private RangeSliderAccess createSlider(String type) {
        if (Objects.equals(primaryType, type)) {
            return primaryModel;
        }
        RangeSliderAccess a = new RangeSliderAccess(Arrays.asList("dummy")); // NOI18N
        a.addPropertyChangeListener(partitionListener);
        a.getChangedEvent().addListener(partitionListener);
        a.oldPos1 = a.getFirstPosition();
        a.oldPos2 = a.getSecondPosition();
        return a;
    }

    @Override
    public void addTimelineListener(TimelineListener l) {
        synchronized (this) {
            listeners.add(l);
        }
    }

    @Override
    public void removeTimelineListener(TimelineListener l) {
        synchronized (this) {
            listeners.remove(l);
        }
    }

    private synchronized void ensureInitialized() {
        if (sliderModels != null) {
            return;
        }

        scheduleRefresh();
        Map<String, GraphContainer> ncmap = new HashMap<>();
        Map<String, RangeSliderAccess> sliders = new LinkedHashMap<>();
        ncmap.put(primaryType, createContainer(primaryType));
        RangeSliderAccess mdl = createSlider(primaryType);
        sliders.put(primaryType, mdl);

        sliderModels = sliders;
        containers = ncmap;
    }

    @Override
    public String getPrimaryType() {
        return primaryType;
    }

    @Override
    public void changed(Group source) {
        scheduleRefresh();
    }

    void scheduleRefresh() {
        scheduleRefresh(false);
    }

    final void scheduleRefresh(boolean immediately) {
        synchronized (this) {
            if (refreshTask != null) {
                LOG.log(Level.FINER, "{0}: already scheduled", this);
                return;
            }
            LOG.log(Level.FINER, "{0}: Schedule refresh delay", this);
            refreshTask = LOAD_RP.post(this, immediately ? 0 : 200);
        }
    }

    void _testCompleteRefresh() {
        RequestProcessor.Task rt;
        synchronized (this) {
            rt = refreshTask;
        }
        if (rt != null) {
            rt.waitFinished();
        }
        try {
            SwingUtilities.invokeAndWait(() -> {
            });
        } catch (InterruptedException | InvocationTargetException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    @Override
    public void run() {
        synchronized (this) {
            refreshTask = null;
        }
        // gather all the elements, but replan the refresh to EDT
        List<InputGraph> els = storage.getGraphs();
        LOG.log(Level.FINE, "{0}: Scheduling EDT refresh", this);
        SwingUtilities.invokeLater(() -> refresh(els));
    }

    private boolean partitionsChanged;

    private L partitionListener = new L();

    private synchronized String findRangeType(RangeSliderModel mdl) {
        ensureInitialized();
        for (Map.Entry<String, RangeSliderAccess> e : sliderModels.entrySet()) {
            if (e.getValue() == mdl) {
                return e.getKey();
            }
        }
        return null;
    }

    /**
     * @param mdl
     */
    private void fireRangeChanged(RangeSliderModel mdl) {
        RangeSliderAccess acc = (RangeSliderAccess) mdl;
        int p1 = mdl.getFirstPosition();
        int p2 = mdl.getSecondPosition();

        String type = findRangeType(mdl);
        if (type == null) {
            return;
        }
        if (p1 == acc.oldPos1 && p2 == acc.oldPos2) {
            return;
        }
        TimelineEvent ev = new TimelineEvent(this, mdl, type, acc.oldPos1, acc.oldPos2);
        acc.oldPos1 = p1;
        acc.oldPos1 = p2;
        if (mdl == getPrimaryRange()) {
            fireTimelineEvent(ev, TimelineListener::primaryRangeChanged);
        } else {
            fireTimelineEvent(ev, TimelineListener::rangeChanged);
        }
    }

    private void fireRangePropertyChanged(PropertyChangeEvent e) {
        RangeSliderModel mdl = (RangeSliderModel) e.getSource();
        String type = findRangeType(mdl);
        if (type == null) {
            return;
        }
        fireTimelineEvent(new TimelineEvent(this, mdl, type, e), TimelineListener::rangePropertyChanged);
    }

    private void fireTimelineEvent(TimelineEvent e, BiConsumer<TimelineListener, TimelineEvent> f) {
        TimelineListener[] ll;

        synchronized (this) {
            if (listeners.isEmpty()) {
                return;
            }
            ll = listeners.toArray(new TimelineListener[listeners.size()]);
        }
        for (TimelineListener l : ll) {
            f.accept(l, e);
        }
    }

    void refresh(List<InputGraph> contents) {
        Map<String, GraphContainer> ncmap;
        LOG.log(Level.FINE, "{0}: Refreshing...", this);
        synchronized (this) {
            ncmap = containers == null ? new HashMap<>() : new HashMap<>(containers);
        }
        Map<String, List<Integer>> indices = new HashMap<>();
        // FIXME: useless copy
        List<InputGraph> els = new ArrayList<>(contents);
        partitionsChanged = false;
        int b = 0;
        Set<String> usedTypes = new HashSet<>();
        for (Iterator<InputGraph> it = els.iterator(); it.hasNext(); ) {
            InputGraph graph = it.next();
            String t = graph.getGraphType();

            if (t == null) {
                t = TYPE_DEFAULT_LAST; // NOI18N
            } else if (!classifier.knownGraphTypes().contains(t)) {
                // do not include
                it.remove();
                continue;
            }
            ncmap.computeIfAbsent(t, this::createContainer);
            if (isHideDuplicates() && graph.isDuplicate()) {
                it.remove();
                continue;
            }
            usedTypes.add(t);
            indices.computeIfAbsent(t, (k) -> new ArrayList<>(els.size())).add(b);
            b++;
        }
        ncmap.keySet().retainAll(usedTypes);
        List<String> keys = new ArrayList<>(ncmap.keySet());
        Collections.sort(keys);

        Map<String, RangeSliderAccess> nsliders;

        synchronized (this) {
            nsliders = sliderModels == null ?
                    new LinkedHashMap<>() : new LinkedHashMap<>(sliderModels);
            Collection<RangeSliderAccess> oldSliders = new ArrayList<>(nsliders.values());
            nsliders.put(primaryType, primaryModel);

            // do update individual planes
            for (String k : keys) {
                nsliders.computeIfAbsent(k, this::createSlider);
            }

            oldSliders.removeAll(nsliders.values());
            for (RangeSliderAccess a : oldSliders) {
                a.removePropertyChangeListener(partitionListener);
                a.getChangedEvent().removeListener(partitionListener);
            }
            sliderModels = nsliders;
            containers = ncmap;
        }

        LOG.log(Level.FINE, "{0}: Updating timeline", this);

        // do update individual planes
        for (String k : keys) {
            RangeSliderAccess mdl = nsliders.get(k);
            List<Integer> itemIndices = indices.get(k);
            List<String> names = new ArrayList<>(itemIndices.size());
            Map<String, Integer> m = new HashMap<>();
            for (int i : itemIndices) {
                String s = els.get(i).getName();
                names.add(s);
                m.put(s, i);
            }
            if (mdl != primaryModel) {
                suppressRefireEvents = true;
            }
            try {
                // exploit the fact we can suppress change events for some time.
                mdl.getChangedEvent().beginAtomic();
                mdl.setPositions(names);
                mdl.setIndices0(m);
            } finally {
                mdl.getChangedEvent().endAtomic();
                if (mdl != primaryModel) {
                    suppressRefireEvents = false;
                }
            }
        }

        boolean c = partitionsChanged;
        if (c) {
            fireOwnPropertyChange(PROP_PARTITIONS, null, null);
        }
    }

    /**
     * Fires a property change.
     * The change is fired to both regular PropertyChangeListeners (i.e. framework)
     * and to TimelineListeners, so the client need not to attach two listeners on
     * the same object.
     *
     * @param prop property name
     * @param old  old value
     * @param n    new value.
     */
    private void fireOwnPropertyChange(String prop, Object old, Object n) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> fireOwnPropertyChange(prop, old, n));
            return;
        }
        if (prop != null && (old != null || n != null)) {
            if (Objects.equals(old, n)) {
                return;
            }
        }
        PropertyChangeEvent e = new PropertyChangeEvent(this, prop, old, n);
        fireTimelineEvent(null, (l, ev) -> l.propertyChange(e));
        propSupport.firePropertyChange(e);
    }

    private void updateColors() {
    }

    @Override
    public synchronized boolean isHideDuplicates() {
        return hideDuplicates;
    }

    @Override
    public void setHideDuplicates(boolean ignore) {
        synchronized (this) {
            if (this.hideDuplicates == ignore) {
                return;
            }
            this.hideDuplicates = ignore;
        }
        changed(null);
        fireOwnPropertyChange(PROP_HIDE_DUPLICATES, !ignore, ignore);
    }

    @Override
    public GraphContainer getPartition(String type) {
        ensureInitialized();
        return containers.get(type);
    }

    @Override
    public GraphContainer getPrimaryPartition() {
        return primaryContainer;
    }

    @Override
    public RangeSliderModel getPrimaryRange() {
        ensureInitialized();
        return primaryModel;
    }

    @Override
    public Set<GraphContainer> getPartitions() {
        ensureInitialized();
        return new HashSet<>(containers.values());
    }

    @Override
    public Set<String> getPartitionTypes() {
        ensureInitialized();
        return Collections.unmodifiableSet(containers.keySet());
    }

    @Override
    public RangeSliderModel getPartitionRange(String type) {
        if (Objects.equals(primaryType, type)) {
            return primaryModel;
        } else if (type == null) {
            return null;
        }
        ensureInitialized();
        synchronized (this) {
            return sliderModels.get(type);
        }
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener l) {
        propSupport.addPropertyChangeListener(l);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener l) {
        propSupport.removePropertyChangeListener(l);
    }

    /**
     * Provides access to internals of RangeSliderModel
     */
    private class RangeSliderAccess extends RangeSliderModel {
        /**
         * Tracks position in the range slider model, as it only sends ChangedEvent
         * with no old-pos info.
         */
        private int oldPos1;
        private int oldPos2;

        RangeSliderAccess(List<String> positions) {
            super(positions);
        }

        RangeSliderAccess(RangeSliderModel model) {
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

        protected void setIndices0(Map<String, Integer> indices) {
            setIndices(indices);
        }

        @Override
        public String toString() {
            int f = getFirstPosition();
            int s = getSecondPosition();
            if (f == s) {
                return "(" + f + ")";
            } else {
                return "(" + f + "-" + s + ")";
            }
        }
    }

    @Override
    public void setTrackedNodes(Set<Integer> nodes) {
        trackedNodes = new HashSet<>(nodes);
        updateColors();
    }

    @Override
    public Set<Integer> getTrackedNodes() {
        return Collections.unmodifiableSet(trackedNodes);
    }

    @Override
    public InputGraph findGraph(RangeSliderModel model, int position) {
        List<String> posNames = model.getPositions();
        if (position < 0 || position >= posNames.size()) {
            return null;
        }
        String name = posNames.get(position);
        String type = findRangeType(model);
        GraphContainer cont = getPartition(type);
        for (InputGraph ig : cont.getGraphs()) {
            if (name.equals(ig.getName())) {
                return ig;
            }
        }
        return null;
    }

    @Override
    public GraphContainer getSource() {
        return storage;
    }

    private void doExecuteWhenStable(Runnable r) {
        Task t;
        synchronized (this) {
            t = refreshTask;
        }
        if (t != null) {
            LOG.log(Level.FINER, "{0}: Task pending, adding listener; {1}", new Object[]{this, t.isFinished()});
            t.addTaskListener(new TaskListener() {
                @Override
                public void taskFinished(Task task) {
                    LOG.log(Level.FINER, "{0}: Task finished, invoking in EDT", this);
                    SwingUtilities.invokeLater(r);
                }
            });
        } else {
            LOG.log(Level.FINER, "{0}: No task, going EDT", this);
            SwingUtilities.invokeLater(r);
        }
    }

    private final Executor awtExecutor = new Executor() {
        @Override
        public void execute(Runnable command) {
            doExecuteWhenStable(command);
        }
    };

    @Override
    public Executor whenStable() {
        return awtExecutor;
    }

    @Override
    public String toString() {
        return "Timeline[" + primaryType + ", range=" + primaryModel + ", max=" + primaryModel.getPositions().size() + "]";
    }
}
