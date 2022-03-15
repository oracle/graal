/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.amd64;

import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Reexecutability.REEXECUTABLE;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.LEAF;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallsProviderImpl.NO_LOCATIONS;
import static org.graalvm.compiler.replacements.ArrayIndexOf.S1;
import static org.graalvm.compiler.replacements.ArrayIndexOf.S2;
import static org.graalvm.compiler.replacements.ArrayIndexOf.S4;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.stubs.SnippetStub;
import org.graalvm.compiler.lir.amd64.AMD64ArrayEqualsOp;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.StrideUtil;
import org.graalvm.compiler.replacements.nodes.ArrayEqualsNode;
import org.graalvm.compiler.replacements.nodes.ArrayRegionEqualsNode;
import org.graalvm.word.Pointer;

import jdk.vm.ci.meta.JavaKind;

public final class AMD64ArrayEqualsStub extends SnippetStub {

    private static final HotSpotForeignCallDescriptor STUB_BOOLEAN_ARRAY_EQUALS = new HotSpotForeignCallDescriptor(LEAF, REEXECUTABLE, NO_LOCATIONS,
                    "booleanArraysEquals", boolean.class, Pointer.class, Pointer.class, int.class);
    private static final HotSpotForeignCallDescriptor STUB_BYTE_ARRAY_EQUALS = new HotSpotForeignCallDescriptor(LEAF, REEXECUTABLE, NO_LOCATIONS,
                    "byteArraysEquals", boolean.class, Pointer.class, Pointer.class, int.class);
    private static final HotSpotForeignCallDescriptor STUB_CHAR_ARRAY_EQUALS = new HotSpotForeignCallDescriptor(LEAF, REEXECUTABLE, NO_LOCATIONS,
                    "charArraysEquals", boolean.class, Pointer.class, Pointer.class, int.class);
    private static final HotSpotForeignCallDescriptor STUB_SHORT_ARRAY_EQUALS = new HotSpotForeignCallDescriptor(LEAF, REEXECUTABLE, NO_LOCATIONS,
                    "shortArraysEquals", boolean.class, Pointer.class, Pointer.class, int.class);
    private static final HotSpotForeignCallDescriptor STUB_INT_ARRAY_EQUALS = new HotSpotForeignCallDescriptor(LEAF, REEXECUTABLE, NO_LOCATIONS,
                    "intArraysEquals", boolean.class, Pointer.class, Pointer.class, int.class);
    private static final HotSpotForeignCallDescriptor STUB_LONG_ARRAY_EQUALS = new HotSpotForeignCallDescriptor(LEAF, REEXECUTABLE, NO_LOCATIONS,
                    "longArraysEquals", boolean.class, Pointer.class, Pointer.class, int.class);
    private static final HotSpotForeignCallDescriptor STUB_FLOAT_ARRAY_EQUALS = new HotSpotForeignCallDescriptor(LEAF, REEXECUTABLE, NO_LOCATIONS,
                    "floatArraysEquals", boolean.class, Pointer.class, Pointer.class, int.class);
    private static final HotSpotForeignCallDescriptor STUB_DOUBLE_ARRAY_EQUALS = new HotSpotForeignCallDescriptor(LEAF, REEXECUTABLE, NO_LOCATIONS,
                    "doubleArraysEquals", boolean.class, Pointer.class, Pointer.class, int.class);

    private static final HotSpotForeignCallDescriptor STUB_REGION_EQUALS_GENERIC = new HotSpotForeignCallDescriptor(LEAF, REEXECUTABLE, NO_LOCATIONS,
                    "arrayRegionEqualsGeneric", boolean.class, Object.class, long.class, Object.class, long.class, int.class, int.class);

    private static HotSpotForeignCallDescriptor foreignCallDescriptor(String name) {
        return new HotSpotForeignCallDescriptor(LEAF, REEXECUTABLE, NO_LOCATIONS, name, boolean.class, Object.class, long.class, Object.class, long.class, int.class);
    }

