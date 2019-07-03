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
package com.oracle.truffle.llvm.nodes.asm.syscall;

import java.io.PrintStream;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.SystemContextExtension;
import com.oracle.truffle.llvm.runtime.memory.LLVMSyscallOperationNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;

@NodeChild("rax")
@NodeChild("rdi")
@NodeChild("rsi")
@NodeChild("rdx")
@NodeChild("r10")
@NodeChild("r8")
@NodeChild("r9")
public abstract class LLVMAMD64SyscallNode extends LLVMExpressionNode {
    protected static final int NUM_SYSCALLS = 332;

    protected LLVMSyscallOperationNode createNode(long rax) {
        return LLVMLanguage.getLanguage().getContextExtension(SystemContextExtension.class).createSyscallNode(rax);
    }

    @Specialization(guards = "rax == cachedRax", limit = "NUM_SYSCALLS")
    protected long cachedSyscall(@SuppressWarnings("unused") long rax, Object rdi, Object rsi, Object rdx, Object r10, Object r8, Object r9,
                    @Cached("rax") @SuppressWarnings("unused") long cachedRax,
                    @Cached("createNode(rax)") LLVMSyscallOperationNode node) {
        if (traceEnabled()) {
            trace("[sulong] syscall: %s (%s, %s, %s, %s, %s, %s)\n", getNodeName(node), rdi, rsi, rdx, r10, r8, r9);
        }
        long result = node.execute(rdi, rsi, rdx, r10, r8, r9);
        if (traceEnabled()) {
            trace("         result: %d\n", result);
        }
        return result;
    }

    @TruffleBoundary
    private static String getNodeName(LLVMSyscallOperationNode node) {
        return node.getName();
    }

    @Specialization(replaces = "cachedSyscall")
    protected long doI64(long rax, Object rdi, Object rsi, Object rdx, Object r10, Object r8, Object r9) {
        // TODO: implement big switch with type casts + logic + ...?
        CompilerDirectives.transferToInterpreter();
        return createNode(rax).execute(rdi, rsi, rdx, r10, r8, r9);
    }

    @CompilationFinal private boolean traceEnabledFlag;
    @CompilationFinal private PrintStream traceStream;

    private void cacheTrace() {
        if (traceStream == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            traceStream = SulongEngineOption.getStream(lookupContextReference(LLVMLanguage.class).get().getEnv().getOptions().get(SulongEngineOption.DEBUG_SYSCALLS));
            traceEnabledFlag = SulongEngineOption.isTrue(lookupContextReference(LLVMLanguage.class).get().getEnv().getOptions().get(SulongEngineOption.DEBUG_SYSCALLS));
        }
    }

    private boolean traceEnabled() {
        cacheTrace();
        return traceEnabledFlag;
    }

    private PrintStream traceStream() {
        cacheTrace();
        return traceStream;
    }

    @TruffleBoundary
    private void trace(String format, Object... args) {
        traceStream().printf(format, args);
    }
}
