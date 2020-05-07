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

import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.AsCompilableTruffleAST;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.AsJavaConstant;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.CallNodeHashCode;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.CancelInstalledTask;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.CompilableToString;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.ConsumeOptimizedAssumptionDependency;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.CreateInliningPlan;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.CreateStringSupplier;
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
    static void consumeOptimizedAssumptionDependency(Consumer<OptimizedAssumptionDependency> consumer, OptimizedAssumptionDependency dep) {
        consumer.accept(dep);
    }

    @TruffleFromLibGraal(GetCallTargetForCallNode)
    static long getCallTargetForCallNode(HotSpotTruffleCompilerRuntime truffleRuntime, long callNodeHandle) {
        JavaConstant callNode = LibGraal.unhand(JavaConstant.class, callNodeHandle);
        JavaConstant callTarget = truffleRuntime.getCallTargetForCallNode(callNode);
        return LibGraal.translate(callTarget);
    }

    @TruffleFromLibGraal(IsTruffleBoundary)
    static boolean isTruffleBoundary(HotSpotTruffleCompilerRuntime truffleRuntime, long methodHandle) {
        ResolvedJavaMethod method = LibGraal.unhand(ResolvedJavaMethod.class, methodHandle);
        return truffleRuntime.isTruffleBoundary(method);
    }

    @TruffleFromLibGraal(IsValueType)
    static boolean isValueType(HotSpotTruffleCompilerRuntime truffleRuntime, long typeHandle) {
        ResolvedJavaType type = LibGraal.unhand(ResolvedJavaType.class, typeHandle);
        return truffleRuntime.isValueType(type);
    }

    @TruffleFromLibGraal(GetInlineKind)
    static int getInlineKind(HotSpotTruffleCompilerRuntime truffleRuntime, long methodHandle, boolean duringPartialEvaluation) {
        ResolvedJavaMethod method = LibGraal.unhand(ResolvedJavaMethod.class, methodHandle);
        TruffleCompilerRuntime.InlineKind inlineKind = truffleRuntime.getInlineKind(method, duringPartialEvaluation);
        return inlineKind.ordinal();
    }

    @TruffleFromLibGraal(GetLoopExplosionKind)
    static int getLoopExplosionKind(HotSpotTruffleCompilerRuntime truffleRuntime, long methodHandle) {
        ResolvedJavaMethod method = LibGraal.unhand(ResolvedJavaMethod.class, methodHandle);
        TruffleCompilerRuntime.LoopExplosionKind loopExplosionKind = truffleRuntime.getLoopExplosionKind(method);
        return loopExplosionKind.ordinal();
    }

    @TruffleFromLibGraal(GetConstantFieldInfo)
    static int getConstantFieldInfo(HotSpotTruffleCompilerRuntime truffleRuntime, long typeHandle, boolean isStatic, int fieldIndex) {
        ResolvedJavaType enclosing = LibGraal.unhand(ResolvedJavaType.class, typeHandle);
        ResolvedJavaField[] declaredFields = isStatic ? enclosing.getStaticFields() : enclosing.getInstanceFields(false);
        ResolvedJavaField field = declaredFields[fieldIndex];

        TruffleCompilerRuntime.ConstantFieldInfo constantFieldInfo = truffleRuntime.getConstantFieldInfo(field);
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
    static int getJavaKindForFrameSlotKind(HotSpotTruffleCompilerRuntime truffleRuntime, int frameSlotKindTag) {
        JavaKind kind = truffleRuntime.getJavaKindForFrameSlotKind(frameSlotKindTag);
        return kind.getBasicType();
    }

    @TruffleFromLibGraal(Id.GetFrameSlotKindTagsCount)
    static int getFrameSlotKindTagsCount(HotSpotTruffleCompilerRuntime truffleRuntime) {
        return truffleRuntime.getFrameSlotKindTagsCount();
    }

    @TruffleFromLibGraal(GetTruffleCallBoundaryMethods)
    static long[] getTruffleCallBoundaryMethods(HotSpotTruffleCompilerRuntime truffleRuntime) {
        Collection<ResolvedJavaMethod> source;
        Iterable<ResolvedJavaMethod> iterable = truffleRuntime.getTruffleCallBoundaryMethods();
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
    static int getFrameSlotKindTagForJavaKind(HotSpotTruffleCompilerRuntime truffleRuntime, int basicType) {
        JavaKind kind = getJavaKind(basicType);
        return truffleRuntime.getFrameSlotKindTagForJavaKind(kind);
    }

    @TruffleFromLibGraal(Log)
    static void log(HotSpotTruffleCompilerRuntime truffleRuntime, CompilableTruffleAST compilable, String message) {
        truffleRuntime.log(compilable, message);
    }

    @TruffleFromLibGraal(CreateInliningPlan)
    static TruffleInliningPlan createInliningPlan(HotSpotTruffleCompilerRuntime truffleRuntime, CompilableTruffleAST compilable, TruffleCompilationTask task) {
        return truffleRuntime.createInliningPlan(compilable, task);
    }

    @TruffleFromLibGraal(RegisterOptimizedAssumptionDependency)
    static Consumer<OptimizedAssumptionDependency> registerOptimizedAssumptionDependency(HotSpotTruffleCompilerRuntime truffleRuntime, long optimizedAssumptionHandle) {
        JavaConstant optimizedAssumption = LibGraal.unhand(JavaConstant.class, optimizedAssumptionHandle);
        return truffleRuntime.registerOptimizedAssumptionDependency(optimizedAssumption);
    }

    @TruffleFromLibGraal(AsCompilableTruffleAST)
    static CompilableTruffleAST asCompilableTruffleAST(HotSpotTruffleCompilerRuntime truffleRuntime, long constantHandle) {
        JavaConstant constant = LibGraal.unhand(JavaConstant.class, constantHandle);
        return truffleRuntime.asCompilableTruffleAST(constant);
    }

    @TruffleFromLibGraal(FindDecision)
    static TruffleInliningPlan.Decision findDecision(TruffleInliningPlan inliningPlan, long callNodeHandle) {
        JavaConstant callNode = LibGraal.unhand(JavaConstant.class, callNodeHandle);
        return inliningPlan.findDecision(callNode);
    }

    @TruffleFromLibGraal(GetPosition)
    static TruffleSourceLanguagePosition getPosition(TruffleInliningPlan inliningPlan, long callNodeHandle) {
        JavaConstant callNode = LibGraal.unhand(JavaConstant.class, callNodeHandle);
        return inliningPlan.getPosition(callNode);
    }

    @TruffleFromLibGraal(GetNodeRewritingAssumption)
    static long getNodeRewritingAssumption(TruffleInliningPlan.Decision decision) {
        JavaConstant assumption = decision.getNodeRewritingAssumption();
        return LibGraal.translate(assumption);
    }

    @TruffleFromLibGraal(GetNodeRewritingAssumptionConstant)
    static long getNodeRewritingAssumptionConstant(CompilableTruffleAST compilable) {
        JavaConstant assumption = compilable.getNodeRewritingAssumptionConstant();
        return LibGraal.translate(assumption);
    }

    @TruffleFromLibGraal(GetURI)
    static String getURI(TruffleSourceLanguagePosition position) {
        URI uri = position.getURI();
        return uri == null ? null : uri.toString();
    }

    @TruffleFromLibGraal(AsJavaConstant)
    static long asJavaConstant(CompilableTruffleAST compilable) {
        JavaConstant constant = compilable.asJavaConstant();
        return LibGraal.translate(constant);
    }

    @TruffleFromLibGraal(OnCodeInstallation)
    static void onCodeInstallation(HotSpotTruffleCompilerRuntime truffleRuntime, CompilableTruffleAST compilable, long installedCodeHandle) {
        InstalledCode installedCode = LibGraal.unhand(InstalledCode.class, installedCodeHandle);
        truffleRuntime.onCodeInstallation(compilable, installedCode);
    }

    @TruffleFromLibGraal(GetFailedSpeculationsAddress)
    static long getFailedSpeculationsAddress(CompilableTruffleAST compilable) {
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
    static boolean isCancelled(TruffleCompilationTask task) {
        return task.isCancelled();
    }

    @TruffleFromLibGraal(IsLastTier)
    static boolean isLastTier(TruffleCompilationTask task) {
        return task.isLastTier();
    }

    @TruffleFromLibGraal(CompilableToString)
    static String compilableToString(CompilableTruffleAST compilable) {
        return compilable.toString();
    }

    @TruffleFromLibGraal(GetCompilableName)
    static String getCompilableName(CompilableTruffleAST compilable) {
        return compilable.getName();
    }

    @TruffleFromLibGraal(GetDescription)
    static String getDescription(TruffleSourceLanguagePosition pos) {
        return pos.getDescription();
    }

    @TruffleFromLibGraal(GetLanguage)
    static String getLanguage(TruffleSourceLanguagePosition pos) {
        return pos.getLanguage();
    }

    @TruffleFromLibGraal(GetLineNumber)
    static int getLineNumber(TruffleSourceLanguagePosition pos) {
        return pos.getLineNumber();
    }

    @TruffleFromLibGraal(GetOffsetEnd)
    static int getOffsetEnd(TruffleSourceLanguagePosition pos) {
        return pos.getOffsetEnd();
    }

    @TruffleFromLibGraal(GetOffsetStart)
    static int getOffsetStart(TruffleSourceLanguagePosition pos) {
        return pos.getOffsetStart();
    }

    @TruffleFromLibGraal(GetTargetName)
    static String getTargetName(Decision decision) {
        return decision.getTargetName();
    }

    @TruffleFromLibGraal(IsTargetStable)
    static boolean isTargetStable(Decision decision) {
        return decision.isTargetStable();
    }

    @TruffleFromLibGraal(OnCompilationFailed)
    static void onCompilationFailed(CompilableTruffleAST compilable, Supplier<String> serializedException, boolean bailout, boolean permanentBailout) {
        compilable.onCompilationFailed(serializedException, bailout, permanentBailout);
    }

    @TruffleFromLibGraal(OnSuccess)
    static void onSuccess(TruffleCompilerListener listener, CompilableTruffleAST compilable, TruffleInliningPlan plan, long graphInfoHandle, long compilationResultInfoHandle) {
        try (LibGraalGraphInfo graphInfo = new LibGraalGraphInfo(graphInfoHandle);
                        LibGraalCompilationResultInfo compilationResultInfo = new LibGraalCompilationResultInfo(compilationResultInfoHandle)) {
            listener.onSuccess(compilable, plan, graphInfo, compilationResultInfo);
        }
    }

    @TruffleFromLibGraal(OnFailure)
    static void onFailure(TruffleCompilerListener listener, CompilableTruffleAST compilable, String reason, boolean bailout, boolean permanentBailout) {
        listener.onFailure(compilable, reason, bailout, permanentBailout);
    }

    @TruffleFromLibGraal(OnGraalTierFinished)
    static void onGraalTierFinished(TruffleCompilerListener listener, CompilableTruffleAST compilable, long graphInfoHandle) {
        try (LibGraalGraphInfo graphInfo = new LibGraalGraphInfo(graphInfoHandle)) {
            listener.onGraalTierFinished(compilable, graphInfo);
        }
    }

    @TruffleFromLibGraal(OnTruffleTierFinished)
    static void onTruffleTierFinished(TruffleCompilerListener listener, CompilableTruffleAST compilable, TruffleInliningPlan plan, long graphInfoHandle) {
        try (LibGraalGraphInfo graphInfo = new LibGraalGraphInfo(graphInfoHandle)) {
            listener.onTruffleTierFinished(compilable, plan, graphInfo);
        }
    }

    @TruffleFromLibGraal(ShouldInline)
    static boolean shouldInline(Decision decision) {
        return decision.shouldInline();
    }

    @TruffleFromLibGraal(CancelInstalledTask)
    static void cancelInstalledTask(CompilableTruffleAST compilableTruffleAST) {
        compilableTruffleAST.cancelInstalledTask();
    }

    @TruffleFromLibGraal(FindCallNode)
    static TruffleCallNode findCallNode(TruffleMetaAccessProvider provider, long callNodeHandle) {
        JavaConstant callNode = LibGraal.unhand(JavaConstant.class, callNodeHandle);
        return provider.findCallNode(callNode);
    }

    @TruffleFromLibGraal(GetCallCount)
    static int getCallCount(TruffleCallNode callNode) {
        return callNode.getCallCount();
    }

    @TruffleFromLibGraal(GetCurrentCallTarget)
    static CompilableTruffleAST getCurrentCallTarget(TruffleCallNode truffleCallNode) {
        return truffleCallNode.getCurrentCallTarget();
    }

    @TruffleFromLibGraal(IsInliningForced)
    static boolean isInliningForced(TruffleCallNode truffleCallNode) {
        return truffleCallNode.isInliningForced();
    }

    @TruffleFromLibGraal(CallNodeHashCode)
    static int callNodeHashCode(TruffleCallNode truffleCallNode) {
        return truffleCallNode.hashCode();
    }

    @TruffleFromLibGraal(GetCompilableCallCount)
    static int getCompilableCallCount(CompilableTruffleAST compilableTruffleAST) {
        return compilableTruffleAST.getCallCount();
    }

    @TruffleFromLibGraal(GetCallNodes)
    static TruffleCallNode[] getCallNodes(CompilableTruffleAST compilableTruffleAST) {
        return compilableTruffleAST.getCallNodes();
    }

    @TruffleFromLibGraal(GetKnownCallSiteCount)
    static int getKnownCallSiteCount(CompilableTruffleAST compilableTruffleAST) {
        return compilableTruffleAST.getKnownCallSiteCount();
    }

    @TruffleFromLibGraal(IsSameOrSplit)
    static boolean isSameOrSplit(CompilableTruffleAST compilableTruffleAST1, CompilableTruffleAST compilableTruffleAST2) {
        return compilableTruffleAST1.isSameOrSplit(compilableTruffleAST2);
    }

    @TruffleFromLibGraal(GetNonTrivialNodeCount)
    static int getNonTrivialNodeCount(CompilableTruffleAST compilableTruffleAST) {
        return compilableTruffleAST.getNonTrivialNodeCount();
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
