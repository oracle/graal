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

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.graalvm.polyglot.Engine;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.impl.DebuggerInstrument;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.LoadSourceEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceListener;
import com.oracle.truffle.api.instrumentation.SourceFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

/**
 * Class that simplifies implementing a debugger on top of Truffle. Primarily used to implement
 * debugging protocol support.
 * <p>
 * Access to the (singleton) instance in an engine, is available via:
 * <ul>
 * <li>{@link Debugger#find(Engine)}</li>
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

    /**
     * Name of a property that is fired when a list of breakpoints changes.
     *
     * @since 0.27
     * @see #getBreakpoints()
     * @see #addPropertyChangeListener(java.beans.PropertyChangeListener)
     * @deprecated Use {@link #addBreakpointAddedListener(Consumer)} and
     *             {@link #addBreakpointRemovedListener(Consumer)}
     */
    @Deprecated public static final String PROPERTY_BREAKPOINTS = "breakpoints";
    static final boolean TRACE = Boolean.getBoolean("truffle.debug.trace");

    private final Env env;
    final List<Object> propSupport = new CopyOnWriteArrayList<>();
    private final List<Consumer<Breakpoint>> breakpointAddedListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Breakpoint>> breakpointRemovedListeners = new CopyOnWriteArrayList<>();
    final ObjectStructures.MessageNodes msgNodes;
    private final Set<DebuggerSession> sessions = new HashSet<>();
    private final List<Breakpoint> breakpoints = new ArrayList<>();
    final Breakpoint alwaysHaltBreakpoint;

    Debugger(Env env) {
        this.env = env;
        this.msgNodes = new ObjectStructures.MessageNodes();
        this.alwaysHaltBreakpoint = new Breakpoint(BreakpointLocation.ANY, SuspendAnchor.BEFORE);
        this.alwaysHaltBreakpoint.setEnabled(true);
    }

    /**
     * Starts a new {@link DebuggerSession session} provided with a callback that gets notified
     * whenever the execution is suspended. Uses {@link SourceElement#STATEMENT} as the source
     * element available for stepping. Use
     * {@link #startSession(SuspendedCallback, SourceElement...)} to specify a different set of
     * source elements.
     *
     * @param callback the callback to notify
     * @see DebuggerSession
     * @see SuspendedEvent
     * @see #startSession(SuspendedCallback, SourceElement...)
     * @since 0.17
     */
    public DebuggerSession startSession(SuspendedCallback callback) {
        return startSession(callback, SourceElement.STATEMENT);
    }

    /**
     * Starts a new {@link DebuggerSession session} provided with a callback that gets notified
     * whenever the execution is suspended and with a list of source syntax elements on which it is
     * possible to step. Only steps created with one of these element kinds are accepted in this
     * session. All specified elements are used by steps by default, if not specified otherwise by
     * {@link StepConfig.Builder#sourceElements(SourceElement...)}. When no elements are provided,
     * stepping is not possible and the session itself has no instrumentation overhead.
     *
     * @param callback the callback to notify
     * @param defaultSourceElements a list of source elements, an explicit empty list disables
     *            stepping
     * @see DebuggerSession
     * @see SuspendedEvent
     * @since 0.33
     */
    public DebuggerSession startSession(SuspendedCallback callback, SourceElement... defaultSourceElements) {
        DebuggerSession session = new DebuggerSession(this, callback, defaultSourceElements);
        Breakpoint[] bpts;
        synchronized (this) {
            sessions.add(session);
            bpts = breakpoints.toArray(new Breakpoint[]{});
        }
        for (Breakpoint b : bpts) {
            session.install(b, true);
        }
        session.install(alwaysHaltBreakpoint, true);
        return session;
    }

    void disposedSession(DebuggerSession session) {
        synchronized (this) {
            sessions.remove(session);
            for (Breakpoint b : breakpoints) {
                b.sessionClosed(session);
            }
        }
    }

    /**
     * Adds a new breakpoint to this Debugger instance and makes it available in all its sessions.
     * <p>
     * The breakpoint suspends execution in all active {@link DebuggerSession sessions} by making a
     * callback to the appropriate session {@link SuspendedCallback callback handler}, together with
     * an event description that includes {@linkplain SuspendedEvent#getBreakpoints() which
     * breakpoint(s)} were hit.
     *
     * @param breakpoint a new breakpoint
     * @return the installed breakpoint
     * @throws IllegalStateException if the session has been closed
     *
     * @since 0.27
     */
    public Breakpoint install(Breakpoint breakpoint) {
        if (breakpoint.isDisposed()) {
            throw new IllegalArgumentException("Cannot install breakpoint, it is already disposed.");
        }
        breakpoint.installGlobal(this);
        DebuggerSession[] ds;
        synchronized (this) {
            this.breakpoints.add(breakpoint);
            ds = sessions.toArray(new DebuggerSession[]{});
        }
        for (DebuggerSession s : ds) {
            s.install(breakpoint, true);
        }
        BreakpointsPropertyChangeEvent.firePropertyChange(this, null, breakpoint);
        for (Consumer<Breakpoint> listener : breakpointAddedListeners) {
            listener.accept(breakpoint.getROWrapper());
        }
        if (Debugger.TRACE) {
            trace("installed debugger breakpoint %s", breakpoint);
        }
        return breakpoint;
    }

    /**
     * Returns all breakpoints {@link #install(com.oracle.truffle.api.debug.Breakpoint) installed}
     * in this debugger instance, in the install order. The returned list contains a current
     * snapshot of breakpoints, those that were {@link Breakpoint#dispose() disposed} are not
     * included.
     * <p>
     * It's not possible to modify state of breakpoints returned from this list, or from methods on
     * listeners, they are not {@link Breakpoint#isModifiable() modifiable}. An attempt to modify
     * breakpoints state using any of their set method, or an attempt to dispose such breakpoints,
     * fails with an {@link IllegalStateException}. Use the original installed breakpoint instance
     * to change breakpoint state or dispose the breakpoint.
     *
     * @since 0.27
     * @see DebuggerSession#getBreakpoints()
     */
    public List<Breakpoint> getBreakpoints() {
        List<Breakpoint> bpts;
        synchronized (this) {
            bpts = new ArrayList<>(this.breakpoints.size());
            for (Breakpoint b : this.breakpoints) {
                bpts.add(b.getROWrapper());
            }
        }
        return Collections.unmodifiableList(bpts);
    }

    /**
     * For package access only, access under synchronized on this.
     */
    List<Breakpoint> getRawBreakpoints() {
        return breakpoints;
    }

    void disposeBreakpoint(Breakpoint breakpoint) {
        boolean removed;
        synchronized (this) {
            removed = breakpoints.remove(breakpoint);
        }
        if (removed) {
            BreakpointsPropertyChangeEvent.firePropertyChange(this, breakpoint, null);
            for (Consumer<Breakpoint> listener : breakpointRemovedListeners) {
                listener.accept(breakpoint.getROWrapper());
            }
        }
        if (Debugger.TRACE) {
            trace("disposed debugger breakpoint %s", breakpoint);
        }
    }

    /**
     * Returns a list of all loaded sources. The sources are returned in the order as they have been
     * loaded by the languages.
     *
     * @return an unmodifiable list of sources
     * @since 0.17
     * @deprecated not very flexible, polls all sources without any notification about changes.
     */
    @Deprecated
    public List<Source> getLoadedSources() {
        final List<Source> sources = new ArrayList<>();
        EventBinding<?> binding = env.getInstrumenter().attachLoadSourceListener(SourceFilter.ANY, new LoadSourceListener() {
            public void onLoad(LoadSourceEvent event) {
                sources.add(event.getSource());
            }
        }, true);
        binding.dispose();
        return Collections.unmodifiableList(sources);
    }

    /**
     * Add a listener that is notified when a new breakpoint is added into {@link #getBreakpoints()
     * list of breakpoints}. The reported breakpoint is not {@link Breakpoint#isModifiable()
     * modifiable}.
     *
     * @since 1.0
     */
    public void addBreakpointAddedListener(Consumer<Breakpoint> listener) {
        breakpointAddedListeners.add(listener);
    }

    /**
     * Remove a listener that was added by {@link #addBreakpointAddedListener(Consumer)}.
     *
     * @since 1.0
     */
    public void removeBreakpointAddedListener(Consumer<Breakpoint> listener) {
        breakpointAddedListeners.remove(listener);
    }

    /**
     * Add a listener that is notified when a breakpoint is removed from {@link #getBreakpoints()
     * list of breakpoints}. The reported breakpoint is not {@link Breakpoint#isModifiable()
     * modifiable}.
     *
     * @since 1.0
     */
    public void addBreakpointRemovedListener(Consumer<Breakpoint> listener) {
        breakpointRemovedListeners.add(listener);
    }

    /**
     * Remove a listener that was added by {@link #addBreakpointRemovedListener(Consumer)}.
     *
     * @since 1.0
     */
    public void removeBreakpointRemovedListener(Consumer<Breakpoint> listener) {
        breakpointRemovedListeners.remove(listener);
    }

    /**
     * Add a property change listener that is notified when a property of this debugger changes.
     *
     * @since 0.27
     * @see #PROPERTY_BREAKPOINTS
     * @deprecated Use {@link #addBreakpointAddedListener(Consumer)} and
     *             {@link #addBreakpointRemovedListener(Consumer)}
     */
    @Deprecated
    public void addPropertyChangeListener(java.beans.PropertyChangeListener listener) {
        // using FQN to avoid mx to generate dependency on java.desktop module
        propSupport.add(listener);
    }

    /**
     * Remove a property change listener that is notified when state of this debugger changes.
     *
     * @since 0.27
     * @see #addPropertyChangeListener(java.beans.PropertyChangeListener)
     * @deprecated Use {@link #removeBreakpointAddedListener(Consumer)} and
     *             {@link #removeBreakpointRemovedListener(Consumer)}
     */
    @Deprecated
    public void removePropertyChangeListener(java.beans.PropertyChangeListener listener) {
        // using FQN to avoid mx to generate dependency on java.desktop module
        propSupport.remove(listener);
    }

    Env getEnv() {
        return env;
    }

    Instrumenter getInstrumenter() {
        return env.getInstrumenter();
    }

    ObjectStructures.MessageNodes getMessageNodes() {
        return msgNodes;
    }

    static void trace(String message, Object... parameters) {
        if (TRACE) {
            PrintStream out = System.out;
            out.println("Debugger: " + String.format(message, parameters));
        }
    }

    /**
     * Finds the debugger associated with a given instrument environment.
     *
     * @param env the instrument environment to find debugger for
     * @return an instance of associated debugger, never <code>null</code>
     * @since 1.0
     */
    public static Debugger find(TruffleInstrument.Env env) {
        return env.lookup(env.getInstruments().get("debugger"), Debugger.class);
    }

    /**
     * Finds the debugger associated with a given an engine.
     *
     * @param engine the engine to find debugger for
     * @return an instance of associated debugger, never <code>null</code>
     * @since 1.0
     */
    public static Debugger find(Engine engine) {
        return engine.getInstruments().get("debugger").lookup(Debugger.class);
    }

    /**
     * Finds the debugger associated with a given language environment. There is at most one
     * debugger associated with any {@link org.graalvm.polyglot.Engine}. Please note that a debugger
     * instance looked up with a language also has access to all other languages and sources that
     * were loaded by them.
     *
     * @param env the language environment to find debugger for
     * @return an instance of associated debugger, never <code>null</code>
     * @since 0.17
     */
    public static Debugger find(TruffleLanguage.Env env) {
        return env.lookup(env.getInstruments().get("debugger"), Debugger.class);
    }

    static final class AccessorDebug extends Accessor {

        @Override
        protected Nodes nodes() {
            return super.nodes();
        }

        /*
         * TODO get rid of this access and replace it with an API in {@link TruffleInstrument.Env}.
         * I don't think {@link CallTarget} is the right return type here as we want to make it
         * embeddable into the current AST.
         */
        protected CallTarget parse(Source code, Node context, String... argumentNames) {
            RootNode rootNode = context.getRootNode();
            return languageSupport().parse(engineSupport().getEnvForInstrument(rootNode.getLanguageInfo()), code, context, argumentNames);
        }

        /*
         * TODO I initially moved this to TruffleInstrument.Env but decided against as a new API for
         * inline parsing might replace it.
         */
        protected Object evalInContext(Source source, Node node, MaterializedFrame frame) {
            return languageSupport().evalInContext(source, node, frame);
        }

    }

    static final AccessorDebug ACCESSOR = new AccessorDebug();

    static {
        DebuggerInstrument.setFactory(new DebuggerInstrument.DebuggerFactory() {
            public Debugger create(Env env) {
                return new Debugger(env);
            }
        });
    }

}
