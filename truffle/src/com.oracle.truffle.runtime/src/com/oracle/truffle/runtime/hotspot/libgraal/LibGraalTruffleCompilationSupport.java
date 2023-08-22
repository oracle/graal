/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;

import com.oracle.truffle.compiler.TruffleCompilationSupport;
import com.oracle.truffle.compiler.TruffleCompiler;
import com.oracle.truffle.compiler.TruffleCompilerOptionDescriptor;
import com.oracle.truffle.compiler.TruffleCompilerRuntime;
import com.oracle.truffle.compiler.TruffleCompilerOptionDescriptor.Type;
import com.oracle.truffle.runtime.hotspot.HotSpotTruffleRuntime;
import com.oracle.truffle.runtime.hotspot.libgraal.LibGraalScope.DetachAction;

/**
 * Represents a truffle compilation bundling compilable and task into a single object. Also installs
 * the TTY filter to forward log messages to the truffle runtime.
 */
public final class LibGraalTruffleCompilationSupport implements TruffleCompilationSupport {

    private volatile String cachedCompilerConfigurationName;

    public static void initializeIsolate(long isolateThreadId) {
        runtime().registerNativeMethods(TruffleToLibGraalCalls.class);
        TruffleToLibGraalCalls.initializeIsolate(isolateThreadId, LibGraalTruffleCompilationSupport.class);
    }

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
