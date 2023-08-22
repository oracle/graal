/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.runtime.hotspot.libgraal;

import static com.oracle.truffle.runtime.hotspot.libgraal.LibGraalScope.getIsolateThread;

import java.util.function.Supplier;

import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilationTask;
import com.oracle.truffle.compiler.TruffleCompilerListener;
import com.oracle.truffle.compiler.hotspot.HotSpotTruffleCompiler;
import com.oracle.truffle.runtime.hotspot.HotSpotTruffleRuntime;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Encapsulates handles to {@link HotSpotTruffleCompiler} objects in the libgraal isolates.
 */
final class LibGraalHotSpotTruffleCompiler implements HotSpotTruffleCompiler {

    static final class Handle extends LibGraalObject {

        Handle(long handle) {
            super(handle);
        }
    }

    private final HotSpotTruffleRuntime runtime;

    private long getOrCreateIsolate(TruffleCompilable compilable, boolean firstInitialization) {
        return resolveIsolateHandleImpl(() -> {
            long isolateThread = getIsolateThread();
            long compilerHandle = TruffleToLibGraalCalls.newCompiler(isolateThread, LibGraalTruffleCompilationSupport.handle(runtime));
            TruffleToLibGraalCalls.initializeCompiler(isolateThread, compilerHandle, compilable, firstInitialization);
            return new Handle(compilerHandle);
        });
    }

    private static long resolveIsolateHandleImpl(Supplier<Handle> handleSupplier) {
        try (LibGraalScope scope = new LibGraalScope()) {
            return scope.getIsolate().getSingleton(Handle.class, handleSupplier).getHandle();
        }
    }

    LibGraalHotSpotTruffleCompiler(HotSpotTruffleRuntime runtime) {
        this.runtime = runtime;
    }

    @SuppressWarnings("try")
    @Override
    public void initialize(TruffleCompilable compilable, boolean firstInitialization) {
        getOrCreateIsolate(compilable, firstInitialization);
    }

    @Override
    @SuppressWarnings("try")
    public void doCompile(
                    TruffleCompilationTask task,
                    TruffleCompilable compilable,
                    TruffleCompilerListener listener) {
        try (LibGraalScope scope = new LibGraalScope(LibGraalScope.DetachAction.DETACH_RUNTIME_AND_RELEASE)) {
            TruffleToLibGraalCalls.doCompile(getIsolateThread(), getOrCreateIsolate(compilable, false), task, compilable, listener);
        }
    }

    @SuppressWarnings("try")
    @Override
    public void shutdown() {
        // When to call shutdown?
        // When the isolate with the compiler is closing or when the VM is exiting?
        // If it should be called when the VM is exiting we may need to create a new isolate and a
        // new Truffle compiler just to call shutdown on it.
        // Current implementations only dump profiling data which does not work on libgraal GR-24633
    }

    @Override
    @SuppressWarnings("try")
    public void installTruffleCallBoundaryMethod(ResolvedJavaMethod method, TruffleCompilable compilable) {
        try (LibGraalScope scope = new LibGraalScope(LibGraalScope.DetachAction.DETACH_RUNTIME_AND_RELEASE)) {
            TruffleToLibGraalCalls.installTruffleCallBoundaryMethod(getIsolateThread(), getOrCreateIsolate(compilable, false), LibGraal.translate(method));
        }
    }

    @Override
    @SuppressWarnings("try")
    public void installTruffleReservedOopMethod(ResolvedJavaMethod method, TruffleCompilable compilable) {
        try (LibGraalScope scope = new LibGraalScope(LibGraalScope.DetachAction.DETACH_RUNTIME_AND_RELEASE)) {
            TruffleToLibGraalCalls.installTruffleReservedOopMethod(getIsolateThread(), getOrCreateIsolate(compilable, false), LibGraal.translate(method));
        }
    }

    Integer pendingTransferToInterpreterOffset;

    @SuppressWarnings("try")
    @Override
    public int pendingTransferToInterpreterOffset(TruffleCompilable compilable) {
        if (pendingTransferToInterpreterOffset == null) {
            try (LibGraalScope scope = new LibGraalScope(LibGraalScope.DetachAction.DETACH_RUNTIME_AND_RELEASE)) {
                pendingTransferToInterpreterOffset = TruffleToLibGraalCalls.pendingTransferToInterpreterOffset(getIsolateThread(), getOrCreateIsolate(compilable, false),
                                compilable);
            }
        }
        return pendingTransferToInterpreterOffset;
    }

    @Override
    @SuppressWarnings("try")
    public void purgePartialEvaluationCaches() {
        try (LibGraalScope scope = new LibGraalScope(LibGraalScope.DetachAction.DETACH_RUNTIME_AND_RELEASE)) {
            try {
                Handle compilerHandle = scope.getIsolate().getSingleton(Handle.class, () -> null);
                // Clear the encoded graph cache only if the compiler has already been created.
                if (compilerHandle != null) {
                    TruffleToLibGraalCalls.purgePartialEvaluationCaches(getIsolateThread(), compilerHandle.getHandle());
                }
            } catch (DestroyedIsolateException e) {
                // Truffle compiler threads (trying to purge PE caches) may race during VM exit with
                // the compiler isolate teardown. DestroyedIsolateException is only expected to be
                // observed here during VM exit; where it can be safely ignored.
                if (e.isVmExit()) {
                    // ignore
                } else {
                    throw e;
                }
            }
        }
    }
}
