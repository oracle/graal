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

import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;
import static org.graalvm.libgraal.LibGraalScope.getIsolateThread;

import org.graalvm.compiler.truffle.common.TruffleCompilerOptionDescriptor;
import org.graalvm.compiler.truffle.common.TruffleCompilerOptionDescriptor.Type;
import org.graalvm.compiler.truffle.common.hotspot.HotSpotTruffleCompiler;
import org.graalvm.compiler.truffle.runtime.hotspot.AbstractHotSpotTruffleRuntime;
import org.graalvm.libgraal.DestroyedIsolateException;
import org.graalvm.libgraal.LibGraalObject;
import org.graalvm.libgraal.LibGraalScope;
import org.graalvm.libgraal.LibGraalScope.DetachAction;
import org.graalvm.nativebridge.BinaryInput;

import com.oracle.truffle.api.TruffleRuntime;

/**
 * A {@link TruffleRuntime} that uses libgraal for compilation.
 */
final class LibGraalTruffleRuntime extends AbstractHotSpotTruffleRuntime {

    @SuppressWarnings("try")
    LibGraalTruffleRuntime() {
        try (LibGraalScope scope = new LibGraalScope(DetachAction.DETACH_RUNTIME_AND_RELEASE)) {
            runtime().registerNativeMethods(TruffleToLibGraalCalls.class);
            if (!TruffleToLibGraalCalls.registerRuntime(getIsolateThread(), this)) {
                throw new IllegalStateException("Truffle with libgraal cannot be loaded in multiple class loaders. Make sure Truffle is loaded with the system class loader.");
            }
        }
    }

    long handle() {
        try (LibGraalScope scope = new LibGraalScope()) {
            return scope.getIsolate().getSingleton(Handle.class, () -> {
                return new Handle(TruffleToLibGraalCalls.initializeRuntime(getIsolateThread(), LibGraalTruffleRuntime.this, getClass()));
            }).getHandle();
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
    public boolean existsCompilerOption(String key) {
        try (LibGraalScope scope = new LibGraalScope(DetachAction.DETACH_RUNTIME_AND_RELEASE)) {
            return TruffleToLibGraalCalls.existsCompilerOption(getIsolateThread(), key);
        }
    }

    @Override
    public HotSpotTruffleCompiler newTruffleCompiler() {
        return new LibGraalHotSpotTruffleCompiler(this);
    }

    @SuppressWarnings("try")
    @Override
    protected String initLazyCompilerConfigurationName() {
        try (LibGraalScope scope = new LibGraalScope(DetachAction.DETACH_RUNTIME_AND_RELEASE)) {
            return TruffleToLibGraalCalls.getCompilerConfigurationFactoryName(getIsolateThread(), handle());
        }
    }

    @Override
    protected AutoCloseable openCompilerThreadScope() {
        return new LibGraalScope(DetachAction.DETACH_RUNTIME_AND_RELEASE);
    }

    @Override
    protected boolean isSuppressedTruffleRuntimeException(Throwable throwable) {
        return throwable instanceof DestroyedIsolateException && ((DestroyedIsolateException) throwable).isVmExit();
    }

    /**
     * Handle to a HSTruffleCompilerRuntime object in an libgraal heap.
     */
    static final class Handle extends LibGraalObject {
        Handle(long handle) {
            super(handle);
        }
    }
}
