/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.guest.staging;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import com.oracle.svm.shared.util.NumUtil;
import com.oracle.svm.shared.Uninterruptible;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.impl.Word;

public class IsolateArgumentAccess {
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean isNull(IsolateArguments arguments, int optionIndex) {
        int nullFlagIndex = getNullFlagIndex(optionIndex);
        long nullFlag = arguments.getParsedArgs().read(nullFlagIndex);
        assert nullFlag == 0 || nullFlag == 1;
        return nullFlag == 1;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void writeNullFlag(IsolateArguments arguments, int optionIndex, boolean isNull) {
        int nullFlagIndex = getNullFlagIndex(optionIndex);
        writeRawValue(arguments, nullFlagIndex, isNull ? 1 : 0);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static int getNullFlagIndex(int optionIndex) {
        return IsolateArgumentParser.getOptionCount() + optionIndex;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static long readLong(IsolateArguments arguments, int optionIndex) {
        assert !isNull(arguments, optionIndex);
        return readRawUnchecked(arguments, optionIndex);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void writeLong(IsolateArguments arguments, int optionIndex, long value, boolean isNull) {
        assert !isNull || value == 0;
        writeRawValue(arguments, optionIndex, value);
        writeNullFlag(arguments, optionIndex, isNull);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static int readInt(IsolateArguments arguments, int optionIndex) {
        long value = readLong(arguments, optionIndex);
        return NumUtil.safeToInt(value);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void writeInt(IsolateArguments arguments, int optionIndex, int value, boolean isNull) {
        writeLong(arguments, optionIndex, value, isNull);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean readBoolean(IsolateArguments arguments, int optionIndex) {
        long value = readLong(arguments, optionIndex);
        assert value == 0L || value == 1L;
        return value == 1L;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void writeBoolean(IsolateArguments arguments, int optionIndex, boolean value, boolean isNull) {
        writeLong(arguments, optionIndex, value ? 1L : 0L, isNull);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static CCharPointer readCCharPointer(IsolateArguments arguments, int optionIndex) {
        long rawValue = readRawUnchecked(arguments, optionIndex);
        return Word.pointer(rawValue);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void writeCCharPointer(IsolateArguments arguments, int optionIndex, CCharPointer value) {
        writeLong(arguments, optionIndex, value.rawValue(), value == Word.nullPointer());
    }

    /* Reads the value without checking for null. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static long readRawUnchecked(IsolateArguments arguments, int optionIndex) {
        return arguments.getParsedArgs().read(optionIndex);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void writeRawValue(IsolateArguments arguments, int index, long value) {
        arguments.getParsedArgs().write(index, value);
    }
}
