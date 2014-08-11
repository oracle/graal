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
package com.oracle.graal.hotspot.nfi;

import static com.oracle.graal.api.code.CodeUtil.*;
import static com.oracle.graal.compiler.common.UnsafeAccess.*;
import static com.oracle.graal.hotspot.nfi.NativeCallStubGraphBuilder.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.meta.ProfilingInfo.TriState;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.nfi.api.*;

public class HotSpotNativeFunctionInterface implements NativeFunctionInterface {

    private final HotSpotProviders providers;
    private final Backend backend;
    private final HotSpotNativeLibraryHandle rtldDefault;
    private final HotSpotNativeFunctionPointer libraryLoadFunctionPointer;
    private final HotSpotNativeFunctionPointer functionLookupFunctionPointer;
    private final RawNativeCallNodeFactory factory;

    private HotSpotNativeFunctionHandle libraryLookupFunctionHandle;
    private HotSpotNativeFunctionHandle dllLookupFunctionHandle;

    public HotSpotNativeFunctionInterface(HotSpotProviders providers, RawNativeCallNodeFactory factory, Backend backend, long dlopen, long dlsym, long rtldDefault) {
        this.rtldDefault = rtldDefault == HotSpotVMConfig.INVALID_RTLD_DEFAULT_HANDLE ? null : new HotSpotNativeLibraryHandle("RTLD_DEFAULT", rtldDefault);
        this.providers = providers;
        this.backend = backend;
        this.factory = factory;
        this.libraryLoadFunctionPointer = new HotSpotNativeFunctionPointer(dlopen, "os::dll_load");
        this.functionLookupFunctionPointer = new HotSpotNativeFunctionPointer(dlsym, "os::dll_lookup");
    }

    @Override
    public HotSpotNativeLibraryHandle getLibraryHandle(String libPath) {
        if (libraryLookupFunctionHandle == null) {
            libraryLookupFunctionHandle = createHandle(libraryLoadFunctionPointer, long.class, long.class, long.class, int.class);
        }

        int ebufLen = 1024;
        // Allocating a single chunk for both the error message buffer and the
        // file name simplifies deallocation below.
        long buffer = unsafe.allocateMemory(ebufLen + libPath.length() + 1);
        long ebuf = buffer;
        long libPathCString = writeCString(libPath, buffer + ebufLen);
        try {
            long handle = (long) libraryLookupFunctionHandle.call(libPathCString, ebuf, ebufLen);
            if (handle == 0) {
                throw new UnsatisfiedLinkError(libPath);
            }
            return new HotSpotNativeLibraryHandle(libPath, handle);
        } finally {
            unsafe.freeMemory(buffer);
        }
    }

    @Override
    public HotSpotNativeFunctionHandle getFunctionHandle(NativeLibraryHandle library, String name, Class<?> returnType, Class<?>... argumentTypes) {
        HotSpotNativeFunctionPointer functionPointer = lookupFunctionPointer(name, library, true);
        return createHandle(functionPointer, returnType, argumentTypes);
    }

    @Override
    public HotSpotNativeFunctionHandle getFunctionHandle(NativeLibraryHandle[] libraries, String name, Class<?> returnType, Class<?>... argumentTypes) {
        HotSpotNativeFunctionPointer functionPointer = null;
        for (NativeLibraryHandle libraryHandle : libraries) {
            functionPointer = lookupFunctionPointer(name, libraryHandle, false);
            if (functionPointer != null) {
                return createHandle(functionPointer, returnType, argumentTypes);
            }
        }
        // Fall back to default library path
        return getFunctionHandle(name, returnType, argumentTypes);
    }

    @Override
    public HotSpotNativeFunctionHandle getFunctionHandle(String name, Class<?> returnType, Class<?>... argumentTypes) {
        if (rtldDefault == null) {
            throw new UnsatisfiedLinkError(name);
        }
        return getFunctionHandle(rtldDefault, name, returnType, argumentTypes);
    }

    private HotSpotNativeFunctionPointer lookupFunctionPointer(String name, NativeLibraryHandle library, boolean linkageErrorIfMissing) {
        if (name == null || library == null) {
            throw new NullPointerException();
        }

        if (dllLookupFunctionHandle == null) {
            dllLookupFunctionHandle = createHandle(functionLookupFunctionPointer, long.class, long.class, long.class);
        }
        long nameCString = createCString(name);
        try {
            long functionPointer = (long) dllLookupFunctionHandle.call(((HotSpotNativeLibraryHandle) library).value, nameCString);
            if (functionPointer == 0L) {
                if (!linkageErrorIfMissing) {
                    return null;
                }
                throw new UnsatisfiedLinkError(name);
            }
            return new HotSpotNativeFunctionPointer(functionPointer, name);
        } finally {
            unsafe.freeMemory(nameCString);
        }
    }

    @Override
    public HotSpotNativeFunctionHandle getFunctionHandle(NativeFunctionPointer functionPointer, Class<?> returnType, Class<?>... argumentTypes) {
        if (!(functionPointer instanceof HotSpotNativeFunctionPointer)) {
            throw new UnsatisfiedLinkError(functionPointer.getName());
        }
        return createHandle(functionPointer, returnType, argumentTypes);
    }

    private HotSpotNativeFunctionHandle createHandle(NativeFunctionPointer functionPointer, Class<?> returnType, Class<?>... argumentTypes) {
        HotSpotNativeFunctionPointer hs = (HotSpotNativeFunctionPointer) functionPointer;
        InstalledCode code = installNativeFunctionStub(hs.value, returnType, argumentTypes);
        return new HotSpotNativeFunctionHandle(code, hs.name, argumentTypes);
    }

    /**
     * Creates and installs a stub for calling a native function.
     */
    private InstalledCode installNativeFunctionStub(long functionPointer, Class<?> returnType, Class<?>... argumentTypes) {
        StructuredGraph g = getGraph(providers, factory, functionPointer, returnType, argumentTypes);
        Suites suites = providers.getSuites().createSuites();
        PhaseSuite<HighTierContext> phaseSuite = backend.getSuites().getDefaultGraphBuilderSuite().copy();
        CallingConvention cc = getCallingConvention(providers.getCodeCache(), Type.JavaCallee, g.method(), false);
        CompilationResult compResult = GraalCompiler.compileGraph(g, null, cc, g.method(), providers, backend, backend.getTarget(), null, phaseSuite, OptimisticOptimizations.ALL,
                        DefaultProfilingInfo.get(TriState.UNKNOWN), null, suites, new CompilationResult(), CompilationResultBuilderFactory.Default);
        InstalledCode installedCode;
        try (Scope s = Debug.scope("CodeInstall", providers.getCodeCache(), g.method())) {
            installedCode = providers.getCodeCache().addMethod(g.method(), compResult, null, null);
        }
        return installedCode;
    }

    @Override
    public HotSpotNativeFunctionPointer getFunctionPointer(NativeLibraryHandle[] libraries, String name) {
        for (NativeLibraryHandle libraryHandle : libraries) {
            HotSpotNativeFunctionPointer functionPointer = lookupFunctionPointer(name, libraryHandle, false);
            if (functionPointer != null) {
                return functionPointer;
            }
        }
        // Fall back to default library path
        if (rtldDefault == null) {
            throw new UnsatisfiedLinkError(name);
        }
        return lookupFunctionPointer(name, rtldDefault, true);
    }

    public boolean isDefaultLibrarySearchSupported() {
        return rtldDefault != null;
    }

    @Override
    public NativeFunctionPointer getNativeFunctionPointerFromRawValue(long rawValue) {
        return new HotSpotNativeFunctionPointer(rawValue, null);
    }
}
