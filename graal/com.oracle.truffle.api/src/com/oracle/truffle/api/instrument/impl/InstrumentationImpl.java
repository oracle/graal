/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrument.impl;

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.source.*;

public final class InstrumentationImpl implements Instrumentation {

    private final ExecutionContext context;

    // TODO (mlvdv) maps should really use weak references.

    /**
     * Map: SourceSection ==> probe associated with that source section in an AST.
     */
    private final Map<SourceSection, Probe> srcToProbe = new HashMap<>();

    /**
     * Map: Source line ==> probes associated with source sections starting on the line.
     */
    private final Map<SourceLineLocation, Collection<Probe>> lineToProbes = new HashMap<>();

    private final List<ProbeListener> probeListeners = new ArrayList<>();

    public InstrumentationImpl(ExecutionContext context) {
        this.context = context;
    }

    public void addNodeProber(ASTNodeProber nodeProber) {
        context.addNodeProber(nodeProber);
    }

    public void addProbeListener(ProbeListener listener) {
        assert listener != null;
        probeListeners.add(listener);
    }

    /**
     * Return the (possibly newly created) {@link Probe} uniquely associated with a particular
     * source code location. A newly created probe carries no tags.
     *
     * @param eventListener an optional listener for certain instrumentation-related events
     * @return a probe uniquely associated with an extent of guest language source code.
     */
    public Probe getProbe(SourceSection sourceSection, InstrumentEventListener eventListener) {
        assert sourceSection != null;

        Probe probe = srcToProbe.get(sourceSection);

        if (probe != null) {
            return probe;
        }
        probe = InstrumentationNode.createProbe(this, sourceSection, eventListener);

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
        if (tag == null) {
            return new ArrayList<>(srcToProbe.values());
        }
        final List<Probe> probes = new ArrayList<>();
        for (Probe probe : srcToProbe.values()) {
            if (probe.isTaggedAs(tag)) {
                probes.add(probe);
            }
        }
        return probes;
    }

    /**
     * Returns all existing probes with first character on a specified line; empty collection if no
     * probes found.
     */
    public Collection<Probe> findProbesByLine(SourceLineLocation lineLocation) {
        final Collection<Probe> probes = lineToProbes.get(lineLocation);
        if (probes == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(probes);
    }

    /**
     * Receives (from the {@link Probe} implementation) and distributes notification that a
     * {@link Probe} has acquired a new {@linkplain PhylumTag tag}.
     */
    void newTagAdded(Probe probe, PhylumTag tag) {
        for (ProbeListener listener : probeListeners) {
            listener.probeTaggedAs(probe, tag);
        }
    }

}
