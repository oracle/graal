/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal;

import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.Custom;
import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.FromAlias;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.core.gen.NodeLIRBuilder;
import org.graalvm.compiler.core.match.MatchStatement;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.MetricKey;
import org.graalvm.compiler.debug.TimeSource;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.CompositeValue;
import org.graalvm.compiler.lir.CompositeValueClass;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.lir.phases.LIRPhase;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.printer.NoDeadCodeVerifyHandler;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.hosted.FieldsOffsetsFeature;
import com.oracle.svm.graal.hosted.GraalFeature;
import com.oracle.svm.graal.meta.SubstrateMethod;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;

// Checkstyle: stop

@TargetClass(value = org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.class, onlyWith = GraalFeature.IsEnabledAndNotLibgraal.class)
final class Target_org_graalvm_compiler_nodes_graphbuilderconf_InvocationPlugins {

    @Alias//
    private List<Runnable> deferredRegistrations = new ArrayList<>();

    @Substitute
    private void flushDeferrables() {
        if (deferredRegistrations != null) {
            throw VMError.shouldNotReachHere("not initialized during image generation");
        }
    }
}

@TargetClass(value = org.graalvm.compiler.phases.common.inlining.info.elem.InlineableGraph.class, onlyWith = GraalFeature.IsEnabledAndNotLibgraal.class)
@SuppressWarnings({"unused"})
final class Target_org_graalvm_compiler_phases_common_inlining_info_elem_InlineableGraph {

    @Substitute
    private static StructuredGraph parseBytecodes(ResolvedJavaMethod method, HighTierContext context, CanonicalizerPhase canonicalizer, StructuredGraph caller, boolean trackNodeSourcePosition) {
        DebugContext debug = caller.getDebug();
        StructuredGraph result = GraalSupport.decodeGraph(debug, null, CompilationIdentifier.INVALID_COMPILATION_ID, (SubstrateMethod) method);
        assert result != null : "should not try to inline method when no graph is in the native image";
        assert !trackNodeSourcePosition || result.trackNodeSourcePosition();
        return result;
    }
}

@TargetClass(value = org.graalvm.compiler.phases.common.inlining.walker.ComputeInliningRelevance.class, onlyWith = GraalFeature.IsEnabledAndNotLibgraal.class)
@SuppressWarnings({"static-method", "unused"})
final class Target_org_graalvm_compiler_phases_common_inlining_walker_ComputeInliningRelevance {

    @Substitute
    private void compute() {
    }

    @Substitute
    public double getRelevance(Invoke invoke) {
        /*
         * We do not have execution frequency that come from profiling information. We could compute
         * a relevance from loop depth and if-nesting, but we keep it simple for now.
         */
        return 1;
    }
}

@TargetClass(value = DebugContext.class, innerClass = "Invariants", onlyWith = GraalFeature.IsEnabled.class)
final class Target_org_graalvm_compiler_debug_DebugContext_Invariants {
}

@TargetClass(value = DebugContext.class, innerClass = "Immutable", onlyWith = GraalFeature.IsEnabled.class)
final class Target_org_graalvm_compiler_debug_DebugContext_Immutable {
    static class ClearImmutableCache implements RecomputeFieldValue.CustomFieldValueComputer {

        @Override
        public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
            for (Class<?> c : DebugContext.class.getDeclaredClasses()) {
                if (c.getSimpleName().equals("Immutable")) {
                    Object[] cache = ReflectionUtil.readStaticField(c, "CACHE");
                    Object[] clearedCache = cache.clone();
                    for (int i = 0; i < clearedCache.length; i++) {
                        clearedCache[i] = null;
                    }
                    return clearedCache;
                }
            }
            throw VMError.shouldNotReachHere("Cannot find " + DebugContext.class.getName() + ".Immutable");
        }
    }

    /**
     * The cache in {@link DebugContext}.Immutable can hold onto {@link HostedOptionValues} so must
     * be cleared.
     */
    @Alias @RecomputeFieldValue(kind = Custom, declClass = ClearImmutableCache.class)//
    private static Target_org_graalvm_compiler_debug_DebugContext_Immutable[] CACHE;
}

@TargetClass(value = DebugHandlersFactory.class, onlyWith = GraalFeature.IsEnabled.class)
final class Target_org_graalvm_compiler_debug_DebugHandlersFactory {
    static class CachedFactories implements RecomputeFieldValue.CustomFieldValueComputer {

