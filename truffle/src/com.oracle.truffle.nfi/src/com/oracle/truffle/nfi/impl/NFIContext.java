/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
import com.oracle.truffle.nfi.spi.types.NativeArrayTypeMirror;
import com.oracle.truffle.nfi.spi.types.NativeFunctionTypeMirror;
import com.oracle.truffle.nfi.spi.types.NativeSimpleType;
import com.oracle.truffle.nfi.spi.types.NativeSimpleTypeMirror;
import com.oracle.truffle.nfi.spi.types.NativeTypeMirror;
import com.oracle.truffle.nfi.spi.types.NativeTypeMirror.Kind;

class NFIContext {

    final NFILanguageImpl language;
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
    @CompilationFinal int ISOLATED_NAMESPACE;
    // Checkstyle: resume field name check

    // Initialized lazily by native code.
    private volatile long isolatedNamespaceId;

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

    NFIContext(NFILanguageImpl language, Env env) {
        this.language = language;
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
        if (nativeContext != 0) {
            disposeNativeContext(nativeContext);
            nativeContext = 0;
        }
        nativeEnv.set(null);
        synchronized (nativePointerMap) {
            nativePointerMap.clear();
        }
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

    Object lookupSymbol(LibFFILibrary library, String name) {
        return LibFFISymbol.create(language, library, name, lookup(nativeContext, library.handle, name));
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

    @TruffleBoundary
    private static native long lookup(long nativeContext, long library, String name);

    static native void freeLibrary(long library);
}
