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

import com.oracle.truffle.llvm.parser.metadata.subtypes.MDName;

public final class MDTemplateTypeParameter extends MDName implements MDBaseNode {

    private final MDReference baseType;

    private final MDReference context;

    private final MDReference file;

    private final long line;

    private final long column;

    private MDTemplateTypeParameter(MDReference name, MDReference baseType, MDReference context, MDReference file, long line, long column) {
        super(name);
        this.baseType = baseType;
        this.context = context;
        this.file = file;
        this.line = line;
        this.column = column;
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    public MDReference getBaseType() {
        return baseType;
    }

    public MDReference getContext() {
        return context;
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

    @Override
    public String toString() {
        return String.format("TemplateTypeParameter (baseType=%s, name=%s, context=%s, file=%s, line=%d, column=%d)", baseType, getName(), context, file, line, column);
    }

    private static final int ARGINDEX_CONTEXT = 1;
    private static final int ARGINDEX_NAME = 2;
    private static final int ARGINDEX_TYPE = 3;
    private static final int ARGINDEX_FILE = 4;
    private static final int ARGINDEX_LINE = 5;
    private static final int ARGINDEX_COLUMN = 6;

    public static MDTemplateTypeParameter create32(MDTypedValue[] args) {
        final MDReference context = ParseUtil.getReference(args[ARGINDEX_CONTEXT]);
        final MDReference name = ParseUtil.getReference(args[ARGINDEX_NAME]);
        final MDReference type = ParseUtil.getReference(args[ARGINDEX_TYPE]);
        final MDReference file = ParseUtil.getReference(args[ARGINDEX_FILE]);
        final long line = ParseUtil.asInt64(args[ARGINDEX_LINE]);
        final long column = ParseUtil.asInt64(args[ARGINDEX_COLUMN]);

        return new MDTemplateTypeParameter(name, type, context, file, line, column);
    }
}
