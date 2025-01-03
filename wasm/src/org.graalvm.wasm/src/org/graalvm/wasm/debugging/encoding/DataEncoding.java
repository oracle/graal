/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.wasm.debugging.encoding;

/**
 * Internal representation of data encoding extracted from Dwarf debug information.
 */
@SuppressWarnings("unused")
public final class DataEncoding {
    private static final int NUMBER = 1 << 0;
    private static final int BOOLEAN = 1 << 1;
    private static final int STRING = 1 << 2;
    private static final int BYTE_ARRAY = 1 << 3;

    private static final int LEN_1 = 1 << 8;
    private static final int LEN_2 = 1 << 9;
    private static final int LEN_4 = 1 << 10;
    private static final int LEN_8 = 1 << 11;
    private static final int LEB128_SIGNED = 1 << 12;
    private static final int LEB128_UNSIGNED = 1 << 13;
    private static final int FLAG = 1 << 14;

    private static final int FLOAT = 1 << 15;

    private static final int ADDRESS = 1 << 24;
    private static final int BLOCK = 1 << 25;
    private static final int CONSTANT = 1 << 26;
    private static final int EXPRESSION = 1 << 27;
    private static final int REFERENCE = 1 << 28;
    private static final int INDIRECT = 1 << 29;
    private static final int UNSIGNED = 1 << 30;
    private static final int UNSUPPORTED = 1 << 31;

    private DataEncoding() {
    }

    public static boolean isNumber(int encoding) {
        return (NUMBER & encoding) != 0;
    }

    public static boolean isBoolean(int encoding) {
        return (BOOLEAN & encoding) != 0;
    }

    public static boolean isString(int encoding) {
        return (STRING & encoding) != 0;
    }

    public static boolean isByteArray(int encoding) {
        return (BYTE_ARRAY & encoding) != 0;
    }

    public static boolean isLen1(int encoding) {
        return (LEN_1 & encoding) != 0;
    }

    public static boolean isLen2(int encoding) {
        return (LEN_2 & encoding) != 0;
    }

    public static boolean isLen4(int encoding) {
        return (LEN_4 & encoding) != 0;
    }

    public static boolean isLen8(int encoding) {
        return (LEN_8 & encoding) != 0;
    }

    public static boolean isLeb128Signed(int encoding) {
        return (LEB128_SIGNED & encoding) != 0;
    }

    public static boolean isLeb128Unsigned(int encoding) {
        return (LEB128_UNSIGNED & encoding) != 0;
    }

    public static boolean isFlag(int encoding) {
        return (FLAG & encoding) != 0;
    }

    public static boolean isByte(int encoding) {
        return isNumber(encoding) && isLen1(encoding);
    }

    public static boolean isShort(int encoding) {
        return isNumber(encoding) && isLen2(encoding);
    }

    public static boolean isInt(int encoding) {
        return isNumber(encoding) && (isLen4(encoding) || isLeb128Signed(encoding) || isLeb128Unsigned(encoding)) && (FLOAT & encoding) == 0;
    }

    public static boolean isLong(int encoding) {
        return isNumber(encoding) && isLen8(encoding) && (FLOAT & encoding) == 0;
    }

    public static boolean isFloat(int encoding) {
        return isNumber(encoding) && isLen4(encoding) && (FLOAT & encoding) != 0;
    }

    public static boolean isDouble(int encoding) {
        return isNumber(encoding) && isLen8(encoding) && (FLOAT & encoding) != 0;
    }

    public static boolean isUnsupported(int encoding) {
        return (UNSUPPORTED & encoding) != 0;
    }

    public static boolean isConstant(int encoding) {
        return (CONSTANT & encoding) != 0;
    }

    public static boolean isExpression(int encoding) {
        return (EXPRESSION & encoding) != 0;
    }

    public static boolean isUnsigned(int encoding) {
        return (UNSIGNED & encoding) != 0;
    }

    public static int createByte() {
        return NUMBER | LEN_1;
    }

    public static int createUnsignedByte() {
        return NUMBER | LEN_1 | UNSIGNED;
    }

    public static int createShort() {
        return NUMBER | LEN_2;
    }

