/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.runtime.hotspot.libgraal;

import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.AddInlinedTarget;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.AddTargetToDequeue;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.AsJavaConstant;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.CancelCompilation;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.CompilableToString;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.ConsumeOptimizedAssumptionDependency;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.CreateStringSupplier;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetCompilableCallCount;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetCompilableName;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetConstantFieldInfo;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetDescription;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetFailedSpeculationsAddress;
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
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetSuppliedString;
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
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.OnSuccess;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.OnTruffleTierFinished;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.RegisterOptimizedAssumptionDependency;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.SetCallCounts;
import static java.lang.String.format;
import static java.lang.System.lineSeparator;
import static java.util.stream.Collectors.joining;

import java.io.PrintStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.oracle.truffle.compiler.ConstantFieldInfo;
import com.oracle.truffle.compiler.HostMethodInfo;
import com.oracle.truffle.compiler.OptimizedAssumptionDependency;
import com.oracle.truffle.compiler.PartialEvaluationMethodInfo;
import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilationTask;
import com.oracle.truffle.compiler.TruffleCompilerAssumptionDependency;
import com.oracle.truffle.compiler.TruffleCompilerListener;
import com.oracle.truffle.compiler.TruffleCompilerRuntime;
import com.oracle.truffle.compiler.TruffleSourceLanguagePosition;
import com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal;
import com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.hotspot.libgraal.BinaryOutput.ByteArrayBinaryOutput;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.hotspot.HotSpotSpeculationLog;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Entry points in HotSpot for {@link TruffleFromLibGraal calls} from libgraal.
 */
final class TruffleFromLibGraalEntryPoints {

    static {
        assert checkHotSpotCalls();
    }

    @TruffleFromLibGraal(Id.OnIsolateShutdown)
    static void onIsolateShutdown(long isolateId) {
        LibGraalIsolate.unregister(isolateId);
    }

    @TruffleFromLibGraal(ConsumeOptimizedAssumptionDependency)
    static void consumeOptimizedAssumptionDependency(Consumer<OptimizedAssumptionDependency> consumer, Object target, long installedCode) {
        OptimizedCallTarget callTarget = (OptimizedCallTarget) target;
        OptimizedAssumptionDependency dependency;
        if (callTarget == null) {
            dependency = null;
        } else {
            dependency = new TruffleCompilerAssumptionDependency(callTarget, LibGraal.unhand(InstalledCode.class, installedCode));
        }
        consumer.accept(dependency);
    }

    @TruffleFromLibGraal(IsValueType)
    static boolean isValueType(Object truffleRuntime, long typeHandle) {
        ResolvedJavaType type = LibGraal.unhand(ResolvedJavaType.class, typeHandle);
        return ((TruffleCompilerRuntime) truffleRuntime).isValueType(type);
    }

    @TruffleFromLibGraal(GetConstantFieldInfo)
    static int getConstantFieldInfo(Object truffleRuntime, long typeHandle, boolean isStatic, int fieldIndex) {
        ResolvedJavaType enclosing = LibGraal.unhand(ResolvedJavaType.class, typeHandle);
        ResolvedJavaField[] declaredFields = isStatic ? enclosing.getStaticFields() : enclosing.getInstanceFields(false);
        ResolvedJavaField field = declaredFields[fieldIndex];

        ConstantFieldInfo constantFieldInfo = ((TruffleCompilerRuntime) truffleRuntime).getConstantFieldInfo(field);
        if (constantFieldInfo == null) {
            return Integer.MIN_VALUE;
        } else if (constantFieldInfo.isChildren()) {
            return -2;
        } else if (constantFieldInfo.isChild()) {
            return -1;
        } else {
            return constantFieldInfo.getDimensions();
        }
    }

    @TruffleFromLibGraal(Log)
    static void log(Object truffleRuntime, String loggerId, Object compilable, String message) {
        ((TruffleCompilerRuntime) truffleRuntime).log(loggerId, (TruffleCompilable) compilable, message);
    }

    @TruffleFromLibGraal(RegisterOptimizedAssumptionDependency)
    static Consumer<OptimizedAssumptionDependency> registerOptimizedAssumptionDependency(Object truffleRuntime, long optimizedAssumptionHandle) {
        JavaConstant optimizedAssumption = LibGraal.unhand(JavaConstant.class, optimizedAssumptionHandle);
        return ((TruffleCompilerRuntime) truffleRuntime).registerOptimizedAssumptionDependency(optimizedAssumption);
    }

