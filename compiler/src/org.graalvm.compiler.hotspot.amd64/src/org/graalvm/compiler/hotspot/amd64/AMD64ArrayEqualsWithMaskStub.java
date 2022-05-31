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

import static org.graalvm.compiler.core.common.StrideUtil.NONE;
import static org.graalvm.compiler.core.common.StrideUtil.S1;
import static org.graalvm.compiler.core.common.StrideUtil.S2;
import static org.graalvm.compiler.core.common.StrideUtil.S4;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.stubs.SnippetStub;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.amd64.AMD64ArrayRegionEqualsWithMaskNode;

public final class AMD64ArrayEqualsWithMaskStub extends SnippetStub {

    public AMD64ArrayEqualsWithMaskStub(OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super(linkage.getDescriptor().getName(), options, providers, linkage);
    }

    @Snippet
    private static boolean arrayRegionEqualsBS1S1S1(byte[] arrayA, long offsetA, byte[] arrayB, long offsetB, byte[] mask, int length) {
        return AMD64ArrayRegionEqualsWithMaskNode.regionEquals(arrayA, offsetA, arrayB, offsetB, mask, length, S1, S1, S1, S1);
    }

    @Snippet
    private static boolean arrayRegionEqualsBS1S2S1(byte[] arrayA, long offsetA, byte[] arrayB, long offsetB, byte[] mask, int length) {
        return AMD64ArrayRegionEqualsWithMaskNode.regionEquals(arrayA, offsetA, arrayB, offsetB, mask, length, S1, S1, S2, S1);
    }

    @Snippet
    private static boolean arrayRegionEqualsBS1S2S2(byte[] arrayA, long offsetA, byte[] arrayB, long offsetB, byte[] mask, int length) {
        return AMD64ArrayRegionEqualsWithMaskNode.regionEquals(arrayA, offsetA, arrayB, offsetB, mask, length, S1, S1, S2, S2);
    }

    @Snippet
    private static boolean arrayRegionEqualsBS2S1S1(byte[] arrayA, long offsetA, byte[] arrayB, long offsetB, byte[] mask, int length) {
        return AMD64ArrayRegionEqualsWithMaskNode.regionEquals(arrayA, offsetA, arrayB, offsetB, mask, length, S1, S2, S1, S1);
    }

    @Snippet
    private static boolean arrayRegionEqualsBS2S2S1(byte[] arrayA, long offsetA, byte[] arrayB, long offsetB, byte[] mask, int length) {
        return AMD64ArrayRegionEqualsWithMaskNode.regionEquals(arrayA, offsetA, arrayB, offsetB, mask, length, S1, S2, S2, S1);
    }

    @Snippet
    private static boolean arrayRegionEqualsBS2S2S2(byte[] arrayA, long offsetA, byte[] arrayB, long offsetB, byte[] mask, int length) {
        return AMD64ArrayRegionEqualsWithMaskNode.regionEquals(arrayA, offsetA, arrayB, offsetB, mask, length, S1, S2, S2, S2);
    }

    @Snippet
    private static boolean arrayRegionEqualsC(char[] arrayA, long offsetA, char[] arrayB, long offsetB, char[] mask, int length) {
        return AMD64ArrayRegionEqualsWithMaskNode.regionEquals(arrayA, offsetA, arrayB, offsetB, mask, length, S2, S2, S2, S2);
    }

    @Snippet
    private static boolean arrayRegionEqualsS1S1(Object arrayA, long offsetA, Object arrayB, long offsetB, byte[] mask, int length) {
        return AMD64ArrayRegionEqualsWithMaskNode.regionEquals(arrayA, offsetA, arrayB, offsetB, mask, length, NONE, S1, S1, S1);
    }

    @Snippet
    private static boolean arrayRegionEqualsS1S2(Object arrayA, long offsetA, Object arrayB, long offsetB, byte[] mask, int length) {
        return AMD64ArrayRegionEqualsWithMaskNode.regionEquals(arrayA, offsetA, arrayB, offsetB, mask, length, NONE, S1, S2, S2);
    }

    @Snippet
    private static boolean arrayRegionEqualsS1S4(Object arrayA, long offsetA, Object arrayB, long offsetB, byte[] mask, int length) {
        return AMD64ArrayRegionEqualsWithMaskNode.regionEquals(arrayA, offsetA, arrayB, offsetB, mask, length, NONE, S1, S4, S4);
    }

    @Snippet
    private static boolean arrayRegionEqualsS2S1(Object arrayA, long offsetA, Object arrayB, long offsetB, byte[] mask, int length) {
        return AMD64ArrayRegionEqualsWithMaskNode.regionEquals(arrayA, offsetA, arrayB, offsetB, mask, length, NONE, S2, S1, S1);
    }

    @Snippet
    private static boolean arrayRegionEqualsS2S2(Object arrayA, long offsetA, Object arrayB, long offsetB, byte[] mask, int length) {
        return AMD64ArrayRegionEqualsWithMaskNode.regionEquals(arrayA, offsetA, arrayB, offsetB, mask, length, NONE, S2, S2, S2);
    }

    @Snippet
    private static boolean arrayRegionEqualsS2S4(Object arrayA, long offsetA, Object arrayB, long offsetB, byte[] mask, int length) {
        return AMD64ArrayRegionEqualsWithMaskNode.regionEquals(arrayA, offsetA, arrayB, offsetB, mask, length, NONE, S2, S4, S4);
    }

    @Snippet
    private static boolean arrayRegionEqualsS4S1(Object arrayA, long offsetA, Object arrayB, long offsetB, byte[] mask, int length) {
        return AMD64ArrayRegionEqualsWithMaskNode.regionEquals(arrayA, offsetA, arrayB, offsetB, mask, length, NONE, S4, S1, S1);
    }

    @Snippet
    private static boolean arrayRegionEqualsS4S2(Object arrayA, long offsetA, Object arrayB, long offsetB, byte[] mask, int length) {
        return AMD64ArrayRegionEqualsWithMaskNode.regionEquals(arrayA, offsetA, arrayB, offsetB, mask, length, NONE, S4, S2, S2);
    }

    @Snippet
    private static boolean arrayRegionEqualsS4S4(Object arrayA, long offsetA, Object arrayB, long offsetB, byte[] mask, int length) {
        return AMD64ArrayRegionEqualsWithMaskNode.regionEquals(arrayA, offsetA, arrayB, offsetB, mask, length, NONE, S4, S4, S4);
    }

    @Snippet
    private static boolean arrayRegionEqualsDynamicStrides(Object arrayA, long offsetA, Object arrayB, long offsetB, byte[] mask, int length, int dynamicStrides) {
        return AMD64ArrayRegionEqualsWithMaskNode.regionEquals(arrayA, offsetA, arrayB, offsetB, mask, length, dynamicStrides);
    }
}
