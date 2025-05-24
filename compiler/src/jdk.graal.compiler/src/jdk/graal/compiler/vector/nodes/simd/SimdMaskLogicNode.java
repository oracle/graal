/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.nodes.simd;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.NotNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;

/**
 * Test whether the input SIMD mask value is all-ones or all-zeros. This node assumes that each
 * element of the SIMD value is all-ones or all-zeros, so sampling a suitable set of bits from the
 * SIMD mask will yield the correct result.
 */
@NodeInfo(cycles = CYCLES_UNKNOWN, size = SIZE_UNKNOWN, nameTemplate = "SimdMaskLogic {p#condition/s}")
public class SimdMaskLogicNode extends LogicNode implements Canonicalizable {
    public static final NodeClass<SimdMaskLogicNode> TYPE = NodeClass.create(SimdMaskLogicNode.class);

    @Input ValueNode value;
    Condition condition;

    public enum Condition {
        /** Succeed if all elements of the input value are all-ones. */
        ALL_ONES,
        /** Succeed if all elements of the input value are all-zeros. */
        ALL_ZEROS
    }

    public SimdMaskLogicNode(ValueNode value, Condition condition) {
        super(TYPE);
        this.value = value;
        this.condition = condition;
    }

    public ValueNode getValue() {
        return value;
    }

    public Condition getCondition() {
        return condition;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        /*
         * Canonicalize away any negations in the input vector: all_ones (not x) -> all_zeros x,
         * all_zeros (not x) -> all_ones x
         */
        boolean negate = false;
        ValueNode v = getValue();
        while (v instanceof NotNode vectorNegation) {
            negate = !negate;
            v = vectorNegation.getValue();
        }
        if (negate) {
            return new SimdMaskLogicNode(v, condition == Condition.ALL_ZEROS ? Condition.ALL_ONES : Condition.ALL_ZEROS);
        }
        if (value instanceof ConstantNode c && c.getValue() instanceof SimdConstant simdConstant && simdConstant.isAllSame()) {
            boolean isZero = simdConstant.getValue(0).isDefaultForKind();
            return LogicConstantNode.forBoolean(isZero == (condition == Condition.ALL_ZEROS));
        }
        return this;
    }
}
