/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.debug;

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.instrument.*;
import com.oracle.truffle.api.nodes.instrument.InstrumentationProbeNode.ProbeChain;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.methods.*;

/**
 * Manager for Ruby AST execution.
 */
public final class RubyDebugManager implements DebugManager {

    // TODO (mlvdv) no REPL support yet for debugging "locals"; only lines

    private static enum BreakpointStatus {

        /**
         * Created for a source location but not yet attached for some legitimate reason: new and
         * not yet attached; new and the source file hasn't been loaded yet; old and the source file
         * is in the process of being reloaded.
         */
        PENDING("Pending"),

        /**
         * Has an active break probe in the AST.
         */
        ATTACHED("Active"),

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

    /**
     * Map: Source lines ==> source chains known to be at line locations in an AST.
     */
    private final Map<SourceLineLocation, ProbeChain> linesToProbeChains = new HashMap<>();

    private final Set<Source> loadedSources = new HashSet<>();

    private Source beingLoaded = null;

    /**
     * Map: Source lines ==> attached Breakpoints/procs to be activated before execution at line.
     */
    private final Map<SourceLineLocation, RubyLineBreakpoint> linesToBreakpoints = new TreeMap<>();

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

    public void notifyStartLoading(Source source) {

        beingLoaded = source;

        // Forget all the probe chains from previous loading
        final List<SourceLineLocation> locations = new ArrayList<>();
        for (SourceLineLocation lineLocation : linesToProbeChains.keySet()) {
            if (lineLocation.getSource().equals(beingLoaded)) {
                locations.add(lineLocation);
            }
        }
        for (SourceLineLocation lineLocation : locations) {
            linesToProbeChains.remove(lineLocation);
        }

        // Forget whatever we knew, and detach from old AST/ProbeChain if needed
        for (RubyLineBreakpoint breakpoint : linesToBreakpoints.values()) {
            if (breakpoint.getSourceLineLocation().getSource().equals(beingLoaded)) {
                breakpoint.setPending();
            }
        }
    }

    public void notifyFinishedLoading(Source source) {
        assert source == beingLoaded;

        // Any pending breakpoints are now erroneous, didn't find the line.
        for (RubyLineBreakpoint breakpoint : linesToBreakpoints.values()) {
            if (breakpoint.getSourceLineLocation().getSource().equals(beingLoaded)) {
                if (breakpoint.status == BreakpointStatus.PENDING) {
                    breakpoint.setError();
                }
            }
        }

        loadedSources.add(source);
        beingLoaded = null;

    }

    /**
     * Notifies the manager about creation of a newly created probe chain associated with a proxy
     * for an AST node at a specific location in the source text.
     */
    public void registerProbeChain(SourceSection sourceSection, ProbeChain probeChain) {

        assert sourceSection.getSource().equals(beingLoaded);

        // Remember this probe chain, indexed by line number
        final SourceLineLocation lineLocation = new SourceLineLocation(sourceSection.getSource(), sourceSection.getStartLine());
        linesToProbeChains.put(lineLocation, probeChain);

        final RubyLineBreakpoint breakpoint = linesToBreakpoints.get(lineLocation);
        if (breakpoint != null && breakpoint.location.equals(lineLocation)) {
            // We only register while we're loading;
            // While we're loading, there should only be pending breakpoints for this source
            assert breakpoint.status == BreakpointStatus.PENDING;

            // Found a line/probeChain where a pending breakpoint should be set
            breakpoint.attach(probeChain);
        }
    }

