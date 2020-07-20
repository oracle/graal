/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.AsJavaConstant;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.CancelCompilation;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.CompilableToString;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.CreateStringSupplier;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetCallNodes;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetCompilableCallCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetCompilableName;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetFailedSpeculationsAddress;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetKnownCallSiteCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetNodeRewritingAssumptionConstant;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.IsSameOrSplit;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetNonTrivialNodeCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.OnCompilationFailed;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSCompilableTruffleASTGen.callAsJavaConstant;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSCompilableTruffleASTGen.callCancelCompilation;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSCompilableTruffleASTGen.callCompilableToString;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSCompilableTruffleASTGen.callCreateStringSupplier;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSCompilableTruffleASTGen.callGetCompilableCallCount;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSCompilableTruffleASTGen.callGetCallNodes;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSCompilableTruffleASTGen.callGetCompilableName;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSCompilableTruffleASTGen.callGetFailedSpeculationsAddress;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSCompilableTruffleASTGen.callGetKnownCallSiteCount;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSCompilableTruffleASTGen.callGetNodeRewritingAssumptionConstant;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSCompilableTruffleASTGen.callGetNonTrivialNodeCount;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSCompilableTruffleASTGen.callIsSameOrSplit;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSCompilableTruffleASTGen.callOnCompilationFailed;
import static org.graalvm.libgraal.jni.JNILibGraalScope.env;
import static org.graalvm.libgraal.jni.JNILibGraalScope.scope;
import static org.graalvm.libgraal.jni.JNIUtil.createString;

import java.util.function.Supplier;

import org.graalvm.compiler.hotspot.HotSpotGraalServices;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.OptimizedAssumptionDependency;
import org.graalvm.compiler.truffle.common.TruffleCallNode;
import org.graalvm.libgraal.jni.HSObject;
import org.graalvm.libgraal.jni.JNILibGraalScope;
import org.graalvm.libgraal.jni.JNI.JNIEnv;
import org.graalvm.libgraal.jni.JNI.JObject;
import org.graalvm.libgraal.jni.JNI.JObjectArray;
import org.graalvm.libgraal.jni.JNI.JString;
import org.graalvm.libgraal.jni.JNIUtil;
import org.graalvm.libgraal.LibGraal;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.SpeculationLog;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal;

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
    HSCompilableTruffleAST(JNILibGraalScope<TruffleToLibGraal.Id> scope, JObject handle) {
        super(scope, handle);
    }

    @TruffleFromLibGraal(GetFailedSpeculationsAddress)
    @Override
    public SpeculationLog getCompilationSpeculationLog() {
        Long res = cachedFailedSpeculationsAddress;
        if (res == null) {
            res = callGetFailedSpeculationsAddress(env(), getHandle());
            cachedFailedSpeculationsAddress = res;
        }
        return HotSpotGraalServices.newHotSpotSpeculationLog(cachedFailedSpeculationsAddress);
    }

    @TruffleFromLibGraal(GetNodeRewritingAssumptionConstant)
    @Override
    public JavaConstant getNodeRewritingAssumptionConstant() {
        long javaConstantHandle = callGetNodeRewritingAssumptionConstant(env(), getHandle());
        return LibGraal.unhand(JavaConstant.class, javaConstantHandle);
    }

    @TruffleFromLibGraal(AsJavaConstant)
    @Override
    public JavaConstant asJavaConstant() {
        return LibGraal.unhand(JavaConstant.class, callAsJavaConstant(env(), getHandle()));
    }

    @TruffleFromLibGraal(CreateStringSupplier)
    @TruffleFromLibGraal(OnCompilationFailed)
    @Override
    public void onCompilationFailed(Supplier<String> serializedException, boolean bailout, boolean permanentBailout) {
        long serializedExceptionHandle = LibGraalObjectHandles.create(serializedException);
        boolean success = false;
        JNIEnv env = env();
        try {
            JObject instance = callCreateStringSupplier(env, serializedExceptionHandle);
            callOnCompilationFailed(env, getHandle(), instance, bailout, permanentBailout);
            success = true;
        } finally {
            if (!success) {
                LibGraalObjectHandles.remove(serializedExceptionHandle);
            }
        }
    }

    @TruffleFromLibGraal(GetCompilableName)
    @Override
    public String getName() {
        String res = cachedName;
        if (res == null) {
            JNIEnv env = JNILibGraalScope.env();
            JString name = callGetCompilableName(env, getHandle());
            res = createString(env, name);
            cachedName = res;
        }
        return res;
    }

    @TruffleFromLibGraal(GetNonTrivialNodeCount)
    @Override
    public int getNonTrivialNodeCount() {
        return callGetNonTrivialNodeCount(env(), getHandle());
    }

    @TruffleFromLibGraal(GetCallNodes)
    @Override
    public TruffleCallNode[] getCallNodes() {
        JNILibGraalScope<TruffleToLibGraal.Id> scope = scope().narrow(TruffleToLibGraal.Id.class);
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

    @TruffleFromLibGraal(GetCompilableCallCount)
    @Override
    public int getCallCount() {
        return callGetCompilableCallCount(env(), getHandle());
    }

    private volatile String cachedString;

    @TruffleFromLibGraal(CompilableToString)
    @Override
    public String toString() {
        String res = cachedString;
        if (res == null) {
            JNIEnv env = JNILibGraalScope.env();
            JString value = callCompilableToString(env, getHandle());
            res = createString(env, value);
            cachedString = res;
        }
        return res;
    }

    private IllegalArgumentException error() {
        throw new IllegalArgumentException("Cannot call method on libgraal proxy to HotSpotOptimizedCallTarget " + this);
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

    @TruffleFromLibGraal(CancelCompilation)
    @Override
    public boolean cancelCompilation(CharSequence reason) {
        JNIEnv env = env();
        JString jniReason = JNIUtil.createHSString(env, reason.toString());
        return callCancelCompilation(env, getHandle(), jniReason);
    }

    @TruffleFromLibGraal(IsSameOrSplit)
    @Override
    public boolean isSameOrSplit(CompilableTruffleAST ast) {
        JObject astHandle = ((HSCompilableTruffleAST) ast).getHandle();
        return callIsSameOrSplit(env(), getHandle(), astHandle);
    }

    @TruffleFromLibGraal(GetKnownCallSiteCount)
    @Override
    public int getKnownCallSiteCount() {
        return callGetKnownCallSiteCount(env(), getHandle());
    }
}
