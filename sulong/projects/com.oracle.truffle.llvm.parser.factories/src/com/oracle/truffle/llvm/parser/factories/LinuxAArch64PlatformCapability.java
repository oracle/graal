/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
 * Copyright (c) 2020, Arm Limited.
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

import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMSyscallOperationNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.syscall.LLVMSyscallExitNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.syscall.LLVMNativeSyscallNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.syscall.linux.aarch64.LinuxAArch64Syscall;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_64VaListStorage;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.types.Type;

final class LinuxAArch64PlatformCapability extends BasicPlatformCapability<LinuxAArch64Syscall> {

    LinuxAArch64PlatformCapability(boolean loadCxxLibraries) {
        super(LinuxAArch64Syscall.class, loadCxxLibraries);
    }

    @Override
    protected LLVMSyscallOperationNode createSyscallNode(LinuxAArch64Syscall syscall) {
        switch (syscall) {
            case SYS_exit:
            case SYS_exit_group: // TODO: implement difference to SYS_exit
                return new LLVMSyscallExitNode();
            default:
                return new LLVMNativeSyscallNode(syscall);
        }
    }

    // TODO: The following methods temporarily return X86 va_list objects until the AArch64 managed
    // va_list is implemented.

    @Override
    public Object createVAListStorage(RootNode rootNode) {
        return new LLVMX86_64VaListStorage(rootNode);
    }

    @Override
    public Type getVAListType() {
        return LLVMX86_64VaListStorage.VA_LIST_TYPE;
    }

    @Override
    public Object createNativeVAListWrapper(LLVMNativePointer vaListPtr, RootNode rootNode) {
        return new LLVMX86_64VaListStorage.NativeVAListWrapper(vaListPtr, rootNode);
    }

}