    /**
     * CAUTION: the ordering here is important: entries 0-9 must match the indices generated by
     * {@link org.graalvm.compiler.replacements.StrideUtil#getDirectStubCallIndex(ValueNode, JavaKind, JavaKind)}.
     *
     * @see #getRegionEqualsStub(ArrayRegionEqualsNode, AMD64HotSpotLIRGenerator)
     */
    public static final HotSpotForeignCallDescriptor[] STUBS = {
                    foreignCallDescriptor("arrayRegionEqualsS1S1"),
                    foreignCallDescriptor("arrayRegionEqualsS1S2"),
                    foreignCallDescriptor("arrayRegionEqualsS1S4"),
                    foreignCallDescriptor("arrayRegionEqualsS2S1"),
                    foreignCallDescriptor("arrayRegionEqualsS2S2"),
                    foreignCallDescriptor("arrayRegionEqualsS2S4"),
                    foreignCallDescriptor("arrayRegionEqualsS4S1"),
                    foreignCallDescriptor("arrayRegionEqualsS4S2"),
                    foreignCallDescriptor("arrayRegionEqualsS4S4"),

                    STUB_REGION_EQUALS_GENERIC,

                    STUB_BOOLEAN_ARRAY_EQUALS,
                    STUB_BYTE_ARRAY_EQUALS,
                    STUB_CHAR_ARRAY_EQUALS,
                    STUB_SHORT_ARRAY_EQUALS,
                    STUB_INT_ARRAY_EQUALS,
                    STUB_LONG_ARRAY_EQUALS,
                    STUB_FLOAT_ARRAY_EQUALS,
                    STUB_DOUBLE_ARRAY_EQUALS,
    };

    public static ForeignCallDescriptor getArrayEqualsStub(ArrayEqualsNode arrayEqualsNode, AMD64HotSpotLIRGenerator gen) {
        JavaKind stride = arrayEqualsNode.getKind();
        ValueNode length = arrayEqualsNode.getLength();
        if (length.isJavaConstant() && AMD64ArrayEqualsOp.canGenerateConstantLengthCompare(gen.target(), stride, stride, length.asJavaConstant().asInt(), gen.getMaxVectorSize())) {
            // Yield constant-length arrays comparison assembly
            return null;
        }
        switch (stride) {
            case Boolean:
                return STUB_BOOLEAN_ARRAY_EQUALS;
            case Byte:
                return STUB_BYTE_ARRAY_EQUALS;
            case Char:
                return STUB_CHAR_ARRAY_EQUALS;
            case Short:
                return STUB_SHORT_ARRAY_EQUALS;
            case Int:
                return STUB_INT_ARRAY_EQUALS;
            case Long:
                return STUB_LONG_ARRAY_EQUALS;
            case Float:
                return STUB_FLOAT_ARRAY_EQUALS;
            case Double:
                return STUB_DOUBLE_ARRAY_EQUALS;
            default:
                return null;
        }
    }

    public static ForeignCallDescriptor getRegionEqualsStub(ArrayRegionEqualsNode regionEqualsNode, AMD64HotSpotLIRGenerator gen) {
        int directStubCallIndex = regionEqualsNode.getDirectStubCallIndex();
        GraalError.guarantee(-1 <= directStubCallIndex && directStubCallIndex < 9, "invalid direct stub call index");
        ValueNode length = regionEqualsNode.getLength();
        if (directStubCallIndex >= 0 && length.isJavaConstant() &&
                        AMD64ArrayEqualsOp.canGenerateConstantLengthCompare(gen.target(),
                                        StrideUtil.getStrideA(directStubCallIndex),
                                        StrideUtil.getStrideB(directStubCallIndex), length.asJavaConstant().asInt(), gen.getMaxVectorSize())) {
            // Yield constant-length arrays comparison assembly
            return null;
        }
        return directStubCallIndex < 0 ? STUB_REGION_EQUALS_GENERIC : STUBS[directStubCallIndex];
    }

    public AMD64ArrayEqualsStub(ForeignCallDescriptor foreignCallDescriptor, OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super(foreignCallDescriptor.getName(), options, providers, linkage);
    }

    @Snippet
    private static boolean booleanArraysEquals(Pointer array1, Pointer array2, int length) {
        return ArrayEqualsNode.equals(array1, array2, length, JavaKind.Boolean);
    }

