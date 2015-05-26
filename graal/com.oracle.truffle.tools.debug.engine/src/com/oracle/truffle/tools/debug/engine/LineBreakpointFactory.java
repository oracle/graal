/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.debug.engine;

import static com.oracle.truffle.tools.debug.engine.Breakpoint.BreakpointState.*;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.instrument.impl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.tools.*;
import com.oracle.truffle.tools.debug.engine.DebugEngine.BreakpointCallback;
import com.oracle.truffle.tools.debug.engine.DebugEngine.WarningLog;

//TODO (mlvdv) some common functionality could be factored out of this and TagBreakpointSupport

/**
 * Support class for creating and managing all existing ordinary (user visible) line breakpoints.
 * <p>
 * Notes:
 * <ol>
 * <li>Line breakpoints can only be set at nodes tagged as {@link StandardSyntaxTag#STATEMENT}.</li>
 * <li>A newly created breakpoint looks for probes matching the location, attaches to them if found
 * by installing an {@link Instrument} that calls back to the breakpoint.</li>
 * <li>When Truffle "splits" or otherwise copies an AST, any attached {@link Instrument} will be
 * copied along with the rest of the AST and will call back to the same breakpoint.</li>
 * <li>When notification is received of a new Node being tagged as a statement, and if a
 * breakpoint's line location matches the Probe's line location, then the breakpoint will attach a
 * new Instrument at the probe to activate the breakpoint at that location.</li>
 * <li>A breakpoint may have multiple Instruments deployed, one attached to each Probe that matches
 * the breakpoint's line location; this might happen when a source is reloaded.</li>
 * </ol>
 *
 */
final class LineBreakpointFactory {

    private static final boolean TRACE = false;
    private static final PrintStream OUT = System.out;

    private static final String BREAKPOINT_NAME = "LINE BREAKPOINT";

    private static void trace(String format, Object... args) {
        if (TRACE) {
            OUT.println(String.format("%s: %s", BREAKPOINT_NAME, String.format(format, args)));
        }
    }

    private static final Comparator<Entry<LineLocation, LineBreakpointImpl>> BREAKPOINT_COMPARATOR = new Comparator<Entry<LineLocation, LineBreakpointImpl>>() {

        @Override
        public int compare(Entry<LineLocation, LineBreakpointImpl> entry1, Entry<LineLocation, LineBreakpointImpl> entry2) {
            final LineLocation line1 = entry1.getKey();
            final LineLocation line2 = entry2.getKey();
            final int nameOrder = line1.getSource().getShortName().compareTo(line2.getSource().getShortName());
            if (nameOrder != 0) {
                return nameOrder;
            }
            return Integer.compare(line1.getLineNumber(), line2.getLineNumber());
        }
    };

    private final SourceExecutionProvider sourceExecutionProvider;
    private final BreakpointCallback breakpointCallback;
    private final WarningLog warningLog;

    /**
     * Map: Source lines ==> attached breakpoints. There may be no more than one line breakpoint
     * associated with a line.
     */
    private final Map<LineLocation, LineBreakpointImpl> lineToBreakpoint = new HashMap<>();

    /**
     * A map of {@link LineLocation} to a collection of {@link Probe}s. This list must be
     * initialized and filled prior to being used by this class.
     */
    private final LineToProbesMap lineToProbesMap;

    /**
     * Globally suspends all line breakpoint activity when {@code false}, ignoring whether
     * individual breakpoints are enabled.
     */
    @CompilationFinal private boolean breakpointsActive = true;
    private final CyclicAssumption breakpointsActiveUnchanged = new CyclicAssumption(BREAKPOINT_NAME + " globally active");

