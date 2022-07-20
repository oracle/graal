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

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.core.common.Stride;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.stubs.SnippetStub;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.nodes.ArrayRegionCompareToNode;

public final class AMD64ArrayRegionCompareToStub extends SnippetStub {

    public AMD64ArrayRegionCompareToStub(OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super(linkage.getDescriptor().getName(), options, providers, linkage);
    }

    @Snippet
    private static int arrayRegionCompareToS1S1(Object arrayA, long offsetA, Object arrayB, long offsetB, int length) {
        return ArrayRegionCompareToNode.compare(arrayA, offsetA, arrayB, offsetB, length, Stride.S1, Stride.S1);
    }

    @Snippet
    private static int arrayRegionCompareToS1S2(Object arrayA, long offsetA, Object arrayB, long offsetB, int length) {
        return ArrayRegionCompareToNode.compare(arrayA, offsetA, arrayB, offsetB, length, Stride.S1, Stride.S2);
    }

    @Snippet
    private static int arrayRegionCompareToS1S4(Object arrayA, long offsetA, Object arrayB, long offsetB, int length) {
        return ArrayRegionCompareToNode.compare(arrayA, offsetA, arrayB, offsetB, length, Stride.S1, Stride.S4);
    }

    @Snippet
    private static int arrayRegionCompareToS2S1(Object arrayA, long offsetA, Object arrayB, long offsetB, int length) {
        return ArrayRegionCompareToNode.compare(arrayA, offsetA, arrayB, offsetB, length, Stride.S2, Stride.S1);
    }

    @Snippet
    private static int arrayRegionCompareToS2S2(Object arrayA, long offsetA, Object arrayB, long offsetB, int length) {
        return ArrayRegionCompareToNode.compare(arrayA, offsetA, arrayB, offsetB, length, Stride.S2, Stride.S2);
    }

    @Snippet
    private static int arrayRegionCompareToS2S4(Object arrayA, long offsetA, Object arrayB, long offsetB, int length) {
        return ArrayRegionCompareToNode.compare(arrayA, offsetA, arrayB, offsetB, length, Stride.S2, Stride.S4);
    }

    @Snippet
    private static int arrayRegionCompareToS4S1(Object arrayA, long offsetA, Object arrayB, long offsetB, int length) {
        return ArrayRegionCompareToNode.compare(arrayA, offsetA, arrayB, offsetB, length, Stride.S4, Stride.S1);
    }

    @Snippet
    private static int arrayRegionCompareToS4S2(Object arrayA, long offsetA, Object arrayB, long offsetB, int length) {
        return ArrayRegionCompareToNode.compare(arrayA, offsetA, arrayB, offsetB, length, Stride.S4, Stride.S2);
    }

    @Snippet
    private static int arrayRegionCompareToS4S4(Object arrayA, long offsetA, Object arrayB, long offsetB, int length) {
        return ArrayRegionCompareToNode.compare(arrayA, offsetA, arrayB, offsetB, length, Stride.S4, Stride.S4);
    }

    @Snippet
    private static int arrayRegionCompareToDynamicStrides(Object arrayA, long offsetA, Object arrayB, long offsetB, int length, int dynamicStrides) {
        return ArrayRegionCompareToNode.compare(arrayA, offsetA, arrayB, offsetB, length, dynamicStrides);
    }
}
