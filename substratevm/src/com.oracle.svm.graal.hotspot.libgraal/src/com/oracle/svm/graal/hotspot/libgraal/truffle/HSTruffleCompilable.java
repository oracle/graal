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
package com.oracle.svm.graal.hotspot.libgraal.truffle;

import static com.oracle.svm.graal.hotspot.libgraal.truffle.HSTruffleCompilableGen.callAsJavaConstant;
import static com.oracle.svm.graal.hotspot.libgraal.truffle.HSTruffleCompilableGen.callCancelCompilation;
import static com.oracle.svm.graal.hotspot.libgraal.truffle.HSTruffleCompilableGen.callCompilableToString;
import static com.oracle.svm.graal.hotspot.libgraal.truffle.HSTruffleCompilableGen.callCountDirectCallNodes;
import static com.oracle.svm.graal.hotspot.libgraal.truffle.HSTruffleCompilableGen.callCreateStringSupplier;
import static com.oracle.svm.graal.hotspot.libgraal.truffle.HSTruffleCompilableGen.callGetCompilableCallCount;
import static com.oracle.svm.graal.hotspot.libgraal.truffle.HSTruffleCompilableGen.callGetCompilableName;
import static com.oracle.svm.graal.hotspot.libgraal.truffle.HSTruffleCompilableGen.callGetFailedSpeculationsAddress;
import static com.oracle.svm.graal.hotspot.libgraal.truffle.HSTruffleCompilableGen.callGetKnownCallSiteCount;
import static com.oracle.svm.graal.hotspot.libgraal.truffle.HSTruffleCompilableGen.callGetNonTrivialNodeCount;
import static com.oracle.svm.graal.hotspot.libgraal.truffle.HSTruffleCompilableGen.callIsSameOrSplit;
import static com.oracle.svm.graal.hotspot.libgraal.truffle.HSTruffleCompilableGen.callIsTrivial;
import static com.oracle.svm.graal.hotspot.libgraal.truffle.HSTruffleCompilableGen.callOnCompilationFailed;
import static com.oracle.svm.graal.hotspot.libgraal.truffle.HSTruffleCompilableGen.callPrepareForCompilation;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.AsJavaConstant;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.CancelCompilation;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.CompilableToString;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.CreateStringSupplier;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetCompilableCallCount;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetCompilableName;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetFailedSpeculationsAddress;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetKnownCallSiteCount;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetNonTrivialNodeCount;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.IsSameOrSplit;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.IsTrivial;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.OnCompilationFailed;
import static org.graalvm.jniutils.JNIMethodScope.env;
import static org.graalvm.jniutils.JNIUtil.createString;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.HotSpotGraalServices;
import org.graalvm.jniutils.HSObject;
import org.graalvm.jniutils.JNI;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JObject;
import org.graalvm.jniutils.JNI.JString;
import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.jniutils.JNIUtil;
import org.graalvm.nativebridge.BinaryInput;

import com.oracle.svm.graal.hotspot.libgraal.LibGraal;
import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal;
import com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * Proxy for a {@code HotSpotOptimizedCallTarget} object in the HotSpot heap.
 */
final class HSTruffleCompilable extends HSObject implements TruffleCompilable {

    private final TruffleFromLibGraalCalls calls;
    private volatile String cachedName;

    /**
     * Handle to {@code speculationLog} field of the {@code OptimizedCallTarget}.
     */
    private Long cachedFailedSpeculationsAddress;

    /**
     * Creates a new {@link HSTruffleCompilable} holding the JNI {@code JObject} by a local
     * reference.
     *
     * @param scope the owning scope
     * @param handle the JNI object reference
     */
    HSTruffleCompilable(JNIMethodScope scope, JObject handle, HSTruffleCompilerRuntime runtime) {
        super(scope, handle);
        this.calls = runtime.calls;
    }

    @TruffleFromLibGraal(GetFailedSpeculationsAddress)
    @Override
    public SpeculationLog getCompilationSpeculationLog() {
        Long res = cachedFailedSpeculationsAddress;
        if (res == null) {
            res = callGetFailedSpeculationsAddress(calls, env(), getHandle());
            cachedFailedSpeculationsAddress = res;
        }
        return HotSpotGraalServices.newHotSpotSpeculationLog(cachedFailedSpeculationsAddress);
    }

