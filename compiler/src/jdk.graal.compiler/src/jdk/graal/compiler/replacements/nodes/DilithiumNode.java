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
import static jdk.graal.compiler.nodeinfo.InputType.Memory;

import java.util.EnumSet;

import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.StampFactory;
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

@NodeInfo(allowedUsageTypes = Memory, cycles = NodeCycles.CYCLES_1024, size = NodeSize.SIZE_1024)
public abstract class DilithiumNode extends MemoryKillStubIntrinsicNode {

    public static final NodeClass<DilithiumNode> TYPE = NodeClass.create(DilithiumNode.class);

    public static final LocationIdentity[] KILLED_LOCATIONS = {NamedLocationIdentity.getArrayLocation(JavaKind.Int)};

    protected static ForeignCallDescriptor foreignCallDescriptor(String name, Class<?>... args) {
        return new ForeignCallDescriptor(name, int.class, args, HAS_SIDE_EFFECT, KILLED_LOCATIONS, false, false);
    }

    public static EnumSet<AMD64.CPUFeature> minFeaturesAMD64() {
        return EnumSet.of(AMD64.CPUFeature.AVX, AMD64.CPUFeature.AVX2, AMD64.CPUFeature.AVX512F, AMD64.CPUFeature.AVX512BW);
    }

    public static boolean isSupported(Architecture arch) {
        if (arch instanceof AMD64 amd64) {
            return amd64.getFeatures().containsAll(minFeaturesAMD64());
        }
        return arch instanceof AArch64;
    }

    protected DilithiumNode(NodeClass<? extends DilithiumNode> c, EnumSet<?> runtimeCheckedCPUFeatures) {
        super(c, StampFactory.intValue(), runtimeCheckedCPUFeatures, LocationIdentity.any());
    }

    @Override
    public LocationIdentity[] getKilledLocationIdentities() {
        return KILLED_LOCATIONS;
    }

    @NodeInfo(allowedUsageTypes = Memory, cycles = NodeCycles.CYCLES_1024, size = NodeSize.SIZE_1024)
    public static final class DilithiumAlmostNttNode extends DilithiumNode {
        public static final NodeClass<DilithiumAlmostNttNode> TYPE = NodeClass.create(DilithiumAlmostNttNode.class);
        public static final ForeignCallDescriptor STUB = foreignCallDescriptor("dilithiumAlmostNtt", Pointer.class, Pointer.class);

        @Input private ValueNode coeffs;
        @Input private ValueNode zetas;

        public DilithiumAlmostNttNode(ValueNode coeffs, ValueNode zetas) {
            this(coeffs, zetas, null);
        }

        public DilithiumAlmostNttNode(ValueNode coeffs, ValueNode zetas, EnumSet<?> runtimeCheckedCPUFeatures) {
            super(TYPE, runtimeCheckedCPUFeatures);
            this.coeffs = coeffs;
            this.zetas = zetas;
        }

        @Override
        public ValueNode[] getForeignCallArguments() {
            return new ValueNode[]{coeffs, zetas};
        }

        @Override
        public ForeignCallDescriptor getForeignCallDescriptor() {
            return STUB;
        }

        @Override
        public void emitIntrinsic(NodeLIRBuilderTool gen) {
            gen.setResult(this, gen.getLIRGeneratorTool().emitDilithiumAlmostNtt(gen.operand(coeffs), gen.operand(zetas)));
        }

        @NodeIntrinsic
        @GenerateStub(name = "dilithiumAlmostNtt", minimumCPUFeaturesAMD64 = "minFeaturesAMD64")
        public static native int dilithiumAlmostNtt(Pointer coeffs, Pointer zetas);

        @NodeIntrinsic
        public static native int dilithiumAlmostNtt(Pointer coeffs, Pointer zetas, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
    }

    @NodeInfo(allowedUsageTypes = Memory, cycles = NodeCycles.CYCLES_1024, size = NodeSize.SIZE_1024)
    public static final class DilithiumAlmostInverseNttNode extends DilithiumNode {
        public static final NodeClass<DilithiumAlmostInverseNttNode> TYPE = NodeClass.create(DilithiumAlmostInverseNttNode.class);
        public static final ForeignCallDescriptor STUB = foreignCallDescriptor("dilithiumAlmostInverseNtt", Pointer.class, Pointer.class);

