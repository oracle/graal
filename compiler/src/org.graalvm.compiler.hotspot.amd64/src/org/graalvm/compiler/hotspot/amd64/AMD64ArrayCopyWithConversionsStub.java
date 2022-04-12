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

import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Reexecutability.NOT_REEXECUTABLE;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.LEAF;
import static org.graalvm.compiler.replacements.ArrayIndexOf.S1;
import static org.graalvm.compiler.replacements.ArrayIndexOf.S2;
import static org.graalvm.compiler.replacements.ArrayIndexOf.S4;
import static org.graalvm.compiler.replacements.nodes.ArrayCopyWithConversionsNode.KILLED_LOCATIONS;

import jdk.vm.ci.meta.JavaKind;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.stubs.SnippetStub;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.nodes.ArrayCopyWithConversionsNode;

/**
 * Arraycopy operation for arbitrary source and destination arrays, with arbitrary byte offset, with
 * support for arbitrary compression and inflation of {@link JavaKind#Byte 8 bit},
 * {@link JavaKind#Char 16 bit} or {@link JavaKind#Int 32 bit} array elements.
 *
 * @see org.graalvm.compiler.lir.amd64.AMD64ArrayCopyWithConversionsOp
 */
public final class AMD64ArrayCopyWithConversionsStub extends SnippetStub {

    private static final HotSpotForeignCallDescriptor STUB_COPY = new HotSpotForeignCallDescriptor(LEAF, NOT_REEXECUTABLE, KILLED_LOCATIONS,
                    "stubS1S1", void.class, Object.class, long.class, Object.class, long.class, int.class);
    private static final HotSpotForeignCallDescriptor STUB_S1_S2 = new HotSpotForeignCallDescriptor(LEAF, NOT_REEXECUTABLE, KILLED_LOCATIONS,
                    "stubS1S2", void.class, Object.class, long.class, Object.class, long.class, int.class);
    private static final HotSpotForeignCallDescriptor STUB_S1_S4 = new HotSpotForeignCallDescriptor(LEAF, NOT_REEXECUTABLE, KILLED_LOCATIONS,
                    "stubS1S4", void.class, Object.class, long.class, Object.class, long.class, int.class);
    private static final HotSpotForeignCallDescriptor STUB_S2_S1 = new HotSpotForeignCallDescriptor(LEAF, NOT_REEXECUTABLE, KILLED_LOCATIONS,
                    "stubS2S1", void.class, Object.class, long.class, Object.class, long.class, int.class);
    private static final HotSpotForeignCallDescriptor STUB_S2_S4 = new HotSpotForeignCallDescriptor(LEAF, NOT_REEXECUTABLE, KILLED_LOCATIONS,
                    "stubS2S4", void.class, Object.class, long.class, Object.class, long.class, int.class);
    private static final HotSpotForeignCallDescriptor STUB_S4_S1 = new HotSpotForeignCallDescriptor(LEAF, NOT_REEXECUTABLE, KILLED_LOCATIONS,
                    "stubS4S1", void.class, Object.class, long.class, Object.class, long.class, int.class);
    private static final HotSpotForeignCallDescriptor STUB_S4_S2 = new HotSpotForeignCallDescriptor(LEAF, NOT_REEXECUTABLE, KILLED_LOCATIONS,
                    "stubS4S2", void.class, Object.class, long.class, Object.class, long.class, int.class);

    public static final HotSpotForeignCallDescriptor[] STUBS = {
                    STUB_COPY,
                    STUB_S1_S2,
                    STUB_S1_S4,
                    STUB_S2_S1,
                    STUB_S2_S4,
                    STUB_S4_S1,
                    STUB_S4_S2,
    };

    public static HotSpotForeignCallDescriptor getStub(ArrayCopyWithConversionsNode node) {
        switch (node.getStrideSrc()) {
            case Byte:
                switch (node.getStrideDst()) {
                    case Byte:
                        return STUB_COPY;
                    case Char:
                        return STUB_S1_S2;
                    case Int:
                        return STUB_S1_S4;
                    default:
                        throw GraalError.shouldNotReachHere();
                }
            case Char:
                switch (node.getStrideDst()) {
                    case Byte:
                        return STUB_S2_S1;
                    case Int:
                        return STUB_S2_S4;
                    default:
                        throw GraalError.shouldNotReachHere();
                }
            case Int:
                switch (node.getStrideDst()) {
                    case Byte:
                        return STUB_S4_S1;
                    case Char:
                        return STUB_S4_S2;
                    default:
                        throw GraalError.shouldNotReachHere();
                }
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    public AMD64ArrayCopyWithConversionsStub(ForeignCallDescriptor foreignCallDescriptor, OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super(foreignCallDescriptor.getName(), options, providers, linkage);
    }

    @Snippet
    private static void stubS1S1(Object arraySrc, long offsetSrc, Object arrayDst, long offsetDst, int length) {
        ArrayCopyWithConversionsNode.arrayCopy(arraySrc, offsetSrc, arrayDst, offsetDst, length, S1, S1);
    }

    @Snippet
    private static void stubS1S2(Object arraySrc, long offsetSrc, Object arrayDst, long offsetDst, int length) {
        ArrayCopyWithConversionsNode.arrayCopy(arraySrc, offsetSrc, arrayDst, offsetDst, length, S1, S2);
    }

    @Snippet
    private static void stubS1S4(Object arraySrc, long offsetSrc, Object arrayDst, long offsetDst, int length) {
        ArrayCopyWithConversionsNode.arrayCopy(arraySrc, offsetSrc, arrayDst, offsetDst, length, S1, S4);
    }

    @Snippet
    private static void stubS2S1(Object arraySrc, long offsetSrc, Object arrayDst, long offsetDst, int length) {
        ArrayCopyWithConversionsNode.arrayCopy(arraySrc, offsetSrc, arrayDst, offsetDst, length, S2, S1);
    }

    @Snippet
    private static void stubS2S4(Object arraySrc, long offsetSrc, Object arrayDst, long offsetDst, int length) {
        ArrayCopyWithConversionsNode.arrayCopy(arraySrc, offsetSrc, arrayDst, offsetDst, length, S2, S4);
    }

    @Snippet
    private static void stubS4S1(Object arraySrc, long offsetSrc, Object arrayDst, long offsetDst, int length) {
        ArrayCopyWithConversionsNode.arrayCopy(arraySrc, offsetSrc, arrayDst, offsetDst, length, S4, S1);
    }

    @Snippet
    private static void stubS4S2(Object arraySrc, long offsetSrc, Object arrayDst, long offsetDst, int length) {
        ArrayCopyWithConversionsNode.arrayCopy(arraySrc, offsetSrc, arrayDst, offsetDst, length, S4, S2);
    }
}
