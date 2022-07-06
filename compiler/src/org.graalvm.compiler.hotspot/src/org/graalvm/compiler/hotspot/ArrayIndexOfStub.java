/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot;

import jdk.vm.ci.meta.JavaKind;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.core.common.Stride;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.stubs.SnippetStub;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.nodes.ArrayIndexOfNode;

public class ArrayIndexOfStub extends SnippetStub {

    public ArrayIndexOfStub(OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super(linkage.getDescriptor().getName(), options, providers, linkage);
    }

    @Snippet
    private static int indexOfTwoConsecutiveS1(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Void, Stride.S1, true, false, array, offset, arrayLength, fromIndex, v1, v2);
    }

    @Snippet
    private static int indexOfTwoConsecutiveS2(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Void, Stride.S2, true, false, array, offset, arrayLength, fromIndex, v1, v2);
    }

    @Snippet
    private static int indexOfTwoConsecutiveS4(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Void, Stride.S4, true, false, array, offset, arrayLength, fromIndex, v1, v2);
    }

    @Snippet
    private static int indexOf1S1(Object array, long offset, int arrayLength, int fromIndex, int v1) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Void, Stride.S1, false, false, array, offset, arrayLength, fromIndex, v1);
    }

    @Snippet
    private static int indexOf2S1(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Void, Stride.S1, false, false, array, offset, arrayLength, fromIndex, v1, v2);
    }

    @Snippet
    private static int indexOf3S1(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Void, Stride.S1, false, false, array, offset, arrayLength, fromIndex, v1, v2, v3);
    }

    @Snippet
    private static int indexOf4S1(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3, int v4) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Void, Stride.S1, false, false, array, offset, arrayLength, fromIndex, v1, v2, v3, v4);
    }

    @Snippet
    private static int indexOf1S2(Object array, long offset, int arrayLength, int fromIndex, int v1) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Void, Stride.S2, false, false, array, offset, arrayLength, fromIndex, v1);
    }

    @Snippet
    private static int indexOf2S2(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Void, Stride.S2, false, false, array, offset, arrayLength, fromIndex, v1, v2);
    }

    @Snippet
    private static int indexOf3S2(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Void, Stride.S2, false, false, array, offset, arrayLength, fromIndex, v1, v2, v3);
    }

    @Snippet
    private static int indexOf4S2(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3, int v4) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Void, Stride.S2, false, false, array, offset, arrayLength, fromIndex, v1, v2, v3, v4);
    }

    @Snippet
    private static int indexOf1S4(Object array, long offset, int arrayLength, int fromIndex, int v1) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Void, Stride.S4, false, false, array, offset, arrayLength, fromIndex, v1);
    }

    @Snippet
    private static int indexOf2S4(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Void, Stride.S4, false, false, array, offset, arrayLength, fromIndex, v1, v2);
    }

    @Snippet
    private static int indexOf3S4(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Void, Stride.S4, false, false, array, offset, arrayLength, fromIndex, v1, v2, v3);
    }

    @Snippet
    private static int indexOf4S4(Object array, long offset, int arrayLength, int fromIndex, int v1, int v2, int v3, int v4) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Void, Stride.S4, false, false, array, offset, arrayLength, fromIndex, v1, v2, v3, v4);
    }

    @Snippet
    private static int indexOfWithMaskS1(Object array, long offset, int length, int fromIndex, int v1, int mask1) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Void, Stride.S1, false, true, array, offset, length, fromIndex, v1, mask1);
    }

    @Snippet
    private static int indexOfWithMaskS2(Object array, long offset, int length, int fromIndex, int v1, int mask1) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Void, Stride.S2, false, true, array, offset, length, fromIndex, v1, mask1);
    }

    @Snippet
    private static int indexOfWithMaskS4(Object array, long offset, int length, int fromIndex, int v1, int mask1) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Void, Stride.S4, false, true, array, offset, length, fromIndex, v1, mask1);
    }

    @Snippet
    private static int indexOfTwoConsecutiveWithMaskS1(Object array, long offset, int length, int fromIndex, int v1, int v2, int mask1, int mask2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Void, Stride.S1, true, true, array, offset, length, fromIndex, v1, v2, mask1, mask2);
    }

    @Snippet
    private static int indexOfTwoConsecutiveWithMaskS2(Object array, long offset, int length, int fromIndex, int v1, int v2, int mask1, int mask2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Void, Stride.S2, true, true, array, offset, length, fromIndex, v1, v2, mask1, mask2);
    }

    @Snippet
    private static int indexOfTwoConsecutiveWithMaskS4(Object array, long offset, int length, int fromIndex, int v1, int v2, int mask1, int mask2) {
        return ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Void, Stride.S4, true, true, array, offset, length, fromIndex, v1, v2, mask1, mask2);
    }
}
