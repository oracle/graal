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

import java.util.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.graph.*;

/**
 * This phase performs a bit of hygiene on {@link ValueAnchorNode} by removing inputs that have
 * already been anchored in a dominating value anchor. Value anchors that lose their last input,
 * have no usages and are not marked as permanent are removed.
 */
public class ValueAnchorCleanupPhase extends Phase {

    private static class State extends MergeableState<State> implements Cloneable {

        private final HashSet<Node> anchoredValues;

        public State() {
            anchoredValues = new HashSet<>();
        }

        public State(State other) {
            anchoredValues = new HashSet<>(other.anchoredValues);
        }

        @Override
        public boolean merge(MergeNode merge, List<State> withStates) {
            for (State other : withStates) {
                anchoredValues.retainAll(other.anchoredValues);
            }
            return true;
        }

        @Override
        public State clone() {
            return new State(this);
        }
    }

    private static class CleanupValueAnchorsClosure extends PostOrderNodeIterator<State> {

        public CleanupValueAnchorsClosure(FixedNode start) {
            super(start, new State());
        }

        @Override
        protected void node(FixedNode node) {
            if (node instanceof ValueAnchorNode) {
                ValueAnchorNode anchor = (ValueAnchorNode) node;
                ValueNode anchored = anchor.getAnchoredNode();
                if (anchored != null) {
                    if (state.anchoredValues.contains(anchored)) {
                        anchor.removeAnchoredNode();
                    } else {
                        state.anchoredValues.add(anchored);
                    }
                }
                if (anchor.getAnchoredNode() == null && anchor.usages().isEmpty()) {
                    node.graph().removeFixed(anchor);
                }
            }
        }
    }

    @Override
    protected void run(StructuredGraph graph) {
        CleanupValueAnchorsClosure closure = new CleanupValueAnchorsClosure(graph.start());
        closure.apply();
    }
}
