/*
 * Copyright (c) 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.c;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LibraryLocator;
import com.oracle.truffle.llvm.runtime.PlatformCapability;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMAsForeignLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMReadStringNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@NodeChild(type = LLVMExpressionNode.class)
@NodeChild(type = LLVMExpressionNode.class)
public abstract class LLVMDLOpen extends LLVMIntrinsic {

    public enum RTLDFlags {
        RTLD_DEFAULT,       // Linux Max/Darwin
        RTLD_LAZY,          // 1 1
        RTLD_NOW,           // 2 2
        RTLD_GLOBAL,        // 256 8
        RTLD_LOCAL;         // 0 4

        public boolean isActive(RTLDFlags phase) {
            return phase == this;
        }
    }

    @ExportLibrary(LLVMAsForeignLibrary.class)
    protected static final class LLVMDLHandler {
        final Object library;

        private LLVMDLHandler(Object library) {
            this.library = library;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        public boolean isForeign() {
            return true;
        }

        @ExportMessage
        public Object asForeign() {
            return library;
        }

        public Object getLibrary() {
            return library;
        }
    }

    @Specialization
    protected Object doOp(Object file,
                          int flag,
                          @Cached() LLVMReadStringNode readStr,
                          @CachedContext(LLVMLanguage.class) LLVMContext ctx) {
        // Default settings for RTLD flags.
        RTLDFlags globalOrLocal = RTLDFlags.RTLD_LOCAL;
        // Check for flag settings for each platform.
        PlatformCapability<?> sysContextExt = LLVMLanguage.getLanguage().getCapability(PlatformCapability.class);
        if (sysContextExt.isGlobalDLOpenFlagSet(flag)) {
            globalOrLocal = RTLDFlags.RTLD_GLOBAL;
        }
        try {
            return LLVMManagedPointer.create(new LLVMDLHandler(loadLibrary(ctx, globalOrLocal, flag, file, readStr)));
        } catch (RuntimeException e) {
            ctx.setDLError(1);
            return LLVMNativePointer.createNull();
        }
    }

    @TruffleBoundary
    protected Object loadLibrary(LLVMContext ctx, RTLDFlags globalOrLocal, int flag, Object file, LLVMReadStringNode readStr) {
        String filename = readStr.executeWithTarget(file);
        Path path = Paths.get(filename);
        TruffleFile truffleFile;
        if (filename.contains("/")) {
            truffleFile = ctx.getEnv().getInternalTruffleFile(path.toUri());
        } else {
            truffleFile = createTruffleFile(filename, path.toString(), ctx.getMainLibraryLocator(), ctx);
        }
        try {
            Source source = Source.newBuilder("llvm", truffleFile).build();
            CallTarget callTarget = ctx.getEnv().parsePublic(source, String.valueOf(flag));
            return callTarget.call(globalOrLocal);
        } catch (IOException e) {
            ctx.setDLError(1);
            throw new IllegalStateException(e);
        }
    }

    private static TruffleFile createTruffleFile(String libName, String libPath, LibraryLocator locator, LLVMContext context) {
        TruffleFile file = locator.locate(context, libName, "<source library>");
        if (file == null) {
            if (libPath != null) {
                file = context.getEnv().getInternalTruffleFile(libPath);
            } else {
                Path path = Paths.get(libName);
                LibraryLocator.traceDelegateNative(context, path);
                file = context.getEnv().getInternalTruffleFile(path.toUri());
            }
        }
        return file;
    }
}