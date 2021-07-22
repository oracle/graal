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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.graal.pointsto.StaticAnalysisEngine;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.AbstractStateSplit;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.nodes.spi.Simplifiable;
import org.graalvm.compiler.nodes.spi.SimplifierTool;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.phases.IntrinsifyMethodHandlesInvocationPlugin;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Performs constant folding for some static final fields in classes that are initialized at run
 * time. When a class is initialized at image build time, all static final fields are constant
 * folded by the regular constant folding mechanism. But if a class is initialized at run time, the
 * class initializer of that class is analyzed like any other method, i.e., the static analysis sees
 * a static final field as written and does not perform constant folding. Without constant folding
 * during parsing already, other graph builder plugins like
 * {@link IntrinsifyMethodHandlesInvocationPlugin} do not work on such fields.
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
@AutomaticFeature
final class StaticFinalFieldFoldingFeature implements GraalFeature {

    public static class Options {
        @Option(help = "Optimize static final fields that get a constant assigned in the class initializer.")//
        public static final HostedOptionKey<Boolean> OptStaticFinalFieldFolding = new HostedOptionKey<>(true);
    }

    StaticAnalysisEngine analysis;
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

        access.getHostVM().addMethodAfterParsingHook(this::onAnalysisMethodParsed);
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

        analysis = access.getStaticAnalysisEngine();
    }

    /**
     * Computes a unique index for each optimized field, and prepares the boolean[] array for the
     * image heap that tracks the field initialization state at run time.
     */
    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        analysis = null;

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
            field.getDeclaringClass().getClassInitializer().ensureGraphParsed(analysis);
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

/**
 * Performs the constant folding of fields that are optimizable.
 */
final class StaticFinalFieldFoldingNodePlugin implements NodePlugin {

    private final StaticFinalFieldFoldingFeature feature;

    StaticFinalFieldFoldingNodePlugin(StaticFinalFieldFoldingFeature feature) {
        this.feature = feature;
    }

    @Override
    public boolean handleLoadStaticField(GraphBuilderContext b, ResolvedJavaField field) {
        assert field.isStatic();
        if (!field.isFinal()) {
            return false;
        }

        if (b.getMethod().isClassInitializer()) {
            /*
             * Cannot optimize static field loads in class initializers because that can lead to
             * deadlocks when classes have cyclic dependencies.
             */
            return false;
        }

        AnalysisField aField = StaticFinalFieldFoldingFeature.toAnalysisField(field);
        AnalysisMethod classInitializer = aField.getDeclaringClass().getClassInitializer();
        if (classInitializer == null) {
            /* If there is no class initializer, there cannot be a foldable constant found in it. */
            return false;
        }

        /*
         * The foldable field values are collected during parsing of the class initializer. If the
         * class initializer is not parsed yet, parsing needs to be forced so that {@link
         * StaticFinalFieldFoldingFeature#onAnalysisMethodParsed} determines which fields can be
         * optimized.
         */
        classInitializer.ensureGraphParsed(feature.analysis);

        JavaConstant initializedValue = feature.foldedFieldValues.get(aField);
        if (initializedValue == null) {
            /* Field cannot be optimized. */
            return false;
        }

        /*
         * Create a if-else structure with a PhiNode that either has the optimized value of the
         * field, or the uninitialized value. The initialization status array and the index into
         * that array are not known yet during bytecode parsing, so the array access will be created
         * lazily.
         */
        ValueNode fieldCheckStatusNode = b.add(new IsStaticFinalFieldInitializedNode(field));
        LogicNode isUninitializedNode = b.add(IntegerEqualsNode.create(fieldCheckStatusNode, ConstantNode.forBoolean(false), NodeView.DEFAULT));

        JavaConstant uninitializedValue = b.getConstantReflection().readFieldValue(field, null);
        ConstantNode uninitializedValueNode = ConstantNode.forConstant(uninitializedValue, b.getMetaAccess());
        ConstantNode initializedValueNode = ConstantNode.forConstant(initializedValue, b.getMetaAccess());

        EndNode uninitializedEndNode = b.getGraph().add(new EndNode());
        EndNode initializedEndNode = b.getGraph().add(new EndNode());
        b.add(new IfNode(isUninitializedNode, uninitializedEndNode, initializedEndNode, BranchProbabilityNode.EXTREMELY_SLOW_PATH_PROFILE));

        MergeNode merge = b.append(new MergeNode());
        merge.addForwardEnd(uninitializedEndNode);
        merge.addForwardEnd(initializedEndNode);

        ConstantNode[] phiValueNodes = {uninitializedValueNode, initializedValueNode};
        ValuePhiNode phi = new ValuePhiNode(StampTool.meet(Arrays.asList(phiValueNodes)), merge, phiValueNodes);
        b.setStateAfter(merge);

        b.addPush(field.getJavaKind(), phi);
        return true;
    }

