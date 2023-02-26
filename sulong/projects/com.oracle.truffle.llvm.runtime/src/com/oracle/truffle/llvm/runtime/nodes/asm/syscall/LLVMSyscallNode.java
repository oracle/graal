/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.PlatformCapability;
import com.oracle.truffle.llvm.runtime.memory.LLVMSyscallOperationNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeChild("syscallNum")
@NodeChild("arg1")
@NodeChild("arg2")
@NodeChild("arg3")
@NodeChild("arg4")
@NodeChild("arg5")
@NodeChild("arg6")
public abstract class LLVMSyscallNode extends LLVMExpressionNode {
    protected static final int NUM_SYSCALLS = 332;

    protected LLVMSyscallOperationNode createNode(long syscallNum) {
        return LLVMLanguage.get(null).getCapability(PlatformCapability.class).createSyscallNode(syscallNum);
    }

    @Specialization(guards = "syscallNum == cachedSyscallNum", limit = "NUM_SYSCALLS", rewriteOn = UnexpectedResultException.class)
    protected long cachedSyscall(@SuppressWarnings("unused") long syscallNum, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6,
                    @Cached("syscallNum") @SuppressWarnings("unused") long cachedSyscallNum,
                    @Cached("createNode(syscallNum)") LLVMSyscallOperationNode node) throws UnexpectedResultException {
        if (LLVMContext.logSysCallsEnabled()) {
            trace("[sulong] syscall: %s (%s, %s, %s, %s, %s, %s)\n", getNodeName(node), arg1, arg2, arg3, arg4, arg5, arg6);
        }

        try {
            long result = node.executeLong(arg1, arg2, arg3, arg4, arg5, arg6);
            if (LLVMContext.logSysCallsEnabled()) {
                trace("         result: %d\n", result);
            }
            return result;
        } catch (UnexpectedResultException ex) {
            Object result = ex.getResult();
            if (LLVMContext.logSysCallsEnabled()) {
                trace("         result: %s\n", result);
            }
            throw ex;
        }
    }

    @Specialization(guards = "syscallNum == cachedSyscallNum", limit = "NUM_SYSCALLS", replaces = "cachedSyscall")
    protected Object cachedSyscallGeneric(@SuppressWarnings("unused") long syscallNum, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6,
                    @Cached("syscallNum") @SuppressWarnings("unused") long cachedSyscallNum,
                    @Cached("createNode(syscallNum)") LLVMSyscallOperationNode node) {
        if (LLVMContext.logSysCallsEnabled()) {
            trace("[sulong] syscall: %s (%s, %s, %s, %s, %s, %s)\n", getNodeName(node), arg1, arg2, arg3, arg4, arg5, arg6);
        }
        Object result = node.executeGeneric(arg1, arg2, arg3, arg4, arg5, arg6);
        if (LLVMContext.logSysCallsEnabled()) {
            trace("         result: %s\n", result);
        }
        return result;
    }

    @TruffleBoundary
    private static String getNodeName(LLVMSyscallOperationNode node) {
        return node.getName();
    }

    @TruffleBoundary
    @Specialization(replaces = "cachedSyscallGeneric")
    protected Object doGeneric(long syscallNum, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6) {
        // TODO: implement big switch with type casts + logic + ...?
        return createNode(syscallNum).executeGeneric(arg1, arg2, arg3, arg4, arg5, arg6);
    }

    @TruffleBoundary
    private static void trace(String format, Object... args) {
        LLVMContext.logSysCall(String.format(format, args));
    }
}
