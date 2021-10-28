/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates.
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
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;

public final class MDSubrange implements MDBaseNode {

    private MDBaseNode count;
    private MDBaseNode lowerBound;
    private MDBaseNode upperBound;
    private MDBaseNode stride;

    private MDSubrange() {
        this.count = MDVoidNode.INSTANCE;
        this.lowerBound = MDVoidNode.INSTANCE;
        this.upperBound = MDVoidNode.INSTANCE;
        this.stride = MDVoidNode.INSTANCE;
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    public MDBaseNode getCount() {
        return count;
    }

    public MDBaseNode getLowerBound() {
        return lowerBound;
    }

    public MDBaseNode getUpperBound() {
        return upperBound;
    }

    public MDBaseNode getStride() {
        return stride;
    }

    @Override
    public void replace(MDBaseNode oldValue, MDBaseNode newValue) {
        if (count == oldValue) {
            count = newValue;
        }
    }

    // private static final int ARGINDEX_VERSION = 0;
    private static final int VERSION_SHIFT = 1;
    // private static final int ARGINDEX_COUNT = 1;
    // private static final int ARGINDEX_STARTFROM = 2;

    public static MDSubrange createNewFormat(RecordBuffer buffer, MetadataValueList md) {
        int version = (int) (buffer.read() >> VERSION_SHIFT);
        MDSubrange subrange = new MDSubrange();
        switch (version) {
            case 0:
                subrange.count = MDValue.create(new IntegerConstant(PrimitiveType.I64, buffer.read()));
                subrange.lowerBound = MDValue.create(new IntegerConstant(PrimitiveType.I64, ParseUtil.unrotateSign(buffer.read())));
                break;
            case 1:
                subrange.count = md.getNullable(buffer.read(), subrange);
                subrange.lowerBound = MDValue.create(new IntegerConstant(PrimitiveType.I64, ParseUtil.unrotateSign(buffer.read())));
                break;
            case 2:
                subrange.count = md.getNullable(buffer.read(), subrange);
                subrange.lowerBound = md.getNullable(buffer.read(), subrange);
                subrange.upperBound = md.getNullable(buffer.read(), subrange);
                subrange.stride = md.getNullable(buffer.read(), subrange);
                break;
            default:
                throw new LLVMParserException("Invalid record: Unsupported version of DISubrange");
        }

        return subrange;
    }

    private static final int ARGINDEX_32_LOWERBOUND = 1;
    private static final int ARGINDEX_32_UPPERBOUND = 2;

    public static MDSubrange createOldFormat(long[] args, Metadata md) {
        final long lowerBound = ParseUtil.asLong(args, ARGINDEX_32_LOWERBOUND, md);
        final long upperBound = ParseUtil.asLong(args, ARGINDEX_32_UPPERBOUND, md);
        final long size = upperBound - lowerBound + 1;
        final MDSubrange subrange = new MDSubrange();
        subrange.count = MDValue.create(new IntegerConstant(PrimitiveType.I64, size));
        subrange.lowerBound = MDValue.create(new IntegerConstant(PrimitiveType.I64, lowerBound));
        return subrange;
    }
}
