/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.model.functions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.llvm.parser.metadata.MDAttachment;
import com.oracle.truffle.llvm.parser.metadata.MDString;
import com.oracle.truffle.llvm.parser.metadata.MDSubprogram;
import com.oracle.truffle.llvm.parser.metadata.MetadataAttachmentHolder;
import com.oracle.truffle.llvm.parser.metadata.debuginfo.SourceFunction;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.parser.model.attributes.AttributesCodeEntry;
import com.oracle.truffle.llvm.parser.model.attributes.AttributesGroup;
import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.enums.Linkage;
import com.oracle.truffle.llvm.parser.model.enums.Visibility;
import com.oracle.truffle.llvm.parser.model.symbols.constants.Constant;
import com.oracle.truffle.llvm.parser.model.visitors.FunctionVisitor;
import com.oracle.truffle.llvm.parser.model.visitors.SymbolVisitor;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.symbols.LLVMIdentifier;

public final class FunctionDefinition extends FunctionSymbol implements Constant, MetadataAttachmentHolder {

    public static final InstructionBlock[] EMPTY = new InstructionBlock[0];

    private final List<FunctionParameter> parameters = new ArrayList<>();
    private final Visibility visibility;

    private List<MDAttachment> mdAttachments = null;
    private SourceFunction sourceFunction = SourceFunction.DEFAULT;

    private InstructionBlock[] blocks = EMPTY;
    private int currentBlock = 0;

    private FunctionDefinition(FunctionType type, String name, Linkage linkage, Visibility visibility, AttributesCodeEntry paramAttr, int index) {
        super(type, name, linkage, paramAttr, index);
        this.visibility = visibility;
    }

    public FunctionDefinition(FunctionType type, Linkage linkage, Visibility visibility, AttributesCodeEntry paramAttr, int index) {
        this(type, LLVMIdentifier.UNKNOWN, linkage, visibility, paramAttr, index);
    }

    @Override
    public boolean hasAttachedMetadata() {
        return mdAttachments != null;
    }

    @Override
    public List<MDAttachment> getAttachedMetadata() {
        if (mdAttachments == null) {
            mdAttachments = new ArrayList<>(1);
        }
        return mdAttachments;
    }

    public String getSourceName() {
        final String scopeName = sourceFunction.getName();
        return SourceFunction.DEFAULT_SOURCE_NAME.equals(scopeName) ? null : scopeName;
    }

    public String getDisplayName() {
        /*
         * For LLVM code produced from C++ sources, function.name stores the linkage name, but not
         * 'original' C++ name.
         */
        if (mdAttachments != null && mdAttachments.size() > 0) {
            for (MDAttachment mdAttachment : mdAttachments) {
                if (mdAttachment.getValue() instanceof MDSubprogram) {
                    MDSubprogram mdSubprogram = (MDSubprogram) mdAttachment.getValue();
                    if (mdSubprogram.getName() instanceof MDString) {
                        return ((MDString) mdSubprogram.getName()).getString();
                    }
                }
            }
        }
        return getSourceName();
    }

    @Override
    public void replace(SymbolImpl oldValue, SymbolImpl newValue) {
    }

    @Override
    public void accept(SymbolVisitor visitor) {
        visitor.visit(this);
    }

    public void accept(FunctionVisitor visitor) {
        for (InstructionBlock block : blocks) {
            visitor.visit(block);
        }
    }

    public void allocateBlocks(int count) {
        blocks = new InstructionBlock[count];
        for (int i = 0; i < count; i++) {
            blocks[i] = new InstructionBlock(i);
        }
    }

    public FunctionParameter createParameter(Type t) {
        final int argIndex = parameters.size();
        final AttributesGroup attrGroup = getParameterAttributesGroup(argIndex);
        final FunctionParameter parameter = new FunctionParameter(t, attrGroup, argIndex);
        parameters.add(parameter);
        return parameter;
    }

    public InstructionBlock generateBlock() {
        return blocks[currentBlock++];
    }

    public InstructionBlock getBlock(long idx) {
        CompilerAsserts.neverPartOfCompilation();
        return blocks[(int) idx];
    }

    public List<InstructionBlock> getBlocks() {
        CompilerAsserts.neverPartOfCompilation();
        return Arrays.asList(blocks);
    }

    public List<FunctionParameter> getParameters() {
        CompilerAsserts.neverPartOfCompilation();
        return parameters;
    }

    public void nameBlock(int index, String argName) {
        blocks[index].setName(argName);
    }

    public void onAfterParse() {
        // drop the parser symbol tree after parsing the function
        blocks = EMPTY;
        currentBlock = 0;
        mdAttachments = null;
        sourceFunction.clearLocals();
        parameters.clear();
    }

    @Override
    public int hashCode() {
        CompilerAsserts.neverPartOfCompilation();
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        CompilerAsserts.neverPartOfCompilation();
        return super.equals(obj);
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return String.format("%s %s {...}", getType(), getName());
    }

    public LLVMSourceLocation getLexicalScope() {
        return sourceFunction != null ? sourceFunction.getLexicalScope() : null;
    }

    public SourceFunction getSourceFunction() {
        return sourceFunction;
    }

    public void setSourceFunction(SourceFunction sourceFunction) {
        this.sourceFunction = sourceFunction;
    }

    @Override
    public boolean isExported() {
        return Linkage.isExported(getLinkage(), visibility);
    }

    @Override
    public boolean isOverridable() {
        return Linkage.isOverridable(getLinkage(), visibility);
    }

    @Override
    public boolean isExternal() {
        return Linkage.isExternal(getLinkage());
    }
}