    @TruffleFromLibGraal(IsSuppressedFailure)
    static boolean isSuppressedFailure(Object truffleRuntime, Object compilable, Supplier<String> serializedException) {
        return ((TruffleCompilerRuntime) truffleRuntime).isSuppressedFailure((TruffleCompilable) compilable, serializedException);
    }

    @TruffleFromLibGraal(GetPosition)
    static Object getPosition(Object task, long callNodeHandle) {
        JavaConstant callNode = LibGraal.unhand(JavaConstant.class, callNodeHandle);
        return ((TruffleCompilationTask) task).getPosition(callNode);
    }

    @TruffleFromLibGraal(Id.EngineId)
    static long engineId(Object compilable) {
        return ((OptimizedCallTarget) compilable).engineId();
    }

    @TruffleFromLibGraal(Id.GetDebugProperties)
    static byte[] getDebugProperties(Object task, long callNodeHandle) {
        JavaConstant callNode = LibGraal.unhand(JavaConstant.class, callNodeHandle);
        Map<String, Object> properties = ((TruffleCompilationTask) task).getDebugProperties(callNode);
        if (properties == null) {
            properties = Collections.emptyMap();
        }
        ByteArrayBinaryOutput output = BinaryOutput.create(new byte[128]);
        output.writeInt(properties.size());
        for (Map.Entry<String, Object> e : properties.entrySet()) {
            output.writeUTF(e.getKey());
            Object value = e.getValue();
            if (!BinaryOutput.isTypedValue(value)) {
                value = value.toString();
            }
            output.writeTypedValue(value);
        }
        return output.getArray();
    }

    @TruffleFromLibGraal(Id.GetCompilerOptions)
    static byte[] getCompilerOptions(Object o) {
        TruffleCompilable compilable = ((TruffleCompilable) o);
        Map<String, String> properties = compilable.getCompilerOptions();
        ByteArrayBinaryOutput output = BinaryOutput.create();
        output.writeInt(properties.size());
        for (Map.Entry<String, String> e : properties.entrySet()) {
            output.writeUTF(e.getKey());
            String value = e.getValue();
            output.writeUTF(value);
        }
        return output.getArray();
    }

    @TruffleFromLibGraal(Id.PrepareForCompilation)
    static void prepareForCompilation(Object compilable) {
        ((TruffleCompilable) compilable).prepareForCompilation();
    }

    @TruffleFromLibGraal(GetURI)
    static String getURI(Object position) {
        URI uri = ((TruffleSourceLanguagePosition) position).getURI();
        return uri == null ? null : uri.toString();
    }

    @TruffleFromLibGraal(AsJavaConstant)
    static long asJavaConstant(Object compilable) {
        JavaConstant constant = ((TruffleCompilable) compilable).asJavaConstant();
        return LibGraal.translate(constant);
    }

    @TruffleFromLibGraal(OnCodeInstallation)
    static void onCodeInstallation(Object truffleRuntime, Object compilable, long installedCodeHandle) {
        InstalledCode installedCode = LibGraal.unhand(InstalledCode.class, installedCodeHandle);
        ((TruffleCompilerRuntime) truffleRuntime).onCodeInstallation((TruffleCompilable) compilable, installedCode);
    }

    @TruffleFromLibGraal(GetFailedSpeculationsAddress)
    static long getFailedSpeculationsAddress(Object compilable) {
        OptimizedCallTarget callTarget = (OptimizedCallTarget) compilable;
        HotSpotSpeculationLog log = (HotSpotSpeculationLog) callTarget.getSpeculationLog();
        return LibGraal.getFailedSpeculationsAddress(log);
    }

    @TruffleFromLibGraal(CreateStringSupplier)
    static Supplier<String> createStringSupplier(long handle) {
        return new LibGraalStringSupplier(handle);
    }

    @TruffleFromLibGraal(GetSuppliedString)
    static String getSuppliedString(Supplier<String> supplier) {
        return supplier.get();
    }

    @TruffleFromLibGraal(IsCancelled)
    static boolean isCancelled(Object task) {
        return ((TruffleCompilationTask) task).isCancelled();
    }

