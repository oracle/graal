/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.CancelInstalledTask;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.CompilableToString;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.CreateStringSupplier;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetCallNodes;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetCompilableCallCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetCompilableName;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetFailedSpeculationsAddress;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetKnownCallSiteCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.IsSameOrSplit;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetNonTrivialNodeCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.OnCompilationFailed;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSCompilableTruffleASTGen.callAsJavaConstant;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSCompilableTruffleASTGen.callCancelInstalledTask;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSCompilableTruffleASTGen.callCompilableToString;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSCompilableTruffleASTGen.callCreateStringSupplier;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSCompilableTruffleASTGen.callGetCompilableCallCount;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSCompilableTruffleASTGen.callGetCallNodes;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSCompilableTruffleASTGen.callGetCompilableName;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSCompilableTruffleASTGen.callGetFailedSpeculationsAddress;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSCompilableTruffleASTGen.callGetKnownCallSiteCount;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSCompilableTruffleASTGen.callGetNonTrivialNodeCount;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSCompilableTruffleASTGen.callIsSameOrSplit;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSCompilableTruffleASTGen.callOnCompilationFailed;
import static org.graalvm.libgraal.jni.HotSpotToSVMScope.env;
import static org.graalvm.libgraal.jni.HotSpotToSVMScope.scope;
import static org.graalvm.libgraal.jni.JNIUtil.createString;

import java.util.function.Supplier;

import org.graalvm.compiler.hotspot.HotSpotGraalServices;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.OptimizedAssumptionDependency;
import org.graalvm.compiler.truffle.common.TruffleCallNode;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot;
import org.graalvm.libgraal.jni.HSObject;
import org.graalvm.libgraal.jni.HotSpotToSVMScope;
import org.graalvm.libgraal.jni.JNI.JNIEnv;
import org.graalvm.libgraal.jni.JNI.JObject;
import org.graalvm.libgraal.jni.JNI.JObjectArray;
import org.graalvm.libgraal.jni.JNI.JString;
import org.graalvm.libgraal.jni.JNIUtil;
import org.graalvm.libgraal.LibGraal;

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
    HSCompilableTruffleAST(HotSpotToSVMScope<HotSpotToSVM.Id> scope, JObject handle) {
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
        return HotSpotGraalServices.newHotSpotSpeculationLog(cachedFailedSpeculationsAddress);
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

    @SVMToHotSpot(GetNonTrivialNodeCount)
    @Override
    public int getNonTrivialNodeCount() {
        return callGetNonTrivialNodeCount(env(), getHandle());
    }

    @SVMToHotSpot(GetCallNodes)
    @Override
    public TruffleCallNode[] getCallNodes() {
        HotSpotToSVMScope<HotSpotToSVM.Id> scope = scope().narrow(HotSpotToSVM.Id.class);
        JNIEnv env = scope.getEnv();
        JObjectArray peerArr = callGetCallNodes(env, getHandle());
        int len = JNIUtil.GetArrayLength(env, peerArr);
        TruffleCallNode[] res = new TruffleCallNode[len];
        for (int i = 0; i < len; i++) {
            JObject peerTruffleCallNode = JNIUtil.GetObjectArrayElement(env, peerArr, i);
            res[i] = new HSTruffleCallNode(scope, peerTruffleCallNode);
        }
        return res;
    }

    @SVMToHotSpot(GetCompilableCallCount)
    @Override
    public int getCallCount() {
        return callGetCompilableCallCount(env(), getHandle());
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
        throw error();
    }

    @Override
    public boolean isValid() {
        throw error();
    }

    @SVMToHotSpot(CancelInstalledTask)
    @Override
    public void cancelInstalledTask() {
        callCancelInstalledTask(env(), getHandle());
    }

    @SVMToHotSpot(IsSameOrSplit)
    @Override
    public boolean isSameOrSplit(CompilableTruffleAST ast) {
        JObject astHandle = ((HSCompilableTruffleAST) ast).getHandle();
        return callIsSameOrSplit(env(), getHandle(), astHandle);
    }

    @SVMToHotSpot(GetKnownCallSiteCount)
    @Override
    public int getKnownCallSiteCount() {
        return callGetKnownCallSiteCount(env(), getHandle());
    }
}
