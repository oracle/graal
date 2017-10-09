/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.parser.metadata;

import com.oracle.truffle.llvm.parser.model.symbols.constants.NullConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.BigIntegerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.IntegerConstant;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;

public final class ParseUtil {

    static boolean asInt1(MDTypedValue t) {
        return asInt64(t) != 0;
    }

    public static int asInt32(MDTypedValue t) {
        return (int) asInt64(t);
    }

    public static long asInt64(MDTypedValue t) {
        if (t instanceof MDSymbolReference) {
            final Symbol s = ((MDSymbolReference) t).get();
            if (s instanceof IntegerConstant) {
                return ((IntegerConstant) s).getValue();

            } else if (s instanceof BigIntegerConstant) {
                return ((BigIntegerConstant) s).getValue().longValue();

            } else if (s instanceof NullConstant) {
                return 0L;

            }

        }

        throw new AssertionError("Cannot retrieve int value from this: " + t);
    }

    static long asInt64IfPresent(MDTypedValue[] values, int index) {
        return index < values.length ? asInt64(values[index]) : 0;
    }

    static MDBaseNode resolveReferenceIfPresent(MDTypedValue[] values, int index, MDBaseNode dependent, MetadataValueList valueList) {
        return index < values.length ? resolveReference(values[index], dependent, valueList) : MDReference.VOID;
    }

    static MDBaseNode resolveReference(MDTypedValue t, MDBaseNode dependent, MetadataValueList valueList) {
        if (t instanceof MDReference.MDRef) {
            return valueList.getNonNullable(((MDReference.MDRef) t).getIndex(), dependent);

        } else if (t instanceof MDSymbolReference) {
            return MDValue.createFromSymbolReference(t);

        } else {
            return MDReference.VOID;
        }
    }

    public static boolean isInt(MDTypedValue val) {
        final Type t = val.getType();
        if (!Type.isIntegerType(t) || !(val instanceof MDSymbolReference)) {
            return false;
        }

        final Symbol s = ((MDSymbolReference) val).get();
        return s instanceof IntegerConstant || s instanceof BigIntegerConstant;
    }

    private static final long BYTE_MASK = 0xFF;

    public static String longArrayToString(int startIndex, long[] chars) {
        // We use a byte array, so "new String(...)" is able to handle Unicode Characters correctly
        final byte[] bytes = new byte[chars.length - startIndex];
        for (int from = startIndex, to = 0; to < bytes.length; from++, to++) {
            bytes[to] = (byte) (chars[from] & BYTE_MASK);
        }
        return new String(bytes);
    }

    static long unrotateSign(long u) {
        return (u & 1) == 1 ? ~(u >> 1) : u >> 1;
    }

}
