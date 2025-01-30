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
package com.oracle.svm.graal.hotspot.libgraal;

import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callAddInlinedTarget;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callAddTargetToDequeue;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callAsJavaConstant;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callCancelCompilation;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callCompilableToString;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callConsumeOptimizedAssumptionDependency;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callCountDirectCallNodes;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callCreateStringSupplier;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callEngineId;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callGetCompilableCallCount;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callGetCompilableName;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callGetCompilerOptions;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callGetConstantFieldInfo;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callGetDebugProperties;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callGetDescription;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callGetFailedSpeculationsAddress;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callGetKnownCallSiteCount;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callGetLanguage;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callGetLineNumber;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callGetNodeClassName;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callGetNodeId;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callGetNonTrivialNodeCount;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callGetOffsetEnd;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callGetOffsetStart;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callGetPartialEvaluationMethodInfo;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callGetPosition;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callGetSuppliedString;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callGetURI;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callHasNextTier;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callIsCancelled;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callIsLastTier;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callIsSameOrSplit;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callIsSuppressedFailure;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callIsTrivial;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callIsValueType;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callLog;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callOnCodeInstallation;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callOnCompilationFailed;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callOnCompilationRetry;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callOnFailure;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callOnGraalTierFinished;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callOnIsolateShutdown;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callOnSuccess;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callOnTruffleTierFinished;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callPrepareForCompilation;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callRegisterOptimizedAssumptionDependency;
import static com.oracle.svm.graal.hotspot.libgraal.TruffleFromLibGraalStartPointsGen.callSetCallCounts;
import static org.graalvm.jniutils.JNIMethodScope.env;
import static org.graalvm.jniutils.JNIUtil.ExceptionClear;
import static org.graalvm.jniutils.JNIUtil.GetStaticMethodID;
import static org.graalvm.jniutils.JNIUtil.createString;
import static org.graalvm.nativeimage.c.type.CTypeConversion.toCString;

import java.util.LinkedHashMap;
import java.util.Map;

import org.graalvm.jniutils.HSObject;
import org.graalvm.jniutils.JNI.JByteArray;
import org.graalvm.jniutils.JNI.JClass;
import org.graalvm.jniutils.JNI.JMethodID;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JObject;
import org.graalvm.jniutils.JNI.JValue;
import org.graalvm.jniutils.JNI.JavaVM;
import org.graalvm.jniutils.JNICalls.JNIMethod;
import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.jniutils.JNIUtil;
import org.graalvm.nativebridge.BinaryInput;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;

import com.oracle.truffle.compiler.hotspot.libgraal.FromLibGraalId;
import com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal;
import com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id;

import jdk.graal.compiler.word.Word;

/**
 * JNI calls to HotSpot called by guest Graal using method handles.
 */
public final class TruffleFromLibGraalStartPoints {

    private static volatile TruffleFromLibGraalCalls calls;
    private static JavaVM javaVM;

    private TruffleFromLibGraalStartPoints() {
    }

    static void initializeJNI(JClass runtimeClass) {
        TruffleFromLibGraalCalls localCalls = calls;
        if (localCalls == null) {
            initialize(runtimeClass);
        }
    }

    private static synchronized void initialize(JClass runtimeClass) {
        calls = new TruffleFromLibGraalCalls(JNIMethodScope.env(), runtimeClass);
        JavaVM vm = JNIUtil.GetJavaVM(JNIMethodScope.env());
        assert javaVM.isNull() || javaVM.equal(vm);
        javaVM = vm;
    }

    @TruffleFromLibGraal(Id.OnIsolateShutdown)
    public static void onIsolateShutdown(long isolateId) {
        JNIEnv env = JNIUtil.GetEnv(javaVM);
        callOnIsolateShutdown(calls, env, isolateId);
    }

    @TruffleFromLibGraal(Id.GetPartialEvaluationMethodInfo)
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

    @TruffleFromLibGraal(Id.OnCodeInstallation)
    public static void onCodeInstallation(Object hsHandle, Object compilableHsHandle, long installedCodeHandle) {
        JNIEnv env = JNIMethodScope.env();
        callOnCodeInstallation(calls, env, ((HSObject) hsHandle).getHandle(), ((HSObject) compilableHsHandle).getHandle(), installedCodeHandle);
    }

