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
package com.oracle.truffle.llvm.runtime.memory;

import com.oracle.truffle.api.nodes.NodeInterface;
import com.oracle.truffle.llvm.runtime.LLVMAddress;

public interface LLVMNativeFunctions {

    MemCopyNode createMemMoveNode();

    MemCopyNode createMemCopyNode();

    MemSetNode createMemSetNode();

    FreeNode createFreeNode();

    MallocNode createMallocNode();

    DynamicCastNode createDynamicCast();

    SulongCanCatchNode createSulongCanCatch();

    SulongThrowNode createSulongThrow();

    SulongGetUnwindHeaderNode createGetUnwindHeader();

    SulongGetThrownObjectNode createGetThrownObject();

    SulongGetExceptionPointerNode createGetExceptionPointer();

    SulongFreeExceptionNode createFreeException();

    SulongGetDestructorNode createGetDestructor();

    SulongIncrementHandlerCountNode createIncrementHandlerCount();

    SulongDecrementHandlerCountNode createDecrementHandlerCount();

    SulongGetHandlerCountNode createGetHandlerCount();

    SulongSetHandlerCountNode createSetHandlerCount();

    SulongGetExceptionTypeNode createGetExceptionType();

    public interface SulongIncrementHandlerCountNode extends NodeInterface {
        void inc(LLVMAddress ptr);
    }

    public interface SulongDecrementHandlerCountNode extends NodeInterface {
        void dec(LLVMAddress ptr);
    }

    public interface SulongGetHandlerCountNode extends NodeInterface {
        int get(LLVMAddress ptr);
    }

    public interface SulongSetHandlerCountNode extends NodeInterface {
        void set(LLVMAddress ptr, int value);
    }

    public interface SulongFreeExceptionNode extends NodeInterface {
        void free(LLVMAddress ptr);
    }

    public interface SulongGetDestructorNode extends NodeInterface {
        LLVMAddress get(LLVMAddress ptr);
    }

    public interface SulongGetExceptionTypeNode extends NodeInterface {
        LLVMAddress get(LLVMAddress ptr);
    }

    public interface SulongGetUnwindHeaderNode extends NodeInterface {
        LLVMAddress getUnwind(LLVMAddress ptr);
    }

    public interface SulongGetThrownObjectNode extends NodeInterface {
        LLVMAddress getThrownObject(LLVMAddress ptr);
    }

    public interface SulongGetExceptionPointerNode extends NodeInterface {
        LLVMAddress getExceptionPointer(LLVMAddress ptr);
    }

    public interface SulongCanCatchNode extends NodeInterface {
        // bool sulong_eh_canCatch(void *adjustedPtr, __shim_type_info *excpType, __shim_type_info
        // *catchType)
        int canCatch(LLVMAddress adjustedPtr, LLVMAddress excpType, LLVMAddress catchType);
    }

    public interface SulongThrowNode extends NodeInterface {
        // void sulong_eh_throw(void *ptr, std::type_info *type, void (*destructor)(void *), void
        // (*unexpectedHandler)(void *), void (*terminateHandler)(void *))
        void throvv(LLVMAddress ptr, LLVMAddress type, LLVMAddress destructor, LLVMAddress unexpectedHandler, LLVMAddress terminateHandler);
    }

    public interface DynamicCastNode extends NodeInterface {

        LLVMAddress execute(LLVMAddress object, LLVMAddress type1, LLVMAddress type2, long value);
    }

    public interface MemCopyNode extends NodeInterface {

        void execute(LLVMAddress target, LLVMAddress source, long length);
    }

    public interface MemSetNode extends NodeInterface {

        void execute(LLVMAddress target, int value, long length);
    }

    public interface FreeNode extends NodeInterface {

        void execute(LLVMAddress addr);
    }

    public interface MallocNode extends NodeInterface {

        LLVMAddress execute(long size);
    }

}
