/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.nodes;

import static org.graalvm.compiler.core.common.GraalOptions.UseGraalStubs;
import static org.graalvm.compiler.nodeinfo.InputType.Memory;

import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValueNodeUtil;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.MemoryKill;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.word.WordCastNode;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;

import java.nio.ByteOrder;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.Value;

// JaCoCo Exclude

/**
 * Compares two array regions with a given length. This node can compare regions of arrays of the
 * same primitive element kinds. As a special case, it also supports comparing an array region
 * interpreted as {@code char}s with an array region interpreted as {@code byte}s, in which case the
 * {@code byte} values are zero-extended for the comparison. In this case, the first kind must be
 * {@code char}, and the underlying array must be a {@code byte} array (this condition is not
 * checked). Other combinations of kinds are currently not allowed.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_128)
public class ArrayRegionEqualsNode extends FixedWithNextNode implements Canonicalizable, LIRLowerable, MemoryAccess {

    public static final NodeClass<ArrayRegionEqualsNode> TYPE = NodeClass.create(ArrayRegionEqualsNode.class);

    /** {@link JavaKind} of the arrays to compare. */
    protected final JavaKind kind1;
    protected final JavaKind kind2;

    /**
     * Pointer to first array region to be tested for equality. This is a pointer into an array's
     * contents, not to the array object itself.
     */
    @Input protected ValueNode array1;

    /**
     * Pointer to second array region to be tested for equality. This is a pointer into an array's
     * contents, not to the array object itself.
     */
    @Input protected ValueNode array2;

    /** Length of the array region. */
    @Input protected ValueNode length;

    @OptionalInput(Memory) protected MemoryKill lastLocationAccess;

    public ArrayRegionEqualsNode(ValueNode array1, ValueNode array2, ValueNode length, @ConstantNodeParameter JavaKind kind1, @ConstantNodeParameter JavaKind kind2) {
        this(TYPE, array1, array2, length, kind1, kind2);
    }

    protected ArrayRegionEqualsNode(NodeClass<? extends ArrayRegionEqualsNode> c, ValueNode array1, ValueNode array2, ValueNode length, @ConstantNodeParameter JavaKind kind1,
                    @ConstantNodeParameter JavaKind kind2) {
        super(c, StampFactory.forKind(JavaKind.Boolean));
        this.kind1 = kind1;
        this.kind2 = kind2;
        this.array1 = array1;
        this.array2 = array2;
        this.length = length;

        assert kind1.isPrimitive() && kind2.isPrimitive() : "expected primitive kinds, got: " + kind1 + ", " + kind2;
        assert kind1 == kind2 || (kind1 == JavaKind.Char && kind2 == JavaKind.Byte) : "expected equal kinds or char+byte, got: " + kind1 + ", " + kind2;
    }

    public static boolean regionEquals(Pointer array1, Pointer array2, int length, @ConstantNodeParameter JavaKind kind) {
        return regionEquals(array1, array2, length, kind, kind);
    }

    @NodeIntrinsic
    public static native boolean regionEquals(Pointer array1, Pointer array2, int length, @ConstantNodeParameter JavaKind kind1, @ConstantNodeParameter JavaKind kind2);

    public ValueNode getArray1() {
        return array1;
    }

    public ValueNode getArray2() {
        return array2;
    }

    public JavaKind getKind1() {
        return kind1;
    }

    public JavaKind getKind2() {
        return kind2;
    }

    public ValueNode getLength() {
        return length;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        if (UseGraalStubs.getValue(graph().getOptions())) {
            ForeignCallLinkage linkage = gen.lookupGraalStub(this);
            if (linkage != null) {
                Value result = gen.getLIRGeneratorTool().emitForeignCall(linkage, null, gen.operand(array1), gen.operand(array2), gen.operand(length));
                gen.setResult(this, result);
                return;
            }
        }
        generateArrayRegionEquals(gen);
    }

    protected int getArrayBaseOffset(MetaAccessProvider metaAccess, @SuppressWarnings("unused") ValueNode array, JavaKind elementKind) {
        return metaAccess.getArrayBaseOffset(elementKind);
    }

    protected void generateArrayRegionEquals(NodeLIRBuilderTool gen) {
        Value result;
        MetaAccessProvider metaAccess = gen.getLIRGeneratorTool().getMetaAccess();
        int array1BaseOffset = getArrayBaseOffset(metaAccess, array1, kind1);
        int array2BaseOffset = getArrayBaseOffset(metaAccess, array2, kind2);
        if (kind1 == kind2) {
            result = gen.getLIRGeneratorTool().emitArrayEquals(kind1, array1BaseOffset, array2BaseOffset, gen.operand(array1), gen.operand(array2), gen.operand(length), true);
        } else {
            result = gen.getLIRGeneratorTool().emitArrayEquals(kind1, kind2, array1BaseOffset, array2BaseOffset, gen.operand(array1), gen.operand(array2), gen.operand(length), true);
        }
        gen.setResult(this, result);
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return kind1 != kind2 ? LocationIdentity.ANY_LOCATION : NamedLocationIdentity.getArrayLocation(kind1);
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
    public ValueNode canonical(CanonicalizerTool tool) {
        /*
         * This node expects its inputs addresses represented using explicit integer arithmetic
         * applied to a base pointer value. It doesn't use an OffsetAddressNode because during code
         * generation we want to have these address values in registers. An OffsetAddressNode would
         * be generated to LIR AddressValues which cannot be copied by phis, but users of this node
         * want to pass conditional addresses to it.
         *
         * Try constant folding if both addresses are of the form WordCast(Constant) + Constant.
         */
        if (length.isJavaConstant() && array1 instanceof AddNode && array2 instanceof AddNode) {
            int len = length.asJavaConstant().asInt();
            ValueNode arrayBase1 = ((AddNode) array1).getX();
            ValueNode arrayBase2 = ((AddNode) array2).getX();
            if (arrayBase1 instanceof WordCastNode && arrayBase2 instanceof WordCastNode) {
                arrayBase1 = ((WordCastNode) arrayBase1).getInput();
                arrayBase2 = ((WordCastNode) arrayBase2).getInput();
                ValueNode arrayOffset1 = ((AddNode) array1).getY();
                ValueNode arrayOffset2 = ((AddNode) array2).getY();
                if (canFoldReads(tool, arrayBase1, arrayOffset1, kind1, len) && canFoldReads(tool, arrayBase2, arrayOffset2, kind2, len)) {
                    Integer startIndex1 = startIndex(tool, arrayBase1, arrayOffset1.asJavaConstant(), kind1);
                    Integer startIndex2 = startIndex(tool, arrayBase2, arrayOffset2.asJavaConstant(), kind2);
                    if (kind1 == kind2) {
                        return ConstantNode.forBoolean(
                                        ArrayEqualsNode.arrayEquals(tool.getConstantReflection(), arrayBase1.asJavaConstant(), startIndex1, arrayBase2.asJavaConstant(), startIndex2, len));
                    } else {
                        assert kind1 == JavaKind.Char && kind2 == JavaKind.Byte;
                        return ConstantNode.forBoolean(mixedArrayRegionEquals(tool.getConstantReflection(), arrayBase1.asJavaConstant(), startIndex1, arrayBase2.asJavaConstant(), startIndex2, len));
                    }
                }
            }
        }

        return this;
    }

    /**
     * Return {@code true} if the array and the starting offset are constants and we can perform
     * {@code len} in-bounds constant reads starting at {@code offset}. Return {@code false} if not
     * everything constant or if we would try to read out of bounds.
     */
    private boolean canFoldReads(CanonicalizerTool tool, ValueNode array, ValueNode offset, JavaKind elementKind, int len) {
        if (array.isJavaConstant() && ((ConstantNode) array).getStableDimension() >= 1 && offset.isJavaConstant()) {
            ConstantReflectionProvider c = tool.getConstantReflection();
            Integer arrayLength = c.readArrayLength(array.asJavaConstant());
            Integer index = startIndex(tool, array, offset.asJavaConstant(), elementKind);
            return arrayLength != null && index != null && index >= 0 && index + len <= arrayLength;
        }
        return false;
    }

    /**
     * Compute an element index from a byte offset from the start of the array object. Returns
     * {@code null} if the given offset is not aligned correctly for the element kind's stride.
     */
    private Integer startIndex(CanonicalizerTool tool, ValueNode array, JavaConstant offset, JavaKind elementKind) {
        long elementOffset = offset.asLong() - getArrayBaseOffset(tool.getMetaAccess(), array, elementKind);
        if (elementOffset % elementKind.getByteCount() != 0) {
            return null;
        }
        return (int) (elementOffset / elementKind.getByteCount());
    }

    protected static boolean mixedArrayRegionEquals(ConstantReflectionProvider constantReflection, JavaConstant a, int startIndexA, JavaConstant b, int startIndexB, int len) {
        // Read pairs of bytes interpreted as chars from a, bytes from b. The caller must ensure
        // correct array kinds.
        for (int i = 0; i < len; i++) {
            int b0 = constantReflection.readArrayElement(a, startIndexA + i * 2).asInt() & 0xFF;
            int b1 = constantReflection.readArrayElement(a, startIndexA + i * 2 + 1).asInt() & 0xFF;
            char charValue;
            if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
                charValue = (char) ((b0 << 8) | b1);
            } else {
                charValue = (char) (b0 | (b1 << 8));
            }
            int byteValue = constantReflection.readArrayElement(b, startIndexB + i).asInt() & 0xFF;
            if (charValue != byteValue) {
                return false;
            }
        }
        return true;
    }
}
