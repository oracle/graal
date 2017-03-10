/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.impl.DebuggerInstrument;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.impl.Accessor.EngineSupport;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.LoadSourceEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags.CallTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;

/**
 * Represents debugging related state of a {@link PolyglotEngine}.
 * <p>
 * Access to the (singleton) instance in an engine, is available via:
 * <ul>
 * <li>{@link Debugger#find(PolyglotEngine)}</li>
 * <li>{@link Debugger#find(TruffleLanguage.Env)}</li>
 * <li>{@link DebuggerSession#getDebugger()}</li>
 * </ul>
 *
 * To start new debugger session use {@link #startSession(SuspendedCallback)}. Please see
 * {@link DebuggerSession} for a usage example.
 * <p>
 * The debugger supports diagnostic tracing that can be enabled using the
 * <code>-Dtruffle.debug.trace=true</code> Java property. The output of this tracing is not
 * guaranteed and will change without notice.
 *
 * @see Debugger#startSession(SuspendedCallback)
 * @see DebuggerSession
 * @see Breakpoint
 *
 * @since 0.9
 */
public final class Debugger {

    static final boolean TRACE = Boolean.getBoolean("truffle.debug.trace");

    /**
     * @since 0.9
     * @deprecated use class literal {@link StatementTag} instead for tagging
     */
    @Deprecated public static final String HALT_TAG = "debug-HALT";

    /**
     * @since 0.9
     * @deprecated use class literal {@link CallTag} instead for tagging
     */
    @Deprecated public static final String CALL_TAG = "debug-CALL";

    /*
     * The engine with this debugger was created.
     */
    private final PolyglotEngine sourceVM;
    private final Env env;
    private final DebuggerSession legacySession;

    /*
     * Deprecation note. This map can go away with the removal if setLineBreakpoint.
     */
    private final Map<BreakpointLocation, Breakpoint> breakpointPerLocation = Collections.synchronizedMap(new HashMap<BreakpointLocation, Breakpoint>());

    Debugger(PolyglotEngine sourceVM, Env env) {
        this.env = env;
        this.sourceVM = sourceVM;
        legacySession = new DebuggerSession(this, new SuspendedCallback() {
            public void onSuspend(SuspendedEvent event) {
                // TODO remove this Truffle > 0.16.
                AccessorDebug.dispatchEvent(Debugger.this.sourceVM, event, EngineSupport.SUSPENDED_EVENT);
            }
        }, true);
    }

    /**
     * Starts a new {@link DebuggerSession session} provided with a callback that gets notified
     * whenever the execution is suspended.
     *
     * @param callback the callback to notify
     * @see DebuggerSession
     * @see SuspendedEvent
     * @since 0.17
     */
    public DebuggerSession startSession(SuspendedCallback callback) {
        return new DebuggerSession(this, callback, false);
    }

    /**
     * Returns a list of all loaded sources. The sources are returned in the order as they have been
     * loaded by the languages.
     *
     * @return an unmodifiable list of sources
     * @since 0.17
     */
    public List<Source> getLoadedSources() {
        final List<Source> sources = new ArrayList<>();
        EventBinding<?> binding = env.getInstrumenter().attachLoadSourceListener(SourceSectionFilter.ANY, new LoadSourceListener() {
            public void onLoad(LoadSourceEvent event) {
                sources.add(event.getSource());
            }
        }, true);
        binding.dispose();
        return Collections.unmodifiableList(sources);
    }

    /**
     * Gets all existing breakpoints, whatever their status, in natural sorted order. Modification
     * save.
     *
     * @since 0.9
     * @deprecated use {@link DebuggerSession#getBreakpoints()} instead. Note the behavior of the
     *             returned collection changed.
     */
    @Deprecated
    @TruffleBoundary
    public Collection<Breakpoint> getBreakpoints() {
        return getLegacySession().getLegacyBreakpoints();
    }

    /**
     * Request a pause. As soon as the execution arrives at a node holding a debugger tag,
     * {@link SuspendedEvent} is emitted.
     * <p>
     * This method can be called in any thread. When called from the {@link SuspendedEvent} callback
     * thread, execution is paused on a nearest next node holding a debugger tag.
     *
     * @return <code>true</code> when pause was requested on the current execution,
     *         <code>false</code> when there is no running execution to pause.
     * @since 0.14
     * @deprecated use debugger.{@link #startSession(SuspendedCallback) startSession(callback)}.
     *             {@link DebuggerSession#suspendNextExecution() suspendNextExecution()} instead
     */
    @Deprecated
    public boolean pause() {
        getLegacySession().suspendNextExecution();
        return true; // no way to support this anymore
    }

