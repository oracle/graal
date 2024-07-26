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

import com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id;
import jdk.graal.compiler.debug.GraalError;
import org.graalvm.jniutils.HSObject;
import org.graalvm.jniutils.JNI.JObject;
import org.graalvm.jniutils.JNI.JByteArray;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JavaVM;
import org.graalvm.jniutils.JNI.JString;
import org.graalvm.jniutils.JNI.JValue;
import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.jniutils.JNIUtil;
import org.graalvm.nativebridge.BinaryInput;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.WordFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

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
import static java.lang.invoke.MethodType.methodType;
import static org.graalvm.jniutils.JNIUtil.createString;

// TODO: Should be generated by annotation processor
public final class TruffleFromLibGraalStartPoint {

    private static FromLibGraalCalls<Id> calls;
    private static JavaVM javaVM;

    private TruffleFromLibGraalStartPoint() {
    }

    static void initialize(FromLibGraalCalls<Id> fromLibGraalCalls) {
        calls = fromLibGraalCalls;
        JavaVM vm = JNIUtil.GetJavaVM(JNIMethodScope.env());
        assert javaVM.isNull() || javaVM.equal(vm);
        javaVM = vm;
    }

    public static void onIsolateShutdown(long isolateId) {
        JNIEnv env = JNIUtil.GetEnv(javaVM);
        JValue args = StackValue.get(1, JValue.class);
        args.addressOf(0).setLong(isolateId);
        calls.callVoid(env, OnIsolateShutdown, args);
    }

