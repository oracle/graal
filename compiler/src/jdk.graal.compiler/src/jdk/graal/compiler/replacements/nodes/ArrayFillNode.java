/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.EnumSet;

import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.GenerateStub;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaKind;

// JaCoCo Exclude

/**
 * Fills an array by setting all elements to {@link #valueToFillWith}. The value must be a primitive
 * integer, and its bit size must match the array's {@link #elementKind}. Filling of floating-point
 * arrays is supported by passing a floating-point value reinterpreted as integer bits.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_UNKNOWN, allowedUsageTypes = InputType.Memory)
public class ArrayFillNode extends MemoryKillStubIntrinsicNode
                implements StateSplit, MemoryAccess {

    public static final NodeClass<ArrayFillNode> TYPE = NodeClass.create(ArrayFillNode.class);
    private static final ForeignCallDescriptor BOOLEAN_ARRAY_FILL_STUB = createForeignCallDescriptor(JavaKind.Boolean, byte.class);
    private static final ForeignCallDescriptor BYTE_ARRAY_FILL_STUB = createForeignCallDescriptor(JavaKind.Byte, byte.class);
    private static final ForeignCallDescriptor CHAR_ARRAY_FILL_STUB = createForeignCallDescriptor(JavaKind.Char, short.class);
    private static final ForeignCallDescriptor SHORT_ARRAY_FILL_STUB = createForeignCallDescriptor(JavaKind.Short, short.class);
    private static final ForeignCallDescriptor INT_ARRAY_FILL_STUB = createForeignCallDescriptor(JavaKind.Int, int.class);
    private static final ForeignCallDescriptor FLOAT_ARRAY_FILL_STUB = createForeignCallDescriptor(JavaKind.Float, int.class);
    private static final ForeignCallDescriptor LONG_ARRAY_FILL_STUB = createForeignCallDescriptor(JavaKind.Long, long.class);
    private static final ForeignCallDescriptor DOUBLE_ARRAY_FILL_STUB = createForeignCallDescriptor(JavaKind.Double, long.class);

    public static final ForeignCallDescriptor[] STUBS = {
                    BOOLEAN_ARRAY_FILL_STUB,
                    BYTE_ARRAY_FILL_STUB,
                    CHAR_ARRAY_FILL_STUB,
                    SHORT_ARRAY_FILL_STUB,
                    INT_ARRAY_FILL_STUB,
                    LONG_ARRAY_FILL_STUB,
                    FLOAT_ARRAY_FILL_STUB,
                    DOUBLE_ARRAY_FILL_STUB,
    };

    /** JavaKind of the array's entries. */
    protected JavaKind elementKind;

    /** Address of the array object on the Java heap. */
    @Input protected ValueNode arrayBase;

    /** Offset from start of array object in Java heap to its first element. */
    @Input protected ValueNode offsetToFirstElement;

    /** Number of entries in the array. */
    @Input protected ValueNode arrayLength;

    /** Value to fill array with. */
    @Input protected ValueNode valueToFillWith;

    public ArrayFillNode(ValueNode arrayBase, ValueNode offsetToFirstElement, ValueNode arrayLength, ValueNode valueToFillWith, @ConstantNodeParameter JavaKind elementKind) {
        this(arrayBase, offsetToFirstElement, arrayLength, valueToFillWith, elementKind, null);
    }

    public ArrayFillNode(ValueNode arrayBase, ValueNode offsetToFirstElement, ValueNode arrayLength, ValueNode valueToFillWith, @ConstantNodeParameter JavaKind elementKind,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures) {
        super(TYPE, StampFactory.forVoid(), runtimeCheckedCPUFeatures, NamedLocationIdentity.getArrayLocation(elementKind));
        this.arrayBase = arrayBase;
        this.offsetToFirstElement = offsetToFirstElement;
        this.arrayLength = arrayLength;
        this.valueToFillWith = valueToFillWith;
        this.elementKind = elementKind;

        GraalError.guarantee(valueToFillWith.stamp(NodeView.DEFAULT) instanceof IntegerStamp, "valueToFillWith needs to be an integer.");
        GraalError.guarantee(elementKind.isPrimitive(), "elementKind needs to be a primitive type.");
    }

    @NodeIntrinsic
    @GenerateStub(name = "booleanArrayFill", parameters = {"Boolean"})
    @GenerateStub(name = "byteArrayFill", parameters = {"Byte"})
    public static native void fill(Pointer array, long offset, int length, byte value,
                    @ConstantNodeParameter JavaKind kind);

    @NodeIntrinsic
    public static native void fill(Pointer array, long offset, int length, byte value,
                    @ConstantNodeParameter JavaKind kind,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    @NodeIntrinsic
    @GenerateStub(name = "charArrayFill", parameters = {"Char"})
    @GenerateStub(name = "shortArrayFill", parameters = {"Short"})
    public static native void fill(Pointer array, long offset, int length, short value,
                    @ConstantNodeParameter JavaKind kind);

    @NodeIntrinsic
    public static native void fill(Pointer array, long offset, int length, short value,
                    @ConstantNodeParameter JavaKind kind,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    @NodeIntrinsic
    @GenerateStub(name = "intArrayFill", parameters = {"Int"})
    @GenerateStub(name = "floatArrayFill", parameters = {"Float"})
    public static native void fill(Pointer array, long offset, int length, int value,
                    @ConstantNodeParameter JavaKind kind);

    @NodeIntrinsic
    public static native void fill(Pointer array, long offset, int length, int value,
                    @ConstantNodeParameter JavaKind kind,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    @NodeIntrinsic
    @GenerateStub(name = "longArrayFill", parameters = {"Long"})
    @GenerateStub(name = "doubleArrayFill", parameters = {"Double"})
    public static native void fill(Pointer array, long offset, int length, long value,
                    @ConstantNodeParameter JavaKind kind);

    @NodeIntrinsic
    public static native void fill(Pointer array, long offset, int length, long value,
                    @ConstantNodeParameter JavaKind kind,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        switch (this.elementKind) {
            case Boolean:
                return BOOLEAN_ARRAY_FILL_STUB;
            case Byte:
                return BYTE_ARRAY_FILL_STUB;
            case Char:
                return CHAR_ARRAY_FILL_STUB;
            case Short:
                return SHORT_ARRAY_FILL_STUB;
            case Int:
                return INT_ARRAY_FILL_STUB;
            case Long:
                return LONG_ARRAY_FILL_STUB;
            case Float:
                return FLOAT_ARRAY_FILL_STUB;
            case Double:
                return DOUBLE_ARRAY_FILL_STUB;
            default:
                return null;
        }
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        return new ValueNode[]{arrayBase, offsetToFirstElement, arrayLength, valueToFillWith};
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        gen.getLIRGeneratorTool().emitArrayFill(elementKind, gen.operand(arrayBase), gen.operand(offsetToFirstElement), gen.operand(arrayLength),
                        gen.operand(valueToFillWith));
    }

    @Override
    public LocationIdentity[] getKilledLocationIdentities() {
        return new LocationIdentity[]{NamedLocationIdentity.getArrayLocation(this.elementKind)};
    }

    public static boolean isSupported(Architecture arch) {
        return (arch instanceof AArch64);
    }

    private static ForeignCallDescriptor createForeignCallDescriptor(JavaKind kind, Class<?> cls) {
        LocationIdentity[] locs = new LocationIdentity[]{NamedLocationIdentity.getArrayLocation(kind)};
        Class<?>[] argTypes = new Class<?>[]{Pointer.class, long.class, int.class, cls};
        String name = kind.getJavaName() + "ArrayFill";

        return new ForeignCallDescriptor(name, void.class, argTypes, HAS_SIDE_EFFECT, locs, false, false);
    }

    public JavaKind getElementKind() {
        return elementKind;
    }

    public ValueNode getArrayBase() {
        return arrayBase;
    }

    public ValueNode getOffsetToFirstElement() {
        return offsetToFirstElement;
    }

    public ValueNode getArrayLength() {
        return arrayLength;
    }

    public ValueNode getValueToFillWith() {
        return valueToFillWith;
    }
}
