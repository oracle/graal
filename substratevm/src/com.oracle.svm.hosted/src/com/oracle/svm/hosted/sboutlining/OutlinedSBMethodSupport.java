/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.sboutlining;

import java.lang.invoke.MethodType;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.infrastructure.ResolvedSignature;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.sboutlining.OutlinedSBMethodHolder;
import com.oracle.svm.core.util.Counter;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.PartiallyLayerAware;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Build-time registry for outlined string aggregation methods and their diagnostics.
 *
 * <p>
 * The registry groups call sites by their result type and their ordered list of parameter types.
 * Before lookup, the outlining transformations replace reference parameter types with
 * {@link Object}. They preserve primitive parameter types, parameter order, and the result type.
 * For example, call sites with the types {@code String(String, int)} and
 * {@code String(CharSequence, int)} both use the group {@code String(Object, int)}. The registry
 * creates one {@link OutlinedSBMethod} for each group. {@link #lookup} registers the method in the
 * analysis universe.
 *
 * <p>
 * Analysis runs on multiple worker threads. Several threads can discover call sites in the same
 * group at the same time. The registry uses {@link ConcurrentHashMap#compute} to create the method
 * atomically, so all threads receive the same {@link OutlinedSBMethod} instance.
 *
 * <p>
 * This singleton also owns the global counters and optional per-method metrics collected by
 * {@link SBOutliningAnalysis}, {@link SBOutliningPhase}, and the invokedynamic plugin. These
 * diagnostics describe both rejected candidates and the materializations that replaced accepted
 * candidates.
 */
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = PartiallyLayerAware.class)
@Platforms(Platform.HOSTED_ONLY.class)
public class OutlinedSBMethodSupport {
    public static OutlinedSBMethodSupport singleton() {
        return ImageSingletons.lookup(OutlinedSBMethodSupport.class);
    }

    private final ConcurrentHashMap<MethodType, OutlinedSBMethod> outlinedSBMethods = new ConcurrentHashMap<>();

    private final Counter.Group counters = new Counter.Group(SBOutliningFeature.Options.PrintSBOutliningCounters, "SBOutliningStats");

    /**
     * Total number of SB allocation sites seen.
     */
    public final Counter totalSBs = new Counter(counters, "total_sbs", "");
    /**
     * Total number of SBs which have unhandled behavior. The logic for this is in
     * {@code SBOutliningAnalysis$SBAllocationAssigner#findEscapingUsesHelper}. Usually due to
     * floating uses which would be hard to map to a subsequent materialization.
     */
    public final Counter unhandledSBs = new Counter(counters, "unhandled_sbs", "");
    /**
     * Number of SBs we try to optimize.
     */
    public final Counter candidateSBs = new Counter(counters, "candidate_sbs", "");
    /**
     * Number of SBs we are able to successfully convert to outline calls.
     */
    public final Counter acceptedSBs = new Counter(counters, "accepted_sbs", "");
    /**
     * Number of SB unhandled append operations which cause an escape. These are inits and appends
     * which pass in a char[] or a (non-SB) CharSeq.
     */
    public final Counter unhandledAppendEscapingSBOps = new Counter(counters, "unhandled_append_escaping_sb_ops", "");
    /**
     * Number of calls originally due to SB initialization, appends, and toString operations.
     */
    public final Counter virtualizeCalls = new Counter(counters, "virtualized_total_calls", "");
    /**
     * Number of calls originally due to SB initialization.
     */
    public final Counter virtualizedInits = new Counter(counters, "virtualized_inits", "");
    /**
     * Number of calls originally to SB.append(...).
     */
    public final Counter virtualizedAppends = new Counter(counters, "virtualized_appends", "");
    /**
     * Number of calls originally to SB.toString().
     */
    public final Counter virtualizedToStrings = new Counter(counters, "virtualized_tostrings", "");
    /**
     * Number of calls to SB initialization, appends, and toString operations in graphs.
     */
    public final Counter sbCalls = new Counter(counters, "sb_total_calls", "");
    /**
     * Number of calls to SB initializations in graphs.
     */
    public final Counter sbInits = new Counter(counters, "sb_inits", "");
    /**
     * Number of calls to SB.append(...) in graphs.
     */
    public final Counter sbAppends = new Counter(counters, "sb_appends", "");
    /**
     * Number of calls to SB.append(...) in graphs which we don't handle.
     */
    public final Counter sbUnhandledAppends = new Counter(counters, "sb_unhandled_appends", "");
    /**
     * Number of calls to SB.toString() in graphs.
     */
    public final Counter sbToStrings = new Counter(counters, "sb_tostrings", "");
    /**
     * Number of invoke dynamics converted to static String Concatenation calls.
     */
    public final Counter indysOutlined = new Counter(counters, "indys_outlined", "");
    /**
     * Number of calls related to SB actions in the optimized methods.
     */
    public final Counter totalCalls = new Counter(counters, "total_calls", "");
    /**
     * Number of calls to outlined String/SB materializations.
     */
    public final Counter totalMaterializations = new Counter(counters, "total_materializations", "");
    /**
     * Number of calls to outlined String materializations.
     */
    public final Counter toStringMaterializations = new Counter(counters, "tostring_materializations", "");
    /**
     * Number of calls to outlined SB materializations.
     */
    public final Counter instanceMaterializations = new Counter(counters, "instance_materializations", "");
    /**
     * Number of SB materializations which are empty.
     */
    public final Counter emptyMaterializations = new Counter(counters, "empty_materializations", "");
    /**
     * Number of SBs which only have empty materializations. This number should be compared against
     * {@link #acceptedSBs}.
     */
    public final Counter allEmptyMaterializations = new Counter(counters, "all_empty_materializations_for_sb", "");
    /**
     * Number of times materializations which are redundant (i.e., materialize the same object as it
     * materialized elsewhere in the method).
     */
    public final Counter redundantVirtualState = new Counter(counters, "redundant_virtual_state", "");
    /**
     * Number of materializations which are dominated by another materialization of the same SB
     * (with less appends). This can happen since materializations have to be placed at a preceding
     * invoke.
     */
    public final Counter redundantDominators = new Counter(counters, "redundant_dominators", "");
    /**
     * Number of stringify operations.
     */
    public final Counter totalStringifies = new Counter(counters, "total_stringifies", "");
    /**
     * Number of stringifies to primitivies (i.e., booleans, ints, etc.). These do not need an
     * explicit stringify call.
     */
    public final Counter primitiveStringifies = new Counter(counters, "primitive_stringifies", "");
    /**
     * Number of stringifies for {@link String}s. These do not need an explicit stringify call.
     */
    public final Counter stringStringifies = new Counter(counters, "string_stringifies", "");
    /**
     * Number of stringifies for boxed primitives (i.e., Booleans, Integers, etc.) which were
     * unboxed. These do not need an explicit stringify call.
     */
    public final Counter unboxedStringifies = new Counter(counters, "unboxed_stringifies", "");
    /**
     * Number of stringifies for boxed primitives (i.e., Booleans, Integers, etc.) which were not
     * unboxed. These do not need an explicit stringify call.
     */
    public final Counter boxedStringifies = new Counter(counters, "boxed_stringifies", "");
    /**
     * Number of stringifies which need an explicit call to {@link String#valueOf(Object)}.
     */
    public final Counter explicitStringifies = new Counter(counters, "explicit_stringifies", "");
    /**
     * Number of calls to {@code SubstrateSBConcatHelper$CapacityHelper#initialCapacitFor}.
     */
    public final Counter explicitCapacityInits = new Counter(counters, "explicit_capacity_inits", "");
    /**
     * Number of times a materialization occurred due to encountering a CharSequence or StringBuffer
     * init/append; these are currently more efficient to perform on a materialized SB and we have
     * observed happen infrequently.
     */
    public final Counter forcedMaterializations = new Counter(counters, "forced_materializations", "");
    /**
     * Materializations which occurred due to a merge.
     */
    public final Counter mergeMaterializations = new Counter(counters, "merge_materializations", "");
    /**
     * Materializations which occurred due to a merge, but that have the same shape. These merges
     * would be preventable if we stored the appropriate PhiValue within the stringify.
     * Unfortunately, determining the correct lastInvoke in this case is currently not worth the
     * effort.
     */
    public final Counter mergeMaterializationsWithSameShape = new Counter(counters, "merge_materializations_with_same_shape", "");

    /**
     * Per-method counters for tracking the number and types of outlining.
     */
    private final ConcurrentHashMap<MethodType, Integer> outlinedUsageCount = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ResolvedJavaMethod, Integer> methodToOulinedUsageType = new ConcurrentHashMap<>();

    public enum UseKind {
        String,
        StringBuffer,
        StringBuilder,
        Indy
    }

    public ResolvedJavaMethod lookup(AnalysisMetaAccess metaAccess, MethodType methodType) {
        OutlinedSBMethod sbOutlinedMethod = getMethod(metaAccess, methodType);

        // registering method in analysis universe
        return metaAccess.getUniverse().lookup(sbOutlinedMethod);
    }

    private OutlinedSBMethod getMethod(AnalysisMetaAccess aMetaAccess, MethodType methodType) {
        return outlinedSBMethods.compute(methodType, (key, value) -> {
            if (value != null) {
                outlinedUsageCount.put(key, outlinedUsageCount.get(key) + 1);
                return value;
            }
            MetaAccessProvider meta = aMetaAccess.getWrapped();
            outlinedUsageCount.put(key, 1);
            ResolvedJavaType declaringClass = meta.lookupJavaType(OutlinedSBMethodHolder.class);
            ConstantPool pool = declaringClass.getDeclaredConstructors(false)[0].getConstantPool();
            return new OutlinedSBMethod(declaringClass, ResolvedSignature.fromMethodType(methodType, meta), pool, methodType);
        });
    }

    public static void registerOutliningUse(ResolvedJavaMethod method, UseKind kind) {
        if (!SBOutliningFeature.Options.PrintOutlinedSBMethodMetrics.getValue()) {
            return;
        }

        VMError.guarantee(method != null);
        VMError.guarantee(method instanceof AnalysisMethod);
        int encoding = 1 << kind.ordinal();
        singleton().methodToOulinedUsageType.compute(method, (_, v) -> {
            if (v == null) {
                return encoding;
            } else {
                return v | encoding;
            }
        });
    }

    public void printCounters() {
        System.out.println("****Start SBOutlining Counters****");
        for (Counter counter : counters.getCounters()) {
            System.out.println(counter.getName() + " ; " + counter.getValue());
        }
        System.out.println("****End SBOutlining Counters****");
    }

    public void printMethodMetrics() {
        System.out.println("****Start SBOutlining Method Level Info****");
        int numMethodsWithString = 0;
        int numMethodsWithStringBuffer = 0;
        int numMethodsWithStringBuilder = 0;
        int numMethodsWithSB = 0;
        int numMethodsWithOutlinedIndy = 0;
        for (var entry : methodToOulinedUsageType.entrySet()) {
            int value = entry.getValue();
            boolean hasSB = false;
            if ((value & (1 << UseKind.String.ordinal())) != 0) {
                hasSB = true;
                numMethodsWithString++;
            }
            if ((value & (1 << UseKind.StringBuffer.ordinal())) != 0) {
                hasSB = true;
                numMethodsWithStringBuffer++;
            }
            if ((value & (1 << UseKind.StringBuilder.ordinal())) != 0) {
                hasSB = true;
                numMethodsWithStringBuilder++;
            }
            if (hasSB) {
                numMethodsWithSB++;
            }
            if ((value & (1 << UseKind.Indy.ordinal())) != 0) {
                numMethodsWithOutlinedIndy++;
            }
        }
        System.out.printf(" methods with outlined strings: %s%n", numMethodsWithString);
        System.out.printf(" methods with outlined string buffers: %s%n", numMethodsWithStringBuffer);
        System.out.printf(" methods with outlined string builders: %s%n", numMethodsWithStringBuilder);
        System.out.printf(" methods with outlined sbs: %s%n", numMethodsWithSB);
        System.out.printf(" methods with outlined indys: %s%n", numMethodsWithOutlinedIndy);
        System.out.println("****End SBOutlining Method Level Info****");

        System.out.printf("%n%n%n****Start SBOutlining Histogram****%n");
        System.out.println("Number of outlined methods: " + outlinedSBMethods.size());
        if (!outlinedUsageCount.isEmpty()) {
            var sortedResults = outlinedUsageCount.entrySet().stream().sorted((x, y) -> Integer.compare(y.getValue(), x.getValue())).toList();
            for (var value : sortedResults) {
                System.out.printf("Count: %s \tMethod: %s%n", value.getValue(), value.getKey());
            }
        }
        System.out.println("****End SBOutlining Histogram****");

        System.out.printf("%n%n%n****Start Methods with SB Outlining****%n");
        int sbEncoding = (1 << UseKind.String.ordinal()) | (1 << UseKind.StringBuffer.ordinal()) | (1 << UseKind.StringBuilder.ordinal());
        methodToOulinedUsageType.entrySet().stream()
                        .filter(e -> (e.getValue() & sbEncoding) != 0)
                        .map(e -> e.getKey().format("%H.%n.%p"))
                        .sorted()
                        .forEach(System.out::println);
        System.out.println("****End Methods with SB Outlining****");

        System.out.printf("%n%n%n****Start Methods with Indys****%n");
        methodToOulinedUsageType.entrySet().stream()
                        .filter(e -> (e.getValue() & (1 << UseKind.Indy.ordinal())) != 0)
                        .map(e -> e.getKey().format("%H.%n.%p"))
                        .sorted()
                        .forEach(System.out::println);
        System.out.println("****End Methods with Indys****");
    }
}
