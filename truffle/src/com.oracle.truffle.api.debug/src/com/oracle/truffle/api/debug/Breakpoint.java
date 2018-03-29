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

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecuteSourceEvent;
import com.oracle.truffle.api.instrumentation.ExecuteSourceListener;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.SourceFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.SlowPathException;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * A request that guest language program execution be suspended at specified locations on behalf of
 * a debugging client {@linkplain DebuggerSession session}.
 * <p>
 * <h4>Breakpoint lifetime</h4>
 * <p>
 * <ul>
 * <li>A client of a {@link DebuggerSession} uses a {@link Builder} to create a new breakpoint,
 * choosing among multiple ways to specify the intended location. Examples include a specified
 * {@link #newBuilder(Source) source}, a specified {@link #newBuilder(URI) URI}, line ranges, or an
 * exact {@link #newBuilder(SourceSection) SourceSection}.</li>
 *
 * <li>A new breakpoint cannot affect execution until after it has been
 * {@linkplain DebuggerSession#install(Breakpoint) installed} by a session, which may be done only
 * once.</li>
 *
 * <li>A breakpoint that is both installed and {@linkplain Breakpoint#isEnabled() enabled} (true by
 * default) will suspend any guest language execution thread that arrives at a matching AST
 * location. The breakpoint (synchronously) {@linkplain SuspendedCallback calls back} to the
 * responsible session on the execution thread.</li>
 *
 * <li>A breakpoint may be enabled or disabled any number of times.</li>
 *
 * <li>A breakpoint that is no longer needed may be {@linkplain #dispose() disposed}. A disposed
 * breakpoint:
 * <ul>
 * <li>is disabled</li>
 * <li>is not installed in any session</li>
 * <li>can have no effect on program execution, and</li>
 * <li>must not be used again.</li>
 * </ul>
 * </li>
 *
 * <li>A session being {@linkplain DebuggerSession#close() closed} disposes all installed
 * breakpoints.</li>
 * </ul>
 * </p>
 * <p>
 * Example usage: {@link com.oracle.truffle.api.debug.BreakpointSnippets#example()}
 *
 * @since 0.9
 */
public class Breakpoint {

    private static final Breakpoint BUILDER_INSTANCE = new Breakpoint();

    private final SuspendAnchor suspendAnchor;
    private final BreakpointLocation locationKey;
    private final boolean oneShot;
    private final ResolveListener resolveListener;

    private volatile Debugger debugger;
    private final List<DebuggerSession> sessions = new LinkedList<>();
    private volatile Assumption sessionsUnchanged;

    private volatile boolean enabled;
    private volatile boolean resolved;
    private volatile int ignoreCount;
    private volatile boolean disposed;
    private volatile String condition;
    private volatile boolean global;
    private volatile GlobalBreakpoint roWrapper;

    /* We use long instead of int in the implementation to avoid not hitting again on overflows. */
    private final AtomicLong hitCount = new AtomicLong();
    private volatile Assumption conditionUnchanged;
    private volatile Assumption conditionExistsUnchanged;

    private volatile EventBinding<? extends ExecutionEventNodeFactory> breakpointBinding;
    private EventBinding<?> sourceBinding;

    Breakpoint(BreakpointLocation key, SuspendAnchor suspendAnchor, boolean oneShot, ResolveListener resolveListener) {
        this.locationKey = key;
        this.suspendAnchor = suspendAnchor;
        this.oneShot = oneShot;
        this.resolveListener = resolveListener;
        this.enabled = true;
    }

    private Breakpoint() {
        this.locationKey = null;
        this.suspendAnchor = SuspendAnchor.BEFORE;
        this.oneShot = false;
        this.resolveListener = null;
    }

    /**
     * @return whether this breakpoint is permanently unable to affect execution
     * @see #dispose()
     *
     * @since 0.17
     */
    public boolean isDisposed() {
        return disposed;
    }

    /**
     * @return whether this breakpoint is currently allowed to suspend execution (true by default)
     * @see #setEnabled(boolean)
     *
     * @since 0.9
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Controls whether this breakpoint is currently allowed to suspend execution (true by default).
     * This can be changed arbitrarily until breakpoint is {@linkplain #dispose() disposed}.
     * <p>
     * When not {@link #isModifiable() modifiable}, {@link IllegalStateException} is thrown.
     *
     * @param enabled whether this breakpoint should be allowed to suspend execution
     *
     * @since 0.9
     */
    public synchronized void setEnabled(boolean enabled) {
        if (disposed) {
            // cannot enable disposed breakpoints
            return;
        }
        if (this.enabled != enabled) {
            if (!sessions.isEmpty()) {
                if (enabled) {
                    install();
                } else {
                    uninstall();
                }
            }
            this.enabled = enabled;
        }
    }

    /**
     * @return whether at least one source has been loaded that contains a match for this
     *         breakpoint's location.
     *
     * @since 0.17
     */
    public boolean isResolved() {
        return resolved;
    }

    /**
     * Assigns to this breakpoint a boolean expression whose evaluation will determine whether the
     * breakpoint suspends execution (i.e. "hits"), {@code null} to remove any condition and always
     * suspend.
     * <p>
     * Breakpoints are by default unconditional.
     * </p>
     * <p>
     * <strong>Evaluation:</strong> expressions are parsed and evaluated in the lexical context of
     * the breakpoint's location. A conditional breakpoint that applies to multiple code locations
     * will be parsed and evaluated separately for each location.
     * </p>
     * <p>
     * <strong>Evaluation failure:</strong> when evaluation of a condition fails for any reason,
     * including the return of a non-boolean value:
     * <ul>
     * <li>execution suspends, as if evaluation had returned {@code true}, and</li>
     * <li>a message is logged that can be
     * {@linkplain SuspendedEvent#getBreakpointConditionException(Breakpoint) retrieved} while
     * execution is suspended.</li>
     * </ul>
     * When not {@link #isModifiable() modifiable}, {@link IllegalStateException} is thrown.
     *
     * @param expression if non{@code -null}, a boolean expression, expressed in the guest language
     *            of the breakpoint's location.
     * @see SuspendedEvent#getBreakpointConditionException(Breakpoint)
     *
     * @since 0.9
     */
    public synchronized void setCondition(String expression) {
        boolean existsChanged = (this.condition == null) != (expression == null);
        this.condition = expression;
        Assumption assumption = conditionUnchanged;
        if (assumption != null) {
            this.conditionUnchanged = null;
            assumption.invalidate();
        }
        if (existsChanged) {
            assumption = conditionExistsUnchanged;
            if (assumption != null) {
                this.conditionExistsUnchanged = null;
                assumption.invalidate();
            }
        }
    }

    /**
     * Returns the expression used to create the current breakpoint condition, null if no condition
     * set.
     *
     * @since 0.20
     */
    @SuppressFBWarnings("UG")
    public String getCondition() {
        return condition;
    }

    /**
     * Permanently prevents this breakpoint from affecting execution. When not
     * {@link #isModifiable() modifiable}, {@link IllegalStateException} is thrown.
     *
     * @since 0.9
     */
    public synchronized void dispose() {
        if (!disposed) {
            setEnabled(false);
            if (sourceBinding != null) {
                sourceBinding.dispose();
                sourceBinding = null;
            }
            for (DebuggerSession session : sessions) {
                session.disposeBreakpoint(this);
            }
            if (debugger != null) {
                debugger.disposeBreakpoint(this);
                debugger = null;
            }
            disposed = true;
        }
    }

    /**
     * @return whether this breakpoint disables itself after suspending execution, i.e. on first hit
     *
     * @since 0.9
     */
    public boolean isOneShot() {
        return oneShot;
    }

    /**
     * @return the number of times breakpoint will be executed but not hit (i.e. suspend execution).
     * @see #setIgnoreCount(int)
     *
     * @since 0.9
     */
    public int getIgnoreCount() {
        return ignoreCount;
    }

    /**
     * Changes the number of times the breakpoint must be executed before it hits (i.e. suspends
     * execution).
     * <p>
     * When a breakpoint {@linkplain #setCondition(String) condition} evaluates to {@code false}:
     * <ul>
     * <li>execution is <em>not</em> suspended</li>
     * <li>it does not count as a hit</li>
     * <li>the remaining {@code ignoreCount} does not change.</li>
     * </ul>
     * When not {@link #isModifiable() modifiable}, {@link IllegalStateException} is thrown.
     *
     * @param ignoreCount number of breakpoint activations to ignore before it hits
     *
     * @since 0.9
     */
    public void setIgnoreCount(int ignoreCount) {
        this.ignoreCount = ignoreCount;
    }

    /**
     * @return the number of times this breakpoint has suspended execution
     *
     * @since 0.9
     */
    public int getHitCount() {
        return (int) hitCount.get();
    }

    /**
     * @return a description of this breakpoint's specified location
     *
     * @since 0.9
     */
    public String getLocationDescription() {
        return locationKey.toString();
    }

    /**
     * Returns the suspended position within the guest language source location.
     *
     * @since 0.32
     */
    public SuspendAnchor getSuspendAnchor() {
        return suspendAnchor;
    }

    /**
     * Test whether this breakpoint can be modified. When <code>false</code>, methods that change
     * breakpoint state throw {@link IllegalStateException}.
     * <p>
     * Unmodifiable breakpoints are created from installed breakpoints as read-only copies to be
     * available to clients other than the one who installed the original breakpoint.
     * {@link Debugger#getBreakpoints()} returns unmodifiable breakpoints, for instance.
     *
     * @return whether this breakpoint can be modified.
     * @since 0.27
     */
    public boolean isModifiable() {
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.9
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());
    }

    DebuggerNode lookupNode(EventContext context) {
        if (!isEnabled()) {
            return null;
        } else {
            EventBinding<? extends ExecutionEventNodeFactory> binding = breakpointBinding;
            if (binding != null) {
                return (DebuggerNode) context.lookupExecutionEventNode(binding);
            }
            return null;
        }
    }

    private synchronized Assumption getConditionUnchanged() {
        if (conditionUnchanged == null) {
            conditionUnchanged = Truffle.getRuntime().createAssumption("Breakpoint condition unchanged.");
        }
        return conditionUnchanged;
    }

    private synchronized Assumption getConditionExistsUnchanged() {
        if (conditionExistsUnchanged == null) {
            conditionExistsUnchanged = Truffle.getRuntime().createAssumption("Breakpoint condition existence unchanged.");
        }
        return conditionExistsUnchanged;
    }

    synchronized void installGlobal(Debugger d) {
        if (disposed) {
            throw new IllegalArgumentException("Cannot install breakpoint, it is disposed already.");
        }
        if (this.debugger != null) {
            throw new IllegalStateException("Breakpoint is already installed in a Debugger instance.");
        }
        install(d);
        this.global = true;
    }

    private void install(Debugger d) {
        assert Thread.holdsLock(this);
        if (this.debugger != null && this.debugger != d) {
            throw new IllegalStateException("Breakpoint is already installed in a different Debugger instance.");
        }
        this.debugger = d;
    }

    synchronized boolean install(DebuggerSession d, boolean failOnError) {
        if (disposed) {
            if (failOnError) {
                throw new IllegalArgumentException("Cannot install breakpoint, it is disposed already.");
            } else {
                return false;
            }
        }
        if (this.sessions.contains(d)) {
            if (failOnError) {
                throw new IllegalStateException("Breakpoint is already installed in the session.");
            } else {
                return true;
            }
        }
        install(d.getDebugger());
        this.sessions.add(d);
        sessionsAssumptionInvalidate();
        if (enabled) {
            install();
        }
        return true;
    }

    private void install() {
        assert Thread.holdsLock(this);
        if (sourceBinding == null) {
            SourceFilter filter = locationKey.createSourceFilter();
            final boolean[] sourceResolved = new boolean[]{false};
            sourceBinding = debugger.getInstrumenter().attachExecuteSourceListener(filter, new ExecuteSourceListener() {
                @Override
                public void onExecute(ExecuteSourceEvent event) {
                    if (sourceResolved[0]) {
                        return;
                    }
                    sourceResolved[0] = true;
                    synchronized (Breakpoint.this) {
                        if (sourceBinding != null) {
                            sourceBinding.dispose();
                        }
                    }
                    Source source = event.getSource();
                    SourceSection location = locationKey.adjustLocation(source, debugger.getEnv(), suspendAnchor);
                    if (location != null) {
                        resolveBreakpoint(location);
                    }
                    SourceSectionFilter locationFilter = locationKey.createLocationFilter(source, suspendAnchor);
                    breakpointBinding = createBinding(locationFilter);
                }
            }, true);
            if (sourceResolved[0]) {
                sourceBinding.dispose();
            }
        } else if (breakpointBinding == null && sourceBinding.isDisposed()) {
            // re-installing breakpoint
            SourceSectionFilter locationFilter = locationKey.createLocationFilter(null, suspendAnchor);
            breakpointBinding = createBinding(locationFilter);
        }
    }

    private EventBinding<? extends ExecutionEventNodeFactory> createBinding(SourceSectionFilter locationFilter) {
        EventBinding<BreakpointNodeFactory> binding = debugger.getInstrumenter().attachExecutionEventFactory(locationFilter, new BreakpointNodeFactory());
        synchronized (this) {
            for (DebuggerSession s : sessions) {
                s.allBindings.add(binding);
            }
        }
        return binding;
    }

    boolean isGlobal() {
        return global;
    }

    synchronized void sessionClosed(DebuggerSession d) {
        this.sessions.remove(d);
        sessionsAssumptionInvalidate();
        if (this.sessions.isEmpty()) {
            uninstall();
        }
    }

    private void sessionsAssumptionInvalidate() {
        assert Thread.holdsLock(this);
        Assumption assumption = sessionsUnchanged;
        if (assumption != null) {
            this.sessionsUnchanged = null;
            assumption.invalidate();
        }
    }

    private void resolveBreakpoint(SourceSection resolvedLocation) {
        boolean notifyResolved = false;
        synchronized (this) {
            if (disposed) {
                // cannot resolve disposed breakpoint
                return;
            }
            if (!isResolved()) {
                notifyResolved = true;
                resolved = true;
            }
        }
        if (notifyResolved && resolveListener != null) {
            resolveListener.breakpointResolved(Breakpoint.this, resolvedLocation);
        }
    }

    private void uninstall() {
        assert Thread.holdsLock(this);
        EventBinding<?> binding = breakpointBinding;
        breakpointBinding = null;
        for (DebuggerSession s : sessions) {
            s.allBindings.remove(binding);
        }
        if (binding != null) {
            binding.dispose();
        }
        resolved = false;
    }

    /**
     * Returns <code>true</code> if it should appear in the breakpoints list.
     *
     * @throws BreakpointConditionFailure
     */
    boolean notifyIndirectHit(DebuggerNode source, DebuggerNode node, MaterializedFrame frame) throws BreakpointConditionFailure {
        if (!isEnabled()) {
            return false;
        }
        assert node.getBreakpoint() == this;

        if (source != node) {
            // We're testing a different breakpoint at the same location
            if (!((AbstractBreakpointNode) node).shouldBreak(frame)) {
                return false;
            }
            if (this.hitCount.incrementAndGet() <= ignoreCount) {
                // breakpoint hit was ignored
                return false;
            }
        }

        if (isOneShot()) {
            setEnabled(false);
        }
        return true;
    }

    @TruffleBoundary
    @SuppressWarnings("hiding") // We want to mask "sessions", as we recieve preferred ones
    private void doBreak(DebuggerNode source, DebuggerSession[] sessions, MaterializedFrame frame, boolean onEnter, Object result, BreakpointConditionFailure failure) {
        if (!isEnabled()) {
            // make sure we do not cause break events if we got disabled already
            // the instrumentation framework will make sure that this is not happening if the
            // binding was disposed.
            return;
        }
        if (this.hitCount.incrementAndGet() <= ignoreCount) {
            // breakpoint hit was ignored
            return;
        }
        SuspendAnchor anchor = onEnter ? SuspendAnchor.BEFORE : SuspendAnchor.AFTER;
        for (DebuggerSession session : sessions) {
            if (session.isBreakpointsActive()) {
                session.notifyCallback(source, frame, anchor, null, result, failure);
            }
        }
    }

    Breakpoint getROWrapper() {
        assert global;  // wrappers are for global breakpoints only
        GlobalBreakpoint wrapper = roWrapper;
        if (wrapper == null) {
            synchronized (this) {
                wrapper = roWrapper;
                if (wrapper == null) {
                    roWrapper = wrapper = new GlobalBreakpoint(this);
                }
            }
        }
        return wrapper;
    }

    /**
     * Creates a new breakpoint builder based on a URI location.
     *
     * @param sourceUri a URI to specify breakpoint location
     *
     * @since 0.17
     */
    public static Builder newBuilder(URI sourceUri) {
        return BUILDER_INSTANCE.new Builder(sourceUri);
    }

    /**
     * Creates a new breakpoint builder based on a {@link Source}.
     *
     * @param source a {@link Source} to specify breakpoint location
     *
     * @since 0.17
     */
    public static Builder newBuilder(Source source) {
        return BUILDER_INSTANCE.new Builder(source);
    }

    /**
     * Creates a new breakpoint builder based on the textual region of a guest language syntactic
     * component.
     *
     * @param sourceSection a specification for guest language syntactic component
     *
     * @since 0.17
     */
    public static Builder newBuilder(SourceSection sourceSection) {
        return BUILDER_INSTANCE.new Builder(sourceSection);
    }

    /**
     * Builder implementation for a new {@link Breakpoint breakpoint}.
     *
     * @see Breakpoint#newBuilder(Source)
     * @see Breakpoint#newBuilder(URI)
     * @see Breakpoint#newBuilder(SourceSection)
     *
     * @since 0.17
     */
    public final class Builder {

        private final Object key;

        private int line = -1;
        private SuspendAnchor anchor = SuspendAnchor.BEFORE;
        private int column = -1;
        private ResolveListener resolveListener;
        private int ignoreCount;
        private boolean oneShot;
        private SourceSection sourceSection;
        private SourceElement[] sourceElements;

        private Builder(Object key) {
            Objects.requireNonNull(key);
            this.key = key;
        }

        private Builder(SourceSection key) {
            this(key.getSource());
            Objects.requireNonNull(key);
            sourceSection = key;
        }

        /**
         * Specifies the breakpoint's line number.
         *
         * Can only be invoked once per builder. Cannot be used together with
         * {@link Breakpoint#newBuilder(SourceSection)}.
         *
         * @param line 1-based line number
         * @throws IllegalStateException if {@code line < 1}
         *
         * @since 0.17
         */
        public Builder lineIs(@SuppressWarnings("hiding") int line) {
            if (line <= 0) {
                throw new IllegalArgumentException("Line argument must be > 0.");
            }
            if (this.line != -1) {
                throw new IllegalStateException("LineIs can only be called once per breakpoint builder.");
            }
            if (sourceSection != null) {
                throw new IllegalArgumentException("LineIs cannot be used with source section based breakpoint. ");
            }
            this.line = line;
            return this;
        }

        /**
         * Specify the breakpoint suspension anchor within the guest language source location. By
         * default, the breakpoint suspends {@link SuspendAnchor#BEFORE before} the source location.
         *
         * @param anchor the breakpoint suspension anchor
         * @since 0.32
         */
        public Builder suspendAnchor(@SuppressWarnings("hiding") SuspendAnchor anchor) {
            this.anchor = anchor;
            return this;
        }

        /**
         * Specifies the breakpoint's column number.
         *
         * Can only be invoked once per builder. Cannot be used together with
         * {@link Breakpoint#newBuilder(SourceSection)}. A line needs to be specified before a
         * column can be set.
         *
         * @param column 1-based column number
         * @throws IllegalStateException if {@code column < 1}
         *
         * @since 0.33
         */
        public Builder columnIs(@SuppressWarnings("hiding") int column) {
            if (column <= 0) {
                throw new IllegalArgumentException("Column argument must be > 0.");
            }
            if (this.line == -1) {
                throw new IllegalStateException("ColumnIs can only be called after a line is set.");
            }
            this.column = column;
            return this;
        }

        /**
         * Set a resolve listener. The listener is called when the breakpoint is resolved at the
         * target location. A breakpoint is not resolved till the target source section is loaded.
         * The target resolved location may differ from the specified {@link #lineIs(int) line} and
         * {@link #columnIs(int) column}.
         *
         * @since 0.33
         */
        public Builder resolveListener(@SuppressWarnings("hiding") ResolveListener resolveListener) {
            Objects.requireNonNull(resolveListener);
            if (this.resolveListener != null) {
                throw new IllegalStateException("ResolveListener can only be set once per breakpoint builder.");
            }
            this.resolveListener = resolveListener;
            return this;
        }

        /**
         * Specifies the number of times a breakpoint is ignored until it hits (i.e. suspends
         * execution}.
         *
         * @see Breakpoint#setIgnoreCount(int)
         *
         * @since 0.17
         */
        public Builder ignoreCount(@SuppressWarnings("hiding") int ignoreCount) {
            if (ignoreCount < 0) {
                throw new IllegalArgumentException("IgnoreCount argument must be >= 0.");
            }
            this.ignoreCount = ignoreCount;
            return this;
        }

        /**
         * Specifies that the breakpoint will {@linkplain Breakpoint#setEnabled(boolean) disable}
         * itself after suspending execution, i.e. on first hit.
         * <p>
         * Disabled one-shot breakpoints can be {@linkplain Breakpoint#setEnabled(boolean)
         * re-enabled}.
         *
         * @since 0.17
         */
        public Builder oneShot() {
            this.oneShot = true;
            return this;
        }

        /**
         * Specifies which source elements will this breakpoint adhere to. When not specified,
         * breakpoint adhere to {@link SourceElement#STATEMENT} elements. Can only be invoked once
         * per builder.
         *
         * @param sourceElements a non-empty list of source elements
         * @since 0.33
         */
        public Builder sourceElements(@SuppressWarnings("hiding") SourceElement... sourceElements) {
            if (this.sourceElements != null) {
                throw new IllegalStateException("Step source elements can only be set once per the builder.");
            }
            if (sourceElements.length == 0) {
                throw new IllegalArgumentException("At least one source element needs to be provided.");
            }
            this.sourceElements = sourceElements;
            return this;
        }

        /**
         * @return a new breakpoint instance
         *
         * @since 0.17
         */
        public Breakpoint build() {
            if (sourceElements == null) {
                sourceElements = new SourceElement[]{SourceElement.STATEMENT};
            }
            BreakpointLocation location;
            if (sourceSection != null) {
                location = new BreakpointLocation(key, sourceElements, sourceSection);
            } else {
                location = new BreakpointLocation(key, sourceElements, line, column);
            }
            Breakpoint breakpoint = new Breakpoint(location, anchor, oneShot, resolveListener);
            breakpoint.setIgnoreCount(ignoreCount);
            return breakpoint;
        }

    }

    /**
     * This listener is called when a breakpoint is resolved at the target location. The breakpoint
     * is not resolved till the target source section is loaded. The target resolved location may
     * differ from the specified breakpoint location.
     *
     * @since 0.33
     */
    public interface ResolveListener {

        /**
         * Notify about a breakpoint resolved at the specified location.
         *
         * @param breakpoint The resolved breakpoint
         * @param section The resolved location
         * @since 0.33
         */
        void breakpointResolved(Breakpoint breakpoint, SourceSection section);
    }

    private class BreakpointNodeFactory implements ExecutionEventNodeFactory {

        @Override
        public ExecutionEventNode create(EventContext context) {
            if (!isResolved()) {
                resolveBreakpoint(context.getInstrumentedSourceSection());
            }
            switch (suspendAnchor) {
                case BEFORE:
                    return new BreakpointBeforeNode(Breakpoint.this, context);
                case AFTER:
                    return new BreakpointAfterNode(Breakpoint.this, context);
                default:
                    throw new IllegalStateException("Unknown suspend anchor: " + suspendAnchor);
            }
        }

    }

    private static class BreakpointBeforeNode extends AbstractBreakpointNode {

        BreakpointBeforeNode(Breakpoint breakpoint, EventContext context) {
            super(breakpoint, context);
        }

        @Override
        Set<SuspendAnchor> getSuspendAnchors() {
            return DebuggerSession.ANCHOR_SET_BEFORE;
        }

        @Override
        boolean isActiveAt(SuspendAnchor anchor) {
            return SuspendAnchor.BEFORE == anchor;
        }

        @Override
        protected void onEnter(VirtualFrame frame) {
            onNode(frame, true, null);
        }
    }

    private static class BreakpointAfterNode extends AbstractBreakpointNode {

        BreakpointAfterNode(Breakpoint breakpoint, EventContext context) {
            super(breakpoint, context);
        }

        @Override
        Set<SuspendAnchor> getSuspendAnchors() {
            return DebuggerSession.ANCHOR_SET_AFTER;
        }

        @Override
        boolean isActiveAt(SuspendAnchor anchor) {
            return SuspendAnchor.AFTER == anchor;
        }

        @Override
        protected void onReturnValue(VirtualFrame frame, Object result) {
            onNode(frame, false, result);
        }

    }

    private abstract static class AbstractBreakpointNode extends DebuggerNode {

        private final Breakpoint breakpoint;
        private final BranchProfile breakBranch = BranchProfile.create();

        @Child private ConditionalBreakNode breakCondition;
        @CompilationFinal private Assumption conditionExistsUnchanged;
        @CompilationFinal(dimensions = 1) private DebuggerSession[] sessions;
        @CompilationFinal private Assumption sessionsUnchanged;

        AbstractBreakpointNode(Breakpoint breakpoint, EventContext context) {
            super(context);
            this.breakpoint = breakpoint;
            initializeSessions();
            this.conditionExistsUnchanged = breakpoint.getConditionExistsUnchanged();
            if (breakpoint.condition != null) {
                this.breakCondition = new ConditionalBreakNode(context, breakpoint);
            }
        }

        private void initializeSessions() {
            CompilerAsserts.neverPartOfCompilation();
            synchronized (breakpoint) {
                this.sessions = breakpoint.sessions.toArray(new DebuggerSession[]{});
                sessionsUnchanged = Truffle.getRuntime().createAssumption("Breakpoint sessions unchanged.");
                breakpoint.sessionsUnchanged = sessionsUnchanged;
            }
        }

        @Override
        boolean isStepNode() {
            return false;
        }

        @Override
        Breakpoint getBreakpoint() {
            return breakpoint;
        }

        @Override
        EventBinding<?> getBinding() {
            return breakpoint.breakpointBinding;
        }

        @ExplodeLoop
        protected final void onNode(VirtualFrame frame, boolean onEnter, Object result) {
            if (!sessionsUnchanged.isValid()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                initializeSessions();
            }
            DebuggerSession[] debuggerSessions = sessions;
            boolean active = false;
            List<DebuggerSession> nonDuplicateSessions = null;
            for (DebuggerSession session : debuggerSessions) {
                if (consumeIsDuplicate(session)) {
                    if (nonDuplicateSessions == null) {
                        if (debuggerSessions.length == 1) {
                            // This node is marked as duplicate in the only session that's there.
                            return;
                        }
                    }
                    nonDuplicateSessions = collectNonDuplicateSessions(debuggerSessions, session, nonDuplicateSessions);
                } else if (session.isBreakpointsActive()) {
                    active = true;
                }
            }
            if (!active) {
                return;
            }
            if (nonDuplicateSessions != null) {
                if (nonDuplicateSessions.isEmpty()) {
                    return;
                }
                debuggerSessions = nonDuplicateSessions.toArray(new DebuggerSession[nonDuplicateSessions.size()]);
            }
            if (!conditionExistsUnchanged.isValid()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (breakpoint.condition != null) {
                    this.breakCondition = insert(new ConditionalBreakNode(context, breakpoint));
                    notifyInserted(this.breakCondition);
                } else {
                    this.breakCondition = null;
                }
                conditionExistsUnchanged = breakpoint.getConditionExistsUnchanged();
            }
            BreakpointConditionFailure conditionError = null;
            try {
                if (!shouldBreak(frame)) {
                    return;
                }
            } catch (BreakpointConditionFailure e) {
                conditionError = e;
            }
            breakBranch.enter();
            breakpoint.doBreak(this, debuggerSessions, frame.materialize(), onEnter, result, conditionError);
        }

        @TruffleBoundary
        private static List<DebuggerSession> collectNonDuplicateSessions(DebuggerSession[] sessions, DebuggerSession session, List<DebuggerSession> nonDuplicateSessionsList) {
            List<DebuggerSession> nonDuplicateSessions = nonDuplicateSessionsList;
            if (nonDuplicateSessions == null) {
                nonDuplicateSessions = new ArrayList<>(sessions.length);
                for (DebuggerSession s : sessions) {
                    if (s != session) {
                        nonDuplicateSessions.add(s);
                    }
                }
            } else {
                nonDuplicateSessions.remove(session);
            }
            return nonDuplicateSessions;
        }

        boolean shouldBreak(VirtualFrame frame) throws BreakpointConditionFailure {
            if (breakCondition != null) {
                try {
                    return breakCondition.executeBreakCondition(frame, sessions);
                } catch (Throwable e) {
                    CompilerDirectives.transferToInterpreter();
                    throw new BreakpointConditionFailure(breakpoint, e);
                }
            }
            return true;
        }

    }

    static final class BreakpointConditionFailure extends SlowPathException {

        private static final long serialVersionUID = 1L;

        private final Breakpoint breakpoint;

        BreakpointConditionFailure(Breakpoint breakpoint, Throwable cause) {
            super(cause);
            this.breakpoint = breakpoint;
        }

        public Breakpoint getBreakpoint() {
            return breakpoint;
        }

        public Throwable getConditionFailure() {
            return getCause();
        }

    }

    private static class ConditionalBreakNode extends Node {

        private static final Object[] EMPTY_ARRAY = new Object[0];

        private final EventContext context;
        private final Breakpoint breakpoint;
        @Child private SetThreadSuspensionEnabledNode suspensionEnabledNode = SetThreadSuspensionEnabledNodeGen.create();
        @Child private DirectCallNode conditionCallNode;
        @Child private ExecutableNode conditionSnippet;
        @CompilationFinal private Assumption conditionUnchanged;

        ConditionalBreakNode(EventContext context, Breakpoint breakpoint) {
            this.context = context;
            this.breakpoint = breakpoint;
            this.conditionUnchanged = breakpoint.getConditionUnchanged();
        }

        boolean executeBreakCondition(VirtualFrame frame, DebuggerSession[] sessions) {
            if ((conditionSnippet == null && conditionCallNode == null) || !conditionUnchanged.isValid()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                initializeConditional(frame.materialize());
            }
            Object result;
            try {
                suspensionEnabledNode.execute(false, sessions);
                if (conditionSnippet != null) {
                    result = conditionSnippet.execute(frame);
                } else {
                    result = conditionCallNode.call(EMPTY_ARRAY);
                }
            } finally {
                suspensionEnabledNode.execute(true, sessions);
            }
            if (!(result instanceof Boolean)) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalArgumentException("Unsupported return type " + result + " in condition.");
            }
            return (Boolean) result;
        }

        private void initializeConditional(MaterializedFrame frame) {
            Node instrumentedNode = context.getInstrumentedNode();
            final RootNode rootNode = instrumentedNode.getRootNode();
            if (rootNode == null) {
                throw new IllegalStateException("Probe was disconnected from the AST.");
            }

            Source instrumentedSource = context.getInstrumentedSourceSection().getSource();
            Source conditionSource;
            synchronized (breakpoint) {
                conditionSource = Source.newBuilder(breakpoint.condition).language(instrumentedSource.getLanguage()).mimeType(instrumentedSource.getMimeType()).name(
                                "breakpoint condition").build();
                if (conditionSource == null) {
                    throw new IllegalStateException("Condition is not resolved " + rootNode);
                }
                conditionUnchanged = breakpoint.getConditionUnchanged();
            }

            ExecutableNode snippet = breakpoint.debugger.getEnv().parseInline(conditionSource, instrumentedNode, frame);
            if (snippet != null) {
                conditionSnippet = insert(snippet);
                notifyInserted(snippet);
            } else {
                CallTarget callTarget = Debugger.ACCESSOR.parse(conditionSource, instrumentedNode, new String[0]);
                conditionCallNode = insert(Truffle.getRuntime().createDirectCallNode(callTarget));
            }
        }
    }

    /**
     * A read-only wrapper over "global" breakpoint installed on {@link Debugger}. Instances of this
     * wrapper are for public access to global breakpoints.
     */
    @SuppressWarnings("sync-override")
    static final class GlobalBreakpoint extends Breakpoint {

        private final Breakpoint delegate;

        GlobalBreakpoint(Breakpoint delegate) {
            this.delegate = delegate;
        }

        @Override
        public void dispose() {
            fail();
        }

        @Override
        public void setCondition(String expression) {
            fail();
        }

        @Override
        public void setEnabled(boolean enabled) {
            fail();
        }

        @Override
        public void setIgnoreCount(int ignoreCount) {
            fail();
        }

        private static void fail() {
            throw new IllegalStateException("Unmodifiable breakpoint.");
        }

        @Override
        public boolean isModifiable() {
            return false;
        }

        @Override
        public String getCondition() {
            return delegate.getCondition();
        }

        @Override
        public int getHitCount() {
            return delegate.getHitCount();
        }

        @Override
        public int getIgnoreCount() {
            return delegate.getIgnoreCount();
        }

        @Override
        public String getLocationDescription() {
            return delegate.getLocationDescription();
        }

        @Override
        public SuspendAnchor getSuspendAnchor() {
            return delegate.getSuspendAnchor();
        }

        @Override
        public boolean isDisposed() {
            return delegate.isDisposed();
        }

        @Override
        public boolean isEnabled() {
            return delegate.isEnabled();
        }

        @Override
        public boolean isOneShot() {
            return delegate.isOneShot();
        }

        @Override
        public boolean isResolved() {
            return delegate.isResolved();
        }
    }

}

class BreakpointSnippets {

    @SuppressFBWarnings("")
    public void example() {
        SuspendedCallback suspendedCallback = new SuspendedCallback() {
            public void onSuspend(SuspendedEvent event) {
            }
        };
        Source someCode = Source.newBuilder("").mimeType("").name("").build();
        TruffleInstrument.Env instrumentEnvironment = null;
        // @formatter:off
        // BEGIN: BreakpointSnippets.example
        try (DebuggerSession session = Debugger.find(instrumentEnvironment).
                        startSession(suspendedCallback)) {

            // install breakpoint in someCode at line 3.
            session.install(Breakpoint.newBuilder(someCode).
                            lineIs(3).build());

            // install breakpoint for a URI at line 3
            session.install(Breakpoint.newBuilder(someCode.getURI()).
                            lineIs(3).build());

        }
        // END: BreakpointSnippets.example
        // @formatter:on

    }
}
