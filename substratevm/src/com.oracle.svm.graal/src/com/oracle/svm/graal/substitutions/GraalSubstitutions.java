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
package com.oracle.svm.graal.substitutions;

import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.Custom;
import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.FromAlias;

import java.io.PrintStream;
import java.lang.ref.Cleaner;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.GraalCompilerSupport;
import com.oracle.svm.graal.TruffleRuntimeCompilationSupport;
import com.oracle.svm.graal.hosted.FieldsOffsetsFeature;
import com.oracle.svm.graal.hosted.GraalCompilerFeature;
import com.oracle.svm.graal.hosted.runtimecompilation.RuntimeCompilationFeature;
import com.oracle.svm.graal.meta.SubstrateMethod;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.core.gen.NodeLIRBuilder;
import jdk.graal.compiler.core.match.MatchRuleRegistry;
import jdk.graal.compiler.core.match.MatchStatement;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugHandlersFactory;
import jdk.graal.compiler.debug.KeyRegistry;
import jdk.graal.compiler.debug.MetricKey;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.debug.TimeSource;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.CompositeValue;
import jdk.graal.compiler.lir.CompositeValueClass;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.lir.phases.LIRPhase;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.inlining.info.elem.InlineableGraph;
import jdk.graal.compiler.phases.common.inlining.walker.ComputeInliningRelevance;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.printer.NoDeadCodeVerifyHandler;
import jdk.graal.compiler.replacements.nodes.BinaryMathIntrinsicNode;
import jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode;
import jdk.graal.compiler.serviceprovider.GlobalAtomicLong;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@TargetClass(value = InvocationPlugins.class, onlyWith = RuntimeCompilationFeature.IsEnabled.class)
final class Target_jdk_graal_compiler_nodes_graphbuilderconf_InvocationPlugins {

    @Alias//
    private List<Runnable> deferredRegistrations = new ArrayList<>();

    @Substitute
    private void flushDeferrables() {
        if (deferredRegistrations != null) {
            throw VMError.shouldNotReachHere("not initialized during image generation");
        }
    }
}

@TargetClass(value = InlineableGraph.class, onlyWith = RuntimeCompilationFeature.IsEnabled.class)
@SuppressWarnings({"unused"})
final class Target_jdk_graal_compiler_phases_common_inlining_info_elem_InlineableGraph {

    @Substitute
    private static StructuredGraph parseBytecodes(ResolvedJavaMethod method, HighTierContext context, CanonicalizerPhase canonicalizer, StructuredGraph caller, boolean trackNodeSourcePosition) {
        DebugContext debug = caller.getDebug();
        StructuredGraph result = TruffleRuntimeCompilationSupport.decodeGraph(debug, null, CompilationIdentifier.INVALID_COMPILATION_ID, (SubstrateMethod) method, caller);
        assert result != null : "should not try to inline method when no graph is in the native image";
        assert !trackNodeSourcePosition || result.trackNodeSourcePosition();
        return result;
    }
}

@TargetClass(value = ComputeInliningRelevance.class, onlyWith = RuntimeCompilationFeature.IsEnabled.class)
@SuppressWarnings({"static-method", "unused"})
final class Target_jdk_graal_compiler_phases_common_inlining_walker_ComputeInliningRelevance {

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

@TargetClass(value = DebugContext.class, innerClass = "Invariants", onlyWith = GraalCompilerFeature.IsEnabled.class)
final class Target_jdk_graal_compiler_debug_DebugContext_Invariants {
}

@TargetClass(value = DebugContext.class, innerClass = "Immutable", onlyWith = GraalCompilerFeature.IsEnabled.class)
final class Target_jdk_graal_compiler_debug_DebugContext_Immutable {
    static class ClearImmutableCache implements FieldValueTransformer {
        @Override
        public Object transform(Object receiver, Object originalValue) {
            for (Class<?> c : DebugContext.class.getDeclaredClasses()) {
                // Checkstyle: allow Class.getSimpleName
                if (c.getSimpleName().equals("Immutable")) {
                    // Checkstyle: disallow Class.getSimpleName
                    Object[] cache = ReflectionUtil.readStaticField(c, "CACHE");
                    Object[] clearedCache = cache.clone();
                    for (int i = 0; i < clearedCache.length; i++) {
                        clearedCache[i] = null;
                    }
                    return clearedCache;
                }
            }
            throw VMError.shouldNotReachHere(String.format("Cannot find %s.Immutable", DebugContext.class.getName()));
        }
    }

