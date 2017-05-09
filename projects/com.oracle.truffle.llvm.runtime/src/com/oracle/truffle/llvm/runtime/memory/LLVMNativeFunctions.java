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

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMAddress;

public abstract class LLVMNativeFunctions {

    public abstract NullPointerNode createNullPointerNode();

    public abstract MemCopyNode createMemMoveNode();

    public abstract MemCopyNode createMemCopyNode();

    public abstract DynamicCastNode createDynamicCast();

    public abstract SulongCanCatchNode createSulongCanCatch();

    public abstract SulongThrowNode createSulongThrow();

    public abstract SulongGetUnwindHeaderNode createGetUnwindHeader();

    public abstract SulongGetThrownObjectNode createGetThrownObject();

    public abstract SulongGetExceptionPointerNode createGetExceptionPointer();

    public abstract SulongFreeExceptionNode createFreeException();

    public abstract SulongGetDestructorNode createGetDestructor();

    public abstract SulongIncrementHandlerCountNode createIncrementHandlerCount();

    public abstract SulongDecrementHandlerCountNode createDecrementHandlerCount();

    public abstract SulongGetHandlerCountNode createGetHandlerCount();

    public abstract SulongSetHandlerCountNode createSetHandlerCount();

    public abstract SulongGetExceptionTypeNode createGetExceptionType();

    protected static class HeapFunctionNode extends Node {

        private final TruffleObject function;
        @Child private Node nativeExecute;

        protected HeapFunctionNode(TruffleObject function, int argCount) {
            this.function = function;
            this.nativeExecute = Message.createExecute(argCount).createNode();
        }

        protected Object execute(Object... args) {
            try {
                return ForeignAccess.sendExecute(nativeExecute, function, args);
            } catch (InteropException e) {
                throw new AssertionError(e);
            }
        }
    }

    public abstract static class SulongIncrementHandlerCountNode extends HeapFunctionNode {
        protected SulongIncrementHandlerCountNode(TruffleObject function, int argCount) {
            super(function, argCount);
        }

        public abstract void inc(LLVMAddress ptr);
    }

    public abstract static class SulongDecrementHandlerCountNode extends HeapFunctionNode {
        protected SulongDecrementHandlerCountNode(TruffleObject function, int argCount) {
            super(function, argCount);
        }

        public abstract void dec(LLVMAddress ptr);
    }

    public abstract static class SulongGetHandlerCountNode extends HeapFunctionNode {
        protected SulongGetHandlerCountNode(TruffleObject function, int argCount) {
            super(function, argCount);
        }

        public abstract int get(LLVMAddress ptr);
    }

    public abstract static class SulongSetHandlerCountNode extends HeapFunctionNode {
        protected SulongSetHandlerCountNode(TruffleObject function, int argCount) {
            super(function, argCount);
        }

        public abstract void set(LLVMAddress ptr, int value);
    }

    public abstract static class SulongFreeExceptionNode extends HeapFunctionNode {
        protected SulongFreeExceptionNode(TruffleObject function, int argCount) {
            super(function, argCount);
        }

        public abstract void free(LLVMAddress ptr);
    }

    public abstract static class SulongGetDestructorNode extends HeapFunctionNode {
        protected SulongGetDestructorNode(TruffleObject function, int argCount) {
            super(function, argCount);
        }

        public abstract LLVMAddress get(LLVMAddress ptr);
    }

    public abstract static class SulongGetExceptionTypeNode extends HeapFunctionNode {
        protected SulongGetExceptionTypeNode(TruffleObject function, int argCount) {
            super(function, argCount);
        }

        public abstract LLVMAddress get(LLVMAddress ptr);
    }

    public abstract static class SulongGetUnwindHeaderNode extends HeapFunctionNode {
        protected SulongGetUnwindHeaderNode(TruffleObject function, int argCount) {
            super(function, argCount);
        }

        public abstract LLVMAddress getUnwind(LLVMAddress ptr);
    }

    public abstract static class SulongGetThrownObjectNode extends HeapFunctionNode {
        protected SulongGetThrownObjectNode(TruffleObject function, int argCount) {
            super(function, argCount);
        }

        public abstract LLVMAddress getThrownObject(LLVMAddress ptr);
    }

    public abstract static class SulongGetExceptionPointerNode extends HeapFunctionNode {
        protected SulongGetExceptionPointerNode(TruffleObject function, int argCount) {
            super(function, argCount);
        }

        public abstract LLVMAddress getExceptionPointer(LLVMAddress ptr);
    }

    public abstract static class SulongCanCatchNode extends HeapFunctionNode {
        protected SulongCanCatchNode(TruffleObject function, int argCount) {
            super(function, argCount);
        }

        // bool sulong_eh_canCatch(void *adjustedPtr, __shim_type_info *excpType, __shim_type_info
        // *catchType)
        public abstract int canCatch(LLVMAddress adjustedPtr, LLVMAddress excpType, LLVMAddress catchType);
    }

    public abstract static class SulongThrowNode extends HeapFunctionNode {
        protected SulongThrowNode(TruffleObject function, int argCount) {
            super(function, argCount);
        }

        // void sulong_eh_throw(void *ptr, std::type_info *type, void (*destructor)(void *), void
        // (*unexpectedHandler)(void *), void (*terminateHandler)(void *))
        public abstract void throvv(LLVMAddress ptr, LLVMAddress type, LLVMAddress destructor, LLVMAddress unexpectedHandler, LLVMAddress terminateHandler);
    }

    public abstract static class DynamicCastNode extends HeapFunctionNode {

        protected DynamicCastNode(TruffleObject function, int argCount) {
            super(function, argCount);
        }

        public abstract LLVMAddress execute(LLVMAddress object, LLVMAddress type1, LLVMAddress type2, long value);
    }

    public abstract static class MemCopyNode extends HeapFunctionNode {

        protected MemCopyNode(TruffleObject function, int argCount) {
            super(function, argCount);
        }

        public abstract void execute(LLVMAddress target, LLVMAddress source, long length);
    }

    public abstract static class NullPointerNode extends HeapFunctionNode {

        protected NullPointerNode(TruffleObject function, int argCount) {
            super(function, argCount);
        }

        public abstract TruffleObject getNullPointer();
    }

}
