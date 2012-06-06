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

import com.oracle.graal.compiler.phases.*;
import com.oracle.graal.nodes.*;


public abstract class LoopTransformations {
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

    public static void fullUnroll(LoopEx loop) {
        //assert loop.isCounted(); //TODO (gd) strenghten : counted with known trip count
        LoopBeginNode loopBegin = loop.loopBegin();
        StructuredGraph graph = (StructuredGraph) loopBegin.graph();
        while (!loopBegin.isDeleted()) {
            int mark = graph.getMark();
            peel(loop);
            new CanonicalizerPhase(null, null, null, mark, null).apply(graph);
        }
    }

    public static void unswitch(LoopEx loop, IfNode ifNode) {
        // duplicate will be true case, original will be false case
        LoopFragmentWhole duplicateLoop = loop.whole().duplicate();
        StructuredGraph graph = (StructuredGraph) ifNode.graph();
        BeginNode tempBegin = graph.add(new BeginNode());
        loop.entryPoint().replaceAtPredecessor(tempBegin);
        double takenProbability = ifNode.probability(ifNode.blockSuccessorIndex(ifNode.trueSuccessor()));
        IfNode newIf = graph.add(new IfNode(ifNode.compare(), duplicateLoop.loop().entryPoint(), loop.entryPoint(), takenProbability));
        tempBegin.setNext(newIf);
        ifNode.setCompare(graph.unique(ConstantNode.forBoolean(false, graph)));
        duplicateLoop.getDuplicatedNode(ifNode).setCompare(graph.unique(ConstantNode.forBoolean(true, graph)));
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
}
