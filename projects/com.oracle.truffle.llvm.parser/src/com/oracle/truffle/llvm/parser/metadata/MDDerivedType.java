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
import com.oracle.truffle.llvm.parser.records.DwTagRecord;

public final class MDDerivedType extends MDType implements MDBaseNode {

    // TODO extend with objective c property names

    private MDBaseNode baseType;
    private MDBaseNode scope;
    private MDBaseNode extraData;

    private MDDerivedType(long tag, long line, long size, long align, long offset, long flags) {
        super(tag, size, align, offset, line, flags);

        this.scope = MDVoidNode.INSTANCE;
        this.baseType = MDVoidNode.INSTANCE;
        this.extraData = MDVoidNode.INSTANCE;
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    public MDBaseNode getBaseType() {
        return baseType;
    }

    public MDBaseNode getScope() {
        return scope;
    }

    public MDBaseNode getExtraData() {
        return extraData;
    }

    @Override
    public void replace(MDBaseNode oldValue, MDBaseNode newValue) {
        super.replace(oldValue, newValue);
        if (baseType == oldValue) {
            baseType = newValue;
        }
        if (scope == oldValue) {
            scope = newValue;
        }
        if (extraData == oldValue) {
            extraData = newValue;
        }
    }

    private static final int ARGINDEX_38_TAG = 1;
    private static final int ARGINDEX_38_NAME = 2;
    private static final int ARGINDEX_38_FILE = 3;
    private static final int ARGINDEX_38_LINE = 4;
    private static final int ARGINDEX_38_SCOPE = 5;
    private static final int ARGINDEX_38_BASETYPE = 6;
    private static final int ARGINDEX_38_SIZE = 7;
    private static final int ARGINDEX_38_ALIGN = 8;
    private static final int ARGINDEX_38_OFFSET = 9;
    private static final int ARGINDEX_38_FLAGS = 10;
    private static final int ARGINDEX_38_EXTRADATA = 11;

    public static MDDerivedType create38(long[] args, MetadataValueList md) {
        final long tag = args[ARGINDEX_38_TAG];
        final long line = args[ARGINDEX_38_LINE];
        final long size = args[ARGINDEX_38_SIZE];
        final long align = args[ARGINDEX_38_ALIGN];
        final long offset = args[ARGINDEX_38_OFFSET];
        final long flags = args[ARGINDEX_38_FLAGS];

        final MDDerivedType derivedType = new MDDerivedType(tag, line, size, align, offset, flags);

        derivedType.scope = md.getNullable(args[ARGINDEX_38_SCOPE], derivedType);
        derivedType.baseType = md.getNullable(args[ARGINDEX_38_BASETYPE], derivedType);
        derivedType.extraData = md.getNullable(args[ARGINDEX_38_EXTRADATA], derivedType);

        derivedType.setFile(md.getNullable(args[ARGINDEX_38_FILE], derivedType));
        derivedType.setName(md.getNullable(args[ARGINDEX_38_NAME], derivedType));

        return derivedType;
    }

    private static final int ARGINDEX_32_TAG = 0;
    private static final int ARGINDEX_32_SCOPE = 1;
    private static final int ARGINDEX_32_NAME = 2;
    private static final int ARGINDEX_32_FILE = 3;
    private static final int ARGINDEX_32_LINE = 4;
    private static final int ARGINDEX_32_SIZE = 5;
    private static final int ARGINDEX_32_ALIGN = 6;
    private static final int ARGINDEX_32_OFFSET = 7;
    private static final int ARGINDEX_32_FLAGS = 8;
    private static final int ARGINDEX_32_BASETYPE = 9;

    public static MDDerivedType create32(long[] args, Metadata md) {
        final long tag = DwTagRecord.decode(ParseUtil.asLong(args, ARGINDEX_32_TAG, md)).code();
        final long line = ParseUtil.asInt(args, ARGINDEX_32_LINE, md);
        final long size = ParseUtil.asLong(args, ARGINDEX_32_SIZE, md);
        final long align = ParseUtil.asLong(args, ARGINDEX_32_ALIGN, md);
        final long offset = ParseUtil.asLong(args, ARGINDEX_32_OFFSET, md);
        final long flags = ParseUtil.asLong(args, ARGINDEX_32_FLAGS, md);

        final MDDerivedType derivedType = new MDDerivedType(tag, line, size, align, offset, flags);

        derivedType.scope = ParseUtil.resolveReference(args, ARGINDEX_32_SCOPE, derivedType, md);
        derivedType.baseType = ParseUtil.resolveReference(args, ARGINDEX_32_BASETYPE, derivedType, md);

        derivedType.setFile(ParseUtil.resolveReference(args, ARGINDEX_32_FILE, derivedType, md));
        derivedType.setName(ParseUtil.resolveReference(args, ARGINDEX_32_NAME, derivedType, md));

        return derivedType;
    }
}
