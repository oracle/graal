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
package org.graalvm.visualizer.filter.profiles.impl;

import jdk.graal.compiler.graphio.parsing.model.ChangedEvent;
import jdk.graal.compiler.graphio.parsing.model.ChangedListener;
import jdk.graal.compiler.graphio.parsing.model.GraphContainer;
import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import org.graalvm.visualizer.data.services.GraphViewer;
import org.graalvm.visualizer.data.services.InputGraphProvider;
import org.graalvm.visualizer.filter.DataFilterSelector;
import org.graalvm.visualizer.filter.FilterChain;
import org.graalvm.visualizer.filter.FilterEvent;
import org.graalvm.visualizer.filter.FilterListener;
import org.graalvm.visualizer.filter.FilterSequence;
import org.graalvm.visualizer.filter.profiles.FilterProfile;
import org.graalvm.visualizer.filter.profiles.Profiles;
import org.graalvm.visualizer.filter.profiles.mgmt.ProfileService;
import org.graalvm.visualizer.util.ListenerSupport;
import org.openide.util.Lookup;
import org.openide.util.Utilities;
import org.openide.util.WeakListeners;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.TopComponent;

import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;

/**
 * Determines the filter profile to be used with data. The service maintains a 'sticky' profile
 * associated with a particular TopComponent. New TopComponents get profile based on 'similar' opened
 * TopComponents (that display a Graph of the same type).
 * <p/>
 * When activated TopComponent changes, this service sets the TC's profile into the global {@link ProfileService}
 * as the default; similar, if the user changes the selected profile, it will remember the setting for the
 * currently active (or last active) TopComponent.
 *
 * @author sdedic
 */
@ServiceProvider(service = DataFilterSelector.class)
public class FilterProfileSelector implements DataFilterSelector, ChangeListener, PropertyChangeListener {
    private final GraphViewer viewerService;
    private final ProfileService profileService;

    /**
     * Profiles assigned to individual graph collections.
     */
    private final Map<TopComponent, FilterProfileSwitcher> stickyProfiles = new WeakHashMap<>();

    public FilterProfileSelector() {
        this(
                Lookup.getDefault().lookup(GraphViewer.class),
                Lookup.getDefault().lookup(ProfileService.class),
                TopComponent.getRegistry());
    }

