/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_1;

import jdk.graal.compiler.core.common.calc.FloatConvert;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool.RoundingMode;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.ArithmeticLIRLowerable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Round floating-point value.
 */
@NodeInfo(cycles = CYCLES_1)
public final class RoundNode extends UnaryNode implements ArithmeticLIRLowerable {

    public static final NodeClass<RoundNode> TYPE = NodeClass.create(RoundNode.class);

    private final RoundingMode mode;

    public RoundNode(ValueNode value, RoundingMode mode) {
        super(TYPE, roundStamp((FloatStamp) value.stamp(NodeView.DEFAULT), mode), value);
        this.mode = mode;
    }

    public static ValueNode create(ValueNode input, RoundingMode mode) {
        ValueNode folded = tryFold(input, mode);
        if (folded != null) {
            return folded;
        }
        return new RoundNode(input, mode);
    }

    public RoundingMode mode() {
        return mode;
    }

    private static double round(RoundingMode mode, double input) {
        return switch (mode) {
            case DOWN -> Math.floor(input);
            case NEAREST -> Math.rint(input);
            case UP -> Math.ceil(input);
            case TRUNCATE -> input < 0.0 ? Math.ceil(input) : Math.floor(input);
        };
    }

    private static FloatStamp roundStamp(FloatStamp stamp, RoundingMode mode) {
        if (stamp.isEmpty()) {
            return stamp;
        }

        double min = stamp.lowerBound();
        min = Math.min(min, round(mode, min));

        double max = stamp.upperBound();
        max = Math.max(max, round(mode, max));

        return FloatStamp.create(stamp.getBits(), min, max, stamp.isNonNaN());
    }

    @Override
    public Stamp foldStamp(Stamp newStamp) {
        assert newStamp.isCompatible(getValue().stamp(NodeView.DEFAULT));
        return roundStamp((FloatStamp) newStamp, mode);
    }

    public static ValueNode tryFold(ValueNode input, RoundingMode mode) {
        if (input.isConstant()) {
            JavaConstant c = input.asJavaConstant();
            if (c.getJavaKind() == JavaKind.Double) {
                return ConstantNode.forDouble(round(mode, c.asDouble()));
            } else if (c.getJavaKind() == JavaKind.Float) {
                return ConstantNode.forFloat((float) round(mode, c.asFloat()));
            }
        }
        return null;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        ValueNode folded = tryFold(forValue, mode);
        if (folded != null) {
            return folded;
        }
        /*
         * Try to replace F2D->RoundD->D2F with RoundF. First replace F2D->RoundD with RoundF->F2D,
         * then rely on further FloatConvertNode canonicalization to eliminate F2D->D2F, if any.
         */
        if (forValue instanceof FloatConvertNode convert && convert.getFloatConvert() == FloatConvert.F2D) {
            return FloatConvertNode.create(FloatConvert.F2D, RoundNode.create(convert.getValue(), mode), NodeView.from(tool));
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool gen) {
        builder.setResult(this, gen.emitRound(builder.operand(getValue()), mode));
    }

    @SuppressWarnings("unlikely-arg-type")
    public static boolean isSupported(Architecture arch) {
        return switch (arch) {
            case AMD64 amd64 -> amd64.getFeatures().contains(AMD64.CPUFeature.SSE4_1);
            case AArch64 aarch64 -> true;
            default -> false;
        };
    }
}
