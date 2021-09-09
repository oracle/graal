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

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.stubs.SnippetStub;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.nodes.ArrayEqualsNode;
import org.graalvm.compiler.replacements.nodes.ArrayRegionEqualsNode;
import org.graalvm.word.Pointer;

import jdk.vm.ci.meta.JavaKind;

public final class AMD64ArrayEqualsStub extends SnippetStub {

    public static final HotSpotForeignCallDescriptor STUB_BOOLEAN_ARRAY_EQUALS = new HotSpotForeignCallDescriptor(LEAF, REEXECUTABLE, NO_LOCATIONS,
                    "booleanArraysEquals", boolean.class, Pointer.class, Pointer.class, int.class);
    public static final HotSpotForeignCallDescriptor STUB_BYTE_ARRAY_EQUALS = new HotSpotForeignCallDescriptor(LEAF, REEXECUTABLE, NO_LOCATIONS,
                    "byteArraysEquals", boolean.class, Pointer.class, Pointer.class, int.class);
    public static final HotSpotForeignCallDescriptor STUB_CHAR_ARRAY_EQUALS = new HotSpotForeignCallDescriptor(LEAF, REEXECUTABLE, NO_LOCATIONS,
                    "charArraysEquals", boolean.class, Pointer.class, Pointer.class, int.class);
    public static final HotSpotForeignCallDescriptor STUB_SHORT_ARRAY_EQUALS = new HotSpotForeignCallDescriptor(LEAF, REEXECUTABLE, NO_LOCATIONS,
                    "shortArraysEquals", boolean.class, Pointer.class, Pointer.class, int.class);
    public static final HotSpotForeignCallDescriptor STUB_INT_ARRAY_EQUALS = new HotSpotForeignCallDescriptor(LEAF, REEXECUTABLE, NO_LOCATIONS,
                    "intArraysEquals", boolean.class, Pointer.class, Pointer.class, int.class);
    public static final HotSpotForeignCallDescriptor STUB_LONG_ARRAY_EQUALS = new HotSpotForeignCallDescriptor(LEAF, REEXECUTABLE, NO_LOCATIONS,
                    "longArraysEquals", boolean.class, Pointer.class, Pointer.class, int.class);
    public static final HotSpotForeignCallDescriptor STUB_FLOAT_ARRAY_EQUALS = new HotSpotForeignCallDescriptor(LEAF, REEXECUTABLE, NO_LOCATIONS,
                    "floatArraysEquals", boolean.class, Pointer.class, Pointer.class, int.class);
    public static final HotSpotForeignCallDescriptor STUB_DOUBLE_ARRAY_EQUALS = new HotSpotForeignCallDescriptor(LEAF, REEXECUTABLE, NO_LOCATIONS,
                    "doubleArraysEquals", boolean.class, Pointer.class, Pointer.class, int.class);

    public static final HotSpotForeignCallDescriptor STUB_BYTE_ARRAY_EQUALS_DIRECT = new HotSpotForeignCallDescriptor(LEAF, REEXECUTABLE, NO_LOCATIONS,
                    "byteArraysEqualsDirect", boolean.class, Pointer.class, Pointer.class, int.class);
    public static final HotSpotForeignCallDescriptor STUB_CHAR_ARRAY_EQUALS_DIRECT = new HotSpotForeignCallDescriptor(LEAF, REEXECUTABLE, NO_LOCATIONS,
                    "charArraysEqualsDirect", boolean.class, Pointer.class, Pointer.class, int.class);
    public static final HotSpotForeignCallDescriptor STUB_CHAR_ARRAY_EQUALS_BYTE_ARRAY = new HotSpotForeignCallDescriptor(LEAF, REEXECUTABLE, NO_LOCATIONS,
                    "charArrayEqualsByteArray", boolean.class, Pointer.class, Pointer.class, int.class);

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
    private static boolean byteArraysEqualsDirect(Pointer array1, Pointer array2, int length) {
        return ArrayRegionEqualsNode.regionEquals(array1, array2, length, JavaKind.Byte, JavaKind.Byte);
    }

    @Snippet
    private static boolean charArraysEqualsDirect(Pointer array1, Pointer array2, int length) {
        return ArrayRegionEqualsNode.regionEquals(array1, array2, length, JavaKind.Char, JavaKind.Char);
    }

    @Snippet
    private static boolean charArrayEqualsByteArray(Pointer array1, Pointer array2, int length) {
        return ArrayRegionEqualsNode.regionEquals(array1, array2, length, JavaKind.Char, JavaKind.Byte);
    }
}
