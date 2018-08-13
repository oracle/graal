/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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

import com.oracle.truffle.llvm.parser.listeners.Metadata;
import com.oracle.truffle.llvm.parser.model.symbols.constants.NullConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.UndefinedConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.BigIntegerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.IntegerConstant;
import com.oracle.truffle.llvm.runtime.types.MetaType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VoidType;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;

public final class ParseUtil {

    public static boolean isInteger(long[] args, int index, Metadata md) {
        final int typeIndex = index << 1;
        final Type type = md.getTypeById(args[typeIndex]);
        if (type == MetaType.METADATA || VoidType.INSTANCE.equals(type)) {
            return false;
        }

        final int valueIndex = typeIndex + 1;
        final SymbolImpl value = md.getScope().getSymbols().getOrNull((int) args[valueIndex]);

        return value instanceof IntegerConstant || value instanceof BigIntegerConstant || value instanceof NullConstant || value instanceof UndefinedConstant;
    }

    // LLVM uses the same behaviour
    private static final long DEFAULT_NUMBER = 0L;

    public static long asLong(long[] args, int index, Metadata md) {
        final int typeIndex = index << 1;
        if (typeIndex >= args.length) {
            return DEFAULT_NUMBER;
        }

        final Type type = md.getTypeById(args[typeIndex]);
        if (type == MetaType.METADATA || VoidType.INSTANCE.equals(type)) {
            return DEFAULT_NUMBER;
        }

        final int valueIndex = typeIndex + 1;
        final SymbolImpl value = md.getScope().getSymbols().getOrNull((int) args[valueIndex]);

        if (value instanceof IntegerConstant) {
            return ((IntegerConstant) value).getValue();

        } else if (value instanceof BigIntegerConstant) {
            return ((BigIntegerConstant) value).getValue().longValue();

        } else if (value instanceof NullConstant || value instanceof UndefinedConstant) {
            return 0L;

        } else {
            return DEFAULT_NUMBER;
        }
    }

    public static int asInt(long[] args, int index, Metadata md) {
        return (int) asLong(args, index, md);
    }

    static boolean asBoolean(long[] args, int index, Metadata md) {
        return asLong(args, index, md) != 0L;
    }

    static MDBaseNode resolveReference(long[] args, int index, MDBaseNode dependent, Metadata md) {
        final int typeIndex = index << 1;
        if (typeIndex >= args.length) {
            return MDVoidNode.INSTANCE;
        }

        final int valueIndex = typeIndex + 1;
        final Type type = md.getTypeById(args[typeIndex]);
        final long value = args[valueIndex];
        if (type == MetaType.METADATA) {
            return md.getScope().getMetadata().getNonNullable(value, dependent);

        } else if (type != VoidType.INSTANCE) {
            return MDValue.create(value, md.getScope());

        } else {
            return MDVoidNode.INSTANCE;
        }
    }

    static MDBaseNode resolveSymbol(long[] args, int index, Metadata md) {
        final int typeIndex = index << 1;
        if (typeIndex >= args.length) {
            return MDVoidNode.INSTANCE;
        }

        final int valueIndex = typeIndex + 1;
        final Type type = md.getTypeById(args[typeIndex]);
        final long value = (int) args[valueIndex];
        if (type != MetaType.METADATA && !VoidType.INSTANCE.equals(type)) {
            return MDValue.create(value, md.getScope());
        } else {
            return MDVoidNode.INSTANCE;
        }
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
