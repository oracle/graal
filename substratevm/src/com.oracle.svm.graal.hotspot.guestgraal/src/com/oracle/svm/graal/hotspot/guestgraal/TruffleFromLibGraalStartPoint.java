/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hotspot.guestgraal;

import com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal;
import org.graalvm.jniutils.HSObject;
import org.graalvm.jniutils.JNI.JClass;
import org.graalvm.jniutils.JNI.JObject;
import org.graalvm.jniutils.JNI.JByteArray;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JavaVM;
import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.jniutils.JNIUtil;
import org.graalvm.nativebridge.BinaryInput;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.WordFactory;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callAddInlinedTarget;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callAddTargetToDequeue;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callAsJavaConstant;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callCancelCompilation;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callCompilableToString;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callConsumeOptimizedAssumptionDependency;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callCountDirectCallNodes;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callCreateStringSupplier;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callEngineId;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callGetCompilableCallCount;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callGetCompilableName;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callGetCompilerOptions;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callGetConstantFieldInfo;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callGetDebugProperties;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callGetDescription;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callGetFailedSpeculationsAddress;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callGetHostMethodInfo;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callGetKnownCallSiteCount;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callGetLanguage;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callGetLineNumber;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callGetNodeClassName;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callGetNodeId;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callGetNonTrivialNodeCount;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callGetOffsetEnd;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callGetOffsetStart;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callGetPartialEvaluationMethodInfo;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callGetPosition;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callGetURI;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callHasNextTier;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callIsCancelled;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callIsLastTier;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callIsSameOrSplit;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callIsSuppressedFailure;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callIsTrivial;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callIsValueType;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callLog;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callOnCodeInstallation;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callOnCompilationFailed;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callOnCompilationRetry;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callOnFailure;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callOnGraalTierFinished;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callOnIsolateShutdown;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callOnSuccess;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callOnTruffleTierFinished;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callPrepareForCompilation;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callRegisterOptimizedAssumptionDependency;
import static com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPointGen.callSetCallCounts;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.AddInlinedTarget;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.AddTargetToDequeue;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.AsJavaConstant;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.CancelCompilation;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.CompilableToString;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.ConsumeOptimizedAssumptionDependency;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.CountDirectCallNodes;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.CreateStringSupplier;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.EngineId;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetCompilableCallCount;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetCompilableName;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetCompilerOptions;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetConstantFieldInfo;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetDebugProperties;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetDescription;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetFailedSpeculationsAddress;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetHostMethodInfo;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetKnownCallSiteCount;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetLanguage;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetLineNumber;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetNodeClassName;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetNodeId;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetNonTrivialNodeCount;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetOffsetEnd;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetOffsetStart;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetPartialEvaluationMethodInfo;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetPosition;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetURI;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.HasNextTier;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.IsCancelled;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.IsLastTier;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.IsSameOrSplit;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.IsSuppressedFailure;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.IsTrivial;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.IsValueType;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.Log;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.OnCodeInstallation;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.OnCompilationFailed;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.OnCompilationRetry;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.OnFailure;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.OnGraalTierFinished;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.OnIsolateShutdown;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.OnSuccess;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.OnTruffleTierFinished;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.PrepareForCompilation;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.RegisterOptimizedAssumptionDependency;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.SetCallCounts;
import static org.graalvm.jniutils.JNIMethodScope.env;
import static org.graalvm.jniutils.JNIUtil.createString;

// TODO: Should be generated by annotation processor
/**
 * JNI calls to HotSpot called by guest Graal using method handles.
 */
public final class TruffleFromLibGraalStartPoint {

    private static TruffleFromLibGraalCalls calls;
    private static JavaVM javaVM;

    private TruffleFromLibGraalStartPoint() {
    }

    static void initializeJNI(JClass runtimeClass) {
        if (calls == null) {
            calls = new TruffleFromLibGraalCalls(JNIMethodScope.env(), runtimeClass);
            JavaVM vm = JNIUtil.GetJavaVM(JNIMethodScope.env());
            assert javaVM.isNull() || javaVM.equal(vm);
            javaVM = vm;
        }
    }