    public static byte[] getPartialEvaluationMethodInfo(Object hsHandle, long methodHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(2, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        args.addressOf(1).setLong(methodHandle);
        JByteArray jbytes = calls.callJObject(env, GetPartialEvaluationMethodInfo, args);
        return JNIUtil.createArray(env, jbytes);
    }

    public static byte[] getHostMethodInfo(Object hsHandle, long methodHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(2, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        args.addressOf(1).setLong(methodHandle);
        JByteArray jbytes = calls.callJObject(env, GetHostMethodInfo, args);
        return JNIUtil.createArray(env, jbytes);
    }

    public static void onCodeInstallation(Object hsHandle, Object compilableHsHandle, long installedCodeHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(3, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        args.addressOf(1).setJObject(((HSObject) compilableHsHandle).getHandle());
        args.addressOf(2).setLong(installedCodeHandle);
        calls.callVoid(env, OnCodeInstallation, args);
    }

    public static Object registerOptimizedAssumptionDependency(Object hsHandle, long optimizedAssumptionHandle) {
        JNIMethodScope scope = JNIMethodScope.scope();
        JValue args = StackValue.get(2, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        args.addressOf(1).setLong(optimizedAssumptionHandle);
        return new HSObject(scope, calls.callJObject(scope.getEnv(), RegisterOptimizedAssumptionDependency, args));
    }

    public static boolean isValueType(Object hsHandle, long typeHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(2, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        args.addressOf(1).setLong(typeHandle);
        return calls.callBoolean(env, IsValueType, args);
    }

    public static int getConstantFieldInfo(Object hsHandle, long typeHandle, boolean isStatic, int fieldIndex) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(4, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        args.addressOf(1).setLong(typeHandle);
        args.addressOf(2).setBoolean(isStatic);
        args.addressOf(3).setInt(fieldIndex);
        return calls.callInt(env, GetConstantFieldInfo, args);
    }

    public static void log(Object hsHandle, String loggerId, Object compilableHsHandle, String message) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(4, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        args.addressOf(1).setJObject(JNIUtil.createHSString(env, loggerId));
        args.addressOf(2).setJObject(((HSObject) compilableHsHandle).getHandle());
        args.addressOf(3).setJObject(JNIUtil.createHSString(env, message));
        calls.callVoid(env, Log, args);
    }

    public static Object createStringSupplier(Object serializedException) {
        JNIMethodScope scope = JNIMethodScope.scope();
        long serializedExceptionHandle = LibGraalObjectHandles.create(serializedException);
        JValue args = StackValue.get(1, JValue.class);
        args.addressOf(0).setLong(serializedExceptionHandle);
        return new HSObject(scope, calls.callJObject(scope.getEnv(), CreateStringSupplier, args));
    }

    public static boolean isSuppressedFailure(Object hsHandle, Object compilableHsHandle, Object supplierHsHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(3, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        args.addressOf(1).setJObject(((HSObject) compilableHsHandle).getHandle());
        args.addressOf(2).setJObject(((HSObject) supplierHsHandle).getHandle());
        return calls.callBoolean(env, IsSuppressedFailure, args);
    }

    public static long getFailedSpeculationsAddress(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(1, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        return calls.callLong(env, GetFailedSpeculationsAddress, args);
    }

    public static Map<String, String> getCompilerOptions(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(1, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        JByteArray res = calls.callJObject(env, GetCompilerOptions, args);
        BinaryInput in = BinaryInput.create(JNIUtil.createArray(env, res));
        int size = in.readInt();
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            String key = in.readUTF();
            map.put(key, in.readUTF());
        }
        return map;
    }

    public static long engineId(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(1, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        return calls.callLong(env, EngineId, args);
    }

    public static void prepareForCompilation(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(1, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        calls.callVoid(env, PrepareForCompilation, args);
    }

    public static boolean isTrivial(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(1, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        return calls.callBoolean(env, IsTrivial, args);
    }

    public static long asJavaConstant(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(1, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        return calls.callLong(env, AsJavaConstant, args);
    }

    public static void onCompilationFailed(Object hsHandle, Object serializedExceptionHsHandle, boolean suppressed, boolean bailout, boolean permanentBailout, boolean graphTooBig) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(6, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        args.addressOf(1).setJObject(((HSObject) serializedExceptionHsHandle).getHandle());
        args.addressOf(2).setBoolean(suppressed);
        args.addressOf(3).setBoolean(bailout);
        args.addressOf(4).setBoolean(permanentBailout);
        args.addressOf(5).setBoolean(graphTooBig);
        calls.callVoid(env, OnCompilationFailed, args);
    }

    public static String getCompilableName(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(1, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        return createString(env, calls.callJObject(env, GetCompilableName, args));
    }

    public static int getNonTrivialNodeCount(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(1, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        return calls.callInt(env, GetNonTrivialNodeCount, args);
    }

    public static int countDirectCallNodes(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(1, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        return calls.callInt(env, CountDirectCallNodes, args);
    }

    public static int getCompilableCallCount(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(1, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        return calls.callInt(env, GetCompilableCallCount, args);
    }

    public static String compilableToString(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(1, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        return createString(env, calls.callJObject(env, CompilableToString, args));
    }

    public static boolean cancelCompilation(Object hsHandle, CharSequence reason) {
        JNIEnv env = JNIMethodScope.env();
        JString jniReason = JNIUtil.createHSString(env, reason.toString());
        JValue args = StackValue.get(2, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        args.addressOf(1).setJObject(jniReason);
        return calls.callBoolean(env, CancelCompilation, args);
    }

    public static boolean isSameOrSplit(Object hsHandle, Object otherHsHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(2, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        args.addressOf(1).setJObject(otherHsHandle == null ? WordFactory.nullPointer() : ((HSObject) otherHsHandle).getHandle());
        return calls.callBoolean(env, IsSameOrSplit, args);
    }

    public static int getKnownCallSiteCount(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(1, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        return calls.callInt(env, GetKnownCallSiteCount, args);
    }

    public static void consumeOptimizedAssumptionDependency(Object hsHandle, Object compilableHsHandle, long installedCode) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(3, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        args.addressOf(1).setJObject(((HSObject) compilableHsHandle).getHandle());
        args.addressOf(2).setLong(installedCode);
        calls.callVoid(env, ConsumeOptimizedAssumptionDependency, args);
    }

    public static boolean isCancelled(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(1, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        return calls.callBoolean(env, IsCancelled, args);
    }

    public static boolean isLastTier(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(1, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        return calls.callBoolean(env, IsLastTier, args);
    }

    public static boolean hasNextTier(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(1, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        return calls.callBoolean(env, HasNextTier, args);
    }

    public static Object getPosition(Object hsHandle, long nodeHandle) {
        JNIMethodScope scope = JNIMethodScope.scope();
        JValue args = StackValue.get(2, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        args.addressOf(1).setLong(nodeHandle);
        JObject hsPosition = calls.callJObject(scope.getEnv(), GetPosition, args);
        if (hsPosition.isNull()) {
            return null;
        } else {
            return new HSObject(scope, hsPosition);
        }
    }

    public static void addTargetToDequeue(Object hsHandle, Object compilableHsHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(2, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        args.addressOf(1).setJObject(((HSObject) compilableHsHandle).getHandle());
        calls.callVoid(env, AddTargetToDequeue, args);
    }

    public static void setCallCounts(Object hsHandle, int total, int inlined) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(3, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        args.addressOf(1).setInt(total);
        args.addressOf(2).setInt(inlined);
        calls.callVoid(env, SetCallCounts, args);
    }

    public static void addInlinedTarget(Object hsHandle, Object compilableHsHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(2, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        args.addressOf(1).setJObject(((HSObject) compilableHsHandle).getHandle());
        calls.callVoid(env, AddInlinedTarget, args);
    }

    public static Map<String, Object> getDebugProperties(Object hsHandle, long nodeHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(2, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        args.addressOf(1).setLong(nodeHandle);
        byte[] bytes = JNIUtil.createArray(env, (JByteArray) calls.callJObject(env, GetDebugProperties, args));
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

    public static void onSuccess(Object hsHandle, Object compilableHsHandle, Object taskHsHandle, Object graphInfo, Object compilationResultInfo, int tier) {
        try (LibGraalObjectHandleScope graphInfoScope = LibGraalObjectHandleScope.forObject(graphInfo);
                        LibGraalObjectHandleScope compilationResultInfoScope = LibGraalObjectHandleScope.forObject(compilationResultInfo)) {
            JNIEnv env = JNIMethodScope.env();
            JValue args = StackValue.get(6, JValue.class);
            args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
            args.addressOf(1).setJObject(((HSObject) compilableHsHandle).getHandle());
            args.addressOf(2).setJObject(((HSObject) taskHsHandle).getHandle());
            args.addressOf(3).setLong(graphInfoScope.getHandle());
            args.addressOf(4).setLong(compilationResultInfoScope.getHandle());
            args.addressOf(5).setInt(tier);
            calls.callVoid(env, OnSuccess, args);
        }
    }

    public static void onTruffleTierFinished(Object hsHandle, Object compilableHsHandle, Object taskHsHandle, Object graphInfo) {
        try (LibGraalObjectHandleScope graphInfoScope = LibGraalObjectHandleScope.forObject(graphInfo)) {
            JNIEnv env = JNIMethodScope.env();
            JValue args = StackValue.get(4, JValue.class);
            args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
            args.addressOf(1).setJObject(((HSObject) compilableHsHandle).getHandle());
            args.addressOf(2).setJObject(((HSObject) taskHsHandle).getHandle());
            args.addressOf(3).setLong(graphInfoScope.getHandle());
            calls.callVoid(env, OnTruffleTierFinished, args);
        }
    }

    public static void onGraalTierFinished(Object hsHandle, Object compilableHsHandle, Object graphInfo) {
        try (LibGraalObjectHandleScope graphInfoScope = LibGraalObjectHandleScope.forObject(graphInfo)) {
            JNIEnv env = JNIMethodScope.env();
            JValue args = StackValue.get(3, JValue.class);
            args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
            args.addressOf(1).setJObject(((HSObject) compilableHsHandle).getHandle());
            args.addressOf(2).setLong(graphInfoScope.getHandle());
            calls.callVoid(env, OnGraalTierFinished, args);
        }
    }

    public static void onFailure(Object hsHandle, Object compilableHsHandle, String reason, boolean bailout, boolean permanentBailout, int tier, Object lazyStackTrace) {
        try (LibGraalObjectHandleScope lazyStackTraceScope = lazyStackTrace != null ? LibGraalObjectHandleScope.forObject(lazyStackTrace) : null) {
            JNIEnv env = JNIMethodScope.env();
            JValue args = StackValue.get(7, JValue.class);
            args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
            args.addressOf(1).setJObject(((HSObject) compilableHsHandle).getHandle());
            args.addressOf(2).setJObject(JNIUtil.createHSString(env, reason));
            args.addressOf(3).setBoolean(bailout);
            args.addressOf(4).setBoolean(permanentBailout);
            args.addressOf(5).setInt(tier);
            args.addressOf(6).setLong(lazyStackTraceScope != null ? lazyStackTraceScope.getHandle() : 0L);
            calls.callVoid(env, OnFailure, args);
        }
    }

    public static void onCompilationRetry(Object hsHandle, Object compilableHsHandle, Object taskHsHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(3, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        args.addressOf(1).setJObject(((HSObject) compilableHsHandle).getHandle());
        args.addressOf(2).setJObject(((HSObject) taskHsHandle).getHandle());
        calls.callVoid(env, OnCompilationRetry, args);
    }

    public static int getOffsetStart(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(1, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        return calls.callInt(env, GetOffsetStart, args);
    }

    public static int getOffsetEnd(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(1, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        return calls.callInt(env, GetOffsetEnd, args);
    }

    public static int getLineNumber(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(1, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        return calls.callInt(env, GetLineNumber, args);
    }

    public static String getLanguage(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(1, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        return JNIUtil.createString(env, calls.callJObject(env, GetLanguage, args));
    }

    public static String getDescription(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(1, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        return JNIUtil.createString(env, calls.callJObject(env, GetDescription, args));
    }

    public static String getURI(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(1, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        return JNIUtil.createString(env, calls.callJObject(env, GetURI, args));
    }

    public static String getNodeClassName(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(1, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        return JNIUtil.createString(env, calls.callJObject(env, GetNodeClassName, args));
    }

    public static int getNodeId(Object hsHandle) {
        JNIEnv env = JNIMethodScope.env();
        JValue args = StackValue.get(1, JValue.class);
        args.addressOf(0).setJObject(((HSObject) hsHandle).getHandle());
        return calls.callInt(env, GetNodeId, args);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    static Map<String, MethodHandle> getUpCallHandles() {
        try {
            Map<String, MethodHandle> result = new HashMap<>();
            insertMethodHandle(result, OnIsolateShutdown, methodType(void.class, long.class));
            insertMethodHandle(result, GetPartialEvaluationMethodInfo, methodType(byte[].class, Object.class, long.class));
            insertMethodHandle(result, GetHostMethodInfo, methodType(byte[].class, Object.class, long.class));
            insertMethodHandle(result, OnCodeInstallation, methodType(void.class, Object.class, Object.class, long.class));
            insertMethodHandle(result, RegisterOptimizedAssumptionDependency, methodType(Object.class, Object.class, long.class));
            insertMethodHandle(result, IsValueType, methodType(boolean.class, Object.class, long.class));
            insertMethodHandle(result, GetConstantFieldInfo, methodType(int.class, Object.class, long.class, boolean.class, int.class));
            insertMethodHandle(result, Log, methodType(void.class, Object.class, String.class, Object.class, String.class));
            insertMethodHandle(result, CreateStringSupplier, methodType(Object.class, Object.class));
            insertMethodHandle(result, IsSuppressedFailure, methodType(boolean.class, Object.class, Object.class, Object.class));
            insertMethodHandle(result, GetFailedSpeculationsAddress, methodType(long.class, Object.class));
            insertMethodHandle(result, GetCompilerOptions, methodType(Map.class, Object.class));
            insertMethodHandle(result, EngineId, methodType(long.class, Object.class));
            insertMethodHandle(result, PrepareForCompilation, methodType(void.class, Object.class));
            insertMethodHandle(result, IsTrivial, methodType(boolean.class, Object.class));
            insertMethodHandle(result, AsJavaConstant, methodType(long.class, Object.class));
            insertMethodHandle(result, OnCompilationFailed, methodType(void.class, Object.class, Object.class, boolean.class, boolean.class, boolean.class, boolean.class));
            insertMethodHandle(result, GetCompilableName, methodType(String.class, Object.class));
            insertMethodHandle(result, GetNonTrivialNodeCount, methodType(int.class, Object.class));
            insertMethodHandle(result, CountDirectCallNodes, methodType(int.class, Object.class));
            insertMethodHandle(result, GetCompilableCallCount, methodType(int.class, Object.class));
            insertMethodHandle(result, CompilableToString, methodType(String.class, Object.class));
            insertMethodHandle(result, CancelCompilation, methodType(boolean.class, Object.class, CharSequence.class));
            insertMethodHandle(result, IsSameOrSplit, methodType(boolean.class, Object.class, Object.class));
            insertMethodHandle(result, GetKnownCallSiteCount, methodType(int.class, Object.class));
            insertMethodHandle(result, ConsumeOptimizedAssumptionDependency, methodType(void.class, Object.class, Object.class, long.class));
            insertMethodHandle(result, IsCancelled, methodType(boolean.class, Object.class));
            insertMethodHandle(result, IsLastTier, methodType(boolean.class, Object.class));
            insertMethodHandle(result, HasNextTier, methodType(boolean.class, Object.class));
            insertMethodHandle(result, GetPosition, methodType(Object.class, Object.class, long.class));
            insertMethodHandle(result, AddTargetToDequeue, methodType(void.class, Object.class, Object.class));
            insertMethodHandle(result, SetCallCounts, methodType(void.class, Object.class, int.class, int.class));
            insertMethodHandle(result, AddInlinedTarget, methodType(void.class, Object.class, Object.class));
            insertMethodHandle(result, GetDebugProperties, methodType(Map.class, Object.class, long.class));
            insertMethodHandle(result, OnSuccess, methodType(void.class, Object.class, Object.class, Object.class, Object.class, Object.class, int.class));
            insertMethodHandle(result, OnTruffleTierFinished, methodType(void.class, Object.class, Object.class, Object.class, Object.class));
            insertMethodHandle(result, OnGraalTierFinished, methodType(void.class, Object.class, Object.class, Object.class));
            insertMethodHandle(result, OnFailure, methodType(void.class, Object.class, Object.class, String.class, boolean.class, boolean.class, int.class, Object.class));
            insertMethodHandle(result, OnCompilationRetry, methodType(void.class, Object.class, Object.class, Object.class));
            insertMethodHandle(result, GetOffsetStart, methodType(int.class, Object.class));
            insertMethodHandle(result, GetOffsetEnd, methodType(int.class, Object.class));
            insertMethodHandle(result, GetLineNumber, methodType(int.class, Object.class));
            insertMethodHandle(result, GetLanguage, methodType(String.class, Object.class));
            insertMethodHandle(result, GetDescription, methodType(String.class, Object.class));
            insertMethodHandle(result, GetURI, methodType(String.class, Object.class));
            insertMethodHandle(result, GetNodeClassName, methodType(String.class, Object.class));
            insertMethodHandle(result, GetNodeId, methodType(int.class, Object.class));
            return result;
        } catch (Throwable e) {
            throw GraalError.shouldNotReachHere(e);
        }
    }

    private static final MethodHandles.Lookup LKP = MethodHandles.lookup();

    @Platforms(Platform.HOSTED_ONLY.class)
    private static void insertMethodHandle(Map<String, MethodHandle> into, Id id, MethodType type) throws NoSuchMethodException, IllegalAccessException {
        into.put(id.name(), LKP.findStatic(TruffleFromLibGraalStartPoint.class, id.getMethodName(), type));
    }
}
