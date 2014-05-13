/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.impl;

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.instrument.impl.*;
import com.oracle.truffle.api.instrument.impl.InstrumentationNode.ProbeCallback;
import com.oracle.truffle.api.instrument.impl.InstrumentationNode.ProbeImpl;
import com.oracle.truffle.api.source.*;

public abstract class AbstractExecutionContext implements ExecutionContext {

    // TODO (mlvdv) use weak references.
    /**
     * Map: SourceSection ==> probe associated with that source section in an AST.
     */
    private final Map<SourceSection, ProbeImpl> srcToProbe = new HashMap<>();

    // TODO (mlvdv) use weak references.
    /**
     * Map: Source line ==> probes associated with source sections starting on the line.
     */
    private final Map<SourceLineLocation, Collection<Probe>> lineToProbes = new HashMap<>();

    private final SourceManager sourceManager = new SourceManager();
    private final List<SourceListener> sourceListeners = new ArrayList<>();
    private final List<ProbeListener> probeListeners = new ArrayList<>();

    private final SourceCallback sourceCallback = new SourceCallback() {

        public void startLoading(Source source) {
            for (SourceListener listener : sourceListeners) {
                listener.loadStarting(source);
            }
        }

        public void endLoading(Source source) {
            for (SourceListener listener : sourceListeners) {
                listener.loadEnding(source);
            }
        }
    };

    private final ProbeCallback probeCallback = new ProbeCallback() {
        /**
         * Receives (from the {@link Probe} implementation) and distributes notification that a
         * {@link Probe} has acquired a new {@linkplain PhylumTag tag}.
         */
        public void newTagAdded(ProbeImpl probe, PhylumTag tag) {
            for (ProbeListener listener : probeListeners) {
                listener.probeTaggedAs(probe, tag);
            }
            if (trap != null && tag == trap.getTag()) {
                probe.setTrap(trap);
            }
        }
    };

    private Visualizer visualizer = new DefaultVisualizer();

    /**
     * When non-null, "enter" events with matching tags will trigger a callback.
     */
    private PhylumTrap trap = null;

    protected AbstractExecutionContext() {
    }

    public void initialize() {
        setSourceCallback(sourceCallback);
    }

    public final SourceManager getSourceManager() {
        return sourceManager;
    }

    public void addSourceListener(SourceListener listener) {
        assert listener != null;
        sourceListeners.add(listener);
    }

    public void addProbeListener(ProbeListener listener) {
        assert listener != null;
        probeListeners.add(listener);
    }

    public Probe getProbe(SourceSection sourceSection) {
        assert sourceSection != null;

        ProbeImpl probe = srcToProbe.get(sourceSection);

        if (probe != null) {
            return probe;
        }
        probe = InstrumentationNode.createProbe(sourceSection, probeCallback);

        // Register new probe by unique SourceSection
        srcToProbe.put(sourceSection, probe);

        // Register new probe by source line, there may be more than one
        // Create line location for map key
        final SourceLineLocation lineLocation = new SourceLineLocation(sourceSection.getSource(), sourceSection.getStartLine());

        Collection<Probe> probes = lineToProbes.get(lineLocation);
        if (probes == null) {
            probes = new ArrayList<>(2);
            lineToProbes.put(lineLocation, probes);
        }
        probes.add(probe);

        for (ProbeListener listener : probeListeners) {
            listener.newProbeInserted(sourceSection, probe);
        }

        return probe;
    }

    /**
     * Returns all existing probes with specific tag, or all probes if {@code tag = null}; empty
     * collection if no probes found.
     */
    public Collection<Probe> findProbesTaggedAs(PhylumTag tag) {
        final List<Probe> probes = new ArrayList<>();
        for (Probe probe : srcToProbe.values()) {
            if (tag == null || probe.isTaggedAs(tag)) {
                probes.add(probe);
            }
        }
        return probes;
    }

    public Collection<Probe> findProbesByLine(SourceLineLocation lineLocation) {
        final Collection<Probe> probes = lineToProbes.get(lineLocation);
        if (probes == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(probes);
    }

    // TODO (mlvdv) consider allowing multiple traps (without inhibiting Truffle inlining)
    public void setTrap(PhylumTrap trap) {
        assert trap != null;
        if (this.trap != null) {
            throw new IllegalStateException("trap already set");
        }
        this.trap = trap;

        for (ProbeImpl probe : srcToProbe.values()) {
            if (probe.isTaggedAs(trap.getTag())) {
                probe.setTrap(trap);
            }
        }
    }

    public void clearTrap() {
        if (this.trap == null) {
            throw new IllegalStateException("no trap set");
        }
        for (ProbeImpl probe : srcToProbe.values()) {
            if (probe.isTaggedAs(trap.getTag())) {
                probe.setTrap(null);
            }
        }
        trap = null;
    }

    public Visualizer getVisualizer() {
        return visualizer;
    }

    /**
     * Assign guest language-specific visualization support for tools. This must be assigned outside
     * the implementation context to avoid build circularities.
     */
    public void setVisualizer(Visualizer visualizer) {
        this.visualizer = visualizer;
    }

    /**
     * Assigns a guest language-specific manager for using {@link ASTNodeProber}s added by tools to
     * instrument ASTs with {@link Probe}s at specified nodes. This must be assigned outside the
     * implementation context to avoid build circularities. It must also be set before any
     * instrumentation probe implementations are assigned.
     */
    public abstract void setASTProber(ASTProber astProber);

    /**
     * Establishes source event reporting
     */
    protected abstract void setSourceCallback(SourceCallback sourceCallback);

}
