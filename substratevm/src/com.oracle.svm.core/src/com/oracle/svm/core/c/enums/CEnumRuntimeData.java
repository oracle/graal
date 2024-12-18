/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.c.enums;

import jdk.graal.compiler.word.Word;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jdk.UninterruptibleUtils.CodeUtil;

public abstract class CEnumRuntimeData {
    private static final NullPointerException CACHED_NULL_EXCEPTION = new NullPointerException(
                    "null enum object cannot be converted to C enum integer (typically for automatic conversions on return to C code)");

    /** Stores the sign- or zero-extended C values (depending on the signedness of the C enum). */
    private final long[] javaToC;
    private final int bytesInC;
    private final boolean isCEnumTypeUnsigned;

    protected CEnumRuntimeData(long[] javaToC, int bytesInC, boolean isCEnumTypeUnsigned) {
        this.javaToC = javaToC;
        this.bytesInC = bytesInC;
        this.isCEnumTypeUnsigned = isCEnumTypeUnsigned;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public final boolean enumToBoolean(Enum<?> value) {
        return enumToLong(value) != 0L;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public final byte enumToByte(Enum<?> value) {
        return (byte) enumToLong(value);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public final short enumToShort(Enum<?> value) {
        return (short) enumToLong(value);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public final char enumToChar(Enum<?> value) {
        return (char) CodeUtil.zeroExtend(enumToLong(value), bytesInC * Byte.SIZE);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public final int enumToInt(Enum<?> value) {
        return (int) enumToLong(value);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public final long enumToLong(Enum<?> value) {
        if (value == null) {
            throw CACHED_NULL_EXCEPTION;
        }
        return javaToC[value.ordinal()];
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public final SignedWord enumToSignedWord(Enum<?> value) {
        long result = enumToLong(value);
        return Word.signed(result);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public final UnsignedWord enumToUnsignedWord(Enum<?> value) {
        long result = CodeUtil.zeroExtend(enumToLong(value), bytesInC * Byte.SIZE);
        return Word.unsigned(result);
    }

    /**
     * Reduce the C value to the bits that fit into the C enum type. Then, sign- or zero-extend that
     * value to 64-bit so that it can be compared to the data in the image heap.
     */
    public final Enum<?> longToEnum(long rawValue) {
        long lookupValue;
        if (isCEnumTypeUnsigned) {
            lookupValue = CodeUtil.zeroExtend(rawValue, bytesInC * Byte.SIZE);
        } else {
            lookupValue = CodeUtil.signExtend(rawValue, bytesInC * Byte.SIZE);
        }
        return lookupEnum(lookupValue);
    }

    protected abstract Enum<?> lookupEnum(long value);
}