    /**
     * The cache in {@link DebugContext}.Immutable can hold onto {@link HostedOptionValues} so must
     * be cleared.
     */
    @Alias//
    @RecomputeFieldValue(kind = Custom, declClass = ClearImmutableCache.class)//
    private static Target_jdk_graal_compiler_debug_DebugContext_Immutable[] CACHE;
}

@TargetClass(value = DebugHandlersFactory.class, onlyWith = GraalCompilerFeature.IsEnabled.class)
final class Target_jdk_graal_compiler_debug_DebugHandlersFactory {
    static class CachedFactories implements FieldValueTransformer {
        @Override
        public Object transform(Object receiver, Object originalValue) {
            return GraalCompilerSupport.get().getDebugHandlersFactories();
        }
    }

    /**
     * Cannot do service loading at runtime so cache the loaded service providers in the native
     * image.
     */
    @Alias//
    @RecomputeFieldValue(kind = Custom, declClass = CachedFactories.class)//
    private static Iterable<DebugHandlersFactory> LOADER;
}

@TargetClass(value = TimeSource.class, onlyWith = GraalCompilerFeature.IsEnabled.class)
final class Target_jdk_graal_compiler_debug_TimeSource {
    // Checkstyle: stop
    @Alias//
    @RecomputeFieldValue(kind = FromAlias)//
    private static boolean USING_THREAD_CPU_TIME = false;
    // Checkstyle: resume
}

@TargetClass(value = TTY.class, onlyWith = RuntimeCompilationFeature.IsEnabled.class)
final class Target_jdk_graal_compiler_debug_TTY {

    @Alias//
    @RecomputeFieldValue(kind = FromAlias)//
    private static PrintStream out = Log.logStream();
}

@TargetClass(className = "jdk.graal.compiler.serviceprovider.IsolateUtil", onlyWith = GraalCompilerFeature.IsEnabled.class)
final class Target_jdk_graal_compiler_serviceprovider_IsolateUtil {

    @Substitute
    public static long getIsolateAddress() {
        return CurrentIsolate.getIsolate().rawValue();
    }

    @Substitute
    public static long getIsolateID() {
        return ImageSingletons.lookup(GraalCompilerSupport.class).getIsolateId();
    }
}

class GlobalAtomicLongAddressProvider implements FieldValueTransformer {
    @Override
    public Object transform(Object receiver, Object originalValue) {
        long initialValue = ((GlobalAtomicLong) receiver).getInitialValue();
        return CGlobalDataFactory.createWord(WordFactory.unsigned(initialValue), null, true);
    }
}

@TargetClass(className = "jdk.graal.compiler.serviceprovider.GlobalAtomicLong", onlyWith = GraalCompilerFeature.IsEnabled.class)
final class Target_jdk_graal_compiler_serviceprovider_GlobalAtomicLong {

    @Inject//
    @RecomputeFieldValue(kind = Kind.Custom, declClass = GlobalAtomicLongAddressProvider.class) //
    private CGlobalData<Pointer> addressSupplier;

    @Delete private long address;

    @Delete private static Cleaner cleaner;

    /**
     * Delete the constructor to ensure instances of {@link GlobalAtomicLong} cannot be created at
     * runtime.
     */
    @Substitute
    @TargetElement(name = TargetElement.CONSTRUCTOR_NAME)
    @SuppressWarnings({"unused", "static-method"})
    public void constructor(long initialValue) {
        throw VMError.unsupportedFeature("Cannot create " + GlobalAtomicLong.class.getName() + " objects in native image runtime");
    }

