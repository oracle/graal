/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.factories;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.InternalResource.CPUArchitecture;
import com.oracle.truffle.api.InternalResource.OS;
import com.oracle.truffle.llvm.parser.factories.inlineasm.UnknownInlineAssemblyParser;
import com.oracle.truffle.llvm.runtime.LLVMSyscallEntry;
import com.oracle.truffle.llvm.runtime.memory.LLVMSyscallOperationNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.syscall.LLVMUnsupportedSyscallNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVAListNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListStorage.VAListPointerWrapperFactory;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.Type;

/**
 * Fallback implementation for unknown platforms.
 */
final class UnknownBasicPlatformCapability extends BasicPlatformCapability<UnknownBasicPlatformCapability.UnknownSyscalls> {

    /**
     * We don't know anything about this platform.
     */
    enum UnknownSyscalls implements LLVMSyscallEntry {
        /* DUMMY */;
        @Override
        public int value() {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    UnknownBasicPlatformCapability(boolean loadCxxLibraries) {
        super(UnknownSyscalls.class, loadCxxLibraries, new UnknownInlineAssemblyParser());
    }

    @Override
    public LLVMSyscallOperationNode createSyscallNode(long index) {
        return LLVMUnsupportedSyscallNode.create(index);
    }

    @Override
    protected LLVMSyscallOperationNode createSyscallNode(UnknownSyscalls syscall) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public Object createVAListStorage(LLVMVAListNode allocaNode, LLVMPointer vaListStackPtr, Type vaListType) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public Type getGlobalVAListType(Type type) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public VAListPointerWrapperFactory createNativeVAListWrapper(boolean cached) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public OS getOS() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public CPUArchitecture getArch() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public int getDoubleLongSize() {
        throw CompilerDirectives.shouldNotReachHere();
    }
}
