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
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMLanguage;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMGlobalRootNode;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor.LLVMRuntimeType;
import com.oracle.truffle.llvm.types.memory.LLVMHeap;
import com.oracle.truffle.llvm.types.memory.LLVMMemory;

public class LLVMRootNodeFactory {

    public static LLVMGlobalRootNode createGlobalRootNode(LLVMParserRuntime runtime, LLVMNode[] staticInits, RootCallTarget mainCallTarget, LLVMAddress[] allocatedGlobalAddresses, Object[] args,
                    Source sourceFile, LLVMRuntimeType[] mainTypes) {
        return new LLVMGlobalRootNode(runtime.getStackPointerSlot(), runtime.getGlobalFrameDescriptor(), LLVMLanguage.INSTANCE.findContext0(LLVMLanguage.INSTANCE.createFindContextNode0()),
                        staticInits,
                        mainCallTarget, allocatedGlobalAddresses,
                        createArgs(sourceFile, args, mainTypes));
    }

    private static Object[] createArgs(Source sourceFile, Object[] mainArgs, LLVMRuntimeType[] llvmRuntimeTypes) {
        int argsCount;
        if (mainArgs == null) {
            argsCount = 1;
        } else {
            argsCount = mainArgs.length + 1;
        }
        if (llvmRuntimeTypes.length == 0) {
            return new Object[0];
        } else if (llvmRuntimeTypes.length == 1) {
            return new Object[]{argsCount};
        } else {
            Object[] args = new Object[mainArgs.length + 1];
            args[0] = sourceFile;
            System.arraycopy(mainArgs, 0, args, 1, mainArgs.length);
            LLVMAddress allocatedArgsStartAddress = getArgsAsStringArray(args);
            // Checkstyle: stop magic number check
            if (llvmRuntimeTypes.length == 2) {
                return new Object[]{argsCount, allocatedArgsStartAddress};
            } else if (llvmRuntimeTypes.length == 3) {
                LLVMAddress posixEnvPointer = LLVMAddress.NULL_POINTER;
                return new Object[]{argsCount, allocatedArgsStartAddress, posixEnvPointer};
            } else {
                throw new AssertionError(sourceFile + " " + Arrays.toString(mainArgs) + " " + Arrays.toString(llvmRuntimeTypes));
            }
            // Checkstyle: resume magic number check
        }
    }

    private static LLVMAddress getArgsAsStringArray(Object... args) {
        String[] stringArgs = getStringArgs(args);
        int argsMemory = stringArgs.length * LLVMAddress.WORD_LENGTH_BIT / Byte.SIZE;
        LLVMAddress allocatedArgsStartAddress = LLVMHeap.allocateMemory(argsMemory);
        LLVMAddress allocatedArgs = allocatedArgsStartAddress;
        for (int i = 0; i < stringArgs.length; i++) {
            String string = stringArgs[i];
            LLVMAddress allocatedCString = LLVMHeap.allocateCString(string);
            LLVMMemory.putAddress(allocatedArgs, allocatedCString);
            allocatedArgs = allocatedArgs.increment(LLVMAddress.WORD_LENGTH_BIT / Byte.SIZE);
        }
        return allocatedArgsStartAddress;
    }

    private static String[] getStringArgs(Object... args) {
        String[] stringArgs = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            stringArgs[i] = args[i].toString();
        }
        return stringArgs;
    }

}
