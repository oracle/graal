/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hotspot.libgraal.truffle;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.serviceprovider.GlobalAtomicLong;
import jdk.graal.compiler.truffle.host.TruffleHostEnvironment;
import org.graalvm.jniutils.JNI.JClass;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JObject;
import org.graalvm.jniutils.JNI.JWeak;
import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.jniutils.JNIUtil;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.graal.hotspot.libgraal.LibGraal;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This handles the Truffle host environment lookup on HotSpot with Libgraal.
 * <p>
 * For Libgraal the Truffle runtime needs to be discovered across multiple isolates. When a Truffle
 * runtime in libgraal configuration gets initialized then {@link #registerRuntime(JNIEnv, JObject)}
 * gets called in any libgraal isolate. We remember the registered Truffle runtime using a weak
 * global JNI reference in a {@link GlobalAtomicLong}. Since we use a {@link GlobalAtomicLong} to
 * remember the reference, all libgraal isolates now see the registered runtime and can provide
 * access to it. This way any libgraal host compilation isolate can see Truffle after it was first
 * initialized even if none of the Truffle compilation isolates are still alive. Another positive
 * side-effect of this is that Truffle related host compilation intrinsics and phases are never
 * applied if no Truffle runtime was ever registered.
 */
public final class LibGraalTruffleHostEnvironmentLookup implements TruffleHostEnvironment.Lookup {

    private static final int NO_TRUFFLE_REGISTERED = 0;
    private static final GlobalAtomicLong WEAK_TRUFFLE_RUNTIME_INSTANCE = new GlobalAtomicLong(NO_TRUFFLE_REGISTERED);

    @RecomputeFieldValue(kind = Kind.Reset) private TruffleHostEnvironment previousRuntime;

    @Override
    public TruffleHostEnvironment lookup(ResolvedJavaType forType) {
        long globalReference = WEAK_TRUFFLE_RUNTIME_INSTANCE.get();
        if (globalReference == NO_TRUFFLE_REGISTERED) {
            // fast path if Truffle was not initialized
            return null;
        }
        JNIEnv env = JNIMethodScope.env();
        JObject runtimeLocalRef = JNIUtil.NewLocalRef(env, WordFactory.pointer(globalReference));
        if (runtimeLocalRef.isNull()) {
            // The Truffle runtime was collected by the GC
            return null;
        }
        TruffleHostEnvironment environment = this.previousRuntime;
        if (environment != null) {
            JObject cached = hsRuntime(environment).getHandle();
            if (JNIUtil.IsSameObject(env, cached, runtimeLocalRef)) {
                // fast path for registered and cached Truffle runtime handle
                return environment;
            }
        }
        JClass runtimeClass = JNIUtil.GetObjectClass(env, runtimeLocalRef);
        ResolvedJavaType runtimeType = LibGraal.asResolvedJavaType(runtimeClass);
        if (runtimeType == null) {
            throw GraalError.shouldNotReachHere("The object class needs to be available for a Truffle runtime object.");
        }
        /*
         * We do not currently validate the forType. But in the future we want to lookup the runtime
         * per type. So in theory multiple truffle runtimes can be loaded.
         */
        HSTruffleCompilerRuntime runtime = new HSTruffleCompilerRuntime(env, runtimeLocalRef, runtimeType, runtimeClass);
        this.previousRuntime = environment = new LibGraalTruffleHostEnvironment(runtime, HotSpotJVMCIRuntime.runtime().getHostJVMCIBackend().getMetaAccess());
        assert JNIUtil.IsSameObject(env, hsRuntime(environment).getHandle(), runtimeLocalRef);
        return environment;
    }

    private static HSTruffleCompilerRuntime hsRuntime(TruffleHostEnvironment environment) {
        return (HSTruffleCompilerRuntime) environment.runtime();
    }

    static JClass lookupPeer(JNIEnv env) {
        long globalReference = WEAK_TRUFFLE_RUNTIME_INSTANCE.get();
        if (globalReference == NO_TRUFFLE_REGISTERED) {
            // fast path if Truffle was not initialized
            return WordFactory.nullPointer();
        }
        JObject runtimeLocalRef = JNIUtil.NewLocalRef(env, WordFactory.pointer(globalReference));
        if (runtimeLocalRef.isNull()) {
            // The Truffle runtime was collected by the GC
            return WordFactory.nullPointer();
        }
        return JNIUtil.GetObjectClass(env, runtimeLocalRef);
    }

    public static boolean registerRuntime(JNIEnv env, JObject truffleRuntime) {
        // TODO GR-44222 support multiple runtimes.
        JWeak globalRuntimeRef = JNIUtil.NewWeakGlobalRef(env, truffleRuntime, "");
        return WEAK_TRUFFLE_RUNTIME_INSTANCE.compareAndSet(0, globalRuntimeRef.rawValue());
    }

}
