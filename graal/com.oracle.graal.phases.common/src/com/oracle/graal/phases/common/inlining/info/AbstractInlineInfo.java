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
package com.oracle.graal.phases.common.inlining.info;

import java.util.*;

import com.oracle.graal.api.code.Assumptions;
import com.oracle.graal.api.meta.ResolvedJavaMethod;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.common.CanonicalizerPhase;
import com.oracle.graal.phases.common.inlining.InliningUtil;
import com.oracle.graal.phases.common.inlining.info.elem.Inlineable;
import com.oracle.graal.phases.common.inlining.info.elem.InlineableMacroNode;
import com.oracle.graal.phases.common.inlining.info.elem.InlineableGraph;
import com.oracle.graal.phases.tiers.HighTierContext;

public abstract class AbstractInlineInfo implements InlineInfo {

    protected final Invoke invoke;

    public AbstractInlineInfo(Invoke invoke) {
        this.invoke = invoke;
    }

    @Override
    public StructuredGraph graph() {
        return invoke.asNode().graph();
    }

    @Override
    public Invoke invoke() {
        return invoke;
    }

    protected static Collection<Node> inline(Invoke invoke, ResolvedJavaMethod concrete, Inlineable inlineable, Assumptions assumptions, boolean receiverNullCheck) {
        List<Node> canonicalizeNodes = new ArrayList<>();
        if (inlineable instanceof InlineableGraph) {
            StructuredGraph calleeGraph = ((InlineableGraph) inlineable).getGraph();
            Map<Node, Node> duplicateMap = InliningUtil.inline(invoke, calleeGraph, receiverNullCheck, canonicalizeNodes);
            getInlinedParameterUsages(canonicalizeNodes, calleeGraph, duplicateMap);
        } else {
            assert inlineable instanceof InlineableMacroNode;

            Class<? extends FixedWithNextNode> macroNodeClass = ((InlineableMacroNode) inlineable).getMacroNodeClass();
            FixedWithNextNode macroNode = InliningUtil.inlineMacroNode(invoke, concrete, macroNodeClass);
            canonicalizeNodes.add(macroNode);
        }

        InliningUtil.InlinedBytecodes.add(concrete.getCodeSize());
        assumptions.recordMethodContents(concrete);
        return canonicalizeNodes;
    }

    public static void getInlinedParameterUsages(Collection<Node> parameterUsages, StructuredGraph calleeGraph, Map<Node, Node> duplicateMap) {
        for (ParameterNode parameter : calleeGraph.getNodes(ParameterNode.class)) {
            for (Node usage : parameter.usages()) {
                Node node = duplicateMap.get(usage);
                if (node != null && node.isAlive()) {
                    parameterUsages.add(node);
                }
            }
        }
    }

    public final void populateInlinableElements(HighTierContext context, Assumptions calleeAssumptions, CanonicalizerPhase canonicalizer) {
        for (int i = 0; i < numberOfMethods(); i++) {
            Inlineable elem = Inlineable.getInlineableElement(methodAt(i), invoke, context.replaceAssumptions(calleeAssumptions), canonicalizer);
            setInlinableElement(i, elem);
        }
    }

    public final int determineNodeCount() {
        int nodes = 0;
        for (int i = 0; i < numberOfMethods(); i++) {
            Inlineable elem = inlineableElementAt(i);
            if (elem != null) {
                nodes += elem.getNodeCount();
            }
        }
        return nodes;
    }
}