    @TruffleFromLibGraal(OnIsolateShutdown)
    public static void onIsolateShutdown(long isolateId) {
        JNIEnv env = JNIUtil.GetEnv(javaVM);
        callOnIsolateShutdown(calls, env, isolateId);
    }

    @TruffleFromLibGraal(GetPartialEvaluationMethodInfo)
    public static byte[] getPartialEvaluationMethodInfo(Object hsHandle, long methodHandle) {
        JNIEnv env = JNIMethodScope.env();
        JByteArray hsByteArray = callGetPartialEvaluationMethodInfo(calls, env, ((HSObject) hsHandle).getHandle(), methodHandle);
        CCharPointer buffer = StackValue.get(5);
        JNIUtil.GetByteArrayRegion(env(), hsByteArray, 0, 5, buffer);
        BinaryInput in = BinaryInput.create(buffer, 5);
        return new byte[]{
                        in.readByte(),
                        in.readByte(),
                        in.readByte(),
                        (byte) (in.readBoolean() ? 1 : 0),
                        (byte) (in.readBoolean() ? 1 : 0),
        };
    }

    @TruffleFromLibGraal(GetHostMethodInfo)
    public static boolean[] getHostMethodInfo(Object hsHandle, long methodHandle) {
        JNIEnv env = JNIMethodScope.env();
        JByteArray hsByteArray = callGetHostMethodInfo(calls, env, ((HSObject) hsHandle).getHandle(), methodHandle);
        CCharPointer buffer = StackValue.get(4);
        JNIUtil.GetByteArrayRegion(env(), hsByteArray, 0, 4, buffer);
        BinaryInput in = BinaryInput.create(buffer, 4);
        boolean[] result = new boolean[4];
        for (int i = 0; i < result.length; i++) {
            result[i] = in.readBoolean();
        }
        return result;
    }

    @TruffleFromLibGraal(OnCodeInstallation)
    public static void onCodeInstallation(Object hsHandle, Object compilableHsHandle, long installedCodeHandle) {
        JNIEnv env = JNIMethodScope.env();
        callOnCodeInstallation(calls, env, ((HSObject) hsHandle).getHandle(), ((HSObject) compilableHsHandle).getHandle(), installedCodeHandle);
    }

    @TruffleFromLibGraal(RegisterOptimizedAssumptionDependency)
    public static Object registerOptimizedAssumptionDependency(Object hsHandle, long optimizedAssumptionHandle) {
        JNIMethodScope scope = JNIMethodScope.scope();
        JObject assumptionConsumer = callRegisterOptimizedAssumptionDependency(calls, scope.getEnv(), ((HSObject) hsHandle).getHandle(), optimizedAssumptionHandle);
        return assumptionConsumer.isNull() ? null : new HSObject(scope, assumptionConsumer);
    }

    @TruffleFromLibGraal(IsValueType)
    public static boolean isValueType(Object hsHandle, long typeHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callIsValueType(calls, env, ((HSObject) hsHandle).getHandle(), typeHandle);
    }

    @TruffleFromLibGraal(GetConstantFieldInfo)
    public static int getConstantFieldInfo(Object hsHandle, long typeHandle, boolean isStatic, int fieldIndex) {
        JNIEnv env = JNIMethodScope.env();
        return callGetConstantFieldInfo(calls, env, ((HSObject) hsHandle).getHandle(), typeHandle, isStatic, fieldIndex);
    }

    @TruffleFromLibGraal(Log)
    public static void log(Object hsHandle, String loggerId, Object compilableHsHandle, String message) {
        JNIEnv env = JNIMethodScope.env();
        callLog(calls, env, ((HSObject) hsHandle).getHandle(), JNIUtil.createHSString(env, loggerId), ((HSObject) compilableHsHandle).getHandle(), JNIUtil.createHSString(env, message));
    }

    @TruffleFromLibGraal(CreateStringSupplier)
    public static Object createStringSupplier(Object serializedException) {
        JNIMethodScope scope = JNIMethodScope.scope();
        long serializedExceptionHandle = LibGraalObjectHandles.create(serializedException);
        return new HSObject(scope, callCreateStringSupplier(calls, scope.getEnv(), serializedExceptionHandle));
    }

