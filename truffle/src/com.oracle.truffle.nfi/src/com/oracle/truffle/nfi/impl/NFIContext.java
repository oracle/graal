/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.truffle.nfi.impl;

import java.util.HashMap;
import java.util.function.Supplier;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.nfi.impl.LibFFIType.ClosureType;
import com.oracle.truffle.nfi.impl.LibFFIType.EnvType;
import com.oracle.truffle.nfi.impl.NativeAllocation.FreeDestructor;
import com.oracle.truffle.nfi.types.NativeArrayTypeMirror;
import com.oracle.truffle.nfi.types.NativeFunctionTypeMirror;
import com.oracle.truffle.nfi.types.NativeSimpleType;
import com.oracle.truffle.nfi.types.NativeSimpleTypeMirror;
import com.oracle.truffle.nfi.types.NativeTypeMirror;
import com.oracle.truffle.nfi.types.NativeTypeMirror.Kind;

class NFIContext {

    Env env;

    private long nativeContext;
    private final ThreadLocal<NativeEnv> nativeEnv = ThreadLocal.withInitial(new NativeEnvSupplier());

    @CompilationFinal(dimensions = 1) final LibFFIType[] simpleTypeMap = new LibFFIType[NativeSimpleType.values().length];
    @CompilationFinal(dimensions = 1) final LibFFIType[] arrayTypeMap = new LibFFIType[NativeSimpleType.values().length];

    private final HashMap<Long, ClosureNativePointer> nativePointerMap = new HashMap<>();

    // initialized by native code
    // Checkstyle: stop field name check
    @CompilationFinal int RTLD_GLOBAL;
    @CompilationFinal int RTLD_LOCAL;
    @CompilationFinal int RTLD_LAZY;
    @CompilationFinal int RTLD_NOW;
    // Checkstyle: resume field name check

    private static class NativeEnv {

        private final long pointer;

        NativeEnv(long pointer) {
            this.pointer = pointer;
        }
    }

    private class NativeEnvSupplier implements Supplier<NativeEnv> {

        @Override
        public NativeEnv get() {
            NativeEnv ret = new NativeEnv(initializeNativeEnv(nativeContext));
            NativeAllocation.getGlobalQueue().registerNativeAllocation(ret, new FreeDestructor(ret.pointer));
            return ret;
        }
    }

    NFIContext(Env env) {
        this.env = env;
    }

    void patchEnv(Env newEnv) {
        this.env = newEnv;
    }

    // called from native
    long getNativeEnv() {
        return nativeEnv.get().pointer;
    }

    void initialize() {
        loadNFILib();
        NativeAllocation.ensureGCThreadRunning();
        nativeContext = initializeNativeContext();
        nativeEnv.remove();
    }

    void dispose() {
        disposeNativeContext(nativeContext);
        nativeContext = 0;
        nativeEnv.set(null);
        nativePointerMap.clear();
    }

    private ClosureNativePointer getClosureNativePointer(long codePointer) {
        synchronized (nativePointerMap) {
            return nativePointerMap.get(codePointer);
        }
    }

    void removeClosureNativePointer(long codePointer) {
        synchronized (nativePointerMap) {
            nativePointerMap.remove(codePointer);
        }
    }

    // called from native
    ClosureNativePointer createClosureNativePointer(long nativeClosure, long codePointer, CallTarget callTarget, LibFFISignature signature) {
        ClosureNativePointer ret = ClosureNativePointer.create(this, nativeClosure, codePointer, callTarget, signature);
        synchronized (nativePointerMap) {
            nativePointerMap.put(codePointer, ret);
        }
        return ret;
    }

    // called from native
    void newClosureRef(long codePointer) {
        getClosureNativePointer(codePointer).addRef();
    }

    // called from native
    void releaseClosureRef(long codePointer) {
        getClosureNativePointer(codePointer).releaseRef();
    }

    // called from native
    TruffleObject getClosureObject(long codePointer) {
        return LibFFIClosure.newClosureWrapper(getClosureNativePointer(codePointer));
    }

    LibFFILibrary loadLibrary(String name, int flags) {
        return LibFFILibrary.create(loadLibrary(nativeContext, name, flags));
    }

    TruffleObject lookupSymbol(LibFFILibrary library, String name) {
        return LibFFISymbol.create(library, lookup(nativeContext, library.handle, name));
    }

    LibFFIType lookupArgType(NativeTypeMirror type) {
        return lookup(type, false);
    }

    LibFFIType lookupRetType(NativeTypeMirror type) {
        return lookup(type, true);
    }

    LibFFIType lookupSimpleType(NativeSimpleType type) {
        return simpleTypeMap[type.ordinal()];
    }

