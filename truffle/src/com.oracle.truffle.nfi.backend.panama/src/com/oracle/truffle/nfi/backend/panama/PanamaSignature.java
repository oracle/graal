/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.backend.panama;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.nfi.backend.spi.NFIBackendSignatureBuilderLibrary;
import com.oracle.truffle.nfi.backend.spi.NFIBackendSignatureLibrary;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

import java.lang.foreign.Addressable;
import java.lang.foreign.Linker;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryAddress;
import java.lang.foreign.MemorySession;

@ExportLibrary(value = NFIBackendSignatureLibrary.class, useForAOT = false)
final class PanamaSignature {

    private final FunctionDescriptor functionDescriptor;

    private final MethodType downcallType;
    private final MethodType upcallType;

    @CompilationFinal private MethodHandle uncachedUpcallHandle;

    PanamaSignature(FunctionDescriptor functionDescriptor, MethodType downcallType, MethodType upcallType) {
        this.functionDescriptor = functionDescriptor;
        this.downcallType = downcallType;
        this.upcallType = upcallType;
    }

    MethodHandle createDowncallHandle(long functionPointer) {
        return Linker.nativeLinker().downcallHandle(MemoryAddress.ofLong(functionPointer), functionDescriptor)
                        .asSpreader(Object[].class, downcallType.parameterCount())
                        .asType(MethodType.methodType(Object.class, Object[].class));
    }

    @ExportMessage
    static final class Call {

        @Specialization(limit = "3", guards = {"args.length == 2", "cachedFunctionPointer == fnInterop.asPointer(functionPointer)"})
        static Object doCached(PanamaSignature receiver, Object functionPointer, Object[] args,
                        @CachedLibrary("functionPointer") InteropLibrary fnInterop,
                        @Cached("fnInterop.asPointer(functionPointer)") long cachedFunctionPointer,
                        @Cached("receiver.createDowncallHandle(cachedFunctionPointer)") MethodHandle downcallHandle,
                        @CachedLibrary(limit = "1") InteropLibrary arg0Lib,
                        @CachedLibrary(limit = "1") InteropLibrary arg1Lib) throws UnsupportedMessageException {
            Object[] processedArgs = new Object[2];
            processedArgs[0] = arg0Lib.asInt(args[0]);
            processedArgs[1] = arg1Lib.asLong(args[1]);
            try {
                return downcallHandle.invokeExact(processedArgs);
            } catch (Throwable t) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException(t);
            }
        }

        @Specialization(replaces = "doCached")
        static Object doGeneric(PanamaSignature receiver, Object functionPointer, Object[] args) {
            throw CompilerDirectives.shouldNotReachHere(); // TODO
        }
    }

    @TruffleBoundary
    MethodHandle createCachedUpcallHandle() {
        PanamaNFILanguage language = PanamaNFILanguage.get(null);
        MethodHandle base = PanamaClosureRootNode.createUpcallHandle(language);
        return base.asType(upcallType);
    }

    @TruffleBoundary
    private void initUncachedUpcallHandle() {
        synchronized (this) {
            if (uncachedUpcallHandle == null) {
                /*
                 * The cached and uncached MethodHandle look exactly the same, the only
                 * difference is the scope of the Truffle profiling info.
                 */
                uncachedUpcallHandle = createCachedUpcallHandle();
            }
        }
    }

    MethodHandle getUncachedUpcallHandle() {
        if (uncachedUpcallHandle == null) {
            initUncachedUpcallHandle();
            assert uncachedUpcallHandle != null;
        }
        return uncachedUpcallHandle;
    }

    boolean checkUpcallMethodHandle(MethodHandle handle) {
        return handle.type() == upcallType;
    }

    @TruffleBoundary
    MemoryAddress bind(MethodHandle cachedHandle, Object receiver) {
        MethodHandle bound = cachedHandle.bindTo(receiver);
        return Linker.nativeLinker().upcallStub(bound, functionDescriptor, MemorySession.global() /* TODO */).address();
    }

    @ExportMessage
    static final class CreateClosure {

        @Specialization(guards = "receiver.checkUpcallMethodHandle(cachedHandle)")
        static PanamaSymbol createCached(PanamaSignature receiver, Object executable,
                        @Cached("receiver.createCachedUpcallHandle()") MethodHandle cachedHandle) {
            MemoryAddress ret = receiver.bind(cachedHandle, executable);
            return new PanamaSymbol(ret);
        }

        @Specialization(replaces = "createCached")
        static PanamaSymbol createGeneric(PanamaSignature receiver, Object executable) {
            MethodHandle handle = receiver.getUncachedUpcallHandle();
            MemoryAddress ret = receiver.bind(handle, executable);
            return new PanamaSymbol(ret);
        }
    }

    @ExportLibrary(NFIBackendSignatureBuilderLibrary.class)
    static final class PanamaSignatureBuilder {

        FunctionDescriptor descriptor;
        MethodType downcallType;
        MethodType upcallType;

        PanamaSignatureBuilder() {
            descriptor = FunctionDescriptor.ofVoid();
            downcallType = MethodType.methodType(void.class);
            upcallType = MethodType.methodType(void.class, Object.class);
        }

        @ExportMessage
        void setReturnType(Object t) {
            PanamaType type = (PanamaType) t;

            if (type.nativeLayout == null) {
                descriptor = descriptor.dropReturnLayout();
            } else {
                descriptor = descriptor.changeReturnLayout(type.nativeLayout);
            }

            downcallType = downcallType.changeReturnType(type.javaType);
            upcallType = upcallType.changeReturnType(type.javaType);
        }

        @ExportMessage
        void addArgument(Object t) {
            PanamaType type = (PanamaType) t;

            descriptor = descriptor.appendArgumentLayouts(type.nativeLayout);
            downcallType = downcallType.appendParameterTypes(type.javaType);
            upcallType = upcallType.appendParameterTypes(type.javaType);
        }

        @ExportMessage
        Object build() {
            return new PanamaSignature(descriptor, downcallType, upcallType);
        }
    }
}
