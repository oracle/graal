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
package org.graalvm.compiler.replacements.amd64;

import static org.graalvm.compiler.nodeinfo.InputType.Memory;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.DeoptimizingNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValueNodeUtil;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.MemoryKill;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;

// JaCoCo Exclude

@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_128)
public final class AMD64ArrayRegionEqualsWithMaskNode extends FixedWithNextNode implements Lowerable, MemoryAccess, DeoptimizingNode.DeoptBefore {

    public static final NodeClass<AMD64ArrayRegionEqualsWithMaskNode> TYPE = NodeClass.create(AMD64ArrayRegionEqualsWithMaskNode.class);

    /** {@link JavaKind} of the arrays to compare. */
    private final JavaKind arrayKind;
    private final JavaKind kind1;
    private final JavaKind kind2;
    private final JavaKind kindMask;

    @Input private ValueNode array1;
    @Input private ValueNode fromIndex1;
    @Input private ValueNode array2;
    @Input private ValueNode fromIndex2;
    @Input private ValueNode arrayMask;
    @Input private ValueNode length;

    @OptionalInput(Memory) private MemoryKill lastLocationAccess;
    @OptionalInput(InputType.State) protected FrameState stateBefore;

    public AMD64ArrayRegionEqualsWithMaskNode(ValueNode array1, ValueNode fromIndex1, ValueNode array2, ValueNode fromIndex2, ValueNode arrayMask, ValueNode length,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind kind1,
                    @ConstantNodeParameter JavaKind kind2,
                    @ConstantNodeParameter JavaKind kindMask) {
        super(TYPE, StampFactory.forKind(JavaKind.Boolean));
        this.arrayKind = arrayKind;
        this.kind1 = kind1;
        this.kind2 = kind2;
        this.kindMask = kindMask;
        this.array1 = array1;
        this.fromIndex1 = fromIndex1;
        this.array2 = array2;
        this.fromIndex2 = fromIndex2;
        this.arrayMask = arrayMask;
        this.length = length;
    }

    public JavaKind getArrayKind() {
        return arrayKind;
    }

    public JavaKind getKind1() {
        return kind1;
    }

    public JavaKind getKind2() {
        return kind2;
    }

    public JavaKind getKindMask() {
        return kindMask;
    }

    public ValueNode getArray1() {
        return array1;
    }

    public ValueNode getFromIndex1() {
        return fromIndex1;
    }

    public ValueNode getArray2() {
        return array2;
    }

    public ValueNode getFromIndex2() {
        return fromIndex2;
    }

    public ValueNode getArrayMask() {
        return arrayMask;
    }

    public ValueNode getLength() {
        return length;
    }

    public boolean sameKinds() {
        return kind1 == arrayKind && kind2 == arrayKind && kindMask == arrayKind;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return sameKinds() ? LocationIdentity.ANY_LOCATION : NamedLocationIdentity.getArrayLocation(arrayKind);
    }

    @Override
    public MemoryKill getLastLocationAccess() {
        return lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryKill lla) {
        updateUsages(ValueNodeUtil.asNode(lastLocationAccess), ValueNodeUtil.asNode(lla));
        lastLocationAccess = lla;
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

    @Override
    public void setStateBefore(FrameState f) {
        updateUsages(stateBefore, f);
        stateBefore = f;
    }

    @Override
    public FrameState stateBefore() {
        return stateBefore;
    }

    @NodeIntrinsic
    private static native boolean regionEquals(Object value, int fromIndex1, Object value2, int fromIndex2, Object mask, int length,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind kind1,
                    @ConstantNodeParameter JavaKind kind2,
                    @ConstantNodeParameter JavaKind kindMask);

    public static boolean regionEquals(byte[] value, int fromIndex1, byte[] value2, int fromIndex2, byte[] mask) {
        return regionEquals(value, fromIndex1, value2, fromIndex2, mask, mask.length, JavaKind.Byte, JavaKind.Byte, JavaKind.Byte, JavaKind.Byte);
    }

    public static boolean regionEquals(char[] value, int fromIndex1, char[] value2, int fromIndex2, char[] mask) {
        return regionEquals(value, fromIndex1, value2, fromIndex2, mask, mask.length, JavaKind.Char, JavaKind.Char, JavaKind.Char, JavaKind.Char);
    }

    public static boolean regionEquals(byte[] value, int fromIndex1, byte[] value2, int fromIndex2, byte[] mask, int length, JavaKind kind1, JavaKind kind2, JavaKind kindMask) {
        return regionEquals(value, fromIndex1, value2, fromIndex2, mask, length, JavaKind.Byte, kind1, kind2, kindMask);
    }
}
