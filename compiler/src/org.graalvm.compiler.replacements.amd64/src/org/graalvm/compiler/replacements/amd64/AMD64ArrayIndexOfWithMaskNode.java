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

import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_512;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
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

@NodeInfo(size = SIZE_512, cycles = NodeCycles.CYCLES_UNKNOWN)
public class AMD64ArrayIndexOfWithMaskNode extends FixedWithNextNode implements Lowerable, MemoryAccess, DeoptimizingNode.DeoptBefore {

    public static final NodeClass<AMD64ArrayIndexOfWithMaskNode> TYPE = NodeClass.create(AMD64ArrayIndexOfWithMaskNode.class);

    private final JavaKind arrayKind;
    private final JavaKind valueKind;
    private final boolean findTwoConsecutive;

    @Input private ValueNode arrayPointer;
    @Input private ValueNode arrayLength;
    @Input private ValueNode fromIndex;
    @Input private ValueNode searchValue;
    @Input private ValueNode mask;

    @OptionalInput(InputType.Memory) private MemoryKill lastLocationAccess;
    @OptionalInput(InputType.State) protected FrameState stateBefore;

    public AMD64ArrayIndexOfWithMaskNode(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    ValueNode arrayPointer, ValueNode arrayLength, ValueNode fromIndex, ValueNode searchValue, ValueNode mask) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.arrayKind = arrayKind;
        this.valueKind = valueKind;
        this.findTwoConsecutive = findTwoConsecutive;
        this.arrayPointer = arrayPointer;
        this.arrayLength = arrayLength;
        this.fromIndex = fromIndex;
        this.searchValue = searchValue;
        this.mask = mask;
    }

    public boolean isFindTwoConsecutive() {
        return findTwoConsecutive;
    }

    public ValueNode getArrayPointer() {
        return arrayPointer;
    }

    public ValueNode getArrayLength() {
        return arrayLength;
    }

    public ValueNode getFromIndex() {
        return fromIndex;
    }

    public ValueNode getSearchValue() {
        return searchValue;
    }

    public ValueNode getMask() {
        return mask;
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

    public JavaKind getArrayKind() {
        return arrayKind;
    }

    public JavaKind getValueKind() {
        return valueKind;
    }

    public JavaKind getComparisonKind() {
        return findTwoConsecutive ? (valueKind == JavaKind.Byte ? JavaKind.Char : JavaKind.Int) : valueKind;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return NamedLocationIdentity.getArrayLocation(arrayKind);
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

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, byte v1, byte mask);

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, char v1, char mask);

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, int searchValue, int mask);

    public static int indexOfWithMask(byte[] array, int arrayLength, int fromIndex, byte searchValue, byte mask) {
        return optimizedArrayIndexOf(JavaKind.Byte, JavaKind.Byte, false, array, arrayLength, fromIndex, searchValue, mask);
    }

    public static int indexOfWithMask(char[] array, int arrayLength, int fromIndex, char searchValue, char mask) {
        return optimizedArrayIndexOf(JavaKind.Char, JavaKind.Char, false, array, arrayLength, fromIndex, searchValue, mask);
    }

    public static int indexOfWithMask(byte[] array, int arrayLength, int fromIndex, char searchValue, char mask) {
        return optimizedArrayIndexOf(JavaKind.Byte, JavaKind.Char, false, array, arrayLength, fromIndex, searchValue, mask);
    }

    public static int indexOf2ConsecutiveBytesWithMask(byte[] array, int arrayLength, int fromIndex, int searchValue, int mask) {
        return optimizedArrayIndexOf(JavaKind.Byte, JavaKind.Byte, true, array, arrayLength, fromIndex, searchValue, mask);
    }

    public static int indexOf2ConsecutiveCharsWithMask(char[] array, int arrayLength, int fromIndex, int searchValue, int mask) {
        return optimizedArrayIndexOf(JavaKind.Char, JavaKind.Char, true, array, arrayLength, fromIndex, searchValue, mask);
    }

    public static int indexOf2ConsecutiveCharsWithMask(byte[] array, int arrayLength, int fromIndex, int searchValue, int mask) {
        return optimizedArrayIndexOf(JavaKind.Byte, JavaKind.Char, true, array, arrayLength, fromIndex, searchValue, mask);
    }
}
