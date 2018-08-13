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

public final class MDMacroFile implements MDBaseNode {

    private final long type;
    private final long line;

    private MDBaseNode file;
    private MDBaseNode elements;

    private MDMacroFile(long type, long line) {
        this.type = type;
        this.line = line;

        this.file = MDVoidNode.INSTANCE;
        this.elements = MDVoidNode.INSTANCE;
    }

    public long getType() {
        return type;
    }

    public long getLine() {
        return line;
    }

    public MDBaseNode getFile() {
        return file;
    }

    public MDBaseNode getElements() {
        return elements;
    }

    @Override
    public void replace(MDBaseNode oldValue, MDBaseNode newValue) {
        if (file == oldValue) {
            file = newValue;
        }
        if (elements == oldValue) {
            elements = newValue;
        }
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    private static final int ARGINDEX_TYPE = 1;
    private static final int ARGINDEX_LINE = 2;
    private static final int ARGINDEX_FILE = 3;
    private static final int ARGINDEX_ELEMENTS = 4;

    public static MDMacroFile create38(long[] args, MetadataValueList md) {
        final long type = args[ARGINDEX_TYPE];
        final long line = args[ARGINDEX_LINE];

        final MDMacroFile macroFile = new MDMacroFile(type, line);

        macroFile.file = md.getNullable(args[ARGINDEX_FILE], macroFile);
        macroFile.elements = md.getNullable(args[ARGINDEX_ELEMENTS], macroFile);

        return macroFile;
    }
}
