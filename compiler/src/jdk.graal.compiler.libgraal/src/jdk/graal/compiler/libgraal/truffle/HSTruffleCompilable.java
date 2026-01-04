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
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.IsSameOrSplit;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.IsTrivial;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.OnCompilationFailed;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.PrepareForCompilation;
import static org.graalvm.jniutils.JNIMethodScope.env;
import static org.graalvm.jniutils.JNIUtil.createString;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.graalvm.jniutils.HSObject;
import org.graalvm.jniutils.JNI;
import org.graalvm.jniutils.JNI.JByteArray;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JObject;
import org.graalvm.jniutils.JNICalls;
import org.graalvm.jniutils.JNICalls.JNIMethod;
import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.jniutils.JNIUtil;
import org.graalvm.nativeimage.StackValue;

import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.HotSpotGraalServices;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.SpeculationLog;

final class HSTruffleCompilable extends HSObject implements TruffleCompilable {

    private static volatile JNIMethod prepareForCompilationNewMethod;
    private static volatile JNIMethod getSuccessfulCompilationCountMethod;
    private static volatile JNIMethod onCompilationSuccessMethod;
    private static volatile JNIMethod canBeInlinedMethod;

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
        JNIEnv env = JNIMethodScope.env();
        JNIMethod newMethod = findPrepareForCompilationNewMethod(env);
        if (newMethod != null) {
            return callPrepareForCompilationNew(newMethod, env, getHandle(), rootCompilation, compilationTier, lastTier);
        } else {
            HSTruffleCompilableGen.callPrepareForCompilation(calls, env(), getHandle());
            return true;
        }
    }

    private JNIMethod findPrepareForCompilationNewMethod(JNIEnv env) {
        JNIMethod res = prepareForCompilationNewMethod;
        if (res == null) {
            res = calls.findJNIMethod(env, "prepareForCompilation", boolean.class, Object.class, boolean.class, int.class, boolean.class);
            prepareForCompilationNewMethod = res;
        }
        return res.getJMethodID().isNonNull() ? res : null;
    }

    private boolean callPrepareForCompilationNew(JNIMethod method, JNIEnv env, JObject p0, boolean p1, int p2, boolean p3) {
        JNI.JValue args = StackValue.get(4, JNI.JValue.class);
        args.addressOf(0).setJObject(p0);
        args.addressOf(1).setBoolean(p1);
        args.addressOf(2).setInt(p2);
        args.addressOf(3).setBoolean(p3);
        return calls.getJNICalls().callStaticBoolean(env, calls.getPeer(), method, args);
    }

    @Override
    public int getSuccessfulCompilationCount() {
        JNIEnv env = JNIMethodScope.env();
        JNIMethod method = findGetSuccessfulCompilationCountMethod(env);
        if (method != null) {
            return callGetSuccessfulCompilationCountMethod(method, env, getHandle());
        }
        return 0;
    }

    private JNIMethod findGetSuccessfulCompilationCountMethod(JNIEnv env) {
        JNIMethod res = getSuccessfulCompilationCountMethod;
        if (res == null) {
            res = calls.findJNIMethod(env, "getSuccessfulCompilationCount", int.class, Object.class);
            getSuccessfulCompilationCountMethod = res;
        }
        return res.getJMethodID().isNonNull() ? res : null;
    }

    private int callGetSuccessfulCompilationCountMethod(JNIMethod method, JNIEnv env, JObject p0) {
        JNI.JValue args = StackValue.get(1, JNI.JValue.class);
        args.addressOf(0).setJObject(p0);
        return calls.getJNICalls().callStaticInt(env, calls.getPeer(), method, args);
    }

    @Override
    public boolean canBeInlined() {
        JNIEnv env = JNIMethodScope.env();
        JNIMethod method = findCanBeInlinedMethod(env);
        if (method != null) {
            return callCanBeInlinedMethod(method, env, getHandle());
        }
        return true;
    }

    private JNIMethod findCanBeInlinedMethod(JNIEnv env) {
        JNIMethod res = canBeInlinedMethod;
        if (res == null) {
            res = calls.findJNIMethod(env, "canBeInlined", boolean.class, Object.class);
            canBeInlinedMethod = res;
        }
        return res.getJMethodID().isNonNull() ? res : null;
    }

    private boolean callCanBeInlinedMethod(JNIMethod method, JNIEnv env, JObject p0) {
        JNI.JValue args = StackValue.get(1, JNI.JValue.class);
        args.addressOf(0).setJObject(p0);
        return calls.getJNICalls().callStaticBoolean(env, calls.getPeer(), method, args);
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

    @Override
    public void onCompilationSuccess(int compilationTier, boolean lastTier) {
        JNIEnv env = JNIMethodScope.env();
        JNICalls.JNIMethod methodOrNull = findOnCompilationSuccessMethod(env);
        if (methodOrNull != null) {
            JNI.JValue args = StackValue.get(3, JNI.JValue.class);
            args.addressOf(0).setJObject(getHandle());
            args.addressOf(1).setInt(compilationTier);
            args.addressOf(2).setBoolean(lastTier);
            calls.getJNICalls().callStaticVoid(env, calls.getPeer(), methodOrNull, args);
        }
    }

    private JNIMethod findOnCompilationSuccessMethod(JNIEnv env) {
        JNIMethod res = onCompilationSuccessMethod;
        if (res == null) {
            res = calls.findJNIMethod(env, "onCompilationSuccess", void.class, Object.class, int.class, boolean.class);
            onCompilationSuccessMethod = res;
        }
        return res.getJMethodID().isNonNull() ? res : null;
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