    /*
     * CHumer deprecation note: I marked setLineBreakpoint as deprecated without replacement because
     * with expanding breakpoint capabilities (breaking on a source without a line, breaking in an
     * area, column based breaking) we are going to have overlapping breakpoints (breakpoints that
     * hit the same location). Disambiguating them using the line would not make much sense in these
     * cases. Therefore I've discarded all the overlapping checks in favor of arbitrary installs of
     * breakpoints. A debugger client must then use its own technique to verify if a breakpoint is
     * already installed at a particular line. SuspendendedEvents now also publish which breakpoints
     * they have hit.
     */
    /**
     * Sets a breakpoint to halt at a source line.
     * <p>
     * If a breakpoint <em>condition</em> is applied to the breakpoint, then the condition will be
     * assumed to be in the same language as the code location where attached.
     *
     *
     * @param ignoreCount number of hits to ignore before halting
     * @param lineLocation where to set the breakpoint (source, line number)
     * @param oneShot breakpoint disposes itself after fist hit, if {@code true}
     * @return a new breakpoint, initially enabled
     * @throws IOException if the breakpoint can not be set.
     * @since 0.9
     * @deprecated use {@link Breakpoint}.{@link Breakpoint#newBuilder(Source)
     *             newBuilder(lineLocation.getSource))}.lineIs(lineLocation.getLineNumber()).build()
     *             instead. You can install a breakpoint with
     *             {@link DebuggerSession#install(Breakpoint)}.
     */
    @SuppressWarnings("deprecation")
    @TruffleBoundary
    @Deprecated
    public Breakpoint setLineBreakpoint(int ignoreCount, com.oracle.truffle.api.source.LineLocation lineLocation, boolean oneShot) throws IOException {
        return setLineBreakpointImpl(ignoreCount, lineLocation.getSource(), lineLocation.getLineNumber(), oneShot);
    }

    /**
     * Sets a breakpoint to halt at a source line.
     * <p>
     * If a breakpoint <em>condition</em> is applied to the breakpoint, then the condition will be
     * assumed to be in the same language as the code location where attached.
     *
     *
     * @param ignoreCount number of hits to ignore before halting
     * @param sourceUri URI of the source to set the breakpoint into
     * @param line line number of the breakpoint
     * @param oneShot breakpoint disposes itself after fist hit, if {@code true}
     * @return a new breakpoint, initially enabled
     * @throws IOException if the breakpoint can not be set.
     * @since 0.14
     * @deprecated use {@link Breakpoint}.{@link Breakpoint#newBuilder(Source) newBuilder(line)}
     *             .lineIs(line).oneShot().build() instead. You can install a breakpoint with
     *             {@link DebuggerSession#install(Breakpoint)}.
     */
    @TruffleBoundary
    @Deprecated
    public Breakpoint setLineBreakpoint(int ignoreCount, URI sourceUri, int line, boolean oneShot) throws IOException {
        return setLineBreakpointImpl(ignoreCount, sourceUri, line, oneShot);
    }

    private Breakpoint setLineBreakpointImpl(int ignoreCount, Object key, int line, boolean oneShot) throws IOException {
        Breakpoint breakpoint = breakpointPerLocation.get(new BreakpointLocation(key, line));
        if (breakpoint != null) {
            if (ignoreCount == breakpoint.getIgnoreCount()) {
                throw new IOException("Breakpoint already set for " + key + " line: " + line);
            }
            breakpoint.setIgnoreCount(ignoreCount);
            return breakpoint;
        }
        Breakpoint.Builder builder;
        if (key instanceof Source) {
            builder = Breakpoint.newBuilder((Source) key);
        } else {
            assert key instanceof URI;
            builder = Breakpoint.newBuilder((URI) key);
        }
        builder.lineIs(line);
        if (oneShot) {
            builder.oneShot();
        }
        breakpoint = builder.build();
        breakpointPerLocation.put(breakpoint.getLocationKey(), breakpoint);
        getLegacySession().install(breakpoint);
        return breakpoint;
    }

