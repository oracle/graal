/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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

import com.oracle.truffle.llvm.parser.scanner.RecordBuffer;

public final class MDLocation implements MDBaseNode {

    private final long line;
    private final long column;

    private MDBaseNode inlinedAt;
    private MDBaseNode scope;

    private MDLocation(long line, long column) {
        this.line = line;
        this.column = column;
        this.inlinedAt = MDVoidNode.INSTANCE;
        this.scope = MDVoidNode.INSTANCE;
    }

    public MDLocation(long line, long column, MDBaseNode inlinedAt, MDBaseNode scope) {
        this.line = line;
        this.column = column;
        this.inlinedAt = inlinedAt;
        this.scope = scope;
    }

    public long getLine() {
        return line;
    }

    public long getColumn() {
        return column;
    }

    public MDBaseNode getInlinedAt() {
        return inlinedAt;
    }

    public MDBaseNode getScope() {
        return scope;
    }

    @Override
    public void replace(MDBaseNode oldValue, MDBaseNode newValue) {
        if (inlinedAt == oldValue) {
            inlinedAt = newValue;
        }
        if (scope == oldValue) {
            scope = newValue;
        }
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    // private static final int MDNODE_LINE = 1;
    // private static final int MDNODE_COLUMN = 2;
    // private static final int MDNODE_SCOPE = 3;
    // private static final int MDNODE_INLINEDAT = 4;

    public static MDLocation create38(RecordBuffer buffer, MetadataValueList md) {
        // [distinct, line, col, scope, inlined-at?]
        buffer.skip();
        final long line = buffer.read();
        final long column = buffer.read();

        final MDLocation location = new MDLocation(line, column);
        location.scope = md.getNonNullable(buffer.read(), location);
        location.inlinedAt = md.getNullable(buffer.read(), location);

        return location;
    }

    // private static final int ARG_LINE = 0;
    // private static final int ARG_COL = 1;
    // private static final int ARG_SCOPE = 2;
    // private static final int ARG_INLINEDAT = 3;

    public static MDLocation createFromFunctionArgs(RecordBuffer buffer, MetadataValueList md) {
        final long line = buffer.read();
        final long col = buffer.read();

        final MDLocation location = new MDLocation(line, col);
        location.scope = md.getNullable(buffer.read(), location);
        location.inlinedAt = md.getNullable(buffer.read(), location);

        return location;
    }
}