    @TruffleFromLibGraal(Id.RegisterOptimizedAssumptionDependency)
    public static Object registerOptimizedAssumptionDependency(Object hsHandle, long optimizedAssumptionHandle) {
        JNIMethodScope scope = JNIMethodScope.scope();
        JObject assumptionConsumer = callRegisterOptimizedAssumptionDependency(calls, scope.getEnv(), ((HSObject) hsHandle).getHandle(), optimizedAssumptionHandle);
        return assumptionConsumer.isNull() ? null : new HSObject(scope, assumptionConsumer);
    }

    @TruffleFromLibGraal(Id.IsValueType)
    public static boolean isValueType(Object hsHandle, long typeHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callIsValueType(calls, env, ((HSObject) hsHandle).getHandle(), typeHandle);
    }

    @TruffleFromLibGraal(Id.GetConstantFieldInfo)
    public static int getConstantFieldInfo(Object hsHandle, long typeHandle, boolean isStatic, int fieldIndex) {
        JNIEnv env = JNIMethodScope.env();
        return callGetConstantFieldInfo(calls, env, ((HSObject) hsHandle).getHandle(), typeHandle, isStatic, fieldIndex);
    }

    @TruffleFromLibGraal(Id.Log)
    public static void log(Object hsHandle, String loggerId, Object compilableHsHandle, String message) {
        JNIEnv env = JNIMethodScope.env();
        callLog(calls, env, ((HSObject) hsHandle).getHandle(), JNIUtil.createHSString(env, loggerId), ((HSObject) compilableHsHandle).getHandle(), JNIUtil.createHSString(env, message));
    }

    @TruffleFromLibGraal(Id.CreateStringSupplier)
    public static Object createStringSupplier(Object serializedException) {
        JNIMethodScope scope = JNIMethodScope.scope();
        long serializedExceptionHandle = LibGraalObjectHandles.create(serializedException);
        return new HSObject(scope, callCreateStringSupplier(calls, scope.getEnv(), serializedExceptionHandle));
    }

    @TruffleFromLibGraal(Id.GetSuppliedString)
    public static String getSuppliedString(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return createString(env, callGetSuppliedString(calls, env, ((HSObject) hsHandle).getHandle()));
    }

    @TruffleFromLibGraal(Id.IsSuppressedFailure)
    public static boolean isSuppressedFailure(Object hsHandle, Object compilableHsHandle, Object supplierHsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callIsSuppressedFailure(calls, env, ((HSObject) hsHandle).getHandle(), ((HSObject) compilableHsHandle).getHandle(), ((HSObject) supplierHsHandle).getHandle());
    }

    @TruffleFromLibGraal(Id.GetFailedSpeculationsAddress)
    public static long getFailedSpeculationsAddress(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callGetFailedSpeculationsAddress(calls, env, ((HSObject) hsHandle).getHandle());
    }

    @TruffleFromLibGraal(Id.GetCompilerOptions)
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

    @TruffleFromLibGraal(Id.EngineId)
    public static long engineId(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callEngineId(calls, env, ((HSObject) hsHandle).getHandle());
    }

    @TruffleFromLibGraal(Id.PrepareForCompilation)
    public static boolean prepareForCompilation(Object hsHandle, boolean p1, int p2, boolean p3) {
        JNIEnv env = JNIMethodScope.env();
        JNIMethod newMethod = findPrepareForCompilationNewMethod(env);
        if (newMethod != null) {
            return callPrepareForCompilationNew(newMethod, env, ((HSObject) hsHandle).getHandle(), p1, p2, p3);
        } else {
            callPrepareForCompilation(calls, env, ((HSObject) hsHandle).getHandle());
            return true;
        }
    }

    private static volatile JNIMethod prepareForCompilationNewMethod;

    private static JNIMethod findPrepareForCompilationNewMethod(JNIEnv env) {
        JNIMethod res = prepareForCompilationNewMethod;
        if (res == null) {
            res = findJNIMethod(env, "prepareForCompilation", boolean.class, Object.class, boolean.class, int.class, boolean.class);
            prepareForCompilationNewMethod = res;
        }
        return res.getJMethodID().isNonNull() ? res : null;
    }

