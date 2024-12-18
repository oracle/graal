/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.fieldfolding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.AnalysisParsedGraph.Stage;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.ameta.FieldValueInterceptionSupport;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives;
import com.oracle.svm.hosted.meta.HostedField;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Performs constant folding for some static final fields in classes that are initialized at run
 * time. When a class is initialized at image build time, all static final fields are constant
 * folded by the regular constant folding mechanism. But if a class is initialized at run time, the
 * class initializer of that class is analyzed like any other method, i.e., the static analysis sees
 * a static final field as written and does not perform constant folding. Without constant folding
 * during parsing already, other simplifications and intrinsifications do not work on such fields,
 * such as those involving method handles.
 *
 * This feature performs constant folding for a limited but important class of static final fields:
 * the class initializer contains a single field store and the stored value is a constant. That
 * single constant is propagated to field loads.
 *
 * The specification of Java class initializers and static final fields complicate this
 * optimization: Even if it is guaranteed that the field eventually gets a constant value assigned
 * in the class initializer, field loads that happen before the field store while the class
 * initializer is running must yield the uninitialized value. Therefore, it is necessary to maintain
 * a separate boolean value per optimized static final field, which is set to true when the
 * initializing field store is done. The field load is therefore not intrinsified to a single
 * constant, but an if-else structure with the likely case returning the constant value, and the
 * slow-path case of returning the uninitialized value. All these boolean values are stored in the
 * {@link #fieldInitializationStatus} array, and {@link #fieldCheckIndexMap} stores the index for
 * the optimized fields.
 *
 * The optimized field load is also preceded by a {@link EnsureClassInitializedNode} to trigger the
 * necessary class initialization. It would be possible to combine the class initialization check
 * and the field initialization check to a single check. But that leads to several corner cases and
 * possible performance issues:
 * <ul>
 * <li>Even if a class is fully initialized, no field store for the static final field could have
 * happened. The Java language specification prohibits that, but the Java VM specification allows
 * it. Checking that a field is initialized in every possible path through the class initializer
 * would be complicated.</li>
 * <li>The class initialization check can be optimized away by a dominator-based analysis, but the
 * field initialization check cannot. On the other hand, the standard read elimination can optimize
 * field initialization checks.</li>
 * <li>Static final fields are often accessed from within the class that they are declared in. In
 * that case, the class initialization check is in a caller method, i.e., there is no
 * {@link EnsureClassInitializedNode} necessary in the method that performs the field access.</li>
 * </ul>
 *
 * This feature makes use of staged parsing to be able to optimize field loads in class
 * initializers. Since those can have cyclic dependencies, it is necessary to do the field store
 * analysis in a separate pass. Otherwise, the graph parsing may end up in a deadlock because
 * parsing requests may be processed by different threads and could then depend on each other.
 */
@AutomaticallyRegisteredFeature
public final class StaticFinalFieldFoldingFeature implements InternalFeature {

    public static class Options {
        @Option(help = "Optimize static final fields that get a constant assigned in the class initializer.")//
        public static final HostedOptionKey<Boolean> OptStaticFinalFieldFolding = new HostedOptionKey<>(true);
    }

    /**
     * Folded field values after stage {@link Stage#BYTECODE_PARSED}.
     */
    private final Map<AnalysisField, JavaConstant> bytecodeParsedFoldedFieldValues = new ConcurrentHashMap<>();

    /**
     * Folded field values after stage {@link Stage#AFTER_PARSING_HOOKS_DONE}.
     */
    private final Map<AnalysisField, JavaConstant> afterParsingHooksDoneFoldedFieldValues = new ConcurrentHashMap<>();

    BigBang bb;
    Map<AnalysisField, Integer> fieldCheckIndexMap;
    /**
     * Stores the field check index from the base layer.
     *
     * GR-59855: This is currently only used to determine that a field was folded in a previous
     * layer already. The value from the base layer should also be reused in extension layers to
     * ensure valid field folding across layers.
     */
    Map<Integer, Integer> baseLayerFieldCheckIndexMap = new HashMap<>();
    boolean[] fieldInitializationStatus;

    private final Set<AnalysisMethod> analyzedMethods = ConcurrentHashMap.newKeySet();

    public static StaticFinalFieldFoldingFeature singleton() {
        return ImageSingletons.lookup(StaticFinalFieldFoldingFeature.class);
    }

    static boolean isAvailable() {
        return ImageSingletons.contains(StaticFinalFieldFoldingFeature.class);
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return Options.OptStaticFinalFieldFolding.getValue();
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        DuringSetupAccessImpl access = (DuringSetupAccessImpl) a;

        access.getHostVM().addMethodAfterBytecodeParsedListener((method, graph) -> onAnalysisMethodParsed(Stage.BYTECODE_PARSED, method, graph));
        access.getHostVM().addMethodAfterParsingListener((method, graph) -> onAnalysisMethodParsed(Stage.AFTER_PARSING_HOOKS_DONE, method, graph));
    }

    @Override
    public void registerGraphBuilderPlugins(Providers providers, Plugins plugins, ParsingReason reason) {
        if (reason != ParsingReason.JITCompilation) {
            /*
             * All classes we care about that are JIT compiled, like Truffle languages, are
             * initialized at image build time. So we do not need to make this plugin and the nodes
             * it references safe for execution at image run time.
             */
            plugins.appendNodePlugin(new StaticFinalFieldFoldingNodePlugin());
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        BeforeAnalysisAccessImpl access = (BeforeAnalysisAccessImpl) a;

        bb = access.getBigBang();
    }

    /**
     * Computes a unique index for each optimized field, and prepares the boolean[] array for the
     * image heap that tracks the field initialization state at run time.
     */
    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        bb = null;

        HashSet<AnalysisField> foldedFieldsSet = new HashSet<>();
        for (Stage stage : Stage.values()) {
            foldedFieldsSet.addAll(getFoldedFieldValues(stage).keySet());
        }

        List<AnalysisField> foldedFields = new ArrayList<>(foldedFieldsSet);
        /* Make the fieldCheckIndex deterministic by using an (arbitrary) sort order. */
        foldedFields.sort(Comparator.comparing(field -> field.format("%H.%n")));

        fieldCheckIndexMap = new HashMap<>();
        int fieldCheckIndex = 0;
        for (AnalysisField field : foldedFields) {
            assert !baseLayerFieldCheckIndexMap.containsKey(field.getId()) : "The field %s was already assigned an index in the base layer".formatted(field);
            fieldCheckIndexMap.put(field, fieldCheckIndex);
            fieldCheckIndex++;
        }

        fieldInitializationStatus = new boolean[fieldCheckIndex];
    }

    /**
     * When a class is initialized later after static analysis, the
     * {@link IsStaticFinalFieldInitializedNode} is still folded away during compilation. But since
     * we have already reserved the memory for the status flag, we are paranoid and set the status
     * to initialized.
     */
    @Override
    public void afterHeapLayout(AfterHeapLayoutAccess access) {
        for (Map.Entry<AnalysisField, Integer> entry : fieldCheckIndexMap.entrySet()) {
            if (entry.getKey().getDeclaringClass().isInitialized()) {
                fieldInitializationStatus[entry.getValue()] = true;
            }
        }
    }

    private Map<AnalysisField, JavaConstant> getFoldedFieldValues(Stage stage) {
        return switch (stage) {
            case BYTECODE_PARSED -> bytecodeParsedFoldedFieldValues;
            case AFTER_PARSING_HOOKS_DONE -> afterParsingHooksDoneFoldedFieldValues;
        };
    }

    /**
     * Invoked for each method that is parsed during static analysis, before the type flow graph of
     * that method is created. If the method is a class initializer, the static final fields that
     * can be optimized are detected and added to {@link #bytecodeParsedFoldedFieldValues} or
     * {@link #afterParsingHooksDoneFoldedFieldValues} (depending on the stage). If the method is
     * not a class initializer, it is verified that there is no illegal store to an optimized field.
     */
    private void onAnalysisMethodParsed(Stage stage, AnalysisMethod method, StructuredGraph graph) {
        boolean isClassInitializer = method.isClassInitializer();
        Map<AnalysisField, JavaConstant> optimizableFields = isClassInitializer ? new HashMap<>() : null;
        Set<AnalysisField> ineligibleFields = isClassInitializer ? new HashSet<>() : null;

        for (Node n : graph.getNodes()) {
            if (n instanceof StoreFieldNode node) {
                AnalysisField field = (AnalysisField) node.field();
                if (field.isStatic() && field.isFinal() && !field.isInBaseLayer()) {
                    if (isClassInitializer && field.getDeclaringClass().equals(method.getDeclaringClass())) {
                        analyzeStoreInClassInitializer(node, field, optimizableFields, ineligibleFields);
                    } else {
                        analyzeStoreOutsideClassInitializer(stage, method, field);
                    }
                }
            }
        }

        if (optimizableFields != null && !optimizableFields.isEmpty()) {
            verifyOptimizableFields(stage, method, optimizableFields);
            getFoldedFieldValues(stage).putAll(optimizableFields);
        }

        // remember that the method was analyzed for static final fields
        analyzedMethods.add(method);
    }

    /**
     * Verifies that the constant value for an optimizable field is always the same in case a method
     * is parsed several times.
     *
     * A method may be parsed several times (e.g. due to {@link Stage staged parsing} or if
     * {@link AnalysisMethod#reparseGraph(BigBang) reparsing is forced}) and in such cases, we need
     * to ensure that static final field folding always leads to the same constant value. If a
     * violation is detected, the image build fails and static final field folding needs to be
     * disabled.
     */
    private void verifyOptimizableFields(Stage stage, AnalysisMethod method, Map<AnalysisField, JavaConstant> optimizableFields) {
        if (!analyzedMethods.contains(method)) {
            return;
        }

        for (Entry<AnalysisField, JavaConstant> entry : optimizableFields.entrySet()) {
            JavaConstant javaConstant = getFoldedFieldValue(stage, entry.getKey());
            /*
             * The field is found optimizable, i.e., a constant is assigned in a class initializer,
             * and then the class initializer is parsed again (in a later stage or reparsing was
             * forced) and a different value was encountered. The user needs to disable the
             * optimization.
             */
            UserError.guarantee(javaConstant == null || javaConstant.equals(entry.getValue()), "" +
                            "The static final field optimization found a static final field %s " +
                            "which is initialized multiple times with different constant values. " +
                            "You can use %s to disable the optimization.",
                            entry.getKey().format("%H.%n"),
                            SubstrateOptionsParser.commandArgument(Options.OptStaticFinalFieldFolding, "-"));
        }
    }

    /**
     * Store of a static final field in the class initializer of its declaring class. This is the
     * normal way how static final fields are initialized.
     */
    private static void analyzeStoreInClassInitializer(StoreFieldNode node, AnalysisField field, Map<AnalysisField, JavaConstant> optimizableFields, Set<AnalysisField> ineligibleFields) {
        if (field.isSynthetic() && field.getName().startsWith("$assertionsDisabled")) {
            /*
             * Loads of assertion status fields are constant folded using a different mechanism, so
             * no need to handle them here.
             */
            return;
        }

        if (node.value().isJavaConstant() && !ineligibleFields.contains(field)) {
            JavaConstant existingValue = optimizableFields.get(field);
            JavaConstant newValue = node.value().asJavaConstant();
            if (existingValue == null || existingValue.equals(newValue)) {
                /* Either the first store of the field, or a subsequent store of the same value. */
                optimizableFields.put(field, newValue);
                return;
            }
        }

        /* The field cannot be optimized. */
        ineligibleFields.add(field);
        optimizableFields.remove(field);
    }

    /**
     * Store of a static final field outside of its class initializer. That is not allowed according
     * to the latest Java VM spec, but languages like Scala do it anyway. As long as the field is
     * not found as optimizable, this is no problem.
     */
    private void analyzeStoreOutsideClassInitializer(Stage stage, AnalysisMethod method, AnalysisField field) {
        if (field.getDeclaringClass().getClassInitializer() != null) {
            /*
             * Analyze the class initializer of the class that defines the field. This ensures that
             * the order in which graphs are parsed during static analysis does not affect the
             * outcome of the optimizable check below.
             */
            field.getDeclaringClass().getClassInitializer().ensureGraphParsed(bb);
        }

        if (getFoldedFieldValues(stage).containsKey(field)) {
            /*
             * The field is found optimizable, i.e., a constant is assigned in a class initializer,
             * and then the field is written again outside the class initializer. The user needs to
             * disable the optimization.
             */
            throw UserError.abort("" +
                            "The static final field optimization found a static final field that is initialized both inside and outside of its class initializer. " +
                            "Field %s is stored in method %s. This violates the Java bytecode specification. You can use %s to disable the optimization.",
                            field.format("%H.%n"), method.format("%H.%n(%p)"), SubstrateOptionsParser.commandArgument(Options.OptStaticFinalFieldFolding, "-"));
        }
    }

    static AnalysisField toAnalysisField(ResolvedJavaField field) {
        if (field instanceof HostedField) {
            return ((HostedField) field).wrapped;
        } else {
            return (AnalysisField) field;
        }
    }

    /**
     * Looks up a folded field value for an AnalysisField. Since field stores may be folded and
     * added in different parsing stages, the stage must be provided to ensure deterministic image
     * builds. If the field could not be folded, null will be returned.
     */
    JavaConstant getFoldedFieldValue(Stage stage, AnalysisField field) {
        for (Stage curStage = stage; curStage != null; curStage = curStage.previous()) {
            JavaConstant javaConstant = getFoldedFieldValues(curStage).get(field);
            if (javaConstant != null) {
                return javaConstant;
            }
        }
        return null;
    }

    public Integer getFieldCheckIndex(ResolvedJavaField field) {
        return getFieldCheckIndex(toAnalysisField(field));
    }

    public Integer getFieldCheckIndex(AnalysisField field) {
        if (field.isInBaseLayer()) {
            return baseLayerFieldCheckIndexMap.get(field.getId());
        } else {
            return fieldCheckIndexMap.get(field);
        }
    }

    public void putBaseLayerFieldCheckIndex(int id, Integer fieldCheckIndex) {
        baseLayerFieldCheckIndexMap.put(id, fieldCheckIndex);
    }

    static boolean isOptimizationCandidate(AnalysisField aField, AnalysisMethod definingClassInitializer, FieldValueInterceptionSupport fieldValueInterceptionSupport) {
        if (definingClassInitializer == null) {
            /* If there is no class initializer, there cannot be a foldable constant found in it. */
            return false;
        }

        if (!fieldValueInterceptionSupport.isValueAvailable(aField)) {
            /*
             * Cannot optimize static field whose value is recomputed and is not yet available,
             * i.e., it may depend on analysis/compilation derived data.
             */
            return false;
        }
        return true;
    }

    static boolean isAllowedTargetMethod(ResolvedJavaMethod method) {
        /*
         * (1) Don't do this optimization for run-time compiled methods because this plugin and the
         * nodes it references are not safe for execution at image run time.
         *
         * (2) Don't apply this optimization to deopt targets to save effort since deopt targets are
         * not expected to be optimized
         */
        return !SubstrateCompilationDirectives.isRuntimeCompiledMethod(method) && !SubstrateCompilationDirectives.isDeoptTarget(method);
    }
}
