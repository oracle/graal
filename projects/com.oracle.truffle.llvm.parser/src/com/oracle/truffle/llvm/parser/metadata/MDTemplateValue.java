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

public final class MDTemplateValue extends MDName implements MDBaseNode {

    private final long tag;

    private final MDReference type;

    private final MDReference value;

    private MDTemplateValue(MDReference name, long tag, MDReference type, MDReference value) {
        super(name);
        this.tag = tag;
        this.type = type;
        this.value = value;
    }

    public long getTag() {
        return tag;
    }

    public MDReference getType() {
        return type;
    }

    public MDReference getValue() {
        return value;
    }

    @Override
    public String toString() {
        return String.format("TemplateValue (name=%s, tag=%d, type=%s, value=%s)", getName(), tag, type, value);
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    private static final int ARGINDEX_TAG = 1;
    private static final int ARGINDEX_NAME = 2;
    private static final int ARGINDEX_TYPE = 3;
    private static final int ARGINDEX_VALUE = 4;

    public static MDTemplateValue create38(long[] args, MetadataList md) {
        final long tag = args[ARGINDEX_TAG];
        final MDReference name = md.getMDRefOrNullRef(args[ARGINDEX_NAME]);
        final MDReference type = md.getMDRefOrNullRef(args[ARGINDEX_TYPE]);
        final MDReference value = md.getMDRefOrNullRef(args[ARGINDEX_VALUE]);
        return new MDTemplateValue(name, tag, type, value);
    }

    public static MDTemplateValue create32(MDTypedValue[] args) {
        // final MDReference context = getReference(args[1]);
        final MDReference name = ParseUtil.getReference(args[ARGINDEX_NAME]);
        final MDReference type = ParseUtil.getReference(args[ARGINDEX_TYPE]);
        final MDReference value = MDReference.fromSymbolRef(ParseUtil.getSymbolReference(args[ARGINDEX_VALUE]));
        // final MDReference file = getReference(args[5]);
        // final long line = ParseUtil.asInt64(args[6]);
        // final long column = ParseUtil.asInt64(args[7]);
        return new MDTemplateValue(name, -1L, type, value);
    }
}