    public static int createUnsignedShort() {
        return NUMBER | LEN_2 | UNSIGNED;
    }

    public static int createInt() {
        return NUMBER | LEN_4;
    }

    public static int createUnsignedInt() {
        return NUMBER | LEN_4 | UNSIGNED;
    }

    public static int createLong() {
        return NUMBER | LEN_8;
    }

    public static int createUnsignedLong() {
        return NUMBER | LEN_8 | UNSIGNED;
    }

    public static int createBoolean() {
        return BOOLEAN;
    }

    public static int createFloat() {
        return NUMBER | LEN_4 | FLOAT;
    }

    public static int createDouble() {
        return NUMBER | LEN_8 | FLOAT;
    }

    public static int createUnsupported() {
        return UNSUPPORTED;
    }

    public static int createString() {
        return STRING;
    }

    public static int createByteArray() {
        return BYTE_ARRAY;
    }

    public static int fromForm(int form) {
        return switch (form) {
            case Forms.ADDR -> NUMBER | LEN_4 | ADDRESS;
            case Forms.BLOCK2 -> BYTE_ARRAY | LEN_2 | BLOCK;
            case Forms.BLOCK4 -> BYTE_ARRAY | LEN_4 | BLOCK;
            case Forms.DATA2 -> NUMBER | LEN_2 | CONSTANT;
            case Forms.DATA4 -> NUMBER | LEN_4 | CONSTANT;
            case Forms.DATA8 -> NUMBER | LEN_8 | CONSTANT;
            case Forms.STRING -> STRING | CONSTANT;
            case Forms.BLOCK -> BYTE_ARRAY | LEB128_UNSIGNED | BLOCK;
            case Forms.BLOCK1 -> BYTE_ARRAY | LEN_1 | BLOCK;
            case Forms.DATA1 -> NUMBER | LEN_1 | CONSTANT;
            case Forms.FLAG -> BOOLEAN | LEN_1;
            case Forms.SDATA -> NUMBER | LEB128_SIGNED | CONSTANT;
            case Forms.STRP -> STRING | LEN_4 | ADDRESS;
            case Forms.UDATA -> NUMBER | LEB128_UNSIGNED | CONSTANT;
            case Forms.REF_ADDR, Forms.REF4, Forms.SEC_OFFSET -> NUMBER | LEN_4 | REFERENCE;
            case Forms.REF1 -> NUMBER | LEN_1 | REFERENCE;
            case Forms.REF2 -> NUMBER | LEN_2 | REFERENCE;
            case Forms.REF8, Forms.REF_SIG8 -> NUMBER | LEN_8 | REFERENCE;
            case Forms.REF_UDATA -> NUMBER | LEB128_UNSIGNED | REFERENCE;
            case Forms.INDIRECT -> NUMBER | LEB128_UNSIGNED | INDIRECT;
            case Forms.EXPRLOC -> BYTE_ARRAY | LEB128_UNSIGNED | EXPRESSION;
            case Forms.FLAG_PRESENT -> BOOLEAN | FLAG;
            default -> 0;
        };
    }

    private static final class Forms {
        private static final int ADDR = 0x01;
        private static final int BLOCK2 = 0x03;
        private static final int BLOCK4 = 0x04;
        private static final int DATA2 = 0x05;
        private static final int DATA4 = 0x06;
        private static final int DATA8 = 0x07;
        private static final int STRING = 0x08;
        private static final int BLOCK = 0x09;
        private static final int BLOCK1 = 0x0A;
        private static final int DATA1 = 0x0B;
        private static final int FLAG = 0x0C;
        private static final int SDATA = 0x0D;
        private static final int STRP = 0x0E;
        private static final int UDATA = 0x0F;
        private static final int REF_ADDR = 0x10;
        private static final int REF1 = 0x11;
        private static final int REF2 = 0x12;
        private static final int REF4 = 0x13;
        private static final int REF8 = 0x14;
        private static final int REF_UDATA = 0x15;
        private static final int INDIRECT = 0x16;
        private static final int SEC_OFFSET = 0x17;
        private static final int EXPRLOC = 0x18;
        private static final int FLAG_PRESENT = 0x19;
        private static final int REF_SIG8 = 0x20;
    }
}
