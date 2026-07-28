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
package jdk.graal.compiler.libgraal.truffle;

import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.AsJavaConstant;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.CanBeInlined;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.CancelCompilation;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.CompilableToString;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.CountDirectCallNodes;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.CreateStringSupplier;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.EngineId;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetCompilableCallCount;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetCompilableName;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetCompilerOptions;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetFailedSpeculationsAddress;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetKnownCallSiteCount;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetNonTrivialNodeCount;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetSuccessfulCompilationCount;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.IsSameOrSplit;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.IsTrivial;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.OnCompilationFailed;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.OnCompilationSuccess;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.PrepareForCompilation;
import static org.graalvm.jniutils.JNIMethodScope.env;
import static org.graalvm.jniutils.JNIUtil.createString;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.graalvm.jniutils.HSObject;
import org.graalvm.jniutils.JNI.JByteArray;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JObject;
import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.jniutils.JNIUtil;

import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.HotSpotGraalServices;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.SpeculationLog;
import org.graalvm.word.impl.Word;

final class HSTruffleCompilable extends HSObject implements TruffleCompilable {

    private final TruffleFromLibGraalCalls calls;
    /**
     * Handle to {@code speculationLog} field of the {@code OptimizedCallTarget}.
     */
    private Long cachedFailedSpeculationsAddress;
    private volatile String cachedName;
    private volatile String cachedString;

    HSTruffleCompilable(JNIMethodScope scope, JObject handle, HSTruffleCompilerRuntime runtime) {
        super(scope, handle);
        this.calls = runtime.calls;
    }

    @TruffleFromLibGraal(GetFailedSpeculationsAddress)
    @Override
    public SpeculationLog getCompilationSpeculationLog() {
        Long res = cachedFailedSpeculationsAddress;
        if (res == null) {
            res = HSTruffleCompilableGen.callGetFailedSpeculationsAddress(calls, env(), getHandle());
            cachedFailedSpeculationsAddress = res;
        }
        return HotSpotGraalServices.newHotSpotSpeculationLog(cachedFailedSpeculationsAddress);
    }

