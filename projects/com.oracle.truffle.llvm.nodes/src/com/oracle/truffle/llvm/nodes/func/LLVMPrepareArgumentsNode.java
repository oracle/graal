/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.func;

import java.util.Arrays;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMMemoryIntrinsic.LLVMMalloc;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMMemoryIntrinsicFactory.LLVMMallocNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMAddressGetElementPtrNode.LLVMIncrementPointerNode;
import com.oracle.truffle.llvm.nodes.memory.LLVMAddressGetElementPtrNodeGen.LLVMIncrementPointerNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMAddressStoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMI64StoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMI8StoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType.PrimitiveKind;
import com.oracle.truffle.llvm.runtime.types.Type;

public class LLVMPrepareArgumentsNode extends LLVMNode {
    private static final long AT_NULL = 0;
    private static final long AT_PLATFORM = 15;
    private static final long AT_EXECFN = 31;

    private static final String PLATFORM = "x86_64";

    @CompilationFinal(dimensions = 1) private final Type[] types;

    @Child private LLVMMalloc malloc;
    @Child private LLVMIncrementPointerNode inc;
    @Child private LLVMExpressionNode memorySize;
    @Child private LLVMExpressionNode getargs;
    @Child private LLVMStoreNode storeI64;
    @Child private LLVMStoreNode storeAddress;
    @Child private LLVMStoreCStringNode cstr;

    public LLVMPrepareArgumentsNode(Source source, Type[] types) {
        this.types = types;
        getargs = new LLVMGetArgumentsNode(source);
        memorySize = new LLVMGetArgumentSizeNode();
        inc = LLVMIncrementPointerNodeGen.create();
        malloc = LLVMMallocNodeGen.create(memorySize);
        storeI64 = LLVMI64StoreNodeGen.create();
        storeAddress = LLVMAddressStoreNodeGen.create(new PointerType(PrimitiveType.I8));
        cstr = new LLVMStoreCStringNode();

        if (types.length > 3) {
            throw new AssertionError(source + " " + Arrays.toString(types));
        }
    }

    public Object[] execute(VirtualFrame frame) {
        Object[] mainArgs = (Object[]) getargs.executeGeneric(frame);
        int type = 0;
        // Rust extra handling: main(i64,...)
        if (types.length > 0 && types[0] instanceof PrimitiveType) {
            if (((PrimitiveType) types[0]).getPrimitiveKind() == PrimitiveKind.I64) {
                type = 1;
            }
        }

        Type ptrType = new PointerType(PrimitiveType.I8);
        int ptrsz = LLVMExpressionNode.ADDRESS_SIZE_IN_BYTES;
        String[] args = getStringArgs(mainArgs);
        String[] env = getenv(getContextReference().get().getEnvironment());
        // argc, two NULL pointers and 3 auxv entries
        int offset = (args.length + env.length + 3 + 3 * 2) * ptrsz;

        Object memory = malloc.executeGeneric(frame);
        Object ptr = memory;
        Object valuePtr = inc.executeWithTarget(memory, offset, PrimitiveType.I8);

        // argc
        storeI64.executeWithTarget(frame, ptr, (long) args.length);
        ptr = inc.executeWithTarget(ptr, LLVMExpressionNode.I64_SIZE_IN_BYTES, ptrType);

        // argv
        for (String arg : args) {
            storeAddress.executeWithTarget(frame, ptr, valuePtr);
            ptr = inc.executeWithTarget(ptr, ptrsz, ptrType);
            valuePtr = cstr.executeWithTarget(frame, valuePtr, arg);
        }
        storeAddress.executeWithTarget(frame, ptr, LLVMAddress.nullPointer());

        // env
        ptr = inc.executeWithTarget(ptr, ptrsz, ptrType);
        for (String var : env) {
            storeAddress.executeWithTarget(frame, ptr, valuePtr);
            ptr = inc.executeWithTarget(ptr, ptrsz, ptrType);
            valuePtr = cstr.executeWithTarget(frame, valuePtr, var);
        }
        storeAddress.executeWithTarget(frame, ptr, LLVMAddress.nullPointer());
        ptr = inc.executeWithTarget(ptr, ptrsz, ptrType);

        // auxv
        Object platform = valuePtr;
        valuePtr = cstr.executeWithTarget(frame, valuePtr, PLATFORM);
        Object execfn = valuePtr;
        valuePtr = cstr.executeWithTarget(frame, valuePtr, args[0]);

        // AT_EXECFN = argv[0]
        storeI64.executeWithTarget(frame, ptr, AT_EXECFN);
        ptr = inc.executeWithTarget(ptr, LLVMExpressionNode.I64_SIZE_IN_BYTES, ptrType);
        storeAddress.executeWithTarget(frame, ptr, execfn);
        ptr = inc.executeWithTarget(ptr, LLVMExpressionNode.I64_SIZE_IN_BYTES, PrimitiveType.I64);
        // AT_PLATFORM = "x86_64"
        storeI64.executeWithTarget(frame, ptr, AT_PLATFORM);
        ptr = inc.executeWithTarget(ptr, LLVMExpressionNode.I64_SIZE_IN_BYTES, ptrType);
        storeAddress.executeWithTarget(frame, ptr, platform);
        ptr = inc.executeWithTarget(ptr, LLVMExpressionNode.I64_SIZE_IN_BYTES, PrimitiveType.I64);
        // AT_NULL = 0
        storeI64.executeWithTarget(frame, ptr, AT_NULL);
        ptr = inc.executeWithTarget(ptr, LLVMExpressionNode.I64_SIZE_IN_BYTES, PrimitiveType.I64);
        storeI64.executeWithTarget(frame, ptr, 0L);

        return new Object[]{memory, type};
    }

