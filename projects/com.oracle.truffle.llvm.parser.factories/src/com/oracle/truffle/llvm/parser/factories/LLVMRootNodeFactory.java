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
        int mainArgsCount = mainArgs == null ? 0 : mainArgs.length;
        int argsCount = mainArgsCount + 1;
        if (llvmRuntimeTypes.length == 0) {
            return new Object[0];
        } else if (llvmRuntimeTypes.length == 1) {
            return new Object[]{argsCount};
        } else {
            Object[] args = new Object[argsCount];
            args[0] = sourceFile.getPath() == null ? "" : sourceFile.getPath();
            if (mainArgsCount > 0) {
                System.arraycopy(mainArgs, 0, args, 1, mainArgsCount);
            }
            LLVMAddress allocatedArgsStartAddress = getArgsAsStringArray(args);
            // Checkstyle: stop magic number check
            if (llvmRuntimeTypes.length == 2) {
                return new Object[]{argsCount, allocatedArgsStartAddress};
            } else if (llvmRuntimeTypes.length == 3) {
                LLVMAddress posixEnvPointer = LLVMAddress.nullPointer();
                return new Object[]{argsCount, allocatedArgsStartAddress, posixEnvPointer};
            } else {
                throw new AssertionError(sourceFile + " " + Arrays.toString(mainArgs) + " " + Arrays.toString(llvmRuntimeTypes));
            }
            // Checkstyle: resume magic number check
        }
    }

    private static LLVMAddress getArgsAsStringArray(Object[] args) {
        String[] stringArgs = getStringArgs(args);
        int argsMemory = stringArgs.length * LLVMAddress.WORD_LENGTH_BIT / Byte.SIZE;
        LLVMAddress allocatedArgsStartAddress = LLVMMemory.allocateMemory(argsMemory);
        long allocatedArgsPtr = allocatedArgsStartAddress.getVal();
        for (int i = 0; i < stringArgs.length; i++) {
            String string = stringArgs[i];
            LLVMAddress allocatedCString = LLVMHeap.allocateCString(string);
            LLVMMemory.putAddress(allocatedArgsPtr, allocatedCString);
            allocatedArgsPtr += LLVMAddress.WORD_LENGTH_BIT / Byte.SIZE;
        }
        return allocatedArgsStartAddress;
    }

    private static String[] getStringArgs(Object[] args) {
        String[] stringArgs = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            stringArgs[i] = args[i].toString();
        }
        return stringArgs;
    }

}