    @TruffleFromLibGraal(IsLastTier)
    static boolean isLastTier(Object task) {
        return ((TruffleCompilationTask) task).isLastTier();
    }

    @TruffleFromLibGraal(HasNextTier)
    static boolean hasNextTier(Object task) {
        return ((TruffleCompilationTask) task).hasNextTier();
    }

    @TruffleFromLibGraal(CompilableToString)
    static String compilableToString(Object compilable) {
        return ((TruffleCompilable) compilable).toString();
    }

    @TruffleFromLibGraal(GetCompilableName)
    static String getCompilableName(Object compilable) {
        return ((TruffleCompilable) compilable).getName();
    }

    @TruffleFromLibGraal(GetDescription)
    static String getDescription(Object pos) {
        return ((TruffleSourceLanguagePosition) pos).getDescription();
    }

    @TruffleFromLibGraal(GetLanguage)
    static String getLanguage(Object pos) {
        return ((TruffleSourceLanguagePosition) pos).getLanguage();
    }

    @TruffleFromLibGraal(GetLineNumber)
    static int getLineNumber(Object pos) {
        return ((TruffleSourceLanguagePosition) pos).getLineNumber();
    }

    @TruffleFromLibGraal(GetOffsetEnd)
    static int getOffsetEnd(Object pos) {
        return ((TruffleSourceLanguagePosition) pos).getOffsetEnd();
    }

    @TruffleFromLibGraal(GetOffsetStart)
    static int getOffsetStart(Object pos) {
        return ((TruffleSourceLanguagePosition) pos).getOffsetStart();
    }

    @TruffleFromLibGraal(GetNodeClassName)
    static String getNodeClassName(Object pos) {
        return ((TruffleSourceLanguagePosition) pos).getNodeClassName();
    }

    @TruffleFromLibGraal(GetNodeId)
    static int getNodeId(Object pos) {
        return ((TruffleSourceLanguagePosition) pos).getNodeId();
    }

    @TruffleFromLibGraal(OnCompilationFailed)
    static void onCompilationFailed(Object compilable, Supplier<String> serializedException, boolean silent, boolean bailout, boolean permanentBailout, boolean graphTooBig) {
        ((TruffleCompilable) compilable).onCompilationFailed(serializedException, silent, bailout, permanentBailout, graphTooBig);
    }

    @TruffleFromLibGraal(OnSuccess)
    static void onSuccess(Object listener, Object compilable, Object plan, long graphInfoHandle, long compilationResultInfoHandle, int tier) {
        try (LibGraalGraphInfo graphInfo = new LibGraalGraphInfo(graphInfoHandle);
                        LibGraalCompilationResultInfo compilationResultInfo = new LibGraalCompilationResultInfo(compilationResultInfoHandle)) {
            ((TruffleCompilerListener) listener).onSuccess((TruffleCompilable) compilable, (TruffleCompilationTask) plan, graphInfo, compilationResultInfo, tier);
        }
    }

    /**
     * Entry point for deprecated
     * {@link TruffleCompilerListener#onFailure(TruffleCompilable, String, boolean, boolean, int)}
     * used for compatibility with LTS graalvm-23.1.
     * <p>
     * GR-54187: Remove in graalvm-25.1
     * </p>
     */
    @TruffleFromLibGraal(OnFailure)
    @Deprecated
    @SuppressWarnings("deprecation")
    static void onFailure(Object listener, Object compilable, String reason, boolean bailout, boolean permanentBailout, int tier) {
        ((TruffleCompilerListener) listener).onFailure((TruffleCompilable) compilable, reason, bailout, permanentBailout, tier);
    }

    @TruffleFromLibGraal(OnFailure)
    static void onFailure(Object listener, Object compilable, String reason, boolean bailout, boolean permanentBailout, int tier, long serializedExceptionHandle) {
        try (LibGraalScopedStringSupplier serializedException = serializedExceptionHandle != 0L ? new LibGraalScopedStringSupplier(serializedExceptionHandle) : null) {
            ((TruffleCompilerListener) listener).onFailure((TruffleCompilable) compilable, reason, bailout, permanentBailout, tier, serializedException);
        }
    }

