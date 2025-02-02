/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.libgraal.truffle;

import jdk.graal.compiler.serviceprovider.GlobalAtomicLong;
import jdk.graal.compiler.truffle.host.TruffleHostEnvironment;
import jdk.graal.compiler.truffle.host.TruffleHostEnvironment.TruffleRuntimeScope;
import jdk.vm.ci.common.NativeImageReinitialize;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This handles the Truffle host environment lookup on HotSpot with Libgraal.
 * <p>
 * For Libgraal the Truffle runtime needs to be discovered across multiple isolates. When a Truffle
 * runtime in libgraal configuration gets initialized then {@link #registerRuntime(long)} gets
 * called in any libgraal isolate. We remember the registered Truffle runtime using a weak global
 * JNI reference in a {@link GlobalAtomicLong}. Since we use a {@link GlobalAtomicLong} to remember
 * the reference, all libgraal isolates now see the registered runtime and can provide access to it.
 * This way any libgraal host compilation isolate can see Truffle after it was first initialized
 * even if none of the Truffle compilation isolates are still alive. Another positive side-effect of
 * this is that Truffle related host compilation intrinsics and phases are never applied if no
 * Truffle runtime was ever registered.
 */
public final class LibGraalTruffleHostEnvironmentLookup implements TruffleHostEnvironment.Lookup {

    private static final int NO_TRUFFLE_REGISTERED = 0;
    private static final GlobalAtomicLong WEAK_TRUFFLE_RUNTIME_INSTANCE = new GlobalAtomicLong(NO_TRUFFLE_REGISTERED);

    @NativeImageReinitialize private TruffleHostEnvironment previousRuntime;

    @Override
    @SuppressWarnings("try")
    public TruffleHostEnvironment lookup(ResolvedJavaType forType) {
        long globalReference = WEAK_TRUFFLE_RUNTIME_INSTANCE.get();
        if (globalReference == NO_TRUFFLE_REGISTERED) {
            // fast path if Truffle was not initialized
            return null;
        }
        Object runtimeLocalHandle = NativeImageHostCalls.createLocalHandleForWeakGlobalReference(globalReference);
        if (runtimeLocalHandle == null) {
            // The Truffle runtime was collected by the GC
            return null;
        }
        TruffleHostEnvironment environment = this.previousRuntime;
        if (environment != null) {
            Object cached = hsRuntime(environment).hsHandle;
            if (NativeImageHostCalls.isSameObject(cached, runtimeLocalHandle)) {
                // fast path for registered and cached Truffle runtime handle
                return environment;
            }
        }
        /*
         * We do not currently validate the forType. But in the future we want to lookup the runtime
         * per type. So in theory multiple truffle runtimes can be loaded.
         */
        try (TruffleRuntimeScope scope = LibGraalTruffleHostEnvironment.openTruffleRuntimeScopeImpl()) {
            HSTruffleCompilerRuntime runtime = new HSTruffleCompilerRuntime(NativeImageHostCalls.createGlobalHandle(runtimeLocalHandle, true), NativeImageHostCalls.getObjectClass(runtimeLocalHandle));
            this.previousRuntime = environment = new LibGraalTruffleHostEnvironment(runtime, HotSpotJVMCIRuntime.runtime().getHostJVMCIBackend().getMetaAccess());
            return environment;
        }
    }

    private static HSTruffleCompilerRuntime hsRuntime(TruffleHostEnvironment environment) {
        return (HSTruffleCompilerRuntime) environment.runtime();
    }

    static boolean registerRuntime(long truffleRuntimeWeakRef) {
        // TODO GR-44222 support multiple runtimes.
        return WEAK_TRUFFLE_RUNTIME_INSTANCE.compareAndSet(0, truffleRuntimeWeakRef);
    }
}
