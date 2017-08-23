/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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

import com.oracle.truffle.llvm.parser.metadata.subtypes.MDVariable;

public final class MDLocalVariable extends MDVariable implements MDBaseNode {

    private final long arg;

    private final long flags;

    private MDLocalVariable(MDReference scope, MDReference name, MDReference file, long line, MDReference type, long arg, long flags) {
        super(scope, name, type, file, line);
        this.arg = arg;
        this.flags = flags;
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    public long getArg() {
        return arg;
    }

    public long getFlags() {
        return flags;
    }

    @Override
    public String toString() {
        return String.format("LocalVariable (scope=%s, name=%s, file=%s, line=%d, arg=%d, type=%s, flags=%d)", getScope(), getName(), getFile(), getLine(), arg, getType(), flags);
    }

    private static final int ARGINDEX_38_TAG_ALIGNMENT = 0;
    private static final int ARGINDEX_38_SCOPE = 1;
    private static final int ARGINDEX_38_NAME = 2;
    private static final int ARGINDEX_38_FILE = 3;
    private static final int ARGINDEX_38_LINE = 4;
    private static final int ARGINDEX_38_TYPE = 5;
    private static final int ARGINDEX_38_ARG = 6;
    private static final int ARGINDEX_38_FLAGS = 7;
    private static final int OFFSET_INDICATOR = 8;
    private static final int ALIGNMENT_INDICATOR = 2;

    public static MDLocalVariable create38(long[] args, MetadataList md) {
        // this apparently exists for historical reasons...
        final int argOffset = (args.length > OFFSET_INDICATOR && ((args[ARGINDEX_38_TAG_ALIGNMENT] & ALIGNMENT_INDICATOR) == 0)) ? 1 : 0;

        final MDReference scope = md.getMDRefOrNullRef(args[ARGINDEX_38_SCOPE + argOffset]);
        final MDReference name = md.getMDRefOrNullRef(args[ARGINDEX_38_NAME + argOffset]);
        final MDReference file = md.getMDRefOrNullRef(args[ARGINDEX_38_FILE + argOffset]);
        final long line = args[ARGINDEX_38_LINE + argOffset];
        final MDReference type = md.getMDRefOrNullRef(args[ARGINDEX_38_TYPE + argOffset]);
        final long arg = args[ARGINDEX_38_ARG + argOffset];
        final long flags = args[ARGINDEX_38_FLAGS + argOffset];
        return new MDLocalVariable(scope, name, file, line, type, arg, flags);
    }

    private static final long DW_TAG_LOCAL_VARIABLE_LINE_MASK = 0x00FFFFFF;
    private static final long DW_TAG_LOCAL_VARIABLE_ARG_MASK = 0xFF000000;
    private static final long DW_TAG_LOCAL_VARIABLE_ARG_SHIFT = 24;

    private static final int ARGINDEX_32_SCOPE = 1;
    private static final int ARGINDEX_32_NAME = 2;
    private static final int ARGINDEX_32_FILE = 3;
    private static final int ARGINDEX_32_LINEARG = 4;
    private static final int ARGINDEX_32_TYPE = 5;
    private static final int ARGINDEX_32_FLAGS = 6;

    public static MDLocalVariable create32(MDTypedValue[] args) {
        final MDReference context = ParseUtil.getReference(args[ARGINDEX_32_SCOPE]);
        final MDReference name = ParseUtil.getReference(args[ARGINDEX_32_NAME]);
        final MDReference file = ParseUtil.getReference(args[ARGINDEX_32_FILE]);
        final long lineAndArg = ParseUtil.asInt32(args[ARGINDEX_32_LINEARG]);
        final long line = lineAndArg & DW_TAG_LOCAL_VARIABLE_LINE_MASK;
        final long arg = (lineAndArg & DW_TAG_LOCAL_VARIABLE_ARG_MASK) >> DW_TAG_LOCAL_VARIABLE_ARG_SHIFT;
        final MDReference type = ParseUtil.getReference(args[ARGINDEX_32_TYPE]);
        final long flags = ParseUtil.asInt32(args[ARGINDEX_32_FLAGS]);
        return new MDLocalVariable(context, name, file, line, type, arg, flags);
    }
}