        @Input private ValueNode coeffs;
        @Input private ValueNode zetas;

        public DilithiumAlmostInverseNttNode(ValueNode coeffs, ValueNode zetas) {
            this(coeffs, zetas, null);
        }

        public DilithiumAlmostInverseNttNode(ValueNode coeffs, ValueNode zetas, EnumSet<?> runtimeCheckedCPUFeatures) {
            super(TYPE, runtimeCheckedCPUFeatures);
            this.coeffs = coeffs;
            this.zetas = zetas;
        }

        @Override
        public ValueNode[] getForeignCallArguments() {
            return new ValueNode[]{coeffs, zetas};
        }

        @Override
        public ForeignCallDescriptor getForeignCallDescriptor() {
            return STUB;
        }

        @Override
        public void emitIntrinsic(NodeLIRBuilderTool gen) {
            gen.setResult(this, gen.getLIRGeneratorTool().emitDilithiumAlmostInverseNtt(gen.operand(coeffs), gen.operand(zetas)));
        }

        @NodeIntrinsic
        @GenerateStub(name = "dilithiumAlmostInverseNtt", minimumCPUFeaturesAMD64 = "minFeaturesAMD64")
        public static native int dilithiumAlmostInverseNtt(Pointer coeffs, Pointer zetas);

        @NodeIntrinsic
        public static native int dilithiumAlmostInverseNtt(Pointer coeffs, Pointer zetas, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
    }

    @NodeInfo(allowedUsageTypes = Memory, cycles = NodeCycles.CYCLES_1024, size = NodeSize.SIZE_1024)
    public static final class DilithiumNttMultNode extends DilithiumNode {
        public static final NodeClass<DilithiumNttMultNode> TYPE = NodeClass.create(DilithiumNttMultNode.class);
        public static final ForeignCallDescriptor STUB = foreignCallDescriptor("dilithiumNttMult", Pointer.class, Pointer.class, Pointer.class);

        @Input private ValueNode product;
        @Input private ValueNode coeffs1;
        @Input private ValueNode coeffs2;

        public DilithiumNttMultNode(ValueNode product, ValueNode coeffs1, ValueNode coeffs2) {
            this(product, coeffs1, coeffs2, null);
        }

        public DilithiumNttMultNode(ValueNode product, ValueNode coeffs1, ValueNode coeffs2, EnumSet<?> runtimeCheckedCPUFeatures) {
            super(TYPE, runtimeCheckedCPUFeatures);
            this.product = product;
            this.coeffs1 = coeffs1;
            this.coeffs2 = coeffs2;
        }

        @Override
        public ValueNode[] getForeignCallArguments() {
            return new ValueNode[]{product, coeffs1, coeffs2};
        }

        @Override
        public ForeignCallDescriptor getForeignCallDescriptor() {
            return STUB;
        }

        @Override
        public void emitIntrinsic(NodeLIRBuilderTool gen) {
            gen.setResult(this, gen.getLIRGeneratorTool().emitDilithiumNttMult(gen.operand(product), gen.operand(coeffs1), gen.operand(coeffs2)));
        }

        @NodeIntrinsic
        @GenerateStub(name = "dilithiumNttMult", minimumCPUFeaturesAMD64 = "minFeaturesAMD64")
        public static native int dilithiumNttMult(Pointer product, Pointer coeffs1, Pointer coeffs2);

        @NodeIntrinsic
        public static native int dilithiumNttMult(Pointer product, Pointer coeffs1, Pointer coeffs2, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
    }

    @NodeInfo(allowedUsageTypes = Memory, cycles = NodeCycles.CYCLES_1024, size = NodeSize.SIZE_1024)
    public static final class DilithiumMontMulByConstantNode extends DilithiumNode {
        public static final NodeClass<DilithiumMontMulByConstantNode> TYPE = NodeClass.create(DilithiumMontMulByConstantNode.class);
        public static final ForeignCallDescriptor STUB = foreignCallDescriptor("dilithiumMontMulByConstant", Pointer.class, int.class);

