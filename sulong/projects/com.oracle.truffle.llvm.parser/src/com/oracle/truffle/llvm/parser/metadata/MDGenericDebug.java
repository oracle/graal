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

public final class MDGenericDebug extends MDAggregateNode {

    private final long tag;
    private final long version;

    private MDBaseNode header;

    private MDGenericDebug(long tag, long version, int size) {
        super(size);
        this.tag = tag;
        this.version = version;
        this.header = MDVoidNode.INSTANCE;
    }

    public long getTag() {
        return tag;
    }

    public long getVersion() {
        return version;
    }

    public MDBaseNode getHeader() {
        return header;
    }

    @Override
    public void replace(MDBaseNode oldValue, MDBaseNode newValue) {
        super.replace(oldValue, newValue);
        if (header == oldValue) {
            header = newValue;
        }
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    // private static final int ARGINDEX_TAG = 1;
    // private static final int ARGINDEX_VERSION = 2;
    // private static final int ARGINDEX_HEADER = 3;
    // private static final int ARGINDEX_DATASTART = 4;

    public static MDGenericDebug create38(RecordBuffer buffer, MetadataValueList md) {
        buffer.skip();
        final long tag = buffer.read();
        final long version = buffer.read();
        long header = buffer.read();
        final int size = buffer.remaining();
        final MDGenericDebug debug = new MDGenericDebug(tag, version, size);

        debug.header = md.getNullable(header, debug);
        for (int i = 0; i < size; i++) {
            debug.set(i, md.getNullable(buffer.read(), debug));
        }

        return debug;
    }
}
