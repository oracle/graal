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

import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512BW;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512DQ;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512F;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512VL;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.FMA;

import java.util.EnumSet;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.GenerateStub;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaKind;

@NodeInfo(cycles = NodeCycles.CYCLES_64, size = NodeSize.SIZE_64)
public final class DoubleModStubNode extends FixedWithNextNode implements IntrinsicMethodNodeInterface {
    public static final NodeClass<DoubleModStubNode> TYPE = NodeClass.create(DoubleModStubNode.class);

    public static final ForeignCallDescriptor STUB = ForeignCalls.pureFunctionForeignCallDescriptor("fmod", double.class, double.class, double.class);

    @Input protected ValueNode x;
    @Input protected ValueNode y;

    private final EnumSet<?> runtimeCheckedCPUFeatures;

    public DoubleModStubNode(ValueNode x, ValueNode y) {
        this(x, y, null);
    }

    public DoubleModStubNode(ValueNode x, ValueNode y, EnumSet<?> runtimeCheckedCPUFeatures) {
        super(TYPE, StampFactory.forKind(JavaKind.Double));
        this.x = x;
        this.y = y;
        this.runtimeCheckedCPUFeatures = runtimeCheckedCPUFeatures;
    }

    public static EnumSet<AMD64.CPUFeature> minFeaturesAMD64() {
        return EnumSet.of(FMA, AVX);
    }

    public static EnumSet<AMD64.CPUFeature> maxFeaturesAMD64() {
        return EnumSet.of(FMA, AVX, AVX2, AVX512F, AVX512DQ, AVX512BW, AVX512VL);
    }

    public static boolean isSupported(Architecture arch) {
        return arch instanceof AMD64 amd64 && amd64.getFeatures().containsAll(minFeaturesAMD64());
    }

    public EnumSet<?> getRuntimeCheckedCPUFeatures() {
        return runtimeCheckedCPUFeatures;
    }

    public ValueNode getX() {
        return x;
    }

    public ValueNode getY() {
        return y;
    }

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        return STUB;
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        return new ValueNode[]{getX(), getY()};
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        var result = gen.getLIRGeneratorTool().emitDoubleMod(gen.operand(getX()), gen.operand(getY()));
        gen.setResult(this, result);
    }

    @NodeIntrinsic
    @GenerateStub(name = "fmod", minimumCPUFeaturesAMD64 = "minFeaturesAMD64")
    public static native double compute(double x, double y);

    @NodeIntrinsic
    public static native double compute(double x, double y, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
}
