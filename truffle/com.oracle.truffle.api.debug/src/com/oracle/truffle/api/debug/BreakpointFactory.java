/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.debug.Breakpoint.State.DISABLED;
import static com.oracle.truffle.api.debug.Breakpoint.State.DISABLED_UNRESOLVED;
import static com.oracle.truffle.api.debug.Breakpoint.State.DISPOSED;
import static com.oracle.truffle.api.debug.Breakpoint.State.ENABLED;
import static com.oracle.truffle.api.debug.Breakpoint.State.ENABLED_UNRESOLVED;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.Breakpoint.State;
import com.oracle.truffle.api.debug.Debugger.BreakpointCallback;
import com.oracle.truffle.api.debug.Debugger.WarningLog;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.LineLocation;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.utilities.CyclicAssumption;

/**
 * Creator and manager of program breakpoints.
 */
final class BreakpointFactory {

    private static final boolean TRACE = Boolean.getBoolean("truffle.debug.trace");
    private static final PrintStream OUT = System.out;
    private static final String TRACE_PREFIX = "Brkpt";

    @TruffleBoundary
    private static void trace(String format, Object... args) {
        if (TRACE) {
            OUT.println(String.format("%s: %s", TRACE_PREFIX, String.format(format, args)));
        }
    }

    /**
     * Map: Location key ==> attached breakpoints, where key is either a {@link LineLocation} or
     * {@linkplain String tag}; there may be no more than one breakpoint per key.
     */
    private final Map<Object, BreakpointImpl> breakpoints = new HashMap<>();

    private static final Comparator<Entry<Object, BreakpointImpl>> BREAKPOINT_COMPARATOR = new Comparator<Entry<Object, BreakpointImpl>>() {

        @Override
        public int compare(Entry<Object, BreakpointImpl> entry1, Entry<Object, BreakpointImpl> entry2) {
            final Object key1 = entry1.getKey();
            final Object key2 = entry2.getKey();
            if (key1 instanceof LineLocation && key2 instanceof LineLocation) {
                final LineLocation line1 = (LineLocation) key1;
                final LineLocation line2 = (LineLocation) key2;
                final int nameOrder = line1.getSource().getShortName().compareTo(line2.getSource().getShortName());
                if (nameOrder != 0) {
                    return nameOrder;
                }
                return Integer.compare(line1.getLineNumber(), line2.getLineNumber());
            }
            return key1.toString().compareTo(key2.toString());
        }
    };

    /**
     * Globally suspends all line breakpoint activity when {@code false}, ignoring whether
     * individual breakpoints are enabled.
     */
    @CompilationFinal boolean breakpointsActive = true;
    private final CyclicAssumption breakpointsActiveUnchanged = new CyclicAssumption("All breakpoints globally active");

    private final Instrumenter instrumenter;
    private final BreakpointCallback breakpointCallback;
    private final WarningLog warningLog;

    BreakpointFactory(Instrumenter instrumenter, BreakpointCallback breakpointCallback, final WarningLog warningLog) {
        this.instrumenter = instrumenter;
        this.breakpointCallback = breakpointCallback;
        this.warningLog = warningLog;
    }

    /**
     * Globally enables breakpoint activity; all breakpoints are ignored when set to {@code false}.
     * When set to {@code true}, the enabled/disabled status of each breakpoint determines whether
     * it will trigger when flow of execution reaches it.
     *
     * @param breakpointsActive
     */
    void setActive(boolean breakpointsActive) {
        if (this.breakpointsActive != breakpointsActive) {
            breakpointsActiveUnchanged.invalidate();
            this.breakpointsActive = breakpointsActive;
        }
    }

    /**
     * Creates a new line breakpoint if one doesn't already exist. If one does exist, then resets
     * the <em>ignore count</em>.
     * <p>
     * If a breakpoint <em>condition</em> is applied to the breakpoint, then the condition will be
     * assumed to be in the same language as the code location where attached.
     *
     * @param lineLocation where to set the breakpoint
     * @param ignoreCount number of initial hits before the breakpoint starts causing breaks.
     * @param oneShot whether the breakpoint should dispose itself after one hit
     * @return a possibly new breakpoint
     * @throws IOException if a breakpoint already exists at the location and the ignore count is
     *             the same
     */
    Breakpoint create(int ignoreCount, LineLocation lineLocation, boolean oneShot) throws IOException {
        BreakpointImpl breakpoint = breakpoints.get(lineLocation);
        if (breakpoint == null) {
            final SourceSectionFilter query = SourceSectionFilter.newBuilder().sourceIs(lineLocation.getSource()).lineIs(lineLocation.getLineNumber()).tagIs(Debugger.HALT_TAG).build();
            breakpoint = new BreakpointImpl(lineLocation, query, ignoreCount, oneShot);
            if (TRACE) {
                trace("NEW " + breakpoint.getShortDescription());
            }
            breakpoints.put(lineLocation, breakpoint);
        } else {
            if (ignoreCount == breakpoint.getIgnoreCount()) {
                throw new IOException("Breakpoint already set at location " + lineLocation);
            }
            breakpoint.setIgnoreCount(ignoreCount);
            if (TRACE) {
                trace("CHANGED ignoreCount %s", breakpoint.getShortDescription());
            }
        }
        return breakpoint;
    }