        @Override
        public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
            return GraalSupport.get().debugHandlersFactories;
        }
    }

    /**
     * Cannot do service loading at runtime so cache the loaded service providers in the native
     * image.
     */
    @Alias @RecomputeFieldValue(kind = Custom, declClass = CachedFactories.class)//
    private static Iterable<DebugHandlersFactory> LOADER;
}

@TargetClass(value = TimeSource.class, onlyWith = GraalFeature.IsEnabled.class)
final class Target_org_graalvm_compiler_debug_TimeSource {
    @Alias @RecomputeFieldValue(kind = FromAlias)//
    private static boolean USING_THREAD_CPU_TIME = false;
}

@TargetClass(value = org.graalvm.compiler.debug.TTY.class, onlyWith = GraalFeature.IsEnabledAndNotLibgraal.class)
final class Target_org_graalvm_compiler_debug_TTY {

    @Alias @RecomputeFieldValue(kind = FromAlias)//
    private static PrintStream out = Log.logStream();
}

@TargetClass(className = "org.graalvm.compiler.serviceprovider.IsolateUtil", onlyWith = GraalFeature.IsEnabled.class)
final class Target_org_graalvm_compiler_serviceprovider_IsolateUtil {

    @Substitute
    public static long getIsolateAddress() {
        return CurrentIsolate.getIsolate().rawValue();
    }

    @Substitute
    public static long getIsolateID() {
        return ImageSingletons.lookup(GraalSupport.class).getIsolateId();
    }
}

/*
 * The following substitutions replace methods where reflection is used in the Graal code.
 */

@TargetClass(value = org.graalvm.compiler.virtual.phases.ea.EffectList.class, onlyWith = GraalFeature.IsEnabled.class)
@SuppressWarnings({"static-method", "unused"})
final class Target_org_graalvm_compiler_virtual_phases_ea_EffectList {

    @Substitute
    private void toString(StringBuilder str, int i) {
        str.append("<Effect - no string representation possible>");
    }
}

@TargetClass(value = org.graalvm.compiler.debug.KeyRegistry.class, onlyWith = GraalFeature.IsEnabled.class)
final class Target_org_graalvm_compiler_debug_KeyRegistry {

    @Alias @RecomputeFieldValue(kind = FromAlias)//
    private static EconomicMap<String, Integer> keyMap = EconomicMap.create();

    @Alias @RecomputeFieldValue(kind = FromAlias)//
    private static List<MetricKey> keys = new ArrayList<>();
}

@TargetClass(value = org.graalvm.compiler.core.match.MatchRuleRegistry.class, onlyWith = GraalFeature.IsEnabled.class)
final class Target_org_graalvm_compiler_core_match_MatchRuleRegistry {

    @Substitute
    public static EconomicMap<Class<? extends Node>, List<MatchStatement>> lookup(Class<? extends NodeLIRBuilder> theClass, @SuppressWarnings("unused") OptionValues options,
                    @SuppressWarnings("unused") DebugContext debug) {
        EconomicMap<Class<? extends Node>, List<MatchStatement>> result = GraalSupport.get().matchRuleRegistry.get(theClass);
        if (result == null) {
            VMError.shouldNotReachHere("MatchRuleRegistry.lookup(): unexpected class " + theClass.getName());
        }
        return result;
    }
}

@TargetClass(value = org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode.class, onlyWith = GraalFeature.IsEnabledAndNotLibgraal.class)
@SuppressWarnings({"unused", "static-method"})
final class Target_org_graalvm_compiler_replacements_nodes_BinaryMathIntrinsicNode {

    /*
     * The node is lowered to a foreign call, the LIR generation is only used for the compilation of
     * the actual Math functions - which we have AOT compiled. Therefore, the LIR generation is
     * unreachable. But the static analysis cannot detect that, so we manually substitute the
     * method.
     */
    @Substitute
    void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        throw VMError.shouldNotReachHere("Node must have been lowered to a runtime call");
    }
}

@TargetClass(value = org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.class, onlyWith = GraalFeature.IsEnabledAndNotLibgraal.class)
@SuppressWarnings({"unused", "static-method"})
final class Target_org_graalvm_compiler_replacements_nodes_UnaryMathIntrinsicNode {

    @Substitute
    void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        throw VMError.shouldNotReachHere("Node must have been lowered to a runtime call");
    }
}

@TargetClass(value = org.graalvm.compiler.phases.BasePhase.class, onlyWith = GraalFeature.IsEnabled.class)
final class Target_org_graalvm_compiler_phases_BasePhase {

