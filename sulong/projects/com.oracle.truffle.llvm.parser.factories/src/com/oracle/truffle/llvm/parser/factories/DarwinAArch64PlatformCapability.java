/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates.
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
import com.oracle.truffle.llvm.runtime.nodes.asm.syscall.LLVMNativeSyscallNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.syscall.LLVMSyscallExitNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.syscall.darwin.DarwinSyscall;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.aarch64.darwin.LLVMDarwinAarch64VaListStorage;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.aarch64.darwin.LLVMDarwinAarch64VaListStorageFactory.Aarch64VAListPointerWrapperFactoryNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVAListNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListStorage;
import com.oracle.truffle.llvm.runtime.pointer.LLVMMaybeVaPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.Type;

final class DarwinAArch64PlatformCapability extends BasicAarch64PlatformCapability<DarwinSyscall> {

    public static final int RTLD_GLOBAL_DARWIN = 8;
    public static final int RTLD_FIRST_DARWIN = 100;
    public static final long RTLD_DEFAULT_DARWIN = -2;

    DarwinAArch64PlatformCapability(boolean loadCxxLibraries) {
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
            case SYS_exit:
                return new LLVMSyscallExitNode();
            default:
                return new LLVMNativeSyscallNode(syscall);
        }
    }

    @Override
    public Object createVAListStorage(LLVMVAListNode allocaNode, LLVMPointer vaListStackPtr, Type vaListType) {
        return LLVMMaybeVaPointer.createWithAlloca(vaListStackPtr, allocaNode);
    }

    @Override
    public Object createActualVAListStorage() {
        return new LLVMDarwinAarch64VaListStorage();
    }

    @Override
    public Type getGlobalVAListType(Type type) {
        return LLVMDarwinAarch64VaListStorage.VA_LIST_TYPE.equals(type) ? LLVMDarwinAarch64VaListStorage.VA_LIST_TYPE : null;
    }

    @Override
    public LLVMVaListStorage.VAListPointerWrapperFactory createNativeVAListWrapper(boolean cached) {
        return cached ? Aarch64VAListPointerWrapperFactoryNodeGen.create() : Aarch64VAListPointerWrapperFactoryNodeGen.getUncached();
    }

    @Override
    public OS getOS() {
        return OS.DARWIN;
    }

    @Override
    public int getDoubleLongSize() {
        return 128;
    }

}
