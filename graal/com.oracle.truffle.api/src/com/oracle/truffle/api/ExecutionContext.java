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
package com.oracle.truffle.api;

import java.util.*;

import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.instrument.impl.*;
import com.oracle.truffle.api.source.*;

/**
 * Access to information and basic services in the runtime context for a Truffle-implemented guest
 * language.
 * <p>
 * <strong>Disclaimer:</strong> this class is under development and will change.
 */
public abstract class ExecutionContext {

    private final ProbeManager probeManager = new ProbeManager();
    private final List<SourceListener> sourceListeners = new ArrayList<>();
    private Visualizer visualizer = new DefaultVisualizer();

    protected ExecutionContext() {
    }

    /**
     * Sets up the {@link SourceCallback} for this execution context.
     */
    public void initialize() {
        setSourceCallback(new SourceCallback() {

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
        });
    }

    /**
     * Registers a tool interested in being notified about the loading of {@link Source}s.
     */
    public final void addSourceListener(SourceListener listener) {
        assert listener != null;
        sourceListeners.add(listener);
    }

    /**
     * Unregisters a tool interested in being notified about the loading of {@link Source}s.
     */
    public final void removeSourceListener(SourceListener removeListener) {
        final List<SourceListener> listeners = new ArrayList<>(sourceListeners);
        for (SourceListener listener : listeners) {
            if (listener == removeListener) {
                sourceListeners.remove(listener);
            }
        }
    }

    /**
     * Registers a tool interested in being notified about the insertion of a newly created
     * {@link Probe} into a Truffle AST.
     */
    public final void addProbeListener(ProbeListener listener) {
        probeManager.addProbeListener(listener);
    }

    /**
     * Unregisters a tool interested in being notified about the insertion of a newly created
     * {@link Probe} into a Truffle AST.
     */
    public final void removeProbeListener(ProbeListener listener) {
        probeManager.removeProbeListener(listener);
    }

    /**
     * Return a newly created {@link Probe} uniquely associated with a particular source section. A
     * newly created probe carries no tags.
     *
     * @return a probe uniquely associated with an extent of guest language source code.
     */
    public final Probe createProbe(SourceSection source) {
        return probeManager.createProbe(source);
    }

    /**
     * Returns all existing probes with specific tag, or all probes if {@code tag = null}; empty
     * collection if no probes found.
     */
    public final Collection<Probe> findProbesTaggedAs(SyntaxTag tag) {
        return probeManager.findProbesTaggedAs(tag);
    }

    /**
     * Sets a trap that will make a callback at any AST location where a existing probe holds a
     * specified tag; only one trap may be set at a time.
     *
     * @throws IllegalStateException if a trap is already set
     */
    public final void setTagTrap(SyntaxTagTrap trap) throws IllegalStateException {
        // TODO (mlvdv) consider allowing multiple traps (without inhibiting Truffle inlining)
        probeManager.setTagTrap(trap);
    }

    /**
     * Clears a trap that will halt execution; only one trap may be set at a time.
     *
     * @throws IllegalStateException if no trap is set.
     */
    public final void clearTagTrap() {
        probeManager.clearTagTrap();
    }

    /**
     * Access to information visualization services for the specific language.
     */
    public final Visualizer getVisualizer() {
        return visualizer;
    }

    /**
     * Assign guest language-specific visualization support for tools. This must be assigned outside
     * the implementation context to avoid build circularities.
     */
    public final void setVisualizer(Visualizer visualizer) {
        this.visualizer = visualizer;
    }

    /**
     * Gets the name of the language, possibly with version number. in short enough form that it
     * might be used for an interactive prompt.
     */
    public abstract String getLanguageShortName();

    /**
     * Establishes source event reporting
     */
    protected abstract void setSourceCallback(SourceCallback sourceCallback);

}
