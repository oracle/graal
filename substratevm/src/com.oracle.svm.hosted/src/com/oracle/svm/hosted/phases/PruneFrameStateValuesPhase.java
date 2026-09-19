/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.phases;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

import com.oracle.svm.core.code.FrameInfoEncoder.ValueRetentionPolicy;
import com.oracle.svm.hosted.code.FrameInfoRetention;
import com.oracle.svm.hosted.meta.HostedMethod;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.NodeWithState;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.tiers.LowTierContext;

/** Prunes unneeded local and operand-stack values from frame states. */
public final class PruneFrameStateValuesPhase extends BasePhase<LowTierContext> {
    @Override
    protected void run(StructuredGraph graph, LowTierContext context) {
        /* Use the compilation root's capabilities, including for states from inlined methods. */
        if (!(graph.method() instanceof HostedMethod method) || !FrameInfoRetention.canPruneFrameStateValues(method)) {
            return;
        }
        pruneFrameStateValues(graph, state -> FrameInfoRetention.getValueRetentionPolicy(method, state));
    }

    static void pruneFrameStateValues(StructuredGraph graph, Function<FrameState, ValueRetentionPolicy> retentionForState) {
        Map<FrameState, FrameState> pruned = new IdentityHashMap<>();
        for (Node node : graph.getNodes().snapshot()) {
            if (!(node instanceof NodeWithState nodeWithState)) {
                continue;
            }
            for (FrameState state : nodeWithState.states().snapshot()) {
                ValueRetentionPolicy retention = retentionForState.apply(state);
                if (retention != ValueRetentionPolicy.ALL) {
                    node.replaceFirstInput(state, pruneFrameState(state, retention, pruned));
                }
            }
        }
        for (FrameState state : pruned.keySet()) {
            if (state.isAlive() && state.hasNoUsages()) {
                GraphUtil.killWithUnusedFloatingInputs(state);
            }
        }
    }

    private static FrameState pruneFrameState(FrameState state, ValueRetentionPolicy retention, Map<FrameState, FrameState> pruned) {
        ArrayList<FrameState> pending = new ArrayList<>();
        FrameState current = state;
        while (current != null && !pruned.containsKey(current)) {
            pending.add(current);
            current = current.outerFrameState();
        }
        FrameState outer = pruned.get(current);
        while (!pending.isEmpty()) {
            current = pending.removeLast();
            /* A caller state can also be shared by a chain whose values must be retained. */
            /* Preserve locks; the replacement also keeps monitor IDs and virtual-object mappings. */
            ArrayList<ValueNode> values = new ArrayList<>(current.values().subList(0, current.locksSize()));
            for (int i = 0; i < current.stackSize(); i++) {
                values.add(retention.retainStackOperand(current.getMethod(), outer == null ? null : outer.getMethod(), i) ? current.stackAt(i) : null);
            }
            for (int i = 0; i < current.localsSize(); i++) {
                values.add(retention.retainLocalValue(current.getMethod(), outer == null ? null : outer.getMethod(), i) ? current.localAt(i) : null);
            }
            /* FrameState stores trailing null locals implicitly. */
            while (values.size() > current.locksSize() + current.stackSize() && values.getLast() == null) {
                values.removeLast();
            }
            FrameState replacement = current.graph().add(new FrameState(outer, current.getCode(), current.bci, values,
                            current.localsSize(), current.stackSize(), current.locksSize(), current.getStackState(), false,
                            current.monitorIds(), current.virtualObjectMappings(), null));
            replacement.setNodeSourcePosition(current.getNodeSourcePosition());
            pruned.put(current, replacement);
            outer = replacement;
        }
        return outer;
    }
}
