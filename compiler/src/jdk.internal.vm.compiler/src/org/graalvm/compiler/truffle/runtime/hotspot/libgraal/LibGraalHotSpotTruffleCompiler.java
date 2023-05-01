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

import java.util.Map;
import java.util.function.Supplier;

import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener;
import org.graalvm.compiler.truffle.common.hotspot.HotSpotTruffleCompiler;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.libgraal.DestroyedIsolateException;
import org.graalvm.libgraal.LibGraal;
import org.graalvm.libgraal.LibGraalObject;
import org.graalvm.libgraal.LibGraalScope;
import org.graalvm.util.OptionsEncoder;

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

    private final LibGraalTruffleRuntime runtime;
    private volatile Map<String, Object> previousOptions;

    private long resolveIsolateHandle(Supplier<Map<String, Object>> optionsSupplier, CompilableTruffleAST compilable, boolean firstInitialization) {
        return resolveIsolateHandleImpl(() -> {
            long isolateThread = getIsolateThread();
            long compilerHandle = TruffleToLibGraalCalls.newCompiler(isolateThread, runtime.handle());
            TruffleToLibGraalCalls.initializeCompiler(isolateThread, compilerHandle, OptionsEncoder.encode(optionsSupplier.get()), compilable, firstInitialization);
            return new Handle(compilerHandle);
        });
    }

    long resolveIsolateHandle(Map<String, Object> options) {
        return resolveIsolateHandle(() -> options, null, false);
    }

    private static long resolveIsolateHandleImpl(Supplier<Handle> handleSupplier) {
        try (LibGraalScope scope = new LibGraalScope()) {
            return scope.getIsolate().getSingleton(Handle.class, handleSupplier).getHandle();
        }
    }

    LibGraalHotSpotTruffleCompiler(LibGraalTruffleRuntime runtime) {
        this.runtime = runtime;
    }

    @SuppressWarnings("try")
    @Override
    public void initialize(Map<String, Object> options, CompilableTruffleAST compilable, boolean firstInitialization) {
        /*
         * There can only be a single set of options a compiler can be configured with. The first
         * Truffle engine of a process typically initializes the compiler which also determines the
         * compiler configuration. Any options specified after that will be ignored. So it is safe
         * to store the previous options here and reuse later for recreating a disposed isolate if
         * needed.
         */
        previousOptions = options;
        // Force installation of the Truffle call boundary methods.
        // See AbstractHotSpotTruffleRuntime.setDontInlineCallBoundaryMethod
        // for further details.
        resolveIsolateHandle(() -> options, compilable, firstInitialization);
    }

    @Override
    @SuppressWarnings("try")
    public void doCompile(
                    TruffleCompilationTask task,
                    CompilableTruffleAST compilable,
                    Map<String, Object> options,
                    TruffleCompilerListener listener) {
        try (LibGraalScope scope = new LibGraalScope(LibGraalScope.DetachAction.DETACH_RUNTIME_AND_RELEASE)) {
            TruffleToLibGraalCalls.doCompile(getIsolateThread(), resolveIsolateHandle(() -> options, compilable, false), task, compilable, OptionsEncoder.encode(options), listener);
        }
    }

    @SuppressWarnings("try")
    @Override
    public String getCompilerConfigurationName() {
        return runtime.initLazyCompilerConfigurationName();
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
    public void installTruffleCallBoundaryMethod(ResolvedJavaMethod method) {
        try (LibGraalScope scope = new LibGraalScope(LibGraalScope.DetachAction.DETACH_RUNTIME_AND_RELEASE)) {
            Map<String, Object> options = previousOptions;
            assert options != null : "truffle compiler was never initialized";
            TruffleToLibGraalCalls.installTruffleCallBoundaryMethod(getIsolateThread(), resolveIsolateHandle(options), LibGraal.translate(method));
        }
    }

    @Override
    @SuppressWarnings("try")
    public void installTruffleReservedOopMethod(ResolvedJavaMethod method) {
        try (LibGraalScope scope = new LibGraalScope(LibGraalScope.DetachAction.DETACH_RUNTIME_AND_RELEASE)) {
            Map<String, Object> options = previousOptions;
            assert options != null : "truffle compiler was never initialized";
            TruffleToLibGraalCalls.installTruffleReservedOopMethod(getIsolateThread(), resolveIsolateHandle(options), LibGraal.translate(method));
        }
    }

    Integer pendingTransferToInterpreterOffset;

    @SuppressWarnings("try")
    @Override
    public int pendingTransferToInterpreterOffset(CompilableTruffleAST compilable) {
        if (pendingTransferToInterpreterOffset == null) {
            try (LibGraalScope scope = new LibGraalScope(LibGraalScope.DetachAction.DETACH_RUNTIME_AND_RELEASE)) {
                pendingTransferToInterpreterOffset = TruffleToLibGraalCalls.pendingTransferToInterpreterOffset(getIsolateThread(), resolveIsolateHandle(optionsEncoder(compilable), compilable, false),
                                compilable);
            }
        }
        return pendingTransferToInterpreterOffset;
    }

    private static Supplier<Map<String, Object>> optionsEncoder(CompilableTruffleAST compilable) {
        return () -> GraalTruffleRuntime.getOptionsForCompiler((OptimizedCallTarget) compilable);
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
