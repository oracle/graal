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
package com.oracle.truffle.api.debug;

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.instrument.InstrumentationProbeNode.ProbeChain;
import com.oracle.truffle.api.source.*;

/**
 * A minimal, language-agnostic implementation that tracks loaded sources, and keeps maps describing
 * what locations in the source have instrumentation available. This implementation will do nothing
 * unless there are calls to it during AST construction, notably {@link #notifyStartLoading(Source)}
 * and {@link #notifyFinishedLoading(Source)} <em>and</em> there are at least some AST nodes being
 * instrumented.
 */
public class DefaultDebugManager implements DebugManager {

    private final Set<Source> loadedSources = new HashSet<>();

    private Source beingLoaded = null;

    /**
     * Map: SourceSection ==&gt; probe chain associated with that source section in an AST.
     */
    private final Map<SourceSection, ProbeChain> srcToProbeChain = new HashMap<>();

    /**
     * Map: Source lines ==&gt; probe chains associated with source sections starting on the line.
     */
    private final Map<SourceLineLocation, Set<ProbeChain>> lineToProbeChains = new HashMap<>();

    private final ExecutionContext context;

    public DefaultDebugManager(ExecutionContext context) {
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
        assert beingLoaded == null;
        beingLoaded = source;
    }

    public void notifyFinishedLoading(Source source) {
        assert source == beingLoaded;
        loadedSources.add(source);
        beingLoaded = null;
    }

    public void haltedAt(Node astNode, MaterializedFrame frame) {
    }

}
