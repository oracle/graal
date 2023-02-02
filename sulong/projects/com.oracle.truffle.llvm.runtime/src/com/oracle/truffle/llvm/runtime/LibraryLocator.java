/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates.
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

import java.util.List;
import java.util.logging.Level;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleFile;

/**
 * Encapsulates logic for locating libraries.
 */
public abstract class LibraryLocator {
    private static final Level LOADER_LOGGING_LEVEL = Level.FINER;

    @CompilerDirectives.TruffleBoundary
    public final TruffleFile locate(LLVMContext context, String lib, Object reason) {
        if (loggingEnabled()) {
            LibraryLocator.traceLoader(context, "");
        }
        traceFind(context, lib, reason);
        return locateLibrary(context, lib, reason);
    }

    protected abstract TruffleFile locateLibrary(LLVMContext context, String lib, Object reason);

    public static void traceFind(LLVMContext context, Object lib, Object reason) {
        if (loggingEnabled()) {
            traceLoader(context, "find external library=%s; needed by %s", lib, reason);
        }
    }

    public static void traceTry(LLVMContext context, Object file) {
        if (loggingEnabled()) {
            traceLoader(context, "  trying file=%s", file);
        }
    }

    public static void traceDelegateNative(LLVMContext context, Object file) {
        if (loggingEnabled()) {
            traceLoader(context, "  delegating to native=%s", file);
        }
    }

    public static void traceLoadNative(LLVMContext context, Object file) {
        if (loggingEnabled()) {
            traceLoader(context, "load library natively=%s", file);
        }
    }

    public static void traceSearchPath(LLVMContext context, List<?> paths) {
        if (loggingEnabled()) {
            traceLoader(context, " search path=%s", paths);
        }
    }

    public static void traceSearchPath(LLVMContext context, List<?> paths, Object reason) {
        if (loggingEnabled()) {
            traceLoader(context, " search path=%s (local path from %s)", paths, reason);
        }
    }

    public static void traceParseBitcode(LLVMContext context, Object path) {
        if (loggingEnabled()) {
            traceLoader(context, "parse bitcode=%s", path);
        }
    }

    public static void traceAlreadyLoaded(LLVMContext context, Object path) {
        if (loggingEnabled()) {
            traceLoader(context, "library already located: %s", path);
        }
    }

    public static void traceStaticInits(LLVMContext context, Object prefix, Object module) {
        traceStaticInits(context, prefix, module, "");
    }

    public static void traceStaticInits(LLVMContext context, Object prefix, Object module, Object details) {
        if (loggingEnabled()) {
            traceLoader(context, "calling %s: %s %s", prefix, module, details);
        }
    }

    private static final boolean isLoggingEnabled = LLVMContext.loaderLogger().isLoggable(LOADER_LOGGING_LEVEL);

    public static boolean loggingEnabled() {
        return isLoggingEnabled;
    }

    @CompilerDirectives.TruffleBoundary
    private static void traceLoader(LLVMContext context, String str) {
        LLVMContext.loaderLogger().log(LOADER_LOGGING_LEVEL,
                        String.format("lli(%x): %s", prefix(context), str));
    }

    @CompilerDirectives.TruffleBoundary
    private static void traceLoader(LLVMContext context, String format, Object arg0) {
        LLVMContext.loaderLogger().log(LOADER_LOGGING_LEVEL,
                        String.format("lli(%x): " + format, prefix(context), arg0));
    }

    @CompilerDirectives.TruffleBoundary
    private static void traceLoader(LLVMContext context, String format, Object arg0, Object arg1) {
        LLVMContext.loaderLogger().log(LOADER_LOGGING_LEVEL,
                        String.format("lli(%x): " + format, prefix(context), arg0, arg1));
    }

    @CompilerDirectives.TruffleBoundary
    private static void traceLoader(LLVMContext context, String format, Object arg0, Object arg1, Object arg2) {
        LLVMContext.loaderLogger().log(LOADER_LOGGING_LEVEL,
                        String.format("lli(%x): " + format, prefix(context), arg0, arg1, arg2));
    }

    private static int prefix(LLVMContext context) {
        return System.identityHashCode(context);
    }
}
