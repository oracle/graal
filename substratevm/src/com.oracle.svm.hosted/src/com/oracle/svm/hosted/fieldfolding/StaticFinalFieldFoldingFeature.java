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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.phases.util.Providers;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.meta.HostedField;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;

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
 */
@AutomaticallyRegisteredFeature
final class StaticFinalFieldFoldingFeature implements InternalFeature {

    public static class Options {
        @Option(help = "Optimize static final fields that get a constant assigned in the class initializer.")//
        public static final HostedOptionKey<Boolean> OptStaticFinalFieldFolding = new HostedOptionKey<>(true);
    }

    BigBang bb;
    final Map<AnalysisField, JavaConstant> foldedFieldValues = new ConcurrentHashMap<>();
    Map<AnalysisField, Integer> fieldCheckIndexMap;
    boolean[] fieldInitializationStatus;

    public static StaticFinalFieldFoldingFeature singleton() {
        return ImageSingletons.lookup(StaticFinalFieldFoldingFeature.class);
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return Options.OptStaticFinalFieldFolding.getValue();
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        DuringSetupAccessImpl access = (DuringSetupAccessImpl) a;

        access.getHostVM().addMethodAfterParsingListener(this::onAnalysisMethodParsed);
    }

    @Override
    public void registerGraphBuilderPlugins(Providers providers, Plugins plugins, ParsingReason reason) {
        if (reason != ParsingReason.JITCompilation) {
            /*
             * All classes we care about that are JIT compiled, like Truffle languages, are
             * initialized at image build time. So we do not need to make this plugin and the nodes
             * it references safe for execution at image run time.
             */
            plugins.appendNodePlugin(new StaticFinalFieldFoldingNodePlugin(this));
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

        List<AnalysisField> foldedFields = new ArrayList<>(foldedFieldValues.keySet());
        /* Make the fieldCheckIndex deterministic by using an (arbitrary) sort order. */
        foldedFields.sort(Comparator.comparing(field -> field.format("%H.%n")));

        fieldCheckIndexMap = new HashMap<>();
        int fieldCheckIndex = 0;
        for (AnalysisField field : foldedFields) {
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

    /**
     * Invoked for each method that is parsed during static analysis, before the type flow graph of
     * that method is created. If the method is a class initializer, the static final fields that
     * can be optimized are detected and added to {@link #foldedFieldValues}. If the method is not a
     * class initializer, it is verified that there is no illegal store to an optimized field.
     */
    void onAnalysisMethodParsed(AnalysisMethod method, StructuredGraph graph) {
        boolean isClassInitializer = method.isClassInitializer();
        Map<AnalysisField, JavaConstant> optimizableFields = isClassInitializer ? new HashMap<>() : null;
        Set<AnalysisField> ineligibleFields = isClassInitializer ? new HashSet<>() : null;

        for (Node n : graph.getNodes()) {
            if (n instanceof StoreFieldNode) {
                StoreFieldNode node = (StoreFieldNode) n;
                AnalysisField field = (AnalysisField) node.field();
                if (field.isStatic() && field.isFinal()) {
                    if (isClassInitializer && field.getDeclaringClass().equals(method.getDeclaringClass())) {
                        analyzeStoreInClassInitializer(node, field, optimizableFields, ineligibleFields);
                    } else {
                        analyzeStoreOutsideClassInitializer(method, field);
                    }
                }
            }
        }

        if (optimizableFields != null && !optimizableFields.isEmpty()) {
            foldedFieldValues.putAll(optimizableFields);
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
    private void analyzeStoreOutsideClassInitializer(AnalysisMethod method, AnalysisField field) {
        if (field.getDeclaringClass().getClassInitializer() != null) {
            /*
             * Analyze the class initializer of the class that defines the field. This ensures that
             * the order in which graphs are parsed during static analysis does not affect the
             * outcome of the optimizable check below.
             */
            field.getDeclaringClass().getClassInitializer().ensureGraphParsed(bb);
        }

        if (foldedFieldValues.containsKey(field)) {
            /*
             * The field is found optimizable, i.e., a constant is assigned in a class initializer,
             * and then the field is written again outside the class initializer. The user needs to
             * disable the optimization.
             */
            throw new UnsupportedOperationException("" +
                            "The static final field optimization found a static final field that is initialized both inside and outside of its class initializer. " +
                            "Field " + field.format("%H.%n") + " is stored in method " + method.format("%H.%n(%p)") + ". " +
                            "This violates the Java bytecode specification. " +
                            "You can use " + SubstrateOptionsParser.commandArgument(Options.OptStaticFinalFieldFolding, "-") + " to disable the optimization.");
        }
    }

    static AnalysisField toAnalysisField(ResolvedJavaField field) {
        if (field instanceof HostedField) {
            return ((HostedField) field).wrapped;
        } else {
            return (AnalysisField) field;
        }
    }
}