    @Substitute
    private long getAddress() {
        return addressSupplier.get().rawValue();
    }
}

/*
 * The following substitutions replace methods where reflection is used in the Graal code.
 */

@TargetClass(value = KeyRegistry.class, onlyWith = GraalCompilerFeature.IsEnabled.class)
final class Target_jdk_graal_compiler_debug_KeyRegistry {

    @Alias//
    @RecomputeFieldValue(kind = FromAlias)//
    private static EconomicMap<String, Integer> keyMap = EconomicMap.create();

    @Alias//
    @RecomputeFieldValue(kind = FromAlias)//
    private static List<MetricKey> keys = new ArrayList<>();
}

@TargetClass(value = MatchRuleRegistry.class, onlyWith = GraalCompilerFeature.IsEnabled.class)
final class Target_jdk_graal_compiler_core_match_MatchRuleRegistry {

    @Substitute
    public static EconomicMap<Class<? extends Node>, List<MatchStatement>> lookup(Class<? extends NodeLIRBuilder> theClass, @SuppressWarnings("unused") OptionValues options,
                    @SuppressWarnings("unused") DebugContext debug) {
        EconomicMap<Class<? extends Node>, List<MatchStatement>> result = GraalCompilerSupport.get().matchRuleRegistry.get(theClass);
        if (result == null) {
            throw VMError.shouldNotReachHere(String.format("MatchRuleRegistry.lookup(): unexpected class %s", theClass.getName()));
        }
        return result;
    }
}

@TargetClass(value = BinaryMathIntrinsicNode.class, onlyWith = RuntimeCompilationFeature.IsEnabled.class)
@SuppressWarnings({"unused", "static-method"})
final class Target_jdk_graal_compiler_replacements_nodes_BinaryMathIntrinsicNode {

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

@TargetClass(value = UnaryMathIntrinsicNode.class, onlyWith = RuntimeCompilationFeature.IsEnabled.class)
@SuppressWarnings({"unused", "static-method"})
final class Target_jdk_graal_compiler_replacements_nodes_UnaryMathIntrinsicNode {

    @Substitute
    void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        throw VMError.shouldNotReachHere("Node must have been lowered to a runtime call");
    }
}

@TargetClass(value = BasePhase.class, onlyWith = GraalCompilerFeature.IsEnabled.class)
final class Target_jdk_graal_compiler_phases_BasePhase {

    @Substitute
    static BasePhase.BasePhaseStatistics getBasePhaseStatistics(Class<?> clazz) {
        BasePhase.BasePhaseStatistics result = GraalCompilerSupport.get().getBasePhaseStatistics().get(clazz);
        if (result == null) {
            throw VMError.shouldNotReachHere(String.format("Missing statistics for phase class: %s%n", clazz.getName()));
        }
        return result;
    }
}

@TargetClass(value = LIRPhase.class, onlyWith = GraalCompilerFeature.IsEnabled.class)
final class Target_jdk_graal_compiler_lir_phases_LIRPhase {

