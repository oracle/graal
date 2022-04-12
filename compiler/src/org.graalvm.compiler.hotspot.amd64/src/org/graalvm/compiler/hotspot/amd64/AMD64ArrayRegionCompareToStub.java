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
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.nodes.ArrayRegionCompareToNode;

import jdk.vm.ci.meta.JavaKind;

public final class AMD64ArrayRegionCompareToStub extends SnippetStub {

    private static final HotSpotForeignCallDescriptor STUB_S1_S1 = foreignCallDescriptor("stubS1S1");
    private static final HotSpotForeignCallDescriptor STUB_S2_S1 = foreignCallDescriptor("stubS2S1");
    private static final HotSpotForeignCallDescriptor STUB_S2_S2 = foreignCallDescriptor("stubS2S2");
    private static final HotSpotForeignCallDescriptor STUB_S4_S1 = foreignCallDescriptor("stubS4S1");
    private static final HotSpotForeignCallDescriptor STUB_S4_S2 = foreignCallDescriptor("stubS4S2");
    private static final HotSpotForeignCallDescriptor STUB_S4_S4 = foreignCallDescriptor("stubS4S4");

    private static HotSpotForeignCallDescriptor foreignCallDescriptor(String name) {
        return new HotSpotForeignCallDescriptor(LEAF, REEXECUTABLE, NO_LOCATIONS, name, int.class, Object.class, long.class, Object.class, long.class, int.class);
    }

    public static final HotSpotForeignCallDescriptor[] STUBS = {
                    STUB_S1_S1,
                    STUB_S2_S1,
                    STUB_S2_S2,
                    STUB_S4_S1,
                    STUB_S4_S2,
                    STUB_S4_S4,
    };

    public AMD64ArrayRegionCompareToStub(ForeignCallDescriptor foreignCallDescriptor, OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super(foreignCallDescriptor.getName(), options, providers, linkage);
    }

    public static ForeignCallDescriptor getStub(ArrayRegionCompareToNode arrayCompareToNode) {
        JavaKind strideA = arrayCompareToNode.getStrideA();
        JavaKind strideB = arrayCompareToNode.getStrideB();
        switch (strideA) {
            case Byte:
                assert strideB == JavaKind.Byte;
                return STUB_S1_S1;
            case Char:
                if (strideB == JavaKind.Byte) {
                    return STUB_S2_S1;
                } else {
                    assert strideB == JavaKind.Char;
                    return STUB_S2_S2;
                }
            case Int:
                switch (strideB) {
                    case Byte:
                        return STUB_S4_S1;
                    case Char:
                        return STUB_S4_S2;
                    case Int:
                        return STUB_S4_S4;
                    default:
                        throw GraalError.shouldNotReachHere();
                }
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    @Snippet
    private static int stubS1S1(Object arrayA, long offsetA, Object arrayB, long offsetB, int length) {
        return ArrayRegionCompareToNode.compare(arrayA, offsetA, arrayB, offsetB, length, S1, S1);
    }

    @Snippet
    private static int stubS2S1(Object arrayA, long offsetA, Object arrayB, long offsetB, int length) {
        return ArrayRegionCompareToNode.compare(arrayA, offsetA, arrayB, offsetB, length, S2, S1);
    }

    @Snippet
    private static int stubS2S2(Object arrayA, long offsetA, Object arrayB, long offsetB, int length) {
        return ArrayRegionCompareToNode.compare(arrayA, offsetA, arrayB, offsetB, length, S2, S2);
    }

    @Snippet
    private static int stubS4S1(Object arrayA, long offsetA, Object arrayB, long offsetB, int length) {
        return ArrayRegionCompareToNode.compare(arrayA, offsetA, arrayB, offsetB, length, S4, S1);
    }

    @Snippet
    private static int stubS4S2(Object arrayA, long offsetA, Object arrayB, long offsetB, int length) {
        return ArrayRegionCompareToNode.compare(arrayA, offsetA, arrayB, offsetB, length, S4, S2);
    }

    @Snippet
    private static int stubS4S4(Object arrayA, long offsetA, Object arrayB, long offsetB, int length) {
        return ArrayRegionCompareToNode.compare(arrayA, offsetA, arrayB, offsetB, length, S4, S4);
    }
}
