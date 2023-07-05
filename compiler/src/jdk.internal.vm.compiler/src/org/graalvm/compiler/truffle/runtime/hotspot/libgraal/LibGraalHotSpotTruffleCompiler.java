/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.compiler.truffle.runtime.hotspot.libgraal;

import static org.graalvm.libgraal.LibGraalScope.getIsolateThread;

import java.util.function.Supplier;

import org.graalvm.compiler.truffle.common.TruffleCompilable;
import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener;
import org.graalvm.compiler.truffle.common.hotspot.HotSpotTruffleCompiler;
import org.graalvm.compiler.truffle.runtime.hotspot.HotSpotTruffleRuntime;
import org.graalvm.libgraal.DestroyedIsolateException;
import org.graalvm.libgraal.LibGraal;
import org.graalvm.libgraal.LibGraalObject;
import org.graalvm.libgraal.LibGraalScope;

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
