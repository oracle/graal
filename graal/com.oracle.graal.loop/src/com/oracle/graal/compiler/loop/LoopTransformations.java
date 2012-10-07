/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.loop;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.phases.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.util.*;


public abstract class LoopTransformations {
    private static final int UNROLL_LIMIT = GraalOptions.FullUnrollMaxNodes * 2;
    private static final SimplifierTool simplifier = new SimplifierTool() {
        @Override
        public TargetDescription target() {
            return null;
        }
        @Override
        public CodeCacheProvider runtime() {
            return null;
        }
        @Override
        public boolean isImmutable(Constant objectConstant) {
            return false;
        }
        @Override
        public Assumptions assumptions() {
            return null;
        }
        @Override
        public void deleteBranch(FixedNode branch) {
            branch.predecessor().replaceFirstSuccessor(branch, null);
            GraphUtil.killCFG(branch);
        }
        @Override
        public void addToWorkList(Node node) {
        }
    };

    private LoopTransformations() {
        // does not need to be instantiated
    }

    public static void invert(LoopEx loop, FixedNode point) {
        LoopFragmentInsideBefore head = loop.insideBefore(point);
        LoopFragmentInsideBefore duplicate = head.duplicate();
        head.disconnect();
        head.insertBefore(loop);
        duplicate.appendInside(loop);
    }

    public static void peel(LoopEx loop) {
        loop.inside().duplicate().insertBefore(loop);
    }

    public static void fullUnroll(LoopEx loop, MetaAccessProvider runtime) {
        //assert loop.isCounted(); //TODO (gd) strenghten : counted with known trip count
        int iterations = 0;
        LoopBeginNode loopBegin = loop.loopBegin();
        StructuredGraph graph = (StructuredGraph) loopBegin.graph();
        while (!loopBegin.isDeleted()) {
            int mark = graph.getMark();
            peel(loop);
            new CanonicalizerPhase(null, runtime, null, mark, null).apply(graph);
            if (iterations++ > UNROLL_LIMIT || graph.getNodeCount() > GraalOptions.MaximumDesiredSize * 3) {
                throw new BailoutException("FullUnroll : Graph seems to grow out of proportion");
            }
        }
    }

    public static void unswitch(LoopEx loop, IfNode ifNode) {
        // duplicate will be true case, original will be false case
        loop.loopBegin().incUnswitches();
        LoopFragmentWhole originalLoop = loop.whole();
        LoopFragmentWhole duplicateLoop = originalLoop.duplicate();
        StructuredGraph graph = (StructuredGraph) ifNode.graph();
        BeginNode tempBegin = graph.add(new BeginNode());
        originalLoop.entryPoint().replaceAtPredecessor(tempBegin);
        double takenProbability = ifNode.probability(ifNode.blockSuccessorIndex(ifNode.trueSuccessor()));
        IfNode newIf = graph.add(new IfNode(ifNode.compare(), duplicateLoop.entryPoint(), originalLoop.entryPoint(), takenProbability, ifNode.leafGraphId()));
        tempBegin.setNext(newIf);
        ifNode.setCompare(graph.unique(ConstantNode.forBoolean(false, graph)));
        IfNode duplicateIf = duplicateLoop.getDuplicatedNode(ifNode);
        duplicateIf.setCompare(graph.unique(ConstantNode.forBoolean(true, graph)));
        ifNode.simplify(simplifier);
        duplicateIf.simplify(simplifier);
        // TODO (gd) probabilities need some amount of fixup.. (probably also in other transforms)
    }

    public static void unroll(LoopEx loop, int factor) {
        assert loop.isCounted();
        if (factor > 0) {
            throw new UnsupportedOperationException();
        }
        // TODO (gd) implement counted loop
        LoopFragmentWhole main = loop.whole();
        LoopFragmentWhole prologue = main.duplicate();
        prologue.insertBefore(loop);
        //CountedLoopBeginNode counted = prologue.countedLoop();
        //StructuredGraph graph = (StructuredGraph) counted.graph();
        //ValueNode tripCountPrologue = counted.tripCount();
        //ValueNode tripCountMain = counted.tripCount();
        //graph.replaceFloating(tripCountPrologue, "tripCountPrologue % factor");
        //graph.replaceFloating(tripCountMain, "tripCountMain - (tripCountPrologue % factor)");
        LoopFragmentInside inside = loop.inside();
        for (int i = 0; i < factor; i++) {
            inside.duplicate().appendInside(loop);
        }
    }

    public static IfNode findUnswitchableIf(LoopEx loop) {
        for (IfNode ifNode : loop.whole().nodes().filter(IfNode.class)) {
            if (loop.isOutsideLoop(ifNode.compare())) {
                return ifNode;
            }
        }
        return null;
    }
}
