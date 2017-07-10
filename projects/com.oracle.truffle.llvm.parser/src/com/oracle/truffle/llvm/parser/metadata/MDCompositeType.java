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

public final class MDCompositeType extends MDType implements MDBaseNode {

    private final Tag tag;

    private final MDReference context;

    private final MDReference derivedFrom;

    private final MDReference memberDescriptors;

    private final long runtimeLanguage;

    private final MDReference vTableHolder;

    private final MDReference templateParams;

    private final MDReference identifier;

    private MDCompositeType(long tag, MDReference context, MDReference name, MDReference file, long line, long size, long align, long offset, long flags, MDReference derivedFrom,
                    MDReference memberDescriptors, long runtimeLanguage, MDReference vTableHolder, MDReference templateParams, MDReference identifier) {
        super(name, size, align, offset, file, line, flags);
        this.tag = Tag.decode(tag);
        this.context = context;
        this.derivedFrom = derivedFrom;
        this.memberDescriptors = memberDescriptors;
        this.runtimeLanguage = runtimeLanguage;
        this.vTableHolder = vTableHolder;
        this.templateParams = templateParams;
        this.identifier = identifier;
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    public MDReference getContext() {
        return context;
    }

    public MDReference getDerivedFrom() {
        return derivedFrom;
    }

    public MDReference getMemberDescriptors() {
        return memberDescriptors;
    }

    public long getRuntimeLanguage() {
        return runtimeLanguage;
    }

    public Tag getTag() {
        return tag;
    }

    public MDReference getvTableHolder() {
        return vTableHolder;
    }

    public MDReference getTemplateParams() {
        return templateParams;
    }

    public MDReference getIdentifier() {
        return identifier;
    }

    public enum Tag {
        UNKNOWN(-1),
        DW_TAG_ARRAY_TYPE(1),
        DW_TAG_CLASS_TYPE(2),
        DW_TAG_ENUMERATION_TYPE(4),
        DW_TAG_STRUCTURE_TYPE(19),
        DW_TAG_UNION_TYPE(23),
        DW_TAG_VECTOR_TYPE(259),
        DW_TAG_SUBROUTINE_TYPE(21);

        private final int id;

        Tag(int id) {
            this.id = id;
        }

        static Tag decode(long val) {
            return Arrays.stream(values()).filter(e -> e.id == val).findAny().orElse(UNKNOWN);
        }
    }

    @Override
    public String toString() {
        return String.format(
                        "CompositeType (tag=%s, context=%s, name=%s, file=%s, line=%d, size=%d, align=%d, offset=%d, flags=%d, derivedFrom=%s, memberDescriptors=%s, runtimeLanguage=%d, vTableHolder=%s, templateParams=%s, identifier=%s)",
                        tag, context,
                        getName(), getFile(), getLine(), getSize(), getAlign(), getOffset(), getFlags(), derivedFrom, memberDescriptors, runtimeLanguage, vTableHolder, templateParams,
                        identifier);
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
    private static final int ARGINDEX_38_MEMBERDESCRIPTORS = 11;
    private static final int ARGINDEX_38_RUNTIMELANGUAGE = 12;
    private static final int ARGINDEX_38_VTABLEHOLDER = 13;
    private static final int ARGINDEX_38_TEMPLATEPARAMS = 14;
    private static final int ARGINDEX_38_IDENTIFIER = 15;

    public static MDCompositeType create38(long[] args, MetadataList md) {
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
        final MDReference members = md.getMDRefOrNullRef(args[ARGINDEX_38_MEMBERDESCRIPTORS]);
        final long lang = args[ARGINDEX_38_RUNTIMELANGUAGE];
        final MDReference vTableHolder = md.getMDRefOrNullRef(args[ARGINDEX_38_VTABLEHOLDER]);
        final MDReference templateParams = md.getMDRefOrNullRef(args[ARGINDEX_38_TEMPLATEPARAMS]);
        final MDReference idenitifier = md.getMDRefOrNullRef(args[ARGINDEX_38_IDENTIFIER]);
        return new MDCompositeType(tag, scope, name, file, line, size, align, offset, flags, baseType, members, lang, vTableHolder, templateParams, idenitifier);
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
    private static final int ARGINDEX_32_MEMBERDESCRIPTORS = 10;
    private static final int ARGINDEX_32_RUNTIMELANGUAGE = 11;
    private static final int ARGINDEX_32_TEMPLATEPARAMS = 13;

    public static MDCompositeType create32(MDTypedValue[] args) {
        final long tag = DwTagRecord.decode(ParseUtil.asInt64(args[ARGINDEX_32_TAG])).code();
        final MDReference context = ParseUtil.getReference(args[ARGINDEX_32_SCOPE]);
        final MDReference name = ParseUtil.getReference(args[ARGINDEX_32_NAME]);
        final MDReference file = ParseUtil.getReference(args[ARGINDEX_32_FILE]);
        final long line = ParseUtil.asInt32(args[ARGINDEX_32_LINE]);
        final long size = ParseUtil.asInt64(args[ARGINDEX_32_SIZE]);
        final long align = ParseUtil.asInt64(args[ARGINDEX_32_ALIGN]);
        final long offset = ParseUtil.asInt64(args[ARGINDEX_32_OFFSET]);
        final long flags = ParseUtil.asInt32(args[ARGINDEX_32_FLAGS]);
        final MDReference baseType = ParseUtil.getReference(args[ARGINDEX_32_BASETYPE]);
        final MDReference members = ParseUtil.getReferenceIfPresent(args, ARGINDEX_32_MEMBERDESCRIPTORS);
        final long lang = ParseUtil.asInt64IfPresent(args, ARGINDEX_32_RUNTIMELANGUAGE);
        final MDReference templateParams = ParseUtil.getReferenceIfPresent(args, ARGINDEX_32_TEMPLATEPARAMS);
        return new MDCompositeType(tag, context, name, file, line, size, align, offset, flags, baseType, members, lang, MDReference.VOID, templateParams, MDReference.VOID);
    }
}