    static boolean callPrepareForCompilationNew(JNIMethod method, JNIEnv env, JObject p0, boolean p1, int p2, boolean p3) {
        JValue args = StackValue.get(4, JValue.class);
        args.addressOf(0).setJObject(p0);
        args.addressOf(1).setBoolean(p1);
        args.addressOf(2).setInt(p2);
        args.addressOf(3).setBoolean(p3);
        return calls.getJNICalls().callStaticBoolean(env, calls.getPeer(), method, args);
    }

    private static JNIMethod findJNIMethod(JNIEnv env, String methodName, Class<?> returnType, Class<?>... parameterTypes) {
        try (CTypeConversion.CCharPointerHolder cname = toCString(methodName);
                        CTypeConversion.CCharPointerHolder csig = toCString(FromLibGraalId.encodeMethodSignature(returnType, parameterTypes))) {
            JMethodID jniId = GetStaticMethodID(env, calls.getPeer(), cname.get(), csig.get());
            if (jniId.isNull()) {
                /*
                 * The `onFailure` method with 7 arguments is not available in Truffle runtime 24.0,
                 * clear pending NoSuchMethodError.
                 */
                ExceptionClear(env);
            }
            return new JNIMethod() {
                @Override
                public JMethodID getJMethodID() {
                    return jniId;
                }

                @Override
                public String getDisplayName() {
                    return methodName;
                }
            };
        }
    }

    @TruffleFromLibGraal(Id.IsTrivial)
    public static boolean isTrivial(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callIsTrivial(calls, env, ((HSObject) hsHandle).getHandle());
    }

    @TruffleFromLibGraal(Id.AsJavaConstant)
    public static long asJavaConstant(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callAsJavaConstant(calls, env, ((HSObject) hsHandle).getHandle());
    }

    @TruffleFromLibGraal(Id.OnCompilationFailed)
    public static void onCompilationFailed(Object hsHandle, Object serializedExceptionHsHandle, boolean suppressed, boolean bailout, boolean permanentBailout, boolean graphTooBig) {
        JNIEnv env = JNIMethodScope.env();
        callOnCompilationFailed(calls, env, ((HSObject) hsHandle).getHandle(), ((HSObject) serializedExceptionHsHandle).getHandle(),
                        suppressed, bailout, permanentBailout, graphTooBig);
    }

    private static volatile JNIMethod onCompilationSuccessMethod;

    private static JNIMethod findOnCompilationSuccessMethod(JNIEnv env) {
        JNIMethod res = onCompilationSuccessMethod;
        if (res == null) {
            res = findJNIMethod(env, "onCompilationSuccess", void.class, Object.class, int.class, boolean.class);
            onCompilationSuccessMethod = res;
        }
        return res.getJMethodID().isNonNull() ? res : null;
    }

    public static void onCompilationSuccess(Object hsHandle, int compilationTier, boolean lastTier) {
        JNIEnv env = JNIMethodScope.env();
        JNIMethod methodOrNull = findOnCompilationSuccessMethod(env);
        if (methodOrNull != null) {
            JValue args = StackValue.get(3, JValue.class);
            args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
            args.addressOf(1).setInt(compilationTier);
            args.addressOf(2).setBoolean(lastTier);
            calls.getJNICalls().callStaticVoid(env, calls.getPeer(), methodOrNull, args);
        }
    }

    @TruffleFromLibGraal(Id.GetCompilableName)
    public static String getCompilableName(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return createString(env, callGetCompilableName(calls, env, ((HSObject) hsHandle).getHandle()));
    }

    @TruffleFromLibGraal(Id.GetNonTrivialNodeCount)
    public static int getNonTrivialNodeCount(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callGetNonTrivialNodeCount(calls, env, ((HSObject) hsHandle).getHandle());
    }

    @TruffleFromLibGraal(Id.CountDirectCallNodes)
    public static int countDirectCallNodes(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callCountDirectCallNodes(calls, env, ((HSObject) hsHandle).getHandle());
    }

    @TruffleFromLibGraal(Id.GetCompilableCallCount)
    public static int getCompilableCallCount(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callGetCompilableCallCount(calls, env, ((HSObject) hsHandle).getHandle());
    }

    @TruffleFromLibGraal(Id.CompilableToString)
    public static String compilableToString(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return createString(env, callCompilableToString(calls, env, ((HSObject) hsHandle).getHandle()));
    }

