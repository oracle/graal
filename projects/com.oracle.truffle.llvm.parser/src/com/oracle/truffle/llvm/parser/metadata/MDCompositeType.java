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

public final class MDCompositeType extends MDType implements MDBaseNode {

    private final long runtimeLanguage;

    private MDBaseNode scope;
    private MDBaseNode baseType;
    private MDBaseNode members;
    private MDBaseNode templateParams;
    private MDBaseNode identifier;

    private MDCompositeType(long tag, long line, long size, long align, long offset, long flags, long runtimeLanguage) {
        super(tag, size, align, offset, line, flags);
        this.runtimeLanguage = runtimeLanguage;

        this.scope = MDVoidNode.INSTANCE;
        this.baseType = MDVoidNode.INSTANCE;
        this.members = MDVoidNode.INSTANCE;
        this.templateParams = MDVoidNode.INSTANCE;
        this.identifier = MDVoidNode.INSTANCE;
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    public MDBaseNode getScope() {
        return scope;
    }

    public MDBaseNode getBaseType() {
        return baseType;
    }

    public MDBaseNode getMembers() {
        return members;
    }

    public long getRuntimeLanguage() {
        return runtimeLanguage;
    }

    public MDBaseNode getTemplateParams() {
        return templateParams;
    }

    public MDBaseNode getIdentifier() {
        return identifier;
    }

    @Override
    public void replace(MDBaseNode oldValue, MDBaseNode newValue) {
        super.replace(oldValue, newValue);
        if (scope == oldValue) {
            scope = newValue;
        }
        if (baseType == oldValue) {
            baseType = newValue;
        }
        if (members == oldValue) {
            members = newValue;
        }
        if (templateParams == oldValue) {
            templateParams = newValue;
        }
        if (identifier == oldValue) {
            identifier = newValue;
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
    private static final int ARGINDEX_38_MEMBERS = 11;
    private static final int ARGINDEX_38_RUNTIMELANGUAGE = 12;
    private static final int ARGINDEX_38_TEMPLATEPARAMS = 14;
    private static final int ARGINDEX_38_IDENTIFIER = 15;

    public static MDCompositeType create38(long[] args, MetadataValueList md) {
        final long tag = args[ARGINDEX_38_TAG];
        final long line = args[ARGINDEX_38_LINE];
        final long size = args[ARGINDEX_38_SIZE];
        final long align = args[ARGINDEX_38_ALIGN];
        final long offset = args[ARGINDEX_38_OFFSET];
        final long flags = args[ARGINDEX_38_FLAGS];
        final long lang = args[ARGINDEX_38_RUNTIMELANGUAGE];

        final MDCompositeType compositeType = new MDCompositeType(tag, line, size, align, offset, flags, lang);

        compositeType.scope = md.getNullable(args[ARGINDEX_38_SCOPE], compositeType);
        compositeType.baseType = md.getNullable(args[ARGINDEX_38_BASETYPE], compositeType);
        compositeType.members = md.getNullable(args[ARGINDEX_38_MEMBERS], compositeType);
        compositeType.templateParams = md.getNullable(args[ARGINDEX_38_TEMPLATEPARAMS], compositeType);
        compositeType.identifier = md.getNullable(args[ARGINDEX_38_IDENTIFIER], compositeType);

        compositeType.setFile(md.getNullable(args[ARGINDEX_38_FILE], compositeType));
        compositeType.setName(md.getNullable(args[ARGINDEX_38_NAME], compositeType));

        return compositeType;
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
    private static final int ARGINDEX_32_MEMBERS = 10;
    private static final int ARGINDEX_32_RUNTIMELANGUAGE = 11;
    private static final int ARGINDEX_32_TEMPLATEPARAMS = 13;

    public static MDCompositeType create32(long[] args, Metadata md) {
        final long tag = DwTagRecord.decode(ParseUtil.asLong(args, ARGINDEX_32_TAG, md)).code();
        final long line = ParseUtil.asInt(args, ARGINDEX_32_LINE, md);
        final long size = ParseUtil.asLong(args, ARGINDEX_32_SIZE, md);
        final long align = ParseUtil.asLong(args, ARGINDEX_32_ALIGN, md);
        final long offset = ParseUtil.asLong(args, ARGINDEX_32_OFFSET, md);
        final long flags = ParseUtil.asInt(args, ARGINDEX_32_FLAGS, md);
        final long lang = ParseUtil.asLong(args, ARGINDEX_32_RUNTIMELANGUAGE, md);

        final MDCompositeType compositeType = new MDCompositeType(tag, line, size, align, offset, flags, lang);

        compositeType.scope = ParseUtil.resolveReference(args, ARGINDEX_32_SCOPE, compositeType, md);
        compositeType.baseType = ParseUtil.resolveReference(args, ARGINDEX_32_BASETYPE, compositeType, md);

        compositeType.members = ParseUtil.resolveReference(args, ARGINDEX_32_MEMBERS, compositeType, md);
        compositeType.templateParams = ParseUtil.resolveReference(args, ARGINDEX_32_TEMPLATEPARAMS, compositeType, md);

        compositeType.setFile(ParseUtil.resolveReference(args, ARGINDEX_32_FILE, compositeType, md));
        compositeType.setName(ParseUtil.resolveReference(args, ARGINDEX_32_NAME, compositeType, md));

        return compositeType;
    }
}
