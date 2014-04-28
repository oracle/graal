/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.phases.common;

import com.oracle.graal.graph.Graph.Mark;
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.util.*;
import com.oracle.graal.phases.tiers.*;

/**
 * A phase suite that applies {@linkplain CanonicalizerPhase canonicalization} to a graph after all
 * phases in the suite have been applied if any of the phases changed the graph.
 */
public class IncrementalCanonicalizerPhase<C extends PhaseContext> extends PhaseSuite<C> {

    private final CanonicalizerPhase canonicalizer;

    public IncrementalCanonicalizerPhase(CanonicalizerPhase canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    public IncrementalCanonicalizerPhase(CanonicalizerPhase canonicalizer, BasePhase<? super C> phase) {
        this.canonicalizer = canonicalizer;
        appendPhase(phase);
    }

    @Override
    protected void run(StructuredGraph graph, C context) {
        Mark newNodesMark = graph.getMark();

        HashSetNodeChangeListener listener = new HashSetNodeChangeListener();
        graph.trackInputChange(listener);
        graph.trackUsagesDroppedZero(listener);

        super.run(graph, context);

        graph.stopTrackingInputChange();
        graph.stopTrackingUsagesDroppedZero();

        if (graph.getMark() != newNodesMark || !listener.getChangedNodes().isEmpty()) {
            canonicalizer.applyIncremental(graph, context, listener.getChangedNodes(), newNodesMark, false);
        }
    }
}
