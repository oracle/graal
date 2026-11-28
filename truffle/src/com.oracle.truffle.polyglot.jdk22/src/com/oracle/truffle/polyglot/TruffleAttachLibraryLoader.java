/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

final class TruffleAttachLibraryLoader {

    /* JVMTI retains callbacks into truffleattach for the lifetime of the VM. */
    private static final Arena ARENA = Arena.global();
    private static final int ERROR_BUFFER_SIZE = 1024;

    @SuppressWarnings("restricted")
    static String load(Path libTruffleAttachPath, BiConsumer<String, Throwable> onError) {
        MethodHandle initHandle;
        try {
            SymbolLookup truffleAttachLib = SymbolLookup.libraryLookup(libTruffleAttachPath, ARENA);
            MemorySegment initAddress = truffleAttachLib.find("truffle_attach_init").orElse(null);
            if (initAddress == null) {
                String errorMessage = String.format("The TruffleAttach library at '%s' could not be loaded: It's missing the `truffle_attach_init` symbol.",
                                libTruffleAttachPath);
                onError.accept(errorMessage, null);
                return errorMessage;
            }
            initHandle = Linker.nativeLinker().downcallHandle(initAddress, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        } catch (IllegalCallerException illegalCaller) {
            String vmOption = "--enable-native-access=" + (TruffleAttachLibraryLoader.class.getModule().isNamed() ? "org.graalvm.truffle" : "ALL-UNNAMED");
            String errorMessage = String.format("Failed to load the TruffleAttach library. The Truffle module does not have native access enabled. " +
                            "To resolve this, pass the following VM option: %s.", vmOption);
            onError.accept(errorMessage, illegalCaller);
            return errorMessage;
        } catch (IllegalArgumentException failedToLoad) {
            String errorMessage = String.format("The TruffleAttach library at '%s' could not be loaded: %s.",
                            libTruffleAttachPath, Objects.toString(failedToLoad.getMessage(), failedToLoad.toString()));
            onError.accept(errorMessage, failedToLoad);
            return errorMessage;
        } catch (UnsupportedOperationException unsupported) {
            String errorMessage = String.format("The TruffleAttach library at '%s' could not be loaded: The Foreign Function and Memory API is not supported on this platform: %s.",
                            libTruffleAttachPath, Objects.toString(unsupported.getMessage(), unsupported.toString()));
            onError.accept(errorMessage, unsupported);
            return errorMessage;
        }
        String reason;
        try {
            reason = initializeWithDefiningLoader(initHandle);
        } catch (RuntimeException | Error e) {
            String errorMessage = String.format("The TruffleAttach library at '%s' could not be loaded: Failed to initialize due to exception.", libTruffleAttachPath);
            onError.accept(errorMessage, e);
            return errorMessage;
        }
        if (reason != null) {
            String errorMessage = String.format("The TruffleAttach library at '%s' could not be loaded: %s", libTruffleAttachPath, reason);
            onError.accept(errorMessage, null);
            return errorMessage;
        }
        return null;
    }

    private static String initializeWithDefiningLoader(MethodHandle initHandle) {
        Thread caller = Thread.currentThread();
        ClassLoader loader = TruffleAttachLibraryLoader.class.getClassLoader();
        ClassLoader previous = caller.getContextClassLoader();
        if (previous == loader) {
            return initialize(initHandle);
        }
        try {
            caller.setContextClassLoader(loader);
        } catch (SecurityException denied) {
            // Cleaner threads, for example, reject context loader changes. Only run the downcall
            // on the worker: JDKSupport is being initialized by the waiting caller.
            return CompletableFuture.supplyAsync(() -> initialize(initHandle), task -> {
                Thread worker = Thread.ofPlatform().name("TruffleAttach bootstrap").inheritInheritableThreadLocals(false).unstarted(task);
                worker.setContextClassLoader(loader);
                worker.start();
            }).join();
        }
        try {
            return initialize(initHandle);
        } finally {
            caller.setContextClassLoader(previous);
        }
    }

    private static String initialize(MethodHandle initHandle) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment error = arena.allocate(ERROR_BUFFER_SIZE);
            int rc;
            try {
                rc = (int) initHandle.invokeExact(error, ERROR_BUFFER_SIZE);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                throw new AssertionError("Unexpected unchecked exception from a foreign call.", e);
            }
            return rc == 0 ? null : String.format("Failed to initialize (return code %d): %s", rc, error.getString(0));
        }
    }
}