    @Override
    @TruffleFromLibGraal(Id.GetCompilerOptions)
    public Map<String, String> getCompilerOptions() {
        JNIEnv env = env();
        JNI.JByteArray res = HSTruffleCompilableGen.callGetCompilerOptions(calls, env, getHandle());
        byte[] realArray = JNIUtil.createArray(env, res);
        return readDebugMap(BinaryInput.create(realArray));
    }

    private static Map<String, String> readDebugMap(BinaryInput in) {
        int size = in.readInt();
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            String key = in.readUTF();
            map.put(key, in.readUTF());
        }
        return map;
    }

    @Override
    @TruffleFromLibGraal(Id.EngineId)
    public long engineId() {
        return HSTruffleCompilableGen.callEngineId(calls, env(), getHandle());
    }

    @Override
    @TruffleFromLibGraal(Id.PrepareForCompilation)
    public void prepareForCompilation() {
        callPrepareForCompilation(calls, env(), getHandle());
    }

    @TruffleFromLibGraal(IsTrivial)
    @Override
    public boolean isTrivial() {
        return callIsTrivial(calls, env(), getHandle());
    }

    @TruffleFromLibGraal(AsJavaConstant)
    @Override
    public JavaConstant asJavaConstant() {
        return LibGraal.unhand(JavaConstant.class, callAsJavaConstant(calls, env(), getHandle()));
    }

    @TruffleFromLibGraal(CreateStringSupplier)
    @TruffleFromLibGraal(OnCompilationFailed)
    @Override
    public void onCompilationFailed(Supplier<String> serializedException, boolean silent, boolean bailout, boolean permanentBailout, boolean graphTooBig) {
        long serializedExceptionHandle = LibGraalObjectHandles.create(serializedException);
        boolean success = false;
        JNIEnv env = env();
        try {
            JObject instance = callCreateStringSupplier(calls, env, serializedExceptionHandle);
            callOnCompilationFailed(calls, env, getHandle(), instance, silent, bailout, permanentBailout, graphTooBig);
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
            JNIEnv env = JNIMethodScope.env();
            JString name = callGetCompilableName(calls, env, getHandle());
            res = createString(env, name);
            cachedName = res;
        }
        return res;
    }

    @TruffleFromLibGraal(GetNonTrivialNodeCount)
    @Override
    public int getNonTrivialNodeCount() {
        return callGetNonTrivialNodeCount(calls, env(), getHandle());
    }

    @TruffleFromLibGraal(Id.CountDirectCallNodes)
    @Override
    public int countDirectCallNodes() {
        return callCountDirectCallNodes(calls, env(), getHandle());
    }

    @TruffleFromLibGraal(GetCompilableCallCount)
    @Override
    public int getCallCount() {
        return callGetCompilableCallCount(calls, env(), getHandle());
    }

    private volatile String cachedString;

    @TruffleFromLibGraal(CompilableToString)
    @Override
    public String toString() {
        String res = cachedString;
        if (res == null) {
            JNIEnv env = JNIMethodScope.env();
            JString value = callCompilableToString(calls, env, getHandle());
            res = createString(env, value);
            cachedString = res;
        }
        return res;
    }

    @TruffleFromLibGraal(CancelCompilation)
    @Override
    public boolean cancelCompilation(CharSequence reason) {
        JNIEnv env = env();
        JString jniReason = JNIUtil.createHSString(env, reason.toString());
        return callCancelCompilation(calls, env, getHandle(), jniReason);
    }

    @TruffleFromLibGraal(IsSameOrSplit)
    @Override
    public boolean isSameOrSplit(TruffleCompilable ast) {
        JObject astHandle = ((HSTruffleCompilable) ast).getHandle();
        return callIsSameOrSplit(calls, env(), getHandle(), astHandle);
    }

    @TruffleFromLibGraal(GetKnownCallSiteCount)
    @Override
    public int getKnownCallSiteCount() {
        return callGetKnownCallSiteCount(calls, env(), getHandle());
    }

    @Override
    public boolean onInvalidate(Object source, CharSequence reason, boolean wasActive) {
        throw GraalError.shouldNotReachHere("Should not be reachable."); // ExcludeFromJacocoGeneratedReport
    }
}
