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
package org.graalvm.visualizer.filter.impl;

import org.graalvm.visualizer.filter.Filter;
import org.graalvm.visualizer.filter.FilterChain;
import org.graalvm.visualizer.filter.FilterEnvironment;
import org.graalvm.visualizer.filter.FilterEvent;
import org.graalvm.visualizer.filter.FilterExecution;
import org.graalvm.visualizer.filter.FilterExecutionService;
import org.graalvm.visualizer.filter.FilterListener;
import org.graalvm.visualizer.graph.Diagram;
import org.graalvm.visualizer.script.ScriptEnvironment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * @author sdedic
 */
public class FilterExecutionServiceImpl implements FilterExecutionService {
    private static final Logger LOG = Logger.getLogger(FilterExecutionServiceImpl.class.getName());
    private final List<FilterListener> listeners = new ArrayList<>();

    private static FilterExecutionServiceImpl INSTANCE = new FilterExecutionServiceImpl();

    public static synchronized FilterExecutionServiceImpl get() {
        return INSTANCE;
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

    @Override
    public FilterExecution createExecution(FilterChain sequence, ScriptEnvironment e, Diagram d) {

        List<Filter> orderedFilters = sequence.getFilters();
        if (e != null) {
            return new FilterExecution(orderedFilters, d, sequence, new FEImpl(d, e));
        } else {
            return new FilterExecution(orderedFilters, d, sequence);
        }
    }

    public void runWith(FilterChain chain, FilterExecution exec, Runnable r) {
        RunListener rl = new RunListener(chain);
        exec.addFilterListener(rl);
        try {
            r.run();
        } finally {
            exec.removeFilterListener(rl);
        }
    }

    class RunListener implements FilterListener {
        private final FilterChain currentChain;

        public RunListener(FilterChain currentChain) {
            this.currentChain = currentChain;
        }

        @Override
        public void filterStart(FilterEvent e) {
            forwardEvent(e, true);
        }

        @Override
        public void filterEnd(FilterEvent e) {
            forwardEvent(e, false);
        }

        @Override
        public void executionStart(FilterEvent e) {
            forwardExecutionEvent(e, true);
        }

        @Override
        public void executionEnd(FilterEvent e) {
            forwardExecutionEvent(e, false);
        }
    }

    private void forwardExecutionEvent(FilterEvent ev, boolean start) {
        FilterListener[] ll;

        synchronized (listeners) {
            if (listeners.isEmpty()) {
                return;
            }
            ll = listeners.toArray(new FilterListener[listeners.size()]);
        }
        for (FilterListener l : ll) {
            if (start) {
                l.executionStart(ev);
            } else {
                l.executionEnd(ev);
            }
        }
    }

    private void forwardEvent(FilterEvent ev, boolean start) {
        FilterListener[] ll;

        synchronized (listeners) {
            if (listeners.isEmpty()) {
                return;
            }
            ll = listeners.toArray(new FilterListener[listeners.size()]);
        }
        for (FilterListener l : ll) {
            if (start) {
                l.filterStart(ev);
            } else {
                l.filterEnd(ev);
            }
        }
    }

    @Override
    public void addFilterListener(FilterListener l) {
        listeners.add(l);
    }

    @Override
    public void removeFilterListener(FilterListener l) {
        listeners.remove(l);
    }
}
