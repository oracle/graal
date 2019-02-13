/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.phases;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.DynamicDeoptimizeNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.phases.common.DeoptimizationGroupingPhase;

import com.oracle.svm.core.graal.nodes.DeoptSourcePositionInfoNode;

/**
 * This phase collects {@link NodeSourcePosition} for deoptimizations. The source information is
 * printed during deoptimization to help indentifying deoptimization issues like repeated
 * deoptimizations.
 * <p>
 * The program counter of the call to the deoptimization in the compiled code is not unique to
 * identify a deoptimization, because {@link DeoptimizationGroupingPhase} tries to reduce the number
 * of calls. Therefore, we associate a unique number with each deoptimization here before the
 * grouping: the {@link DeoptimizeNode#getDebugId()}.
 */
public class CollectDeoptimizationSourcePositionsPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        List<NodeSourcePosition> deoptimzationSourcePositions = new ArrayList<>();

        /*
         * The debugId 0 is reserved for "unknown" to avoid any possible confusion with an
         * uninitialized debugId.
         */
        deoptimzationSourcePositions.add(null);

        for (DeoptimizeNode node : graph.getNodes(DeoptimizeNode.TYPE)) {
            node.setDebugId(deoptimzationSourcePositions.size());
            deoptimzationSourcePositions.add(node.getNodeSourcePosition());
        }
        assert graph.getNodes(DynamicDeoptimizeNode.TYPE).isEmpty() : "must collect NodeSourcePosition before DeoptimizationGroupingPhase";

        graph.addAfterFixed(graph.start(), graph.add(new DeoptSourcePositionInfoNode(deoptimzationSourcePositions)));

    }
}