    /**
     * Creates a new line breakpoint if one doesn't already exist. If one does exist, then resets
     * the <em>ignore count</em>.
     * <p>
     * If a breakpoint <em>condition</em> is applied to the breakpoint, then the condition will be
     * assumed to be in the same language as the code location where attached.
     *
     * @param lineLocation where to set the breakpoint
     * @param ignoreCount number of initial hits before the breakpoint starts causing breaks.
     * @param oneShot whether the breakpoint should dispose itself after one hit
     * @return a possibly new breakpoint
     * @throws IOException if a breakpoint already exists at the location and the ignore count is
     *             the same
     */
    Breakpoint create(int ignoreCount, String tag, boolean oneShot) throws IOException {
        BreakpointImpl breakpoint = breakpoints.get(tag);
        if (breakpoint == null) {
            final SourceSectionFilter query = SourceSectionFilter.newBuilder().tagIs(tag).build();
            breakpoint = new BreakpointImpl(tag, query, ignoreCount, oneShot);
            if (TRACE) {
                trace("NEW " + breakpoint.getShortDescription());
            }
            breakpoints.put(tag, breakpoint);
        } else {
            if (ignoreCount == breakpoint.getIgnoreCount()) {
                throw new IOException("Breakpoint already set at location " + tag);
            }
            breakpoint.setIgnoreCount(ignoreCount);
            if (TRACE) {
                trace("CHANGED ignoreCount %s", breakpoint.getShortDescription());
            }
        }
        return breakpoint;
    }

    /**
     * Returns the {@link Breakpoint} for a given, if any, either a {@link LineLocation} or
     * {@linkplain String tag}. There should only ever be one breakpoint per location.
     *
     * @param lineLocation The {@link LineLocation} to get the breakpoint for.
     * @return The breakpoint for the given line.
     */
    Breakpoint get(Object key) {
        return breakpoints.get(key);
    }

    /**
     * Gets all current line breakpoints,regardless of status; sorted and modification safe.
     */
    List<Breakpoint> getAll() {
        ArrayList<Entry<Object, BreakpointImpl>> entries = new ArrayList<>(breakpoints.entrySet());
        Collections.sort(entries, BREAKPOINT_COMPARATOR);

        final ArrayList<Breakpoint> breakpointList = new ArrayList<>(entries.size());
        for (Entry<Object, BreakpointImpl> entry : entries) {
            breakpointList.add(entry.getValue());
        }
        return breakpointList;
    }

    /**
     * Removes the associated instrumentation for all one-shot breakpoints only.
     */
    void disposeOneShots() {
        for (BreakpointImpl breakpoint : breakpoints.values()) {
            if (breakpoint.isOneShot()) {
                breakpoint.dispose();
            }
        }
    }

    /**
     * Removes all knowledge of a breakpoint, presumed disposed.
     */
    private void forget(BreakpointImpl breakpoint) {
        assert breakpoint.getState() == State.DISPOSED;
        breakpoints.remove(breakpoint.getKey());
    }

    private final class BreakpointImpl extends Breakpoint implements ExecutionEventNodeFactory {

        private static final String SHOULD_NOT_HAPPEN = "BreakpointImpl:  should not happen";

        private final Object locationKey;
        private final SourceSectionFilter locationQuery;
        private final boolean isOneShot;
        private int ignoreCount;
        private int hitCount = 0;
        private State state = ENABLED_UNRESOLVED;
        @SuppressWarnings("rawtypes") private EventBinding binding;

        // Cached assumption that the global status of line breakpoint activity has not changed.
        private Assumption breakpointsActiveAssumption;

        // Whether this breakpoint is enable/disabled
        @CompilationFinal private boolean isEnabled;
        private Assumption enabledUnchangedAssumption;

