/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.c;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.syscall.LLVMSyscallNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMSyscallFactory.SyscallArgConverterNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMI64LiteralNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public abstract class LLVMSyscall {

    private LLVMSyscall() {
        // private constructor
    }

    public static LLVMExpressionNode create(LLVMExpressionNode[] arguments) {
        assert arguments.length >= 1 && arguments.length <= 7;
        LLVMExpressionNode[] args = new LLVMExpressionNode[7];
        for (int i = 0; i < arguments.length; i++) {
            args[i] = SyscallArgConverterNodeGen.create(arguments[i]);
        }
        for (int i = arguments.length; i < args.length; i++) {
            args[i] = LLVMI64LiteralNodeGen.create(0L);
        }
        return LLVMSyscallNodeGen.create(args[0], args[1], args[2], args[3], args[4], args[5], args[6]);

    }

    @NodeChild(value = "value", type = LLVMExpressionNode.class)
    abstract static class SyscallArgConverter extends LLVMExpressionNode {

        @Specialization
        protected static long convert(long value) {
            return value;
        }

        @Specialization
        protected static long convert(int value) {
            return value;
        }

        @Specialization
        protected static long convert(short value) {
            return value;
        }

        @Specialization
        protected static long convert(byte value) {
            return value;
        }

        @Specialization
        protected static LLVMPointer convert(LLVMPointer value) {
            return value;
        }

        @Fallback
        protected static Object convert(Object value) {
            return value;
        }
    }
}
