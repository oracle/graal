/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.GeneratedBy;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMNativeLibrary;
import com.oracle.truffle.llvm.runtime.nodes.asm.syscall.LLVMInfo;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.aarch64.darwin.LLVMDarwinAarch64VaListStorage;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.aarch64.linux.LLVMLinuxAarch64VaListStorage;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListLibrary;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_64VaListStorage;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86_win.LLVMX86_64_WinVaListStorage;
import com.oracle.truffle.llvm.runtime.pointer.LLVMMaybeVaPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;
import com.oracle.truffle.nfi.api.NativePointerLibrary;
import com.oracle.truffle.nfi.api.SignatureLibrary;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The purpose of this class is to provide a "dummy" receiver used during the AOT preparation to get
 * rid of the Truffle library exports that are incompatible with the actual platform and thus help
 * reduce the size of ASTs. The replacement takes place in an alternative AOT preparation AST
 * traversal in {@link com.oracle.truffle.llvm.runtime.nodes.func.LLVMFunctionStartNode}.
 */
@ExportLibrary(value = LLVMManagedReadLibrary.class, useForAOT = false)
@ExportLibrary(value = LLVMManagedWriteLibrary.class, useForAOT = false)
@ExportLibrary(value = LLVMVaListLibrary.class, useForAOT = false)
@ExportLibrary(value = NativeTypeLibrary.class, useForAOT = false)
@ExportLibrary(value = LLVMNativeLibrary.class, useForAOT = false)
@ExportLibrary(value = NativePointerLibrary.class, useForAOT = false)
@ExportLibrary(value = SignatureLibrary.class, useForAOT = false)
public final class DummyReceiver {

    private static final Map<Class<?>, Supplier<Boolean>> platformVerifiers = new ConcurrentHashMap<>();

    private static void registerPlatformVerifier(Class<?> vaListStorageClass, Supplier<Boolean> verifier) {
        platformVerifiers.put(vaListStorageClass, verifier);
    }

    static {
        registerPlatformVerifier(LLVMX86_64_WinVaListStorage.class,
                        () -> LLVMInfo.SYSNAME.toLowerCase().contains("windows") && LLVMInfo.MACHINE.equalsIgnoreCase("x86_64"));
        registerPlatformVerifier(LLVMDarwinAarch64VaListStorage.class,
                        () -> LLVMInfo.SYSNAME.toLowerCase().contains("mac os x") && LLVMInfo.MACHINE.equalsIgnoreCase("aarch64"));
        registerPlatformVerifier(LLVMLinuxAarch64VaListStorage.class,
                        () -> LLVMInfo.SYSNAME.toLowerCase().contains("linux") && LLVMInfo.MACHINE.equalsIgnoreCase("aarch64"));
        registerPlatformVerifier(LLVMX86_64VaListStorage.class,
                        () -> !LLVMInfo.SYSNAME.toLowerCase().contains("windows") && LLVMInfo.MACHINE.equalsIgnoreCase("x86_64"));
        registerPlatformVerifier(LLVMMaybeVaPointer.class,
                        () -> (LLVMInfo.SYSNAME.toLowerCase().contains("mac os x") || LLVMInfo.SYSNAME.toLowerCase().contains("windows")) && LLVMInfo.MACHINE.equalsIgnoreCase("aarch64"));
    }

    public static boolean isValidPlatform(Class<?> possibleVaListStorageClass) {
        Supplier<Boolean> platformVerifier = platformVerifiers.get(possibleVaListStorageClass);
        return platformVerifier == null || platformVerifier.get();
    }

    public static final DummyReceiver INSTANCE_FOR_CREATE = new DummyReceiver();
    public static final DummyReceiver INSTANCE_FOR_ACCEPTS = new DummyReceiver();

