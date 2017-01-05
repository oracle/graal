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

import com.oracle.truffle.llvm.runtime.types.DwLangNameRecord;
import com.oracle.truffle.llvm.runtime.types.MetadataVisitor;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataBlock.MetadataReference;

public class MetadataCompileUnit implements MetadataBaseNode {

    private long context;
    private DwLangNameRecord language;
    private MetadataReference file = MetadataBlock.voidRef;
    private MetadataReference directory = MetadataBlock.voidRef;
    private MetadataReference producer = MetadataBlock.voidRef;
    private boolean isDeprecatedField;
    private boolean isOptimized;
    private MetadataReference flags = MetadataBlock.voidRef;
    private long runtimeVersion;
    private MetadataReference enumType = MetadataBlock.voidRef;
    private MetadataReference retainedTypes = MetadataBlock.voidRef;
    private MetadataReference subprograms = MetadataBlock.voidRef;
    private MetadataReference globalVariables = MetadataBlock.voidRef;

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    public long getContext() {
        return context;
    }

    public void setContext(long context) {
        this.context = context;
    }

    public DwLangNameRecord getLanguage() {
        return language;
    }

    public void setLanguage(DwLangNameRecord language) {
        this.language = language;
    }

    public MetadataReference getFile() {
        return file;
    }

    public void setFile(MetadataReference file) {
        this.file = file;
    }

    public MetadataReference getDirectory() {
        return directory;
    }

    public void setDirectory(MetadataReference directory) {
        this.directory = directory;
    }

    public MetadataReference getProducer() {
        return producer;
    }

    public void setProducer(MetadataReference producer) {
        this.producer = producer;
    }

    public boolean isDeprecatedField() {
        return isDeprecatedField;
    }

    public void setDeprecatedField(boolean isDeprecatedField) {
        this.isDeprecatedField = isDeprecatedField;
    }

    public boolean isOptimized() {
        return isOptimized;
    }

    public void setOptimized(boolean isOptimized) {
        this.isOptimized = isOptimized;
    }

    public MetadataReference getFlags() {
        return flags;
    }

    public void setFlags(MetadataReference flags) {
        this.flags = flags;
    }

    public long getRuntimeVersion() {
        return runtimeVersion;
    }

    public void setRuntimeVersion(long runtimeVersion) {
        this.runtimeVersion = runtimeVersion;
    }

    public MetadataBaseNode getEnumType() {
        return enumType;
    }

    public void setEnumType(MetadataReference enumType) {
        this.enumType = enumType;
    }

    public MetadataReference getRetainedTypes() {
        return retainedTypes;
    }

    public void setRetainedTypes(MetadataReference retainedTypes) {
        this.retainedTypes = retainedTypes;
    }

    public MetadataReference getSubprograms() {
        return subprograms;
    }

    public void setSubprograms(MetadataReference subprograms) {
        this.subprograms = subprograms;
    }

    public MetadataReference getGlobalVariables() {
        return globalVariables;
    }

    public void setGlobalVariables(MetadataReference globalVariables) {
        this.globalVariables = globalVariables;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("MetadataCompileUnit [context=");
        builder.append(context);
        builder.append(", language=");
        builder.append(language);
        builder.append(", file=");
        builder.append(file);
        builder.append(", directory=");
        builder.append(directory);
        builder.append(", producer=");
        builder.append(producer);
        builder.append(", isDeprecatedField=");
        builder.append(isDeprecatedField);
        builder.append(", isOptimized=");
        builder.append(isOptimized);
        builder.append(", flags=");
        builder.append(flags);
        builder.append(", runtimeVersion=");
        builder.append(runtimeVersion);
        builder.append(", enumType=");
        builder.append(enumType);
        builder.append(", retainedTypes=");
        builder.append(retainedTypes);
        builder.append(", subprograms=");
        builder.append(subprograms);
        builder.append(", globalVariables=");
        builder.append(globalVariables);
        builder.append("]");
        return builder.toString();
    }
}
