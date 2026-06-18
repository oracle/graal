/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.HAS_SIDE_EFFECT;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512BW;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512F;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512VL;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512_IFMA;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX_IFMA;

import java.util.EnumSet;

import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.GenerateStub;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaKind;

@NodeInfo(allowedUsageTypes = {InputType.Memory}, cycles = NodeCycles.CYCLES_128, size = NodeSize.SIZE_128)
public class IntegerPolynomialAssignNode extends MemoryKillStubIntrinsicNode {

    public static final NodeClass<IntegerPolynomialAssignNode> TYPE = NodeClass.create(IntegerPolynomialAssignNode.class);
    public static final LocationIdentity[] KILLED_LOCATIONS = {NamedLocationIdentity.getArrayLocation(JavaKind.Long)};

    public static final ForeignCallDescriptor STUB = new ForeignCallDescriptor("intpolyAssign",
                    void.class,
                    new Class<?>[]{int.class, Pointer.class, Pointer.class, int.class},
                    HAS_SIDE_EFFECT,
                    KILLED_LOCATIONS,
                    false,
                    false);

    private final ForeignCallDescriptor foreignCallDescriptor;

    @Input protected ValueNode set;
    @Input protected ValueNode a;
    @Input protected ValueNode b;
    @Input protected ValueNode length;

    public IntegerPolynomialAssignNode(ValueNode set, ValueNode a, ValueNode b, ValueNode length) {
        this(set, a, b, length, null);
    }

    public IntegerPolynomialAssignNode(ValueNode set, ValueNode a, ValueNode b, ValueNode length, EnumSet<?> runtimeCheckedCPUFeatures) {
        this(TYPE, set, a, b, length, runtimeCheckedCPUFeatures, STUB);
    }

    protected IntegerPolynomialAssignNode(NodeClass<? extends IntegerPolynomialAssignNode> type, ValueNode set, ValueNode a, ValueNode b, ValueNode length, EnumSet<?> runtimeCheckedCPUFeatures,
                    ForeignCallDescriptor foreignCallDescriptor) {
        super(type, StampFactory.forVoid(), runtimeCheckedCPUFeatures, LocationIdentity.any());
        this.foreignCallDescriptor = foreignCallDescriptor;
        this.set = set;
        this.a = a;
        this.b = b;
        this.length = length;
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        return new ValueNode[]{set, a, b, length};
    }

    @Override
    public LocationIdentity[] getKilledLocationIdentities() {
        return KILLED_LOCATIONS;
    }

    public static EnumSet<AMD64.CPUFeature> minFeaturesAMD64() {
        return EnumSet.of(AVX, AVX2, AVX_IFMA);
    }

    public static EnumSet<AMD64.CPUFeature> maxFeaturesAMD64() {
        // Preferred runtime-checked feature set. AVX_IFMA and AVX512_IFMA are alternative
        // instruction encodings, so this must not require both feature paths. The AVX512
        // version still emits AVX and AVX2 instructions.
        return EnumSet.of(AVX, AVX2, AVX512F, AVX512BW, AVX512VL, AVX512_IFMA);
    }

    @SuppressWarnings("unlikely-arg-type")
    public static boolean isSupported(Architecture arch) {
        return switch (arch) {
            case AMD64 amd64 -> {
                // SVM uses this static predicate to match the generated stub and foreign-call
                // registration until alternative intrinsic-stub feature sets are modeled explicitly.
                yield amd64.getFeatures().containsAll(maxFeaturesAMD64());
            }
            default -> false;
        };
    }

    @SuppressWarnings("unlikely-arg-type")
    public static boolean isSupportedForRuntimeCheckedStub(Architecture arch) {
        return switch (arch) {
            case AMD64 amd64 -> amd64.getFeatures().containsAll(minFeaturesAMD64()) || amd64.getFeatures().containsAll(maxFeaturesAMD64());
            default -> false;
        };
    }

    @NodeIntrinsic
    @GenerateStub(name = "intpolyAssign", minimumCPUFeaturesAMD64 = "maxFeaturesAMD64")
    public static native void apply(int set, Pointer a, Pointer b, int length);

    @NodeIntrinsic
    public static native void apply(int set, Pointer a, Pointer b, int length, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        return foreignCallDescriptor;
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        gen.getLIRGeneratorTool().emitIntegerPolynomialAssign(gen.operand(set), gen.operand(a), gen.operand(b), gen.operand(length));
    }
}
