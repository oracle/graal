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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.graalvm.visualizer.data.SuppressFBWarnings;
import org.graalvm.visualizer.filter.impl.FilterExecutionServiceImpl;
import org.graalvm.visualizer.graph.Diagram;

/**
 * The instance represents a single filter chain execution.
 * Allows to control the execution from another thread. Similar to Future, but when
 * the caller calls {@link #process}, the computation will be performed in the caller's thread.
 * Another thread can call {@link #cancel} before {@link #process} terminates if it wants
 * to cancel the process. The processing may cancel immediately, or anytime after the
 * processing has started.
 * <p/>
 * FilterExecutions can be nested; the parent FilterExecution controls the nested ones: if
 * parent is cancelled, it will cancel the nested execution as well (recursively).
 * <p/>
 * The execution provides access to {@link FilterEnvironment}. which can be used to share data
 * and communicate between filters.
 * <p/>
 * The instance may not be executed repeatedly or in parallel. The instance becomes invalid once
 * the processing ends, or is cancelled. Invalid instances throw FilterCanceledException from
 * {@link #process} immediately.
 */
public class FilterExecution {
    private static final Logger LOG = Logger.getLogger(FilterExecution.class.getName());

    /**
     * Filters, in the order of their processing
     */
    private final List<Filter> orderedFilters;

    /**
     * The diagram being processed
     */
    private final Diagram dg;

    /**
     * The original filter chain, to make the actual invocation
     */
    private final FilterChain chain;

    /**
     * Execution state.
     */
    // @GuardedBy(this)
    private volatile int state;

    /**
     * Initialization, did not run yet
     */
    private static final int INIT = 0;

    /**
     * Running
     */
    private static final int RUN = 1;

    /**
     * Was cancelled
     */
    private static final int CANCEL = 2;

    private final FilterEnvironment env;

    private FilterExecution nested;

    private static final ThreadLocal<FilterExecution> current = new ThreadLocal<>();

    private final boolean closeEnvironment;

    private final List<FilterListener> listeners = new ArrayList<>();

    public FilterExecution(List<Filter> orderedFilters, Diagram dg, FilterChain chain, FilterEnvironment env) {
        this.orderedFilters = new ArrayList<>(orderedFilters);
        this.dg = dg;
        this.chain = chain;
        this.env = env;
        this.closeEnvironment = false;
    }

    public FilterExecution(List<Filter> orderedFilters, Diagram dg, FilterChain chain) {
        this.orderedFilters = new ArrayList<>(orderedFilters);
        this.dg = dg;
        this.chain = chain;
        this.env = new FilterChain.FEImpl(dg, null);
        this.closeEnvironment = true;
    }

    public List<Filter> getFilters() {
        return Collections.unmodifiableList(orderedFilters);
    }

    void addListeners(List<FilterListener> ll) {
        listeners.addAll(ll);
    }

