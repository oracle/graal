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
public abstract class KyberNode extends MemoryKillStubIntrinsicNode {

    public static final NodeClass<KyberNode> TYPE = NodeClass.create(KyberNode.class);

    public static final LocationIdentity[] KILLED_SHORT_LOCATIONS = {NamedLocationIdentity.getArrayLocation(JavaKind.Short)};
    public static final LocationIdentity[] KILLED_BYTE_SHORT_LOCATIONS = {
                    NamedLocationIdentity.getArrayLocation(JavaKind.Byte),
                    NamedLocationIdentity.getArrayLocation(JavaKind.Short)};

    protected static ForeignCallDescriptor foreignCallDescriptor(String name, LocationIdentity[] killedLocations, Class<?>... args) {
        return new ForeignCallDescriptor(name, int.class, args, HAS_SIDE_EFFECT, killedLocations, false, false);
    }

    protected static ForeignCallDescriptor foreignCallDescriptor(String name, Class<?>... args) {
        return foreignCallDescriptor(name, KILLED_SHORT_LOCATIONS, args);
    }

    public static EnumSet<AMD64.CPUFeature> minFeaturesAMD64() {
        return EnumSet.of(AMD64.CPUFeature.AVX, AMD64.CPUFeature.AVX2, AMD64.CPUFeature.AVX512F, AMD64.CPUFeature.AVX512BW, AMD64.CPUFeature.AVX512VL);
    }

    public static boolean isSupported(Architecture arch) {
        if (arch instanceof AMD64 amd64) {
            return amd64.getFeatures().containsAll(minFeaturesAMD64());
        }
        if (arch instanceof AArch64) {
            return true;
        }
        return false;
    }

    private final LocationIdentity[] killedLocations;

    protected KyberNode(NodeClass<? extends KyberNode> c, EnumSet<?> runtimeCheckedCPUFeatures, LocationIdentity[] killedLocations) {
        super(c, StampFactory.intValue(), runtimeCheckedCPUFeatures, LocationIdentity.any());
        this.killedLocations = killedLocations;
    }

    protected KyberNode(NodeClass<? extends KyberNode> c, EnumSet<?> runtimeCheckedCPUFeatures) {
        this(c, runtimeCheckedCPUFeatures, KILLED_SHORT_LOCATIONS);
    }

    @Override
    public LocationIdentity[] getKilledLocationIdentities() {
        return killedLocations;
    }

    @NodeInfo(allowedUsageTypes = Memory, cycles = NodeCycles.CYCLES_1024, size = NodeSize.SIZE_1024)
    public static final class KyberNttNode extends KyberNode {
        public static final NodeClass<KyberNttNode> TYPE = NodeClass.create(KyberNttNode.class);
        public static final ForeignCallDescriptor STUB = foreignCallDescriptor("kyberNtt", Pointer.class, Pointer.class);

        @Input private ValueNode poly;
        @Input private ValueNode zetas;

        public KyberNttNode(ValueNode poly, ValueNode zetas) {
            this(poly, zetas, null);
        }

        public KyberNttNode(ValueNode poly, ValueNode zetas, EnumSet<?> runtimeCheckedCPUFeatures) {
            super(TYPE, runtimeCheckedCPUFeatures);
            this.poly = poly;
            this.zetas = zetas;
        }

        @Override
        public ValueNode[] getForeignCallArguments() {
            return new ValueNode[]{poly, zetas};
        }

        @Override
        public ForeignCallDescriptor getForeignCallDescriptor() {
            return STUB;
        }

        @Override
        public void emitIntrinsic(NodeLIRBuilderTool gen) {
            gen.setResult(this, gen.getLIRGeneratorTool().emitKyberNtt(gen.operand(poly), gen.operand(zetas)));
        }

        @NodeIntrinsic
        @GenerateStub(name = "kyberNtt", minimumCPUFeaturesAMD64 = "minFeaturesAMD64")
        public static native int kyberNtt(Pointer poly, Pointer zetas);

