/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.nodes;

import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.POPCNT;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE3;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE4_1;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE4_2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSSE3;

import java.util.EnumSet;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValueNodeInterface;
import jdk.graal.compiler.nodes.spi.DelegatingNodeLIRBuilderTool;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

/**
 * Mixin for nodes that represent an entire custom assembly method. These nodes can either emit the
 * method body or a foreign call to a stub containing the method body.
 *
 * On targets using CastValue narrows, {@link #prepareArguments} zero-extends nonconstant int
 * arguments before generating the intrinsic. These arguments have an int LIR kind, but assembly
 * implementations may use the whole register.
 */
public interface IntrinsicMethodNodeInterface extends ValueNodeInterface, LIRLowerable {

    StructuredGraph graph();

    ForeignCallDescriptor getForeignCallDescriptor();

    ValueNode[] getForeignCallArguments();

    /**
     * Whether assembly for {@code arch} may depend on the upper bits of int arguments being zero.
     * Implementations may return {@code false} if both their assembly and stub calls accept arbitrary
     * upper bits for every nonconstant argument with stack kind {@link JavaKind#Int}.
     */
    @SuppressWarnings("unused")
    default boolean needsExplicitZeroExtension(Architecture arch) {
        return true;
    }

    @Override
    default void generate(NodeLIRBuilderTool originalGen) {
        NodeLIRBuilderTool gen = prepareArguments(originalGen);
        ForeignCallDescriptor foreignCallDescriptor = getForeignCallDescriptor();
        ForeignCallLinkage linkage = gen.lookupGraalStub(asNode(), foreignCallDescriptor);
        if (linkage != null) {
            ValueNode[] args = getForeignCallArguments();
            Value[] operands = new Value[args.length];
            for (int i = 0; i < args.length; i++) {
                operands[i] = gen.operand(args[i]);
            }
            Value result = gen.getLIRGeneratorTool().emitForeignCall(linkage, null, operands);
            if (foreignCallDescriptor.getResultType() != void.class) {
                gen.setResult(asNode(), result);
            }
            return;
        }
        emitIntrinsic(gen);
    }

    /**
     * Prepares nonconstant int arguments with explicit zero-extension followed by narrowing to int.
     * Ordinary moves can disappear during allocation. Repeated arguments share one zero-extension;
     * constants allow specialization. Returns a builder whose operand lookup supplies the prepared
     * arguments to assembly emission and stub calls without changing graph inputs or operands in
     * the original builder.
     */
    default NodeLIRBuilderTool prepareArguments(NodeLIRBuilderTool gen) {
        if (!gen.narrowsUseCastValue() || !needsExplicitZeroExtension(gen.getLIRGeneratorTool().target().arch)) {
            return gen;
        }
        ValueNode[] arguments = getForeignCallArguments();
        Class<?>[] types = getForeignCallDescriptor().getArgumentTypes();
        GraalError.guarantee(arguments.length == types.length, "Intrinsic arguments must match the descriptor");
        EconomicMap<Node, Value> extendedArguments = null;
        for (int i = 0; i < arguments.length; i++) {
            ValueNode argument = arguments[i];
            if (JavaKind.fromJavaClass(types[i]).getStackKind() != JavaKind.Int || argument.isJavaConstant()) {
                continue;
            }
            if (extendedArguments == null) {
                extendedArguments = EconomicMap.create();
            } else if (extendedArguments.containsKey(argument)) {
                continue;
            }
            GraalError.guarantee(argument.stamp(NodeView.DEFAULT) instanceof IntegerStamp stamp && stamp.getBits() == 32, "Intrinsic int arguments must have a 32-bit stamp");
            Value extended = gen.getLIRGeneratorTool().getArithmetic().emitZeroExtend(gen.operand(argument), 32, 64, true, true);
            extendedArguments.put(argument, gen.getLIRGeneratorTool().getArithmetic().emitNarrow(extended, 32));
        }
        if (extendedArguments == null) {
            return gen;
        }
        EconomicMap<Node, Value> preparedArguments = extendedArguments;
        return new DelegatingNodeLIRBuilderTool(gen) {
            @Override
            public Value operand(Node node) {
                Value operand = preparedArguments.get(node);
                return operand == null ? super.operand(node) : operand;
            }
        };
    }

    /**
     * Emit the method body.
     */
    void emitIntrinsic(NodeLIRBuilderTool gen);

    static EnumSet<AMD64.CPUFeature> amd64FeaturesAVX2() {
        return EnumSet.of(SSE2, SSE3, SSSE3, SSE4_1, SSE4_2, POPCNT, AVX, AVX2);
    }
}
