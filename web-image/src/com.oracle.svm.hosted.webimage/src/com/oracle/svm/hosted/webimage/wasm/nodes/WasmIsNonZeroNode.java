/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasm.nodes;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.graal.compiler.core.common.type.AbstractPointerStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.UnaryOpLogicNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.TriState;

/**
 * Checks if its input value is not 0.
 * <p>
 * If the value of this node is consumed by a WASM instruction that simply requires a logic value (0
 * for false, non-0 for true), the wrapped value can be lowered directly instead of performing a
 * 0-check. Only used for i32 values since WASM only supports those for logic values.
 */
@NodeInfo(shortName = "!= 0", cycles = CYCLES_UNKNOWN, size = SIZE_1)
public class WasmIsNonZeroNode extends UnaryOpLogicNode {
    public static final NodeClass<WasmIsNonZeroNode> TYPE = NodeClass.create(WasmIsNonZeroNode.class);

    public WasmIsNonZeroNode(ValueNode value) {
        super(TYPE, value);
    }

    @Override
    public Stamp getSucceedingStampForValue(boolean negated) {
        // We cannot provide a stamp since the value may be an integer or a pointer
        return null;
    }

    @Override
    public TriState tryFold(Stamp stampGeneric) {
        if (stampGeneric instanceof IntegerStamp integerStamp) {
            if (integerStamp.getStackKind() == JavaKind.Int) {
                Stamp zeroStamp = StampFactory.forConstant(JavaConstant.forInt(0));
                if (integerStamp.alwaysDistinct(zeroStamp)) {
                    return TriState.TRUE;
                }

                if (integerStamp.neverDistinct(zeroStamp)) {
                    return TriState.FALSE;
                }
            }
        } else if (stampGeneric instanceof AbstractPointerStamp pointerStamp) {
            if (pointerStamp.alwaysNull()) {
                return TriState.FALSE;
            }

            if (pointerStamp.nonNull()) {
                return TriState.TRUE;
            }
        }

        return TriState.UNKNOWN;
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forValue) {
        return switch (tryFold(forValue.stamp(NodeView.DEFAULT))) {
            case TRUE -> LogicConstantNode.tautology();
            case FALSE -> LogicConstantNode.contradiction();
            case UNKNOWN -> this;
        };
    }
}