    void removeListeners(List<FilterListener> ll) {
        listeners.removeAll(ll);
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

    static FilterExecution get() {
        FilterExecution exec = current.get();
        if (exec == null) {
            throw new IllegalStateException();
        }
        return exec;
    }


    static FilterExecution getOrCreate(Diagram d, Filter f) {
        FilterExecution e = get();
        if (e != null && e.getDiagram() == d) {
            return e;
        }
        return new FilterExecution(
                Collections.singletonList(f),
                d, null,
                FilterChain.createStub(d)) {
            @Override
            protected void applyFilter(Filter f) {
                f.applyWith(getEnvironment());
            }
        };
    }

    private synchronized void setChild(FilterExecution ex) {
        this.nested = ex;
    }

    protected void applyFilter(Filter f) {
        applyFilter(f, chain);
    }

    private FilterEvent errorFilterEvent;

    /**
     * Processes the diagram. Throws a {@link FilterCanceledException} unchecked
     * exception if the process is canceled asynchronously. If the processing is canceled,
     * the diagram may be left in a inconsistent state.
     *
     * @throws FilterCanceledException on cancel
     */
    public void process() throws FilterCanceledException {
        doProcess(() -> {
            FilterExecutionServiceImpl serviceImpl = FilterExecutionServiceImpl.get();
            serviceImpl.runWith(chain, this, () -> {
                List<FilterListener> ll = snapshotListeners();
                FilterEvent e = null;
                if (!ll.isEmpty()) {
                    e = new FilterEvent(this, chain, null, dg);
                    for (FilterListener l : ll) {
                        l.executionStart(e);
                    }
                }
                for (Filter f : orderedFilters) {
                    if (state != RUN) {
                        break;
                    }
                    applyFilter(f);
                }
                if (!ll.isEmpty()) {
                    if (errorFilterEvent != null) {
                        e = errorFilterEvent;
                    }
                    for (FilterListener l : ll) {
                        l.executionEnd(e);
                    }
                }
            });
        });
    }

    public FilterEvent getErrorFilterEvent() {
        return errorFilterEvent;
    }

    private List<FilterListener> snapshotListeners() {
        synchronized (listeners) {
            if (listeners.isEmpty()) {
                return Collections.emptyList();
            }
            return new ArrayList<>(listeners);
        }
    }

    void processSingle(Filter f) {
        doProcess(() -> {
            applyFilter(f);
        });
    }

    private void doProcess(Runnable toInvoke) throws FilterCanceledException {
        synchronized (this) {
            if (state > RUN) {
                throw new FilterCanceledException(getEnvironment());
            }
            state = RUN;
        }
        FilterExecution parent = current.get();
        try {

            if (parent != null) {
                parent.setChild(this);
                // adopt parent's environment cancel flag:

                // XXX
                // env.failed = parent.getEnvironment().failed();
            }
            current.set(this);
            toInvoke.run();
            synchronized (this) {
                if (state > RUN) {
                    throw new FilterCanceledException(getEnvironment());
                }
            }
            if (parent != null && parent != this) {
                parent.setChild(null);
            }
            // XXX
            // env.failed = false;
        } catch (FilterCanceledException ex) {
            synchronized (this) {
                state = CANCEL;
                throw ex;
            }
        } finally {
            current.set(parent);
            if (closeEnvironment) {
                try {
                    env.close();
                } catch (IOException ex) {
                    LOG.log(Level.WARNING, "Error closing scripting environment: ", ex);
                }
            }
        }
    }

    public void cancel() {
        FilterExecution child;
        synchronized (this) {
            int s = state;
            state = CANCEL;
            if (s != RUN) {
                return;
            }
            child = nested;
            // XXX
            // env.failed = true;
        }
        if (child != null) {
            child.cancel();
        }
        // go through filters and cancel their processing for the graph
        for (Filter f : orderedFilters) {
            f.cancel(getEnvironment());
        }
    }

    public boolean isCancelled() {
        return state >= CANCEL;
    }

    public Diagram getDiagram() {
        return dg;
    }

    public FilterEnvironment getEnvironment() {
        return env;
    }

    public static FilterExecutionService getExecutionService() {
        return FilterExecutionServiceImpl.get();
    }

    // maybe listeners should be cloned and passed to the Execution ?
    @SuppressWarnings("AssertWithSideEffects")
    @SuppressFBWarnings(value = "UCF_USELESS_CONTROL_FLOW_NEXT_LINE", justification = "Throws exception when asserts are enabled")
    void applyFilter(Filter f, FilterChain chain) {
        final FilterEnvironment env = getEnvironment();
        final FilterExecution exec = this;

        final Diagram d = getDiagram();
//        serviceImpl.runWith(chain, this, () -> {
        List<FilterListener> ll = snapshotListeners();
        FilterEvent initEv;
        initEv = new FilterEvent(this, chain, f, d);
        FilterEvent ev = initEv;
        try {
            LOG.log(Level.FINE, "Applying filter: {0} ", f);
            for (FilterListener l : ll) {
                l.filterStart(ev);
            }
            chain.fireFilterEvent(ev, true);
            f.applyWith(env);
        } catch (ThreadDeath ex) {
            // propagate, do not fire any events
            ev = null;
            throw ex;
        } catch (CancellationException ex) {
            // rewrap into FilterCanceled
            LOG.log(Level.FINE, "Filter was cancelled: " + f, ex);
            FilterCanceledException ee = new FilterCanceledException(env, ex);
            ev = new FilterEvent(this, chain, f, d, ee);
            throw ee;
        } catch (FilterCanceledException ex) {
            LOG.log(Level.FINE, "Filter was cancelled: " + f, ex);
            ev = new FilterEvent(this, chain, f, d, ex);
            throw ex;
        } catch (Throwable ex) {
            // JUnit testing support: propagate assertion errors
            LOG.log(Level.FINE, "Filter terminated abruptly: " + f, ex);
            boolean throwError = false;
            // should be optimized out with disable-assertions
            assert (throwError = (ex instanceof Error) && ex.getClass().getName().startsWith("org.junit")) || true; // NOI18N
            if (throwError) {
                // do not invoke listeners
                ev = null;
                throw (Error) ex;
            }
            ev = new FilterEvent(this, chain, f, d, ex);
            // swallow, but broadcast an event
        } finally {
            if (ev != null) {
                for (FilterListener l : ll) {
                    l.filterEnd(ev);
                }
                chain.fireFilterEvent(ev, false);
                if (ev.getExecutionError() != null) {
                    errorFilterEvent = ev;
                }
            }
        }
//        });
    }

    public boolean isRunning() {
        return state == RUN;
    }

}
