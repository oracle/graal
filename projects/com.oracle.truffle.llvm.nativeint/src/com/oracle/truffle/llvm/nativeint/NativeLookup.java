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
package com.oracle.truffle.llvm.nativeint;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;

import com.oracle.graal.truffle.hotspot.nfi.HotSpotNativeFunctionInterface;
import com.oracle.graal.truffle.hotspot.nfi.HotSpotNativeFunctionPointer;
import com.oracle.graal.truffle.hotspot.nfi.HotSpotNativeLibraryHandle;
import com.oracle.nfi.NativeFunctionInterfaceRuntime;
import com.oracle.nfi.api.NativeFunctionHandle;
import com.oracle.nfi.api.NativeFunctionInterface;
import com.oracle.nfi.api.NativeLibraryHandle;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.parser.NodeFactoryFacade;
import com.oracle.truffle.llvm.runtime.LLVMLogger;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException.UnsupportedReason;
import com.oracle.truffle.llvm.runtime.options.LLVMOptions;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor.LLVMRuntimeType;

public class NativeLookup {

    static final int LOOKUP_FAILURE = 0;

    private static NativeFunctionInterface nfi;

    private static NativeLibraryHandle[] libraryHandles;

    private final Map<LLVMFunctionDescriptor, Integer> nativeFunctionLookupStats;

    private final Map<LLVMFunctionDescriptor, NativeFunctionHandle> cachedNativeFunctions = new WeakHashMap<>();

    private final NodeFactoryFacade facade;

    private static NativeFunctionInterface getNFI() {
        CompilerAsserts.neverPartOfCompilation();
        if (nfi == null) {
            nfi = NativeFunctionInterfaceRuntime.getNativeFunctionInterface();
            if (nfi == null) {
                throw new AssertionError("could not get the Graal NFI!");
            }
        }
        return nfi;
    }

    private static NativeLibraryHandle[] getLibraryHandles() {
        CompilerAsserts.neverPartOfCompilation();
        if (libraryHandles == null) {
            libraryHandles = getNativeFunctionHandles();
            assert libraryHandles != null;
        }
        return libraryHandles;
    }

    private static NativeLibraryHandle[] getNativeFunctionHandles() {
        String[] dynamicLibraryPaths = LLVMOptions.getDynamicLibraryPaths();
        int i = 0;
        NativeLibraryHandle[] handles = new NativeLibraryHandle[dynamicLibraryPaths.length];
        for (String library : dynamicLibraryPaths) {
            handles[i++] = getNFI().getLibraryHandle(library);
        }
        return handles;
    }

    public NativeLookup(NodeFactoryFacade facade) {
        this.facade = facade;
        if (LLVMOptions.printNativeCallStats()) {
            nativeFunctionLookupStats = new TreeMap<>();
        } else {
            nativeFunctionLookupStats = null;
        }
    }

    // TODO extend foreign function interface API
    private static long lookupSymbol(String name) {
        try {
            Method method = HotSpotNativeFunctionInterface.class.getDeclaredMethod("lookupFunctionPointer", String.class, NativeLibraryHandle.class, boolean.class);
            HotSpotNativeFunctionInterface face = (HotSpotNativeFunctionInterface) getNFI();
            method.setAccessible(true);
            HotSpotNativeLibraryHandle handle;
            if (getLibraryHandles().length == 0) {
                handle = new HotSpotNativeLibraryHandle("", 0);
                return ((HotSpotNativeFunctionPointer) method.invoke(face, name, handle, false)).getRawValue();
            } else {
                for (NativeLibraryHandle libraryHandle : getLibraryHandles()) {
                    try {
                        HotSpotNativeFunctionPointer hotSpotPointer = (HotSpotNativeFunctionPointer) method.invoke(face, name, libraryHandle, false);
                        if (hotSpotPointer != null) {
                            return hotSpotPointer.getRawValue();
                        }
                    } catch (UnsatisfiedLinkError e) {
                        // fall through and try with the next
                    }
                }
                return LOOKUP_FAILURE;
            }
        } catch (Exception e) {
            LLVMLogger.info("external symbol " + name + " could not be resolved!");
            return LOOKUP_FAILURE;
        }
    }

    /**
     * Looks the symbol address up. Returns 0 if no address is found.
     *
     * @param name the name of the symbol to look up.
     * @return the address or 0, if the symbol is not found
     */
    public long getNativeHandle(String name) {
        return lookupSymbol(name.substring(1));
    }

    public NativeFunctionHandle getNativeHandle(LLVMFunctionDescriptor function, LLVMExpressionNode[] args) {
        CompilerAsserts.neverPartOfCompilation();
        if (cachedNativeFunctions.containsKey(function)) {
            return cachedNativeFunctions.get(function);
        } else {
            NativeFunctionHandle handle = uncachedGetNativeFunctionHandle(function, args);
            // FIXME we should also cache var args!
            if (!function.isVarArgs()) {
                cachedNativeFunctions.put(function, handle);
            }
            return handle;
        }
    }

    private NativeFunctionHandle uncachedGetNativeFunctionHandle(LLVMFunctionDescriptor function, LLVMExpressionNode[] args) {
        Class<?> retType = getJavaClass(function.getReturnType());
        Class<?>[] paramTypes = getJavaClassses(args);
        String functionName = function.getName().substring(1);
        NativeFunctionHandle functionHandle;
        if (functionName.equals("fork") || functionName.equals("pthread_create") || functionName.equals("pipe")) {
            throw new LLVMUnsupportedException(UnsupportedReason.MULTITHREADING);
        }
        if (LLVMOptions.getDynamicLibraryPaths() == null) {
            functionHandle = getNFI().getFunctionHandle(functionName, retType, paramTypes);
        } else {
            functionHandle = getNFI().getFunctionHandle(getLibraryHandles(), functionName, retType, paramTypes);
        }
        if (LLVMOptions.printNativeCallStats() && functionHandle != null) {
            recordNativeFunctionCallSite(function);
        }
        return functionHandle;
    }

    private void recordNativeFunctionCallSite(LLVMFunctionDescriptor function) {
        CompilerAsserts.neverPartOfCompilation();
        Integer val = nativeFunctionLookupStats.get(function);
        int newVal;
        if (val == null) {
            newVal = 1;
        } else {
            newVal = val + 1;
        }
        nativeFunctionLookupStats.put(function, newVal);
    }

    // TODO: are there cases where the nodes alone are not sufficient, and we also need the types??
    private Class<?>[] getJavaClassses(LLVMExpressionNode[] args) {
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
                return long.class;
            default:
                throw new AssertionError(type);
        }
    }

    public Map<LLVMFunctionDescriptor, Integer> getNativeFunctionLookupStats() {
        return nativeFunctionLookupStats;
    }

}