    @Substitute
    static LIRPhase.LIRPhaseStatistics getLIRPhaseStatistics(Class<?> clazz) {
        LIRPhase.LIRPhaseStatistics result = GraalCompilerSupport.get().getLirPhaseStatistics().get(clazz);
        if (result == null) {
            throw VMError.shouldNotReachHere(String.format("Missing statistics for phase class: %s%n", clazz.getName()));
        }
        return result;
    }
}

@TargetClass(value = NodeClass.class, onlyWith = GraalCompilerFeature.IsEnabled.class)
final class Target_jdk_graal_compiler_graph_NodeClass {

    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = FieldsOffsetsFeature.InputsIterationMaskRecomputation.class)//
    private long inputsIteration;

    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = FieldsOffsetsFeature.SuccessorsIterationMaskRecomputation.class)//
    private long successorIteration;

    @Substitute
    @SuppressWarnings("unlikely-arg-type")
    @SuppressFBWarnings(value = {"GC_UNRELATED_TYPES"}, justification = "Class is DynamicHub")
    public static NodeClass<?> get(Class<?> clazz) {
        NodeClass<?> nodeClass = GraalCompilerSupport.get().nodeClasses.get(clazz);
        if (nodeClass == null) {
            throw VMError.shouldNotReachHere(String.format("Unknown node class: %s%n", clazz.getName()));
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

@TargetClass(value = LIRInstructionClass.class, onlyWith = GraalCompilerFeature.IsEnabled.class)
final class Target_jdk_graal_compiler_lir_LIRInstructionClass {

    @Substitute
    @SuppressWarnings("unlikely-arg-type")
    @SuppressFBWarnings(value = {"GC_UNRELATED_TYPES"}, justification = "Class is DynamicHub")
    public static LIRInstructionClass<?> get(Class<? extends LIRInstruction> clazz) {
        LIRInstructionClass<?> instructionClass = GraalCompilerSupport.get().instructionClasses.get(clazz);
        if (instructionClass == null) {
            throw VMError.shouldNotReachHere(String.format("Unknown instruction class: %s%n", clazz.getName()));
        }
        return instructionClass;
    }
}

@TargetClass(value = CompositeValueClass.class, onlyWith = GraalCompilerFeature.IsEnabled.class)
final class Target_jdk_graal_compiler_lir_CompositeValueClass {

    @Substitute
    @SuppressWarnings("unlikely-arg-type")
    @SuppressFBWarnings(value = {"GC_UNRELATED_TYPES"}, justification = "Class is DynamicHub")
    public static CompositeValueClass<?> get(Class<? extends CompositeValue> clazz) {
        CompositeValueClass<?> compositeValueClass = GraalCompilerSupport.get().compositeValueClasses.get(clazz);
        if (compositeValueClass == null) {
            throw VMError.shouldNotReachHere(String.format("Unknown composite value class: %s%n", clazz.getName()));
        }
        return compositeValueClass;
    }
}

@TargetClass(value = NoDeadCodeVerifyHandler.class)
final class Target_jdk_graal_compiler_printer_NoDeadCodeVerifyHandler {
    @Alias//
    @RecomputeFieldValue(kind = Kind.NewInstance, declClass = ConcurrentHashMap.class)//
    private static Map<String, Boolean> discovered;
}

@TargetClass(value = NamedLocationIdentity.class, innerClass = "DB", onlyWith = GraalCompilerFeature.IsEnabled.class)
final class Target_jdk_graal_compiler_nodes_NamedLocationIdentity_DB {
    @Alias//
    @RecomputeFieldValue(kind = FromAlias, declClass = EconomicMap.class)//
    private static EconomicSet<String> map = EconomicSet.create(Equivalence.DEFAULT);
}

/**
 * Workaround so that {@link TargetDescription} can distinguish between AOT compilation and runtime
 * compilation. Ideally, each case would have its own {@link TargetDescription}, but currently it is
 * created just once during the image build and accessed via {@link ConfigurationValues} and
 * {@link ImageSingletons} from many locations.
 */
@TargetClass(value = TargetDescription.class, onlyWith = GraalCompilerFeature.IsEnabled.class)
final class Target_jdk_vm_ci_code_TargetDescription {
    @Alias//
    @InjectAccessors(value = InlineObjectsAccessor.class) //
    boolean inlineObjects;

    @Inject//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    Boolean inlineObjectsValue;

    @SuppressWarnings("unused")
    static class InlineObjectsAccessor {
        static boolean get(Target_jdk_vm_ci_code_TargetDescription receiver) {
            if (receiver.inlineObjectsValue == null) {
                receiver.inlineObjectsValue = SubstrateTargetDescription.shouldInlineObjectsInRuntimeCode();
            }
            return receiver.inlineObjectsValue;
        }

        /** For TargetDescription constructor at runtime (e.g. Libgraal). */
        static void set(Target_jdk_vm_ci_code_TargetDescription receiver, boolean value) {
            receiver.inlineObjectsValue = value;
        }
    }
}

/** Dummy class to have a class with the file's name. Do not remove. */
public final class GraalSubstitutions {
    // Dummy
}
