/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.graph.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class contains all inlining decisions performed on a graph during the compilation.
 *
 * Each inlining decision consists of:
 *
 * <ul>
 * <li>a value indicating whether the decision was positive or negative</li>
 * <li>the call target method</li>
 * <li>the reason for the inlining decision</li>
 * <li>the name of the phase in which the inlining decision took place</li>
 * <li>the inlining log of the inlined graph, or {@code null} if the decision was negative</li>
 * </ul>
 *
 * A phase that does inlining should use the instance of this class contained in the
 * {@link StructuredGraph} by calling {@link #addDecision} whenever it decides to inline a method.
 * If there are invokes in the graph at the end of the respective phase, then that phase must call
 * {@link #addDecision} to log negative decisions.
 */
public class InliningLog {
    public static final class Decision {
        private final boolean positive;
        private final String reason;
        private final String phase;
        private final ResolvedJavaMethod target;

        private Decision(boolean positive, String reason, String phase, ResolvedJavaMethod target) {
            this.positive = positive;
            this.reason = reason;
            this.phase = phase;
            this.target = target;
        }

        public boolean isPositive() {
            return positive;
        }

        public String getReason() {
            return reason;
        }

        public String getPhase() {
            return phase;
        }

        public ResolvedJavaMethod getTarget() {
            return target;
        }
    }

    private static class Callsite {
        public final Callsite parent;
        public final List<String> decisions;
        public final List<Callsite> children;
        public Node originalInvoke;

        Callsite(Callsite parent, Node originalInvoke) {
            this.parent = parent;
            this.decisions = new ArrayList<>();
            this.children = new ArrayList<>();
            this.originalInvoke = originalInvoke;
        }
    }

    private final Callsite root;
    private final EconomicMap<Node, Callsite> leaves;

    public InliningLog() {
        this.root = new Callsite(null, null);
        this.leaves = EconomicMap.create();
    }

    public void addDecision(boolean positive, String reason, String phase, Node invoke, InliningLog calleeLog) {
        assert leaves.containsKey(invoke);
        Decision decision = new Decision(positive, reason, phase, getCallTarget(invoke).targetMethod());
    }

    private CallTargetNode getCallTarget(Node invoke) {
        if (invoke instanceof Invoke) {
            return ((Invoke) invoke).callTarget();
        }
        // TODO: Macro nodes, add marker interface.
        return null;
    }

    public void trackNewCallsite(Node sibling, Node newInvoke) {
    }

    public void updateCallsite(Node previousInvoke, Node newInvoke) {
    }

    public void inlineCallsite(Node invoke, Map<Node, Node> duplicationMap) {
    }
}

