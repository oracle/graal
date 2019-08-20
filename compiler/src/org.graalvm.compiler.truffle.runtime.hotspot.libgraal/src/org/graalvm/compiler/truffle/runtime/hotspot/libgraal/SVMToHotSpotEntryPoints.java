/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.AsCompilableTruffleAST;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.AsJavaConstant;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.CompilableToString;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.ConsumeOptimizedAssumptionDependency;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.CreateException;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.CreateInliningPlan;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.CreateStringSupplier;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.FindDecision;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetCallTargetForCallNode;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetCompilableName;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetConstantFieldInfo;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetDescription;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetFailedSpeculationsAddress;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetFrameSlotKindTagForJavaKind;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetInlineKind;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetLanguage;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetLineNumber;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetLoopExplosionKind;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetNodeRewritingAssumption;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetOffsetEnd;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetOffsetStart;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetPosition;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetStackTrace;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetStackTraceElementClassName;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetStackTraceElementFileName;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetStackTraceElementLineNumber;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetStackTraceElementMethodName;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetSuppliedString;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetTargetName;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetThrowableMessage;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetTruffleCallBoundaryMethods;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetURI;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.IsCancelled;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.IsLastTier;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.IsTargetStable;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.IsTruffleBoundary;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.IsValueType;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.Log;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.OnCodeInstallation;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.OnCompilationFailed;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.OnFailure;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.OnGraalTierFinished;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.OnSuccess;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.OnTruffleTierFinished;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.RegisterOptimizedAssumptionDependency;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.ShouldInline;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.UpdateStackTrace;

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
import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.TruffleInliningPlan;
import org.graalvm.compiler.truffle.common.TruffleInliningPlan.Decision;
import org.graalvm.compiler.truffle.common.TruffleSourceLanguagePosition;
import org.graalvm.compiler.truffle.common.hotspot.HotSpotTruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.libgraal.LibGraal;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotSpeculationLog;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Entry points in HotSpot for {@linkplain SVMToHotSpot calls} from SVM.
 */
final class SVMToHotSpotEntryPoints {

    private static final Map<Integer, JavaKind> JAVA_KINDS;
    static {
        Map<Integer, JavaKind> m = new HashMap<>();
        for (JavaKind jk : JavaKind.values()) {
            m.put(jk.getBasicType(), jk);
        }
        JAVA_KINDS = Collections.unmodifiableMap(m);

        assert checkHotSpotCalls();
    }

    private static final HotSpotJVMCIRuntime jvmciRuntime = HotSpotJVMCIRuntime.runtime();

    @SVMToHotSpot(ConsumeOptimizedAssumptionDependency)
    static void consumeOptimizedAssumptionDependency(Consumer<OptimizedAssumptionDependency> consumer, OptimizedAssumptionDependency dep) {
        consumer.accept(dep);
    }

    @SVMToHotSpot(GetCallTargetForCallNode)
    static long getCallTargetForCallNode(HotSpotTruffleCompilerRuntime truffleRuntime, long callNodeHandle) {
        JavaConstant callNode = LibGraal.unhand(jvmciRuntime, JavaConstant.class, callNodeHandle);
        JavaConstant callTarget = truffleRuntime.getCallTargetForCallNode(callNode);
        return LibGraal.translate(jvmciRuntime, callTarget);
    }

    @SVMToHotSpot(IsTruffleBoundary)
    static boolean isTruffleBoundary(HotSpotTruffleCompilerRuntime truffleRuntime, long methodHandle) {
        ResolvedJavaMethod method = LibGraal.unhand(jvmciRuntime, ResolvedJavaMethod.class, methodHandle);
        return truffleRuntime.isTruffleBoundary(method);
    }

    @SVMToHotSpot(IsValueType)
    static boolean isValueType(HotSpotTruffleCompilerRuntime truffleRuntime, long typeHandle) {
        ResolvedJavaType type = LibGraal.unhand(jvmciRuntime, ResolvedJavaType.class, typeHandle);
        return truffleRuntime.isValueType(type);
    }

    @SVMToHotSpot(GetInlineKind)
    static int getInlineKind(HotSpotTruffleCompilerRuntime truffleRuntime, long methodHandle, boolean duringPartialEvaluation) {
        ResolvedJavaMethod method = LibGraal.unhand(jvmciRuntime, ResolvedJavaMethod.class, methodHandle);
        TruffleCompilerRuntime.InlineKind inlineKind = truffleRuntime.getInlineKind(method, duringPartialEvaluation);
        return inlineKind.ordinal();
    }

