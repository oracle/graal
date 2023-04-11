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
package org.graalvm.compiler.truffle.compiler.hotspot.libgraal;

import java.util.Collections;

import org.graalvm.compiler.hotspot.HotSpotGraalOptionValues;
import org.graalvm.compiler.serviceprovider.GlobalAtomicLong;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerImpl;
import org.graalvm.compiler.truffle.compiler.host.TruffleHostEnvironment;
import org.graalvm.compiler.truffle.compiler.hotspot.HotSpotTruffleCompilerImpl;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JObject;
import org.graalvm.jniutils.JNI.JWeak;
import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.jniutils.JNIUtil;
import org.graalvm.libgraal.LibGraal;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class LibGraalTruffleHostEnvironmentLookup implements TruffleHostEnvironment.Lookup {

    private static final int NO_TRUFFLE_REGISTERED = 0;
    private static final GlobalAtomicLong WEAK_RUNTIME_INSTANCE = new GlobalAtomicLong(NO_TRUFFLE_REGISTERED);

    @RecomputeFieldValue(kind = Kind.Reset) private ResolvedJavaField classLoaderField;
    @RecomputeFieldValue(kind = Kind.Reset) private ResolvedJavaField parentClassLoaderField;
    @RecomputeFieldValue(kind = Kind.Reset) private TruffleHostEnvironment previousRuntime;

    @Override
    public TruffleHostEnvironment lookup(ResolvedJavaType forType) {
        long globalReference = WEAK_RUNTIME_INSTANCE.get();
        if (globalReference == NO_TRUFFLE_REGISTERED) {
            // fast path for non-truffle
            return null;
        }
        JNIEnv env = JNIMethodScope.env();
        JObject runtimeLocalRef = JNIUtil.NewLocalRef(env, WordFactory.pointer(globalReference));
        if (runtimeLocalRef.isNull()) {
            // Truffle was freed
            return null;
        }
        TruffleHostEnvironment environment = this.previousRuntime;
        if (environment != null) {
            JObject cached = hsRuntime(environment).getHandle();
            if (JNIUtil.IsSameObject(env, cached, runtimeLocalRef)) {
                // fast path for truffle
                return environment;
            }
        }
        // initialize truffle runtime
        ResolvedJavaType runtimeType = LibGraal.asResolvedJavaType(JNIUtil.GetObjectClass(env, runtimeLocalRef).rawValue());
        if (runtimeType == null) {
            // type cannot be resolved
            return null;
        }
        /*
         * We do not currently validate the forType. But in the future we want to lookup the runtime
         * per type. So in theory multiple truffle runtimes can be loaded.
         */
        HSTruffleCompilerRuntime runtime = new HSTruffleCompilerRuntime(env, runtimeLocalRef, runtimeType, HotSpotGraalOptionValues.defaultOptions());
        this.previousRuntime = environment = new TruffleHostEnvironment(runtime, HotSpotJVMCIRuntime.runtime().getHostJVMCIBackend().getMetaAccess(),
                        LibGraalTruffleHostEnvironmentLookup::createCompiler);
        assert JNIUtil.IsSameObject(env, hsRuntime(environment).getHandle(), runtimeLocalRef);
        return environment;
    }

    static TruffleCompilerImpl createCompiler(TruffleHostEnvironment env, CompilableTruffleAST ast) {
        HotSpotTruffleCompilerImpl compiler = HotSpotTruffleCompilerImpl.create(env.runtime());
        compiler.initialize(Collections.emptyMap(), ast, true);
        return compiler;
    }

    private static HSTruffleCompilerRuntime hsRuntime(TruffleHostEnvironment environment) {
        return (HSTruffleCompilerRuntime) environment.runtime();
    }

    public static boolean registerRuntime(JNIEnv env, JObject runtime) {
        // TODO GR-44222 support multiple runtimes.
        JWeak globalRuntimeRef = JNIUtil.NewWeakGlobalRef(env, runtime, "");
        return WEAK_RUNTIME_INSTANCE.compareAndSet(0, globalRuntimeRef.rawValue());
    }

}
