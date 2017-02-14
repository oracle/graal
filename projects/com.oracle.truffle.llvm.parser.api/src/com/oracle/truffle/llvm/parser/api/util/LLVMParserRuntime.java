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
package com.oracle.truffle.llvm.parser.api.util;

import java.util.Map;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.parser.api.facade.NodeFactoryFacade;
import com.oracle.truffle.llvm.parser.api.model.globals.GlobalValueSymbol;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.memory.LLVMHeapFunctions;

public interface LLVMParserRuntime {

    /**
     * Performs an <code>alloc</code> style allocation. At the begin of a function (or the global
     * scope) the memory is allocated and again released when leaving the function (or the global
     * scope). The intrinsic <code>@llvm.stacksave</code> might also cause the allocation to be
     * released earlier.
     *
     * @see <a href="http://llvm.org/docs/LangRef.html#llvm-stacksave-intrinsic">llvm.stacksave
     *      intrinsic</a>
     * @param size the bytes to be allocated
     * @return a node that allocates the requested memory.
     */
    LLVMExpressionNode allocateFunctionLifetime(Type type, int size, int alignment);

    /**
     * Gets the return slot where the function return value is stored.
     *
     * @return the return slot.
     */
    FrameSlot getReturnSlot();

    Object getGlobalAddress(GlobalValueSymbol var);

    FrameSlot getStackPointerSlot();

    int getByteAlignment(Type type);

    int getByteSize(Type type);

    int getBytePadding(int offset, Type type);

    int getIndexOffset(int index, Type type);

    FrameDescriptor getGlobalFrameDescriptor();

    FrameDescriptor getMethodFrameDescriptor();

    /**
     * Adds a destructor node that is executed after returning from the main function.
     *
     * @param destructorNode a node that executes the destructor
     */
    void addDestructor(LLVMExpressionNode destructorNode);

    long getNativeHandle(String name);

    LLVMHeapFunctions getHeapFunctions();

    Map<String, Type> getVariableNameTypesMapping();

    NodeFactoryFacade getNodeFactoryFacade();

}