    @TruffleFromLibGraal(Id.CancelCompilation)
    public static boolean cancelCompilation(Object hsHandle, CharSequence reason) {
        JNIEnv env = JNIMethodScope.env();
        return callCancelCompilation(calls, env, ((HSObject) hsHandle).getHandle(), JNIUtil.createHSString(env, reason.toString()));
    }

    @TruffleFromLibGraal(Id.IsSameOrSplit)
    public static boolean isSameOrSplit(Object hsHandle, Object otherHsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callIsSameOrSplit(calls, env, ((HSObject) hsHandle).getHandle(),
                        otherHsHandle == null ? Word.nullPointer() : ((HSObject) otherHsHandle).getHandle());
    }

    @TruffleFromLibGraal(Id.GetKnownCallSiteCount)
    public static int getKnownCallSiteCount(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callGetKnownCallSiteCount(calls, env, ((HSObject) hsHandle).getHandle());
    }

    @TruffleFromLibGraal(Id.ConsumeOptimizedAssumptionDependency)
    public static void consumeOptimizedAssumptionDependency(Object hsHandle, Object compilableHsHandle, long installedCode) {
        JNIEnv env = JNIMethodScope.env();
        callConsumeOptimizedAssumptionDependency(calls, env, ((HSObject) hsHandle).getHandle(),
                        compilableHsHandle == null ? Word.nullPointer() : ((HSObject) compilableHsHandle).getHandle(),
                        installedCode);
    }

    @TruffleFromLibGraal(Id.IsCancelled)
    public static boolean isCancelled(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callIsCancelled(calls, env, ((HSObject) hsHandle).getHandle());
    }

    @TruffleFromLibGraal(Id.IsLastTier)
    public static boolean isLastTier(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callIsLastTier(calls, env, ((HSObject) hsHandle).getHandle());
    }

    @TruffleFromLibGraal(Id.HasNextTier)
    public static boolean hasNextTier(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callHasNextTier(calls, env, ((HSObject) hsHandle).getHandle());
    }

    @TruffleFromLibGraal(Id.GetPosition)
    public static Object getPosition(Object hsHandle, long nodeHandle) {
        JNIMethodScope scope = JNIMethodScope.scope();
        JObject hsPosition = callGetPosition(calls, scope.getEnv(), ((HSObject) hsHandle).getHandle(), nodeHandle);
        if (hsPosition.isNull()) {
            return null;
        } else {
            return new HSObject(scope, hsPosition);
        }
    }

    @TruffleFromLibGraal(Id.AddTargetToDequeue)
    public static void addTargetToDequeue(Object hsHandle, Object compilableHsHandle) {
        JNIEnv env = JNIMethodScope.env();
        callAddTargetToDequeue(calls, env, ((HSObject) hsHandle).getHandle(), ((HSObject) compilableHsHandle).getHandle());
    }

    @TruffleFromLibGraal(Id.SetCallCounts)
    public static void setCallCounts(Object hsHandle, int total, int inlined) {
        JNIEnv env = JNIMethodScope.env();
        callSetCallCounts(calls, env, ((HSObject) hsHandle).getHandle(), total, inlined);
    }

    @TruffleFromLibGraal(Id.AddInlinedTarget)
    public static void addInlinedTarget(Object hsHandle, Object compilableHsHandle) {
        JNIEnv env = JNIMethodScope.env();
        callAddInlinedTarget(calls, env, ((HSObject) hsHandle).getHandle(), ((HSObject) compilableHsHandle).getHandle());
    }

    @TruffleFromLibGraal(Id.GetDebugProperties)
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

    @TruffleFromLibGraal(Id.OnSuccess)
    public static void onSuccess(Object hsHandle, Object compilableHsHandle, Object taskHsHandle, Object graphInfo, Object compilationResultInfo, int tier) {
        try (LibGraalObjectHandleScope graphInfoScope = LibGraalObjectHandleScope.forObject(graphInfo);
                        LibGraalObjectHandleScope compilationResultInfoScope = LibGraalObjectHandleScope.forObject(compilationResultInfo)) {
            JNIEnv env = JNIMethodScope.env();
            callOnSuccess(calls, env, ((HSObject) hsHandle).getHandle(), ((HSObject) compilableHsHandle).getHandle(),
                            ((HSObject) taskHsHandle).getHandle(), graphInfoScope.getHandle(), compilationResultInfoScope.getHandle(),
                            tier);
        }
    }