    @Override
    public boolean handleStoreStaticField(GraphBuilderContext b, ResolvedJavaField field, ValueNode value) {
        assert field.isStatic();
        if (!field.isFinal()) {
            return false;
        }

        /*
         * We don't know at this time if the field is really going to be optimized. This is only
         * known after static analysis. This node then replaces itself with an array store.
         */
        b.add(new MarkStaticFinalFieldInitializedNode(field));

        /* Always emit the regular field store. It is necessary also for optimized fields. */
        return false;
    }
}

/**
 * Node that marks a static final field as initialized. This is basically just a store of the value
 * true in the {@link StaticFinalFieldFoldingFeature#fieldInitializationStatus} array. But we cannot
 * immediately emit a {@link StoreIndexedNode} in the bytecode parser because we do not know at the
 * time of parsing if the field can actually be optimized or not. So this node is emitted for every
 * static final field store, and then just removed if the field cannot be optimized.
 */
@NodeInfo(size = NodeSize.SIZE_1, cycles = NodeCycles.CYCLES_1)
final class MarkStaticFinalFieldInitializedNode extends AbstractStateSplit implements Simplifiable {
    public static final NodeClass<MarkStaticFinalFieldInitializedNode> TYPE = NodeClass.create(MarkStaticFinalFieldInitializedNode.class);

    private final ResolvedJavaField field;

    protected MarkStaticFinalFieldInitializedNode(ResolvedJavaField field) {
        super(TYPE, StampFactory.forVoid());
        this.field = field;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        StaticFinalFieldFoldingFeature feature = StaticFinalFieldFoldingFeature.singleton();

        if (feature.fieldInitializationStatus == null) {
            /* Static analysis is still running, we do not know yet which fields are optimized. */
            return;
        }

        Integer fieldCheckIndex = feature.fieldCheckIndexMap.get(StaticFinalFieldFoldingFeature.toAnalysisField(field));
        if (fieldCheckIndex != null) {
            ConstantNode fieldInitializationStatusNode = ConstantNode.forConstant(SubstrateObjectConstant.forObject(feature.fieldInitializationStatus), tool.getMetaAccess(), graph());
            ConstantNode fieldCheckIndexNode = ConstantNode.forInt(fieldCheckIndex, graph());
            ConstantNode trueNode = ConstantNode.forBoolean(true, graph());
            StoreIndexedNode replacementNode = graph().add(new StoreIndexedNode(fieldInitializationStatusNode, fieldCheckIndexNode, null, null, JavaKind.Boolean, trueNode));

            graph().addBeforeFixed(this, replacementNode);
            replacementNode.setStateAfter(stateAfter());
        } else {
            /* Field is not optimized, just remove ourselves. */
        }
        graph().removeFixed(this);
    }
}

/**
 * Node that checks if a static final field is initialized. This is basically just a load of the
 * value in the {@link StaticFinalFieldFoldingFeature#fieldInitializationStatus} array. But we
 * cannot immediately emit a {@link LoadIndexedNode} in the bytecode parser because we do not know
 * at the time of parsing if the declaring class of the field is initialized at image build time.
 */
@NodeInfo(size = NodeSize.SIZE_1, cycles = NodeCycles.CYCLES_1)
final class IsStaticFinalFieldInitializedNode extends FixedWithNextNode implements Simplifiable {
    public static final NodeClass<IsStaticFinalFieldInitializedNode> TYPE = NodeClass.create(IsStaticFinalFieldInitializedNode.class);

    private final ResolvedJavaField field;

    protected IsStaticFinalFieldInitializedNode(ResolvedJavaField field) {
        super(TYPE, StampFactory.forKind(JavaKind.Boolean));
        this.field = field;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        StaticFinalFieldFoldingFeature feature = StaticFinalFieldFoldingFeature.singleton();

        if (feature.fieldInitializationStatus == null) {
            /*
             * Static analysis is still running, we do not know yet if class will get initialized at
             * image build time after static analysis.
             */
            return;
        }

        ValueNode replacementNode;
        if (field.getDeclaringClass().isInitialized()) {
            /*
             * The declaring class of the field has been initialized late after static analysis. So
             * we can also constant fold the field now unconditionally.
             */
            replacementNode = ConstantNode.forBoolean(true, graph());

        } else {
            Integer fieldCheckIndex = feature.fieldCheckIndexMap.get(StaticFinalFieldFoldingFeature.toAnalysisField(field));
            assert fieldCheckIndex != null : "Field must be optimizable: " + field;
            ConstantNode fieldInitializationStatusNode = ConstantNode.forConstant(SubstrateObjectConstant.forObject(feature.fieldInitializationStatus), tool.getMetaAccess(), graph());
            ConstantNode fieldCheckIndexNode = ConstantNode.forInt(fieldCheckIndex, graph());

            replacementNode = graph().addOrUniqueWithInputs(LoadIndexedNode.create(graph().getAssumptions(), fieldInitializationStatusNode, fieldCheckIndexNode,
                            null, JavaKind.Boolean, tool.getMetaAccess(), tool.getConstantReflection()));
        }

        graph().replaceFixed(this, replacementNode);
    }
}
