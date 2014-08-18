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

    /**
     * The collection of {@link ProbeListener}s to call.
     */
    private final List<ProbeListener> probeListeners = new ArrayList<>();

    /**
     * The collection of all probes added.
     */
    private final List<ProbeImpl> probeList = new ArrayList<>();

    /**
     * The callback to be triggered by the {@link #tagTrap}.
     *
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
     * Add a {@link ProbeListener} to receive events.
     *
     * @param listener The listener to be added.
     */
    public void addProbeListener(ProbeListener listener) {
        assert listener != null;
        probeListeners.add(listener);
    }

    /**
     * Remove a {@link ProbeListener}. If no matching probe listener is found, nothing happens.
     *
     * @param removeListener
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
     * Creates a {@link Probe} for the given {@link SourceSection} and informs all
     * {@link ProbeListener}s stored in this manager of its creation.
     *
     * @param source The source section to associate with this probe.
     * @return The probe that was created.
     */
    public Probe createProbe(SourceSection source) {
        assert source != null;

        ProbeImpl probe = InstrumentationNode.createProbe(source, probeCallback);
        probeList.add(probe);

        for (ProbeListener listener : probeListeners) {
            listener.newProbeInserted(source, probe);
        }

        return probe;
    }

    /**
     * Returns a collection of {@link Probe}s created by this manager that have the given
     * {@link SyntaxTag}.
     *
     * @param tag The tag to search for.
     * @return An iterable collection of probes containing the given tag.
     */
    public Collection<Probe> findProbesTaggedAs(SyntaxTag tag) {
        final List<Probe> probes = new ArrayList<>();
        for (Probe probe : probeList) {
            if (tag == null || probe.isTaggedAs(tag)) {
                probes.add(probe);
            }
        }
        return probes;
    }

    /**
     * Calls {@link ProbeImpl#setTrap(SyntaxTagTrap)} for all probes with the given
     * {@link SyntaxTag} . There can only be one tag trap set at a time. An
     * {@link IllegalStateException} is thrown if this is called and a tag trap has already been
     * set.
     *
     * @param tagTrap The {@link SyntaxTagTrap} to set.
     */
    public void setTagTrap(SyntaxTagTrap tagTrap) {
        assert tagTrap != null;
        if (this.tagTrap != null) {
            throw new IllegalStateException("trap already set");
        }
        this.tagTrap = tagTrap;

        SyntaxTag tag = tagTrap.getTag();
        for (ProbeImpl probe : probeList) {
            if (probe.isTaggedAs(tag)) {
                probe.setTrap(tagTrap);
            }
        }
    }

    /**
     * Clears the current {@link SyntaxTagTrap}. If no trap has been set, an
     * {@link IllegalStateException} is thrown.
     */
    public void clearTagTrap() {
        if (this.tagTrap == null) {
            throw new IllegalStateException("no trap set");
        }
        for (ProbeImpl probe : probeList) {
            if (probe.isTaggedAs(tagTrap.getTag())) {
                probe.setTrap(null);
            }
        }
        tagTrap = null;
    }

}
