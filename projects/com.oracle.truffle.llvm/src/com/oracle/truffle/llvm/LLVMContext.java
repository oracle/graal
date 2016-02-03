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

import java.lang.reflect.Method;

import com.oracle.graal.truffle.hotspot.nfi.HotSpotNativeFunctionInterface;
import com.oracle.graal.truffle.hotspot.nfi.HotSpotNativeFunctionPointer;
import com.oracle.graal.truffle.hotspot.nfi.HotSpotNativeLibraryHandle;
import com.oracle.nfi.NativeFunctionInterfaceRuntime;
import com.oracle.nfi.api.NativeFunctionHandle;
import com.oracle.nfi.api.NativeFunctionInterface;
import com.oracle.nfi.api.NativeLibraryHandle;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.ExecutionContext;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.parser.factories.NodeFactoryFacade;
import com.oracle.truffle.llvm.runtime.LLVMOptimizationConfiguration;
import com.oracle.truffle.llvm.runtime.LLVMOptions;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException.UnsupportedReason;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunction;
import com.oracle.truffle.llvm.types.LLVMFunction.LLVMRuntimeType;

public class LLVMContext extends ExecutionContext {

    private final LLVMFunctionRegistry registry;

    private final NodeFactoryFacade facade;

    private LLVMNode[] staticInits;

    private LLVMAddress[] deallocations;

    public LLVMContext(NodeFactoryFacade facade, LLVMOptimizationConfiguration optimizationConfig) {
        this.facade = facade;
        this.registry = new LLVMFunctionRegistry(optimizationConfig);
    }

    public RootCallTarget getFunction(LLVMFunction function) {
        return LLVMFunctionRegistry.lookup(function);
    }

    public LLVMNode[] getStaticInits() {
        return staticInits;
    }

    public LLVMAddress[] getAllocatedGlobalAddresses() {
        return deallocations;
    }

    public LLVMFunctionRegistry getFunctionRegistry() {
        CompilerAsserts.neverPartOfCompilation();
        return registry;
    }

    public void setStaticInits(LLVMNode[] staticInits, LLVMAddress[] deallocations) {
        this.staticInits = staticInits;
        this.deallocations = deallocations;
    }

    // TODO lazy loading
    private static final NativeFunctionInterface nfi = NativeFunctionInterfaceRuntime.getNativeFunctionInterface();

    // TODO extend foreign function interface API
    private static long lookupSymbol(String name) {
        try {
            Method method = HotSpotNativeFunctionInterface.class.getDeclaredMethod("lookupFunctionPointer", String.class, NativeLibraryHandle.class, boolean.class);
            HotSpotNativeFunctionInterface face = (HotSpotNativeFunctionInterface) nfi;
            method.setAccessible(true);
            HotSpotNativeFunctionPointer result = (HotSpotNativeFunctionPointer) method.invoke(face, name, new HotSpotNativeLibraryHandle("", 0), false);
            return result.getRawValue();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public long getNativeHandle(String name) {
        return lookupSymbol(name.substring(1));
    }

    public NativeFunctionHandle getNativeHandle(LLVMFunction function, LLVMExpressionNode[] args) {
        CompilerAsserts.neverPartOfCompilation();
        if (nfi == null) {
            throw new IllegalStateException("want to call native function but VM does not support the native function interface!");
        }
        Class<?> retType = getJavaClass(function.getLlvmReturnType());
        Class<?>[] paramTypes = getJavaClassses(args, function.getLlvmParamTypes());
        String functionName = function.getName().substring(1);
        NativeFunctionHandle functionHandle;
        if (functionName.equals("fork") || functionName.equals("pthread_create") || functionName.equals("pipe")) {
            throw new LLVMUnsupportedException(UnsupportedReason.MULTITHREADING);
        }
        String[] dynamicLibraryPaths = LLVMOptions.getDynamicLibraryPaths();
        if (dynamicLibraryPaths == null) {
            functionHandle = nfi.getFunctionHandle(functionName, retType, paramTypes);
        } else {
            int i = 0;
            NativeLibraryHandle[] handles = new NativeLibraryHandle[dynamicLibraryPaths.length];
            for (String library : dynamicLibraryPaths) {
                handles[i++] = nfi.getLibraryHandle(library);
            }
            functionHandle = nfi.getFunctionHandle(handles, functionName, retType, paramTypes);
        }
        return functionHandle;
    }

    // TODO: are there cases where the nodes alone are not sufficient, and we also need the types??
    private Class<?>[] getJavaClassses(LLVMExpressionNode[] args, @SuppressWarnings("unused") LLVMRuntimeType... llvmTypes) {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = facade.getJavaClass(args[i]);
        }
        return types;
    }

    private static Class<?> getJavaClass(LLVMRuntimeType type) {
        switch (type) {
            case I1:
                return boolean.class;
            case I8:
                return byte.class;
            case I16:
                return short.class;
            case I32:
                return int.class;
            case I64:
                return long.class;
            case FLOAT:
                return float.class;
            case DOUBLE:
                return double.class;
            case VOID:
                return void.class;
            case ADDRESS:
            case STRUCT:
                return long.class;
            case X86_FP80:
                return byte[].class;
            case FUNCTION_ADDRESS:
                throw new LLVMUnsupportedException(UnsupportedReason.FUNCTION_POINTER_ESCAPES_TO_NATIVE);
            default:
                throw new AssertionError(type);
        }
    }

}
