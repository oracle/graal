/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.InternalResource.OS;
import com.oracle.truffle.llvm.runtime.memory.LLVMSyscallOperationNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.syscall.LLVMAMD64SyscallMmapNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.syscall.LLVMNativeSyscallNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.syscall.LLVMSyscallExitNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.syscall.darwin.DarwinSyscall;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVAListNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListStorage.VAListPointerWrapperFactory;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_64VaListStorage;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_64VaListStorageFactory.X86_64VAListPointerWrapperFactoryNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.Type;

final class DarwinAMD64PlatformCapability extends BasicAMD64PlatformCapability<DarwinSyscall> {

    public static final int RTLD_GLOBAL_DARWIN = 8;
    public static final int RTLD_FIRST_DARWIN = 100;
    public static final long RTLD_DEFAULT_DARWIN = -2;

    DarwinAMD64PlatformCapability(boolean loadCxxLibraries) {
        super(DarwinSyscall.class, loadCxxLibraries);
    }

    @Override
    public boolean isGlobalDLOpenFlagSet(int flag) {
        return (flag & RTLD_GLOBAL_DARWIN) == RTLD_GLOBAL_DARWIN;
    }

    @Override
    public boolean isFirstDLOpenFlagSet(int flag) {
        return (flag & RTLD_FIRST_DARWIN) == RTLD_FIRST_DARWIN;
    }

    @Override
    public boolean isDefaultDLSymFlagSet(long flag) {
        return flag == RTLD_DEFAULT_DARWIN;
    }

    @Override
    protected LLVMSyscallOperationNode createSyscallNode(DarwinSyscall syscall) {
        switch (syscall) {
            case SYS_mmap:
                return LLVMAMD64SyscallMmapNodeGen.create();
            case SYS_exit:
                return new LLVMSyscallExitNode();
            default:
                return new LLVMNativeSyscallNode(syscall);
        }
    }

    @Override
    public Object createVAListStorage(LLVMVAListNode allocaNode, LLVMPointer vaListStackPtr, Type vaListType) {
        return new LLVMX86_64VaListStorage(vaListStackPtr, vaListType);
    }

    @Override
    public Type getGlobalVAListType(Type type) {
        return LLVMX86_64VaListStorage.VA_LIST_TYPE.equals(type) ? LLVMX86_64VaListStorage.VA_LIST_TYPE : null;
    }

    @Override
    public VAListPointerWrapperFactory createNativeVAListWrapper(boolean cached) {
        return cached ? X86_64VAListPointerWrapperFactoryNodeGen.create() : X86_64VAListPointerWrapperFactoryNodeGen.getUncached();
    }

    @Override
    public OS getOS() {
        return OS.DARWIN;
    }

    @Override
    public int getDoubleLongSize() {
        return 80;
    }
}
