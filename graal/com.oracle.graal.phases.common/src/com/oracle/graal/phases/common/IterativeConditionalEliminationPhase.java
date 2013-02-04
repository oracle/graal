/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.Graph.InputChangedListener;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.*;

public class IterativeConditionalEliminationPhase extends Phase {

    private final TargetDescription target;
    private final MetaAccessProvider runtime;
    private final Assumptions assumptions;

    public IterativeConditionalEliminationPhase(TargetDescription target, MetaAccessProvider runtime, Assumptions assumptions) {
        this.target = target;
        this.runtime = runtime;
        this.assumptions = assumptions;
    }

    @Override
    protected void run(StructuredGraph graph) {
        Set<Node> canonicalizationRoots = new HashSet<>();
        ConditionalEliminationPhase eliminate = new ConditionalEliminationPhase(runtime);
        Listener listener = new Listener(canonicalizationRoots);
        while (true) {
            graph.trackInputChange(listener);
            eliminate.apply(graph);
            graph.stopTrackingInputChange();
            if (canonicalizationRoots.isEmpty()) {
                break;
            }
            new CanonicalizerPhase(target, runtime, assumptions, canonicalizationRoots, null).apply(graph);
            canonicalizationRoots.clear();
        }
    }

    private static class Listener implements InputChangedListener {

        private final Set<Node> canonicalizationRoots;

        public Listener(Set<Node> canonicalizationRoots) {
            this.canonicalizationRoots = canonicalizationRoots;
        }

        @Override
        public void inputChanged(Node node) {
            canonicalizationRoots.add(node);
        }
    }
}
