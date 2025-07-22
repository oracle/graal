/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.calc;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.ArithmeticLIRLowerable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaKind;

import java.util.Objects;

@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public final class FloatTypeTestNode extends UnaryNode implements ArithmeticLIRLowerable {
    public static final NodeClass<FloatTypeTestNode> TYPE = NodeClass.create(FloatTypeTestNode.class);

    public enum FloatTypeTestOp {
        IS_INFINITE,
        IS_FINITE,
    }

    private final FloatTypeTestOp op;

    public FloatTypeTestNode(ValueNode value, FloatTypeTestOp op) {
        super(TYPE, StampFactory.forKind(JavaKind.Boolean), value);
        GraalError.guarantee(value.getStackKind().isNumericFloat(), "float type test on incompatible value %s", value);

        this.op = op;
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forValue) {
        switch (forValue.getStackKind()) {
            case Float:
                if (forValue.isJavaConstant()) {
                    return switch (op) {
                        case IS_FINITE -> ConstantNode.forBoolean(Float.isFinite(forValue.asJavaConstant().asFloat()));
                        case IS_INFINITE -> ConstantNode.forBoolean(Float.isInfinite(forValue.asJavaConstant().asFloat()));
                    };
                }
                break;
            case Double:
                if (forValue.isJavaConstant()) {
                    return switch (op) {
                        case IS_FINITE -> ConstantNode.forBoolean(Double.isFinite(forValue.asJavaConstant().asDouble()));
                        case IS_INFINITE -> ConstantNode.forBoolean(Double.isInfinite(forValue.asJavaConstant().asDouble()));
                    };
                }
                break;
            default:
                throw GraalError.shouldNotReachHere("incompatible value " + forValue);
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        if (Objects.requireNonNull(op) == FloatTypeTestOp.IS_INFINITE) {
            nodeValueMap.setResult(this, gen.emitFloatIsInfinite(nodeValueMap.operand(getValue())));
        } else {
            throw GraalError.shouldNotReachHere("unimplemented float type test op " + op);
        }
    }

    @SuppressWarnings("unlikely-arg-type")
    public static boolean isSupported(Architecture arch) {
        if (arch instanceof AMD64 amd64) {
            return amd64.getFeatures().contains(AMD64.CPUFeature.AVX512DQ);
        }
        return false;
    }
}