        private Source conditionSource;
        @SuppressWarnings("rawtypes") private Class<? extends TruffleLanguage> condLangClass;

        BreakpointImpl(Object key, SourceSectionFilter query, int ignoreCount, boolean isOneShot) {
            super();
            this.ignoreCount = ignoreCount;
            this.isOneShot = isOneShot;
            this.locationKey = key;
            this.locationQuery = query;
            this.binding = instrumenter.attachListener(locationQuery, new BreakpointListener());
            this.breakpointsActiveAssumption = BreakpointFactory.this.breakpointsActiveUnchanged.getAssumption();
            this.isEnabled = true;
            this.enabledUnchangedAssumption = Truffle.getRuntime().createAssumption("Breakpoint enabled state unchanged");
        }

        @Override
        public String getLocationDescription() {
            if (locationKey instanceof LineLocation) {
                return "Line: " + ((LineLocation) locationKey).getShortDescription();
            }
            return "Tag: " + locationKey.toString();
        }

        @Override
        public boolean isEnabled() {
            return isEnabled;
        }

        @Override
        public void setEnabled(boolean enabled) {
            if (enabled != isEnabled) {
                switch (getState()) {
                    case ENABLED:
                        assert !enabled : SHOULD_NOT_HAPPEN;
                        doSetEnabled(false);
                        changeState(DISABLED);
                        break;
                    case ENABLED_UNRESOLVED:
                        assert !enabled : SHOULD_NOT_HAPPEN;
                        doSetEnabled(false);
                        changeState(DISABLED_UNRESOLVED);
                        break;
                    case DISABLED:
                        assert enabled : SHOULD_NOT_HAPPEN;
                        doSetEnabled(true);
                        changeState(ENABLED);
                        break;
                    case DISABLED_UNRESOLVED:
                        assert enabled : SHOULD_NOT_HAPPEN;
                        doSetEnabled(true);
                        changeState(DISABLED_UNRESOLVED);
                        break;
                    case DISPOSED:
                        assert false : "breakpoint disposed";
                        break;
                    default:
                        assert false : SHOULD_NOT_HAPPEN;
                        break;
                }
            }
        }

        @Override
        public Source getCondition() {
            return conditionSource;
        }

        @Override
        public void setCondition(String expr) throws IOException {
            if (getState() == DISPOSED) {
                throw new IllegalStateException("Attempt to modify a disposed breakpoint");
            }
            binding.dispose();
            if (expr == null) {
                conditionSource = null;
                binding = instrumenter.attachListener(locationQuery, new BreakpointListener());
            } else {
                conditionSource = Source.fromText(expr, "breakpoint condition from text: " + expr);
                binding = instrumenter.attachFactory(locationQuery, this);
            }
        }

        @Override
        public boolean isOneShot() {
            return isOneShot;
        }

        @Override
        public int getIgnoreCount() {
            return ignoreCount;
        }

        @Override
        public void setIgnoreCount(int ignoreCount) {
            this.ignoreCount = ignoreCount;
        }

        @Override
        public int getHitCount() {
            return hitCount;
        }

        @Override
        public State getState() {
            return state;
        }

        @TruffleBoundary
        @Override
        public void dispose() {
            if (getState() != DISPOSED) {
                binding.dispose();
                changeState(DISPOSED);
                BreakpointFactory.this.forget(this);
            }
        }

        /* EventNodeFactory for breakpoint condition */
        @Override
        public ExecutionEventNode create(EventContext context) {
            assert conditionSource != null;
            final Node instrumentedNode = context.getInstrumentedNode();
            if (condLangClass == null) {
                condLangClass = Debugger.ACCESSOR.findLanguage(instrumentedNode);
                if (condLangClass == null) {
                    warningLog.addWarning("Unable to find language for condition: \"" + conditionSource.getCode() + "\" at " + getLocationDescription());
                    return null;
                }
            }
            try {
                final CallTarget callTarget = Debugger.ACCESSOR.parse(condLangClass, conditionSource, instrumentedNode, new String[0]);
                final DirectCallNode callNode = Truffle.getRuntime().createDirectCallNode(callTarget);
                return new BreakpointConditionEventNode(instrumentedNode, callNode);
            } catch (IOException e) {
                warningLog.addWarning("Unable to parse breakpoint condition: \"" + conditionSource.getCode() + "\" at " + getLocationDescription());
                return null;
            }
        }

