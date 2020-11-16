/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.Type;

/**
 * Base class for interacting with native code.
 */
public abstract class NativeContextExtension implements ContextExtension {

    public static class UnsupportedNativeTypeException extends Exception {

        private static final long serialVersionUID = 1L;

        private final Type type;

        public UnsupportedNativeTypeException(Type type) {
            super("unsupported type " + type + " in native interop");
            this.type = type;
        }

        public Type getType() {
            return type;
        }
    }

    public static final class NativeLookupResult {
        private final Object object;

        public NativeLookupResult(Object object) {
            this.object = object;
        }

        public Object getObject() {
            return object;
        }
    }

    public static final class NativePointerIntoLibrary {
        private final long address;

        public NativePointerIntoLibrary(long address) {
            this.address = address;
        }

        public long getAddress() {
            return address;
        }
    }

    public abstract NativePointerIntoLibrary getNativeHandle(String name);

    public abstract Object createNativeWrapper(LLVMFunction function, LLVMFunctionCode code);

    public abstract void addLibraryHandles(Object library);

    public abstract CallTarget parseNativeLibrary(TruffleFile file, LLVMContext context) throws UnsatisfiedLinkError;

    public abstract NativeLookupResult getNativeFunctionOrNull(String name);

    public abstract Object getNativeFunction(String name, String signature);

    public abstract String getNativeSignature(FunctionType type, int skipArguments) throws UnsupportedNativeTypeException;

    /**
     * Allow subclasses to locate internal libraries.
     */
    protected TruffleFile locateInternalLibrary(LLVMContext context, String lib, Object reason) {
        return LLVMContext.InternalLibraryLocator.INSTANCE.locateLibrary(context, lib, reason);
    }

    public static String getNativeLibrarySuffix() {
        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            return "dylib";
        } else {
            return "so";
        }
    }

    public static String getNativeLibrarySuffixVersioned(int version) {
        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            return version + ".dylib";
        } else {
            return "so." + version;
        }
    }
}
