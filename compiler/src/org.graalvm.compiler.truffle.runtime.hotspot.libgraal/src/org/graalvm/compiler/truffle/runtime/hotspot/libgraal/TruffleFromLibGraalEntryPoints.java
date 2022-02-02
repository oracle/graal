/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime.hotspot.libgraal;

import static java.lang.String.format;
import static java.lang.System.lineSeparator;
import static java.util.stream.Collectors.joining;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.AddInlinedTarget;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.AddTargetToDequeue;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.AsCompilableTruffleAST;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.AsJavaConstant;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.CallNodeHashCode;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.CancelCompilation;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.CompilableToString;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.ConsumeOptimizedAssumptionDependency;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.CountInlinedCalls;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.CreateStringSupplier;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.DequeueInlined;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.FindCallNode;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetCallCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetCallNodes;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetCallTargetForCallNode;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetCompilableCallCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetCompilableName;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetConstantFieldInfo;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetCurrentCallTarget;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetDescription;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetFailedSpeculationsAddress;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetFrameSlotKindTagForJavaKind;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetInlineKind;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetKnownCallSiteCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetLanguage;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetLineNumber;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetLoopExplosionKind;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetNodeClassName;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetNodeId;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetNodeRewritingAssumptionConstant;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetNonTrivialNodeCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetOffsetEnd;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetOffsetStart;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetPosition;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetSuppliedString;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetURI;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetValidRootAssumptionConstant;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.InliningData;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.IsBytecodeInterpreterSwitch;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.IsBytecodeInterpreterSwitchBoundary;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.IsCancelled;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.IsInliningForced;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.IsLastTier;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.IsSameOrSplit;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.IsSpecializationMethod;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.IsSuppressedFailure;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.HasNextTier;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.IsTrivial;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.IsTruffleBoundary;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.IsValueType;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.Log;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.OnCodeInstallation;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.OnCompilationFailed;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.OnCompilationRetry;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.OnFailure;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.OnGraalTierFinished;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.OnSuccess;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.OnTruffleTierFinished;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.RegisterOptimizedAssumptionDependency;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.SetCallCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.SetInlinedCallCount;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.EnumSet;
import java.util.Formatter;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.OptimizedAssumptionDependency;
import org.graalvm.compiler.truffle.common.TruffleCallNode;
import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.TruffleInliningData;
import org.graalvm.compiler.truffle.common.TruffleSourceLanguagePosition;
import org.graalvm.compiler.truffle.common.hotspot.HotSpotTruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.libgraal.LibGraal;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.hotspot.HotSpotSpeculationLog;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
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

    @TruffleFromLibGraal(ConsumeOptimizedAssumptionDependency)
    static void consumeOptimizedAssumptionDependency(Consumer<OptimizedAssumptionDependency> consumer, Object dep) {
        consumer.accept((OptimizedAssumptionDependency) dep);
    }

    @TruffleFromLibGraal(CountInlinedCalls)
    static int countInlinedCalls(Object provider) {
        return ((TruffleInliningData) provider).countInlinedCalls();
    }

    @TruffleFromLibGraal(GetCallTargetForCallNode)
    static long getCallTargetForCallNode(Object truffleRuntime, long callNodeHandle) {
        JavaConstant callNode = LibGraal.unhand(JavaConstant.class, callNodeHandle);
        JavaConstant callTarget = ((TruffleCompilerRuntime) truffleRuntime).getCallTargetForCallNode(callNode);
        return LibGraal.translate(callTarget);
    }

    @TruffleFromLibGraal(IsTruffleBoundary)
    static boolean isTruffleBoundary(Object truffleRuntime, long methodHandle) {
        ResolvedJavaMethod method = LibGraal.unhand(ResolvedJavaMethod.class, methodHandle);
        return ((TruffleCompilerRuntime) truffleRuntime).isTruffleBoundary(method);
    }

    @TruffleFromLibGraal(IsSpecializationMethod)
    static boolean isSpecializationMethod(Object truffleRuntime, long methodHandle) {
        ResolvedJavaMethod method = LibGraal.unhand(ResolvedJavaMethod.class, methodHandle);
        return ((TruffleCompilerRuntime) truffleRuntime).isSpecializationMethod(method);
    }

    @TruffleFromLibGraal(IsBytecodeInterpreterSwitch)
    static boolean isBytecodeInterpreterSwitch(Object truffleRuntime, long methodHandle) {
        ResolvedJavaMethod method = LibGraal.unhand(ResolvedJavaMethod.class, methodHandle);
        return ((TruffleCompilerRuntime) truffleRuntime).isBytecodeInterpreterSwitch(method);
    }

    @TruffleFromLibGraal(IsBytecodeInterpreterSwitchBoundary)
    static boolean isBytecodeInterpreterSwitchBoundary(Object truffleRuntime, long methodHandle) {
        ResolvedJavaMethod method = LibGraal.unhand(ResolvedJavaMethod.class, methodHandle);
        return ((TruffleCompilerRuntime) truffleRuntime).isBytecodeInterpreterSwitchBoundary(method);
    }

    @TruffleFromLibGraal(IsValueType)
    static boolean isValueType(Object truffleRuntime, long typeHandle) {
        ResolvedJavaType type = LibGraal.unhand(ResolvedJavaType.class, typeHandle);
        return ((TruffleCompilerRuntime) truffleRuntime).isValueType(type);
    }

    @TruffleFromLibGraal(GetInlineKind)
    static int getInlineKind(Object truffleRuntime, long methodHandle, boolean duringPartialEvaluation) {
        ResolvedJavaMethod method = LibGraal.unhand(ResolvedJavaMethod.class, methodHandle);
        TruffleCompilerRuntime.InlineKind inlineKind = ((TruffleCompilerRuntime) truffleRuntime).getInlineKind(method, duringPartialEvaluation);
        return inlineKind.ordinal();
    }

    @TruffleFromLibGraal(GetLoopExplosionKind)
    static int getLoopExplosionKind(Object truffleRuntime, long methodHandle) {
        ResolvedJavaMethod method = LibGraal.unhand(ResolvedJavaMethod.class, methodHandle);
        TruffleCompilerRuntime.LoopExplosionKind loopExplosionKind = ((TruffleCompilerRuntime) truffleRuntime).getLoopExplosionKind(method);
        return loopExplosionKind.ordinal();
    }

    @TruffleFromLibGraal(GetConstantFieldInfo)
    static int getConstantFieldInfo(Object truffleRuntime, long typeHandle, boolean isStatic, int fieldIndex) {
        ResolvedJavaType enclosing = LibGraal.unhand(ResolvedJavaType.class, typeHandle);
        ResolvedJavaField[] declaredFields = isStatic ? enclosing.getStaticFields() : enclosing.getInstanceFields(false);
        ResolvedJavaField field = declaredFields[fieldIndex];

        TruffleCompilerRuntime.ConstantFieldInfo constantFieldInfo = ((TruffleCompilerRuntime) truffleRuntime).getConstantFieldInfo(field);
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

    @TruffleFromLibGraal(Id.GetJavaKindForFrameSlotKind)
    static int getJavaKindForFrameSlotKind(Object truffleRuntime, int frameSlotKindTag) {
        return ((TruffleCompilerRuntime) truffleRuntime).getJavaKindForFrameSlotKind(frameSlotKindTag).ordinal();
    }

    @TruffleFromLibGraal(Id.GetFrameSlotKindTagsCount)
    static int getFrameSlotKindTagsCount(Object truffleRuntime) {
        return ((TruffleCompilerRuntime) truffleRuntime).getFrameSlotKindTagsCount();
    }

    @TruffleFromLibGraal(GetFrameSlotKindTagForJavaKind)
    static int getFrameSlotKindTagForJavaKind(Object truffleRuntime, int ordinal) {
        return ((TruffleCompilerRuntime) truffleRuntime).getFrameSlotKindTagForJavaKind(JavaKind.values()[ordinal]);
    }

    @TruffleFromLibGraal(Log)
    static void log(Object truffleRuntime, String loggerId, Object compilable, String message) {
        ((TruffleCompilerRuntime) truffleRuntime).log(loggerId, (CompilableTruffleAST) compilable, message);
    }

    @TruffleFromLibGraal(RegisterOptimizedAssumptionDependency)
    static Consumer<OptimizedAssumptionDependency> registerOptimizedAssumptionDependency(Object truffleRuntime, long optimizedAssumptionHandle) {
        JavaConstant optimizedAssumption = LibGraal.unhand(JavaConstant.class, optimizedAssumptionHandle);
        return ((TruffleCompilerRuntime) truffleRuntime).registerOptimizedAssumptionDependency(optimizedAssumption);
    }

    @TruffleFromLibGraal(AsCompilableTruffleAST)
    static Object asCompilableTruffleAST(Object truffleRuntime, long constantHandle) {
        JavaConstant constant = LibGraal.unhand(JavaConstant.class, constantHandle);
        return ((TruffleCompilerRuntime) truffleRuntime).asCompilableTruffleAST(constant);
    }

    @TruffleFromLibGraal(IsSuppressedFailure)
    static boolean isSuppressedFailure(Object truffleRuntime, Object compilable, Supplier<String> serializedException) {
        return ((HotSpotTruffleCompilerRuntime) truffleRuntime).isSuppressedFailure((CompilableTruffleAST) compilable, serializedException);
    }

    @TruffleFromLibGraal(GetPosition)
    static Object getPosition(Object inliningPlan, long callNodeHandle) {
        JavaConstant callNode = LibGraal.unhand(JavaConstant.class, callNodeHandle);
        return ((TruffleInliningData) inliningPlan).getPosition(callNode);
    }

    @TruffleFromLibGraal(GetNodeRewritingAssumptionConstant)
    static long getNodeRewritingAssumptionConstant(Object compilable) {
        JavaConstant assumption = ((CompilableTruffleAST) compilable).getNodeRewritingAssumptionConstant();
        return LibGraal.translate(assumption);
    }

    @TruffleFromLibGraal(GetValidRootAssumptionConstant)
    static long getValidRootAssumptionConstant(Object compilable) {
        JavaConstant assumption = ((CompilableTruffleAST) compilable).getValidRootAssumptionConstant();
        return LibGraal.translate(assumption);
    }

    @TruffleFromLibGraal(GetURI)
    static String getURI(Object position) {
        URI uri = ((TruffleSourceLanguagePosition) position).getURI();
        return uri == null ? null : uri.toString();
    }

    @TruffleFromLibGraal(AsJavaConstant)
    static long asJavaConstant(Object compilable) {
        JavaConstant constant = ((CompilableTruffleAST) compilable).asJavaConstant();
        return LibGraal.translate(constant);
    }

    @TruffleFromLibGraal(OnCodeInstallation)
    static void onCodeInstallation(Object truffleRuntime, Object compilable, long installedCodeHandle) {
        InstalledCode installedCode = LibGraal.unhand(InstalledCode.class, installedCodeHandle);
        ((HotSpotTruffleCompilerRuntime) truffleRuntime).onCodeInstallation((CompilableTruffleAST) compilable, installedCode);
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

    @TruffleFromLibGraal(InliningData)
    static Object inliningData(Object task) {
        return ((TruffleCompilationTask) task).inliningData();
    }

    @TruffleFromLibGraal(CompilableToString)
    static String compilableToString(Object compilable) {
        return ((CompilableTruffleAST) compilable).toString();
    }

    @TruffleFromLibGraal(GetCompilableName)
    static String getCompilableName(Object compilable) {
        return ((CompilableTruffleAST) compilable).getName();
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
        ((CompilableTruffleAST) compilable).onCompilationFailed(serializedException, silent, bailout, permanentBailout, graphTooBig);
    }

    @TruffleFromLibGraal(OnSuccess)
    static void onSuccess(Object listener, Object compilable, Object plan, long graphInfoHandle, long compilationResultInfoHandle, int tier) {
        try (LibGraalGraphInfo graphInfo = new LibGraalGraphInfo(graphInfoHandle);
                        LibGraalCompilationResultInfo compilationResultInfo = new LibGraalCompilationResultInfo(compilationResultInfoHandle)) {
            ((TruffleCompilerListener) listener).onSuccess((CompilableTruffleAST) compilable, (TruffleInliningData) plan, graphInfo, compilationResultInfo, tier);
        }
    }

    @TruffleFromLibGraal(OnFailure)
    static void onFailure(Object listener, Object compilable, String reason, boolean bailout, boolean permanentBailout, int tier) {
        ((TruffleCompilerListener) listener).onFailure((CompilableTruffleAST) compilable, reason, bailout, permanentBailout, tier);
    }

    @TruffleFromLibGraal(OnCompilationRetry)
    static void onCompilationRetry(Object listener, Object compilable, Object task) {
        ((TruffleCompilerListener) listener).onCompilationRetry((CompilableTruffleAST) compilable, (TruffleCompilationTask) task);
    }

    @TruffleFromLibGraal(OnGraalTierFinished)
    static void onGraalTierFinished(Object listener, Object compilable, long graphInfoHandle) {
        try (LibGraalGraphInfo graphInfo = new LibGraalGraphInfo(graphInfoHandle)) {
            ((TruffleCompilerListener) listener).onGraalTierFinished((CompilableTruffleAST) compilable, graphInfo);
        }
    }

    @TruffleFromLibGraal(OnTruffleTierFinished)
    static void onTruffleTierFinished(Object listener, Object compilable, Object plan, long graphInfoHandle) {
        try (LibGraalGraphInfo graphInfo = new LibGraalGraphInfo(graphInfoHandle)) {
            ((TruffleCompilerListener) listener).onTruffleTierFinished((CompilableTruffleAST) compilable, (TruffleInliningData) plan, graphInfo);
        }
    }

    @TruffleFromLibGraal(CancelCompilation)
    static boolean cancelCompilation(Object compilableTruffleAST, String reason) {
        return ((CompilableTruffleAST) compilableTruffleAST).cancelCompilation(reason);
    }

    @TruffleFromLibGraal(DequeueInlined)
    static void dequeueInlined(Object compilableTruffleAST) {
        ((CompilableTruffleAST) compilableTruffleAST).dequeueInlined();
    }

    @TruffleFromLibGraal(FindCallNode)
    static Object findCallNode(Object provider, long callNodeHandle) {
        JavaConstant callNode = LibGraal.unhand(JavaConstant.class, callNodeHandle);
        return ((TruffleInliningData) provider).findCallNode(callNode);
    }

    @TruffleFromLibGraal(GetCallCount)
    static int getCallCount(Object callNode) {
        return ((TruffleCallNode) callNode).getCallCount();
    }

    @TruffleFromLibGraal(GetCurrentCallTarget)
    static Object getCurrentCallTarget(Object truffleCallNode) {
        return ((TruffleCallNode) truffleCallNode).getCurrentCallTarget();
    }

    @TruffleFromLibGraal(IsInliningForced)
    static boolean isInliningForced(Object truffleCallNode) {
        return ((TruffleCallNode) truffleCallNode).isInliningForced();
    }

    @TruffleFromLibGraal(CallNodeHashCode)
    static int callNodeHashCode(Object truffleCallNode) {
        return ((TruffleCallNode) truffleCallNode).hashCode();
    }

    @TruffleFromLibGraal(GetCompilableCallCount)
    static int getCompilableCallCount(Object compilableTruffleAST) {
        return ((CompilableTruffleAST) compilableTruffleAST).getCallCount();
    }

    @TruffleFromLibGraal(GetCallNodes)
    static Object[] getCallNodes(Object compilableTruffleAST) {
        return ((CompilableTruffleAST) compilableTruffleAST).getCallNodes();
    }

    @TruffleFromLibGraal(GetKnownCallSiteCount)
    static int getKnownCallSiteCount(Object compilableTruffleAST) {
        return ((CompilableTruffleAST) compilableTruffleAST).getKnownCallSiteCount();
    }

    @TruffleFromLibGraal(IsSameOrSplit)
    static boolean isSameOrSplit(Object compilableTruffleAST1, Object compilableTruffleAST2) {
        return ((CompilableTruffleAST) compilableTruffleAST1).isSameOrSplit((CompilableTruffleAST) compilableTruffleAST2);
    }

    @TruffleFromLibGraal(IsTrivial)
    static boolean isTrivial(Object compilableTruffleAST1) {
        return ((CompilableTruffleAST) compilableTruffleAST1).isTrivial();
    }

    @TruffleFromLibGraal(GetNonTrivialNodeCount)
    static int getNonTrivialNodeCount(Object compilableTruffleAST) {
        return ((CompilableTruffleAST) compilableTruffleAST).getNonTrivialNodeCount();
    }

    @TruffleFromLibGraal(AddTargetToDequeue)
    static void addTargetToDequeue(Object inliningPlan, Object compilableTruffleAST) {
        ((TruffleInliningData) inliningPlan).addTargetToDequeue((CompilableTruffleAST) compilableTruffleAST);
    }

    @TruffleFromLibGraal(SetCallCount)
    static void setCallCount(Object inliningPlan, int count) {
        ((TruffleInliningData) inliningPlan).setCallCount(count);
    }

    @TruffleFromLibGraal(SetInlinedCallCount)
    static void setInlinedCallCount(Object inliningPlan, int count) {
        ((TruffleInliningData) inliningPlan).setInlinedCallCount(count);
    }

    @TruffleFromLibGraal(AddInlinedTarget)
    static void addInlinedTarget(Object inlining, Object target) {
        ((TruffleInliningData) inlining).addInlinedTarget(((CompilableTruffleAST) target));
    }

    /*----------------------*/

    /**
     * Checks that all {@link TruffleFromLibGraal}s are implemented and that their signatures match
     * the {@linkplain Id#getSignature() ID signatures}.
     */
    private static boolean checkHotSpotCalls() {
        Set<Id> unimplemented = EnumSet.allOf(Id.class);
        for (Method method : TruffleFromLibGraalEntryPoints.class.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                TruffleFromLibGraal a = method.getAnnotation(TruffleFromLibGraal.class);
                if (a != null) {
                    Id id = a.value();
                    unimplemented.remove(id);
                    check(id, id.getMethodName().equals(method.getName()), "Expected name \"%s\", got \"%s\"", id.getMethodName(), method.getName());
                    check(id, id.getReturnType().equals(method.getReturnType()), "Expected return type %s, got %s", id.getReturnType().getName(), method.getReturnType().getName());
                    checkParameters(id, method.getParameterTypes());
                }
            }
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
            if (id != null) {
                System.err.printf("ERROR: %s.%s: %s%n", TruffleFromLibGraalEntryPoints.class.getName(), id, msg);
            } else {
                System.err.printf("ERROR: %s: %s%n", TruffleFromLibGraalEntryPoints.class.getName(), msg);
            }
            System.exit(99);
        }
    }
}
