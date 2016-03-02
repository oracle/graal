/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.instrument.Instrumenter;
import com.oracle.truffle.api.instrument.Probe;
import com.oracle.truffle.api.instrument.ProbeListener;
import com.oracle.truffle.api.instrument.impl.DefaultProbeListener;
import com.oracle.truffle.api.source.LineLocation;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * An {@linkplain Instrumenter.Tool Instrumentation Tool} that builds a map of every {@link Probe}
 * attached to some AST, indexed by {@link Source} and line number.
 */
@Deprecated
public final class LineToProbesMap extends Instrumenter.Tool {

    private static final boolean TRACE = false;
    private static final PrintStream OUT = System.out;

    private static void trace(String msg) {
        OUT.println("LineToProbesMap: " + msg);
    }

    /**
     * Map: Source line ==> probes associated with source sections starting on the line.
     */
    private final Map<LineLocation, Collection<Probe>> lineToProbesMap = new HashMap<>();

    private final ProbeListener probeListener;

    /**
     * Create a map of {@link Probe}s that collects information on all probes added to subsequently
     * created ASTs (once installed).
     */
    public LineToProbesMap() {
        this.probeListener = new LineToProbesListener();
    }

    @Override
    protected boolean internalInstall() {
        final Instrumenter instrumenter = getInstrumenter();
        for (Probe probe : instrumenter.findProbesTaggedAs(null)) {
            addMapEntry(probe);
        }
        instrumenter.addProbeListener(probeListener);
        return true;
    }

    @Override
    protected void internalReset() {
        lineToProbesMap.clear();
    }

    @Override
    protected void internalDispose() {
        getInstrumenter().removeProbeListener(probeListener);
    }

    /**
     * Returns the {@link Probe}, if any, associated with a specific line of guest language code; if
     * more than one, return the one with the first starting character location.
     */
    public Probe findFirstProbe(LineLocation lineLocation) {
        Probe probe = null;
        final Collection<Probe> probes = findProbes(lineLocation);
        for (Probe probesOnLine : probes) {
            if (probe == null) {
                probe = probesOnLine;
            } else if (probesOnLine.getProbedSourceSection().getCharIndex() < probe.getProbedSourceSection().getCharIndex()) {
                probe = probesOnLine;
            }
        }
        return probe;
    }

    /**
     * Returns all {@link Probe}s whose associated source begins at the given {@link LineLocation},
     * an empty list if none.
     */
    public Collection<Probe> findProbes(LineLocation line) {
        final Collection<Probe> probes = lineToProbesMap.get(line);
        if (probes == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableCollection(probes);
    }

    private class LineToProbesListener extends DefaultProbeListener {

        @Override
        public void newProbeInserted(Probe probe) {
            addMapEntry(probe);
        }
    }

    private void addMapEntry(Probe probe) {
        final SourceSection sourceSection = probe.getProbedSourceSection();
        if (sourceSection != null && sourceSection.getSource() != null) {
            final LineLocation lineLocation = sourceSection.getLineLocation();
            if (TRACE) {
                trace("ADD " + lineLocation.getShortDescription() + " ==> " + probe.getShortDescription());
            }
            Collection<Probe> probes = lineToProbesMap.get(lineLocation);
            if (probes == null) {
                probes = new ArrayList<>(2);
                lineToProbesMap.put(lineLocation, probes);
            } else {
                assert !probes.contains(probe);
            }
            probes.add(probe);
        }
    }
}
