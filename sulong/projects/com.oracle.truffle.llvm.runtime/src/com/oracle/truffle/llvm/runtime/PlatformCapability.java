/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.runtime.config.LLVMCapability;
import com.oracle.truffle.llvm.runtime.memory.LLVMSyscallOperationNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVAStart;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListStorage.VAListPointerWrapperFactory;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.Type;

import java.lang.reflect.Array;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.List;

public abstract class PlatformCapability<S extends Enum<S> & LLVMSyscallEntry> implements LLVMCapability {

    public abstract ByteOrder getPlatformByteOrder();

    public abstract Path getSulongLibrariesPath();

    public abstract String[] getSulongDefaultLibraries();

    public abstract LLVMSyscallOperationNode createSyscallNode(long index);

    public abstract String getBuiltinsLibrary();

    public abstract String getLibrarySuffix();

    public abstract boolean isGlobalDLOpenFlagSet(int flag);

    public abstract boolean isFirstDLOpenFlagSet(int flag);

    public abstract boolean isLazyDLOpenFlagSet(int flag);

    public abstract boolean isDefaultDLSymFlagSet(long flag);

    @CompilerDirectives.CompilationFinal(dimensions = 1) private final S[] valueToSysCall;

    protected PlatformCapability(Class<S> cls) {
        valueToSysCall = initTable(cls);
    }

    public void initializeThread(@SuppressWarnings("unused") LLVMContext context,
                    @SuppressWarnings("unused") Thread thread) {
        // Nothing needs to be done in Sulong for native thread initialization.
    }

    @SuppressWarnings("unchecked")
    private S[] initTable(Class<S> cls) {
        S[] constants = cls.getEnumConstants();
        if (constants == null) {
            // cls is an empty enum
            return (S[]) Array.newInstance(cls, 0);
        }
        int max = -1;
        for (S syscall : constants) {
            max = Math.max(max, syscall.value());
        }
        S[] syscalls = (S[]) Array.newInstance(cls, max + 1);
        for (S syscall : constants) {
            syscalls[syscall.value()] = syscall;
        }
        return syscalls;
    }

    protected S getSyscall(long value) {
        if (value >= 0 && value < valueToSysCall.length) {
            S syscall = valueToSysCall[(int) value];
            if (syscall != null) {
                return syscall;
            }
        }
        throw error(value);
    }

    @CompilerDirectives.TruffleBoundary
    private static IllegalArgumentException error(long value) {
        return new IllegalArgumentException("Unknown syscall number: " + value);
    }

    /**
     * Inject implicit or modify explicit dependencies for a {@code file}.
     *
     * @param context the {@link LLVMContext}
     * @param file the {@link TruffleFile}
     * @param dependencies (unmodifiable) list of dependencies specified by the file
     */
    public List<String> preprocessDependencies(LLVMContext context, TruffleFile file, List<String> dependencies) {
        return dependencies;
    }

    // va_list interface

    /**
     * @param rootNode TODO
     * @param vaListStackPtr
     * @return a new instance of a platform specific managed va_list object
     */
    public abstract Object createVAListStorage(RootNode rootNode, LLVMPointer vaListStackPtr);

    /**
     * @return the type of the platform specific va_list structure
     */
    public abstract Type getVAListType();

    /**
     * @return the alignment of the platform specific va_list structure
     */
    public int getVAListAlignment() {
        return 16;
    }

    /**
     * @return a helper node creating auxiliary wrappers for native LLVM pointers and managed
     *         pointers not pointing to a platform specific <code>va_list</code> managed object. It
     *         allows for {@link LLVMVAStart} and others to treat LLVM pointers just as the managed
     *         <code>va_list</code> objects and thus to remain platform independent.
     */
    public abstract VAListPointerWrapperFactory createNativeVAListWrapper(boolean cached);

}
