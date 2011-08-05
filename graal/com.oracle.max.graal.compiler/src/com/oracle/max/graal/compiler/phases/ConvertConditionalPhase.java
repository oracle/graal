/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.phases;

import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.compiler.ir.Phi.PhiType;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.compiler.value.*;
import com.oracle.max.graal.graph.*;


/**
 * Temporary phase that converts Conditional/Materialize Nodes that can not be LIRGenered properly for now.
 * Currently LIRGenerating something like
 * BRANCHcc L1 // (compare, instanceof...)
 * mov res, fVal
 * jmp L2
 * L1:
 * mov res, tVal
 * L2:
 *
 * or
 *
 * mov res, tVal
 * BRANCHcc L1:
 * mov res, fVal
 * L1:
 *
 * may create a few problems around register allocation:
 * - in the first construct, register allocation may decide to spill an other variable to allocate res, resulting ina spilling that is done only in one branch :
 * BRANCHcc L1 // (compare, instanceof...)
 * mov SpillSplot, res
 * mov res, fVal
 * jmp L2
 * L1:
 * mov res, tVal
 * L2:
 *
 * - in the second construct the register allocator will thing that the first definition of res is not used and BRANCHcc may need some temporary register
 * the allocator could then allocate the same register for res and this temporary
 */
public class ConvertConditionalPhase extends Phase {

    @Override
    protected void run(Graph graph) {
        IdentifyBlocksPhase schedule = null;
        for (Conditional conditional  : graph.getNodes(Conditional.class)) {
            BooleanNode condition = conditional.condition();
            while (condition instanceof NegateBooleanNode) {
                condition = ((NegateBooleanNode) condition).value();
            }
            if (!(condition instanceof Compare || condition instanceof IsNonNull || condition instanceof NegateBooleanNode || condition instanceof Constant)) {
                If ifNode = new If(conditional.condition(), 0.5, graph);
                EndNode trueEnd = new EndNode(graph);
                EndNode falseEnd = new EndNode(graph);
                ifNode.setTrueSuccessor(trueEnd);
                ifNode.setFalseSuccessor(falseEnd);
                Merge merge = new Merge(graph);
                merge.addEnd(trueEnd);
                merge.addEnd(falseEnd);
                Phi phi = new Phi(conditional.kind, merge, PhiType.Value, graph);
                phi.addInput(conditional.trueValue());
                phi.addInput(conditional.falseValue());
                //recreate framestate
                FrameState stateDuring = conditional.stateDuring();
                FrameStateBuilder builder = new FrameStateBuilder(stateDuring);
                builder.push(phi.kind, phi);
                merge.setStateAfter(builder.create(stateDuring.bci));
                // schedule the if...
                if (schedule == null) {
                    schedule = new IdentifyBlocksPhase(false, false);
                    schedule.apply(graph);
                }
                schedule.assignBlockToNode(conditional);
                Block block = schedule.getNodeToBlock().get(conditional);
                FixedNodeWithNext prev;
                Node firstNode = block.firstNode();
                if (firstNode instanceof Merge) {
                    prev = (Merge) firstNode;
                } else if (firstNode instanceof EndNode) {
                    EndNode end = (EndNode) firstNode;
                    Node pred = end.predecessor();
                    Anchor anchor = new Anchor(graph);
                    pred.replaceFirstSuccessor(end, anchor);
                    anchor.setNext(end);
                    prev = anchor;
                } else if (firstNode instanceof StartNode) {
                    StartNode start = (StartNode) firstNode;
                    Anchor anchor = new Anchor(graph);
                    Node next = start.next();
                    start.setNext(null);
                    anchor.setNext((FixedNode) next);
                    start.setNext(anchor);
                    prev = anchor;
                } else if (firstNode instanceof If) {
                    Node pred = firstNode.predecessor();
                    Anchor anchor = new Anchor(graph);
                    pred.replaceFirstSuccessor(firstNode, anchor);
                    anchor.setNext((If) firstNode);
                    prev = anchor;
                } else {
                    prev = (FixedNodeWithNext) firstNode;
                }
                FixedNode next = prev.next();
                prev.setNext(null);
                merge.setNext(next);
                prev.setNext(ifNode);
                conditional.replaceAndDelete(phi);
            } else {
                FrameState stateDuring = conditional.stateDuring();
                conditional.setStateDuring(null);
                if (stateDuring != null && stateDuring.usages().size() == 0) {
                    stateDuring.delete();
                }
            }
        }
    }
}
