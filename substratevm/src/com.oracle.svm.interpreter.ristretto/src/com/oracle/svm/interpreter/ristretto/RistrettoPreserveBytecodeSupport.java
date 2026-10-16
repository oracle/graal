/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.ristretto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.guest.staging.core.heap.RestrictHeapAccess;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.interpreter.BuildTimeInterpreterUniverse;
import com.oracle.svm.hosted.code.RestrictHeapAccessCalleesImpl;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.MethodFilter;

/**
 * Retains AOT method bodies for inlining into Ristretto runtime compilations and for interpreting
 * their inlined frames after deoptimization.
 *
 * <p>
 * Exact selections become analysis roots; wildcard selections use methods already reachable from
 * the application. During analysis, selected bodies expose field accesses, callees and dynamic-call
 * appendices that AOT optimization may have removed. Later callbacks check transitive heap
 * restrictions and whether the final interpreter pool can still execute each selected body.
 * All instance state is owned by these serial feature callbacks, not by analysis worker tasks.
 *
 * TODO GR-78803: Load preserved bodies from jars instead of the image heap.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class RistrettoPreserveBytecodeSupport {
    static final String LOG_SCOPE = "PreserveBytecodes";

    /** Optional application selection; required runtime adapters are selected independently. */
    private PreservedBytecodeSelection selection;
    /** Required implementation bodies, independent of application filters and reachability policy. */
    private final Set<AnalysisMethod> defaultPreservedBytecodeMethods = new HashSet<>();
    private final PreservedBytecodeDependencies dependencies = new PreservedBytecodeDependencies(defaultPreservedBytecodeMethods);

    private static final String DEFAULT_SELECTION_SOURCE = "the required Ristretto adapter baseline";

    /* Real adapter frames keep the allocated object alive across the void constructor call. */
    private static final String DEFAULT_PRESERVED_METHODS = "java.lang.invoke.Invokers$Holder.invokeExact_MT," +
                    "java.lang.invoke.DirectMethodHandle$Holder.newInvokeSpecial," +
                    "java.lang.invoke.DirectMethodHandle.allocateInstance," +
                    "java.lang.invoke.DirectMethodHandle.constructorMethod," +
                    "java.lang.invoke.MethodHandle.type," +
                    "java.lang.invoke.Invokers.checkExactType," +
                    "java.lang.invoke.Invokers.checkCustomized";

    /** Bodies already retained, so later analysis iterations do not scan them again. */
    private final Set<AnalysisMethod> bytecodePreservedMethodsForRuntimeInlining = new HashSet<>();

    /** Required selections: unlike wildcard matches, these must survive all later checks. */
    private final Set<AnalysisMethod> exactPreservedBytecodeMethods = new HashSet<>();

    private boolean exactRootsRegistered;

    /** Rejected wildcard matches and their current reasons, reported after heap restrictions settle. */
    private final Map<AnalysisMethod, String> skippedPreservedBytecodeMethods = new HashMap<>();

    /**
     * Parses the preserve filter and resolves its exact entries before normal reachability begins.
     *
     * <p>
     * Exact selections are resolved here and rooted in the first analysis callback, after ordinary
     * application roots have been processed, so the additional-reachability policy can inspect them.
     * Wildcard and negated entries remain with the regular {@link MethodFilter} pass because they
     * can be applied only after analysis has discovered candidate methods.
     */
    void beforeAnalysis(FeatureImpl.BeforeAnalysisAccessImpl access) {
        String preserveMethodFilter = RistrettoOptions.JITPreserveBytecodesForRuntime.getValue();
        if (preserveMethodFilter != null && !preserveMethodFilter.isBlank()) {
            selection = PreservedBytecodeSelection.parse(preserveMethodFilter);
        }
        PreservedBytecodeDependencies.registerRuntimeMetadata(access);
        PreservedBytecodeSelection.parse(DEFAULT_PRESERVED_METHODS).forEachExactMethod(access, DEFAULT_SELECTION_SOURCE, method -> {
            defaultPreservedBytecodeMethods.add(method);
            selectExactMethod(access, method);
        });
        if (selection != null) {
            selection.forEachExactMethod(access, method -> selectExactMethod(access, method));
        }
    }

    /**
     * Retains newly reachable eligible bytecode bodies and requests another analysis iteration when
     * that retention exposes new metadata.
     *
     * <p>
     * The normal analysis callback is the only place where wildcard filter matches can be observed,
     * so this pass repeatedly closes over those reachable runtime-compilation candidates.
     */
    void duringAnalysis(Feature.DuringAnalysisAccess a) {
        FeatureImpl.DuringAnalysisAccessImpl access = (FeatureImpl.DuringAnalysisAccessImpl) a;
        boolean registeredExactRoots = false;
        if (!exactRootsRegistered) {
            exactRootsRegistered = true;
            List<AnalysisMethod> exactMethods = new ArrayList<>(exactPreservedBytecodeMethods);
            sortMethods(exactMethods);
            dependencies.registerExactRoots(access, exactMethods);
            registeredExactRoots = !exactMethods.isEmpty();
        }
        boolean preservedNewMethod = preserveNewlyReachableRuntimeCompilationBytecodes(access);
        boolean registeredNewAppendix = dependencies.registerNewInvokeDynamicAppendices(access);
        if (registeredExactRoots || preservedNewMethod || registeredNewAppendix) {
            access.requireAnalysisIteration();
        }
    }

    /** Rechecks exact roots and field semantics after analysis reaches its final fixed point. */
    void afterAnalysis(FeatureImpl.AfterAnalysisAccessImpl access) {
        List<AnalysisMethod> missingExactMethods = new ArrayList<>(exactPreservedBytecodeMethods);
        missingExactMethods.removeAll(bytecodePreservedMethodsForRuntimeInlining);
        if (!missingExactMethods.isEmpty()) {
            sortMethods(missingExactMethods);
            verifyDefaultBodies(missingExactMethods, "analysis did not retain their bodies");
            throw UserError.abort("Cannot preserve bytecodes for exact method(s) selected by %s because analysis did not retain their bodies. " +
                            "Instance-method roots require an instantiated receiver type:%n%s",
                            RistrettoOptions.JITPreserveBytecodesForRuntime.getName(), formatMethods(missingExactMethods));
        }
        verifyFinalPreservedBytecodeEligibility(access);
    }

    /**
     * Applies heap restrictions once transitive restrictions are known from the hosted call graph.
     * Unsupported exact selections fail the build. Wildcard selections lose their interpreter body
     * and keep an AOT entry for execution, unless strict late-rejection handling is enabled.
     * Reachability established during analysis is not undone.
     */
    void beforeCompilation(FeatureImpl.BeforeCompilationAccessImpl access) {
        applyHeapRestrictions(access);
        reportSkippedMethods(access.getDebugContext());
    }

    private void applyHeapRestrictions(FeatureImpl.BeforeCompilationAccessImpl access) {
        Set<AnalysisMethod> selected = new HashSet<>(bytecodePreservedMethodsForRuntimeInlining);
        selected.addAll(skippedPreservedBytecodeMethods.keySet());
        List<AnalysisMethod> methods = new ArrayList<>(selected);
        sortMethods(methods);
        for (AnalysisMethod method : methods) {
            RestrictHeapAccess.Access restriction = RestrictHeapAccessCalleesImpl.heapAccessRestriction(method);
            if (restriction == RestrictHeapAccess.Access.UNRESTRICTED) {
                continue;
            }
            String rejectionReason = "heap access is restricted to " + restriction;
            if (exactPreservedBytecodeMethods.contains(method)) {
                throw UserError.abort("Cannot keep bytecodes for exact method '%s' selected by %s: %s.", method,
                                selectionSource(method), rejectionReason);
            }
            if (bytecodePreservedMethodsForRuntimeInlining.contains(method)) {
                reportLateWildcardRejection(access.getDebugContext(), List.of(method), rejectionReason);
            }
            HostedMethod hostedMethod = access.getUniverse().optionalLookup(method);
            if (hostedMethod != null && method.getAnalyzedGraph() != null) {
                // A restricted body can only execute through AOT code, even if ordinarily inlined.
                SubstrateCompilationDirectives.singleton().registerForcedCompilation(hostedMethod);
            }
            InterpreterResolvedJavaMethod interpreterMethod = BuildTimeInterpreterUniverse.singleton().getMethod(method);
            if (interpreterMethod != null) {
                interpreterMethod.setCode(null);
            }
            bytecodePreservedMethodsForRuntimeInlining.remove(method);
            skippedPreservedBytecodeMethods.put(method, rejectionReason);
        }
    }

    private void reportSkippedMethods(DebugContext debug) {
        if (skippedPreservedBytecodeMethods.isEmpty()) {
            return;
        }
        try (DebugContext.Scope _ = debug.scope(LOG_SCOPE)) {
            if (!debug.isLogEnabled()) {
                return;
            }
            List<Map.Entry<AnalysisMethod, String>> skipped = new ArrayList<>(skippedPreservedBytecodeMethods.entrySet());
            skipped.sort((left, right) -> left.getKey().format("%H.%n(%p)").compareTo(right.getKey().format("%H.%n(%p)")));

            StringBuilder message = new StringBuilder("Skipped ").append(skipped.size()).append(" unsupported method(s) matched by ")
                            .append(RistrettoOptions.JITPreserveBytecodesForRuntime.getName()).append(':');
            int reported = Math.min(skipped.size(), 20);
            for (int i = 0; i < reported; i++) {
                Map.Entry<AnalysisMethod, String> entry = skipped.get(i);
                message.append(System.lineSeparator()).append("  ").append(entry.getKey().format("%H.%n(%p)"))
                                .append(": ").append(entry.getValue());
            }
            if (reported < skipped.size()) {
                message.append(System.lineSeparator()).append("  ... and ").append(skipped.size() - reported).append(" more");
            }
            debug.log("%s", message);
        }
    }

    /**
     * Rechecks retained methods once analysis has reached its final fixed point. A reference that
     * was deliberately treated as unresolved during an earlier iteration may have resolved later
     * to hosted-only field semantics. Retention is irreversible at that point, so fail the build
     * instead of reporting that the method was skipped while leaving unsafe bytecodes available.
     */
    private void verifyFinalPreservedBytecodeEligibility(FeatureImpl.AfterAnalysisAccessImpl access) {
        List<AnalysisMethod> preservedMethods = new ArrayList<>(bytecodePreservedMethodsForRuntimeInlining);
        sortMethods(preservedMethods);
        for (AnalysisMethod method : preservedMethods) {
            String rejectionReason = PreservedBytecodeEligibility.rejectionReason(method, (SVMHost) access.getUniverse().hostVM(), access.getMetaAccess(),
                            defaultPreservedBytecodeMethods.contains(method));
            if (rejectionReason != null) {
                throw UserError.abort("Cannot keep bytecodes previously preserved for method '%s' selected by %s: final analysis resolved %s.", method,
                                selectionSource(method), rejectionReason);
            }
        }
    }

    /**
     * Reports selected bodies removed by final interpreter executable-callee pruning.
     * {@link RistrettoFeature#afterCompilation} calls this after pool hydration, reusing the debugger's
     * complete pools when enabled. Exact requests must fail instead of silently losing the requested
     * body; wildcard requests retain their best-effort semantics with opt-in late-pruning
     * diagnostics, or fail under the strict option.
     */
    void afterCompilation(FeatureImpl.AfterCompilationAccessImpl access) {
        List<AnalysisMethod> exactPrunedMethods = new ArrayList<>();
        List<AnalysisMethod> wildcardPrunedMethods = new ArrayList<>();
        BuildTimeInterpreterUniverse interpreterUniverse = BuildTimeInterpreterUniverse.singleton();
        for (AnalysisMethod method : bytecodePreservedMethodsForRuntimeInlining) {
            InterpreterResolvedJavaMethod interpreterMethod = interpreterUniverse.getMethod(method);
            if (interpreterMethod == null || interpreterMethod.getInterpretedCode() == null) {
                (exactPreservedBytecodeMethods.contains(method) ? exactPrunedMethods : wildcardPrunedMethods).add(method);
            }
        }
        sortMethods(exactPrunedMethods);
        sortMethods(wildcardPrunedMethods);
        if (!exactPrunedMethods.isEmpty()) {
            verifyDefaultBodies(exactPrunedMethods, "final interpreter executable-callee pruning removed their bodies");
            throw UserError.abort("Cannot keep bytecodes for exact method(s) selected by %s because final interpreter executable-callee pruning removed their bodies:%n%s",
                            RistrettoOptions.JITPreserveBytecodesForRuntime.getName(), formatMethods(exactPrunedMethods));
        }
        if (!wildcardPrunedMethods.isEmpty()) {
            reportLateWildcardRejection(access.getDebugContext(), wildcardPrunedMethods, "final interpreter executable-callee pruning removed their bodies");
        }
    }

    /**
     * Reports only bodies that were already preserved before rejection, not early exclusions.
     * Analysis registrations have no rollback here, so a skipped body can leave dependencies behind.
     */
    private static void reportLateWildcardRejection(DebugContext debug, List<AnalysisMethod> methods, String reason) {
        try (DebugContext.Scope _ = debug.scope(LOG_SCOPE)) {
            boolean failOnLateRejection = RistrettoOptions.JITPreserveBytecodesFailOnLateRejection.getValue();
            if (failOnLateRejection || debug.isLogEnabled()) {
                String message = String.format("Cannot keep bytecodes previously preserved for wildcard method(s) selected by %s: %s.%n%s%n" +
                                "Dependencies registered during preservation analysis are not removed and may remain in the image.",
                                RistrettoOptions.JITPreserveBytecodesForRuntime.getName(), reason, formatMethods(methods));
                if (failOnLateRejection) {
                    throw UserError.abort("%s%n%s is enabled.", message, RistrettoOptions.JITPreserveBytecodesFailOnLateRejection.getName());
                }
                debug.log("%s%nSkipping these bodies. Enable %s to fail image generation on late rejection.", message,
                                RistrettoOptions.JITPreserveBytecodesFailOnLateRejection.getName());
            }
        }
    }

    private String selectionSource(AnalysisMethod method) {
        return defaultPreservedBytecodeMethods.contains(method) ? DEFAULT_SELECTION_SOURCE : RistrettoOptions.JITPreserveBytecodesForRuntime.getName();
    }

    private void verifyDefaultBodies(List<AnalysisMethod> missingMethods, String reason) {
        List<AnalysisMethod> missingDefaults = missingMethods.stream().filter(defaultPreservedBytecodeMethods::contains).toList();
        if (!missingDefaults.isEmpty()) {
            throw UserError.abort("Cannot keep bytecodes required by the Ristretto adapter baseline because %s:%n%s", reason, formatMethods(missingDefaults));
        }
    }

    /** Orders diagnostics by holder, method name and parameter types, independent of set iteration. */
    private static void sortMethods(List<AnalysisMethod> methods) {
        methods.sort((left, right) -> left.format("%H.%n(%p)").compareTo(right.format("%H.%n(%p)")));
    }

    private static String formatMethods(List<AnalysisMethod> methods) {
        StringBuilder result = new StringBuilder();
        int reported = Math.min(methods.size(), 20);
        for (int i = 0; i < reported; i++) {
            if (i != 0) {
                result.append(System.lineSeparator());
            }
            result.append("  ").append(methods.get(i).format("%H.%n(%p)"));
        }
        if (reported < methods.size()) {
            result.append(System.lineSeparator()).append("  ... and ").append(methods.size() - reported).append(" more");
        }
        return result.toString();
    }

    /** Selects an eligible exact root for the first analysis callback, rejecting unsupported methods first. */
    private void selectExactMethod(FeatureImpl.BeforeAnalysisAccessImpl access, AnalysisMethod method) {
        String rejectionReason = PreservedBytecodeEligibility.rejectionReason(method, access.getHostVM(), access.getMetaAccess(), defaultPreservedBytecodeMethods.contains(method));
        if (rejectionReason != null) {
            throw UserError.abort("Cannot preserve bytecodes for unsupported method '%s' selected by %s: %s.", method, selectionSource(method),
                            rejectionReason);
        }
        exactPreservedBytecodeMethods.add(method);
    }

    /**
     * Retains runtime-compilation bytecodes for newly reachable, eligible methods selected by the
     * preserve filter. Each method is processed once; discovering one changes interpreter metadata
     * and therefore requires another analysis iteration.
     *
     * @return whether at least one method was newly processed
     */
    private boolean preserveNewlyReachableRuntimeCompilationBytecodes(FeatureImpl.DuringAnalysisAccessImpl access) {
        boolean matchedNewMethod = false;
        for (AnalysisMethod method : access.getUniverse().getMethods()) {
            if (!method.isOriginalMethod() || method.isAbstract()) {
                continue;
            }
            if (!method.isReachable() || !(defaultPreservedBytecodeMethods.contains(method) || selection != null && selection.matches(method))) {
                continue;
            }
            if (bytecodePreservedMethodsForRuntimeInlining.contains(method)) {
                continue;
            }
            String rejectionReason = PreservedBytecodeEligibility.rejectionReason(method, access.getHostVM(), access.getMetaAccess(), defaultPreservedBytecodeMethods.contains(method));
            if (rejectionReason != null) {
                if (exactPreservedBytecodeMethods.contains(method)) {
                    throw UserError.abort("Cannot preserve bytecodes for unsupported method '%s' selected by %s: %s.", method,
                                    selectionSource(method), rejectionReason);
                }
                skippedPreservedBytecodeMethods.put(method, rejectionReason);
                continue;
            }
            String invokeDynamicFailure = dependencies.registerInvokeDynamicInvokers(access, method);
            if (invokeDynamicFailure != null) {
                if (exactPreservedBytecodeMethods.contains(method)) {
                    throw UserError.abort("Cannot preserve bytecodes for unsupported method '%s' selected by %s: %s.", method,
                                    selectionSource(method), invokeDynamicFailure);
                }
                skippedPreservedBytecodeMethods.put(method, invokeDynamicFailure);
                continue;
            }
            skippedPreservedBytecodeMethods.remove(method);
            dependencies.registerInvokeTargets(access, method);
            dependencies.registerBytecodeDependencies(access, method);
            InterpreterResolvedJavaMethod interpreterMethod = BuildTimeInterpreterUniverse.singleton().getOrCreateMethodWithMethodBodyOnly(method, access.getMetaAccess());
            interpreterMethod.preserveRuntimeCompilationBytecodes();
            bytecodePreservedMethodsForRuntimeInlining.add(method);
            dependencies.registerPreservedInvokeDynamicAppendixSites(method);
            matchedNewMethod = true;
        }
        return matchedNewMethod;
    }

}
