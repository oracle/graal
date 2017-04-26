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

public final class MDImportedEntity extends MDName implements MDBaseNode {

    private final long tag;

    private final MDReference scope;

    private final MDReference entity;

    private final long line;

    private MDImportedEntity(long tag, MDReference scope, MDReference entity, long line, MDReference name) {
        super(name);
        this.tag = tag;
        this.scope = scope;
        this.entity = entity;
        this.line = line;
    }

    public long getTag() {
        return tag;
    }

    public MDReference getScope() {
        return scope;
    }

    public MDReference getEntity() {
        return entity;
    }

    public long getLine() {
        return line;
    }

    @Override
    public String toString() {
        return String.format("ImportedEntity (tag=%d, scope=%s, entity=%s, line=%d, name=%s)", tag, scope, entity, line, getName());
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    private static final int ARGINDEX_TAG = 1;
    private static final int ARGINDEX_SCOPE = 2;
    private static final int ARGINDEX_ENTITY = 3;
    private static final int ARGINDEX_LINE = 4;
    private static final int ARGINDEX_NAME = 5;

    public static MDImportedEntity create38(long[] args, MetadataList md) {
        final long tag = args[ARGINDEX_TAG];
        final MDReference scope = md.getMDRefOrNullRef(args[ARGINDEX_SCOPE]);
        final MDReference entity = md.getMDRefOrNullRef(args[ARGINDEX_ENTITY]);
        final long line = args[ARGINDEX_LINE];
        final MDReference name = md.getMDRefOrNullRef(args[ARGINDEX_NAME]);
        return new MDImportedEntity(tag, scope, entity, line, name);
    }
}
