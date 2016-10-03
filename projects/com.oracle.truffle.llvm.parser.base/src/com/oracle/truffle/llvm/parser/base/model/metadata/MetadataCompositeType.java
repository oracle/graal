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
package com.oracle.truffle.llvm.parser.base.model.metadata;

import com.oracle.truffle.llvm.parser.base.model.blocks.MetadataBlock;
import com.oracle.truffle.llvm.parser.base.model.blocks.MetadataBlock.MetadataReference;

public class MetadataCompositeType implements MetadataBaseNode {

    private MetadataReference context = MetadataBlock.voidRef;
    private MetadataReference name = MetadataBlock.voidRef;
    private MetadataReference file = MetadataBlock.voidRef;
    private long line;
    private long size;
    private long align;
    private long offset;
    private long flags;
    private MetadataReference derivedFrom = MetadataBlock.voidRef;
    private MetadataReference memberDescriptors = MetadataBlock.voidRef;
    private long runtimeLanguage;

    public MetadataReference getContext() {
        return context;
    }

    public void setContext(MetadataReference context) {
        this.context = context;
    }

    public MetadataReference getName() {
        return name;
    }

    public void setName(MetadataReference name) {
        this.name = name;
    }

    public MetadataReference getFile() {
        return file;
    }

    public void setFile(MetadataReference file) {
        this.file = file;
    }

    public long getLine() {
        return line;
    }

    public void setLine(long line) {
        this.line = line;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getAlign() {
        return align;
    }

    public void setAlign(long align) {
        this.align = align;
    }

    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public long getFlags() {
        return flags;
    }

    public void setFlags(long flags) {
        this.flags = flags;
    }

    public MetadataReference getDerivedFrom() {
        return derivedFrom;
    }

    public void setDerivedFrom(MetadataReference derivedFrom) {
        this.derivedFrom = derivedFrom;
    }

    public MetadataReference getMemberDescriptors() {
        return memberDescriptors;
    }

    public void setMemberDescriptors(MetadataReference memberDescriptors) {
        this.memberDescriptors = memberDescriptors;
    }

    public long getRuntimeLanguage() {
        return runtimeLanguage;
    }

    public void setRuntimeLanguage(long runtimeLanguage) {
        this.runtimeLanguage = runtimeLanguage;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("MetadataCompositeType [context=");
        builder.append(context);
        builder.append(", name=");
        builder.append(name);
        builder.append(", file=");
        builder.append(file);
        builder.append(", line=");
        builder.append(line);
        builder.append(", size=");
        builder.append(size);
        builder.append(", align=");
        builder.append(align);
        builder.append(", offset=");
        builder.append(offset);
        builder.append(", flags=");
        builder.append(flags);
        builder.append(", derivedFrom=");
        builder.append(derivedFrom);
        builder.append(", memberDescriptors=");
        builder.append(memberDescriptors);
        builder.append(", runtimeLanguage=");
        builder.append(runtimeLanguage);
        builder.append("]");
        return builder.toString();
    }

}
