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

public final class MDSubroutine implements MDBaseNode {

    private final long flags;

    private MDBaseNode types;

    private MDSubroutine(long flags) {
        this.flags = flags;
        this.types = MDVoidNode.INSTANCE;
    }

    public long getFlags() {
        return flags;
    }

    public MDBaseNode getTypes() {
        return types;
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public void replace(MDBaseNode oldValue, MDBaseNode newValue) {
        if (types == oldValue) {
            types = newValue;
        }
    }

    private static final int ARGINDEX_FLAGS = 1;
    private static final int ARGINDEX_TYPES = 2;

    public static MDSubroutine create38(long[] args, MetadataValueList md) {
        final long flags = args[ARGINDEX_FLAGS];
        final MDSubroutine subroutine = new MDSubroutine(flags);
        subroutine.types = md.getNullable(args[ARGINDEX_TYPES], subroutine);
        return subroutine;
    }
}
