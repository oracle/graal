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
import com.oracle.truffle.api.source.*;

/**
 * Access to information and basic services in the runtime context for a Truffle-implemented guest
 * language.
 * <p>
 * <strong>Disclaimer:</strong> this interface is under development and will change.
 */
public interface ExecutionContext {

    /**
     * Gets the name of the language, possibly with version number. in short enough form that it
     * might be used for an interactive prompt.
     */
    String getLanguageShortName();

    /**
     * Gets access to source management services.
     */
    SourceManager getSourceManager();

    /**
     * Registers a tool interested in being notified of events related to the loading of sources.
     */
    void addSourceListener(SourceListener listener);

    /**
     * Add instrumentation to subsequently constructed Truffle ASTs for the guest language; every
     * one added will have the opportunity to add instrumentation.
     *
     * @throws IllegalStateException if AST instrumentation not enabled
     * @throws IllegalArgumentException if prober not usable for the guest language implementation.
     */
    void addNodeProber(ASTNodeProber nodeProber) throws IllegalStateException, IllegalArgumentException;

    /**
     * Registers a tool interested in being notified about the insertion of a newly created
     * {@link Probe} into a Truffle AST.
     */
    void addProbeListener(ProbeListener listener);

    /**
     * Return the (possibly newly created) {@link Probe} uniquely associated with a particular
     * source code location. A newly created probe carries no tags.
     *
     * @return a probe uniquely associated with an extent of guest language source code.
     */
    Probe getProbe(SourceSection sourceSection);

    /**
     * Returns all existing probes with specific tag, or all probes if {@code tag = null}; empty
     * collection if no probes found.
     */
    Collection<Probe> findProbesTaggedAs(PhylumTag tag);

    /**
     * Returns all existing probes with first character on a specified line; empty collection if no
     * probes found.
     */
    Collection<Probe> findProbesByLine(SourceLineLocation lineLocation);

    /**
     * Sets a trap that will make a callback at any AST location where a existing probe holds a
     * specified tag; only one trap may be set at a time.
     *
     * @throws IllegalStateException if a trap is already set
     */
    void setTrap(PhylumTrap trap) throws IllegalStateException;

    /**
     * Clears a trap that will halt execution; only one trap may be set at a time.
     *
     * @throws IllegalStateException if no trap is set.
     */
    void clearTrap() throws IllegalStateException;

    /**
     * Access to information visualization services for the specific language.
     */
    Visualizer getVisualizer();
}
