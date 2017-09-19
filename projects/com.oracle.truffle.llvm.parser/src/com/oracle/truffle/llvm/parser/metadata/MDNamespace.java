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

import com.oracle.truffle.llvm.parser.metadata.subtypes.MDName;

public final class MDNamespace extends MDName implements MDBaseNode {

    private final MDReference scope;
    private final MDReference file;
    private final long line;

    private MDNamespace(MDReference name, MDReference scope, MDReference file, long line) {
        super(name);
        this.scope = scope;
        this.file = file;
        this.line = line;
    }

    public MDReference getScope() {
        return scope;
    }

    public MDReference getFile() {
        return file;
    }

    public long getLine() {
        return line;
    }

    @Override
    public String toString() {
        return String.format("Namespace (name=%s, scope=%s, file=%s, line=%d)", getName(), scope, file, line);
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    private static final int ARGINDEX_38_SCOPE = 1;
    private static final int ARGINDEX_38_FILE = 2;
    private static final int ARGINDEX_38_NAME = 3;
    private static final int ARGINDEX_38_LINE = 4;

    private static final int RECORDSIZE_50 = 3;
    private static final int ARGINDEX_50_NAME = 2;

    public static MDNamespace create38(long[] args, MetadataList md) {
        final MDReference scope = md.getMDRefOrNullRef(args[ARGINDEX_38_SCOPE]);
        final MDReference name;
        final MDReference file;
        final long line;
        if (RECORDSIZE_50 != args.length) {
            file = md.getMDRefOrNullRef(args[ARGINDEX_38_FILE]);
            name = md.getMDRefOrNullRef(args[ARGINDEX_38_NAME]);
            line = args[ARGINDEX_38_LINE];
        } else {
            name = md.getMDRefOrNullRef(args[ARGINDEX_50_NAME]);
            file = MDReference.VOID;
            line = -1L;
        }
        return new MDNamespace(name, scope, file, line);
    }

    private static final int ARGINDEX_32_SCOPE = 1;
    private static final int ARGINDEX_32_NAME = 2;
    private static final int ARGINDEX_32_FILE = 3;
    private static final int ARGINDEX_32_LINE = 4;

    public static MDNamespace create32(MDTypedValue[] args) {
        final MDReference context = ParseUtil.getReference(args[ARGINDEX_32_SCOPE]);
        final MDReference name = ParseUtil.getReference(args[ARGINDEX_32_NAME]);
        final MDReference file = ParseUtil.getReference(args[ARGINDEX_32_FILE]);
        final long line = ParseUtil.asInt64(args[ARGINDEX_32_LINE]);
        return new MDNamespace(name, context, file, line);
    }
}
