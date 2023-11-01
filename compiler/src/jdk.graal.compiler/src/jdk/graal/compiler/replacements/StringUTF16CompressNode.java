/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, Arm Limited. All rights reserved.
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
package jdk.graal.compiler.replacements;

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.HAS_SIDE_EFFECT;
import static jdk.graal.compiler.nodeinfo.InputType.Memory;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_512;

import java.util.EnumSet;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.GenerateStub;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.replacements.nodes.MemoryKillStubIntrinsicNode;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

/**
 * Represents java.lang.StringUTF16.compress methods.
 *
 * <ul>
 * <li>int compress(char[] src, int srcOff, byte[] dst, int dstOff, int len)
 * <li>int compress(byte[] src, int srcOff, byte[] dst, int dstOff, int len)
 * </ul>
 */
@NodeInfo(allowedUsageTypes = Memory, size = SIZE_512, cycles = CYCLES_UNKNOWN, cyclesRationale = "depends on length")
public final class StringUTF16CompressNode extends MemoryKillStubIntrinsicNode {

    public static final NodeClass<StringUTF16CompressNode> TYPE = NodeClass.create(StringUTF16CompressNode.class);

    private static final LocationIdentity[] KILLED_LOCATIONS = {NamedLocationIdentity.getArrayLocation(JavaKind.Byte)};

    public static final ForeignCallDescriptor STUB = new ForeignCallDescriptor("stringUTF16Compress", int.class, new Class<?>[]{Pointer.class, Pointer.class, int.class},
                    HAS_SIDE_EFFECT, KILLED_LOCATIONS, false, false);

    /** pointer to src[srcOff]. */
    @Input private ValueNode src;
    /** pointer to dst[dstOff]. */
    @Input private ValueNode dst;
    @Input private ValueNode len;

    public StringUTF16CompressNode(ValueNode src, ValueNode dst, ValueNode len, JavaKind readKind) {
        this(src, dst, len, null, NamedLocationIdentity.getArrayLocation(readKind));
    }

    /**
     * Constructor for stub compilation. We set {@code locationIdentity} to
     * {@link LocationIdentity#any()} here, because we want to re-use the same stub for all call
     * sites.
     */
    public StringUTF16CompressNode(ValueNode src, ValueNode dst, ValueNode len) {
        this(src, dst, len, null, LocationIdentity.any());
    }

    public StringUTF16CompressNode(ValueNode src, ValueNode dst, ValueNode len, EnumSet<?> runtimeCheckedCPUFeatures) {
        this(src, dst, len, runtimeCheckedCPUFeatures, LocationIdentity.any());
    }

    private StringUTF16CompressNode(ValueNode src, ValueNode dst, ValueNode len, EnumSet<?> runtimeCheckedCPUFeatures, LocationIdentity locationIdentity) {
        super(TYPE, IntegerStamp.create(32), runtimeCheckedCPUFeatures, locationIdentity);
        this.src = src;
        this.dst = dst;
        this.len = len;
    }

    @Override
    public LocationIdentity[] getKilledLocationIdentities() {
        // Model write access via 'dst' using:
        return KILLED_LOCATIONS;
    }

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        return STUB;
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        return new ValueNode[]{src, dst, len};
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        LIRGeneratorTool lgt = gen.getLIRGeneratorTool();
        Value res = lgt.emitStringUTF16Compress(runtimeCheckedCPUFeatures, gen.operand(src), gen.operand(dst), gen.operand(len));
        gen.setResult(this, res);
    }

    @NodeIntrinsic
    @GenerateStub
    public static native int stringUTF16Compress(Pointer src, Pointer dst, int len);

    @NodeIntrinsic
    public static native int stringUTF16Compress(Pointer src, Pointer dst, int len, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
}