    PolyglotEngine getSourceVM() {
        return sourceVM;
    }

    Env getEnv() {
        return env;
    }

    Instrumenter getInstrumenter() {
        return env.getInstrumenter();
    }

    /**
     * Returns the session that is used to support the deprecated {@link ExecutionEvent} API.
     */
    DebuggerSession getLegacySession() {
        return legacySession;
    }

    void disposeBreakpoint(Breakpoint breakpoint) {
        if (breakpoint.getSession() == getLegacySession()) {
            breakpointPerLocation.remove(breakpoint.getLocationKey());
        }
    }

    static void trace(String message, Object... parameters) {
        if (TRACE) {
            PrintStream out = System.out;
            out.println("Debugger: " + String.format(message, parameters));
        }
    }

    /**
     * Finds debugger associated with given engine. There is at most one debugger associated with
     * any {@link PolyglotEngine}.
     *
     * @param engine the engine to find debugger for
     * @return an instance of associated debugger, never <code>null</code>
     * @since 0.9
     */
    public static Debugger find(PolyglotEngine engine) {
        return DebuggerInstrument.getDebugger(engine, new DebuggerInstrument.DebuggerFactory() {
            public Debugger create(PolyglotEngine e, Env env) {
                return new Debugger(e, env);
            }
        });
    }

    /**
     * Finds the debugger associated with a given language environment. There is at most one
     * debugger associated with any {@link PolyglotEngine}. Please note that a debugger instance
     * looked up with a language also has access to all other languages and sources that were loaded
     * by them.
     *
     * @param env the language environment to find debugger for
     * @return an instance of associated debugger, never <code>null</code>
     * @since 0.17
     */
    public static Debugger find(TruffleLanguage.Env env) {
        return find((PolyglotEngine) ACCESSOR.findVM(env));
    }

    static final class AccessorDebug extends Accessor {
        @Override
        protected DebugSupport debugSupport() {
            return new DebugImpl();
        }

        private static final class DebugImpl extends DebugSupport {

            @SuppressWarnings("deprecation")
            @Override
            @TruffleBoundary
            public void executionStarted(Object vm) {
                final PolyglotEngine engine = (PolyglotEngine) vm;
                dispatchEvent(engine, new ExecutionEvent(engine), EngineSupport.EXECUTION_EVENT);
            }

        }

        /*
         * TODO remove when deprecated API is removed.
         */
        static void dispatchEvent(PolyglotEngine vm, Object event, int type) {
            ACCESSOR.engineSupport().dispatchEvent(vm, event, type);
        }

        /*
         * TODO get rid of this access and replace it with an API in {@link TruffleInstrument.Env}.
         * I don't think {@link CallTarget} is the right return type here as we want to make it
         * embeddable into the current AST.
         */
        @SuppressWarnings("rawtypes")
        protected CallTarget parse(Source code, Node context, String... argumentNames) {
            RootNode rootNode = context.getRootNode();
            Class<? extends TruffleLanguage> languageClass = nodes().findLanguage(rootNode);
            if (languageClass == null) {
                throw new IllegalStateException("Could not resolve language class for root node " + rootNode);
            }
            final TruffleLanguage<?> truffleLanguage = engineSupport().findLanguageImpl(null, languageClass, code.getMimeType());
            return languageSupport().parse(truffleLanguage, code, context, argumentNames);
        }

        /*
         * TODO we should have a way to identify a language in the instrumentation API without
         * accessor.
         */
        @SuppressWarnings("rawtypes")
        protected Class<? extends TruffleLanguage> findLanguage(RootNode rootNode) {
            return nodes().findLanguage(rootNode);
        }

        /*
         * TODO we should have a better way to publish services from instruments to languages.
         */
        protected Object findVM(com.oracle.truffle.api.TruffleLanguage.Env env) {
            return languageSupport().getVM(env);
        }

        /*
         * TODO I initially moved this to TruffleInstrument.Env but decided against as a new API for
         * inline parsing might replace it.
         */
        protected Object evalInContext(Object sourceVM, Node node, MaterializedFrame frame, String code) {
            return languageSupport().evalInContext(sourceVM, code, node, frame);
        }

    }

    static final AccessorDebug ACCESSOR = new AccessorDebug();

}
