/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;
import static org.graalvm.libgraal.LibGraalScope.getIsolateThread;

import org.graalvm.compiler.truffle.common.TruffleCompilationSupport;
import org.graalvm.compiler.truffle.common.TruffleCompiler;
import org.graalvm.compiler.truffle.common.TruffleCompilerOptionDescriptor;
import org.graalvm.compiler.truffle.common.TruffleCompilerOptionDescriptor.Type;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.runtime.hotspot.HotSpotTruffleRuntime;
import org.graalvm.libgraal.DestroyedIsolateException;
import org.graalvm.libgraal.LibGraalObject;
import org.graalvm.libgraal.LibGraalScope;
import org.graalvm.libgraal.LibGraalScope.DetachAction;
import org.graalvm.nativebridge.BinaryInput;

/**
 * Represents a truffle compilation bundling compilable and task into a single object. Also installs
 * the TTY filter to forward log messages to the truffle runtime.
 */
public final class LibGraalTruffleCompilationSupport implements TruffleCompilationSupport {

    private volatile String cachedCompilerConfigurationName;

    /*
     * When migrated to native-bridge this method should be marked @Idempotent instead.
     */
    @Override
    public String getCompilerConfigurationName(TruffleCompilerRuntime runtime) {
        String compilerConfiguration = this.cachedCompilerConfigurationName;
        if (compilerConfiguration == null) {
            this.cachedCompilerConfigurationName = compilerConfiguration = getCompilerConfigurationNameImpl(runtime);
        }
        return compilerConfiguration;
    }

    @SuppressWarnings("try")
    private static String getCompilerConfigurationNameImpl(TruffleCompilerRuntime runtime) {
        try (LibGraalScope scope = new LibGraalScope(DetachAction.DETACH_RUNTIME_AND_RELEASE)) {
            return TruffleToLibGraalCalls.getCompilerConfigurationFactoryName(getIsolateThread(), handle(runtime));
        }
    }

    @Override
    public TruffleCompiler createCompiler(TruffleCompilerRuntime runtime) {
        return new LibGraalHotSpotTruffleCompiler((HotSpotTruffleRuntime) runtime);
    }

    @SuppressWarnings("try")
    @Override
    public void registerRuntime(TruffleCompilerRuntime runtime) {
        try (LibGraalScope scope = new LibGraalScope(DetachAction.DETACH_RUNTIME_AND_RELEASE)) {
            runtime().registerNativeMethods(TruffleToLibGraalCalls.class);
            if (!TruffleToLibGraalCalls.registerRuntime(getIsolateThread(), runtime)) {
                throw new IllegalStateException("Truffle with libgraal cannot be loaded in multiple class loaders. Make sure Truffle is loaded with the system class loader.");
            }
        }
    }

    @SuppressWarnings("try")
    @Override
    public TruffleCompilerOptionDescriptor[] listCompilerOptions() {
        try (LibGraalScope scope = new LibGraalScope(DetachAction.DETACH_RUNTIME_AND_RELEASE)) {
            byte[] binary = TruffleToLibGraalCalls.listCompilerOptions(getIsolateThread());
            BinaryInput input = BinaryInput.create(binary);
            int length = input.readInt();
            TruffleCompilerOptionDescriptor[] descriptors = new TruffleCompilerOptionDescriptor[length];
            Type[] types = Type.values();
            for (int i = 0; i < length; i++) {
                String name = input.readUTF();
                int typeOrdinal = input.readInt();
                boolean deprecated = input.readBoolean();
                String help = input.readUTF();
                String deprecationMessage = input.readUTF();
                descriptors[i] = new TruffleCompilerOptionDescriptor(name, types[typeOrdinal], deprecated, help, deprecationMessage);
            }
            return descriptors;
        }
    }

    @SuppressWarnings("try")
    @Override
    public String validateCompilerOption(String key, String value) {
        try (LibGraalScope scope = new LibGraalScope(DetachAction.DETACH_RUNTIME_AND_RELEASE)) {
            return TruffleToLibGraalCalls.validateCompilerOption(getIsolateThread(), key, value);
        }
    }

    @SuppressWarnings("try")
    @Override
    public boolean compilerOptionExists(String key) {
        try (LibGraalScope scope = new LibGraalScope(DetachAction.DETACH_RUNTIME_AND_RELEASE)) {
            return TruffleToLibGraalCalls.compilerOptionExists(getIsolateThread(), key);
        }
    }

    @Override
    public AutoCloseable openCompilerThreadScope() {
        return new LibGraalScope(DetachAction.DETACH_RUNTIME_AND_RELEASE);
    }

    /**
     * Handle to a HSTruffleCompilerRuntime object in an libgraal heap.
     */
    static final class Handle extends LibGraalObject {
        Handle(long handle) {
            super(handle);
        }
    }

    @Override
    public boolean isSuppressedCompilationFailure(Throwable throwable) {
        return throwable instanceof DestroyedIsolateException && ((DestroyedIsolateException) throwable).isVmExit();
    }

    @SuppressWarnings("try")
    static long handle(TruffleCompilerRuntime runtime) {
        try (LibGraalScope scope = new LibGraalScope()) {
            return scope.getIsolate().getSingleton(Handle.class, () -> {
                return new Handle(TruffleToLibGraalCalls.initializeRuntime(getIsolateThread(), runtime, runtime.getClass()));
            }).getHandle();
        }
    }

}
