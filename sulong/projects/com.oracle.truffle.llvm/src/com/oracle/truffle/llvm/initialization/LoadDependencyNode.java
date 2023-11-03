/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates.
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

package com.oracle.truffle.llvm.initialization;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.runtime.DefaultLibraryLocator;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LibraryLocator;
import com.oracle.truffle.llvm.runtime.NativeContextExtension;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;

import java.io.IOException;

public final class LoadDependencyNode extends LLVMNode {

    public static final LoadDependencyNode[] EMPTY = {};

    private final String libraryName;
    private final String reason;
    private final LibraryLocator libraryLocator;

    private LoadDependencyNode(String libraryName, LibraryLocator libraryLocator, String reason) {
        this.libraryName = libraryName;
        this.libraryLocator = libraryLocator;
        this.reason = reason;
    }

    public static LoadDependencyNode create(String libraryName, LibraryLocator libraryLocator, String reason) {
        return new LoadDependencyNode(libraryName, libraryLocator, reason);
    }

    public CallTarget execute() {
        LLVMContext context = getContext();
        CallTarget callTarget = context.getCalltargetFromCache(libraryName);
        if (LibraryLocator.loggingEnabled()) {
            LibraryLocator.traceStaticInits(context, "load dependency execute, loading library", libraryLocator);
            LibraryLocator.traceStaticInits(context, "load dependency execute, call target is", callTarget);
        }
        if (callTarget != null) {
            return callTarget;
        } else {
            return parse(context);
        }
    }

    @TruffleBoundary
    private CallTarget parse(LLVMContext context) {
        Source source;
        try {
            source = libraryLocator.locateSource(context, libraryName, reason);
        } catch (IOException | SecurityException | OutOfMemoryError ex) {
            throw new LLVMParserException("Error reading library " + libraryName + ".");
        }
        if (source == null) {
            TruffleFile nativeFile = createNativeTruffleFile(libraryName);
            // null is returned if the NFIContextExtension does not exists.
            if (nativeFile == null) {
                return null;
            }
            return createNativeLibraryCallTarget(nativeFile);
        } else {
            CallTarget cached = getLanguage().getCachedLibrary(source);
            if (LibraryLocator.loggingEnabled()) {
                LibraryLocator.traceStaticInits(context, "load dependency execute, cached library", cached);
            }
            if (cached != null) {
                return cached;
            }
            return getContext().getEnv().parseInternal(source);
        }
    }

    private TruffleFile createNativeTruffleFile(String libName) {
        LLVMContext context = getContext();
        NativeContextExtension nativeContextExtension = context.getContextExtensionOrNull(NativeContextExtension.class);
        if (nativeContextExtension != null) {
            TruffleFile file = DefaultLibraryLocator.INSTANCE.locateFile(context, libName, "<native library>");
            if (file == null) {
                // Unable to locate the library -> will go to native
                LibraryLocator.traceDelegateNative(context, libName);
                file = context.getEnv().getInternalTruffleFile(libName);
            }
            return file;
        }
        return null;
    }

    private CallTarget createNativeLibraryCallTarget(TruffleFile file) {
        if (getContext().getEnv().getOptions().get(SulongEngineOption.PARSE_ONLY)) {
            return RootNode.createConstantNode(0).getCallTarget();
        } else {
            LoadNativeNode loadNative = LoadNativeNode.create(getLanguage(), file);
            return loadNative.getCallTarget();
        }
    }

    public String getLibraryName() {
        return libraryName;
    }
}