    private class LLVMGetArgumentsNode extends LLVMExpressionNode {
        private final Source sourceFile;

        LLVMGetArgumentsNode(Source source) {
            sourceFile = source;
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            Object[] sulongArgs = getContextReference().get().getMainArguments();
            if (types.length > 3) {
                throw new AssertionError(sourceFile + " " + Arrays.toString(types));
            }
            int mainArgsCount = sulongArgs == null ? 0 : sulongArgs.length;
            int argsCount = mainArgsCount + 1;
            Object[] mainArgs = new Object[argsCount];
            mainArgs[0] = sourceFile.getPath() == null ? "" : sourceFile.getPath();
            if (mainArgsCount > 0) {
                System.arraycopy(sulongArgs, 0, mainArgs, 1, mainArgsCount);
            }
            return mainArgs;
        }
    }

    private class LLVMGetArgumentSizeNode extends LLVMExpressionNode {
        @Override
        public Object executeGeneric(VirtualFrame frame) {
            int ptrsz = LLVMExpressionNode.ADDRESS_SIZE_IN_BYTES;
            String[] args = getStringArgs((Object[]) getargs.executeGeneric(frame));
            String[] env = getenv(getContextReference().get().getEnvironment());

            // argc, two NULL pointers and 3 auxv entries
            int size = (args.length + env.length + 3 + 3 * 2) * ptrsz;

            for (String arg : args) {
                size += arg.length() + 1;
            }
            for (String var : env) {
                size += var.length() + 1;
            }

            size += PLATFORM.length() + args[0].length() + 2;

            return size;
        }
    }

    @TruffleBoundary
    private static String[] getStringArgs(Object[] args) {
        String[] stringArgs = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            stringArgs[i] = args[i].toString();
        }
        return stringArgs;
    }

    @TruffleBoundary
    private static String[] getenv(Map<String, String> env) {
        return env.entrySet().stream().map((e) -> e.getKey() + "=" + e.getValue()).toArray(String[]::new);
    }

    private static class LLVMStoreCStringNode extends LLVMNode {
        @Child private LLVMIncrementPointerNode inc = LLVMIncrementPointerNodeGen.create();
        @Child private LLVMStoreNode storeI8 = LLVMI8StoreNodeGen.create();

        @TruffleBoundary
        private static byte[] getBytes(String s) {
            return s.getBytes();
        }

        public Object executeWithTarget(VirtualFrame frame, Object address, String string) {
            Object ptr = address;
            for (byte b : getBytes(string)) { // automatic charset conversion
                storeI8.executeWithTarget(frame, ptr, b);
                ptr = inc.executeWithTarget(ptr, 1, PrimitiveType.I8);
            }
            storeI8.executeWithTarget(frame, ptr, (byte) 0);
            return inc.executeWithTarget(ptr, 1, PrimitiveType.I8);
        }
    }
}