    @Snippet
    private static boolean byteArraysEquals(Pointer array1, Pointer array2, int length) {
        return ArrayEqualsNode.equals(array1, array2, length, JavaKind.Byte);
    }

    @Snippet
    private static boolean charArraysEquals(Pointer array1, Pointer array2, int length) {
        return ArrayEqualsNode.equals(array1, array2, length, JavaKind.Char);
    }

    @Snippet
    private static boolean shortArraysEquals(Pointer array1, Pointer array2, int length) {
        return ArrayEqualsNode.equals(array1, array2, length, JavaKind.Short);
    }

    @Snippet
    private static boolean intArraysEquals(Pointer array1, Pointer array2, int length) {
        return ArrayEqualsNode.equals(array1, array2, length, JavaKind.Int);
    }

    @Snippet
    private static boolean longArraysEquals(Pointer array1, Pointer array2, int length) {
        return ArrayEqualsNode.equals(array1, array2, length, JavaKind.Long);
    }

    @Snippet
    private static boolean floatArraysEquals(Pointer array1, Pointer array2, int length) {
        return ArrayEqualsNode.equals(array1, array2, length, JavaKind.Float);
    }

    @Snippet
    private static boolean doubleArraysEquals(Pointer array1, Pointer array2, int length) {
        return ArrayEqualsNode.equals(array1, array2, length, JavaKind.Double);
    }

    @Snippet
    private static boolean arrayRegionEqualsGeneric(Object arrayA, long offsetA, Object arrayB, long offsetB, int length, int stride) {
        return ArrayRegionEqualsNode.regionEquals(arrayA, offsetA, arrayB, offsetB, length, stride);
    }

    @Snippet
    private static boolean arrayRegionEqualsS1S1(Object arrayA, long offsetA, Object arrayB, long offsetB, int length) {
        return ArrayRegionEqualsNode.regionEquals(arrayA, offsetA, arrayB, offsetB, length, S1, S1);
    }

    @Snippet
    private static boolean arrayRegionEqualsS1S2(Object arrayA, long offsetA, Object arrayB, long offsetB, int length) {
        return ArrayRegionEqualsNode.regionEquals(arrayA, offsetA, arrayB, offsetB, length, S1, S2);
    }

    @Snippet
    private static boolean arrayRegionEqualsS1S4(Object arrayA, long offsetA, Object arrayB, long offsetB, int length) {
        return ArrayRegionEqualsNode.regionEquals(arrayA, offsetA, arrayB, offsetB, length, S1, S4);
    }

    @Snippet
    private static boolean arrayRegionEqualsS2S1(Object arrayA, long offsetA, Object arrayB, long offsetB, int length) {
        return ArrayRegionEqualsNode.regionEquals(arrayA, offsetA, arrayB, offsetB, length, S2, S1);
    }

    @Snippet
    private static boolean arrayRegionEqualsS2S2(Object arrayA, long offsetA, Object arrayB, long offsetB, int length) {
        return ArrayRegionEqualsNode.regionEquals(arrayA, offsetA, arrayB, offsetB, length, S2, S2);
    }

    @Snippet
    private static boolean arrayRegionEqualsS2S4(Object arrayA, long offsetA, Object arrayB, long offsetB, int length) {
        return ArrayRegionEqualsNode.regionEquals(arrayA, offsetA, arrayB, offsetB, length, S2, S4);
    }

    @Snippet
    private static boolean arrayRegionEqualsS4S1(Object arrayA, long offsetA, Object arrayB, long offsetB, int length) {
        return ArrayRegionEqualsNode.regionEquals(arrayA, offsetA, arrayB, offsetB, length, S4, S1);
    }

    @Snippet
    private static boolean arrayRegionEqualsS4S2(Object arrayA, long offsetA, Object arrayB, long offsetB, int length) {
        return ArrayRegionEqualsNode.regionEquals(arrayA, offsetA, arrayB, offsetB, length, S4, S2);
    }

    @Snippet
    private static boolean arrayRegionEqualsS4S4(Object arrayA, long offsetA, Object arrayB, long offsetB, int length) {
        return ArrayRegionEqualsNode.regionEquals(arrayA, offsetA, arrayB, offsetB, length, S4, S4);
    }
}