    @Substitute
    static BasePhase.BasePhaseStatistics getBasePhaseStatistics(Class<?> clazz) {
        BasePhase.BasePhaseStatistics result = GraalSupport.get().basePhaseStatistics.get(clazz);
        if (result == null) {
            throw VMError.shouldNotReachHere("Missing statistics for phase class: " + clazz.getName() + "\n");
        }
        return result;
    }
}

@TargetClass(value = org.graalvm.compiler.lir.phases.LIRPhase.class, onlyWith = GraalFeature.IsEnabled.class)
final class Target_org_graalvm_compiler_lir_phases_LIRPhase {

    @Substitute
    static LIRPhase.LIRPhaseStatistics getLIRPhaseStatistics(Class<?> clazz) {
        LIRPhase.LIRPhaseStatistics result = GraalSupport.get().lirPhaseStatistics.get(clazz);
        if (result == null) {
            throw VMError.shouldNotReachHere("Missing statistics for phase class: " + clazz.getName() + "\n");
        }
        return result;
    }
}

@TargetClass(value = org.graalvm.compiler.graph.NodeClass.class, onlyWith = GraalFeature.IsEnabled.class)
final class Target_org_graalvm_compiler_graph_NodeClass {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = FieldsOffsetsFeature.InputsIterationMaskRecomputation.class)//
    private long inputsIteration;

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = FieldsOffsetsFeature.SuccessorsIterationMaskRecomputation.class)//
    private long successorIteration;

    @Substitute
    @SuppressWarnings("unlikely-arg-type")
    @SuppressFBWarnings(value = {"GC_UNRELATED_TYPES"}, justification = "Class is DynamicHub")
    public static NodeClass<?> get(Class<?> clazz) {
        NodeClass<?> nodeClass = GraalSupport.get().nodeClasses.get(DynamicHub.fromClass(clazz));
        if (nodeClass == null) {
            throw VMError.shouldNotReachHere("Unknown node class: " + clazz.getName() + "\n");
        }
        return nodeClass;
    }

    @Alias //
    private String shortName;

    @Substitute
    public String shortName() {
        assert shortName != null;
        return shortName;
    }
}

@TargetClass(value = org.graalvm.compiler.lir.LIRInstructionClass.class, onlyWith = GraalFeature.IsEnabled.class)
final class Target_org_graalvm_compiler_lir_LIRInstructionClass {

    @Substitute
    @SuppressWarnings("unlikely-arg-type")
    @SuppressFBWarnings(value = {"GC_UNRELATED_TYPES"}, justification = "Class is DynamicHub")
    public static LIRInstructionClass<?> get(Class<? extends LIRInstruction> clazz) {
        LIRInstructionClass<?> instructionClass = GraalSupport.get().instructionClasses.get(DynamicHub.fromClass(clazz));
        if (instructionClass == null) {
            throw VMError.shouldNotReachHere("Unknown instruction class: " + clazz.getName() + "\n");
        }
        return instructionClass;
    }
}

@TargetClass(value = org.graalvm.compiler.lir.CompositeValueClass.class, onlyWith = GraalFeature.IsEnabled.class)
final class Target_org_graalvm_compiler_lir_CompositeValueClass {

    @Substitute
    @SuppressWarnings("unlikely-arg-type")
    @SuppressFBWarnings(value = {"GC_UNRELATED_TYPES"}, justification = "Class is DynamicHub")
    public static CompositeValueClass<?> get(Class<? extends CompositeValue> clazz) {
        CompositeValueClass<?> compositeValueClass = GraalSupport.get().compositeValueClasses.get(DynamicHub.fromClass(clazz));
        if (compositeValueClass == null) {
            throw VMError.shouldNotReachHere("Unknown composite value class: " + clazz.getName() + "\n");
        }
        return compositeValueClass;
    }
}

@TargetClass(value = NoDeadCodeVerifyHandler.class)
final class Target_org_graalvm_compiler_printer_NoDeadCodeVerifyHandler {
    @Alias//
    @RecomputeFieldValue(kind = Kind.NewInstance, declClass = ConcurrentHashMap.class)//
    private static Map<String, Boolean> discovered;
}

@TargetClass(value = org.graalvm.compiler.nodes.NamedLocationIdentity.class, innerClass = "DB", onlyWith = GraalFeature.IsEnabled.class)
final class Target_org_graalvm_compiler_nodes_NamedLocationIdentity_DB {
    @Alias//
    @RecomputeFieldValue(kind = FromAlias, declClass = EconomicMap.class)//
    private static EconomicSet<String> map = EconomicSet.create(Equivalence.DEFAULT);
}

/** Dummy class to have a class with the file's name. Do not remove. */
public final class GraalSubstitutions {
    // Dummy
}
