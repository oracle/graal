/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.loop;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.BinaryArithmeticNode;
import org.graalvm.compiler.nodes.calc.SubNode;

public class InductionVariableHelper {

    public static InductionVariable previousIteration(InductionVariable start) {
        if (start instanceof BasicInductionVariable) {
            return previousIterationBasic((BasicInductionVariable) start);
        } else {
            return previousIterationDerived((DerivedInductionVariable) start);
        }
    }

    private static InductionVariable previousIterationBasic(BasicInductionVariable start) {
        BinaryArithmeticNode<?> previousOp;
        if (start.getOp() instanceof AddNode) {
            previousOp = start.graph().unique(new SubNode(start.valueNode(), start.rawStride()));
        } else if (start.getOp() instanceof SubNode) {
            previousOp = start.graph().unique(new AddNode(start.valueNode(), start.rawStride()));
        } else {
            throw GraalError.shouldNotReachHere();
        }
        InductionVariable previousIv = start.getLoop().getInductionVariables().get(previousOp);
        if (previousIv == null) {
            // @formatter:off
            /*
             * We use rawStride here and make the decision about the operation to the base iv above
             * (add/sub): this means that in the following example
             *
             *      base iv: for (int i = 0; i < x.length; i++)
             *      previousIv to construct i - 1;
             *
             *  The following derived iv cases are semantically equivalent (we use the first one)
             *      value = previousOp = SubNode, offset = positive 1
             *      value = previousOp = AddNode, offset = negative 1
             */
            // @formatter:on
            previousIv = new DerivedOffsetInductionVariable(start.getLoop(), start, start.rawStride(), previousOp);
            start.getLoop().getInductionVariables().put(previousOp, previousIv);
        }
        return previousIv;
    }

    private static InductionVariable previousIterationDerived(DerivedInductionVariable start) {
        InductionVariable previousBase = previousIteration(start.getBase());
        ValueNode previousValue = start.copyValue(previousBase);
        InductionVariable previousIv = start.getLoop().getInductionVariables().get(previousValue);
        if (previousIv == null) {
            previousIv = start.copy(previousBase, previousValue);
            start.getLoop().getInductionVariables().put(previousValue, previousIv);
        }
        return previousIv;
    }

    public static InductionVariable nextIteration(InductionVariable start) {
        if (start instanceof BasicInductionVariable) {
            return nextIteration((BasicInductionVariable) start);
        } else {
            return nextIteration((DerivedInductionVariable) start);
        }
    }

    static InductionVariable nextIteration(BasicInductionVariable start) {
        BinaryArithmeticNode<?> nextOp;
        if (start.getOp() instanceof AddNode) {
            nextOp = start.graph().unique(new AddNode(start.getOp(), start.rawStride()));
        } else if (start.getOp() instanceof SubNode) {
            nextOp = start.graph().unique(new SubNode(start.getOp(), start.rawStride()));
        } else {
            throw GraalError.shouldNotReachHere();
        }
        InductionVariable nextIv = start.getLoop().getInductionVariables().get(nextOp);
        if (nextIv == null) {
            nextIv = new DerivedOffsetInductionVariable(start.getLoop(), start, start.rawStride(), nextOp);
            start.getLoop().getInductionVariables().put(nextOp, nextIv);
        }
        return nextIv;
    }

    static InductionVariable nextIteration(DerivedInductionVariable start) {
        InductionVariable nextBase = nextIteration(start.getBase());
        ValueNode nextValue = start.copyValue(nextBase);
        InductionVariable previousIv = start.getLoop().getInductionVariables().get(nextValue);
        if (previousIv == null) {
            previousIv = start.copy(nextBase, nextValue);
            start.getLoop().getInductionVariables().put(nextValue, previousIv);
        }
        return previousIv;
    }
}
