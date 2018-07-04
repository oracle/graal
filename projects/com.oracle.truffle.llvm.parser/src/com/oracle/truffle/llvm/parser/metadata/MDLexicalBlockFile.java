/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
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

public final class MDLexicalBlockFile implements MDBaseNode {

    private final long discriminator;

    private MDBaseNode scope;
    private MDBaseNode file;

    private MDLexicalBlockFile(long discriminator) {
        this.discriminator = discriminator;

        this.scope = MDVoidNode.INSTANCE;
        this.file = MDVoidNode.INSTANCE;
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    public MDBaseNode getFile() {
        return file;
    }

    public MDBaseNode getScope() {
        return scope;
    }

    public long getDiscriminator() {
        return discriminator;
    }

    @Override
    public void replace(MDBaseNode oldValue, MDBaseNode newValue) {
        if (scope == oldValue) {
            scope = newValue;
        }
        if (file == oldValue) {
            file = newValue;
        }
    }

    private static final int ARGINDEX_SCOPE = 1;
    private static final int ARGINDEX_FILE = 2;
    private static final int ARGINDEX_38_DISCRIMINATOR = 3;

    public static MDLexicalBlockFile create38(long[] args, MetadataValueList md) {
        // [distinct, scope, file, discriminator]
        final long discriminator = args[ARGINDEX_38_DISCRIMINATOR];
        final MDLexicalBlockFile file = new MDLexicalBlockFile(discriminator);
        file.scope = md.getNullable(args[ARGINDEX_SCOPE], file);
        file.file = md.getNullable(args[ARGINDEX_FILE], file);
        return file;
    }

    public static MDLexicalBlockFile create32(long[] args, Metadata md) {
        final MDLexicalBlockFile file = new MDLexicalBlockFile(-1L);
        file.scope = ParseUtil.resolveReference(args, ARGINDEX_SCOPE, file, md);
        file.file = ParseUtil.resolveReference(args, ARGINDEX_FILE, file, md);
        return file;
    }
}
