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
package com.oracle.truffle.api.instrument;

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.source.*;

public interface Instrumentation {

    /**
     * Adds a new specification for how to instrument ASTs.
     */
    void addNodeProber(ASTNodeProber nodeProber);

    /**
     * Registers a tool interested in being notified about the insertion of a newly created
     * {@link Probe} into a Truffle AST.
     */
    void addProbeListener(ProbeListener listener);

    /**
     * Return the (possibly newly created) {@link Probe} uniquely associated with a particular
     * source code location. A newly created probe carries no tags.
     *
     * @param eventListener an optional listener for certain instrumentation-related events
     * @return a probe uniquely associated with an extent of guest language source code.
     */
    Probe getProbe(SourceSection sourceSection, InstrumentEventListener eventListener);

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

}
