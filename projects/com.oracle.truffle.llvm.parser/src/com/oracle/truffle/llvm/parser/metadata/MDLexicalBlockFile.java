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

public final class MDLexicalBlockFile implements MDBaseNode {

    private final MDReference scope;

    private final MDReference file;

    private final long discriminator;

    private MDLexicalBlockFile(MDReference scope, MDReference file, long discriminator) {
        this.scope = scope;
        this.file = file;
        this.discriminator = discriminator;
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    public MDReference getFile() {
        return file;
    }

    @Override
    public String toString() {
        return String.format("LexicalBlockFile (file=%s, scope=%s, discriminator=%d)", file, scope, discriminator);
    }

    private static final int ARGINDEX_SCOPE = 1;
    private static final int ARGINDEX_FILE = 2;
    private static final int ARGINDEX_38_DISCRIMINATOR = 3;

    public static MDLexicalBlockFile create38(long[] args, MetadataList md) {
        // [distinct, scope, file, discriminator]
        final MDReference scope = md.getMDRefOrNullRef(args[ARGINDEX_SCOPE]);
        final MDReference file = md.getMDRefOrNullRef(args[ARGINDEX_FILE]);
        final long discriminator = args[ARGINDEX_38_DISCRIMINATOR];
        return new MDLexicalBlockFile(scope, file, discriminator);
    }

    public static MDLexicalBlockFile create32(MDTypedValue[] args) {
        final MDReference scope = ParseUtil.getReference(args[ARGINDEX_SCOPE]);
        final MDReference file = ParseUtil.getReference(args[ARGINDEX_FILE]);
        return new MDLexicalBlockFile(scope, file, -1L);
    }
}
