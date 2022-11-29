/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.nodes;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.GraalError;

public final class CalcStringAttributesForeignCalls {
    private static final ForeignCallDescriptor STUB_CALC_STRING_ATTRIBUTES_LATIN1 = foreignCallDescriptor("calcStringAttributesLatin1", int.class);
    private static final ForeignCallDescriptor STUB_CALC_STRING_ATTRIBUTES_BMP = foreignCallDescriptor("calcStringAttributesBMP", int.class);
    private static final ForeignCallDescriptor STUB_CALC_STRING_ATTRIBUTES_UTF8_VALID = foreignCallDescriptor("calcStringAttributesUTF8Valid", long.class);
    private static final ForeignCallDescriptor STUB_CALC_STRING_ATTRIBUTES_UTF8_UNKNOWN = foreignCallDescriptor("calcStringAttributesUTF8Unknown", long.class);
    private static final ForeignCallDescriptor STUB_CALC_STRING_ATTRIBUTES_UTF16_VALID = foreignCallDescriptor("calcStringAttributesUTF16Valid", long.class);
    private static final ForeignCallDescriptor STUB_CALC_STRING_ATTRIBUTES_UTF16_UNKNOWN = foreignCallDescriptor("calcStringAttributesUTF16Unknown", long.class);
    private static final ForeignCallDescriptor STUB_CALC_STRING_ATTRIBUTES_UTF32 = foreignCallDescriptor("calcStringAttributesUTF32", int.class);
    public static final ForeignCallDescriptor[] STUBS = {
                    STUB_CALC_STRING_ATTRIBUTES_LATIN1,
                    STUB_CALC_STRING_ATTRIBUTES_BMP,
                    STUB_CALC_STRING_ATTRIBUTES_UTF8_VALID,
                    STUB_CALC_STRING_ATTRIBUTES_UTF8_UNKNOWN,
                    STUB_CALC_STRING_ATTRIBUTES_UTF16_VALID,
                    STUB_CALC_STRING_ATTRIBUTES_UTF16_UNKNOWN,
                    STUB_CALC_STRING_ATTRIBUTES_UTF32};

    private static ForeignCallDescriptor foreignCallDescriptor(String name, Class<?> resultType) {
        return ForeignCalls.pureFunctionForeignCallDescriptor(name, resultType, Object.class, long.class, int.class);
    }

    public static ForeignCallDescriptor getStub(CalcStringAttributesNode node) {
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
}
