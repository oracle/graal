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
package com.oracle.truffle.llvm;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.intrinsics.c.LLVMAbort;
import com.oracle.truffle.llvm.intrinsics.c.LLVMExit;
import com.oracle.truffle.llvm.intrinsics.c.LLVMFree;
import com.oracle.truffle.llvm.intrinsics.c.LLVMMalloc;
import com.oracle.truffle.llvm.intrinsics.c.LLVMSqrt;
import com.oracle.truffle.llvm.intrinsics.cpp.LLVMCxaThrow;
import com.oracle.truffle.llvm.types.LLVMFunction;

/**
 * Manages Sulong functions and intrinsified native functions.
 */
public class LLVMFunctionRegistry {

    private static final Map<String, Class<?>> INTRINSICS = new HashMap<>();

    static {
        // Fortran
        INTRINSICS.put("@_gfortran_abort", LLVMAbort.class);

        // C
        INTRINSICS.put("@abort", LLVMAbort.class);
        INTRINSICS.put("@exit", LLVMExit.class);

        if (LLVMOptimizations.INTRINSIFY_C_LIBRARY_FUNCTIONS) {
            INTRINSICS.put("@sqrt", LLVMSqrt.class);
            INTRINSICS.put("@malloc", LLVMMalloc.class);
            INTRINSICS.put("@free", LLVMFree.class);
        }

        // C++
        INTRINSICS.put("@__cxa_throw", LLVMCxaThrow.class);
    }

    /**
     * Maps a function index (see {@link LLVMFunction#getFunctionIndex()} to a call target.
     */
    @CompilationFinal private static RootCallTarget[] functionPtrCallTargetMap;

    /**
     * Looks up the call target for a specific function. The lookup may return <code>null</code> if
     * the function is a native function or if the function cannot be found.
     *
     * @param function the function
     * @return the call target, <code>null</code> if not found.
     */
    public static RootCallTarget lookup(LLVMFunction function) {
        RootCallTarget result = functionPtrCallTargetMap[function.getFunctionIndex()];
        return result;
    }

    // FIXME should not be static
    public static void register(Map<LLVMFunction, RootCallTarget> functionCallTargets) {
        functionPtrCallTargetMap = new RootCallTarget[LLVMFunction.getNumberRegisteredFunctions() + INTRINSICS.size()];
        try {
            registerIntrinsics();
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new AssertionError(e);
        }
        for (LLVMFunction func : functionCallTargets.keySet()) {
            functionPtrCallTargetMap[func.getFunctionIndex()] = functionCallTargets.get(func);
        }
    }

    private static void registerIntrinsics() throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        for (String intrinsicFunction : INTRINSICS.keySet()) {
            LLVMFunction function = LLVMFunction.createFromName(intrinsicFunction);
            Constructor<?> ctor = INTRINSICS.get(intrinsicFunction).getConstructor();
            RootCallTarget callTarget = Truffle.getRuntime().createCallTarget((RootNode) ctor.newInstance());
            addToFunctionMap(function, callTarget);
        }
    }

    private static void addToFunctionMap(LLVMFunction function, RootCallTarget callTarget) {
        assert functionPtrCallTargetMap[function.getFunctionIndex()] == null;
        functionPtrCallTargetMap[function.getFunctionIndex()] = callTarget;
    }

}