    @TruffleFromLibGraal(IsSuppressedFailure)
    public static boolean isSuppressedFailure(Object hsHandle, Object compilableHsHandle, Object supplierHsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callIsSuppressedFailure(calls, env, ((HSObject) hsHandle).getHandle(), ((HSObject) compilableHsHandle).getHandle(), ((HSObject) supplierHsHandle).getHandle());
    }

    @TruffleFromLibGraal(GetFailedSpeculationsAddress)
    public static long getFailedSpeculationsAddress(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callGetFailedSpeculationsAddress(calls, env, ((HSObject) hsHandle).getHandle());
    }

    @TruffleFromLibGraal(GetCompilerOptions)
    public static Map<String, String> getCompilerOptions(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        JByteArray res = callGetCompilerOptions(calls, env, ((HSObject) hsHandle).getHandle());
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
    public static long engineId(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callEngineId(calls, env, ((HSObject) hsHandle).getHandle());
    }

    @TruffleFromLibGraal(PrepareForCompilation)
    public static void prepareForCompilation(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        callPrepareForCompilation(calls, env, ((HSObject) hsHandle).getHandle());
    }

    @TruffleFromLibGraal(IsTrivial)
    public static boolean isTrivial(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callIsTrivial(calls, env, ((HSObject) hsHandle).getHandle());
    }

    @TruffleFromLibGraal(AsJavaConstant)
    public static long asJavaConstant(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callAsJavaConstant(calls, env, ((HSObject) hsHandle).getHandle());
    }

    @TruffleFromLibGraal(OnCompilationFailed)
    public static void onCompilationFailed(Object hsHandle, Object serializedExceptionHsHandle, boolean suppressed, boolean bailout, boolean permanentBailout, boolean graphTooBig) {
        JNIEnv env = JNIMethodScope.env();
        callOnCompilationFailed(calls, env, ((HSObject) hsHandle).getHandle(), ((HSObject) serializedExceptionHsHandle).getHandle(),
                        suppressed, bailout, permanentBailout, graphTooBig);
    }

    @TruffleFromLibGraal(GetCompilableName)
    public static String getCompilableName(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return createString(env, callGetCompilableName(calls, env, ((HSObject) hsHandle).getHandle()));
    }

    @TruffleFromLibGraal(GetNonTrivialNodeCount)
    public static int getNonTrivialNodeCount(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callGetNonTrivialNodeCount(calls, env, ((HSObject) hsHandle).getHandle());
    }

    @TruffleFromLibGraal(CountDirectCallNodes)
    public static int countDirectCallNodes(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callCountDirectCallNodes(calls, env, ((HSObject) hsHandle).getHandle());
    }

    @TruffleFromLibGraal(GetCompilableCallCount)
    public static int getCompilableCallCount(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callGetCompilableCallCount(calls, env, ((HSObject) hsHandle).getHandle());
    }

    @TruffleFromLibGraal(CompilableToString)
    public static String compilableToString(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return createString(env, callCompilableToString(calls, env, ((HSObject) hsHandle).getHandle()));
    }

    @TruffleFromLibGraal(CancelCompilation)
    public static boolean cancelCompilation(Object hsHandle, CharSequence reason) {
        JNIEnv env = JNIMethodScope.env();
        return callCancelCompilation(calls, env, ((HSObject) hsHandle).getHandle(), JNIUtil.createHSString(env, reason.toString()));
    }

    @TruffleFromLibGraal(IsSameOrSplit)
    public static boolean isSameOrSplit(Object hsHandle, Object otherHsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callIsSameOrSplit(calls, env, ((HSObject) hsHandle).getHandle(),
                        otherHsHandle == null ? WordFactory.nullPointer() : ((HSObject) otherHsHandle).getHandle());
    }

    @TruffleFromLibGraal(GetKnownCallSiteCount)
    public static int getKnownCallSiteCount(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callGetKnownCallSiteCount(calls, env, ((HSObject) hsHandle).getHandle());
    }

    @TruffleFromLibGraal(ConsumeOptimizedAssumptionDependency)
    public static void consumeOptimizedAssumptionDependency(Object hsHandle, Object compilableHsHandle, long installedCode) {
        JNIEnv env = JNIMethodScope.env();
        callConsumeOptimizedAssumptionDependency(calls, env, ((HSObject) hsHandle).getHandle(),
                        compilableHsHandle == null ? WordFactory.nullPointer() : ((HSObject) compilableHsHandle).getHandle(),
                        installedCode);
    }

    @TruffleFromLibGraal(IsCancelled)
    public static boolean isCancelled(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callIsCancelled(calls, env, ((HSObject) hsHandle).getHandle());
    }

    @TruffleFromLibGraal(IsLastTier)
    public static boolean isLastTier(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callIsLastTier(calls, env, ((HSObject) hsHandle).getHandle());
    }

    @TruffleFromLibGraal(HasNextTier)
    public static boolean hasNextTier(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callHasNextTier(calls, env, ((HSObject) hsHandle).getHandle());
    }

    @TruffleFromLibGraal(GetPosition)
    public static Object getPosition(Object hsHandle, long nodeHandle) {
        JNIMethodScope scope = JNIMethodScope.scope();
        JObject hsPosition = callGetPosition(calls, scope.getEnv(), ((HSObject) hsHandle).getHandle(), nodeHandle);
        if (hsPosition.isNull()) {
            return null;
        } else {
            return new HSObject(scope, hsPosition);
        }
    }

    @TruffleFromLibGraal(AddTargetToDequeue)
    public static void addTargetToDequeue(Object hsHandle, Object compilableHsHandle) {
        JNIEnv env = JNIMethodScope.env();
        callAddTargetToDequeue(calls, env, ((HSObject) hsHandle).getHandle(), ((HSObject) compilableHsHandle).getHandle());
    }

    @TruffleFromLibGraal(SetCallCounts)
    public static void setCallCounts(Object hsHandle, int total, int inlined) {
        JNIEnv env = JNIMethodScope.env();
        callSetCallCounts(calls, env, ((HSObject) hsHandle).getHandle(), total, inlined);
    }

    @TruffleFromLibGraal(AddInlinedTarget)
    public static void addInlinedTarget(Object hsHandle, Object compilableHsHandle) {
        JNIEnv env = JNIMethodScope.env();
        callAddInlinedTarget(calls, env, ((HSObject) hsHandle).getHandle(), ((HSObject) compilableHsHandle).getHandle());
    }

    @TruffleFromLibGraal(GetDebugProperties)
    public static Map<String, Object> getDebugProperties(Object hsHandle, long nodeHandle) {
        JNIEnv env = JNIMethodScope.env();
        byte[] bytes = JNIUtil.createArray(env, (JByteArray) callGetDebugProperties(calls, env, ((HSObject) hsHandle).getHandle(), nodeHandle));
        BinaryInput in = BinaryInput.create(bytes);
        Map<String, Object> result = new LinkedHashMap<>();
        int size = in.readInt();
        for (int i = 0; i < size; i++) {
            String key = in.readUTF();
            Object value = in.readTypedValue();
            result.put(key, value);
        }
        return result;
    }

    @TruffleFromLibGraal(OnSuccess)
    public static void onSuccess(Object hsHandle, Object compilableHsHandle, Object taskHsHandle, Object graphInfo, Object compilationResultInfo, int tier) {
        try (LibGraalObjectHandleScope graphInfoScope = LibGraalObjectHandleScope.forObject(graphInfo);
                        LibGraalObjectHandleScope compilationResultInfoScope = LibGraalObjectHandleScope.forObject(compilationResultInfo)) {
            JNIEnv env = JNIMethodScope.env();
            callOnSuccess(calls, env, ((HSObject) hsHandle).getHandle(), ((HSObject) compilableHsHandle).getHandle(),
                            ((HSObject) taskHsHandle).getHandle(), graphInfoScope.getHandle(), compilationResultInfoScope.getHandle(),
                            tier);
        }
    }

    @TruffleFromLibGraal(OnTruffleTierFinished)
    public static void onTruffleTierFinished(Object hsHandle, Object compilableHsHandle, Object taskHsHandle, Object graphInfo) {
        try (LibGraalObjectHandleScope graphInfoScope = LibGraalObjectHandleScope.forObject(graphInfo)) {
            JNIEnv env = JNIMethodScope.env();
            callOnTruffleTierFinished(calls, env, ((HSObject) hsHandle).getHandle(), ((HSObject) compilableHsHandle).getHandle(),
                            ((HSObject) taskHsHandle).getHandle(), graphInfoScope.getHandle());
        }
    }

    @TruffleFromLibGraal(OnGraalTierFinished)
    public static void onGraalTierFinished(Object hsHandle, Object compilableHsHandle, Object graphInfo) {
        try (LibGraalObjectHandleScope graphInfoScope = LibGraalObjectHandleScope.forObject(graphInfo)) {
            JNIEnv env = JNIMethodScope.env();
            callOnGraalTierFinished(calls, env, ((HSObject) hsHandle).getHandle(), ((HSObject) compilableHsHandle).getHandle(),
                            graphInfoScope.getHandle());
        }
    }

    @TruffleFromLibGraal(OnFailure)
    public static void onFailure(Object hsHandle, Object compilableHsHandle, String reason, boolean bailout, boolean permanentBailout, int tier, Object lazyStackTrace) {
        try (LibGraalObjectHandleScope lazyStackTraceScope = lazyStackTrace != null ? LibGraalObjectHandleScope.forObject(lazyStackTrace) : null) {
            JNIEnv env = JNIMethodScope.env();
            callOnFailure(calls, env, ((HSObject) hsHandle).getHandle(),
                            ((HSObject) compilableHsHandle).getHandle(), JNIUtil.createHSString(env, reason),
                            bailout, permanentBailout, tier, lazyStackTraceScope != null ? lazyStackTraceScope.getHandle() : 0L);
        }
    }

    @TruffleFromLibGraal(OnCompilationRetry)
    public static void onCompilationRetry(Object hsHandle, Object compilableHsHandle, Object taskHsHandle) {
        JNIEnv env = JNIMethodScope.env();
        callOnCompilationRetry(calls, env, ((HSObject) hsHandle).getHandle(),
                        ((HSObject) compilableHsHandle).getHandle(), ((HSObject) taskHsHandle).getHandle());
    }

    @TruffleFromLibGraal(GetOffsetStart)
    public static int getOffsetStart(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callGetOffsetStart(calls, env, ((HSObject) hsHandle).getHandle());
    }

    @TruffleFromLibGraal(GetOffsetEnd)
    public static int getOffsetEnd(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callGetOffsetEnd(calls, env, ((HSObject) hsHandle).getHandle());
    }

    @TruffleFromLibGraal(GetLineNumber)
    public static int getLineNumber(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callGetLineNumber(calls, env, ((HSObject) hsHandle).getHandle());
    }

    @TruffleFromLibGraal(GetLanguage)
    public static String getLanguage(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return JNIUtil.createString(env, callGetLanguage(calls, env, ((HSObject) hsHandle).getHandle()));
    }

    @TruffleFromLibGraal(GetDescription)
    public static String getDescription(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return JNIUtil.createString(env, callGetDescription(calls, env, ((HSObject) hsHandle).getHandle()));
    }

    @TruffleFromLibGraal(GetURI)
    public static String getURI(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return JNIUtil.createString(env, callGetURI(calls, env, ((HSObject) hsHandle).getHandle()));
    }

    @TruffleFromLibGraal(GetNodeClassName)
    public static String getNodeClassName(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return JNIUtil.createString(env, callGetNodeClassName(calls, env, ((HSObject) hsHandle).getHandle()));
    }

    @TruffleFromLibGraal(GetNodeId)
    public static int getNodeId(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callGetNodeId(calls, env, ((HSObject) hsHandle).getHandle());
    }
}
