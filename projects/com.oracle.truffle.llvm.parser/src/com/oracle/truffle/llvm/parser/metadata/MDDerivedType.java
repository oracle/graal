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

import com.oracle.truffle.llvm.parser.metadata.subtypes.MDType;
import com.oracle.truffle.llvm.parser.records.DwTagRecord;

import java.util.Arrays;

public final class MDDerivedType extends MDType implements MDBaseNode {

    // TODO extend with objective c property names

    private final MDReference baseType;

    private final Tag tag;

    private final MDReference scope;

    private MDDerivedType(long tag, MDReference name, MDReference file, long line, MDReference scope, MDReference baseType, long size, long align, long offset, long flags) {
        super(name, size, align, offset, file, line, flags);
        this.tag = Tag.decode(tag);
        this.scope = scope;
        this.baseType = baseType;
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    public MDReference getBaseType() {
        return baseType;
    }

    public Tag getTag() {
        return tag;
    }

    public MDReference getScope() {
        return scope;
    }

    @Override
    public String toString() {
        return String.format("DerivedType (tag=%s,name=%s, file=%s, line=%d, scope=%s, size=%d, align=%d, offset=%d, flags=%d, baseType=%s])", getTag(), getName(), getFile(), getLine(), getScope(),
                        getSize(), getAlign(), getOffset(), getFlags(), baseType);
    }

    private boolean isOnlyReference() {
        return getSize() == 0 && getAlign() == 0 && getOffset() == 0 && getFlags() == 0;
    }

    public MDReference getTrueBaseType() {
        if (isOnlyReference() && baseType.get() instanceof MDDerivedType) {
            return ((MDDerivedType) baseType.get()).getTrueBaseType();
        }
        return baseType;
    }

    public enum Tag {
        UNKNOWN(-1),
        DW_TAG_FORMAL_PARAMETER(5),
        DW_TAG_MEMBER(13),
        DW_TAG_POINTER_TYPE(15),
        DW_TAG_REFERENCE_TYPE(16),
        DW_TAG_TYPEDEF(22),
        DW_TAG_INHERITANCE(28),
        DW_TAG_CONST_TYPE(38),
        DW_TAG_VOLATILE_TYPE(53),
        DW_TAG_RESTRICT_TYPE(55),
        DW_TAG_FRIEND(42);

        private final int id;

        Tag(int id) {
            this.id = id;
        }

        private static Tag decode(long val) {
            return Arrays.stream(values()).filter(e -> e.id == val).findAny().orElse(UNKNOWN);
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

    public static MDDerivedType create38(long[] args, MetadataList md) {
        final long tag = args[ARGINDEX_38_TAG];
        final MDReference name = md.getMDRefOrNullRef(args[ARGINDEX_38_NAME]);
        final MDReference file = md.getMDRefOrNullRef(args[ARGINDEX_38_FILE]);
        final long line = args[ARGINDEX_38_LINE];
        final MDReference scope = md.getMDRefOrNullRef(args[ARGINDEX_38_SCOPE]);
        final MDReference baseType = md.getMDRefOrNullRef(args[ARGINDEX_38_BASETYPE]);
        final long size = args[ARGINDEX_38_SIZE];
        final long align = args[ARGINDEX_38_ALIGN];
        final long offset = args[ARGINDEX_38_OFFSET];
        final long flags = args[ARGINDEX_38_FLAGS];
        return new MDDerivedType(tag, name, file, line, scope, baseType, size, align, offset, flags);
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

    public static MDDerivedType create32(MDTypedValue[] args) {
        final long tag = DwTagRecord.decode(ParseUtil.asInt64(args[ARGINDEX_32_TAG])).code();
        final MDReference scope = ParseUtil.getReference(args[ARGINDEX_32_SCOPE]);
        final MDReference name = ParseUtil.getReference(args[ARGINDEX_32_NAME]);
        final MDReference file = ParseUtil.getReference(args[ARGINDEX_32_FILE]);
        final long line = ParseUtil.asInt32(args[ARGINDEX_32_LINE]);
        final long size = ParseUtil.asInt64(args[ARGINDEX_32_SIZE]);
        final long align = ParseUtil.asInt64(args[ARGINDEX_32_ALIGN]);
        final long offset = ParseUtil.asInt64(args[ARGINDEX_32_OFFSET]);
        final long flags = ParseUtil.asInt32(args[ARGINDEX_32_FLAGS]);
        final MDReference baseType = ParseUtil.getReference(args[ARGINDEX_32_BASETYPE]);
        return new MDDerivedType(tag, name, file, line, scope, baseType, size, align, offset, flags);
    }
}