    @Override
    public RubyLineBreakpoint setBreakpoint(SourceLineLocation lineLocation) {

        RubyLineBreakpoint breakpoint = linesToBreakpoints.get(lineLocation);

        if (breakpoint != null) {
            switch (breakpoint.status) {
                case ATTACHED:
                    throw new RuntimeException("Breakpoint already set at line " + lineLocation);

                case PENDING:
                case ERROR:
                    throw new RuntimeException("Breakpoint already pending at line " + lineLocation);

                default:
                    assert false;
            }
        } else {
            breakpoint = new RubyLineBreakpoint(lineLocation, new RubyBreakBeforeProbe(context));
            linesToBreakpoints.put(lineLocation, breakpoint);

            final ProbeChain probeChain = linesToProbeChains.get(lineLocation);
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
    public LineBreakpoint[] getBreakpoints() {
        return linesToBreakpoints.values().toArray(new LineBreakpoint[0]);
    }

    @Override
    public void removeBreakpoint(SourceLineLocation lineLocation) {
        final RubyLineBreakpoint breakpoint = linesToBreakpoints.get(lineLocation);
        if (breakpoint == null) {
            throw new RuntimeException("No break/proc located at line " + lineLocation);
        }
        linesToBreakpoints.remove(lineLocation);
        breakpoint.retire();
    }

    /**
     * Sets a Ruby proc of no arguments to be run before a specified line is executed.
     */
    public void setLineProc(SourceLineLocation lineLocation, RubyProc proc) {

        RubyLineBreakpoint breakpoint = linesToBreakpoints.get(lineLocation);

        if (breakpoint != null) {
            switch (breakpoint.status) {
                case ATTACHED:
                    throw new RuntimeException("Breakpoint already set at line " + lineLocation);

                case PENDING:
                case ERROR:
                    throw new RuntimeException("Breakpoint already pending at line " + lineLocation);

                default:
                    assert false;
            }
        } else {
            breakpoint = new RubyLineBreakpoint(lineLocation, new RubyProcBeforeProbe(context, proc));
            linesToBreakpoints.put(lineLocation, breakpoint);

            final ProbeChain probeChain = linesToProbeChains.get(lineLocation);
            if (probeChain != null) {
                breakpoint.attach(probeChain);
            }
        }
    }

    /**
     * Registers the chain of probes associated with a method local variable in the AST.
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
        probe = new RubyBreakAfterProbe(context);
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
        probe = new RubyProcAfterProbe(context, proc);
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

    /**
     * Receives notification of a suspended execution context; execution resumes when this method
     * returns.
     * 
     * @param astNode a guest language AST node that represents the current execution site, assumed
     *            not to be any kind of {@link InstrumentationNode},
     * @param frame execution frame at the site where execution suspended
     */
    public void haltedAt(Node astNode, MaterializedFrame frame) {
        context.haltedAt(astNode, frame);
    }

    private static final class RubyLineBreakpoint implements DebugManager.LineBreakpoint, Comparable {

        private final SourceLineLocation location;

        private RubyProbe probe;  // non-null until RETIRED, but may get replaced.
        private ProbeChain probeChain = null;  // only non-null when ATTACHED
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
            return status == null ? "<none>" : status.name;
        }

        private void attach(ProbeChain chain) {
            assert status == BreakpointStatus.PENDING;

            probeChain = chain;
            probeChain.appendProbe(probe);

            status = BreakpointStatus.ATTACHED;
        }

        private void setPending() {
            switch (status) {
                case ATTACHED:
                    detach();
                    // TODO (mlvdv) replace the probe
                    status = BreakpointStatus.PENDING;
                    break;
                case ERROR:
                    status = BreakpointStatus.PENDING;
                    break;
                case PENDING:
                    break;
                case RETIRED:
                    assert false;
                    break;
                default:
                    assert false;
            }
        }

        public void setError() {
            assert status == BreakpointStatus.PENDING;

            status = BreakpointStatus.ERROR;
        }

        private void detach() {
            assert status == BreakpointStatus.ATTACHED;

            probeChain.removeProbe(probe);
            probeChain = null;

            status = BreakpointStatus.PENDING;
        }

        private void retire() {

            if (probeChain != null) {
                probeChain.removeProbe(probe);
            }
            probe = null;
            probeChain = null;

            status = BreakpointStatus.RETIRED;
        }
    }

}