        @NodeIntrinsic
        public static native int kyberNtt(Pointer poly, Pointer zetas, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
    }

    @NodeInfo(allowedUsageTypes = Memory, cycles = NodeCycles.CYCLES_1024, size = NodeSize.SIZE_1024)
    public static final class KyberInverseNttNode extends KyberNode {
        public static final NodeClass<KyberInverseNttNode> TYPE = NodeClass.create(KyberInverseNttNode.class);
        public static final ForeignCallDescriptor STUB = foreignCallDescriptor("kyberInverseNtt", Pointer.class, Pointer.class);

        @Input private ValueNode poly;
        @Input private ValueNode zetas;

        public KyberInverseNttNode(ValueNode poly, ValueNode zetas) {
            this(poly, zetas, null);
        }

        public KyberInverseNttNode(ValueNode poly, ValueNode zetas, EnumSet<?> runtimeCheckedCPUFeatures) {
            super(TYPE, runtimeCheckedCPUFeatures);
            this.poly = poly;
            this.zetas = zetas;
        }

        @Override
        public ValueNode[] getForeignCallArguments() {
            return new ValueNode[]{poly, zetas};
        }

        @Override
        public ForeignCallDescriptor getForeignCallDescriptor() {
            return STUB;
        }

        @Override
        public void emitIntrinsic(NodeLIRBuilderTool gen) {
            gen.setResult(this, gen.getLIRGeneratorTool().emitKyberInverseNtt(gen.operand(poly), gen.operand(zetas)));
        }

        @NodeIntrinsic
        @GenerateStub(name = "kyberInverseNtt", minimumCPUFeaturesAMD64 = "minFeaturesAMD64")
        public static native int kyberInverseNtt(Pointer poly, Pointer zetas);

        @NodeIntrinsic
        public static native int kyberInverseNtt(Pointer poly, Pointer zetas, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
    }

    @NodeInfo(allowedUsageTypes = Memory, cycles = NodeCycles.CYCLES_1024, size = NodeSize.SIZE_1024)
    public static final class KyberNttMultNode extends KyberNode {
        public static final NodeClass<KyberNttMultNode> TYPE = NodeClass.create(KyberNttMultNode.class);
        public static final ForeignCallDescriptor STUB = foreignCallDescriptor("kyberNttMult", Pointer.class, Pointer.class, Pointer.class, Pointer.class);

        @Input private ValueNode result;
        @Input private ValueNode ntta;
        @Input private ValueNode nttb;
        @Input private ValueNode zetas;

        public KyberNttMultNode(ValueNode result, ValueNode ntta, ValueNode nttb, ValueNode zetas) {
            this(result, ntta, nttb, zetas, null);
        }

        public KyberNttMultNode(ValueNode result, ValueNode ntta, ValueNode nttb, ValueNode zetas, EnumSet<?> runtimeCheckedCPUFeatures) {
            super(TYPE, runtimeCheckedCPUFeatures);
            this.result = result;
            this.ntta = ntta;
            this.nttb = nttb;
            this.zetas = zetas;
        }

        @Override
        public ValueNode[] getForeignCallArguments() {
            return new ValueNode[]{result, ntta, nttb, zetas};
        }

        @Override
        public ForeignCallDescriptor getForeignCallDescriptor() {
            return STUB;
        }

        @Override
        public void emitIntrinsic(NodeLIRBuilderTool gen) {
            gen.setResult(this, gen.getLIRGeneratorTool().emitKyberNttMult(gen.operand(result), gen.operand(ntta), gen.operand(nttb), gen.operand(zetas)));
        }

        @NodeIntrinsic
        @GenerateStub(name = "kyberNttMult", minimumCPUFeaturesAMD64 = "minFeaturesAMD64")
        public static native int kyberNttMult(Pointer result, Pointer ntta, Pointer nttb, Pointer zetas);

