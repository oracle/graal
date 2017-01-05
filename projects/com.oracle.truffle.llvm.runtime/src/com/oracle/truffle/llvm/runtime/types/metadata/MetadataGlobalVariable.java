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
package com.oracle.truffle.llvm.runtime.types.metadata;

import com.oracle.truffle.llvm.runtime.types.MetadataVisitor;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataBlock.MetadataReference;
import com.oracle.truffle.llvm.runtime.types.metadata.subtypes.MetadataSubtypeName;
import com.oracle.truffle.llvm.runtime.types.metadata.subtypes.MetadataSubtypeType;

public class MetadataGlobalVariable implements MetadataBaseNode, MetadataSubtypeName, MetadataSubtypeType {

    private MetadataReference context = MetadataBlock.voidRef;
    private MetadataReference name = MetadataBlock.voidRef;
    private MetadataReference displayName = MetadataBlock.voidRef;
    private MetadataReference linkageName = MetadataBlock.voidRef;
    private MetadataReference file = MetadataBlock.voidRef;
    private long line;
    private MetadataReference type = MetadataBlock.voidRef;
    private boolean isLocalToCompileUnit;
    private boolean isDefinedInCompileUnit;

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    public MetadataReference getContext() {
        return context;
    }

    public void setContext(MetadataReference context) {
        this.context = context;
    }

    @Override
    public MetadataReference getName() {
        return name;
    }

    @Override
    public void setName(MetadataReference name) {
        this.name = name;
    }

    public MetadataReference getDisplayName() {
        return displayName;
    }

    public void setDisplayName(MetadataReference displayName) {
        this.displayName = displayName;
    }

    public MetadataReference getLinkageName() {
        return linkageName;
    }

    public void setLinkageName(MetadataReference linkageName) {
        this.linkageName = linkageName;
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
    public MetadataReference getType() {
        return type;
    }

    @Override
    public void setType(MetadataReference type) {
        this.type = type;
    }

    public boolean isLocalToCompileUnit() {
        return isLocalToCompileUnit;
    }

    public void setLocalToCompileUnit(boolean isLocalToCompileUnit) {
        this.isLocalToCompileUnit = isLocalToCompileUnit;
    }

    public boolean isDefinedInCompileUnit() {
        return isDefinedInCompileUnit;
    }

    public void setDefinedInCompileUnit(boolean isDefinedInCompileUnit) {
        this.isDefinedInCompileUnit = isDefinedInCompileUnit;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("MetadataGlobalVariable [context=");
        builder.append(context);
        builder.append(", name=");
        builder.append(name);
        builder.append(", displayName=");
        builder.append(displayName);
        builder.append(", linkageName=");
        builder.append(linkageName);
        builder.append(", file=");
        builder.append(file);
        builder.append(", line=");
        builder.append(line);
        builder.append(", type=");
        builder.append(type);
        builder.append(", isLocalToCompileUnit=");
        builder.append(isLocalToCompileUnit);
        builder.append(", isDefinedInCompileUnit=");
        builder.append(isDefinedInCompileUnit);
        builder.append("]");
        return builder.toString();
    }
}