    private LibFFIType lookup(NativeTypeMirror type, boolean asRetType) {
        switch (type.getKind()) {
            case SIMPLE:
                NativeSimpleTypeMirror simpleType = (NativeSimpleTypeMirror) type;
                return lookupSimpleType(simpleType.getSimpleType());

            case ARRAY:
                NativeArrayTypeMirror arrayType = (NativeArrayTypeMirror) type;
                NativeTypeMirror elementType = arrayType.getElementType();

                LibFFIType ret = null;
                if (elementType.getKind() == Kind.SIMPLE) {
                    ret = arrayTypeMap[((NativeSimpleTypeMirror) elementType).getSimpleType().ordinal()];
                }

                if (ret == null) {
                    throw new AssertionError("unsupported array type");
                } else {
                    return ret;
                }

            case FUNCTION:
                NativeFunctionTypeMirror functionType = (NativeFunctionTypeMirror) type;
                LibFFISignature signature = LibFFISignature.create(this, functionType.getSignature());
                return new ClosureType(lookupSimpleType(NativeSimpleType.POINTER), signature, asRetType);

            case ENV:
                if (asRetType) {
                    throw new AssertionError("environment pointer can not be used as return type");
                }
                return new EnvType(lookupSimpleType(NativeSimpleType.POINTER));
        }
        throw new AssertionError("unsupported type");
    }

    protected void initializeSimpleType(NativeSimpleType simpleType, int size, int alignment, long ffiType) {
        assert simpleTypeMap[simpleType.ordinal()] == null : "initializeSimpleType called twice for " + simpleType;
        simpleTypeMap[simpleType.ordinal()] = LibFFIType.createSimpleType(this, simpleType, size, alignment, ffiType);
        arrayTypeMap[simpleType.ordinal()] = LibFFIType.createArrayType(this, simpleType);
    }

    private native long initializeNativeContext();

    private static native void disposeNativeContext(long context);

    private static native long initializeNativeEnv(long context);

    private static void loadNFILib() {
        String nfiLib = System.getProperty("truffle.nfi.library");
        if (nfiLib == null) {
            System.loadLibrary("trufflenfi");
        } else {
            System.load(nfiLib);
        }
    }

    ClosureNativePointer allocateClosureObjectRet(LibFFISignature signature, CallTarget callTarget) {
        return allocateClosureObjectRet(nativeContext, signature, callTarget);
    }

    ClosureNativePointer allocateClosureStringRet(LibFFISignature signature, CallTarget callTarget) {
        return allocateClosureStringRet(nativeContext, signature, callTarget);
    }

    ClosureNativePointer allocateClosureBufferRet(LibFFISignature signature, CallTarget callTarget) {
        return allocateClosureBufferRet(nativeContext, signature, callTarget);
    }

    ClosureNativePointer allocateClosureVoidRet(LibFFISignature signature, CallTarget callTarget) {
        return allocateClosureVoidRet(nativeContext, signature, callTarget);
    }

    private static native ClosureNativePointer allocateClosureObjectRet(long nativeContext, LibFFISignature signature, CallTarget callTarget);

    private static native ClosureNativePointer allocateClosureStringRet(long nativeContext, LibFFISignature signature, CallTarget callTarget);

    private static native ClosureNativePointer allocateClosureBufferRet(long nativeContext, LibFFISignature signature, CallTarget callTarget);

    private static native ClosureNativePointer allocateClosureVoidRet(long nativeContext, LibFFISignature signature, CallTarget callTarget);

    long prepareSignature(LibFFIType retType, LibFFIType... args) {
        return prepareSignature(nativeContext, retType, args);
    }

    long prepareSignatureVarargs(LibFFIType retType, int nFixedArgs, LibFFIType... args) {
        return prepareSignatureVarargs(nativeContext, retType, nFixedArgs, args);
    }

    private static native long prepareSignature(long nativeContext, LibFFIType retType, LibFFIType... args);

    private static native long prepareSignatureVarargs(long nativeContext, LibFFIType retType, int nFixedArgs, LibFFIType... args);

    void executeNative(long cif, long functionPointer, byte[] primArgs, int patchCount, int[] patchOffsets, Object[] objArgs, byte[] ret) {
        executeNative(nativeContext, cif, functionPointer, primArgs, patchCount, patchOffsets, objArgs, ret);
    }

    long executePrimitive(long cif, long functionPointer, byte[] primArgs, int patchCount, int[] patchOffsets, Object[] objArgs) {
        return executePrimitive(nativeContext, cif, functionPointer, primArgs, patchCount, patchOffsets, objArgs);
    }

    TruffleObject executeObject(long cif, long functionPointer, byte[] primArgs, int patchCount, int[] patchOffsets, Object[] objArgs) {
        return executeObject(nativeContext, cif, functionPointer, primArgs, patchCount, patchOffsets, objArgs);
    }

    @TruffleBoundary
    private static native void executeNative(long nativeContext, long cif, long functionPointer, byte[] primArgs, int patchCount, int[] patchOffsets, Object[] objArgs, byte[] ret);

    @TruffleBoundary
    private static native long executePrimitive(long nativeContext, long cif, long functionPointer, byte[] primArgs, int patchCount, int[] patchOffsets, Object[] objArgs);

    @TruffleBoundary
    private static native TruffleObject executeObject(long nativeContext, long cif, long functionPointer, byte[] primArgs, int patchCount, int[] patchOffsets, Object[] objArgs);

    private static native long loadLibrary(long nativeContext, String name, int flags);

    private static native long lookup(long nativeContext, long library, String name);

    static native void freeLibrary(long library);
}
