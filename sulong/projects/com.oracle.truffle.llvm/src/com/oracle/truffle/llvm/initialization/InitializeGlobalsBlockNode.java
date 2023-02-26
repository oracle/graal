/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.initialization;

import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.memory.LLVMAllocateNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.Type.TypeOverflowException;

public final class InitializeGlobalsBlockNode extends LLVMNode {

    @Child private LLVMAllocateNode allocDataSection;

    private final NodeFactory nodeFactory;

    private final StructureType rwSectionType;
    private final StructureType roSectionType;

    private final long rwSectionSize;
    private final long globalsBlockSize;

    public InitializeGlobalsBlockNode(LLVMParserResult result, DataSectionFactory dataSectionFactory, LLVMLanguage language) {
        this.nodeFactory = result.getRuntime().getNodeFactory();

        rwSectionType = dataSectionFactory.getRwSection().getStructureType("rwglobals_struct");
        roSectionType = dataSectionFactory.getRoSection().getStructureType("roglobals_struct");

        long blockSize = 0;
        long rwSize = 0;
        try {
            if (rwSectionType != null) {
                blockSize += alignPageSize(language, rwSectionType.getSize(result.getDataLayout()));
            }
            rwSize = blockSize;

            if (roSectionType != null) {
                blockSize += alignPageSize(language, roSectionType.getSize(result.getDataLayout()));
            }

            this.allocDataSection = blockSize > 0 ? nodeFactory.createAllocateGlobalsBlock(blockSize) : null;
        } catch (TypeOverflowException ex) {
            this.allocDataSection = Type.handleOverflowAllocate(ex);
        }

        globalsBlockSize = blockSize;
        rwSectionSize = rwSize;
    }

    public long getGlobalsBlockSize() {
        return globalsBlockSize;
    }

    public long getRoBlockSize() {
        return globalsBlockSize - rwSectionSize;
    }

    public boolean hasGlobalsBlock() {
        return allocDataSection != null;
    }

    public boolean hasRwSection() {
        return rwSectionSize > 0;
    }

    public boolean hasRoSection() {
        return getRoBlockSize() > 0;
    }

    public LLVMPointer getRwSectionPointer(LLVMPointer basePointer) {
        return hasRwSection() ? basePointer : null;
    }

    public LLVMPointer getRoSectionPointer(LLVMPointer basePointer) {
        return hasRoSection() ? basePointer.increment(rwSectionSize) : null;
    }

    private static long alignPageSize(LLVMLanguage language, long value) {
        int pageSize = language.getLLVMMemory().getPageSize();
        return ((value + (pageSize - 1)) / pageSize) * pageSize;
    }

    public LLVMPointer allocateGlobalsSectionBlock() {
        return allocDataSection != null ? allocDataSection.executeWithTarget() : null;
    }
}
