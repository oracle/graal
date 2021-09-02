/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.agentscript.impl;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.LoadSourceEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceListener;
import com.oracle.truffle.api.instrumentation.SourceFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.source.Source;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

final class InsightPerSource implements ContextsListener, AutoCloseable, LoadSourceListener {
    private final InsightInstrument instrument;
    private final Supplier<Source> src;
    private final AgentObject insight;
    private final IgnoreSources ignoredSources;
    private EventBinding<?> agentBinding;
    private InsightInstrument.Key sourceBinding;
    private final InsightInstrument.Key closeBinding;
    private final Map<InsightFilter, InsightInstrument.Key> bindings = new HashMap<>();
    // XXX: synchronize
    private final Collection<EventBinding<?>> pendingBindings = new ArrayList<>();

    InsightPerSource(InsightInstrument instrument, Supplier<Source> src, IgnoreSources ignoredSources) {
        this.instrument = instrument;
        this.ignoredSources = ignoredSources;
        this.src = src;
        this.insight = instrument.createInsightObject(this);
        this.closeBinding = instrument.newKey(AgentType.CLOSE);
    }

    void collectSymbols(List<String> argNames, List<Object> args) {
        argNames.add("insight");
        args.add(insight);
        instrument.collectGlobalSymbolsImpl(this, argNames, args);
    }

    @CompilerDirectives.TruffleBoundary
    void initializeAgent(TruffleContext ctx) {
        InsightPerContext ctxInight = instrument.find(ctx);

        if (ctxInight.needsInitialization()) {
            Source script = src.get();
            instrument.ignoreSources.ignoreSource(script);
            List<String> argNames = new ArrayList<>();
            List<Object> args = new ArrayList<>();
            collectSymbols(argNames, args);

            CallTarget target;
            try {
                target = instrument.env().parse(script, argNames.toArray(new String[0]));
            } catch (Exception ex) {
                throw InsightException.raise(ex);
            }
            target.call(args.toArray());
        }
    }

    @Override
    public void onContextCreated(TruffleContext context) {
    }

    @Override
    public void onLanguageContextCreated(TruffleContext context, LanguageInfo language) {
    }

    @Override
    public void onLanguageContextInitialized(TruffleContext context, LanguageInfo language) {
        if (agentBinding != null || language.isInternal()) {
            return;
        }
        if (context.isEntered()) {
            initializeAgent(context);
        } else {
            final SourceSectionFilter anyRoot = SourceSectionFilter.newBuilder().tagIs(StandardTags.RootTag.class).build();
            Instrumenter instrumenter = instrument.env().getInstrumenter();
            agentBinding = instrumenter.attachExecutionEventListener(anyRoot, new InitializeLater(context));
            pendingBindings.add(agentBinding);
        }
    }

    @Override
    public void onLanguageContextFinalized(TruffleContext context, LanguageInfo language) {
        instrument.find(context).onClosed(closeBinding);
    }

    @Override
    public void onLanguageContextDisposed(TruffleContext context, LanguageInfo language) {
    }

    @Override
    public void onContextClosed(TruffleContext context) {
    }

    @Override
    public void close() {
        for (Iterator<EventBinding<?>> it = pendingBindings.iterator(); it.hasNext();) {
            it.next().dispose();
            it.remove();
        }
    }

    synchronized void binding(InsightFilter.Data data, Function<InsightInstrument.Key, ExecutionEventNodeFactory> needFactory, Consumer<InsightInstrument.Key> hasFactory) {
        InsightFilter filter = data.filter;
        InsightInstrument.Key key = bindings.get(filter);
        if (key == null) {
            key = instrument.newKey(null);
            // @formatter:off
            SourceSectionFilter.Builder ssfb = SourceSectionFilter.newBuilder()
                .sourceIs(ignoredSources)
                .includeInternal(false)
                .tagIs(filter.getTags());

            if (data.sourceFilterFn != null) {
                InsightSourceFilter predicate = new InsightSourceFilter(instrument, key);
                SourceFilter sf = SourceFilter.newBuilder().sourceIs(predicate).build();
                ssfb.sourceFilter(sf);
            }
            if (filter.getRootNameRegExp() != null) {
                ssfb.rootNameIs(new RegexNameFilter(filter.getRootNameRegExp()));
            } else if (data.rootNameFn != null) {
                ssfb.rootNameIs(new RootNameFilter(instrument, key));
            }
            // @formatter:on

            bindings.put(filter, key);

            Instrumenter instrumenter = instrument.env().getInstrumenter();
            EventBinding<?> handle = instrumenter.attachExecutionEventFactory(ssfb.build(), needFactory.apply(key));
            key.assign(handle);
        } else {
            hasFactory.accept(key);
        }
    }

    InsightInstrument.Key closeBinding() {
        return closeBinding;
    }

    synchronized InsightInstrument.Key sourceBinding() {
        if (sourceBinding == null) {
            // @formatter:off
            final SourceFilter filter = SourceFilter.newBuilder().
                sourceIs(ignoredSources).
                includeInternal(false).
                build();
            // @formatter:on
            Instrumenter instrumenter = instrument.env().getInstrumenter();
            sourceBinding = instrument.newKey(AgentType.SOURCE).assign(instrumenter.attachLoadSourceListener(filter, this, false));
        }
        return sourceBinding;
    }

    @Override
    public void onLoad(LoadSourceEvent event) {
        InteropLibrary interop = InteropLibrary.getUncached();
        Instrumenter instrumenter = instrument.env().getInstrumenter();
        for (Object fn : instrument.findCtx().functionsFor(sourceBinding)) {
            final Source source = event.getSource();
            try {
                interop.execute(fn, new SourceEventObject(source));
            } catch (RuntimeException ex) {
                if (interop.isException(ex)) {
                    InsightException.throwWhenExecuted(instrumenter, source, ex);
                } else {
                    throw ex;
                }
            } catch (InteropException ex) {
                InsightException.throwWhenExecuted(instrumenter, source, ex);
            }
        }
    }

    final class InitializeLater implements ExecutionEventListener {
        private final TruffleContext context;

        InitializeLater(TruffleContext context) {
            this.context = context;
        }

        @Override
        public void onEnter(EventContext ctx, VirtualFrame frame) {
            CompilerDirectives.transferToInterpreter();
            agentBinding.dispose();
            pendingBindings.remove(agentBinding);
            initializeAgent(context);
        }

        @Override
        public void onReturnValue(EventContext ctx, VirtualFrame frame, Object result) {
        }

        @Override
        public void onReturnExceptional(EventContext ctx, VirtualFrame frame, Throwable exception) {
        }
    }
}
