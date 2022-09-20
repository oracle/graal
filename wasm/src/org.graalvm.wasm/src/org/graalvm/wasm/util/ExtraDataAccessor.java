/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.graalvm.wasm.util;

/**
 * Helper class for accessing extra data entries.
 */
public class ExtraDataAccessor {
    private static final int INDICATOR_REMOVAL_MASK = 0x7fff_ffff;

    public static final int COMPACT_IF_LENGTH = 2;
    public static final int EXTENDED_IF_LENGTH = 3;

    public static final int COMPACT_IF_PROFILE_OFFSET = 1;
    public static final int EXTENDED_IF_PROFILE_OFFSET = 2;

    public static final int COMPACT_BR_IF_LENGTH = 2;
    public static final int EXTENDED_BR_IF_LENGTH = 6;

    public static final int COMPACT_BR_IF_PROFILE_OFFSET = 1;
    public static final int EXTENDED_BR_IF_PROFILE_OFFSET = 5;

    public static final int COMPACT_BR_TABLE_PROFILE_OFFSET = 0;
    public static final int EXTENDED_BR_TABLE_PROFILE_OFFSET = 1;

    public static final int COMPACT_BR_TABLE_HEADER_LENGTH = 1;
    public static final int EXTENDED_BR_TABLE_HEADER_LENGTH = 2;

    public static final int CALL_LENGTH = 1;

    public static final int COMPACT_CALL_INDIRECT_LENGTH = 1;
    public static final int EXTENDED_CALL_INDIRECT_LENGTH = 2;

    public static final int COMPACT_CALL_INDIRECT_PROFILE_OFFSET = 0;
    public static final int EXTENDED_CALL_INDIRECT_PROFILE_OFFSET = 1;

    public static final int PRIMITIVE_TYPES = 0;
    public static final int REFERENCE_TYPES = 1;
    public static final int ALL_TYPES = 3;

    public static int compactFirstValueUnsigned(int value) {
        return value >>> 16;
    }

    public static int extendedFirstValueUnsigned(int value) {
        return value & INDICATOR_REMOVAL_MASK;
    }

    public static int compactFirstValueSigned(int value) {
        return value << 1 >> 17;
    }

    public static int extendedFirstValueSigned(int value) {
        return value << 1 >> 1;
    }

    public static int compactSecondValueSigned(int value) {
        return value << 16 >> 16;
    }

    public static int compactThirdValueUnsigned(int value) {
        return value >>> 30;
    }

    public static int compactFourthValueUnsigned(int value) {
        return (value & 0x3F80_0000) >>> 23;
    }

    public static int compactFifthValueUnsigned(int value) {
        return (value & 0x007f_0000) >>> 16;
    }
}
