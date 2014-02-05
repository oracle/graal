/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.graal.nfi.hotspot.amd64;

import java.lang.reflect.*;

import sun.misc.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;

public class AMD64HotSpotNativeFunctionInterface implements NativeFunctionInterface {

    private static final Class LOOKUP_FUNCTION_RETURNTYPE = long.class;
    private static final Class[] LOOKUP_FUNCTION_SIGNATURE = new Class[]{long.class, long.class};

    private static final Unsafe unsafe = getUnsafe();

    private static final int STD_BUFFER_SIZE = 1024;

    protected final HotSpotProviders providers;
    protected final Backend backend;
    protected final AMD64HotSpotNativeLibraryHandle rtldDefault;
    protected final AMD64HotSpotNativeFunctionPointer libraryLoadFunctionPointer;
    protected final AMD64HotSpotNativeFunctionPointer functionLookupFunctionPointer;

    private AMD64HotSpotNativeFunctionHandle libraryLookupFunctionHandle;
    private AMD64HotSpotNativeFunctionHandle dllLookupFunctionHandle;

    public AMD64HotSpotNativeFunctionInterface(HotSpotProviders providers, Backend backend, AMD64HotSpotNativeFunctionPointer libraryLoadFunctionPointer,
                    AMD64HotSpotNativeFunctionPointer functionLookUpFunctionPointer, AMD64HotSpotNativeLibraryHandle rtldDefault) {
        this.rtldDefault = rtldDefault;
        this.providers = providers;
        this.backend = backend;
        this.libraryLoadFunctionPointer = libraryLoadFunctionPointer;
        this.functionLookupFunctionPointer = functionLookUpFunctionPointer;
    }

    @Override
    public AMD64HotSpotNativeLibraryHandle getLibraryHandle(String libPath) {
        if (libraryLookupFunctionHandle == null) {
            libraryLookupFunctionHandle = new AMD64HotSpotNativeFunctionHandle(providers, backend, libraryLoadFunctionPointer, long.class, new Class[]{long.class, long.class, int.class});
        }

        long allocatedMemory = -1;
        try {
            allocatedMemory = unsafe.allocateMemory(STD_BUFFER_SIZE);
        } catch (OutOfMemoryError e) {
            throw new AssertionError();
        }

        Object[] args = new Object[]{copyStringToMemory(libPath), allocatedMemory, STD_BUFFER_SIZE};
        long libraryHandle = (long) libraryLookupFunctionHandle.call(args);
        unsafe.freeMemory(allocatedMemory);
        return new AMD64HotSpotNativeLibraryHandle(libraryHandle);
    }

    @Override
    public AMD64HotSpotNativeFunctionHandle getFunctionHandle(NativeLibraryHandle libraryHandle, String functionName, Class returnType, Class[] argumentTypes) {
        AMD64HotSpotNativeFunctionPointer functionPointer = lookupFunctionPointer(functionName, libraryHandle);
        if (!functionPointer.isValid()) {
            throw new IllegalStateException(functionName + " not found!");
        }
        return getFunctionHandle(functionPointer, returnType, argumentTypes);
    }

    @Override
    public AMD64HotSpotNativeFunctionHandle getFunctionHandle(NativeLibraryHandle[] libraryHandles, String functionName, Class returnType, Class[] argumentTypes) {
        AMD64HotSpotNativeFunctionPointer functionPointer = null;
        for (NativeLibraryHandle libraryHandle : libraryHandles) {
            functionPointer = lookupFunctionPointer(functionName, libraryHandle);
            if (functionPointer.isValid()) {
                return new AMD64HotSpotNativeFunctionHandle(providers, backend, functionPointer, returnType, argumentTypes);
            }
        }
        return getFunctionHandle(functionName, returnType, argumentTypes);
    }

    @Override
    public AMD64HotSpotNativeFunctionHandle getFunctionHandle(String functionName, Class returnType, Class[] argumentTypes) {
        if (rtldDefault.asRawValue() == HotSpotVMConfig.INVALID_RTLD_DEFAULT_HANDLE) {
            throw new AssertionError("No library provided or RTLD_DEFAULT not supported!");
        }
        return getFunctionHandle(rtldDefault, functionName, returnType, argumentTypes);
    }

    private AMD64HotSpotNativeFunctionPointer lookupFunctionPointer(String functionName, NativeLibraryHandle handle) {

        if (!functionLookupFunctionPointer.isValid()) {
            throw new IllegalStateException("no dlsym function pointer");
        }
        if (dllLookupFunctionHandle == null) {
            dllLookupFunctionHandle = new AMD64HotSpotNativeFunctionHandle(providers, backend, functionLookupFunctionPointer, LOOKUP_FUNCTION_RETURNTYPE, LOOKUP_FUNCTION_SIGNATURE);
        }
        long allocatedMemory = copyStringToMemory(functionName);
        Object[] args = new Object[]{handle, allocatedMemory};
        long functionPointer = (long) dllLookupFunctionHandle.call(args);
        unsafe.freeMemory(allocatedMemory);
        return new AMD64HotSpotNativeFunctionPointer(functionPointer, functionName);
    }

    private static long copyStringToMemory(String str) {
        int size = str.length();
        long ptr = unsafe.allocateMemory(size + 1);
        for (int i = 0; i < size; i++) {
            unsafe.putByte(ptr + i, (byte) str.charAt(i));
        }
        unsafe.putByte(ptr + size, (byte) '\0');
        return ptr;
    }

    private static Unsafe getUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException e) {
        }
        try {
            Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeInstance.setAccessible(true);
            return (Unsafe) theUnsafeInstance.get(Unsafe.class);
        } catch (IllegalArgumentException | SecurityException | NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e);
        }
    }

    @Override
    public AMD64HotSpotNativeFunctionHandle getFunctionHandle(NativeFunctionPointer functionPointer, Class returnType, Class[] argumentTypes) {
        if (functionPointer instanceof AMD64HotSpotNativeFunctionPointer) {
            if (!((AMD64HotSpotNativeFunctionPointer) functionPointer).isValid()) {
                throw new IllegalStateException("Function Symbol not found");
            }
        } else {
            throw new IllegalStateException("AMD64 function pointer required!");
        }
        return new AMD64HotSpotNativeFunctionHandle(providers, backend, (AMD64HotSpotNativeFunctionPointer) functionPointer, returnType, argumentTypes);
    }

    @Override
    public AMD64HotSpotNativeFunctionPointer getFunctionPointer(NativeLibraryHandle[] libraryHandles, String functionName) {
        for (NativeLibraryHandle libraryHandle : libraryHandles) {
            AMD64HotSpotNativeFunctionPointer functionPointer = lookupFunctionPointer(functionName, libraryHandle);
            if (functionPointer.isValid()) {
                return functionPointer;
            }
        }
        throw new LinkageError("Function not found: " + functionName);
    }

    @Override
    public NativeFunctionPointer getNativeFunctionPointerFromRawValue(long rawValue) {
        return new AMD64HotSpotNativeFunctionPointer(rawValue, null);
    }

}
