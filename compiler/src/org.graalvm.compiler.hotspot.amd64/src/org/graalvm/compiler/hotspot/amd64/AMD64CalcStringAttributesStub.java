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
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.stubs.SnippetStub;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.amd64.AMD64CalcStringAttributesNode;

public final class AMD64CalcStringAttributesStub extends SnippetStub {

    private static final HotSpotForeignCallDescriptor STUB_CALC_STRING_ATTRIBUTES_LATIN1 = foreignCallDescriptor("calcStringAttributesLatin1", int.class);
    private static final HotSpotForeignCallDescriptor STUB_CALC_STRING_ATTRIBUTES_BMP = foreignCallDescriptor("calcStringAttributesBMP", int.class);
    private static final HotSpotForeignCallDescriptor STUB_CALC_STRING_ATTRIBUTES_UTF8_VALID = foreignCallDescriptor("calcStringAttributesUTF8Valid", long.class);
    private static final HotSpotForeignCallDescriptor STUB_CALC_STRING_ATTRIBUTES_UTF8_UNKNOWN = foreignCallDescriptor("calcStringAttributesUTF8Unknown", long.class);
    private static final HotSpotForeignCallDescriptor STUB_CALC_STRING_ATTRIBUTES_UTF16_VALID = foreignCallDescriptor("calcStringAttributesUTF16Valid", long.class);
    private static final HotSpotForeignCallDescriptor STUB_CALC_STRING_ATTRIBUTES_UTF16_UNKNOWN = foreignCallDescriptor("calcStringAttributesUTF16Unknown", long.class);
    private static final HotSpotForeignCallDescriptor STUB_CALC_STRING_ATTRIBUTES_UTF32 = foreignCallDescriptor("calcStringAttributesUTF32", int.class);

    private static HotSpotForeignCallDescriptor foreignCallDescriptor(String name, Class<?> resultType) {
        return new HotSpotForeignCallDescriptor(LEAF, REEXECUTABLE, NO_LOCATIONS, name, resultType, Object.class, long.class, int.class);
    }

    public static final HotSpotForeignCallDescriptor[] STUBS = {
                    STUB_CALC_STRING_ATTRIBUTES_LATIN1,
                    STUB_CALC_STRING_ATTRIBUTES_BMP,
                    STUB_CALC_STRING_ATTRIBUTES_UTF8_VALID,
                    STUB_CALC_STRING_ATTRIBUTES_UTF8_UNKNOWN,
                    STUB_CALC_STRING_ATTRIBUTES_UTF16_VALID,
                    STUB_CALC_STRING_ATTRIBUTES_UTF16_UNKNOWN,
                    STUB_CALC_STRING_ATTRIBUTES_UTF32};

    public static HotSpotForeignCallDescriptor getStub(AMD64CalcStringAttributesNode node) {
        switch (node.getOp()) {
            case LATIN1:
                return STUB_CALC_STRING_ATTRIBUTES_LATIN1;
            case BMP:
                return STUB_CALC_STRING_ATTRIBUTES_BMP;
            case UTF_8:
                if (node.isAssumeValid()) {
                    return STUB_CALC_STRING_ATTRIBUTES_UTF8_VALID;
                } else {
                    return STUB_CALC_STRING_ATTRIBUTES_UTF8_UNKNOWN;
                }
            case UTF_16:
                if (node.isAssumeValid()) {
                    return STUB_CALC_STRING_ATTRIBUTES_UTF16_VALID;
                } else {
                    return STUB_CALC_STRING_ATTRIBUTES_UTF16_UNKNOWN;
                }
            case UTF_32:
                return STUB_CALC_STRING_ATTRIBUTES_UTF32;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    public AMD64CalcStringAttributesStub(ForeignCallDescriptor foreignCallDescriptor, OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super(foreignCallDescriptor.getName(), options, providers, linkage);
    }

    @Snippet
    private static int calcStringAttributesLatin1(Object array, long offset, int length) {
        return AMD64CalcStringAttributesNode.latin1Internal(array, offset, length);
    }

    @Snippet
    private static int calcStringAttributesBMP(Object array, long offset, int length) {
        return AMD64CalcStringAttributesNode.bmpInternal(array, offset, length);
    }

    @Snippet
    private static long calcStringAttributesUTF8Valid(Object array, long offset, int length) {
        return AMD64CalcStringAttributesNode.utf8Internal(true, array, offset, length);
    }

    @Snippet
    private static long calcStringAttributesUTF8Unknown(Object array, long offset, int length) {
        return AMD64CalcStringAttributesNode.utf8Internal(false, array, offset, length);
    }

    @Snippet
    private static long calcStringAttributesUTF16Valid(Object array, long offset, int length) {
        return AMD64CalcStringAttributesNode.utf16Internal(true, array, offset, length);
    }

    @Snippet
    private static long calcStringAttributesUTF16Unknown(Object array, long offset, int length) {
        return AMD64CalcStringAttributesNode.utf16Internal(false, array, offset, length);
    }

    @Snippet
    private static int calcStringAttributesUTF32(Object array, long offset, int length) {
        return AMD64CalcStringAttributesNode.utf32Internal(array, offset, length);
    }
}
