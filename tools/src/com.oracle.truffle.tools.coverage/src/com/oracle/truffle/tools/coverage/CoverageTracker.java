/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.truffle.tools.coverage;

import static com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;

import java.util.function.Function;

import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionListener;
import com.oracle.truffle.api.instrumentation.SourceFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tools.coverage.impl.CoverageInstrument;
import com.oracle.truffle.tools.coverage.impl.StatementCoverageNode;
import com.oracle.truffle.tools.coverage.impl.RootCoverageNode;

public class CoverageTracker implements AutoCloseable {

    private static final SourceFilter DEFAULT_FILTER = SourceFilter.ANY;

    static {
        CoverageInstrument.setFactory(new Function<Env, CoverageTracker>() {
            @Override
            public CoverageTracker apply(Env env) {
                return new CoverageTracker(env);
            }

        });
    }

    private final Env env;
    private SourceFilter filter;
    private boolean tracking;
    private boolean closed;
    private EventBinding<LoadSourceSectionListener> loadedStatementBinding;
    private EventBinding<ExecutionEventNodeFactory> execStatementBinding;
    private EventBinding<LoadSourceSectionListener> loadedRootBinding;
    private EventBinding<ExecutionEventNodeFactory> execRootBinding;
    private Coverage coverage;


    @Override
    public void close() {
        closed = true;
    }

    public synchronized void addLoadedStatement(SourceSection sourceSection) {
        coverage.addLoadedStatement(sourceSection);
    }

    private synchronized void addLoadedRoot(SourceSection rootSection) {
        coverage.addLoadedRoot(rootSection);
    }

    public synchronized void addCoveredStatement(SourceSection sourceSection) {
        coverage.addCoveredStatement(sourceSection);
    }

    public synchronized void addCoveredRoot(SourceSection rootSection) {
        coverage.addCoveredRoot(rootSection);
    }

    public synchronized Coverage getCoverage() {
        return coverage.copy();
    }
    public enum Mode {
        STATEMENTS,
        ROOTS

    }

    public CoverageTracker(Env env) {
        this.env = env;
    }

    public void setFilter(SourceFilter filter) {
        this.filter = filter;
    }

    public synchronized void setTracking(boolean tracking) {
        if (closed) {
            throw new IllegalStateException("Coverage Tracer is already closed");
        }
        if (this.tracking != tracking) {
            this.tracking = tracking;
            resetTracking();
        }
    }

    private void resetTracking() {
        assert Thread.holdsLock(this);
        disposeBindings();
        if (!tracking || closed) {
            return;
        }
        SourceFilter f = this.filter;
        if (f == null) {
            f = DEFAULT_FILTER;
        }
        final Instrumenter instrumenter = env.getInstrumenter();
        instrumentStatements(f, instrumenter);
        instrumentRoots(f, instrumenter);
        coverage = new Coverage();
    }

    private void instrumentRoots(SourceFilter f, Instrumenter instrumenter) {
        final SourceSectionFilter rootFilter = SourceSectionFilter.newBuilder().sourceFilter(f).tagIs(StandardTags.RootTag.class).build();
        loadedRootBinding = instrumenter.attachLoadSourceSectionListener(rootFilter, new LoadSourceSectionListener() {
            @Override
            public void onLoad(LoadSourceSectionEvent event) {
               CoverageTracker.this.addLoadedRoot(event.getSourceSection());
            }
        }, false);
        execRootBinding = instrumenter.attachExecutionEventFactory(rootFilter, new ExecutionEventNodeFactory() {
            @Override
            public ExecutionEventNode create(EventContext context) {
                return new RootCoverageNode(CoverageTracker.this, context.getInstrumentedSourceSection());
            }
        });
    }

    private void instrumentStatements(SourceFilter f, Instrumenter instrumenter) {
        final SourceSectionFilter statementFilter = SourceSectionFilter.newBuilder().sourceFilter(f).tagIs(StandardTags.StatementTag.class).build();
        loadedStatementBinding = instrumenter.attachLoadSourceSectionListener(statementFilter, new LoadSourceSectionListener() {
            @Override
            public void onLoad(LoadSourceSectionEvent event) {
                CoverageTracker.this.addLoadedStatement(event.getSourceSection());
            }
        }, false);
        execStatementBinding = instrumenter.attachExecutionEventFactory(statementFilter, new ExecutionEventNodeFactory() {
            @Override
            public ExecutionEventNode create(EventContext context) {
                return new StatementCoverageNode(CoverageTracker.this, context.getInstrumentedSourceSection());
            }
        });
    }

    private void disposeBindings() {
        if (loadedStatementBinding != null) {
            loadedStatementBinding.dispose();
            loadedStatementBinding = null;
        }
        if (execStatementBinding != null) {
            execStatementBinding.dispose();
            execStatementBinding = null;
        }
    }

}