    @TruffleFromLibGraal(OnCompilationRetry)
    static void onCompilationRetry(Object listener, Object compilable, Object task) {
        ((TruffleCompilerListener) listener).onCompilationRetry((TruffleCompilable) compilable, (TruffleCompilationTask) task);
    }

    @TruffleFromLibGraal(OnGraalTierFinished)
    static void onGraalTierFinished(Object listener, Object compilable, long graphInfoHandle) {
        try (LibGraalGraphInfo graphInfo = new LibGraalGraphInfo(graphInfoHandle)) {
            ((TruffleCompilerListener) listener).onGraalTierFinished((TruffleCompilable) compilable, graphInfo);
        }
    }

    @TruffleFromLibGraal(OnTruffleTierFinished)
    static void onTruffleTierFinished(Object listener, Object compilable, Object plan, long graphInfoHandle) {
        try (LibGraalGraphInfo graphInfo = new LibGraalGraphInfo(graphInfoHandle)) {
            ((TruffleCompilerListener) listener).onTruffleTierFinished((TruffleCompilable) compilable, (TruffleCompilationTask) plan, graphInfo);
        }
    }

    @TruffleFromLibGraal(CancelCompilation)
    static boolean cancelCompilation(Object compilableTruffleAST, String reason) {
        return ((TruffleCompilable) compilableTruffleAST).cancelCompilation(reason);
    }

    @TruffleFromLibGraal(GetCompilableCallCount)
    static int getCompilableCallCount(Object compilableTruffleAST) {
        return ((TruffleCompilable) compilableTruffleAST).getCallCount();
    }

    @TruffleFromLibGraal(GetKnownCallSiteCount)
    static int getKnownCallSiteCount(Object compilableTruffleAST) {
        return ((TruffleCompilable) compilableTruffleAST).getKnownCallSiteCount();
    }

    @TruffleFromLibGraal(IsSameOrSplit)
    static boolean isSameOrSplit(Object compilableTruffleAST1, Object compilableTruffleAST2) {
        return ((TruffleCompilable) compilableTruffleAST1).isSameOrSplit((TruffleCompilable) compilableTruffleAST2);
    }

    @TruffleFromLibGraal(IsTrivial)
    static boolean isTrivial(Object compilableTruffleAST1) {
        return ((TruffleCompilable) compilableTruffleAST1).isTrivial();
    }

    @TruffleFromLibGraal(GetNonTrivialNodeCount)
    static int getNonTrivialNodeCount(Object compilableTruffleAST) {
        return ((TruffleCompilable) compilableTruffleAST).getNonTrivialNodeCount();
    }

    @TruffleFromLibGraal(Id.CountDirectCallNodes)
    static int countDirectCallNodes(Object compilableTruffleAST) {
        return ((TruffleCompilable) compilableTruffleAST).countDirectCallNodes();
    }

    @TruffleFromLibGraal(AddTargetToDequeue)
    static void addTargetToDequeue(Object task, Object compilableTruffleAST) {
        ((TruffleCompilationTask) task).addTargetToDequeue((TruffleCompilable) compilableTruffleAST);
    }

    @TruffleFromLibGraal(SetCallCounts)
    static void setCallCounts(Object task, int total, int inlined) {
        ((TruffleCompilationTask) task).setCallCounts(total, inlined);
    }

    @TruffleFromLibGraal(AddInlinedTarget)
    static void addInlinedTarget(Object task, Object target) {
        ((TruffleCompilationTask) task).addInlinedTarget(((TruffleCompilable) target));
    }

    @TruffleFromLibGraal(GetPartialEvaluationMethodInfo)
    static Object getPartialEvaluationMethodInfo(Object truffleRuntime, long methodHandle) {
        ResolvedJavaMethod method = LibGraal.unhand(ResolvedJavaMethod.class, methodHandle);
        PartialEvaluationMethodInfo info = ((TruffleCompilerRuntime) truffleRuntime).getPartialEvaluationMethodInfo(method);
        BinaryOutput.ByteArrayBinaryOutput out = BinaryOutput.create(new byte[5]);
        out.writeByte(info.loopExplosion().ordinal());
        out.writeByte(info.inlineForPartialEvaluation().ordinal());
        out.writeByte(info.inlineForTruffleBoundary().ordinal());
        out.writeBoolean(info.isInlineable());
        out.writeBoolean(info.isSpecializationMethod());
        return out.getArray();
    }