    @TruffleFromLibGraal(GetCompilerOptions)
    @Override
    public Map<String, String> getCompilerOptions() {
        JNIEnv env = JNIMethodScope.env();
        JByteArray res = HSTruffleCompilableGen.callGetCompilerOptions(calls, env, getHandle());
        BinaryInput in = BinaryInput.create(JNIUtil.createArray(env, res));
        int size = in.readInt();
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            String key = in.readUTF();
            map.put(key, in.readUTF());
        }
        return map;
    }

    @TruffleFromLibGraal(EngineId)
    @Override
    public long engineId() {
        return HSTruffleCompilableGen.callEngineId(calls, env(), getHandle());
    }

    @TruffleFromLibGraal(PrepareForCompilation)
    @Override
    public boolean prepareForCompilation(boolean rootCompilation, int compilationTier, boolean lastTier) {
        return HSTruffleCompilableGen.callPrepareForCompilation(calls, env(), getHandle(), rootCompilation, compilationTier, lastTier);
    }

    @TruffleFromLibGraal(GetSuccessfulCompilationCount)
    @Override
    public int getSuccessfulCompilationCount() {
        return HSTruffleCompilableGen.callGetSuccessfulCompilationCount(calls, env(), getHandle());
    }

    @TruffleFromLibGraal(CanBeInlined)
    @Override
    public boolean canBeInlined() {
        return HSTruffleCompilableGen.callCanBeInlined(calls, env(), getHandle());
    }

    @TruffleFromLibGraal(IsTrivial)
    @Override
    public boolean isTrivial() {
        return HSTruffleCompilableGen.callIsTrivial(calls, env(), getHandle());
    }

    @TruffleFromLibGraal(AsJavaConstant)
    @Override
    public JavaConstant asJavaConstant() {
        long constantHandle = HSTruffleCompilableGen.callAsJavaConstant(calls, env(), getHandle());
        return HotSpotJVMCIRuntime.runtime().unhand(JavaConstant.class, constantHandle);
    }

    @TruffleFromLibGraal(CreateStringSupplier)
    @TruffleFromLibGraal(OnCompilationFailed)
    @Override
    public void onCompilationFailed(Supplier<String> serializedException, boolean suppressed, boolean bailout, boolean permanentBailout, boolean graphTooBig) {
        long serializedExceptionHandle = LibGraalObjectHandles.create(serializedException);
        boolean success = false;
        JNIEnv env = env();
        try {
            JObject instance = HSTruffleCompilableGen.callCreateStringSupplier(calls, env, serializedExceptionHandle);
            HSTruffleCompilableGen.callOnCompilationFailed(calls, env, getHandle(), instance, suppressed, bailout, permanentBailout, graphTooBig);
            success = true;
        } finally {
            if (!success) {
                LibGraalObjectHandles.remove(serializedExceptionHandle);
            }
        }
    }

    @TruffleFromLibGraal(OnCompilationSuccess)
    @Override
    public void onCompilationSuccess(int compilationTier, boolean lastTier) {
        HSTruffleCompilableGen.callOnCompilationSuccess(calls, env(), getHandle(), compilationTier, lastTier);
    }

    @Override
    public boolean onInvalidate(Object source, CharSequence reason, boolean wasActive) {
        throw GraalError.shouldNotReachHere("Should not be reachable."); // ExcludeFromJacocoGeneratedReport
    }

    @TruffleFromLibGraal(GetCompilableName)
    @Override
    public String getName() {
        String res = cachedName;
        if (res == null) {
            JNIEnv env = JNIMethodScope.env();
            res = JNIUtil.createString(env, HSTruffleCompilableGen.callGetCompilableName(calls, env, getHandle()));
            cachedName = res;
        }
        return res;
    }

    @TruffleFromLibGraal(CompilableToString)
    @Override
    public String toString() {
        String res = cachedString;
        if (res == null) {
            JNIEnv env = JNIMethodScope.env();
            res = createString(env, HSTruffleCompilableGen.callCompilableToString(calls, env, getHandle()));
            cachedString = res;
        }
        return res;
    }

    @TruffleFromLibGraal(GetNonTrivialNodeCount)
    @Override
    public int getNonTrivialNodeCount() {
        return HSTruffleCompilableGen.callGetNonTrivialNodeCount(calls, env(), getHandle());
    }

    @TruffleFromLibGraal(CountDirectCallNodes)
    @Override
    public int countDirectCallNodes() {
        return HSTruffleCompilableGen.callCountDirectCallNodes(calls, env(), getHandle());
    }

    @TruffleFromLibGraal(GetCompilableCallCount)
    @Override
    public int getCallCount() {
        return HSTruffleCompilableGen.callGetCompilableCallCount(calls, env(), getHandle());
    }

    @TruffleFromLibGraal(CancelCompilation)
    @Override
    public boolean cancelCompilation(CharSequence reason) {
        JNIEnv env = env();
        return HSTruffleCompilableGen.callCancelCompilation(calls, env, getHandle(), JNIUtil.createHSString(env, reason.toString()));
    }

    @TruffleFromLibGraal(IsSameOrSplit)
    @Override
    public boolean isSameOrSplit(TruffleCompilable ast) {
        JObject astHandle = ast == null ? Word.nullPointer() : ((HSTruffleCompilable) ast).getHandle();
        return HSTruffleCompilableGen.callIsSameOrSplit(calls, env(), getHandle(), astHandle);
    }

    @TruffleFromLibGraal(GetKnownCallSiteCount)
    @Override
    public int getKnownCallSiteCount() {
        return HSTruffleCompilableGen.callGetKnownCallSiteCount(calls, env(), getHandle());
    }
}
