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
package com.oracle.truffle.llvm.parser.model.functions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.llvm.parser.metadata.MDAttachment;
import com.oracle.truffle.llvm.parser.model.attributes.AttributesCodeEntry;
import com.oracle.truffle.llvm.parser.model.attributes.AttributesGroup;
import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.enums.Linkage;
import com.oracle.truffle.llvm.parser.model.IRScope;
import com.oracle.truffle.llvm.parser.model.symbols.constants.Constant;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.Instruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ValueInstruction;
import com.oracle.truffle.llvm.parser.model.visitors.ConstantVisitor;
import com.oracle.truffle.llvm.parser.model.visitors.FunctionVisitor;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.symbols.LLVMIdentifier;
import com.oracle.truffle.llvm.runtime.types.symbols.ValueSymbol;

public final class FunctionDefinition extends IRScope implements Constant, ValueSymbol {

    private final List<FunctionParameter> parameters = new ArrayList<>();
    private final FunctionType type;
    private final AttributesCodeEntry paramAttr;
    private final Linkage linkage;

    private List<MDAttachment> mdAttachments = null;

    private InstructionBlock[] blocks = new InstructionBlock[0];
    private int currentBlock = 0;
    private String name;

    public FunctionDefinition(FunctionType type, String name, Linkage linkage, AttributesCodeEntry paramAttr) {
        this.type = type;
        this.name = name;
        this.paramAttr = paramAttr;
        this.linkage = linkage;
    }

    public FunctionDefinition(FunctionType type, Linkage linkage, AttributesCodeEntry paramAttr) {
        this(type, LLVMIdentifier.UNKNOWN, linkage, paramAttr);
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

    public Linkage getLinkage() {
        return linkage;
    }

    @Override
    public boolean hasName() {
        return name != null;
    }

    @Override
    public String getName() {
        assert name != null;
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = LLVMIdentifier.toGlobalIdentifier(name);
    }

    @Override
    public void accept(ConstantVisitor visitor) {
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

    @Override
    public FunctionType getType() {
        return type;
    }

    public AttributesGroup getFunctionAttributesGroup() {
        CompilerAsserts.neverPartOfCompilation();
        return paramAttr.getFunctionAttributesGroup();
    }

    public AttributesGroup getReturnAttributesGroup() {
        CompilerAsserts.neverPartOfCompilation();
        return paramAttr.getReturnAttributesGroup();
    }

    public AttributesGroup getParameterAttributesGroup(int idx) {
        CompilerAsserts.neverPartOfCompilation();
        return paramAttr.getParameterAttributesGroup(idx);
    }

    public void createParameter(Type t) {
        final int index = parameters.size();
        final AttributesGroup attrGroup = paramAttr.getParameterAttributesGroup(index);
        final FunctionParameter parameter = new FunctionParameter(t, index, attrGroup);
        addSymbol(parameter, t);
        parameters.add(parameter);
    }

    public void exitFunction() {
        int symbolIndex = 0;

        // in K&R style function declarations the parameters are not assigned names
        for (final FunctionParameter parameter : parameters) {
            if (LLVMIdentifier.UNKNOWN.equals(parameter.getName())) {
                parameter.setName(String.valueOf(symbolIndex++));
            }
        }

        final Set<String> explicitBlockNames = Arrays.stream(blocks).map(InstructionBlock::getName).filter(blockName -> !LLVMIdentifier.UNKNOWN.equals(blockName)).collect(Collectors.toSet());
        for (final InstructionBlock block : blocks) {
            if (block.getName().equals(LLVMIdentifier.UNKNOWN)) {
                do {
                    block.setImplicitName(symbolIndex++);
                    // avoid name clashes
                } while (explicitBlockNames.contains(block.getName()));
            }
            for (int i = 0; i < block.getInstructionCount(); i++) {
                final Instruction instruction = block.getInstruction(i);
                if (instruction instanceof ValueInstruction) {
                    final ValueInstruction value = (ValueInstruction) instruction;
                    if (value.getName().equals(LLVMIdentifier.UNKNOWN)) {
                        value.setName(String.valueOf(symbolIndex++));
                    }
                }
            }
        }
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

    @Override
    public void nameBlock(int index, String argName) {
        blocks[index].setName(argName);
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
        final String formalArgs = parameters.stream().map(FunctionParameter::getFrameSlotName).collect(Collectors.joining(", "));
        return String.format("FunctionDefinition %s(%s) {%d blocks}", name, formalArgs, blocks == null ? 0 : blocks.length);
    }
}
