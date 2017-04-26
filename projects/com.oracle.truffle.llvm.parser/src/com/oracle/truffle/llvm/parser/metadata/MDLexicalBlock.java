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

public final class MDLexicalBlock implements MDBaseNode {

    private final MDReference scope;

    private final MDReference file;

    private final long line;

    private final long column;

    private MDLexicalBlock(MDReference scope, MDReference file, long line, long column) {
        this.scope = scope;
        this.file = file;
        this.line = line;
        this.column = column;
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    public MDReference getFile() {
        return file;
    }

    public long getLine() {
        return line;
    }

    public long getColumn() {
        return column;
    }

    public MDReference getScope() {
        return scope;
    }

    @Override
    public String toString() {
        return String.format("LexicalBlock (scope=%s, file=%s, line=%d, column=%d)", scope, file, line, column);
    }

    private static final int ARGINDEX_38_SCOPE = 1;
    private static final int ARGINDEX_38_FILE = 2;
    private static final int ARGINDEX_38_LINE = 3;
    private static final int ARGINDEX_38_COLUMN = 4;

    public static MDLexicalBlock create38(long[] args, MetadataList md) {
        // [distinct, scope, file, line, column]
        final MDReference scope = md.getMDRefOrNullRef(args[ARGINDEX_38_SCOPE]);
        final MDReference file = md.getMDRefOrNullRef(args[ARGINDEX_38_FILE]);
        final long line = args[ARGINDEX_38_LINE];
        final long column = args[ARGINDEX_38_COLUMN];
        return new MDLexicalBlock(scope, file, line, column);
    }

    private static final int ARGINDEX_32_SCOPE = 1;
    private static final int ARGINDEX_32_LINE = 2;
    private static final int ARGINDEX_32_COLUMN = 3;
    private static final int ARGINDEX_32_FILE = 4;

    public static MDLexicalBlock create32(MDTypedValue[] args) {
        final MDReference scope = ParseUtil.getReference(args[ARGINDEX_32_SCOPE]);
        final long line = ParseUtil.asInt32(args[ARGINDEX_32_LINE]);
        final long column = ParseUtil.asInt32(args[ARGINDEX_32_COLUMN]);
        final MDReference file = ParseUtil.getReference(args[ARGINDEX_32_FILE]);
        // asInt32(args[5); // Unique ID to identify blocks from a template function
        return new MDLexicalBlock(scope, file, line, column);
    }
}
