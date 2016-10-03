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

public class MetadataSubprogram implements MetadataBaseNode {

    private MetadataReference name = MetadataBlock.voidRef;
    private MetadataReference displayName = MetadataBlock.voidRef;
    private MetadataReference linkageName = MetadataBlock.voidRef;
    private MetadataReference file = MetadataBlock.voidRef;
    private long line;
    private MetadataReference type = MetadataBlock.voidRef;
    private boolean isLocalToUnit;
    private boolean isDefinedInCompileUnit;
    private long scopeLine;
    private MetadataReference containingType = MetadataBlock.voidRef;
    private long virtuallity;
    private long virtualIndex;
    private long flags;
    private boolean isOptimized;
    private MetadataReference templateParams = MetadataBlock.voidRef;
    private MetadataReference declaration = MetadataBlock.voidRef;
    private MetadataReference variables = MetadataBlock.voidRef;

    public MetadataReference getName() {
        return name;
    }

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

    public MetadataReference getType() {
        return type;
    }

    public void setType(MetadataReference type) {
        this.type = type;
    }

    public boolean isLocalToUnit() {
        return isLocalToUnit;
    }

    public void setLocalToUnit(boolean isLocalToUnit) {
        this.isLocalToUnit = isLocalToUnit;
    }

    public boolean isDefinedInCompileUnit() {
        return isDefinedInCompileUnit;
    }

    public void setDefinedInCompileUnit(boolean isDefinition) {
        this.isDefinedInCompileUnit = isDefinition;
    }

    public long getScopeLine() {
        return scopeLine;
    }

    public void setScopeLine(long scopeLine) {
        this.scopeLine = scopeLine;
    }

    public MetadataReference getContainingType() {
        return containingType;
    }

    public void setContainingType(MetadataReference containingType) {
        this.containingType = containingType;
    }

    public long getVirtuallity() {
        return virtuallity;
    }

    public void setVirtuallity(long virtuallity) {
        this.virtuallity = virtuallity;
    }

    public long getVirtualIndex() {
        return virtualIndex;
    }

    public void setVirtualIndex(long virtualIndex) {
        this.virtualIndex = virtualIndex;
    }

    public long getFlags() {
        return flags;
    }

    public void setFlags(long flags) {
        this.flags = flags;
    }

    public boolean isOptimized() {
        return isOptimized;
    }

    public void setOptimized(boolean isOptimized) {
        this.isOptimized = isOptimized;
    }

    public MetadataReference getTemplateParams() {
        return templateParams;
    }

    public void setTemplateParams(MetadataReference templateParams) {
        this.templateParams = templateParams;
    }

    public MetadataReference getDeclaration() {
        return declaration;
    }

    public void setDeclaration(MetadataReference declaration) {
        this.declaration = declaration;
    }

    public MetadataReference getVariables() {
        return variables;
    }

    public void setVariables(MetadataReference variables) {
        this.variables = variables;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("MetadataSubprogram [name=");
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
        builder.append(", isLocalToUnit=");
        builder.append(isLocalToUnit);
        builder.append(", isDefinedInCompileUnit=");
        builder.append(isDefinedInCompileUnit);
        builder.append(", scopeLine=");
        builder.append(scopeLine);
        builder.append(", containingType=");
        builder.append(containingType);
        builder.append(", virtuallity=");
        builder.append(virtuallity);
        builder.append(", virtualIndex=");
        builder.append(virtualIndex);
        builder.append(", flags=");
        builder.append(flags);
        builder.append(", isOptimized=");
        builder.append(isOptimized);
        builder.append(", templateParams=");
        builder.append(templateParams);
        builder.append(", declaration=");
        builder.append(declaration);
        builder.append(", variables=");
        builder.append(variables);
        builder.append("]");
        return builder.toString();
    }

}