    LineBreakpointFactory(SourceExecutionProvider sourceExecutionProvider, BreakpointCallback breakpointCallback, WarningLog warningLog) {
        this.sourceExecutionProvider = sourceExecutionProvider;
        this.breakpointCallback = breakpointCallback;
        this.warningLog = warningLog;

        lineToProbesMap = new LineToProbesMap();
        lineToProbesMap.install();

        Probe.addProbeListener(new DefaultProbeListener() {

            @Override
            public void probeTaggedAs(Probe probe, SyntaxTag tag, Object tagValue) {
                if (tag == StandardSyntaxTag.STATEMENT) {
                    final SourceSection sourceSection = probe.getProbedSourceSection();
                    if (sourceSection != null) {
                        final LineLocation lineLocation = sourceSection.getLineLocation();
                        if (lineLocation != null) {
                            // A Probe with line location tagged STATEMENT we haven't seen before.
                            final LineBreakpointImpl breakpoint = lineToBreakpoint.get(lineLocation);
                            if (breakpoint != null) {
                                try {
                                    breakpoint.attach(probe);
                                } catch (DebugException e) {
                                    warningLog.addWarning(BREAKPOINT_NAME + " failure attaching to newly tagged Probe: " + e.getMessage());
                                    if (TRACE) {
                                        OUT.println(BREAKPOINT_NAME + " failure: " + e.getMessage());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Globally enables line breakpoint activity; all breakpoints are ignored when set to
     * {@code false}. When set to {@code true}, the enabled/disabled status of each breakpoint
     * determines whether it will trigger when flow of execution reaches it.
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
     * Returns the (not yet disposed) breakpoint by id; null if none.
     */
    LineBreakpoint find(long id) {
        for (LineBreakpoint breakpoint : lineToBreakpoint.values()) {
            if (breakpoint.getId() == id) {
                return breakpoint;
            }
        }
        return null;
    }

    /**
     * Gets all current line breakpoints,regardless of status; sorted and modification safe.
     */
    List<LineBreakpoint> getAll() {
        ArrayList<Entry<LineLocation, LineBreakpointImpl>> entries = new ArrayList<>(lineToBreakpoint.entrySet());
        Collections.sort(entries, BREAKPOINT_COMPARATOR);

        final ArrayList<LineBreakpoint> breakpoints = new ArrayList<>(entries.size());
        for (Entry<LineLocation, LineBreakpointImpl> entry : entries) {
            breakpoints.add(entry.getValue());
        }
        return breakpoints;
    }

    /**
     * Creates a new line breakpoint if one doesn't already exist. If one does exist, then resets
     * the <em>ignore count</em>.
     *
     * @param lineLocation where to set the breakpoint
     * @param ignoreCount number of initial hits before the breakpoint starts causing breaks.
     * @param oneShot whether the breakpoint should dispose itself after one hit
     * @return a possibly new breakpoint
     * @throws DebugException if a breakpoint already exists at the location and the ignore count is
     *             the same
     */
    LineBreakpoint create(int groupId, int ignoreCount, LineLocation lineLocation, boolean oneShot) throws DebugException {

        LineBreakpointImpl breakpoint = lineToBreakpoint.get(lineLocation);

        if (breakpoint == null) {
            breakpoint = new LineBreakpointImpl(groupId, ignoreCount, lineLocation, oneShot);

            if (TRACE) {
                trace("NEW " + breakpoint.getShortDescription());
            }

            lineToBreakpoint.put(lineLocation, breakpoint);

            for (Probe probe : lineToProbesMap.findProbes(lineLocation)) {
                if (probe.isTaggedAs(StandardSyntaxTag.STATEMENT)) {
                    breakpoint.attach(probe);
                    break;
                }
            }
        } else {
            if (ignoreCount == breakpoint.getIgnoreCount()) {
                throw new DebugException(BREAKPOINT_NAME + " already set at line " + lineLocation);
            }
            breakpoint.setIgnoreCount(ignoreCount);
            if (TRACE) {
                trace("CHANGED ignoreCount %s", breakpoint.getShortDescription());
            }
        }
        return breakpoint;
    }

    /**
     * Returns the {@link LineBreakpoint} for a given line. There should only ever be one breakpoint
     * per line.
     *
     * @param lineLocation The {@link LineLocation} to get the breakpoint for.
     * @return The breakpoint for the given line.
     */
    LineBreakpoint get(LineLocation lineLocation) {
        return lineToBreakpoint.get(lineLocation);
    }

    /**
     * Removes the associated instrumentation for all one-shot breakpoints only.
     */
    void disposeOneShots() {
        List<LineBreakpointImpl> breakpoints = new ArrayList<>(lineToBreakpoint.values());
        for (LineBreakpointImpl breakpoint : breakpoints) {
            if (breakpoint.isOneShot()) {
                breakpoint.dispose();
            }
        }
    }

    /**
     * Removes all knowledge of a breakpoint, presumed disposed.
     */
    private void forget(LineBreakpointImpl breakpoint) {
        lineToBreakpoint.remove(breakpoint.getLineLocation());
    }

    /**
     * Concrete representation of a line breakpoint, implemented by attaching an instrument to a
     * probe at the designated source location.
     */
    private final class LineBreakpointImpl extends LineBreakpoint implements AdvancedInstrumentResultListener {

        private static final String SHOULD_NOT_HAPPEN = "LineBreakpointImpl:  should not happen";

        private final LineLocation lineLocation;

        // Cached assumption that the global status of line breakpoint activity has not changed.
        private Assumption breakpointsActiveAssumption;

        // Whether this breakpoint is enable/disabled
        @CompilationFinal private boolean isEnabled;
        private Assumption enabledUnchangedAssumption;

        private String conditionExpr;

        /**
         * The instrument(s) that this breakpoint currently has attached to a {@link Probe}:
         * {@code null} if not attached.
         */
        private List<Instrument> instruments = new ArrayList<>();

        public LineBreakpointImpl(int groupId, int ignoreCount, LineLocation lineLocation, boolean oneShot) {
            super(ENABLED_UNRESOLVED, groupId, ignoreCount, oneShot);
            this.lineLocation = lineLocation;

            this.breakpointsActiveAssumption = LineBreakpointFactory.this.breakpointsActiveUnchanged.getAssumption();
            this.isEnabled = true;
            this.enabledUnchangedAssumption = Truffle.getRuntime().createAssumption(BREAKPOINT_NAME + " enabled state unchanged");
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
        public void setCondition(String expr) throws DebugException {
            if (this.conditionExpr != null || expr != null) {
                // De-instrument the Probes instrumented by this breakpoint
                final ArrayList<Probe> probes = new ArrayList<>();
                for (Instrument instrument : instruments) {
                    probes.add(instrument.getProbe());
                    instrument.dispose();
                }
                instruments.clear();
                this.conditionExpr = expr;
                // Re-instrument the probes previously instrumented
                for (Probe probe : probes) {
                    attach(probe);
                }
            }
        }

        @Override
        public String getCondition() {
            return conditionExpr;
        }

        @Override
        public void dispose() {
            if (getState() != DISPOSED) {
                for (Instrument instrument : instruments) {
                    instrument.dispose();
                }
                changeState(DISPOSED);
                LineBreakpointFactory.this.forget(this);
            }
        }

        private void attach(Probe newProbe) throws DebugException {
            if (getState() == DISPOSED) {
                throw new IllegalStateException("Attempt to attach a disposed " + BREAKPOINT_NAME);
            }
            Instrument newInstrument = null;
            if (conditionExpr == null) {
                newInstrument = Instrument.create(new UnconditionalLineBreakInstrumentListener(), BREAKPOINT_NAME);
            } else {
                newInstrument = Instrument.create(this, sourceExecutionProvider.createAdvancedInstrumentRootFactory(conditionExpr, this), Boolean.class, BREAKPOINT_NAME);
            }
            newProbe.attach(newInstrument);
            instruments.add(newInstrument);
            changeState(isEnabled ? ENABLED : DISABLED);
        }

        private void doSetEnabled(boolean enabled) {
            if (this.isEnabled != enabled) {
                enabledUnchangedAssumption.invalidate();
                this.isEnabled = enabled;
            }
        }

        private String getShortDescription() {
            return BREAKPOINT_NAME + "@" + getLineLocation().getShortDescription();
        }

        private void changeState(BreakpointState after) {
            if (TRACE) {
                trace("STATE %s-->%s %s", getState().getName(), after.getName(), getShortDescription());
            }
            setState(after);
        }

        private void doBreak(Node node, VirtualFrame vFrame) {
            if (incrHitCountCheckIgnore()) {
                breakpointCallback.haltedAt(node, vFrame.materialize(), BREAKPOINT_NAME);
            }
        }

        /**
         * Receives notification from the attached instrument that execution is about to enter node
         * where the breakpoint is set. Designed so that when in the fast path, there is either an
         * unconditional "halt" call to the debugger or nothing.
         */
        private void nodeEnter(Node astNode, VirtualFrame vFrame) {

            // Deopt if the global active/inactive flag has changed
            try {
                this.breakpointsActiveAssumption.check();
            } catch (InvalidAssumptionException ex) {
                this.breakpointsActiveAssumption = LineBreakpointFactory.this.breakpointsActiveUnchanged.getAssumption();
            }

            // Deopt if the enabled/disabled state of this breakpoint has changed
            try {
                this.enabledUnchangedAssumption.check();
            } catch (InvalidAssumptionException ex) {
                this.enabledUnchangedAssumption = Truffle.getRuntime().createAssumption("LineBreakpoint enabled state unchanged");
            }

            if (LineBreakpointFactory.this.breakpointsActive && this.isEnabled) {
                if (isOneShot()) {
                    dispose();
                }
                LineBreakpointImpl.this.doBreak(astNode, vFrame);
            }
        }

        public void notifyResult(Node node, VirtualFrame vFrame, Object result) {
            final boolean condition = (Boolean) result;
            if (TRACE) {
                trace("breakpoint condition = %b  %s", condition, getShortDescription());
            }
            if (condition) {
                nodeEnter(node, vFrame);
            }
        }

        @TruffleBoundary
        public void notifyFailure(Node node, VirtualFrame vFrame, RuntimeException ex) {
            warningLog.addWarning(String.format("Exception in %s:  %s", getShortDescription(), ex.getMessage()));
            if (TRACE) {
                trace("breakpoint failure = %s  %s", ex.toString(), getShortDescription());
            }
            // Take the breakpoint if evaluation fails.
            nodeEnter(node, vFrame);
        }

        @Override
        public String getLocationDescription() {
            return "Line: " + lineLocation.getShortDescription();
        }

        @Override
        public LineLocation getLineLocation() {
            return lineLocation;
        }

        private final class UnconditionalLineBreakInstrumentListener extends DefaultStandardInstrumentListener {

            @Override
            public void enter(Probe probe, Node node, VirtualFrame vFrame) {
                LineBreakpointImpl.this.nodeEnter(node, vFrame);
            }
        }
    }

}
