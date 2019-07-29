/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.AsJavaConstant;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.CompilableToString;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.CreateStringSupplier;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetCompilableName;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetFailedSpeculationsAddress;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.OnCompilationFailed;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSCompilableTruffleASTGen.callAsJavaConstant;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSCompilableTruffleASTGen.callCompilableToString;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSCompilableTruffleASTGen.callCreateStringSupplier;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSCompilableTruffleASTGen.callGetCompilableName;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSCompilableTruffleASTGen.callGetFailedSpeculationsAddress;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSCompilableTruffleASTGen.callOnCompilationFailed;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIUtil.createString;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HotSpotToSVMScope.env;

import java.util.function.Supplier;

import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.OptimizedAssumptionDependency;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JNIEnv;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JObject;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JString;
import org.graalvm.libgraal.LibGraal;

import jdk.vm.ci.hotspot.HotSpotSpeculationLog;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * Proxy for a {@code HotSpotOptimizedCallTarget} object in the HotSpot heap.
 */
final class HSCompilableTruffleAST extends HSObject implements CompilableTruffleAST, OptimizedAssumptionDependency {

    private volatile String cachedName;

    /**
     * Handle to {@code speculationLog} field of the {@code OptimizedCallTarget}.
     */
    private Long cachedFailedSpeculationsAddress;

    /**
     * Creates a new {@link HSCompilableTruffleAST} holding the JNI {@code JObject} by a global
     * reference.
     *
     * @param env the JNIEnv
     * @param handle the JNI object reference
     */
    HSCompilableTruffleAST(JNIEnv env, JObject handle) {
        super(env, handle);
    }

    /**
     * Creates a new {@link HSCompilableTruffleAST} holding the JNI {@code JObject} by a local
     * reference.
     *
     * @param scope the owning scope
     * @param handle the JNI object reference
     */
    HSCompilableTruffleAST(HotSpotToSVMScope scope, JObject handle) {
        super(scope, handle);
    }

    @SVMToHotSpot(GetFailedSpeculationsAddress)
    @Override
    public SpeculationLog getCompilationSpeculationLog() {
        Long res = cachedFailedSpeculationsAddress;
        if (res == null) {
            res = callGetFailedSpeculationsAddress(env(), getHandle());
            cachedFailedSpeculationsAddress = res;
        }
        return new HotSpotSpeculationLog(cachedFailedSpeculationsAddress);
    }

    @SVMToHotSpot(AsJavaConstant)
    @Override
    public JavaConstant asJavaConstant() {
        return LibGraal.unhand(runtime(), JavaConstant.class, callAsJavaConstant(env(), getHandle()));
    }

    @SVMToHotSpot(CreateStringSupplier)
    @SVMToHotSpot(OnCompilationFailed)
    @Override
    public void onCompilationFailed(Supplier<String> reasonAndStackTrace, boolean bailout, boolean permanentBailout) {
        long reasonAndStackTraceHandle = SVMObjectHandles.create(reasonAndStackTrace);
        boolean success = false;
        JNIEnv env = env();
        try {
            JObject instance = callCreateStringSupplier(env, reasonAndStackTraceHandle);
            callOnCompilationFailed(env, getHandle(), instance, bailout, permanentBailout);
            success = true;
        } finally {
            if (!success) {
                SVMObjectHandles.remove(reasonAndStackTraceHandle);
            }
        }
    }

    @SVMToHotSpot(GetCompilableName)
    @Override
    public String getName() {
        String res = cachedName;
        if (res == null) {
            JNIEnv env = HotSpotToSVMScope.env();
            JString name = callGetCompilableName(env, getHandle());
            res = createString(env, name);
            cachedName = res;
        }
        return res;
    }

    private volatile String cachedString;

    @SVMToHotSpot(CompilableToString)
    @Override
    public String toString() {
        String res = cachedString;
        if (res == null) {
            JNIEnv env = HotSpotToSVMScope.env();
            JString value = callCompilableToString(env, getHandle());
            res = createString(env, value);
            cachedString = res;
        }
        return res;
    }

    private IllegalArgumentException error() {
        throw new IllegalArgumentException("Cannot call method on SVM proxy to HotSpotOptimizedCallTarget " + this);
    }

    @Override
    public CompilableTruffleAST getCompilable() {
        return this;
    }

    @Override
    public void invalidate() {
        error();
    }

    @Override
    public boolean isValid() {
        error();
        return false;
    }
}