    @TruffleFromLibGraal(Id.OnTruffleTierFinished)
    public static void onTruffleTierFinished(Object hsHandle, Object compilableHsHandle, Object taskHsHandle, Object graphInfo) {
        try (LibGraalObjectHandleScope graphInfoScope = LibGraalObjectHandleScope.forObject(graphInfo)) {
            JNIEnv env = JNIMethodScope.env();
            callOnTruffleTierFinished(calls, env, ((HSObject) hsHandle).getHandle(), ((HSObject) compilableHsHandle).getHandle(),
                            ((HSObject) taskHsHandle).getHandle(), graphInfoScope.getHandle());
        }
    }

    @TruffleFromLibGraal(Id.OnGraalTierFinished)
    public static void onGraalTierFinished(Object hsHandle, Object compilableHsHandle, Object graphInfo) {
        try (LibGraalObjectHandleScope graphInfoScope = LibGraalObjectHandleScope.forObject(graphInfo)) {
            JNIEnv env = JNIMethodScope.env();
            callOnGraalTierFinished(calls, env, ((HSObject) hsHandle).getHandle(), ((HSObject) compilableHsHandle).getHandle(),
                            graphInfoScope.getHandle());
        }
    }

    @TruffleFromLibGraal(Id.OnFailure)
    public static void onFailure(Object hsHandle, Object compilableHsHandle, String reason, boolean bailout, boolean permanentBailout, int tier, Object lazyStackTrace) {
        try (LibGraalObjectHandleScope lazyStackTraceScope = lazyStackTrace != null ? LibGraalObjectHandleScope.forObject(lazyStackTrace) : null) {
            JNIEnv env = JNIMethodScope.env();
            callOnFailure(calls, env, ((HSObject) hsHandle).getHandle(),
                            ((HSObject) compilableHsHandle).getHandle(), JNIUtil.createHSString(env, reason),
                            bailout, permanentBailout, tier, lazyStackTraceScope != null ? lazyStackTraceScope.getHandle() : 0L);
        }
    }

    @TruffleFromLibGraal(Id.OnCompilationRetry)
    public static void onCompilationRetry(Object hsHandle, Object compilableHsHandle, Object taskHsHandle) {
        JNIEnv env = JNIMethodScope.env();
        callOnCompilationRetry(calls, env, ((HSObject) hsHandle).getHandle(),
                        ((HSObject) compilableHsHandle).getHandle(), ((HSObject) taskHsHandle).getHandle());
    }

    @TruffleFromLibGraal(Id.GetOffsetStart)
    public static int getOffsetStart(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callGetOffsetStart(calls, env, ((HSObject) hsHandle).getHandle());
    }

    @TruffleFromLibGraal(Id.GetOffsetEnd)
    public static int getOffsetEnd(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callGetOffsetEnd(calls, env, ((HSObject) hsHandle).getHandle());
    }

    @TruffleFromLibGraal(Id.GetLineNumber)
    public static int getLineNumber(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callGetLineNumber(calls, env, ((HSObject) hsHandle).getHandle());
    }

    @TruffleFromLibGraal(Id.GetLanguage)
    public static String getLanguage(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return JNIUtil.createString(env, callGetLanguage(calls, env, ((HSObject) hsHandle).getHandle()));
    }

    @TruffleFromLibGraal(Id.GetDescription)
    public static String getDescription(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return JNIUtil.createString(env, callGetDescription(calls, env, ((HSObject) hsHandle).getHandle()));
    }

    @TruffleFromLibGraal(Id.GetURI)
    public static String getURI(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return JNIUtil.createString(env, callGetURI(calls, env, ((HSObject) hsHandle).getHandle()));
    }

    @TruffleFromLibGraal(Id.GetNodeClassName)
    public static String getNodeClassName(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return JNIUtil.createString(env, callGetNodeClassName(calls, env, ((HSObject) hsHandle).getHandle()));
    }

    @TruffleFromLibGraal(Id.GetNodeId)
    public static int getNodeId(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        return callGetNodeId(calls, env, ((HSObject) hsHandle).getHandle());
    }
}