        @NodeIntrinsic
        public static native int kyberNttMult(Pointer result, Pointer ntta, Pointer nttb, Pointer zetas, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
    }

    @NodeInfo(allowedUsageTypes = Memory, cycles = NodeCycles.CYCLES_1024, size = NodeSize.SIZE_1024)
    public static final class KyberAddPoly2Node extends KyberNode {
        public static final NodeClass<KyberAddPoly2Node> TYPE = NodeClass.create(KyberAddPoly2Node.class);
        public static final ForeignCallDescriptor STUB = foreignCallDescriptor("kyberAddPoly_2", Pointer.class, Pointer.class, Pointer.class);

        @Input private ValueNode result;
        @Input private ValueNode a;
        @Input private ValueNode b;

        public KyberAddPoly2Node(ValueNode result, ValueNode a, ValueNode b) {
            this(result, a, b, null);
        }

        public KyberAddPoly2Node(ValueNode result, ValueNode a, ValueNode b, EnumSet<?> runtimeCheckedCPUFeatures) {
            super(TYPE, runtimeCheckedCPUFeatures);
            this.result = result;
            this.a = a;
            this.b = b;
        }

        @Override
        public ValueNode[] getForeignCallArguments() {
            return new ValueNode[]{result, a, b};
        }

        @Override
        public ForeignCallDescriptor getForeignCallDescriptor() {
            return STUB;
        }

        @Override
        public void emitIntrinsic(NodeLIRBuilderTool gen) {
            gen.setResult(this, gen.getLIRGeneratorTool().emitKyberAddPoly2(gen.operand(result), gen.operand(a), gen.operand(b)));
        }

        @NodeIntrinsic
        @GenerateStub(name = "kyberAddPoly_2", minimumCPUFeaturesAMD64 = "minFeaturesAMD64")
        public static native int kyberAddPoly2(Pointer result, Pointer a, Pointer b);

        @NodeIntrinsic
        public static native int kyberAddPoly2(Pointer result, Pointer a, Pointer b, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
    }

    @NodeInfo(allowedUsageTypes = Memory, cycles = NodeCycles.CYCLES_1024, size = NodeSize.SIZE_1024)
    public static final class KyberAddPoly3Node extends KyberNode {
        public static final NodeClass<KyberAddPoly3Node> TYPE = NodeClass.create(KyberAddPoly3Node.class);
        public static final ForeignCallDescriptor STUB = foreignCallDescriptor("kyberAddPoly_3", Pointer.class, Pointer.class, Pointer.class, Pointer.class);

        @Input private ValueNode result;
        @Input private ValueNode a;
        @Input private ValueNode b;
        @Input private ValueNode c;

        public KyberAddPoly3Node(ValueNode result, ValueNode a, ValueNode b, ValueNode c) {
            this(result, a, b, c, null);
        }

        public KyberAddPoly3Node(ValueNode result, ValueNode a, ValueNode b, ValueNode c, EnumSet<?> runtimeCheckedCPUFeatures) {
            super(TYPE, runtimeCheckedCPUFeatures);
            this.result = result;
            this.a = a;
            this.b = b;
            this.c = c;
        }

        @Override
        public ValueNode[] getForeignCallArguments() {
            return new ValueNode[]{result, a, b, c};
        }

        @Override
        public ForeignCallDescriptor getForeignCallDescriptor() {
            return STUB;
        }

        @Override
        public void emitIntrinsic(NodeLIRBuilderTool gen) {
            gen.setResult(this, gen.getLIRGeneratorTool().emitKyberAddPoly3(gen.operand(result), gen.operand(a), gen.operand(b), gen.operand(c)));
        }

        @NodeIntrinsic
        @GenerateStub(name = "kyberAddPoly_3", minimumCPUFeaturesAMD64 = "minFeaturesAMD64")
        public static native int kyberAddPoly3(Pointer result, Pointer a, Pointer b, Pointer c);

        @NodeIntrinsic
        public static native int kyberAddPoly3(Pointer result, Pointer a, Pointer b, Pointer c, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
    }

