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
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.GenerateStub;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.replacements.nodes.MemoryKillStubIntrinsicNode;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;

import jdk.vm.ci.meta.JavaKind;

/**
 * Represents java.lang.StringLatin1.inflate methods.
 *
 * <ul>
 * <li>void inflate(byte[] src, int srcOff, char[] dst, int dstOff, int len)
 * <li>void inflate(byte[] src, int srcOff, byte[] dst, int dstOff, int len)
 * </ul>
 */
@NodeInfo(allowedUsageTypes = Memory, size = SIZE_512, cycles = CYCLES_UNKNOWN, cyclesRationale = "depends on length")
public final class StringLatin1InflateNode extends MemoryKillStubIntrinsicNode {

    public static final NodeClass<StringLatin1InflateNode> TYPE = NodeClass.create(StringLatin1InflateNode.class);

    public static final LocationIdentity[] KILLED_LOCATIONS_BYTE_CHAR = {NamedLocationIdentity.getArrayLocation(JavaKind.Byte), NamedLocationIdentity.getArrayLocation(JavaKind.Char)};
    public static final ForeignCallDescriptor STUB = new ForeignCallDescriptor("stringLatin1Inflate", void.class, new Class<?>[]{Pointer.class, Pointer.class, int.class},
                    HAS_SIDE_EFFECT, KILLED_LOCATIONS_BYTE_CHAR, false, false);

    /** pointer to src[srcOff]. */
    @Input private ValueNode src;
    /** pointer to dst[dstOff]. */
    @Input private ValueNode dst;
    @Input private ValueNode len;
    private final LocationIdentity[] killedLocations;

    public StringLatin1InflateNode(ValueNode src, ValueNode dst, ValueNode len, JavaKind writeKind) {
        this(src, dst, len, new LocationIdentity[]{NamedLocationIdentity.getArrayLocation(writeKind)}, null);
        GraalError.guarantee(writeKind == JavaKind.Byte || writeKind == JavaKind.Char, "write kind must be either Char or Byte");
    }

    /**
     * Constructor for stub compilation. We set {@code killedLocations} to both {@code char} and
     * {@code byte} array locations here, because we want to re-use the same stub for all call
     * sites.
     */
    public StringLatin1InflateNode(ValueNode src, ValueNode dst, ValueNode len) {
        this(src, dst, len, KILLED_LOCATIONS_BYTE_CHAR, null);
    }

    public StringLatin1InflateNode(ValueNode src, ValueNode dst, ValueNode len, EnumSet<?> runtimeCheckedCPUFeatures) {
        this(src, dst, len, KILLED_LOCATIONS_BYTE_CHAR, runtimeCheckedCPUFeatures);
    }

    private StringLatin1InflateNode(ValueNode src, ValueNode dst, ValueNode len, LocationIdentity[] killedLocations, EnumSet<?> runtimeCheckedCPUFeatures) {
        super(TYPE, StampFactory.forVoid(), runtimeCheckedCPUFeatures, NamedLocationIdentity.getArrayLocation(JavaKind.Byte));
        this.src = src;
        this.dst = dst;
        this.len = len;
        this.killedLocations = killedLocations;
    }

    @Override
    public LocationIdentity[] getKilledLocationIdentities() {
        return killedLocations;
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
        gen.getLIRGeneratorTool().emitStringLatin1Inflate(runtimeCheckedCPUFeatures, gen.operand(src), gen.operand(dst), gen.operand(len));
    }

    @NodeIntrinsic
    @GenerateStub
    public static native void stringLatin1Inflate(Pointer src, Pointer dst, int len);

    @NodeIntrinsic
    public static native void stringLatin1Inflate(Pointer src, Pointer dst, int len, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
}
