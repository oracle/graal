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
package org.graalvm.compiler.truffle.runtime.hotspot.libgraal;

import static java.lang.String.format;
import static java.lang.System.lineSeparator;
import static java.util.stream.Collectors.joining;

import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.AddTargetToDequeue;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.AsCompilableTruffleAST;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.AsJavaConstant;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.CallNodeHashCode;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.CancelCompilation;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.CompilableToString;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.ConsumeOptimizedAssumptionDependency;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.CreateInliningPlan;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.CreateStringSupplier;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.DequeueTargets;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.FindCallNode;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.FindDecision;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetCallCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetCallNodes;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetKnownCallSiteCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetCallTargetForCallNode;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetCompilableCallCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetCompilableName;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetConstantFieldInfo;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetCurrentCallTarget;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetDescription;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetFailedSpeculationsAddress;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetFrameSlotKindTagForJavaKind;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetInlineKind;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetLanguage;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetLineNumber;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetLoopExplosionKind;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetNodeRewritingAssumption;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetNodeRewritingAssumptionConstant;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetNonTrivialNodeCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetOffsetEnd;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetOffsetStart;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetPosition;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetSuppliedString;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetTargetName;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetTruffleCallBoundaryMethods;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetURI;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.IsCancelled;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.IsInliningForced;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.IsLastTier;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.IsSameOrSplit;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.IsTargetStable;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.IsTruffleBoundary;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.IsValueType;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.Log;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.OnCodeInstallation;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.OnCompilationFailed;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.OnFailure;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.OnGraalTierFinished;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.OnSuccess;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.OnTruffleTierFinished;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.RegisterOptimizedAssumptionDependency;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.ShouldInline;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;
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
import org.graalvm.compiler.truffle.common.TruffleInliningPlan;
import org.graalvm.compiler.truffle.common.TruffleInliningPlan.Decision;
import org.graalvm.compiler.truffle.common.TruffleMetaAccessProvider;
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

    private static final Map<Integer, JavaKind> JAVA_KINDS;
    static {
        Map<Integer, JavaKind> m = new HashMap<>();
        for (JavaKind jk : JavaKind.values()) {
            m.put(jk.getBasicType(), jk);
        }
        JAVA_KINDS = Collections.unmodifiableMap(m);

        assert checkHotSpotCalls();
    }

    @TruffleFromLibGraal(ConsumeOptimizedAssumptionDependency)
    static void consumeOptimizedAssumptionDependency(Consumer<OptimizedAssumptionDependency> consumer, Object dep) {
        consumer.accept((OptimizedAssumptionDependency) dep);
    }

    @TruffleFromLibGraal(GetCallTargetForCallNode)
    static long getCallTargetForCallNode(Object truffleRuntime, long callNodeHandle) {
        JavaConstant callNode = LibGraal.unhand(JavaConstant.class, callNodeHandle);
        JavaConstant callTarget = ((HotSpotTruffleCompilerRuntime) truffleRuntime).getCallTargetForCallNode(callNode);
        return LibGraal.translate(callTarget);
    }

    @TruffleFromLibGraal(IsTruffleBoundary)
    static boolean isTruffleBoundary(Object truffleRuntime, long methodHandle) {
        ResolvedJavaMethod method = LibGraal.unhand(ResolvedJavaMethod.class, methodHandle);
        return ((HotSpotTruffleCompilerRuntime) truffleRuntime).isTruffleBoundary(method);
    }

    @TruffleFromLibGraal(IsValueType)
    static boolean isValueType(Object truffleRuntime, long typeHandle) {
        ResolvedJavaType type = LibGraal.unhand(ResolvedJavaType.class, typeHandle);
        return ((HotSpotTruffleCompilerRuntime) truffleRuntime).isValueType(type);
    }

    @TruffleFromLibGraal(GetInlineKind)
    static int getInlineKind(Object truffleRuntime, long methodHandle, boolean duringPartialEvaluation) {
        ResolvedJavaMethod method = LibGraal.unhand(ResolvedJavaMethod.class, methodHandle);
        TruffleCompilerRuntime.InlineKind inlineKind = ((HotSpotTruffleCompilerRuntime) truffleRuntime).getInlineKind(method, duringPartialEvaluation);
        return inlineKind.ordinal();
    }

    @TruffleFromLibGraal(GetLoopExplosionKind)
    static int getLoopExplosionKind(Object truffleRuntime, long methodHandle) {
        ResolvedJavaMethod method = LibGraal.unhand(ResolvedJavaMethod.class, methodHandle);
        TruffleCompilerRuntime.LoopExplosionKind loopExplosionKind = ((HotSpotTruffleCompilerRuntime) truffleRuntime).getLoopExplosionKind(method);
        return loopExplosionKind.ordinal();
    }

    @TruffleFromLibGraal(GetConstantFieldInfo)
    static int getConstantFieldInfo(Object truffleRuntime, long typeHandle, boolean isStatic, int fieldIndex) {
        ResolvedJavaType enclosing = LibGraal.unhand(ResolvedJavaType.class, typeHandle);
        ResolvedJavaField[] declaredFields = isStatic ? enclosing.getStaticFields() : enclosing.getInstanceFields(false);
        ResolvedJavaField field = declaredFields[fieldIndex];

        TruffleCompilerRuntime.ConstantFieldInfo constantFieldInfo = ((HotSpotTruffleCompilerRuntime) truffleRuntime).getConstantFieldInfo(field);
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
        JavaKind kind = ((HotSpotTruffleCompilerRuntime) truffleRuntime).getJavaKindForFrameSlotKind(frameSlotKindTag);
        return kind.getBasicType();
    }

    @TruffleFromLibGraal(Id.GetFrameSlotKindTagsCount)
    static int getFrameSlotKindTagsCount(Object truffleRuntime) {
        return ((HotSpotTruffleCompilerRuntime) truffleRuntime).getFrameSlotKindTagsCount();
    }

    @TruffleFromLibGraal(GetTruffleCallBoundaryMethods)
    static long[] getTruffleCallBoundaryMethods(Object truffleRuntime) {
        Collection<ResolvedJavaMethod> source;
        Iterable<ResolvedJavaMethod> iterable = ((HotSpotTruffleCompilerRuntime) truffleRuntime).getTruffleCallBoundaryMethods();
        if (iterable instanceof Collection) {
            source = (Collection<ResolvedJavaMethod>) iterable;
        } else {
            source = new ArrayList<>();
            for (ResolvedJavaMethod m : iterable) {
                source.add(m);
            }
        }
        long[] res = new long[source.size()];
        int i = 0;
        for (ResolvedJavaMethod m : source) {
            res[i++] = LibGraal.translate(m);
        }
        return res;
    }

    @TruffleFromLibGraal(GetFrameSlotKindTagForJavaKind)
    static int getFrameSlotKindTagForJavaKind(Object truffleRuntime, int basicType) {
        JavaKind kind = getJavaKind(basicType);
        return ((HotSpotTruffleCompilerRuntime) truffleRuntime).getFrameSlotKindTagForJavaKind(kind);
    }

    @TruffleFromLibGraal(Log)
    static void log(Object truffleRuntime, Object compilable, String message) {
        ((HotSpotTruffleCompilerRuntime) truffleRuntime).log((CompilableTruffleAST) compilable, message);
    }

    @TruffleFromLibGraal(CreateInliningPlan)
    static Object createInliningPlan(Object truffleRuntime, Object compilable, Object task) {
        return ((HotSpotTruffleCompilerRuntime) truffleRuntime).createInliningPlan((CompilableTruffleAST) compilable, (TruffleCompilationTask) task);
    }

    @TruffleFromLibGraal(RegisterOptimizedAssumptionDependency)
    static Consumer<OptimizedAssumptionDependency> registerOptimizedAssumptionDependency(Object truffleRuntime, long optimizedAssumptionHandle) {
        JavaConstant optimizedAssumption = LibGraal.unhand(JavaConstant.class, optimizedAssumptionHandle);
        return ((HotSpotTruffleCompilerRuntime) truffleRuntime).registerOptimizedAssumptionDependency(optimizedAssumption);
    }

    @TruffleFromLibGraal(AsCompilableTruffleAST)
    static Object asCompilableTruffleAST(Object truffleRuntime, long constantHandle) {
        JavaConstant constant = LibGraal.unhand(JavaConstant.class, constantHandle);
        return ((HotSpotTruffleCompilerRuntime) truffleRuntime).asCompilableTruffleAST(constant);
    }

    @TruffleFromLibGraal(FindDecision)
    static Object findDecision(Object inliningPlan, long callNodeHandle) {
        JavaConstant callNode = LibGraal.unhand(JavaConstant.class, callNodeHandle);
        return ((TruffleInliningPlan) inliningPlan).findDecision(callNode);
    }

    @TruffleFromLibGraal(GetPosition)
    static Object getPosition(Object inliningPlan, long callNodeHandle) {
        JavaConstant callNode = LibGraal.unhand(JavaConstant.class, callNodeHandle);
        return ((TruffleInliningPlan) inliningPlan).getPosition(callNode);
    }

    @TruffleFromLibGraal(GetNodeRewritingAssumption)
    static long getNodeRewritingAssumption(Object decision) {
        JavaConstant assumption = ((TruffleInliningPlan.Decision) decision).getNodeRewritingAssumption();
        return LibGraal.translate(assumption);
    }

    @TruffleFromLibGraal(GetNodeRewritingAssumptionConstant)
    static long getNodeRewritingAssumptionConstant(Object compilable) {
        JavaConstant assumption = ((CompilableTruffleAST) compilable).getNodeRewritingAssumptionConstant();
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

    private static JavaKind getJavaKind(int basicType) {
        JavaKind javaKind = JAVA_KINDS.get(basicType);
        if (javaKind == null) {
            throw new IllegalArgumentException("Unknown JavaKind basic type: " + basicType);
        }
        return javaKind;
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

    @TruffleFromLibGraal(GetTargetName)
    static String getTargetName(Object decision) {
        return ((Decision) decision).getTargetName();
    }

    @TruffleFromLibGraal(IsTargetStable)
    static boolean isTargetStable(Object decision) {
        return ((Decision) decision).isTargetStable();
    }

    @TruffleFromLibGraal(OnCompilationFailed)
    static void onCompilationFailed(Object compilable, Supplier<String> serializedException, boolean bailout, boolean permanentBailout) {
        ((CompilableTruffleAST) compilable).onCompilationFailed(serializedException, bailout, permanentBailout);
    }

    @TruffleFromLibGraal(OnSuccess)
    static void onSuccess(Object listener, Object compilable, Object plan, long graphInfoHandle, long compilationResultInfoHandle) {
        try (LibGraalGraphInfo graphInfo = new LibGraalGraphInfo(graphInfoHandle);
                        LibGraalCompilationResultInfo compilationResultInfo = new LibGraalCompilationResultInfo(compilationResultInfoHandle)) {
            ((TruffleCompilerListener) listener).onSuccess((CompilableTruffleAST) compilable, (TruffleInliningPlan) plan, graphInfo, compilationResultInfo);
        }
    }

    @TruffleFromLibGraal(OnFailure)
    static void onFailure(Object listener, Object compilable, String reason, boolean bailout, boolean permanentBailout) {
        ((TruffleCompilerListener) listener).onFailure((CompilableTruffleAST) compilable, reason, bailout, permanentBailout);
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
            ((TruffleCompilerListener) listener).onTruffleTierFinished((CompilableTruffleAST) compilable, (TruffleInliningPlan) plan, graphInfo);
        }
    }

    @TruffleFromLibGraal(ShouldInline)
    static boolean shouldInline(Object decision) {
        return ((Decision) decision).shouldInline();
    }

    @TruffleFromLibGraal(CancelCompilation)
    static boolean cancelCompilation(Object compilableTruffleAST, String reason) {
        return ((CompilableTruffleAST) compilableTruffleAST).cancelCompilation(reason);
    }

    @TruffleFromLibGraal(FindCallNode)
    static Object findCallNode(Object provider, long callNodeHandle) {
        JavaConstant callNode = LibGraal.unhand(JavaConstant.class, callNodeHandle);
        return ((TruffleMetaAccessProvider) provider).findCallNode(callNode);
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

    @TruffleFromLibGraal(GetNonTrivialNodeCount)
    static int getNonTrivialNodeCount(Object compilableTruffleAST) {
        return ((CompilableTruffleAST) compilableTruffleAST).getNonTrivialNodeCount();
    }

    @TruffleFromLibGraal(AddTargetToDequeue)
    static void addTargetToDequeue(Object inliningPlan, Object compilableTruffleAST) {
        ((TruffleInliningPlan) inliningPlan).addTargetToDequeue((CompilableTruffleAST) compilableTruffleAST);
    }

    @TruffleFromLibGraal(DequeueTargets)
    static void dequeueTargets(Object inliningPlan) {
        ((TruffleInliningPlan) inliningPlan).dequeueTargets();
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
