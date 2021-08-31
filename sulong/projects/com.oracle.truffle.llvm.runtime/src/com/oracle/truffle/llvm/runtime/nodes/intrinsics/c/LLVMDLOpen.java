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

import static com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMDLOpen.RTLDFlags.RTLD_GLOBAL;
import static com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMDLOpen.RTLDFlags.RTLD_LAZY;
import static com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMDLOpen.RTLDFlags.RTLD_LOCAL;
import static com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMDLOpen.RTLDFlags.RTLD_NOW;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.PlatformCapability;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMAsForeignLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMReadStringNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

@NodeChild(type = LLVMExpressionNode.class)
@NodeChild(type = LLVMExpressionNode.class)
public abstract class LLVMDLOpen extends LLVMIntrinsic {

    public enum RTLDFlags {
        RTLD_OPEN_DEFAULT,       // Linux Max/Darwin
        RTLD_LAZY,          // 1 1
        RTLD_NOW,           // 2 2
        RTLD_GLOBAL,        // 256 8
        RTLD_LOCAL,         // 0 4
        RTLD_FIRST;        // - 100

        public boolean isActive(RTLDFlags phase) {
            return phase == this;
        }
    }

    @ExportLibrary(value = LLVMAsForeignLibrary.class, useForAOT = true, useForAOTPriority = 1)
    @ExportLibrary(InteropLibrary.class)
    protected static final class LLVMDLHandler implements TruffleObject {
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

        @ExportMessage
        boolean isPointer(@CachedLibrary("this.library") InteropLibrary interop) {
            return interop.isPointer(library);
        }

        @ExportMessage
        long asPointer(@CachedLibrary("this.library") InteropLibrary interop) throws UnsupportedMessageException {
            return interop.asPointer(library);
        }

        @ExportMessage
        void toNative(@CachedLibrary("this.library") InteropLibrary interop) {
            interop.toNative(library);
        }
    }

    @Specialization
    protected Object doOp(Object file,
                    int flag,
                    @Cached() LLVMReadStringNode readStr) {
        // Default settings for RTLD flags.
        RTLDFlags globalOrLocal = RTLD_LOCAL;
        RTLDFlags lazyOrNow = RTLD_NOW;
        boolean hasFirstFlag;

        // Check for flag settings for each platform.
        PlatformCapability<?> sysContextExt = LLVMLanguage.get(this).getCapability(PlatformCapability.class);
        if (sysContextExt.isGlobalDLOpenFlagSet(flag)) {
            globalOrLocal = RTLD_GLOBAL;
        }
        if (sysContextExt.isLazyDLOpenFlagSet(flag)) {
            lazyOrNow = RTLD_LAZY;
        }
        hasFirstFlag = sysContextExt.isFirstDLOpenFlagSet(flag);
        try {
            return LLVMManagedPointer.create(new LLVMDLHandler(loadLibrary(getContext(), globalOrLocal, lazyOrNow, hasFirstFlag, flag, file, readStr)));
        } catch (RuntimeException e) {
            getContext().setDLError(1);
            return LLVMNativePointer.createNull();
        }
    }

    @TruffleBoundary
    protected Object loadLibrary(LLVMContext ctx, RTLDFlags globalOrLocal, RTLDFlags lazyOrNow, boolean hasFirstFlag, int flag, Object file, LLVMReadStringNode readStr) {
        if (file.equals(LLVMNativePointer.createNull())) {
            if (ctx.getMainLibrary() != null && (lazyOrNow.isActive(RTLD_LAZY) || hasFirstFlag)) {
                return ctx.getMainLibrary();
            } else {
                return LLVMNativePointer.createNull();
            }
        }

        String filename = readStr.executeWithTarget(file);
        Path path = Paths.get(filename);
        TruffleFile truffleFile;
        if (filename.contains("/")) {
            truffleFile = ctx.getEnv().getInternalTruffleFile(path.toUri());
        } else {
            truffleFile = ctx.getMainLibraryLocator().locate(ctx, filename, "<source library>");
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
}