        private String getShortDescription() {
            if (locationKey instanceof LineLocation) {
                return "Breakpoint@" + ((LineLocation) locationKey).getShortDescription();
            }
            return "Breakpoint@" + locationKey.toString();
        }

        private void doSetEnabled(boolean enabled) {
            if (this.isEnabled != enabled) {
                enabledUnchangedAssumption.invalidate();
                this.isEnabled = enabled;
            }
        }

        private Object getKey() {
            return locationKey;
        }

        private void changeState(State after) {
            if (TRACE) {
                trace("STATE %s-->%s %s", getState().getName(), after.getName(), getShortDescription());
            }
            this.state = after;
        }

        private void doBreak(Node node, VirtualFrame vFrame) {
            if (++hitCount > ignoreCount) {
                breakpointCallback.haltedAt(node, vFrame.materialize(), "Breakpoint");
            }
        }

        /**
         * Receives notification from an attached listener that execution is about to enter a node
         * where the breakpoint is set. Designed so that when in the fast path, there is either an
         * unconditional "halt" call to the debugger or nothing.
         */
        private void nodeEnter(Node astNode, VirtualFrame vFrame) {
            try {
                // Deopt if the global active/inactive flag has changed
                this.breakpointsActiveAssumption.check();
            } catch (InvalidAssumptionException ex) {
                this.breakpointsActiveAssumption = BreakpointFactory.this.breakpointsActiveUnchanged.getAssumption();
            }
            try {
                // Deopt if the enabled/disabled state of this breakpoint has changed
                this.enabledUnchangedAssumption.check();
            } catch (InvalidAssumptionException ex) {
                this.enabledUnchangedAssumption = Truffle.getRuntime().createAssumption("Breakpoint enabled state unchanged");
            }
            if (BreakpointFactory.this.breakpointsActive && this.isEnabled) {
                if (isOneShot()) {
                    dispose();
                }
                BreakpointImpl.this.doBreak(astNode, vFrame);
            }
        }

        private void conditionFailure(Node node, VirtualFrame vFrame, Exception ex) {
            addExceptionWarning(ex);
            if (TRACE) {
                trace("breakpoint failure = %s  %s", ex, getShortDescription());
            }
            // Take the breakpoint if evaluation fails.
            nodeEnter(node, vFrame);
        }

        @TruffleBoundary
        private void addExceptionWarning(Exception ex) {
            warningLog.addWarning(String.format("Exception in %s:  %s", getShortDescription(), ex.getMessage()));
        }

        /** Attached to implement an unconditional breakpoint. */
        private final class BreakpointListener implements ExecutionEventListener {

            @Override
            public void onEnter(EventContext context, VirtualFrame frame) {
                if (TRACE) {
                    trace("hit breakpoint " + getShortDescription());
                }
                BreakpointImpl.this.nodeEnter(context.getInstrumentedNode(), frame);
            }

            @Override
            public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
            }

            @Override
            public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
            }

            @Override
            public String toString() {
                return getShortDescription();
            }
        }

        /** Attached to implement a conditional breakpoint. */
        private class BreakpointConditionEventNode extends ExecutionEventNode {
            @Child DirectCallNode callNode;
            final Node instrumentedNode;

            BreakpointConditionEventNode(Node node, DirectCallNode callNode) {
                this.instrumentedNode = node;
                this.callNode = callNode;
            }

            @Override
            protected void onEnter(VirtualFrame frame) {
                try {
                    Object result = callNode.call(frame, new Object[0]);
                    if (result instanceof Boolean) {
                        if (TRACE) {
                            trace("breakpoint cond=%b %s %s", result, conditionSource.getCode(), getShortDescription());
                        }
                        if ((Boolean) result) {
                            nodeEnter(instrumentedNode, frame); // as if unconditional
                        }
                    } else {
                        conditionFailure(instrumentedNode, frame, new RuntimeException("breakpoint condition failure: non-boolean result " + conditionSource.getCode()));
                    }
                } catch (Exception ex) {
                    conditionFailure(instrumentedNode, frame, new RuntimeException("breakpoint condition failure: " + conditionSource.getCode() + ex.getMessage()));
                }
            }
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder(getClass().getSimpleName());
            sb.append(" state=");
            sb.append(getState() == null ? "<none>" : getState().getName());
            if (isOneShot()) {
                sb.append(", " + "One-Shot");
            }
            if (getCondition() != null) {
                sb.append(", condition=\"" + getCondition() + "\"");
            }
            return sb.toString();
        }
    }
}
