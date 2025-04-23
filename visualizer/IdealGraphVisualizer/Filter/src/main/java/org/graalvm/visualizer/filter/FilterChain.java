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
package org.graalvm.visualizer.filter;

import jdk.graal.compiler.graphio.parsing.model.ChangedEvent;
import jdk.graal.compiler.graphio.parsing.model.ChangedEventProvider;
import jdk.graal.compiler.graphio.parsing.model.ChangedListener;
import org.graalvm.visualizer.graph.Diagram;
import org.graalvm.visualizer.script.ScriptEnvironment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FilterChain implements FilterSequence<FilterChain>, ChangedEventProvider<FilterChain> {
    private final List<Filter> filters;
    private final transient ChangedEvent<FilterChain> changedEvent;
    private final transient List<FilterListener> listeners = new ArrayList<>();

    private final ChangedListener<Filter> changedListener = new ChangedListener<Filter>() {
        @Override
        public void changed(Filter source) {
            changedEvent.fire();
        }
    };

    public FilterChain() {
        filters = new ArrayList<>();
        changedEvent = new ChangedEvent<>(this);
    }

    public FilterChain(FilterChain f) {
        this.filters = new ArrayList<>(f.filters);
        synchronized (f.listeners) {
            this.listeners.addAll(f.listeners);
        }
        changedEvent = new ChangedEvent<>(this);
    }

    public FilterChain(FilterSequence f) {
        this.filters = new ArrayList<>(f.getFilters());
        changedEvent = new ChangedEvent<>(this);
    }

    @Override
    public ChangedEvent<FilterChain> getChangedEvent() {
        return changedEvent;
    }

    public void addFilterListener(FilterListener l) {
        synchronized (listeners) {
            this.listeners.add(l);
        }
    }

    public void removeFilterListener(FilterListener l) {
        synchronized (listeners) {
            this.listeners.remove(l);
        }
    }

    public Filter getFilterAt(int index) {
        synchronized (this) {
            assert index >= 0 && index < filters.size();
            return filters.get(index);
        }
    }

    /**
     * Fires filter event
     */
    protected final void fireFilterEvent(FilterEvent e, boolean startEnd) {
        FilterListener[] ll;
        synchronized (listeners) {
            if (listeners.isEmpty()) {
                return;
            }
            ll = listeners.toArray(new FilterListener[listeners.size()]);
        }
        for (FilterListener l : ll) {
            if (startEnd) {
                l.filterStart(e);
            } else {
                l.filterEnd(e);
            }
        }
    }

    // hidden, legacy
    void apply(Diagram d) {
        Filters.applyWithCancel(this, d, null).process();
    }

    /**
     * Get filters, taking account the specified ordering. Filters of this chain present in the
     * {@code ordering} will be listed first, in the order as they appear in {@code ordering}.
     * Other filters (not listed in {@code ordering} will be listed at the end, in no
     * particular order.
     *
     * @param ordering the desired ordering, may be {@code null}.
     * @return Ordered list of Filters
     */
    public final List<Filter> getFilters(FilterChain ordering) {
        List<Filter> applyFilters = getFilters();
        List<Filter> orderedFilters = new ArrayList<>(applyFilters.size());
        if (ordering != null) {
            for (Filter f : ordering.getFilters()) {
                if (applyFilters.contains(f)) {
                    orderedFilters.add(f);
                }
            }
        }
        for (Filter f : applyFilters) {
            if (!orderedFilters.contains(f)) {
                orderedFilters.add(f);
            }
        }
        return orderedFilters;
    }

    protected void replaceFilters(List<Filter> newFilters) {
        synchronized (this) {
            if (this.filters.equals(newFilters)) {
                return;
            }
            for (Filter f : filters) {
                f.getChangedEvent().removeListener(changedListener);
            }
            filters.clear();
            filters.addAll(newFilters);
            for (Filter f : newFilters) {
                f.getChangedEvent().addListener(changedListener);
            }
        }
        changedEvent.fire();
    }

    public void addFilter(Filter filter) {
        assert filter != null;
        synchronized (this) {
            filters.add(filter);
        }
        filter.getChangedEvent().addListener(changedListener);
        changedEvent.fire();
    }

    public boolean containsFilter(Filter filter) {
        synchronized (this) {
            return filters.contains(filter);
        }
    }

    public void removeFilter(Filter filter) {
        synchronized (this) {
            assert filters.contains(filter);
            filters.remove(filter);
        }
        filter.getChangedEvent().removeListener(changedListener);
        changedEvent.fire();
    }

    public void moveFilterUp(Filter filter) {
        synchronized (this) {
            assert filters.contains(filter);
            int index = filters.indexOf(filter);
            if (index != 0) {
                filters.remove(index);
                filters.add(index - 1, filter);
            }
        }
        changedEvent.fire();
    }

    public void moveFilterDown(Filter filter) {
        synchronized (this) {
            assert filters.contains(filter);
            int index = filters.indexOf(filter);
            if (index != filters.size() - 1) {
                filters.remove(index);
                filters.add(index + 1, filter);
            }
        }
        changedEvent.fire();
    }

    public synchronized List<Filter> getFilters() {
        return Collections.unmodifiableList(new ArrayList<>(filters));
    }

    static FEImpl createStub(Diagram d) {
        return new FEImpl(d, null);
    }

    /**
     * TBD
     * Creates an environment for the diagram.
     *
     * @param d the diagram to filter
     * @return the environment
     * public FilterEnvironment    createEnvironment(Diagram d) {
     * return new FEImpl(d);
     * }
     */

    static class ScriptEnvImpl extends ScriptEnvironment {
        Map<Object, Object> vals = new HashMap<>();

        @Override
        public <T> T setValue(Object key, T val) {
            return (T) vals.put(key, val);
        }

        @Override
        public <T> T getValue(Object key) {
            return (T) vals.get(key);
        }

        @Override
        public Set keys() {
            return vals.keySet();
        }

        @Override
        public Iterable values() {
            return vals.values();
        }
    }

    static class FEImpl extends FilterEnvironment {
        private final boolean closeScript;

        public FEImpl(Diagram d, ScriptEnvironment e) {
            super(d, e == null ? new ScriptEnvImpl() : e);
            closeScript = e == null;
        }

        @Override
        public void close() throws IOException {
            if (closeScript) {
                getScriptEnvironment().close();
            }
        }
    }
}
