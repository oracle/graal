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
package com.oracle.truffle.llvm.context;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.memory.LLVMHeapFunctions;

final class LLVMHeapFunctionsImpl implements LLVMHeapFunctions {

    private final TruffleObject memmove;
    private final TruffleObject memcpy;
    private final TruffleObject memset;
    private final TruffleObject free;
    private final TruffleObject malloc;

    LLVMHeapFunctionsImpl(NativeLookup nativeLookup) {
        memmove = nativeLookup.getNativeFunction("@memmove", "(POINTER,POINTER,UINT64):POINTER");
        memcpy = nativeLookup.getNativeFunction("@memcpy", "(POINTER,POINTER,UINT64):POINTER");
        memset = nativeLookup.getNativeFunction("@memset", "(POINTER,SINT32,UINT64):VOID");
        free = nativeLookup.getNativeFunction("@free", "(POINTER):VOID");
        malloc = nativeLookup.getNativeFunction("@malloc", "(UINT64):POINTER");
    }

    @Override
    public MemCopyNode createMemMoveNode() {
        return new MemCopyNodeImpl(memmove);
    }

    @Override
    public MemCopyNode createMemCopyNode() {
        return new MemCopyNodeImpl(memcpy);
    }

    @Override
    public MemSetNode createMemSetNode() {
        return new MemSetNodeImpl(memset);
    }

    @Override
    public FreeNode createFreeNode() {
        return new FreeNodeImpl(free);
    }

    @Override
    public MallocNode createMallocNode() {
        return new MallocNodeImpl(malloc);
    }

    private static class HeapFunctionNode extends Node {

        private final TruffleObject function;
        @Child private Node nativeExecute;

        HeapFunctionNode(TruffleObject function, int argCount) {
            this.function = function;
            this.nativeExecute = Message.createExecute(argCount).createNode();
        }

        Object execute(Object... args) {
            try {
                return ForeignAccess.sendExecute(nativeExecute, function, args);
            } catch (InteropException e) {
                throw new AssertionError(e);
            }
        }
    }

    private static class MemCopyNodeImpl extends HeapFunctionNode implements MemCopyNode {

        MemCopyNodeImpl(TruffleObject function) {
            super(function, 3);
        }

        @Override
        public void execute(LLVMAddress target, LLVMAddress source, long length) {
            execute(target.getVal(), source.getVal(), length);
        }
    }

    private static class MemSetNodeImpl extends HeapFunctionNode implements MemSetNode {

        MemSetNodeImpl(TruffleObject function) {
            super(function, 2);
        }

        @Override
        public void execute(LLVMAddress target, int value, long length) {
            execute(target.getVal(), value, length);
        }
    }

    private static class FreeNodeImpl extends HeapFunctionNode implements FreeNode {

        FreeNodeImpl(TruffleObject function) {
            super(function, 1);
        }

        @Override
        public void execute(LLVMAddress addr) {
            execute(addr.getVal());
        }
    }

    private static class MallocNodeImpl extends HeapFunctionNode implements MallocNode {

        MallocNodeImpl(TruffleObject function) {
            super(function, 1);
        }

        @Child private Node unbox = Message.UNBOX.createNode();

        @Override
        public LLVMAddress execute(long size) {
            try {
                return LLVMAddress.fromLong((long) ForeignAccess.sendUnbox(unbox, (TruffleObject) execute((Object) size)));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }
}