    @NodeInfo(allowedUsageTypes = Memory, cycles = NodeCycles.CYCLES_1024, size = NodeSize.SIZE_1024)
    public static final class Kyber12To16Node extends KyberNode {
        public static final NodeClass<Kyber12To16Node> TYPE = NodeClass.create(Kyber12To16Node.class);
        public static final ForeignCallDescriptor STUB = foreignCallDescriptor("kyber12To16", KILLED_BYTE_SHORT_LOCATIONS, Pointer.class, int.class, Pointer.class, int.class);

        @Input private ValueNode condensed;
        @Input private ValueNode index;
        @Input private ValueNode parsed;
        @Input private ValueNode parsedLength;

        public Kyber12To16Node(ValueNode condensed, ValueNode index, ValueNode parsed, ValueNode parsedLength) {
            this(condensed, index, parsed, parsedLength, null);
        }

        public Kyber12To16Node(ValueNode condensed, ValueNode index, ValueNode parsed, ValueNode parsedLength, EnumSet<?> runtimeCheckedCPUFeatures) {
            super(TYPE, runtimeCheckedCPUFeatures, KILLED_BYTE_SHORT_LOCATIONS);
            this.condensed = condensed;
            this.index = index;
            this.parsed = parsed;
            this.parsedLength = parsedLength;
        }

        @Override
        public ValueNode[] getForeignCallArguments() {
            return new ValueNode[]{condensed, index, parsed, parsedLength};
        }

        @Override
        public ForeignCallDescriptor getForeignCallDescriptor() {
            return STUB;
        }

        @Override
        public void emitIntrinsic(NodeLIRBuilderTool gen) {
            gen.setResult(this, gen.getLIRGeneratorTool().emitKyber12To16(gen.operand(condensed), gen.operand(index), gen.operand(parsed), gen.operand(parsedLength)));
        }

        @NodeIntrinsic
        @GenerateStub(name = "kyber12To16", minimumCPUFeaturesAMD64 = "minFeaturesAMD64")
        public static native int kyber12To16(Pointer condensed, int index, Pointer parsed, int parsedLength);

        @NodeIntrinsic
        public static native int kyber12To16(Pointer condensed, int index, Pointer parsed, int parsedLength, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
    }

    @NodeInfo(allowedUsageTypes = Memory, cycles = NodeCycles.CYCLES_1024, size = NodeSize.SIZE_1024)
    public static final class KyberBarrettReduceNode extends KyberNode {
        public static final NodeClass<KyberBarrettReduceNode> TYPE = NodeClass.create(KyberBarrettReduceNode.class);
        public static final ForeignCallDescriptor STUB = foreignCallDescriptor("kyberBarrettReduce", Pointer.class);

        @Input private ValueNode coeffs;

        public KyberBarrettReduceNode(ValueNode coeffs) {
            this(coeffs, null);
        }

        public KyberBarrettReduceNode(ValueNode coeffs, EnumSet<?> runtimeCheckedCPUFeatures) {
            super(TYPE, runtimeCheckedCPUFeatures);
            this.coeffs = coeffs;
        }

        @Override
        public ValueNode[] getForeignCallArguments() {
            return new ValueNode[]{coeffs};
        }

        @Override
        public ForeignCallDescriptor getForeignCallDescriptor() {
            return STUB;
        }

        @Override
        public void emitIntrinsic(NodeLIRBuilderTool gen) {
            gen.setResult(this, gen.getLIRGeneratorTool().emitKyberBarrettReduce(gen.operand(coeffs)));
        }

        @NodeIntrinsic
        @GenerateStub(name = "kyberBarrettReduce", minimumCPUFeaturesAMD64 = "minFeaturesAMD64")
        public static native int kyberBarrettReduce(Pointer coeffs);

        @NodeIntrinsic
        public static native int kyberBarrettReduce(Pointer coeffs, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
    }
}