    @TruffleFromLibGraal(Id.GetHostMethodInfo)
    static Object getHostMethodInfo(Object truffleRuntime, long methodHandle) {
        ResolvedJavaMethod method = LibGraal.unhand(ResolvedJavaMethod.class, methodHandle);
        HostMethodInfo info = ((TruffleCompilerRuntime) truffleRuntime).getHostMethodInfo(method);
        BinaryOutput.ByteArrayBinaryOutput out = BinaryOutput.create(new byte[4]);
        out.writeBoolean(info.isTruffleBoundary());
        out.writeBoolean(info.isBytecodeInterpreterSwitch());
        out.writeBoolean(info.isBytecodeInterpreterSwitchBoundary());
        out.writeBoolean(info.isInliningCutoff());
        return out.getArray();
    }

    /*----------------------*/

    /**
     * Checks that all {@link TruffleFromLibGraal}s are implemented and that their signatures match
     * the {@linkplain Id#getSignature() ID signatures}.
     */
    private static boolean checkHotSpotCalls() {
        Set<Id> unimplemented = EnumSet.allOf(Id.class);
        Map<Id, List<Method>> idToMethod = new HashMap<>();
        for (Method method : TruffleFromLibGraalEntryPoints.class.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                TruffleFromLibGraal a = method.getAnnotation(TruffleFromLibGraal.class);
                if (a != null) {
                    Id id = a.value();
                    List<Method> methods = idToMethod.computeIfAbsent(id, (k) -> new ArrayList<>());
                    methods.add(method);
                }
            }
        }
        for (Map.Entry<Id, List<Method>> e : idToMethod.entrySet()) {
            Id id = e.getKey();
            List<Method> methods = e.getValue();
            int legacyMethodCount = 0;
            for (Method method : methods) {
                check(id, id.getMethodName().equals(method.getName()), "Expected name \"%s\", got \"%s\"", id.getMethodName(), method.getName());
                if (method.getAnnotation(Deprecated.class) != null) {
                    legacyMethodCount++;
                }
                if (Arrays.equals(id.getParameterTypes(), method.getParameterTypes())) {
                    unimplemented.remove(id);
                    check(id, id.getReturnType().equals(method.getReturnType()), "Expected return type %s, got %s", id.getReturnType().getName(), method.getReturnType().getName());
                    checkParameters(id, method.getParameterTypes());
                }
            }
            check(id, legacyMethodCount == methods.size() - 1, "Entry points with multiple versions must mark all legacy versions with the @Deprecated annotation.");
        }
        check(null, unimplemented.isEmpty(), "Missing implementations:%n%s", unimplemented.stream().map(TruffleFromLibGraalEntryPoints::missingImpl).sorted().collect(joining(lineSeparator())));
        return true;
    }

    private static void checkParameters(Id id, Class<?>[] types) {
        Class<?>[] idTypes = id.getParameterTypes();
        check(id, idTypes.length == types.length, "Expected %d parameters, got %d", idTypes.length, types.length);
        for (int i = 0; i < types.length; i++) {
            check(id, idTypes[i].equals(types[i]), "Parameter %d has wrong type, expected %s, got %s", i, idTypes[i].getName(), types[i].getName());
        }
    }

    private static String missingImpl(Id id) {
        Formatter buf = new Formatter();
        buf.format("    @%s(%s)%n", TruffleFromLibGraal.class.getSimpleName(), id.name());
        buf.format("    static %s %s(%s) {%n    }%n", id.getReturnType().getSimpleName(), id.getMethodName(), Stream.of(id.getParameterTypes()).map(c -> c.getSimpleName()).collect(joining(", ")));
        return buf.toString();
    }

    private static void check(Id id, boolean condition, String format, Object... args) {
        if (!condition) {
            String msg = format(format, args);
            PrintStream err = System.err;
            if (id != null) {
                err.printf("ERROR: %s.%s: %s%n", TruffleFromLibGraalEntryPoints.class.getName(), id, msg);
            } else {
                err.printf("ERROR: %s: %s%n", TruffleFromLibGraalEntryPoints.class.getName(), msg);
            }
            System.exit(99);
        }
    }
}
