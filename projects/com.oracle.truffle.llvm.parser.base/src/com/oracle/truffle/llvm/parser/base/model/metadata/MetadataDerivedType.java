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
import com.oracle.truffle.llvm.parser.base.model.metadata.subtypes.MetadataSubtypeName;
import com.oracle.truffle.llvm.parser.base.model.metadata.subtypes.MetadataSubytypeSizeAlignOffset;
import com.oracle.truffle.llvm.parser.base.model.visitors.MetadataVisitor;

public class MetadataDerivedType implements MetadataBaseNode, MetadataSubtypeName, MetadataSubytypeSizeAlignOffset {

    private MetadataReference name = MetadataBlock.voidRef;
    private MetadataReference file = MetadataBlock.voidRef;
    private long line;
    private long size;
    private long align;
    private long offset;
    private long flags;
    private MetadataReference baseType = MetadataBlock.voidRef;

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public MetadataReference getName() {
        return name;
    }

    @Override
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

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public void setSize(long size) {
        this.size = size;
    }

    @Override
    public long getAlign() {
        return align;
    }

    @Override
    public void setAlign(long align) {
        this.align = align;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public void setOffset(long offset) {
        this.offset = offset;
    }

    public long getFlags() {
        return flags;
    }

    public void setFlags(long flags) {
        this.flags = flags;
    }

    public MetadataReference getBaseType() {
        return baseType;
    }

    public void setBaseType(MetadataReference baseType) {
        this.baseType = baseType;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("MetadataDerivedType [name=");
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
        builder.append(", baseType=");
        builder.append(baseType);
        builder.append("]");
        return builder.toString();
    }

    private boolean isOnlyReference() {
        return size == 0 && align == 0 && offset == 0 && flags == 0;
    }

    public MetadataReference getTrueBaseType() {
        if (isOnlyReference() && baseType instanceof MetadataDerivedType) {
            return ((MetadataDerivedType) baseType.get()).getTrueBaseType();
        }
        return baseType;
    }
}
