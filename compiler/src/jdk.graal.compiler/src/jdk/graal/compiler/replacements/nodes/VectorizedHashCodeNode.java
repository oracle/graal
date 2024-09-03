/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE3;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE4_1;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE4_2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSSE3;

import java.util.EnumSet;

import org.graalvm.word.Pointer;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.GenerateStub;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaKind;

/**
 * Calculate the hash code for an array. Intrinsification for
 * {@code jdk.internal.util.ArraySupport.vectorizedHashCode}.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_256)
public final class VectorizedHashCodeNode extends PureFunctionStubIntrinsicNode {
    public static final NodeClass<VectorizedHashCodeNode> TYPE = NodeClass.create(VectorizedHashCodeNode.class);

    public static final ForeignCallDescriptor STUB_BOOLEAN = ForeignCalls.pureFunctionForeignCallDescriptor("vectorizedHashCodeBoolean",
                    int.class, Pointer.class, int.class, int.class);
    public static final ForeignCallDescriptor STUB_CHAR = ForeignCalls.pureFunctionForeignCallDescriptor("vectorizedHashCodeChar",
                    int.class, Pointer.class, int.class, int.class);
    public static final ForeignCallDescriptor STUB_BYTE = ForeignCalls.pureFunctionForeignCallDescriptor("vectorizedHashCodeByte",
                    int.class, Pointer.class, int.class, int.class);
    public static final ForeignCallDescriptor STUB_SHORT = ForeignCalls.pureFunctionForeignCallDescriptor("vectorizedHashCodeShort",
                    int.class, Pointer.class, int.class, int.class);
    public static final ForeignCallDescriptor STUB_INT = ForeignCalls.pureFunctionForeignCallDescriptor("vectorizedHashCodeInt",
                    int.class, Pointer.class, int.class, int.class);

    public static final ForeignCallDescriptor[] STUBS = {STUB_BOOLEAN, STUB_CHAR, STUB_BYTE, STUB_SHORT, STUB_INT};

    @Input protected ValueNode arrayStart;
    @Input protected ValueNode length;
    @Input protected ValueNode initialValue;

    private final JavaKind arrayKind;

    public VectorizedHashCodeNode(ValueNode arrayStart, ValueNode length, ValueNode initialValue, JavaKind arrayKind) {
        this(arrayStart, length, initialValue, arrayKind, null);
    }

    public VectorizedHashCodeNode(ValueNode arrayStart, ValueNode length, ValueNode initialValue, JavaKind arrayKind, EnumSet<?> runtimeCheckedCPUFeatures) {
        super(TYPE, StampFactory.intValue(), runtimeCheckedCPUFeatures, NamedLocationIdentity.getArrayLocation(arrayKind));

        this.arrayStart = arrayStart;
        this.length = length;
        this.initialValue = initialValue;
        this.arrayKind = arrayKind;
    }

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        return switch (arrayKind) {
            case Boolean -> STUB_BOOLEAN;
            case Char -> STUB_CHAR;
            case Byte -> STUB_BYTE;
            case Short -> STUB_SHORT;
            case Int -> STUB_INT;
            default -> throw GraalError.shouldNotReachHere("Unsupported JavaKind " + arrayKind);
        };
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        return new ValueNode[]{arrayStart, length, initialValue};
    }

    public static EnumSet<AMD64.CPUFeature> minFeaturesAMD64() {
        return EnumSet.of(SSE2, SSE3, SSSE3, SSE4_1, SSE4_2, AVX, AVX2);
    }

    @SuppressWarnings("unlikely-arg-type")
    public static boolean isSupported(Architecture arch) {
        if (arch instanceof AMD64) {
            return ((AMD64) arch).getFeatures().containsAll(minFeaturesAMD64());
        } else if (arch instanceof AArch64) {
            return true;
        }
        return false;
    }

    @Override
    public boolean canBeEmitted(Architecture arch) {
        return isSupported(arch);
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        gen.setResult(this, gen.getLIRGeneratorTool().emitVectorizedHashCode(runtimeCheckedCPUFeatures, gen.operand(arrayStart), gen.operand(length), gen.operand(initialValue), arrayKind));
    }

    @NodeIntrinsic
    @GenerateStub(name = "vectorizedHashCodeBoolean", parameters = "Boolean", minimumCPUFeaturesAMD64 = "minFeaturesAMD64")
    @GenerateStub(name = "vectorizedHashCodeChar", parameters = "Char", minimumCPUFeaturesAMD64 = "minFeaturesAMD64")
    @GenerateStub(name = "vectorizedHashCodeByte", parameters = "Byte", minimumCPUFeaturesAMD64 = "minFeaturesAMD64")
    @GenerateStub(name = "vectorizedHashCodeShort", parameters = "Short", minimumCPUFeaturesAMD64 = "minFeaturesAMD64")
    @GenerateStub(name = "vectorizedHashCodeInt", parameters = "Int", minimumCPUFeaturesAMD64 = "minFeaturesAMD64")
    public static native int vectorizedHashCode(Pointer arrayStart, int length, int initialValue, @ConstantNodeParameter JavaKind arrayKind);

    @NodeIntrinsic
    public static native int vectorizedHashCode(Pointer arrayStart, int length, int initialValue, @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
}