    @SVMToHotSpot(GetLoopExplosionKind)
    static int getLoopExplosionKind(HotSpotTruffleCompilerRuntime truffleRuntime, long methodHandle) {
        ResolvedJavaMethod method = LibGraal.unhand(jvmciRuntime, ResolvedJavaMethod.class, methodHandle);
        TruffleCompilerRuntime.LoopExplosionKind loopExplosionKind = truffleRuntime.getLoopExplosionKind(method);
        return loopExplosionKind.ordinal();
    }

    @SVMToHotSpot(GetConstantFieldInfo)
    static int getConstantFieldInfo(HotSpotTruffleCompilerRuntime truffleRuntime, long typeHandle, boolean isStatic, int fieldIndex) {
        ResolvedJavaType enclosing = LibGraal.unhand(jvmciRuntime, ResolvedJavaType.class, typeHandle);
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

    @SVMToHotSpot(Id.GetJavaKindForFrameSlotKind)
    static int getJavaKindForFrameSlotKind(HotSpotTruffleCompilerRuntime truffleRuntime, int frameSlotKindTag) {
        JavaKind kind = truffleRuntime.getJavaKindForFrameSlotKind(frameSlotKindTag);
        return kind.getBasicType();
    }

    @SVMToHotSpot(Id.GetFrameSlotKindTagsCount)
    static int getFrameSlotKindTagsCount(HotSpotTruffleCompilerRuntime truffleRuntime) {
        return truffleRuntime.getFrameSlotKindTagsCount();
    }

    @SVMToHotSpot(GetTruffleCallBoundaryMethods)
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
            res[i++] = LibGraal.translate(jvmciRuntime, m);
        }
        return res;
    }

    @SVMToHotSpot(GetFrameSlotKindTagForJavaKind)
    static int getFrameSlotKindTagForJavaKind(HotSpotTruffleCompilerRuntime truffleRuntime, int basicType) {
        JavaKind kind = getJavaKind(basicType);
        return truffleRuntime.getFrameSlotKindTagForJavaKind(kind);
    }

    @SVMToHotSpot(Log)
    static void log(HotSpotTruffleCompilerRuntime truffleRuntime, String message) {
        truffleRuntime.log(message);
    }

    @SVMToHotSpot(CreateInliningPlan)
    static TruffleInliningPlan createInliningPlan(HotSpotTruffleCompilerRuntime truffleRuntime, CompilableTruffleAST compilable, TruffleCompilationTask task) {
        return truffleRuntime.createInliningPlan(compilable, task);
    }

    @SVMToHotSpot(RegisterOptimizedAssumptionDependency)
    static Consumer<OptimizedAssumptionDependency> registerOptimizedAssumptionDependency(HotSpotTruffleCompilerRuntime truffleRuntime, long optimizedAssumptionHandle) {
        JavaConstant optimizedAssumption = LibGraal.unhand(jvmciRuntime, JavaConstant.class, optimizedAssumptionHandle);
        return truffleRuntime.registerOptimizedAssumptionDependency(optimizedAssumption);
    }

    @SVMToHotSpot(AsCompilableTruffleAST)
    static CompilableTruffleAST asCompilableTruffleAST(HotSpotTruffleCompilerRuntime truffleRuntime, long constantHandle) {
        JavaConstant constant = LibGraal.unhand(jvmciRuntime, JavaConstant.class, constantHandle);
        return truffleRuntime.asCompilableTruffleAST(constant);
    }

    @SVMToHotSpot(FindDecision)
    static TruffleInliningPlan.Decision findDecision(TruffleInliningPlan inliningPlan, long callNodeHandle) {
        JavaConstant callNode = LibGraal.unhand(jvmciRuntime, JavaConstant.class, callNodeHandle);
        return inliningPlan.findDecision(callNode);
    }

    @SVMToHotSpot(GetPosition)
    static TruffleSourceLanguagePosition getPosition(TruffleInliningPlan inliningPlan, long callNodeHandle) {
        JavaConstant callNode = LibGraal.unhand(jvmciRuntime, JavaConstant.class, callNodeHandle);
        return inliningPlan.getPosition(callNode);
    }

    @SVMToHotSpot(GetNodeRewritingAssumption)
    static long getNodeRewritingAssumption(TruffleInliningPlan.Decision decision) {
        JavaConstant assumption = decision.getNodeRewritingAssumption();
        return LibGraal.translate(jvmciRuntime, assumption);
    }

    @SVMToHotSpot(GetURI)
    static String getURI(TruffleSourceLanguagePosition position) {
        URI uri = position.getURI();
        return uri == null ? null : uri.toString();
    }

    @SVMToHotSpot(AsJavaConstant)
    static long asJavaConstant(CompilableTruffleAST compilable) {
        JavaConstant constant = compilable.asJavaConstant();
        return LibGraal.translate(jvmciRuntime, constant);
    }

    @SVMToHotSpot(OnCodeInstallation)
    static void onCodeInstallation(HotSpotTruffleCompilerRuntime truffleRuntime, CompilableTruffleAST compilable, long installedCodeHandle) {
        InstalledCode installedCode = LibGraal.unhand(jvmciRuntime, InstalledCode.class, installedCodeHandle);
        truffleRuntime.onCodeInstallation(compilable, installedCode);
    }

    @SVMToHotSpot(GetFailedSpeculationsAddress)
    static long getFailedSpeculationsAddress(CompilableTruffleAST compilable) {
        OptimizedCallTarget callTarget = (OptimizedCallTarget) compilable;
        HotSpotSpeculationLog log = (HotSpotSpeculationLog) callTarget.getSpeculationLog();
        return log.getFailedSpeculationsAddress();
    }

    /**
     * Updates an exception stack trace by decoding a stack trace from SVM.
     *
     * @param target the {@link Throwable} to update
     * @param rawElements the stringified stack trace elements. Each element has a form
     *            {@code className|methodName|fileName|lineNumber}. If the fileName is missing it's
     *            encoded as an empty string.
     * @return the updated {@link Throwable}
     */
    @SVMToHotSpot(UpdateStackTrace)
    static Throwable updateStackTrace(Throwable target, String[] rawElements) {
        StackTraceElement[] elements = new StackTraceElement[rawElements.length];
        for (int i = 0; i < rawElements.length; i++) {
            String[] parts = rawElements[i].split("\\|");
            String className = parts[0];
            String methodName = parts[1];
            String fileName = parts[2];
            int lineNumber = Integer.parseInt(parts[3]);
            elements[i] = new StackTraceElement(className, methodName, fileName.isEmpty() ? null : fileName, lineNumber);
        }
        target.setStackTrace(elements);
        return target;
    }

    /**
     * Creates an exception used to throw native exception into Java code.
     *
     * @param message the exception message
     * @return exception
     */
    @SVMToHotSpot(CreateException)
    static Throwable createException(String message) {
        return new RuntimeException(message);
    }

    private static JavaKind getJavaKind(int basicType) {
        JavaKind javaKind = JAVA_KINDS.get(basicType);
        if (javaKind == null) {
            throw new IllegalArgumentException("Unknown JavaKind basic type: " + basicType);
        }
        return javaKind;
    }

    @SVMToHotSpot(CreateStringSupplier)
    static Supplier<String> createStringSupplier(long handle) {
        return new SVMStringSupplier(handle);
    }

    @SVMToHotSpot(GetSuppliedString)
    static String getSuppliedString(Supplier<String> supplier) {
        return supplier.get();
    }

    @SVMToHotSpot(IsCancelled)
    static boolean isCancelled(TruffleCompilationTask task) {
        return task.isCancelled();
    }

    @SVMToHotSpot(IsLastTier)
    static boolean isLastTier(TruffleCompilationTask task) {
        return task.isLastTier();
    }

    @SVMToHotSpot(CompilableToString)
    static String compilableToString(CompilableTruffleAST compilable) {
        return compilable.toString();
    }

    @SVMToHotSpot(GetCompilableName)
    static String getCompilableName(CompilableTruffleAST compilable) {
        return compilable.getName();
    }

    @SVMToHotSpot(GetDescription)
    static String getDescription(TruffleSourceLanguagePosition pos) {
        return pos.getDescription();
    }

    @SVMToHotSpot(GetLanguage)
    static String getLanguage(TruffleSourceLanguagePosition pos) {
        return pos.getLanguage();
    }

    @SVMToHotSpot(GetLineNumber)
    static int getLineNumber(TruffleSourceLanguagePosition pos) {
        return pos.getLineNumber();
    }

    @SVMToHotSpot(GetOffsetEnd)
    static int getOffsetEnd(TruffleSourceLanguagePosition pos) {
        return pos.getOffsetEnd();
    }

    @SVMToHotSpot(GetOffsetStart)
    static int getOffsetStart(TruffleSourceLanguagePosition pos) {
        return pos.getOffsetStart();
    }

    @SVMToHotSpot(GetStackTrace)
    static StackTraceElement[] getStackTrace(Throwable throwable) {
        return throwable.getStackTrace();
    }

    @SVMToHotSpot(GetStackTraceElementClassName)
    static String getStackTraceElementClassName(StackTraceElement element) {
        return element.getClassName();
    }

    @SVMToHotSpot(GetStackTraceElementFileName)
    static String getStackTraceElementFileName(StackTraceElement element) {
        return element.getFileName();
    }

    @SVMToHotSpot(GetStackTraceElementLineNumber)
    static int getStackTraceElementLineNumber(StackTraceElement element) {
        return element.getLineNumber();
    }

    @SVMToHotSpot(GetStackTraceElementMethodName)
    static String getStackTraceElementMethodName(StackTraceElement element) {
        return element.getMethodName();
    }

    @SVMToHotSpot(GetTargetName)
    static String getTargetName(Decision decision) {
        return decision.getTargetName();
    }

    @SVMToHotSpot(IsTargetStable)
    static boolean isTargetStable(Decision decision) {
        return decision.isTargetStable();
    }

    @SVMToHotSpot(OnCompilationFailed)
    static void onCompilationFailed(CompilableTruffleAST compilable, Supplier<String> reasonAndStackTrace, boolean bailout, boolean permanentBailout) {
        compilable.onCompilationFailed(reasonAndStackTrace, bailout, permanentBailout);
    }

    @SVMToHotSpot(OnSuccess)
    static void onSuccess(TruffleCompilerListener listener, CompilableTruffleAST compilable, TruffleInliningPlan plan, long graphInfoHandle, long compilationResultInfoHandle) {
        try (SVMGraphInfo graphInfo = new SVMGraphInfo(graphInfoHandle); SVMCompilationResultInfo compilationResultInfo = new SVMCompilationResultInfo(compilationResultInfoHandle)) {
            listener.onSuccess(compilable, plan, graphInfo, compilationResultInfo);
        }
    }

    @SVMToHotSpot(OnFailure)
    static void onFailure(TruffleCompilerListener listener, CompilableTruffleAST compilable, String reason, boolean bailout, boolean permanentBailout) {
        listener.onFailure(compilable, reason, bailout, permanentBailout);
    }

    @SVMToHotSpot(OnGraalTierFinished)
    static void onGraalTierFinished(TruffleCompilerListener listener, CompilableTruffleAST compilable, long graphInfoHandle) {
        try (SVMGraphInfo graphInfo = new SVMGraphInfo(graphInfoHandle)) {
            listener.onGraalTierFinished(compilable, graphInfo);
        }
    }

    @SVMToHotSpot(OnTruffleTierFinished)
    static void onTruffleTierFinished(TruffleCompilerListener listener, CompilableTruffleAST compilable, TruffleInliningPlan plan, long graphInfoHandle) {
        try (SVMGraphInfo graphInfo = new SVMGraphInfo(graphInfoHandle)) {
            listener.onTruffleTierFinished(compilable, plan, graphInfo);
        }
    }

    @SVMToHotSpot(ShouldInline)
    static boolean shouldInline(Decision decision) {
        return decision.shouldInline();
    }

    @SVMToHotSpot(GetThrowableMessage)
    static String getThrowableMessage(Throwable t) {
        return t.getMessage();
    }

    /*----------------------*/

    /**
     * Checks that all {@link SVMToHotSpot}s are implemented and that their signatures match the
     * {@linkplain Id#getSignature() ID signatures}.
     */
    private static boolean checkHotSpotCalls() {
        Set<Id> unimplemented = EnumSet.allOf(Id.class);
        for (Method method : SVMToHotSpotEntryPoints.class.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                SVMToHotSpot a = method.getAnnotation(SVMToHotSpot.class);
                if (a != null) {
                    Id id = a.value();
                    unimplemented.remove(id);
                    check(id, id.getMethodName().equals(method.getName()), "Expected name \"%s\", got \"%s\"", id.getMethodName(), method.getName());
                    check(id, id.getReturnType().equals(method.getReturnType()), "Expected return type %s, got %s", id.getReturnType().getName(), method.getReturnType().getName());
                    checkParameters(id, method.getParameterTypes());
                }
            }
        }
        check(null, unimplemented.isEmpty(), "Missing implementations:%n%s", unimplemented.stream().map(SVMToHotSpotEntryPoints::missingImpl).sorted().collect(joining(lineSeparator())));
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
        buf.format("    @%s(%s)%n", SVMToHotSpot.class.getSimpleName(), id.name());
        buf.format("    static %s %s(%s) {%n    }%n", id.getReturnType().getSimpleName(), id.getMethodName(), Stream.of(id.getParameterTypes()).map(c -> c.getSimpleName()).collect(joining(", ")));
        return buf.toString();
    }

    private static void check(Id id, boolean condition, String format, Object... args) {
        if (!condition) {
            String msg = format(format, args);
            if (id != null) {
                System.err.printf("ERROR: %s.%s: %s%n", SVMToHotSpotEntryPoints.class.getName(), id, msg);
            } else {
                System.err.printf("ERROR: %s: %s%n", SVMToHotSpotEntryPoints.class.getName(), msg);
            }
            System.exit(99);
        }
    }
}
