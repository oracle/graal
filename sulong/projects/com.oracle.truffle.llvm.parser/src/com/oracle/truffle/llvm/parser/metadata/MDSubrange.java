/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.IntegerConstant;
import com.oracle.truffle.llvm.parser.scanner.RecordBuffer;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;

public final class MDSubrange implements MDBaseNode {

    private final long lowerBound;

    private MDBaseNode count;

    private MDSubrange(long lowerBound) {
        this.lowerBound = lowerBound;
        this.count = MDVoidNode.INSTANCE;
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    public long getLowerBound() {
        return lowerBound;
    }

    public MDBaseNode getCount() {
        return count;
    }

    @Override
    public void replace(MDBaseNode oldValue, MDBaseNode newValue) {
        if (count == oldValue) {
            count = newValue;
        }
    }

    // private static final int ARGINDEX_VERSION = 0;
    private static final int VERSION_SHIFT = 1;
    private static final long VERSION_MASK = 0b1;
    // private static final int ARGINDEX_COUNT = 1;
    // private static final int ARGINDEX_STARTFROM = 2;

    public static MDSubrange createNewFormat(RecordBuffer buffer, MetadataValueList md) {
        long version = (buffer.read() >> VERSION_SHIFT) & VERSION_MASK;
        long count = buffer.read();
        long startFrom = ParseUtil.unrotateSign(buffer.read());
        MDSubrange subrange = new MDSubrange(startFrom);
        if (version == 1) {
            // in LLVM 7+ the "count" argument is a metadata node index
            subrange.count = md.getNullable(count, subrange);

        } else {
            // prior to LLVM 7, the "count argument is a primitive value
            subrange.count = MDValue.create(new IntegerConstant(PrimitiveType.I64, count));
        }

        return subrange;
    }

    private static final int ARGINDEX_32_LOWERBOUND = 1;
    private static final int ARGINDEX_32_UPPERBOUND = 2;

    public static MDSubrange createOldFormat(long[] args, Metadata md) {
        final long lowerBound = ParseUtil.asLong(args, ARGINDEX_32_LOWERBOUND, md);
        final long upperBound = ParseUtil.asLong(args, ARGINDEX_32_UPPERBOUND, md);
        final long size = upperBound - lowerBound + 1;
        final MDSubrange subrange = new MDSubrange(lowerBound);
        subrange.count = MDValue.create(new IntegerConstant(PrimitiveType.I64, size));
        return subrange;
    }
}
