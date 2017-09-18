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
package com.oracle.truffle.llvm.parser.factories;

import java.util.Arrays;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.nodes.func.LLVMGlobalRootNode;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.memory.LLVMHeap;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType.PrimitiveKind;
import com.oracle.truffle.llvm.runtime.types.Type;

class LLVMRootNodeFactory {

    static LLVMGlobalRootNode createGlobalRootNode(
                    LLVMParserRuntime runtime,
                    RootCallTarget mainCallTarget,
                    Object[] args,
                    Source sourceFile,
                    Type[] mainTypes) {
        return createGlobalRootNode(
                        runtime.getLanguage(),
                        runtime.getGlobalFrameDescriptor(),
                        mainCallTarget,
                        args,
                        sourceFile,
                        mainTypes);
    }

    private static LLVMGlobalRootNode createGlobalRootNode(
                    LLVMLanguage language,
                    FrameDescriptor frame,
                    RootCallTarget mainCallTarget,
                    Object[] args,
                    Source sourceFile,
                    Type[] mainTypes) {
        Object[] arguments = createArgs(sourceFile, args, mainTypes);
        return new LLVMGlobalRootNode(language, frame, mainCallTarget, arguments);
    }

    private static Object[] createArgs(Source sourceFile, Object[] mainArgs, Type[] llvmRuntimeTypes) {
        if (llvmRuntimeTypes.length > 3) {
            throw new AssertionError(sourceFile + " " + Arrays.toString(llvmRuntimeTypes));
        }
        int mainArgsCount = mainArgs == null ? 0 : mainArgs.length;
        int argsCount = mainArgsCount + 1;
        Object[] args = new Object[argsCount];
        args[0] = sourceFile.getPath() == null ? "" : sourceFile.getPath();
        if (mainArgsCount > 0) {
            System.arraycopy(mainArgs, 0, args, 1, mainArgsCount);
        }
        int type = 0;
        // Rust extra handling: main(i64,...)
        if (llvmRuntimeTypes.length > 0 && llvmRuntimeTypes[0] instanceof PrimitiveType) {
            if (((PrimitiveType) llvmRuntimeTypes[0]).getPrimitiveKind() == PrimitiveKind.I64) {
                type = 1;
            }
        }
        return new Object[]{getArgs(args), type};
    }

    private static String[] getStringArgs(Object[] args) {
        String[] stringArgs = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            stringArgs[i] = args[i].toString();
        }
        return stringArgs;
    }

    private static String[] getenv() {
        return System.getenv().entrySet().stream().map((e) -> e.getKey() + "=" + e.getValue()).toArray(String[]::new);
    }

    private static LLVMAddress putCString(LLVMAddress address, String string) {
        LLVMAddress ptr = address;
        for (byte b : string.getBytes()) { // automatic charset conversion
            LLVMMemory.putI8(ptr, b);
            ptr = ptr.increment(1);
        }
        LLVMMemory.putI8(ptr, (byte) 0);
        return ptr.increment(1);
    }

    private static LLVMAddress getArgs(Object[] mainArgs) {
        int ptrsz = LLVMExpressionNode.ADDRESS_SIZE_IN_BYTES;
        String[] args = getStringArgs(mainArgs);
        String[] env = getenv();

        int offset = (args.length + env.length + 5) * ptrsz; // 3 = argc (long), two NULL pointers
                                                             // and AT_NULL
        int size = offset;

        for (String arg : args) {
            size += arg.length() + 1;
        }
        for (String var : env) {
            size += var.length() + 1;
        }

        LLVMAddress memory = LLVMHeap.allocateMemory(size);
        LLVMAddress ptr = memory;
        LLVMAddress valuePtr = memory.increment(offset);

        // argc
        LLVMMemory.putI64(ptr, args.length);
        ptr = ptr.increment(LLVMExpressionNode.I64_SIZE_IN_BYTES);

        // argv
        for (String arg : args) {
            LLVMMemory.putAddress(ptr, valuePtr);
            ptr = ptr.increment(ptrsz);
            valuePtr = putCString(valuePtr, arg);
        }
        LLVMMemory.putAddress(ptr, LLVMAddress.nullPointer());

        // env
        ptr = ptr.increment(ptrsz);
        for (String var : env) {
            LLVMMemory.putAddress(ptr, valuePtr);
            ptr = ptr.increment(ptrsz);
            valuePtr = putCString(valuePtr, var);
        }
        LLVMMemory.putAddress(ptr, LLVMAddress.nullPointer());
        ptr = ptr.increment(ptrsz);

        // AT_NULL = 0
        LLVMMemory.putI64(ptr, 0);
        ptr = ptr.increment(LLVMExpressionNode.I64_SIZE_IN_BYTES);
        LLVMMemory.putI64(ptr, 0);
        ptr = ptr.increment(LLVMExpressionNode.I64_SIZE_IN_BYTES);

        assert ptr.getVal() - memory.getVal() == offset;
        assert valuePtr.getVal() - memory.getVal() == size;
        assert memory.increment(offset).getVal() - ptr.getVal() == 0;
        return memory;
    }
}