        @Input private ValueNode coeffs;
        @Input private ValueNode constant;

        public DilithiumMontMulByConstantNode(ValueNode coeffs, ValueNode constant) {
            this(coeffs, constant, null);
        }

        public DilithiumMontMulByConstantNode(ValueNode coeffs, ValueNode constant, EnumSet<?> runtimeCheckedCPUFeatures) {
            super(TYPE, runtimeCheckedCPUFeatures);
            this.coeffs = coeffs;
            this.constant = constant;
        }

        @Override
        public ValueNode[] getForeignCallArguments() {
            return new ValueNode[]{coeffs, constant};
        }

        @Override
        public ForeignCallDescriptor getForeignCallDescriptor() {
            return STUB;
        }

        @Override
        public void emitIntrinsic(NodeLIRBuilderTool gen) {
            gen.setResult(this, gen.getLIRGeneratorTool().emitDilithiumMontMulByConstant(gen.operand(coeffs), gen.operand(constant)));
        }

        @NodeIntrinsic
        @GenerateStub(name = "dilithiumMontMulByConstant", minimumCPUFeaturesAMD64 = "minFeaturesAMD64")
        public static native int dilithiumMontMulByConstant(Pointer coeffs, int constant);

        @NodeIntrinsic
        public static native int dilithiumMontMulByConstant(Pointer coeffs, int constant, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
    }

    @NodeInfo(allowedUsageTypes = Memory, cycles = NodeCycles.CYCLES_1024, size = NodeSize.SIZE_1024)
    public static final class DilithiumDecomposePolyNode extends DilithiumNode {
        public static final NodeClass<DilithiumDecomposePolyNode> TYPE = NodeClass.create(DilithiumDecomposePolyNode.class);
        public static final ForeignCallDescriptor STUB = foreignCallDescriptor("dilithiumDecomposePoly", Pointer.class, Pointer.class, Pointer.class, int.class, int.class);

        @Input private ValueNode input;
        @Input private ValueNode lowPart;
        @Input private ValueNode highPart;
        @Input private ValueNode twoGamma2;
        @Input private ValueNode multiplier;

        public DilithiumDecomposePolyNode(ValueNode input, ValueNode lowPart, ValueNode highPart, ValueNode twoGamma2, ValueNode multiplier) {
            this(input, lowPart, highPart, twoGamma2, multiplier, null);
        }

        public DilithiumDecomposePolyNode(ValueNode input, ValueNode lowPart, ValueNode highPart, ValueNode twoGamma2, ValueNode multiplier, EnumSet<?> runtimeCheckedCPUFeatures) {
            super(TYPE, runtimeCheckedCPUFeatures);
            this.input = input;
            this.lowPart = lowPart;
            this.highPart = highPart;
            this.twoGamma2 = twoGamma2;
            this.multiplier = multiplier;
        }

        @Override
        public ValueNode[] getForeignCallArguments() {
            return new ValueNode[]{input, lowPart, highPart, twoGamma2, multiplier};
        }

        @Override
        public ForeignCallDescriptor getForeignCallDescriptor() {
            return STUB;
        }

        @Override
        public void emitIntrinsic(NodeLIRBuilderTool gen) {
            gen.setResult(this, gen.getLIRGeneratorTool().emitDilithiumDecomposePoly(gen.operand(input), gen.operand(lowPart), gen.operand(highPart),
                            gen.operand(twoGamma2), gen.operand(multiplier)));
        }

        @NodeIntrinsic
        @GenerateStub(name = "dilithiumDecomposePoly", minimumCPUFeaturesAMD64 = "minFeaturesAMD64")
        public static native int dilithiumDecomposePoly(Pointer input, Pointer lowPart, Pointer highPart, int twoGamma2, int multiplier);

        @NodeIntrinsic
        public static native int dilithiumDecomposePoly(Pointer input, Pointer lowPart, Pointer highPart, int twoGamma2, int multiplier,
                        @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
    }
}
