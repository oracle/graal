/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.ArithmeticLIRLowerable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaConstant;

/**
 * Signum function of the input.
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public final class SignumNode extends UnaryNode implements ArithmeticLIRLowerable {
    public static final NodeClass<SignumNode> TYPE = NodeClass.create(SignumNode.class);

    public SignumNode(ValueNode x) {
        super(TYPE, computeStamp(x.stamp(NodeView.DEFAULT)), x);
    }

    private static Stamp computeStamp(Stamp stamp) {
        FloatStamp floatStamp = (FloatStamp) stamp;
        if (floatStamp.isNaN()) {
            return floatStamp;
        }
        if (floatStamp.isNonNaN()) {
            if (floatStamp.lowerBound() > 0) {
                return new FloatStamp(floatStamp.getBits(), 1.0D, 1.0D, true);
            }
            if (floatStamp.upperBound() < 0) {
                return new FloatStamp(floatStamp.getBits(), -1.0D, -1.0D, true);
            }
        }
        // Initially make an empty stamp.
        FloatStamp result = new FloatStamp(floatStamp.getBits(), Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, floatStamp.isNonNaN());
        if (floatStamp.contains(0.0d)) {
            // this also covers stamp.contains(-0.0d)
            result = (FloatStamp) result.meet(new FloatStamp(floatStamp.getBits(), 0.0d, 0.0d, true));
        }
        if (floatStamp.upperBound() > 0) {
            result = (FloatStamp) result.meet(new FloatStamp(floatStamp.getBits(), 1.0d, 1.0d, true));
        }
        if (floatStamp.lowerBound() < 0) {
            result = (FloatStamp) result.meet(new FloatStamp(floatStamp.getBits(), -1.0d, -1.0d, true));
        }
        return result;
    }

    @Override
    public Stamp foldStamp(Stamp newStamp) {
        return computeStamp(newStamp);
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (forValue.isJavaConstant()) {
            JavaConstant c = forValue.asJavaConstant();
            switch (c.getJavaKind()) {
                case Float:
                    return ConstantNode.forFloat(Math.signum(c.asFloat()));
                case Double:
                    return ConstantNode.forDouble(Math.signum(c.asDouble()));
                default:
                    throw GraalError.shouldNotReachHereUnexpectedValue(c.getJavaKind()); // ExcludeFromJacocoGeneratedReport
            }
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        nodeValueMap.setResult(this, gen.emitMathSignum(nodeValueMap.operand(getValue())));
    }
}
