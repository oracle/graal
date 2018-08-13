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

import com.oracle.truffle.llvm.parser.listeners.Metadata;

public final class MDTemplateValue extends MDName implements MDBaseNode {

    private final long tag;

    private MDBaseNode type;
    private MDBaseNode value;

    private MDTemplateValue(long tag) {
        this.tag = tag;

        this.type = MDVoidNode.INSTANCE;
        this.value = MDVoidNode.INSTANCE;
    }

    public long getTag() {
        return tag;
    }

    public MDBaseNode getType() {
        return type;
    }

    public MDBaseNode getValue() {
        return value;
    }

    @Override
    public void replace(MDBaseNode oldValue, MDBaseNode newValue) {
        super.replace(oldValue, newValue);
        if (type == oldValue) {
            type = newValue;
        }
        if (value == oldValue) {
            value = newValue;
        }
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    private static final int ARGINDEX_TAG = 1;
    private static final int ARGINDEX_NAME = 2;
    private static final int ARGINDEX_TYPE = 3;
    private static final int ARGINDEX_VALUE = 4;

    public static MDTemplateValue create38(long[] args, MetadataValueList md) {
        final long tag = args[ARGINDEX_TAG];

        final MDTemplateValue templateValue = new MDTemplateValue(tag);

        templateValue.type = md.getNullable(args[ARGINDEX_TYPE], templateValue);
        templateValue.value = md.getNullable(args[ARGINDEX_VALUE], templateValue);
        templateValue.setName(md.getNullable(args[ARGINDEX_NAME], templateValue));

        return templateValue;
    }

    public static MDTemplateValue create32(long[] args, Metadata md) {
        // final MDReference context = getReference(args[1]);
        // final MDReference file = getReference(args[5]);
        // final long line = ParseUtil.asInt64(args[6]);
        // final long column = ParseUtil.asInt64(args[7]);

        final MDTemplateValue templateValue = new MDTemplateValue(-1L);

        templateValue.type = ParseUtil.resolveReference(args, ARGINDEX_TYPE, templateValue, md);
        templateValue.value = ParseUtil.resolveSymbol(args, ARGINDEX_VALUE, md);
        templateValue.setName(ParseUtil.resolveReference(args, ARGINDEX_NAME, templateValue, md));

        return templateValue;
    }
}
