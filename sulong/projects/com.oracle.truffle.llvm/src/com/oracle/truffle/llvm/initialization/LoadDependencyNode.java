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

package com.oracle.truffle.llvm.initialization;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.runtime.DefaultLibraryLocator;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LibraryLocator;
import com.oracle.truffle.llvm.runtime.NativeContextExtension;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

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
        CallTarget callTarget = getContext().getCalltargetFromCache(libraryName);
        if (callTarget != null) {
            return callTarget;
        }

        TruffleFile file = createTruffleFile(libraryName, libraryLocator);
        CallTarget calls = getLanguage().getCachedLibrary(file.getPath());
        if (calls != null) {
            return calls;
        }

        Object sourceOrCallTarget = createDependencySource(libraryName, libraryName, file);
        if (sourceOrCallTarget instanceof Source) {
            return getContext().getEnv().parseInternal((Source) sourceOrCallTarget);
        } else if (sourceOrCallTarget instanceof CallTarget) {
            return (CallTarget) sourceOrCallTarget;
        }

        return null;
    }

    @TruffleBoundary
    private Object createDependencySource(String libName, String libPath, TruffleFile file) {
        assert file != null;
        boolean createNative = false;
        try {
            if (!file.isRegularFile()) {
                createNative = true;
            }
        } catch (SecurityException se) {
            createNative = true;
        }

        if (createNative) {
            TruffleFile nativeFile = createNativeTruffleFile(libName, libPath);
            // null is returned if the NFIContextExtension does not exists.
            if (nativeFile == null) {
                return null;
            }
            return createNativeLibraryCallTarget(nativeFile);
        }

        Source source;
        LLVMLanguage language = getLanguage();
        if (language.isDefaultInternalLibrary(file.getPath())) {
            source = language.getDefaultInternalLibraryCache(file.getPath());
        } else {
            try {
                source = Source.newBuilder("llvm", file).internal(getContext().isInternalLibraryFile(file)).build();
            } catch (IOException | SecurityException | OutOfMemoryError ex) {
                throw new LLVMParserException("Error reading file " + file.getName() + ".");
            }
        }
        return source;
    }

    @TruffleBoundary
    private TruffleFile createNativeTruffleFile(String libName, String libPath) {
        LLVMContext context = getContext();
        NativeContextExtension nativeContextExtension = context.getContextExtensionOrNull(NativeContextExtension.class);
        if (nativeContextExtension != null) {
            TruffleFile file = DefaultLibraryLocator.INSTANCE.locate(context, libName, "<native library>");
            if (file == null) {
                // Unable to locate the library -> will go to native
                LibraryLocator.traceDelegateNative(context, libPath);
                file = context.getEnv().getInternalTruffleFile(libPath);
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

    @TruffleBoundary
    private TruffleFile createTruffleFile(String libName, LibraryLocator locator) {
        LLVMContext context = getContext();
        TruffleFile file = locator.locate(context, libName, reason);
        if (file == null) {
            Path path = Paths.get(libName);
            LibraryLocator.traceDelegateNative(context, path);
            file = context.getEnv().getInternalTruffleFile(path.toUri());
        }
        return file;
    }
}
