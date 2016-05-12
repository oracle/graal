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
package com.oracle.truffle.llvm.asm.amd64;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.llvm.nodes.impl.asm.LLVMAsmAddlNodeGen;
import com.oracle.truffle.llvm.nodes.impl.asm.LLVMAsmSublNodeGen;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMInlineAssemblyRootNode;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMUnsupportedInlineAssemblerNode.LLVMI32UnsupportedInlineAssemblerNode;

public class AsmNodeFactory {

    public LLVMInlineAssemblyRootNode finishInline(LLVMI32Node n) {
        return new LLVMInlineAssemblyRootNode(null, new FrameDescriptor(), n);
    }

    /**
     *
     * @param operation The binary assembly operation for which a new node has to be generated
     * @param leftNode The left child node of this operation
     * @param rightNode The right child node of this operation
     * @return A subclass of LLVMI32Node using the given parameters based on the given operation
     */
    public LLVMI32Node createBinary(String operation, LLVMI32Node leftNode, LLVMI32Node rightNode) {
        switch (operation) {
            case "addl":
                return LLVMAsmAddlNodeGen.create(leftNode, rightNode);
            case "subl":
                return LLVMAsmSublNodeGen.create(leftNode, rightNode);
            default:
                return new LLVMI32UnsupportedInlineAssemblerNode();
        }
    }

}
