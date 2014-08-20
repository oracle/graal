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

import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.instrument.impl.InstrumentationNode.ProbeCallback;
import com.oracle.truffle.api.instrument.impl.InstrumentationNode.ProbeImpl;
import com.oracle.truffle.api.source.*;

/**
 * Factory and services for AST {@link Probe}s
 */
public final class ProbeManager {

    private final List<ProbeListener> probeListeners = new ArrayList<>();

    private final List<ProbeImpl> allProbes = new ArrayList<>();

    /**
     * Called when a {@link #tagTrap} is activated in a Probe.
     */
    private final ProbeCallback probeCallback;

    /**
     * When non-null, "enter" events with matching tags will trigger a callback.
     */
    private SyntaxTagTrap tagTrap = null;

    public ProbeManager() {
        this.probeCallback = new ProbeCallback() {
            /**
             * Receives (from the {@link Probe} implementation) and distributes notification that a
             * {@link Probe} has acquired a new {@linkplain SyntaxTag tag}.
             */
            public void newTagAdded(ProbeImpl probe, SyntaxTag tag) {
                for (ProbeListener listener : probeListeners) {
                    listener.probeTaggedAs(probe, tag);
                }
                if (tagTrap != null && tag == tagTrap.getTag()) {
                    probe.setTrap(tagTrap);
                }
            }
        };
    }

    /**
     * Adds a {@link ProbeListener} to receive events.
     */
    public void addProbeListener(ProbeListener listener) {
        assert listener != null;
        probeListeners.add(listener);
    }

    /**
     * Removes a {@link ProbeListener}. Ignored if listener not found.
     */
    public void removeProbeListener(ProbeListener removeListener) {
        final List<ProbeListener> listeners = new ArrayList<>(probeListeners);
        for (ProbeListener listener : listeners) {
            if (listener == removeListener) {
                probeListeners.remove(listener);
            }
        }
    }

    /**
     * Creates a new {@link Probe} associated with a {@link SourceSection} of code corresponding to
     * a Truffle AST node.
     */
    public Probe createProbe(SourceSection source) {
        assert source != null;

        ProbeImpl probe = InstrumentationNode.createProbe(source, probeCallback);
        allProbes.add(probe);

        for (ProbeListener listener : probeListeners) {
            listener.newProbeInserted(source, probe);
        }

        return probe;
    }

    /**
     * Returns the subset of all {@link Probe}s holding a particular {@link SyntaxTag}, or the whole
     * collection if the specified tag is {@code null}.
     *
     * @return A collection of probes containing the given tag.
     */
    public Collection<Probe> findProbesTaggedAs(SyntaxTag tag) {
        final List<Probe> probes = new ArrayList<>();
        for (Probe probe : allProbes) {
            if (tag == null || probe.isTaggedAs(tag)) {
                probes.add(probe);
            }
        }
        return probes;
    }

    /**
     * Sets the current "tag trap", which will cause a callback to be triggered whenever execution
     * reaches a Probe (existing or subsequently created) with the specified tag. There can only be
     * one tag trap set at a time.
     * <p>
     *
     * @param tagTrap The {@link SyntaxTagTrap} to set.
     * @throws IllegalStateException if a trap is currently set.
     */
    public void setTagTrap(SyntaxTagTrap tagTrap) throws IllegalStateException {
        assert tagTrap != null;
        if (this.tagTrap != null) {
            throw new IllegalStateException("trap already set");
        }
        this.tagTrap = tagTrap;

        SyntaxTag tag = tagTrap.getTag();
        for (ProbeImpl probe : allProbes) {
            if (probe.isTaggedAs(tag)) {
                probe.setTrap(tagTrap);
            }
        }
    }

    /**
     * Clears the current {@link SyntaxTagTrap}.
     *
     * @throws IllegalStateException if no trap is currently set.
     */
    public void clearTagTrap() {
        if (this.tagTrap == null) {
            throw new IllegalStateException("no trap set");
        }
        for (ProbeImpl probe : allProbes) {
            if (probe.isTaggedAs(tagTrap.getTag())) {
                probe.setTrap(null);
            }
        }
        tagTrap = null;
    }

}
