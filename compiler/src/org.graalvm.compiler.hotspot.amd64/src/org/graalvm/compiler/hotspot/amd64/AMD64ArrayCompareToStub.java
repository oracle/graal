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
import org.graalvm.compiler.replacements.nodes.ArrayCompareToNode;
import org.graalvm.word.Pointer;

import jdk.vm.ci.meta.JavaKind;

public final class AMD64ArrayCompareToStub extends SnippetStub {

    public static final HotSpotForeignCallDescriptor STUB_BYTE_ARRAY_COMPARE_TO_BYTE_ARRAY = new HotSpotForeignCallDescriptor(LEAF, REEXECUTABLE, NO_LOCATIONS,
                    "byteArrayCompareToByteArray", int.class, Pointer.class, Pointer.class, int.class, int.class);
    public static final HotSpotForeignCallDescriptor STUB_BYTE_ARRAY_COMPARE_TO_CHAR_ARRAY = new HotSpotForeignCallDescriptor(LEAF, REEXECUTABLE, NO_LOCATIONS,
                    "byteArrayCompareToCharArray", int.class, Pointer.class, Pointer.class, int.class, int.class);
    public static final HotSpotForeignCallDescriptor STUB_CHAR_ARRAY_COMPARE_TO_BYTE_ARRAY = new HotSpotForeignCallDescriptor(LEAF, REEXECUTABLE, NO_LOCATIONS,
                    "charArrayCompareToByteArray", int.class, Pointer.class, Pointer.class, int.class, int.class);
    public static final HotSpotForeignCallDescriptor STUB_CHAR_ARRAY_COMPARE_TO_CHAR_ARRAY = new HotSpotForeignCallDescriptor(LEAF, REEXECUTABLE, NO_LOCATIONS,
                    "charArrayCompareToCharArray", int.class, Pointer.class, Pointer.class, int.class, int.class);

    public AMD64ArrayCompareToStub(ForeignCallDescriptor foreignCallDescriptor, OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super(foreignCallDescriptor.getName(), options, providers, linkage);
    }

    @Snippet
    private static int byteArrayCompareToByteArray(Pointer array1, Pointer array2, int length1, int length2) {
        return ArrayCompareToNode.compareTo(array1, array2, length1, length2, JavaKind.Byte, JavaKind.Byte);
    }

    @Snippet
    private static int byteArrayCompareToCharArray(Pointer array1, Pointer array2, int length1, int length2) {
        return ArrayCompareToNode.compareTo(array1, array2, length1, length2, JavaKind.Byte, JavaKind.Char);
    }

    @Snippet
    private static int charArrayCompareToByteArray(Pointer array1, Pointer array2, int length1, int length2) {
        return ArrayCompareToNode.compareTo(array1, array2, length1, length2, JavaKind.Char, JavaKind.Byte);
    }

    @Snippet
    private static int charArrayCompareToCharArray(Pointer array1, Pointer array2, int length1, int length2) {
        return ArrayCompareToNode.compareTo(array1, array2, length1, length2, JavaKind.Char, JavaKind.Char);
    }
}