    FilterProfileSelector(GraphViewer viewers, ProfileService profiles, TopComponent.Registry tcreg) {
        this.viewerService = viewers;
        this.profileService = profiles;

        viewerService.addChangeListener(this);
        tcreg.addPropertyChangeListener(WeakListeners.propertyChange(this, TopComponent.getRegistry()));
        profileService.addPropertyChangeListener(WeakListeners.propertyChange(this, ProfileService.PROP_SELECTED_PROFILE, profileService));
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getSource() == profileService) {
            if (ProfileService.PROP_SELECTED_PROFILE.equals(evt.getPropertyName())) {
                changeStickyProfile(profileService.getSelectedProfile());
            }
            return;
        }
        if (evt.getPropertyName() == null) {
            updateSelection();
        } else switch (evt.getPropertyName()) {
            case TopComponent.Registry.PROP_ACTIVATED:
            case TopComponent.Registry.PROP_TC_OPENED:
                updateSelection();
        }
    }

    private void updateSelection() {
        TopComponent active = TopComponent.getRegistry().getActivated();
        if (active == null) {
            return;
        }
        InputGraphProvider igp = active.getLookup().lookup(InputGraphProvider.class);
        if (igp == null) {
            return;
        }
        FilterProfile toSelect;
        FilterProfileSwitcher selected;
        synchronized (this) {
            selected = stickyProfiles.get(active);
        }
        toSelect = selected.delegate;
        profileService.setSelectedProfile(toSelect);
    }

    @Override
    public FilterSequence getFilterChain(InputGraph graph, GraphContainer parent, Lookup context) {
        TopComponent tc = context.lookup(TopComponent.class);
        if (tc == null) {
            // fallback:
            return profileService.getDefaultProfile().getSelectedFilters();
        }
        Group g = parent.getContentOwner();

        FilterProfile selected = null;
        synchronized (this) {
            FilterProfileSwitcher p = stickyProfiles.get(tc);
            if (p != null) {
                return p;
            }
            FilterProfileSwitcher blueprint;
            // try to find the same graph/type for case the clone is being opened
            GraphViewer vService = Lookup.getDefault().lookup(GraphViewer.class);
            List<? extends InputGraphProvider> viewers = vService.getViewers();
            for (InputGraphProvider v : viewers) {
                TopComponent vtc = v.getLookup().lookup(TopComponent.class);
                FilterProfileSwitcher sw = stickyProfiles.get(vtc);
                if (sw != null && sw.group == g && Objects.equals(sw.type, graph.getGraphType())) {
                    blueprint = sw;
                    selected = blueprint.delegate;
                    break;
                }
            }
        }
        if (selected == null) {
            List<? extends FilterProfile> candidates = Profiles.selectProfiles(profileService.getProfiles(), graph, parent, context);

            if (candidates.isEmpty()) {
                selected = profileService.getDefaultProfile();
            } else {
                selected = candidates.get(0);
            }
        }
        FilterProfileSwitcher switcher = new FilterProfileSwitcher(g, graph.getGraphType(), selected);
        synchronized (this) {
            stickyProfiles.putIfAbsent(tc, switcher);
            return stickyProfiles.get(tc);
        }
    }

    public void changeStickyProfile(FilterProfile newSelection) {
        InputGraphProvider igp = viewerService.getActiveViewer();
        if (igp == null) {
            return;
        }
        TopComponent tc = igp.getLookup().lookup(TopComponent.class);

        FilterProfileSwitcher switcher;
        synchronized (this) {
            switcher = stickyProfiles.get(tc);
        }
        if (switcher == null) {
            return;
        }
        switcher.setDelegate(newSelection);
    }

    /**
     * Key into the FilterProfile cache.
     */
    private static class Key extends WeakReference<Group> implements Runnable {
        private final Reference<TopComponent> refTC;
        private final String containerType;
        private final int hashRef;

        public Key(Group g, String type, TopComponent tc) {
            super(g, Utilities.activeReferenceQueue());
            this.refTC = new WeakReference<>(tc);
            this.containerType = type;
            hashRef = g.hashCode();
        }

        @Override
        public void run() {
        }

        public boolean equals(Object o) {
            if (!(o instanceof Key)) {
                return false;
            }
            if (o == this) {
                return true;
            }
            Key k = (Key) o;
            if (!Objects.equals(containerType, k.containerType)) {
                return false;
            }
            Group g = get();
            Group kg = k.get();
            if (g == null || kg == null || !g.equals(kg)) {
                return false;
            }
            TopComponent tc = refTC.get();
            TopComponent ktc = k.refTC.get();
            return tc != null && ktc != null && tc.equals(ktc);
        }

        @Override
        public int hashCode() {
            return (hashRef << 7) ^ containerType.hashCode();
        }
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        List<? extends InputGraphProvider> provs = viewerService.getViewers();
        Collection<TopComponent> keys = new HashSet<>();
        for (InputGraphProvider v : provs) {
            InputGraph gr = v.getGraph();
            GraphContainer c = v.getContainer();
            TopComponent tc = v.getLookup().lookup(TopComponent.class);
            if (tc != null) {
                keys.add(tc);
            }
        }
        synchronized (this) {
            stickyProfiles.keySet().retainAll(keys);
        }
        SwingUtilities.invokeLater(this::updateSelection);
    }

    /**
     * Filter sequence, which switches the contents based on filter profile that is set to it.
     */
    private static class FilterProfileSwitcher
            implements FilterSequence<FilterSequence>, FilterListener, ChangedListener<FilterChain> {
        private final ChangedEvent<FilterSequence> event = new ChangedEvent<>(this);
        private final Group group;
        private final String type;

        private FilterProfile delegate;
        private FilterListener weakFilterL;
        private ChangedListener<FilterChain> weakChangeL;
        private List<FilterListener> listeners = null;

        public FilterProfileSwitcher(Group group, String type, FilterProfile delegate) {
            this.group = group;
            this.type = type;

            setDelegate(delegate);
        }

        public void setDelegate(FilterProfile s) {
            synchronized (this) {
                if (s == delegate) {
                    return;
                }
                if (delegate != null) {
                    if (weakFilterL != null) {
                        delegate.getSelectedFilters().removeFilterListener(weakFilterL);
                    }
                    delegate.getSelectedFilters().getChangedEvent().removeListener(weakChangeL);
                }
                this.delegate = s;
                if (s != null) {
                    ChangedListener<FilterChain> cl = ListenerSupport.addWeakListener(this, delegate.getSelectedFilters().getChangedEvent());
                    weakChangeL = cl;

                    if (weakFilterL != null) {
                        attachFilterListener();
                    }
                }
            }
            event.fire();
        }

        private void attachFilterListener() {
            FilterListener fl = WeakListeners.create(FilterListener.class, this, delegate);
            delegate.getSelectedFilters().addFilterListener(fl);
            weakFilterL = fl;
        }

        @Override
        public ChangedEvent<FilterSequence> getChangedEvent() {
            return event;
        }

        @Override
        public List getFilters() {
            return delegate.getSelectedFilters().getFilters();
        }

        @Override
        public void addFilterListener(FilterListener l) {
            synchronized (this) {
                if (listeners == null) {
                    listeners = new ArrayList<>();
                    attachFilterListener();
                }
                listeners.add(l);
            }
        }

        @Override
        public void removeFilterListener(FilterListener l) {
            synchronized (this) {
                listeners.remove(l);
            }
        }

        private void forward(FilterEvent e, BiConsumer<FilterListener, FilterEvent> method) {
            FilterListener[] ll;

            synchronized (this) {
                if (listeners == null || listeners.isEmpty()) {
                    return;
                }
                ll = listeners.toArray(new FilterListener[listeners.size()]);
            }
            FilterEvent e2 = new FilterEvent(e.getExecution(), this, e.getFilter(),
                    e.getFilteredDiagram(), e.getExecutionError());
            for (int i = 0; i < ll.length; i++) {
                method.accept(ll[i], e2);
            }
        }

        @Override
        public void filterStart(FilterEvent e) {
            forward(e, FilterListener::filterStart);
        }

        @Override
        public void filterEnd(FilterEvent e) {
            forward(e, FilterListener::filterEnd);
        }

        @Override
        public void executionStart(FilterEvent e) {
            forward(e, FilterListener::executionStart);
        }

        @Override
        public void executionEnd(FilterEvent e) {
            forward(e, FilterListener::executionEnd);
        }

        @Override
        public void changed(FilterChain source) {
            event.fire();
        }

    }
}
