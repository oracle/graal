/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.util;

import java.util.List;
import java.util.function.Function;

import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ControlSinkNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;

public class ValueMergeUtil {

    public static ValueNode mergeReturns(AbstractMergeNode merge, List<? extends ReturnNode> returnNodes) {
        return mergeValueProducers(merge, returnNodes, null, returnNode -> returnNode.result());
    }

    public static ValueNode mergeUnwindExceptions(AbstractMergeNode merge, List<? extends UnwindNode> unwindNodes) {
        return mergeValueProducers(merge, unwindNodes, null, UnwindNode::exception);
    }

    public static <T> ValueNode mergeValueProducers(AbstractMergeNode merge, List<? extends T> valueProducers, Function<T, FixedWithNextNode> lastInstrFunction, Function<T, ValueNode> valueFunction) {
        ValueNode singleResult = null;
        PhiNode phiResult = null;
        for (T valueProducer : valueProducers) {
            ValueNode result = valueFunction.apply(valueProducer);
            if (result != null) {
                if (phiResult == null && (singleResult == null || singleResult == result)) {
                    /* Only one result value, so no need yet for a phi node. */
                    singleResult = result;
                } else if (phiResult == null) {
                    /* Found a second result value, so create phi node. */
                    phiResult = merge.graph().addWithoutUnique(new ValuePhiNode(result.stamp(NodeView.DEFAULT).unrestricted(), merge));
                    for (int i = 0; i < merge.forwardEndCount(); i++) {
                        phiResult.addInput(singleResult);
                    }
                    phiResult.addInput(result);

                } else {
                    /* Multiple return values, just add to existing phi node. */
                    phiResult.addInput(result);
                }
            }

            // create and wire up a new EndNode
            EndNode endNode = merge.graph().add(new EndNode());
            merge.addForwardEnd(endNode);
            if (lastInstrFunction == null) {
                assert valueProducer instanceof ReturnNode || valueProducer instanceof UnwindNode : Assertions.errorMessage(valueProducer);
                ((ControlSinkNode) valueProducer).replaceAndDelete(endNode);
            } else {
                FixedWithNextNode lastInstr = lastInstrFunction.apply(valueProducer);
                lastInstr.setNext(endNode);
            }
        }

        if (phiResult != null) {
            assert phiResult.verify();
            phiResult.inferStamp();
            return phiResult;
        } else {
            return singleResult;
        }
    }
}
