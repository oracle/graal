/*
 * Copyright (c) 2013, 2014 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.debug;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.instrument.InstrumentationProbeNode.ProbeChain;
import com.oracle.truffle.api.nodes.instrument.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.methods.*;

/**
 * Manager for Ruby AST execution under debugging control.
 */
public final class RubyDebugManager implements DebugManager {

    // TODO (mlvdv) no REPL support yet for debugging "locals" (assignment to local variables); only
    // line-based step/next/return

    private static final boolean TRACE = false;
    private static final PrintStream OUT = System.out;

    private static enum ExecutionMode {

        /**
         * Context: ordinary debugging execution, e.g. in response to a "Continue" request or a
         * "Load-Run" request.
         * <ul>
         * <li>User breakpoints are enabled.</li>
         * <li>Continue until either:
         * <ol>
         * <li>execution arrives at a node with attached user breakpoint, <strong>or:</strong></li>
         * <li>execution completes.</li>
         * </ol>
         * </ul>
         */
        CONTINUE,

        /**
         * Context: per-statement stepping execution, e.g. in response to a "Step" request.
         * <ul>
         * <li>User breakpoints are disabled.</li>
         * <li>Continue until either:
         * <ol>
         * <li>execution arrives at a "Statement" node, <strong>or:</strong></li>
         * <li>execution completes.</li>
         * </ol>
         * </ul>
         */
        STEP,

        /**
         * Context: per-statement stepping in response to a "Next" request and when not nested in
         * any function/method call.
         * <ul>
         * <li>User breakpoints are disabled.</li>
         * <li>Continue until either:
         * <ol>
         * <li>execution arrives at a "Statement" node <strong>or:</strong></li>
         * <li>the program completes <strong>or:</strong></li>
         * <li>execution arrives at a function/method entry, in which case the mode changes to
         * {@link #NEXT_NESTED} and execution continues.</li>
         * </ol>
         * </ul>
         */
        NEXT,

        /**
         * Context: ordinary debugging execution in response to a "Next" requested and when nested
         * at least one deep in function/method calls.
         * <ul>
         * <li>User breakpoints are enabled.</li>
         * <li>Execute until either:
         * <ol>
         * <li>execution arrives at a node with attached user breakpoint, <strong>or:</strong></li>
         * <li>execution completes, <strong>or:</strong></li>
         * <li>execution returns from all nested function/method calls, in which case the mode
         * changes to {@link #NEXT} and execution continues.</li>
         * </ol>
         * </ul>
         */
        NEXT_NESTED;
    }

    private static enum BreakpointStatus {

        /**
         * Created for a source location but not yet attached for some legitimate reason: perhaps
         * newly created and not yet attached; perhaps newly created and the source file hasn't been
         * loaded yet; perhaps old and the source file is in the process of being reloaded.
         */
        PENDING("Pending"),

        /**
         * Has a {@link RubyProbe}, which is attached to a {@linkplain ProbeChain known location} in
         * the AST.
         */
        ACTIVE("Active"),

        /**
         * Has a {@link RubyProbe}, which is associated with a {@linkplain ProbeChain known
         * location} in the AST, but which has been temporarily removed.
         */
        DISABLED("Disabled"),

        /**
         * Should be attached, but the line location cannot be found in the source.
         */
        ERROR("Error: line not found"),

        /**
         * Abandoned, not attached.
         */
        RETIRED("Retired");

        private final String name;

