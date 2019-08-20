/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
import com.oracle.truffle.api.instrumentation.Instrumenter;
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

    static final boolean TRACE = Boolean.getBoolean("truffle.debug.trace");

    private final Env env;
    final List<Object> propSupport = new CopyOnWriteArrayList<>();
    private final List<Consumer<Breakpoint>> breakpointAddedListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Breakpoint>> breakpointRemovedListeners = new CopyOnWriteArrayList<>();
    private final Set<DebuggerSession> sessions = new HashSet<>();
    private final List<Breakpoint> breakpoints = new ArrayList<>();
    final Breakpoint alwaysHaltBreakpoint;

    Debugger(Env env) {
        this.env = env;
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

    /**
     * Returns the number of active debugger sessions. This is useful, for instance, in deciding
     * whether to open a new debugger session, depending on whether there is an existing one or not.
     *
     * @since 19.0
     */
    public synchronized int getSessionCount() {
        return sessions.size();
    }

    void disposedSession(DebuggerSession session) {
        synchronized (this) {
            sessions.remove(session);
            for (Breakpoint b : breakpoints) {
                b.sessionClosed(session);
            }
            alwaysHaltBreakpoint.sessionClosed(session);
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
            for (Consumer<Breakpoint> listener : breakpointRemovedListeners) {
                listener.accept(breakpoint.getROWrapper());
            }
        }
        if (Debugger.TRACE) {
            trace("disposed debugger breakpoint %s", breakpoint);
        }
    }

    /**
     * Add a listener that is notified when a new breakpoint is added into {@link #getBreakpoints()
     * list of breakpoints}. The reported breakpoint is not {@link Breakpoint#isModifiable()
     * modifiable}.
     *
     * @since 19.0
     */
    public void addBreakpointAddedListener(Consumer<Breakpoint> listener) {
        breakpointAddedListeners.add(listener);
    }

    /**
     * Remove a listener that was added by {@link #addBreakpointAddedListener(Consumer)}.
     *
     * @since 19.0
     */
    public void removeBreakpointAddedListener(Consumer<Breakpoint> listener) {
        breakpointAddedListeners.remove(listener);
    }

    /**
     * Add a listener that is notified when a breakpoint is removed from {@link #getBreakpoints()
     * list of breakpoints}. The reported breakpoint is not {@link Breakpoint#isModifiable()
     * modifiable}.
     *
     * @since 19.0
     */
    public void addBreakpointRemovedListener(Consumer<Breakpoint> listener) {
        breakpointRemovedListeners.add(listener);
    }

    /**
     * Remove a listener that was added by {@link #addBreakpointRemovedListener(Consumer)}.
     *
     * @since 19.0
     */
    public void removeBreakpointRemovedListener(Consumer<Breakpoint> listener) {
        breakpointRemovedListeners.remove(listener);
    }

    Env getEnv() {
        return env;
    }

    Instrumenter getInstrumenter() {
        return env.getInstrumenter();
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
     * @since 19.0
     */
    public static Debugger find(TruffleInstrument.Env env) {
        return env.lookup(env.getInstruments().get("debugger"), Debugger.class);
    }

    /**
     * Finds the debugger associated with a given an engine.
     *
     * @param engine the engine to find debugger for
     * @return an instance of associated debugger, never <code>null</code>
     * @since 19.0
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
