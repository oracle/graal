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

import jdk.graal.compiler.graphio.parsing.model.ChangedEvent;
import jdk.graal.compiler.graphio.parsing.model.ChangedListener;
import org.graalvm.visualizer.filter.CustomFilter;
import org.graalvm.visualizer.filter.Filter;
import org.graalvm.visualizer.filter.FilterChain;
import org.graalvm.visualizer.filter.FilterSequence;
import org.graalvm.visualizer.script.ScriptEnvironment;
import org.graalvm.visualizer.util.ListenerSupport;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author odouda
 */
public class Filters {
    private static final Logger LOG = Logger.getLogger(Filters.class.getName());

    private FilterSequence filterChain;

    private final List<Filter> scriptFilters = new ArrayList<>();
    private ScriptEnvironment scriptEnvironment;
    private FiltersSnapshot filtersSnapshot;

    private ChangedListener<FilterChain> weakFilterChainChangedListener;
    private final ChangedListener<FilterSequence> filterChainChangedListener = this::filtersChanged;

    private final ChangedEvent<Filters> filtersChanged;

    public Filters(FilterSequence filterChain) {
        filtersChanged = new ChangedEvent<>(this);
        setFilterChainInternal(filterChain);
    }

    public synchronized void setDataInternal(Filters otherFilters) {
        setFilterChainInternal(new FilterChain(otherFilters.getFilterChain()));
        setScriptFilters(otherFilters.scriptFilters, otherFilters.scriptEnvironment);
    }

    public void setFilterChain(FilterSequence chain) {
        if (setFilterChainInternal(chain)) {
            LOG.log(Level.FINE, "FilterChain was changed.");
            filtersChanged(chain);
        }
    }

    private synchronized boolean setFilterChainInternal(FilterSequence chain) {
        assert chain != null : "filterChain must never be null";
        if (filterChain == chain) {
            return false;
        }
        if (filterChain != null) {
            filterChain.getChangedEvent().removeListener(weakFilterChainChangedListener);
        }
        filterChain = chain;
        weakFilterChainChangedListener = ListenerSupport.addWeakListener(filterChainChangedListener, filterChain.getChangedEvent());
        return true;
    }

    public void setScriptFilter(Filter scriptFilter, ScriptEnvironment env, boolean append) {
        if (setScriptFilterInternal(scriptFilter, env, append)) {
            LOG.log(Level.FINE, "CustomScriptFilters changed.");
            filtersChanged(null);
        }
    }

    private synchronized boolean setScriptFilterInternal(Filter scriptFilter, ScriptEnvironment env, boolean append) {
        boolean changed = false;
        append = append && (env == scriptEnvironment || scriptEnvironment == null || env == null);
        if (!append) {
            scriptFilters.clear();
            scriptEnvironment = null;
            changed = true;
        }
        if (env != null && scriptEnvironment == null) {
            scriptEnvironment = env;
            changed = true;
        }
        if (scriptFilter != null) {
            scriptFilters.add(scriptFilter);
            changed = true;
        }
        return changed;
    }

    private synchronized void setScriptFilters(Collection<Filter> scriptFilters, ScriptEnvironment env) {
        this.scriptFilters.clear();
        this.scriptFilters.addAll(scriptFilters);
        scriptEnvironment = env;
        filtersSnapshot = null;
    }

    public synchronized List<Filter> getScriptFilters() {
        return Collections.unmodifiableList(new ArrayList<>(scriptFilters));
    }

    public synchronized ScriptEnvironment getScriptEnvironment() {
        return scriptEnvironment;
    }

    public synchronized FilterSequence getFilterChain() {
        return filterChain;
    }

    public ChangedEvent<Filters> getFiltersChangedEvent() {
        return filtersChanged;
    }

    private void filtersChanged(FilterSequence fc) {
        synchronized (this) {
            if (fc != null) {
                LOG.log(Level.FINE, "FilterChain: {0} changed.", fc);
            } else {
                LOG.log(Level.FINE, "Scripts changed.");
            }
            filtersSnapshot = null;
        }
        filtersChanged.fire();
    }

    public synchronized void close() {
        filterChain.getChangedEvent().removeListener(weakFilterChainChangedListener);
    }

    public synchronized List<Filter> getFiltersSnapshot() {
        if (filtersSnapshot == null) {
            filtersSnapshot = new FiltersSnapshot(filterChain, scriptFilters, scriptEnvironment);
        }
        return filtersSnapshot;
    }

    public static class FiltersSnapshot extends AbstractList<Filter> {
        private final List<Filter> filters;
        private final ScriptEnvironment scriptEnvironment;
        private final int hash;

        public FiltersSnapshot(FilterSequence filterChain,
                               List<Filter> scriptFilters, ScriptEnvironment scriptEnvironment) {
            this.filters = new ArrayList<>(filterChain.getFilters());
            this.filters.addAll(scriptFilters);
            this.scriptEnvironment = scriptEnvironment;
            hash = makeHash();
        }

        private int makeHash() {
            int h = Objects.hashCode(scriptEnvironment);
            for (Filter f : filters) {
                CustomFilter cf = null;
                if (f instanceof CustomFilter) {
                    cf = (CustomFilter) f;
                } else {
                    cf = f.getLookup().lookup(CustomFilter.class);
                }
                if (cf != null) {
                    h = h * 13 + cf.getCode().hashCode();
                } else {
                    h = h * 13 + f.hashCode();
                }
            }
            return h;
        }

        @Override
        public Filter get(int index) {
            return filters.get(index);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof FiltersSnapshot)) {
                return false;
            }
            FiltersSnapshot other = (FiltersSnapshot) obj;
            return hash == other.hash && scriptEnvironment == other.scriptEnvironment && filters.equals(other.filters);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("FilterSnapshot(").append(scriptEnvironment).append(")[ ");
            for (Filter f : filters) {
                sb.append(f).append(" ,");
            }
            sb.append(" ]");
            return sb.toString();
        }

        @Override
        public int size() {
            return filters.size();
        }
    }
}