        BreakpointStatus(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private final Set<Source> loadedSources = new HashSet<>();

    private Source beingLoaded = null;

    /**
     * The current mode of execution.
     */
    private ExecutionMode executionMode = ExecutionMode.CONTINUE;

    /**
     * When running in "step" mode, this is the number of steps that haven't yet completed.
     */
    private int unfinishedStepCount = 0;

    /**
     * When running in "next" mode, this is the number of steps that haven't yet completed.
     */
    private int unfinishedNextCount = 0;
    /**
     * When running in "next" mode, this is non-null when running a function/method that must be
     * continued across.
     */
    private Node nextNestedInCallNode = null;

    /**
     * Map: SourceSection ==> probe chain associated with that source section in an AST.
     */
    private final Map<SourceSection, ProbeChain> srcToProbeChain = new HashMap<>();

    /**
     * Map: Source lines ==> probe chains associated with source sections starting on the line.
     */
    private final Map<SourceLineLocation, Set<ProbeChain>> lineToProbeChains = new HashMap<>();

    /**
     * Map: Source lines ==> attached Breakpoints/procs to be activated before execution at line.
     * There should be no more than one line breakpoint associated with a line.
     */
    private final Map<SourceLineLocation, RubyLineBreakpoint> lineToBreakpoint = new TreeMap<>();

    /**
     * Map: Method locals in AST ==> Method local assignments where breakpoints can be attached.
     */
    private final Map<MethodLocal, ProbeChain> localsToProbeChains = new HashMap<>();

    /**
     * Map: Method locals ==> Breakpoints & procs to be activated after assignment to a local.
     */
    private final Map<MethodLocal, RubyProbe> localsToAttachedBreakpoints = new HashMap<>();

    private final RubyContext context;

    public RubyDebugManager(RubyContext context) {
        this.context = context;
    }

    /**
     * Gets the {@linkplain ProbeChain probe} associated with a particular {@link SourceSection
     * source location}, creating a new one if needed. There should only be one probe associated
     * with each {@linkplain SourceSection source location}.
     */
    public ProbeChain getProbeChain(SourceSection sourceSection) {
        assert sourceSection != null;
        assert sourceSection.getSource().equals(beingLoaded);

        ProbeChain probeChain = srcToProbeChain.get(sourceSection);

        if (probeChain != null) {
            return probeChain;
        }
        probeChain = new ProbeChain(context, sourceSection, null);

        // Register new ProbeChain by unique SourceSection
        srcToProbeChain.put(sourceSection, probeChain);

        // Register new ProbeChain by source line, there may be more than one
        // Create line location for map key
        final SourceLineLocation lineLocation = new SourceLineLocation(sourceSection.getSource(), sourceSection.getStartLine());

        Set<ProbeChain> probeChains = lineToProbeChains.get(lineLocation);
        if (probeChains == null) {
            probeChains = new HashSet<>();
            lineToProbeChains.put(lineLocation, probeChains);
        }
        probeChains.add(probeChain);

        return probeChain;
    }

    public void notifyStartLoading(Source source) {

        beingLoaded = source;

        /**
         * We'd like to know when we're reloading a file if the old AST is completely dead, so that
         * we can correctly identify the state of breakpoints related to it, but that doesn't seem
         * possible.
         * 
         * Before we start, find any breakpoints that never got attached, which get reported as
         * errors. Revert them to "pending", in case their lines are found this time around.
         */
        for (RubyLineBreakpoint breakpoint : lineToBreakpoint.values()) {
            if (breakpoint.getSourceLineLocation().getSource().equals(beingLoaded)) {
                if (breakpoint.status == BreakpointStatus.ERROR) {
                    // It was an error, which means we have not yet found that line for this Source.
                    // It might show up while loading this time, so make it pending.
                    breakpoint.setPending();
                }
            }
        }
    }

    public void notifyFinishedLoading(Source source) {
        assert source == beingLoaded;

        // Update any pending breakpoints associated with this source

        for (RubyLineBreakpoint breakpoint : lineToBreakpoint.values()) {
            if (breakpoint.getSourceLineLocation().getSource().equals(beingLoaded)) {
                if (breakpoint.status == BreakpointStatus.PENDING) {
                    final ProbeChain probeChain = findProbeChain(breakpoint.location);
                    if (probeChain == null) {
                        breakpoint.setError();
                    } else {
                        breakpoint.attach(probeChain);
                    }
                }
            }
        }
        loadedSources.add(source);
        beingLoaded = null;
    }

    /**
     * Returns a {@link ProbeChain} associated with source that starts on a specified line; if there
     * are more than one, return the one with the first character location.
     */
    private ProbeChain findProbeChain(SourceLineLocation lineLocation) {
        ProbeChain probeChain = null;
        final Set<ProbeChain> probeChains = lineToProbeChains.get(lineLocation);
        if (probeChains != null) {
            assert probeChains.size() > 0;
            for (ProbeChain chain : probeChains) {
                if (probeChain == null) {
                    probeChain = chain;
                } else if (chain.getProbedSourceSection().getCharIndex() < probeChain.getProbedSourceSection().getCharIndex()) {
                    probeChain = chain;
                }
            }
        }
        return probeChain;
    }

    /**
     * Remove a probe from a line location and retire it permanently.
     */
    public void retireLineProbe(SourceLineLocation location, RubyLineProbe probe) {
        final RubyLineBreakpoint breakpoint = lineToBreakpoint.get(location);
        lineToBreakpoint.remove(location);
        breakpoint.retire(probe);
    }

    @Override
    public LineBreakpoint[] getBreakpoints() {
        return lineToBreakpoint.values().toArray(new LineBreakpoint[0]);
    }

    @Override
    public RubyLineBreakpoint setBreakpoint(SourceLineLocation lineLocation) {

        RubyLineBreakpoint breakpoint = lineToBreakpoint.get(lineLocation);

        if (breakpoint != null) {
            switch (breakpoint.status) {
                case ACTIVE:
                    throw new RuntimeException("Breakpoint already set at line " + lineLocation);

                case PENDING:
                case ERROR:
                    throw new RuntimeException("Breakpoint already pending at line " + lineLocation);

                default:
                    assert false;
            }
        } else {
            breakpoint = new RubyLineBreakpoint(lineLocation, new RubyBreakBeforeLineProbe(context, lineLocation, false));
            lineToBreakpoint.put(lineLocation, breakpoint);

            final ProbeChain probeChain = findProbeChain(lineLocation);
            if (probeChain != null) {
                breakpoint.attach(probeChain);
            }
        }

        return breakpoint;
    }

    @Override
    public RubyLineBreakpoint setConditionalBreakpoint(SourceLineLocation lineLocation, String condition) {
        throw new UnsupportedOperationException("conditional breakpoints not yet supported");
    }

    @Override
    public LineBreakpoint setOneShotBreakpoint(SourceLineLocation lineLocation) {
        RubyLineBreakpoint breakpoint = lineToBreakpoint.get(lineLocation);

        if (breakpoint != null) {
            switch (breakpoint.status) {
                case ACTIVE:
                    throw new RuntimeException("Breakpoint already set at line " + lineLocation);

                case PENDING:
                case ERROR:
                    throw new RuntimeException("Breakpoint already pending at line " + lineLocation);

                default:
                    assert false;
            }
        } else {
            breakpoint = new RubyLineBreakpoint(lineLocation, new RubyBreakBeforeLineProbe(context, lineLocation, true));
            lineToBreakpoint.put(lineLocation, breakpoint);

            final ProbeChain probeChain = findProbeChain(lineLocation);
            if (probeChain != null) {
                breakpoint.attach(probeChain);
            }
        }

        return breakpoint;
    }

    public boolean hasBreakpoint(SourceLineLocation lineLocation) {
        return lineToBreakpoint.get(lineLocation) != null;
    }

    @Override
    public void removeBreakpoint(SourceLineLocation lineLocation) {
        final RubyLineBreakpoint breakpoint = lineToBreakpoint.get(lineLocation);
        if (breakpoint == null) {
            throw new RuntimeException("No break/proc located at line " + lineLocation);
        }
        lineToBreakpoint.remove(lineLocation);
        breakpoint.retire();
    }

    private void removeOneShotBreakpoints() {
        for (Entry<SourceLineLocation, RubyLineBreakpoint> entry : lineToBreakpoint.entrySet()) {
            final RubyLineBreakpoint breakpoint = entry.getValue();
            if (breakpoint.probe.isOneShot()) {
                lineToBreakpoint.remove(entry.getKey());
                breakpoint.retire();
            }
        }
    }

    /**
     * Prepare to execute a "Continue":
     * <ul>
     * <li>Execution will continue until either:
     * <ol>
     * <li>execution arrives at a node to which a breakpoint is attached, <strong>or:</strong></li>
     * <li>execution completes.</li>
     * </ol>
     * </ul>
     */
    public void setContinue() {
        // Nothing to do here; "Continue" is the default, which should be restored after each halt.
    }

    /**
     * Prepare to execute a "Step":
     * <ul>
     * <li>User breakpoints are disabled.</li>
     * <li>Execution will continue until either:
     * <ol>
     * <li>execution arrives at a "Statement" node, <strong>or:</strong></li>
     * <li>execution completes.</li>
     * </ol>
     * This status persists only through one execution, and reverts to
     * {@link ExecutionMode#CONTINUE}.
     * </ul>
     */
    public void setStep(int stepCount) {
        assert executionMode == ExecutionMode.CONTINUE;
        disableLineBreakpoints();
        setStepping(true);
        unfinishedStepCount = stepCount;
        setMode(ExecutionMode.STEP);
    }

    /**
     * Prepare to execute a "Next":
     * <ul>
     * <li>Execution will continue until either:
     * <ol>
     * <li>execution arrives at a "Statement" node when not nested in one or more function/method
     * calls, <strong>or:</strong></li>
     * <li>execution arrives at a node to which a breakpoint is attached and when nested in one or
     * more function/method calls, <strong>or:</strong></li>
     * <li>execution completes.</li>
     * </ol>
     * This status persists only through one execution, and reverts to
     * {@link ExecutionMode#CONTINUE}.
     * </ul>
     */
    public void setNext(int nextCount) {
        assert executionMode == ExecutionMode.CONTINUE;
        disableLineBreakpoints();
        setStepping(true);
        unfinishedNextCount = nextCount;
        setMode(ExecutionMode.NEXT);
    }

    private void disableLineBreakpoints() {
        for (RubyLineBreakpoint breakpoint : lineToBreakpoint.values()) {
            if (breakpoint.status == BreakpointStatus.ACTIVE) {
                breakpoint.disable();
            }
        }
    }

    private void enableLineBreakpoints() {
        for (RubyLineBreakpoint breakpoint : lineToBreakpoint.values()) {
            if (breakpoint.status == BreakpointStatus.DISABLED) {
                breakpoint.enable();
            }
        }
    }

    private void setStepping(boolean isStepping) {
        // Set the "stepping" flag on every statement probe.
        for (ProbeChain probeChain : srcToProbeChain.values()) {
            if (probeChain.isMarkedAs(NodePhylum.STATEMENT)) {
                probeChain.setStepping(isStepping);
            }
        }
    }

    private void setMode(ExecutionMode mode) {
        if (TRACE) {
            OUT.println("DebugManager: " + executionMode.toString() + "-->" + mode.toString());
        }
        executionMode = mode;
    }

    /**
     * Sets a Ruby proc of no arguments to be run before a specified line is executed.
     */
    public void setLineProc(SourceLineLocation lineLocation, RubyProc proc) {

        RubyLineBreakpoint breakpoint = lineToBreakpoint.get(lineLocation);

        if (breakpoint != null) {
            switch (breakpoint.status) {
                case ACTIVE:
                    throw new RuntimeException("Breakpoint already set at line " + lineLocation);

                case PENDING:
                case ERROR:
                    throw new RuntimeException("Breakpoint already pending at line " + lineLocation);

                default:
                    assert false;
            }
        } else {
            breakpoint = new RubyLineBreakpoint(lineLocation, new RubyProcBeforeLineProbe(context, lineLocation, proc));
            lineToBreakpoint.put(lineLocation, breakpoint);

            final ProbeChain probeChain = findProbeChain(lineLocation);
            if (probeChain != null) {
                breakpoint.attach(probeChain);
            }
        }
    }

    // TODO (mlvdv) rework locals (watchpoints) to work like breaks; I doubt it is even correct now
    /**
     * Registers the chain of probes associated with a method local variable assignment in the AST.
     */
    public void registerLocalDebugProxy(UniqueMethodIdentifier methodIdentifier, String localName, ProbeChain probeChain) {
        final MethodLocal methodLocal = new MethodLocal(methodIdentifier, localName);
        localsToProbeChains.put(methodLocal, probeChain);
    }

    /**
     * Sets a breakpoint after assignment to a method local variable in the AST.
     */
    public void setLocalBreak(UniqueMethodIdentifier methodIdentifier, String localName) {
        final MethodLocal methodLocal = new MethodLocal(methodIdentifier, localName);
        ProbeChain probeChain = localsToProbeChains.get(methodLocal);
        if (probeChain == null) {
            throw new RuntimeException("Can't find method local " + methodLocal);
        }
        RubyProbe probe = localsToAttachedBreakpoints.get(methodLocal);
        if (probe != null) {
            throw new RuntimeException("Breakpoint already set on method local " + methodLocal);
        }
        probe = new RubyBreakAfterLocalProbe(context, methodLocal);
        localsToAttachedBreakpoints.put(methodLocal, probe);
        probeChain.appendProbe(probe);
    }

    /**
     * Sets a Ruby proc of one argument to be run after a method local assignment, passed the new
     * value.
     */
    public void setLocalProc(UniqueMethodIdentifier methodIdentifier, String localName, RubyProc proc) {
        final MethodLocal methodLocal = new MethodLocal(methodIdentifier, localName);
        ProbeChain probeChain = localsToProbeChains.get(methodLocal);
        if (probeChain == null) {
            throw new RuntimeException("Can't find method local " + methodLocal);
        }
        RubyProbe probe = localsToAttachedBreakpoints.get(methodLocal);
        if (probe != null) {
            throw new RuntimeException("Assignment proc already set on method local " + methodLocal);
        }
        probe = new RubyProcAfterLocalProbe(context, methodLocal, proc);
        localsToAttachedBreakpoints.put(methodLocal, probe);
        probeChain.appendProbe(probe);
    }

    /**
     * Removes a break or proc on assignment to a method local variable in the AST.
     */
    public void removeLocalProbe(UniqueMethodIdentifier methodIdentifier, String localName) {
        final MethodLocal methodLocal = new MethodLocal(methodIdentifier, localName);
        RubyProbe probe = localsToAttachedBreakpoints.get(methodLocal);
        if (probe == null) {
            throw new RuntimeException("No breakpoint set on method local " + methodLocal);
        }
        localsToProbeChains.get(methodLocal).removeProbe(probe);
        localsToAttachedBreakpoints.remove(methodLocal);
    }

    public void haltedAt(Node astNode, MaterializedFrame frame) {
        switch (executionMode) {
            case CONTINUE:
            case NEXT_NESTED:
                // User breakpoints should already be enabled
                // Stepping should be false
                nextNestedInCallNode = null;
                break;
            case STEP:
                unfinishedStepCount--;
                if (unfinishedStepCount > 0) {
                    return;
                }
                // Revert to default mode.
                enableLineBreakpoints();
                setStepping(false);
                break;
            case NEXT:
                unfinishedNextCount--;
                if (unfinishedNextCount > 0) {
                    return;
                }
                // Revert to default mode.
                enableLineBreakpoints();
                setStepping(false);
                break;
            default:
                assert false;  // Should not happen
                break;

        }
        // Clean up, just in cased the one-shot breakpoints got confused
        removeOneShotBreakpoints();

        setMode(ExecutionMode.CONTINUE);

        // Return control to the debug client
        context.haltedAt(astNode, frame);

    }

    private RubyProbe createReplacement(RubyProbe probe) {
        // Should be a specialized replacement for any kind of probe created.
        // Ugly, but there's no other way to reset the parent pointer and reuse a probe node.
        if (probe instanceof RubyBreakBeforeLineProbe) {
            final RubyBreakBeforeLineProbe oldProbe = (RubyBreakBeforeLineProbe) probe;
            return new RubyBreakBeforeLineProbe(context, oldProbe.getLineLocation(), oldProbe.isOneShot());
        }
        if (probe instanceof RubyProcBeforeLineProbe) {
            final RubyProcBeforeLineProbe oldProbe = (RubyProcBeforeLineProbe) probe;
            return new RubyProcBeforeLineProbe(context, oldProbe.getLineLocation(), oldProbe.getProc());
        }
        assert false;
        return null;
    }

    /**
     * A breakpoint of the sort that would be created by a client, with a life-cycle represented by
     * {@link BreakpointStatus}.
     */
    private final class RubyLineBreakpoint implements DebugManager.LineBreakpoint, Comparable {

        private final SourceLineLocation location;

        private RubyProbe probe;  // non-null until RETIRED, but may get replaced.
        private ProbeChain probeChain = null;
        private BreakpointStatus status = BreakpointStatus.PENDING;

        public RubyLineBreakpoint(SourceLineLocation location, RubyProbe probe) {
            this.location = location;
            this.probe = probe;
        }

        public SourceLineLocation getSourceLineLocation() {
            return location;
        }

        // ensure sorted by location
        public int compareTo(Object o) {
            final RubyLineBreakpoint other = (RubyLineBreakpoint) o;
            return location.compareTo(other.location);
        }

        @Override
        public String getDebugStatus() {
            String result = status == null ? "<none>" : status.name;
            if (probe.isOneShot()) {
                result = result + ", " + "One-Shot";
            }
            return result;
        }

        private void attach(ProbeChain chain) {
            assert status == BreakpointStatus.PENDING;

            probeChain = chain;
            probeChain.appendProbe(probe);

            status = BreakpointStatus.ACTIVE;
        }

        private void disable() {
            assert status == BreakpointStatus.ACTIVE;

            probeChain.removeProbe(probe);
            status = BreakpointStatus.DISABLED;
        }

        private void enable() {
            assert status == BreakpointStatus.DISABLED;

            // Can't re-attach to probe chain, because can't re-assign parent.
            probe = createReplacement(probe);
            probeChain.appendProbe(probe);
            status = BreakpointStatus.ACTIVE;
        }

        private void setPending() {
            assert status == BreakpointStatus.ERROR;

            status = BreakpointStatus.PENDING;
        }

        public void setError() {
            assert status == BreakpointStatus.PENDING;

            status = BreakpointStatus.ERROR;
        }

        private void retire() {

            if (status == BreakpointStatus.ACTIVE) {
                probeChain.removeProbe(probe);
            }
            probe = null;
            probeChain = null;

            status = BreakpointStatus.RETIRED;
        }

        private void retire(RubyProbe retiredProbe) {

            assert this.probe == retiredProbe;
            retire();
        }
    }

    private static final class CallRecord {

        final SourceSection section;
        @SuppressWarnings("unused") final String name;
        final CallRecord predecessor;

        public CallRecord(SourceSection section, String name, CallRecord predecessor) {
            this.section = section;
            this.name = name;
            this.predecessor = predecessor;
        }
    }

    private CallRecord callStack = null;

    public void notifyCallEntry(Node astNode, String name) {
        if (TRACE) {
            OUT.println("DebugManager: ENTER \"" + name + "\" " + nodeToString(astNode));
        }
        if (executionMode == ExecutionMode.NEXT && nextNestedInCallNode == null) {
            // In "Next" mode, where we have been "stepping", but are about to enter a call.
            // Switch modes to be like "Continue" until/if return from this call
            nextNestedInCallNode = astNode;
            enableLineBreakpoints();
            setStepping(false);
            setMode(ExecutionMode.NEXT_NESTED);
        }

        callStack = new CallRecord(astNode.getSourceSection(), name, callStack);
    }

    public void notifyCallExit(Node astNode, String name) {
        if (TRACE) {
            OUT.println("DebugManager: EXIT \"" + name + "\" " + nodeToString(astNode));
        }

        if (executionMode == ExecutionMode.NEXT_NESTED) {
            assert nextNestedInCallNode != null;
            if (nextNestedInCallNode == astNode) {
                // In "Next" mode while nested in a function/method call, but about to return.
                // Switch modes to be like "Step" until/if enter another function/method call.
                nextNestedInCallNode = null;
                disableLineBreakpoints();
                setStepping(true);
                setMode(ExecutionMode.NEXT);
            }
        }

        final SourceSection section = astNode.getSourceSection();
        if (section instanceof NullSourceSection) {
            if (TRACE) {
                OUT.println("Ignoring call exit \"" + name + "\" " + nodeToString(astNode));
            }
        }
        callStack = callStack.predecessor;
    }

    /**
     * Sets a one-shot breakpoint to halt just after the completion of the call site at the top of
     * the current call stack.
     */
    public boolean setReturnBreakpoint() {
        if (callStack == null) {
            return false;
        }
        final SourceLineLocation lineLocation = new SourceLineLocation(callStack.section);
        RubyLineBreakpoint breakpoint = lineToBreakpoint.get(lineLocation);
        if (breakpoint != null) {
            return true;
        }
        final ProbeChain probeChain = findProbeChain(lineLocation);
        if (probeChain != null) {
            breakpoint = new RubyLineBreakpoint(lineLocation, new RubyBreakAfterLineProbe(context, lineLocation, true));
            lineToBreakpoint.put(lineLocation, breakpoint);
            breakpoint.attach(probeChain);
            return true;
        }
        return false;
    }

    /**
     * Notifies that a new execution is about to start, i.e. running a program or an eval.
     */
    @SuppressWarnings("static-method")
    public void startExecution(String name) {
        if (TRACE) {
            OUT.println("RubyDebugManager: START " + name);
        }
        // TODO (mlvdv) push the current call stack onto a stack; start new empty call stack
    }

    /**
     * Notifies that the current execution has ended.
     */
    public void endExecution(String name) {
        if (TRACE) {
            OUT.println("RubyDebugManager: END " + name);
        }

        // TODO (mlvdv) pop the current call stack, restore previous

        switch (executionMode) {
            case CONTINUE:
            case NEXT_NESTED:
                // User breakpoints should already be enabled
                // Stepping should be false
                nextNestedInCallNode = null;
                break;
            case STEP:
                // Revert to default mode.
                enableLineBreakpoints();
                setStepping(false);
                unfinishedStepCount = 0;
                break;
            case NEXT:
                // Revert to default mode.
                enableLineBreakpoints();
                setStepping(false);
                unfinishedNextCount = 0;
                break;
            default:
                assert false;  // Should not happen
                break;
        }
        // Clean up, just in cased the one-shot breakpoints got confused
        removeOneShotBreakpoints();

        setMode(ExecutionMode.CONTINUE);
    }

    @SuppressWarnings("static-method")
    private String nodeToString(Node astNode) {
        final SourceSection sourceSection = astNode.getSourceSection();
        if (sourceSection != null) {
            return Integer.toString(sourceSection.getStartLine()) + ":" + astNode;
        }
        return astNode.toString();
    }

}
