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
package com.oracle.graal.loop;

import static com.oracle.graal.compiler.common.GraalOptions.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.graph.Graph.Mark;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.NodeClass.NodeClassIterator;
import com.oracle.graal.graph.NodeClass.Position;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;

public abstract class LoopTransformations {

    private static final int UNROLL_LIMIT = FullUnrollMaxNodes.getValue() * 2;

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

    public static void fullUnroll(LoopEx loop, PhaseContext context, CanonicalizerPhase canonicalizer) {
        // assert loop.isCounted(); //TODO (gd) strenghten : counted with known trip count
        int iterations = 0;
        LoopBeginNode loopBegin = loop.loopBegin();
        StructuredGraph graph = loopBegin.graph();
        while (!loopBegin.isDeleted()) {
            Mark mark = graph.getMark();
            peel(loop);
            canonicalizer.applyIncremental(graph, context, mark);
            loopBegin.removeDeadPhis();
            loop.invalidateFragments();
            if (iterations++ > UNROLL_LIMIT || graph.getNodeCount() > MaximumDesiredSize.getValue() * 3) {
                throw new BailoutException("FullUnroll : Graph seems to grow out of proportion");
            }
        }
    }

    public static void unswitch(LoopEx loop, List<ControlSplitNode> controlSplitNodeSet) {
        ControlSplitNode firstNode = controlSplitNodeSet.iterator().next();
        LoopFragmentWhole originalLoop = loop.whole();
        StructuredGraph graph = firstNode.graph();

        // create new control split out of loop
        ControlSplitNode newControlSplit = (ControlSplitNode) firstNode.copyWithInputs();
        originalLoop.entryPoint().replaceAtPredecessor(newControlSplit);

        /*
         * The code below assumes that all of the control split nodes have the same successor
         * structure, which should have been enforced by findUnswitchable.
         */
        NodeClassIterator successors = firstNode.successors().iterator();
        assert successors.hasNext();
        // original loop is used as first successor
        Position firstPosition = successors.nextPosition();
        NodeClass controlSplitClass = firstNode.getNodeClass();
        BeginNode originalLoopBegin = BeginNode.begin(originalLoop.entryPoint());
        controlSplitClass.set(newControlSplit, firstPosition, originalLoopBegin);

        while (successors.hasNext()) {
            Position position = successors.nextPosition();
            // create a new loop duplicate and connect it.
            LoopFragmentWhole duplicateLoop = originalLoop.duplicate();
            BeginNode newBegin = BeginNode.begin(duplicateLoop.entryPoint());
            controlSplitClass.set(newControlSplit, position, newBegin);

            // For each cloned ControlSplitNode, simplify the proper path
            for (ControlSplitNode controlSplitNode : controlSplitNodeSet) {
                ControlSplitNode duplicatedControlSplit = duplicateLoop.getDuplicatedNode(controlSplitNode);
                if (duplicatedControlSplit.isAlive()) {
                    BeginNode survivingSuccessor = (BeginNode) controlSplitClass.get(duplicatedControlSplit, position);
                    survivingSuccessor.replaceAtUsages(InputType.Guard, newBegin);
                    graph.removeSplitPropagate(duplicatedControlSplit, survivingSuccessor);
                }
            }
        }
        // original loop is simplified last to avoid deleting controlSplitNode too early
        for (ControlSplitNode controlSplitNode : controlSplitNodeSet) {
            if (controlSplitNode.isAlive()) {
                BeginNode survivingSuccessor = (BeginNode) controlSplitClass.get(controlSplitNode, firstPosition);
                survivingSuccessor.replaceAtUsages(InputType.Guard, originalLoopBegin);
                graph.removeSplitPropagate(controlSplitNode, survivingSuccessor);
            }
        }

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
        // CountedLoopBeginNode counted = prologue.countedLoop();
        // StructuredGraph graph = (StructuredGraph) counted.graph();
        // ValueNode tripCountPrologue = counted.tripCount();
        // ValueNode tripCountMain = counted.tripCount();
        // graph.replaceFloating(tripCountPrologue, "tripCountPrologue % factor");
        // graph.replaceFloating(tripCountMain, "tripCountMain - (tripCountPrologue % factor)");
        LoopFragmentInside inside = loop.inside();
        for (int i = 0; i < factor; i++) {
            inside.duplicate().appendInside(loop);
        }
    }

    public static List<ControlSplitNode> findUnswitchable(LoopEx loop) {
        List<ControlSplitNode> controls = null;
        ValueNode invariantValue = null;
        for (IfNode ifNode : loop.whole().nodes().filter(IfNode.class)) {
            if (loop.isOutsideLoop(ifNode.condition())) {
                if (controls == null) {
                    invariantValue = ifNode.condition();
                    controls = new ArrayList<>();
                    controls.add(ifNode);
                } else if (ifNode.condition() == invariantValue) {
                    controls.add(ifNode);
                }
            }
        }
        if (controls == null) {
            SwitchNode firstSwitch = null;
            for (SwitchNode switchNode : loop.whole().nodes().filter(SwitchNode.class)) {
                if (switchNode.successors().count() > 1 && loop.isOutsideLoop(switchNode.value())) {
                    if (controls == null) {
                        firstSwitch = switchNode;
                        invariantValue = switchNode.value();
                        controls = new ArrayList<>();
                        controls.add(switchNode);
                    } else if (switchNode.value() == invariantValue && firstSwitch.structureEquals(switchNode)) {
                        // Only collect switches which test the same values in the same order
                        controls.add(switchNode);
                    }
                }
            }
        }
        return controls;
    }
}
