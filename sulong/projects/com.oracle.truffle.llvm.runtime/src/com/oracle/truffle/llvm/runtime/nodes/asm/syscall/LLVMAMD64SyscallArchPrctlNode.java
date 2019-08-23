/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.asm.syscall;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.IntValueProfile;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMPointerStoreNode;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.memory.LLVMSyscallOperationNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMPointerStoreNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public abstract class LLVMAMD64SyscallArchPrctlNode extends LLVMSyscallOperationNode {
    private final IntValueProfile profile = IntValueProfile.createIdentityProfile();

    @Override
    public final String getName() {
        return "arch_prctl";
    }

    @Specialization
    protected long doOp(long code, long addr,
                    @CachedContext(LLVMLanguage.class) LLVMContext context,
                    @Cached("createAddressStoreNode()") LLVMPointerStoreNode store) {
        return exec(code, addr, context, store);
    }

    @Specialization
    protected long doOp(long code, LLVMPointer addr,
                    @CachedContext(LLVMLanguage.class) LLVMContext context,
                    @Cached("createAddressStoreNode()") LLVMPointerStoreNode store) {
        return exec(code, addr, context, store);
    }

    protected LLVMPointerStoreNode createAddressStoreNode() {
        CompilerAsserts.neverPartOfCompilation();
        return LLVMPointerStoreNodeGen.create(null, null);
    }

    private long exec(long code, Object addr, LLVMContext context, LLVMPointerStoreNode store) throws AssertionError {
        switch (profile.profile((int) code)) {
            case LLVMAMD64ArchPrctl.ARCH_SET_FS:
                context.setThreadLocalStorage(addr);
                break;
            case LLVMAMD64ArchPrctl.ARCH_GET_FS: {
                Object tls = context.getThreadLocalStorage();
                store.executeWithTarget(addr, tls);
                break;
            }
            default:
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(String.format("not implemented: arch_prcntl(0x%04x)", code));
        }
        return 0;
    }
}