    /**
     * This method is invoked during before the AOT preparation of a Truffle library to make an
     * attempt to replace the given library node for the "dummy" one.
     * 
     * @param node the library node
     * @return the AOT replacement for the library or <code>null</code> if there is no replacement.
     */
    @SuppressWarnings("unchecked")
    public static Library getAOTLibraryReplacement(Library node) {
        GeneratedBy generatedByAnnot = node.getClass().getAnnotation(GeneratedBy.class);
        if (generatedByAnnot != null) {
            Class<?> exportClass = generatedByAnnot.value();
            Class<? extends Library> libClass = node.getClass();
            while (libClass.getSuperclass() != Library.class) {
                libClass = (Class<? extends Library>) libClass.getSuperclass();
            }

            LibraryFactory<? extends Library> libraryFactory = LibraryFactory.resolve(libClass);
            /*
             * The dummy exports accept INSTANCE_FOR_CREATE to pass the assertion in
             * LibraryFactory.create
             */
            Library dummyLibrary = libraryFactory.create(DummyReceiver.INSTANCE_FOR_CREATE);
            /*
             * But the dummy exports DO NOT accept INSTANCE_FOR_ACCEPTS, which allows recognizing a
             * dummy export from a possible fallback export, which is supposed to accept it.
             */
            if (dummyLibrary.accepts(DummyReceiver.INSTANCE_FOR_ACCEPTS)) {
                /*
                 * Accepting the dummy instance indicates that the library is not a dummy one, as a
                 * dummy library must not accept anything. If it is the case, do not replace the
                 * node.
                 */
                return null;
            }

            if (!DummyReceiver.isValidPlatform(exportClass)) {
                return dummyLibrary;
            }

            if (libClass == exportClass) {
                // Generated cached dispatch node
                return null;
            }
        }
        return null;
    }

    @ExportMessage
    public boolean accepts() {
        return this == INSTANCE_FOR_CREATE;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isReadable() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public byte readI8(@SuppressWarnings("unused") long offset) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public short readI16(@SuppressWarnings("unused") long offset) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public int readI32(@SuppressWarnings("unused") long offset) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public LLVMPointer readPointer(@SuppressWarnings("unused") long offset) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public Object readGenericI64(@SuppressWarnings("unused") long offset) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean isWritable() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public void writeI8(@SuppressWarnings("unused") long offset, @SuppressWarnings("unused") byte value) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public void writeI16(@SuppressWarnings("unused") long offset, @SuppressWarnings("unused") short value) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public void writeI32(@SuppressWarnings("unused") long offset, @SuppressWarnings("unused") int value) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public void writeGenericI64(@SuppressWarnings("unused") long offset, @SuppressWarnings("unused") Object value) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public void initialize(@SuppressWarnings("unused") Object[] arguments, @SuppressWarnings("unused") int numberOfExplicitArguments, @SuppressWarnings("unused") Frame frame) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public void cleanup(@SuppressWarnings("unused") Frame frame) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public void copy(@SuppressWarnings("unused") Object destVaList, @SuppressWarnings("unused") Frame frame) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public Object shift(@SuppressWarnings("unused") Type type, @SuppressWarnings("unused") Frame frame) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean hasNativeType() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public Object getNativeType() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @ExportMessage(name = "isPointer", library = LLVMNativeLibrary.class)
    @SuppressWarnings("static-method")
    public boolean isPointerLLVMNativeLibrary() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @ExportMessage(name = "asPointer", library = LLVMNativeLibrary.class)
    @SuppressWarnings("static-method")
    public long asPointerLLVMNativeLibrary() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public LLVMNativePointer toNativePointer() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @ExportMessage(name = "isPointer", library = NativePointerLibrary.class)
    @SuppressWarnings("static-method")
    public boolean isPointerNativePointerLibrary() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @ExportMessage(name = "asPointer", library = NativePointerLibrary.class)
    @SuppressWarnings("static-method")
    public long asPointerNativePointerLibrary() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public Object call(@SuppressWarnings("unused") Object functionPointer, @SuppressWarnings("unused") Object... args) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public Object bind(@SuppressWarnings("unused") Object functionPointer) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public Object createClosure(@SuppressWarnings("unused") Object executable) {
        throw CompilerDirectives.shouldNotReachHere();
    }
}
