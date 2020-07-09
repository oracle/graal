/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.substitute;

import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.ArrayBaseOffset;
import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.ArrayIndexScale;
import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.ArrayIndexShift;
import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.FieldOffset;
import static org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo.createStandardInlineInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Builder;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.GuardsStage;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.c.GraalAccess;
import com.oracle.svm.hosted.classinitialization.ClassInitializerGraphBuilderPhase;
import com.oracle.svm.hosted.phases.NoClassInitializationPlugin;
import com.oracle.svm.hosted.snippets.ReflectionPlugins;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

@AutomaticFeature
class AutomaticSubstitutionFeature implements Feature {

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        DuringAnalysisAccessImpl accessImpl = (DuringAnalysisAccessImpl) access;
        UnsafeAutomaticSubstitutionProcessor automaticSubstitutions = accessImpl.getHostVM().getAutomaticSubstitutionProcessor();
        automaticSubstitutions.processComputedValueFields(accessImpl);
    }

}

/**
 * This class tries to registered automatic substitutions for field offset, array base, array index
 * scale and array index shift unsafe computations.
 */
public class UnsafeAutomaticSubstitutionProcessor extends SubstitutionProcessor {

    private static final int BASIC_LEVEL = 1;
    private static final int INFO_LEVEL = 2;
    private static final int DEBUG_LEVEL = 3;

    static class Options {

        @Option(help = "Unsafe automatic substitutions logging level: Disabled=0, Basic=1, Info=2, Debug=3.)")//
        static final HostedOptionKey<Integer> UnsafeAutomaticSubstitutionsLogLevel = new HostedOptionKey<>(BASIC_LEVEL);
    }

    private final AnnotationSubstitutionProcessor annotationSubstitutions;
    private final Map<ResolvedJavaField, ComputedValueField> fieldSubstitutions;

    private final List<ResolvedJavaType> suppressWarnings;

    private static ResolvedJavaType resolvedUnsafeClass;

    private ResolvedJavaMethod unsafeObjectFieldOffsetFieldMethod;
    private ResolvedJavaMethod unsafeObjectFieldOffsetClassStringMethod;
    private ResolvedJavaMethod unsafeArrayBaseOffsetMethod;
    private ResolvedJavaMethod unsafeArrayIndexScaleMethod;
    private ResolvedJavaMethod integerNumberOfLeadingZerosMethod;

    private HashSet<ResolvedJavaMethod> neverInlineSet = new HashSet<>();
    private HashSet<ResolvedJavaMethod> noCheckedExceptionsSet = new HashSet<>();

    private Plugins plugins;

    private SnippetReflectionProvider snippetReflection;

    public UnsafeAutomaticSubstitutionProcessor(AnnotationSubstitutionProcessor annotationSubstitutions, SnippetReflectionProvider snippetReflection) {
        this.snippetReflection = snippetReflection;
        this.annotationSubstitutions = annotationSubstitutions;
        this.fieldSubstitutions = new ConcurrentHashMap<>();
        this.suppressWarnings = new ArrayList<>();
    }

    public void init(ImageClassLoader loader, MetaAccessProvider originalMetaAccess, SVMHost hostVM) {
        ResolvedJavaMethod atomicIntegerFieldUpdaterNewUpdaterMethod;
        ResolvedJavaMethod atomicLongFieldUpdaterNewUpdaterMethod;
        ResolvedJavaMethod atomicReferenceFieldUpdaterNewUpdaterMethod;

        ResolvedJavaMethod fieldSetAccessibleMethod;
        ResolvedJavaMethod fieldGetMethod;

        try {

            Method fieldSetAccessible = Field.class.getMethod("setAccessible", boolean.class);
            fieldSetAccessibleMethod = originalMetaAccess.lookupJavaMethod(fieldSetAccessible);
            neverInlineSet.add(fieldSetAccessibleMethod);

            Method fieldGet = Field.class.getMethod("get", Object.class);
            fieldGetMethod = originalMetaAccess.lookupJavaMethod(fieldGet);
            neverInlineSet.add(fieldGetMethod);

            if (JavaVersionUtil.JAVA_SPEC > 8) {
                /*
                 * Various factory methods in VarHandle query the array base/index or field offsets.
                 * There is no need to analyze these calls because VarHandle accesses are
                 * intrinsified to simple array and field access nodes in
                 * IntrinsifyMethodHandlesInvocationPlugin.
                 */
                for (Method method : loader.findClassByName("java.lang.invoke.VarHandles", true).getDeclaredMethods()) {
                    neverInlineSet.add(originalMetaAccess.lookupJavaMethod(method));
                }
            }

            Class<?> unsafeClass;

            try {
                if (JavaVersionUtil.JAVA_SPEC <= 8) {
                    unsafeClass = Class.forName("sun.misc.Unsafe");
                } else {
                    unsafeClass = Class.forName("jdk.internal.misc.Unsafe");
                }
            } catch (ClassNotFoundException cnfe) {
                throw VMError.shouldNotReachHere(cnfe);
            }

            resolvedUnsafeClass = originalMetaAccess.lookupJavaType(unsafeClass);

            Method unsafeObjectFieldOffset = unsafeClass.getMethod("objectFieldOffset", java.lang.reflect.Field.class);
            unsafeObjectFieldOffsetFieldMethod = originalMetaAccess.lookupJavaMethod(unsafeObjectFieldOffset);
            noCheckedExceptionsSet.add(unsafeObjectFieldOffsetFieldMethod);
            neverInlineSet.add(unsafeObjectFieldOffsetFieldMethod);

            if (JavaVersionUtil.JAVA_SPEC >= 11) {
                /* JDK 11 and later have Unsafe.objectFieldOffset(Class, String). */
                Method unsafeObjectClassStringOffset = unsafeClass.getMethod("objectFieldOffset", java.lang.Class.class, String.class);
                unsafeObjectFieldOffsetClassStringMethod = originalMetaAccess.lookupJavaMethod(unsafeObjectClassStringOffset);
                noCheckedExceptionsSet.add(unsafeObjectFieldOffsetClassStringMethod);
                neverInlineSet.add(unsafeObjectFieldOffsetClassStringMethod);
            }

            Method unsafeArrayBaseOffset = unsafeClass.getMethod("arrayBaseOffset", java.lang.Class.class);
            unsafeArrayBaseOffsetMethod = originalMetaAccess.lookupJavaMethod(unsafeArrayBaseOffset);
            noCheckedExceptionsSet.add(unsafeArrayBaseOffsetMethod);
            neverInlineSet.add(unsafeArrayBaseOffsetMethod);

            Method unsafeArrayIndexScale = unsafeClass.getMethod("arrayIndexScale", java.lang.Class.class);
            unsafeArrayIndexScaleMethod = originalMetaAccess.lookupJavaMethod(unsafeArrayIndexScale);
            noCheckedExceptionsSet.add(unsafeArrayIndexScaleMethod);
            neverInlineSet.add(unsafeArrayIndexScaleMethod);

            Method integerNumberOfLeadingZeros = java.lang.Integer.class.getMethod("numberOfLeadingZeros", int.class);
            integerNumberOfLeadingZerosMethod = originalMetaAccess.lookupJavaMethod(integerNumberOfLeadingZeros);
            neverInlineSet.add(integerNumberOfLeadingZerosMethod);

            Method atomicIntegerFieldUpdaterNewUpdater = java.util.concurrent.atomic.AtomicIntegerFieldUpdater.class.getMethod("newUpdater", Class.class, String.class);
            atomicIntegerFieldUpdaterNewUpdaterMethod = originalMetaAccess.lookupJavaMethod(atomicIntegerFieldUpdaterNewUpdater);
            neverInlineSet.add(atomicIntegerFieldUpdaterNewUpdaterMethod);

            Method atomicLongFieldUpdaterNewUpdater = java.util.concurrent.atomic.AtomicLongFieldUpdater.class.getMethod("newUpdater", Class.class, String.class);
            atomicLongFieldUpdaterNewUpdaterMethod = originalMetaAccess.lookupJavaMethod(atomicLongFieldUpdaterNewUpdater);
            neverInlineSet.add(atomicLongFieldUpdaterNewUpdaterMethod);

            Method atomicReferenceFieldUpdaterNewUpdater = java.util.concurrent.atomic.AtomicReferenceFieldUpdater.class.getMethod("newUpdater", Class.class, Class.class, String.class);
            atomicReferenceFieldUpdaterNewUpdaterMethod = originalMetaAccess.lookupJavaMethod(atomicReferenceFieldUpdaterNewUpdater);
            neverInlineSet.add(atomicReferenceFieldUpdaterNewUpdaterMethod);

        } catch (NoSuchMethodException e) {
            throw VMError.shouldNotReachHere(e);
        }

        /*
         * Create the GraphBuilderPhase which builds the graph for the static initializers.
         *
         * The builder phase will inline the first level callees to detect cases where the offset
         * computation is performed by methods that wrap over the unsafe API. There are two
         * exceptions:
         *
         * 1. Don't inline the invokes that we are trying to match.
         *
         * 2. Don't inline Atomic*FieldUpdater.newUpdater() methods as they lead to false errors.
         * These methods reach calls to Unsafe.objectFieldOffset() whose value is recomputed by
         * RecomputeFieldValue.Kind.AtomicFieldUpdaterOffset.
         */
        StaticInitializerInlineInvokePlugin inlineInvokePlugin = new StaticInitializerInlineInvokePlugin(neverInlineSet);

        plugins = new Plugins(new InvocationPlugins());
        plugins.appendInlineInvokePlugin(inlineInvokePlugin);
        plugins.setClassInitializationPlugin(new NoClassInitializationPlugin());

        ReflectionPlugins.registerInvocationPlugins(loader, snippetReflection, annotationSubstitutions, plugins.getInvocationPlugins(), hostVM, false, false);

        /*
         * Analyzing certain classes leads to false errors. We disable reporting for those classes
         * by default.
         */
        try {
            suppressWarnings.add(originalMetaAccess.lookupJavaType(Class.forName("sun.security.provider.ByteArrayAccess")));
        } catch (ClassNotFoundException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    /**
     * Post-process computed value fields during analysis, e.g, like registering the target field of
     * field offset computation as unsafe accessed. Operations that lookup fields/methods/types in
     * the analysis universe cannot be executed while the substitution is computed. The call to
     * {@link #computeSubstitutions} is made from
     * com.oracle.graal.pointsto.meta.AnalysisUniverse#createType(ResolvedJavaType), before the type
     * is published. Thus if there is a circular dependency between the processed type and one of
     * the fields/methods/types that it needs to access it might lead to a deadlock in
     * {@link AnalysisType} creation. The automatic substitutions for an {@link AnalysisType} are
     * computed just after the type is created but before it is published to other threads so that
     * all threads see the substitutions.
     */
    void processComputedValueFields(DuringAnalysisAccessImpl access) {
        for (ResolvedJavaField field : fieldSubstitutions.values()) {
            if (field instanceof ComputedValue) {
                ComputedValue cvField = (ComputedValue) field;

                switch (cvField.getRecomputeValueKind()) {
                    case FieldOffset:
                        if (access.registerAsUnsafeAccessed(access.getMetaAccess().lookupJavaField(cvField.getTargetField()))) {
                            access.requireAnalysisIteration();
                        }
                        break;
                }
            }
        }
    }

    private void addSubstitutionField(ResolvedJavaField original, ComputedValueField substitution) {
        assert substitution != null;
        assert !fieldSubstitutions.containsKey(original);
        fieldSubstitutions.put(original, substitution);
    }

    @Override
    public ResolvedJavaField lookup(ResolvedJavaField field) {
        if (fieldSubstitutions.containsKey(field)) {
            return fieldSubstitutions.get(field);
        }
        return field;
    }

    @SuppressWarnings("try")
    public void computeSubstitutions(SVMHost hostVM, ResolvedJavaType hostType, OptionValues options) {
        if (hostType.isArray()) {
            return;
        }
        if (hostVM.getClassInitializationSupport().shouldInitializeAtRuntime(hostType)) {
            /*
             * The class initializer of this type is executed at run time. The methods in Unsafe are
             * substituted to return the correct value at image run time, or fail if the field was
             * not registered for unsafe access.
             *
             * Calls to Unsafe.objectFieldOffset() with a constant field parameter are automatically
             * registered for unsafe access in SubstrateGraphBuilderPlugins. While that logic is a
             * bit less powerful compared to the parsing in this class (because this class performs
             * inlining during parsing), it should be sufficient for most cases to automatically
             * perform the unsafe access registration. And if not, the user needs to provide a
             * proper manual configuration file.
             */
            return;
        }

        if (annotationSubstitutions.findSubstitution(hostType).isPresent()) {
            /* If the class is substituted clinit will be eliminated, so bail early. */
            reportSkippedSubstitution(hostType);
            return;
        }

        /* Detect field offset computation in static initializers. */
        ResolvedJavaMethod clinit = hostType.getClassInitializer();

        if (clinit != null && clinit.hasBytecodes()) {
            /* The following directive links the class and makes clinit available. */
            try {
                hostType.getDeclaredConstructors();
            } catch (NoClassDefFoundError | VerifyError t) {
                /* This code should be non-intrusive so we just ignore. */
                return;
            }
            DebugContext debug = new Builder(options).build();
            try (DebugContext.Scope s = debug.scope("Field offset computation", clinit)) {
                StructuredGraph clinitGraph = getStaticInitializerGraph(clinit, options, debug);

                for (Invoke invoke : clinitGraph.getInvokes()) {
                    if (invoke.callTarget() instanceof MethodCallTargetNode) {
                        if (isInvokeTo(invoke, unsafeObjectFieldOffsetFieldMethod)) {
                            processUnsafeObjectFieldOffsetFieldInvoke(hostType, invoke);
                        } else if (isInvokeTo(invoke, unsafeObjectFieldOffsetClassStringMethod)) {
                            processUnsafeObjectFieldOffsetClassStringInvoke(hostType, invoke);
                        } else if (isInvokeTo(invoke, unsafeArrayBaseOffsetMethod)) {
                            processUnsafeArrayBaseOffsetInvoke(hostType, invoke);
                        } else if (isInvokeTo(invoke, unsafeArrayIndexScaleMethod)) {
                            processUnsafeArrayIndexScaleInvoke(hostType, invoke, clinitGraph);
                        }
                    }
                }

            } catch (Throwable e) {
                throw debug.handle(e);
            }
        }

    }

    /**
     * Process call to <code>Unsafe.objectFieldOffset(Field)</code>. The matching logic below
     * applies to the following code pattern:
     *
     * <code> static final long fieldOffset = Unsafe.getUnsafe().objectFieldOffset(X.class.getDeclaredField("f")); </code>
     */
    private void processUnsafeObjectFieldOffsetFieldInvoke(ResolvedJavaType type, Invoke unsafeObjectFieldOffsetInvoke) {
        List<String> unsuccessfulReasons = new ArrayList<>();

        Class<?> targetFieldHolder = null;
        String targetFieldName = null;

        ValueNode fieldArgument = unsafeObjectFieldOffsetInvoke.callTarget().arguments().get(1);
        if (fieldArgument.isConstant()) {
            Field field = snippetReflection.asObject(Field.class, fieldArgument.asJavaConstant());
            if (field == null) {
                unsuccessfulReasons.add("The argument of Unsafe.objectFieldOffset() is a null constant.");
            } else {
                targetFieldHolder = field.getDeclaringClass();
                targetFieldName = field.getName();
            }
        } else {
            unsuccessfulReasons.add("The argument of Unsafe.objectFieldOffset(Field) is not a constant field.");
        }
        processUnsafeObjectFieldOffsetInvoke(type, unsafeObjectFieldOffsetInvoke, unsuccessfulReasons, targetFieldHolder, targetFieldName);
    }

    /**
     * Process call to <code>Unsafe.objectFieldOffset(Class<?> class, String name)</code>. The
     * matching logic below applies to the following code pattern:
     *
     * <code> static final long fieldOffset = Unsafe.getUnsafe().objectFieldOffset(X.class, "f"); </code>
     */
    private void processUnsafeObjectFieldOffsetClassStringInvoke(ResolvedJavaType type, Invoke unsafeObjectFieldOffsetInvoke) {
        List<String> unsuccessfulReasons = new ArrayList<>();

        Class<?> targetFieldHolder = null;
        String targetFieldName = null;

        ValueNode classArgument = unsafeObjectFieldOffsetInvoke.callTarget().arguments().get(1);
        if (classArgument.isConstant()) {
            Class<?> clazz = snippetReflection.asObject(Class.class, classArgument.asJavaConstant());
            if (clazz == null) {
                unsuccessfulReasons.add("The Class argument of Unsafe.objectFieldOffset(Class, String) is a null constant.");
            } else {
                targetFieldHolder = clazz;
            }
        } else {
            unsuccessfulReasons.add("The Class argument of Unsafe.objectFieldOffset(Class, String) is not a constant class.");
        }

        ValueNode nameArgument = unsafeObjectFieldOffsetInvoke.callTarget().arguments().get(2);
        if (nameArgument.isConstant()) {
            String fieldName = snippetReflection.asObject(String.class, nameArgument.asJavaConstant());
            if (fieldName == null) {
                unsuccessfulReasons.add("The String argument of Unsafe.objectFieldOffset(Class, String) is a null String.");
            } else {
                targetFieldName = fieldName;
            }
        } else {
            unsuccessfulReasons.add("The name argument of Unsafe.objectFieldOffset(Class, String) is not a constant String.");
        }
        processUnsafeObjectFieldOffsetInvoke(type, unsafeObjectFieldOffsetInvoke, unsuccessfulReasons, targetFieldHolder, targetFieldName);
    }

    private void processUnsafeObjectFieldOffsetInvoke(
                    ResolvedJavaType type,
                    Invoke unsafeObjectFieldOffsetInvoke,
                    List<String> unsuccessfulReasons,
                    Class<?> targetFieldHolder,
                    String targetFieldName) {

        /*
         * If the value returned by the call to Unsafe.objectFieldOffset() is stored into a field
         * then that must be the offset field.
         */
        SearchResult result = extractValueStoreField(unsafeObjectFieldOffsetInvoke.asNode(), FieldOffset, unsuccessfulReasons);

        /* No field, but the value doesn't have illegal usages, ignore. */
        if (result.valueStoreField == null && !result.illegalUseFound) {
            return;
        }

        ResolvedJavaField offsetField = result.valueStoreField;
        /*
         * If the target field holder and name, and the offset field were found try to register a
         * substitution.
         */
        if (targetFieldHolder != null && targetFieldName != null && offsetField != null) {
            Class<?> finalTargetFieldHolder = targetFieldHolder;
            String finalTargetFieldName = targetFieldName;
            Supplier<ComputedValueField> supplier = () -> new ComputedValueField(offsetField, null, FieldOffset, finalTargetFieldHolder, finalTargetFieldName, false);
            if (tryAutomaticRecomputation(offsetField, FieldOffset, supplier)) {
                reportSuccessfulAutomaticRecomputation(FieldOffset, offsetField, targetFieldHolder.getName() + "." + targetFieldName);
            }
        } else {
            reportUnsuccessfulAutomaticRecomputation(type, offsetField, unsafeObjectFieldOffsetInvoke, FieldOffset, unsuccessfulReasons);
        }
    }

    /**
     * Process call to <code>Unsafe.arrayBaseOffset(Class)</code>. The matching logic below applies
     * to the following code pattern:
     *
     * <code> static final long arrayBaseOffsets = Unsafe.getUnsafe().arrayBaseOffset(byte[].class); </code>
     */
    private void processUnsafeArrayBaseOffsetInvoke(ResolvedJavaType type, Invoke unsafeArrayBaseOffsetInvoke) {
        SnippetReflectionProvider snippetReflectionProvider = GraalAccess.getOriginalSnippetReflection();

        List<String> unsuccessfulReasons = new ArrayList<>();

        Class<?> arrayClass = null;

        ValueNode arrayClassArgument = unsafeArrayBaseOffsetInvoke.callTarget().arguments().get(1);
        if (arrayClassArgument.isJavaConstant()) {
            arrayClass = snippetReflectionProvider.asObject(Class.class, arrayClassArgument.asJavaConstant());
        } else {
            unsuccessfulReasons.add("The argument of the call to Unsafe.arrayBaseOffset() is not a constant.");
        }

        /*
         * If the value returned by the call to Unsafe.arrayBaseOffset() is stored into a field then
         * that must be the offset field.
         */
        SearchResult result = extractValueStoreField(unsafeArrayBaseOffsetInvoke.asNode(), ArrayBaseOffset, unsuccessfulReasons);

        ResolvedJavaField offsetField = result.valueStoreField;
        if (arrayClass != null && offsetField != null) {
            Class<?> finalArrayClass = arrayClass;
            Supplier<ComputedValueField> supplier = () -> new ComputedValueField(offsetField, null, ArrayBaseOffset, finalArrayClass, null, true);
            if (tryAutomaticRecomputation(offsetField, ArrayBaseOffset, supplier)) {
                reportSuccessfulAutomaticRecomputation(ArrayBaseOffset, offsetField, arrayClass.getCanonicalName());
            }
        } else {
            /* Don't report a failure if the value doesn't have illegal usages. */
            if (result.illegalUseFound) {
                reportUnsuccessfulAutomaticRecomputation(type, offsetField, unsafeArrayBaseOffsetInvoke, ArrayBaseOffset, unsuccessfulReasons);
            }
        }
    }

    /**
     * Process call to <code>Unsafe.arrayIndexScale(Class)</code>. The matching logic below applies
     * to the following code pattern:
     *
     * <code> static final long byteArrayIndexScale = Unsafe.getUnsafe().arrayIndexScale(byte[].class); </code>
     */
    private void processUnsafeArrayIndexScaleInvoke(ResolvedJavaType type, Invoke unsafeArrayIndexScale, StructuredGraph clinitGraph) {
        SnippetReflectionProvider snippetReflectionProvider = GraalAccess.getOriginalSnippetReflection();

        List<String> unsuccessfulReasons = new ArrayList<>();

        Class<?> arrayClass = null;

        ValueNode arrayClassArgument = unsafeArrayIndexScale.callTarget().arguments().get(1);
        if (arrayClassArgument.isJavaConstant()) {
            arrayClass = snippetReflectionProvider.asObject(Class.class, arrayClassArgument.asJavaConstant());
        } else {
            unsuccessfulReasons.add("The argument of the call to Unsafe.arrayIndexScale() is not a constant.");
        }

        /*
         * If the value returned by the call to Unsafe.unsafeArrayIndexScale() is stored into a
         * field then that must be the offset field.
         */
        SearchResult result = extractValueStoreField(unsafeArrayIndexScale.asNode(), ArrayIndexScale, unsuccessfulReasons);

        ResolvedJavaField indexScaleField = result.valueStoreField;
        boolean indexScaleComputed = false;
        boolean indexShiftComputed = false;

        if (arrayClass != null) {
            if (indexScaleField != null) {
                Class<?> finalArrayClass = arrayClass;
                Supplier<ComputedValueField> supplier = () -> new ComputedValueField(indexScaleField, null, ArrayIndexScale, finalArrayClass, null, true);
                if (tryAutomaticRecomputation(indexScaleField, ArrayIndexScale, supplier)) {
                    reportSuccessfulAutomaticRecomputation(ArrayIndexScale, indexScaleField, arrayClass.getCanonicalName());
                    indexScaleComputed = true;
                    /* Try substitution for the array index shift computation if present. */
                    indexShiftComputed = processArrayIndexShiftFromField(type, indexScaleField, arrayClass, clinitGraph);
                }
            } else {
                /*
                 * The index scale is not stored into a field, it might be used to compute the index
                 * shift.
                 */
                indexShiftComputed = processArrayIndexShiftFromLocal(type, unsafeArrayIndexScale, arrayClass);
            }
        }
        if (!indexScaleComputed && !indexShiftComputed) {
            /* Don't report a failure if the value doesn't have illegal usages. */
            if (result.illegalUseFound) {
                reportUnsuccessfulAutomaticRecomputation(type, indexScaleField, unsafeArrayIndexScale, ArrayIndexScale, unsuccessfulReasons);
            }
        }
    }

    /**
     * Process array index shift computation which usually follows a call to
     * <code>Unsafe.arrayIndexScale(Class)</code>. The matching logic below applies to the following
     * code pattern:
     *
     * <code>
     *      static final long byteArrayIndexScale = Unsafe.getUnsafe().arrayIndexScale(byte[].class);
     *      static final long byteArrayIndexShift;
     *      static {
     *          if ((byteArrayIndexScale & (byteArrayIndexScale - 1)) != 0) {
     *              throw new Error("data type scale not a power of two");
     *          }
     *          byteArrayIndexShift = 31 - Integer.numberOfLeadingZeros(byteArrayIndexScale);
     *      }
     * </code>
     */
    private boolean processArrayIndexShiftFromField(ResolvedJavaType type, ResolvedJavaField indexScaleField, Class<?> arrayClass, StructuredGraph clinitGraph) {
        for (LoadFieldNode load : clinitGraph.getNodes().filter(LoadFieldNode.class)) {
            if (load.field().equals(indexScaleField)) {
                /*
                 * Try to determine index shift computation without reporting errors, an index scale
                 * field was already found. The case where both scale and shift are computed is
                 * uncommon.
                 */
                if (processArrayIndexShift(type, arrayClass, load, true)) {
                    /*
                     * Return true as soon as an index shift computed from the index scale field is
                     * found. It is very unlikely that there are multiple shift computations from
                     * the same scale.
                     */
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Process array index shift computation which usually follows a call to
     * <code>Unsafe.arrayIndexScale(Class)</code>. The matching logic below applies to the following
     * code pattern:
     *
     * <code>
     *      static final long byteArrayIndexShift;
     *      static {
     *          long byteArrayIndexScale = Unsafe.getUnsafe().arrayIndexScale(byte[].class);
     *          if ((byteArrayIndexScale & (byteArrayIndexScale - 1)) != 0) {
     *              throw new Error("data type scale not a power of two");
     *          }
     *          byteArrayIndexShift = 31 - Integer.numberOfLeadingZeros(byteArrayIndexScale);
     *      }
     * </code>
     */
    private boolean processArrayIndexShiftFromLocal(ResolvedJavaType type, Invoke unsafeArrayIndexScale, Class<?> arrayClass) {
        /* Try to compute index shift. Report errors since the index scale field was not found. */
        return processArrayIndexShift(type, arrayClass, unsafeArrayIndexScale.asNode(), false);
    }

    /** Try to compute the arrayIndexShift. Return true if successful, false otherwise. */
    private boolean processArrayIndexShift(ResolvedJavaType type, Class<?> arrayClass, ValueNode indexScaleValue, boolean silentFailure) {
        NodeIterable<MethodCallTargetNode> loadMethodCallTargetUsages = indexScaleValue.usages().filter(MethodCallTargetNode.class);
        for (MethodCallTargetNode methodCallTarget : loadMethodCallTargetUsages) {
            /* Iterate over all the calls that use the index scale value. */
            if (isInvokeTo(methodCallTarget.invoke(), integerNumberOfLeadingZerosMethod)) {
                /*
                 * Found a call to Integer.numberOfLeadingZeros(int) that uses the array index scale
                 * field. Check if it is used to calculate the array index shift, i.e., log2 of the
                 * array index scale.
                 */

                SearchResult result = null;
                ResolvedJavaField indexShiftField = null;
                List<String> unsuccessfulReasons = new ArrayList<>();
                Invoke numberOfLeadingZerosInvoke = methodCallTarget.invoke();
                NodeIterable<SubNode> numberOfLeadingZerosInvokeSubUsages = numberOfLeadingZerosInvoke.asNode().usages().filter(SubNode.class);
                if (numberOfLeadingZerosInvokeSubUsages.count() == 1) {
                    /*
                     * Found the SubNode. Determine if it computes the array index shift. If so find
                     * the field where the value is stored.
                     */
                    SubNode subNode = numberOfLeadingZerosInvokeSubUsages.first();
                    if (subNodeComputesLog2(subNode, numberOfLeadingZerosInvoke)) {
                        result = extractValueStoreField(subNode, ArrayIndexShift, unsuccessfulReasons);
                        indexShiftField = result.valueStoreField;
                    } else {
                        unsuccessfulReasons.add("The index array scale value provided by " + indexScaleValue + " is not used to calculate the array index shift.");
                    }
                } else {
                    unsuccessfulReasons.add("The call to " + methodCallTarget.targetMethod().format("%H.%n(%p)") + " has multiple uses.");
                }

                if (indexShiftField != null) {
                    ResolvedJavaField finalIndexShiftField = indexShiftField;
                    Supplier<ComputedValueField> supplier = () -> new ComputedValueField(finalIndexShiftField, null, ArrayIndexShift, arrayClass, null, true);
                    if (tryAutomaticRecomputation(indexShiftField, ArrayIndexShift, supplier)) {
                        reportSuccessfulAutomaticRecomputation(ArrayIndexShift, indexShiftField, arrayClass.getCanonicalName());
                        return true;
                    }
                } else {
                    if (!silentFailure) {
                        /*
                         * Passing null here for the computedField is ok, there is no actual field
                         * that we can refer to in the error, and we check for null inside the
                         * method.
                         */
                        if (result != null && result.illegalUseFound || !unsuccessfulReasons.isEmpty()) {
                            reportUnsuccessfulAutomaticRecomputation(type, null, numberOfLeadingZerosInvoke, ArrayIndexShift, unsuccessfulReasons);
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Check if the SubNode computes log2 of one of it's operands. The order of operands is not
     * assumed; both permutations are checked.
     */
    private static boolean subNodeComputesLog2(SubNode subNode, Invoke numberOfLeadingZerosInvokeNode) {
        ValueNode xValueNode = subNode.getX();
        ValueNode yValueNode = subNode.getY();

        if (xValueNode.isJavaConstant() && xValueNode.asJavaConstant().getJavaKind() == JavaKind.Int) {
            PrimitiveConstant xValueConstant = (PrimitiveConstant) xValueNode.asJavaConstant();
            if (xValueConstant.asInt() == 31) {
                assert yValueNode.equals(numberOfLeadingZerosInvokeNode.asNode());
                return true;
            }
        }

        if (yValueNode.isJavaConstant() && yValueNode.asJavaConstant().getJavaKind() == JavaKind.Int) {
            PrimitiveConstant yValueConstant = (PrimitiveConstant) yValueNode.asJavaConstant();
            if (yValueConstant.asInt() == 31) {
                assert xValueNode.equals(numberOfLeadingZerosInvokeNode.asNode());
                return true;
            }
        }

        return false;
    }

    /**
     * Encodes the result of the left-hand-side analysis of an unsafe call, i.e., the search for a
     * static final field where the unsafe value may be stored.
     */
    static final class SearchResult {
        /** The field where the value is stored, if found. */
        final ResolvedJavaField valueStoreField;
        /**
         * Uses that can lead to the unsafe value having side effects that we didn't account for are
         * considered illegal.
         */
        final boolean illegalUseFound;

        private SearchResult(ResolvedJavaField valueStoreField, boolean illegalUseFound) {
            this.valueStoreField = valueStoreField;
            this.illegalUseFound = illegalUseFound;
        }

        static SearchResult foundField(ResolvedJavaField offsetField) {
            return new SearchResult(offsetField, false);
        }

        static SearchResult foundIllegalUse() {
            return new SearchResult(null, true);
        }

        static SearchResult didNotFindIllegalUse() {
            return new SearchResult(null, false);
        }
    }

    /**
     * If the value produced by valueNode is stored into a static final field then that field is
     * returned. If the field is either not static or not final the method returns null and the
     * reason is recorded in the unsuccessfulReasons parameter.
     */
    private static SearchResult extractValueStoreField(ValueNode valueNode, Kind substitutionKind, List<String> unsuccessfulReasons) {
        ResolvedJavaField offsetField = null;
        boolean illegalUseFound = false;

        /*
         * Cycle through all usages looking for the field where the value may be stored. The search
         * continues until all usages are exhausted or an illegal use is found.
         */
        outer: for (Node valueNodeUsage : valueNode.usages()) {
            if (valueNodeUsage instanceof StoreFieldNode && offsetField == null) {
                offsetField = ((StoreFieldNode) valueNodeUsage).field();
            } else if (valueNodeUsage instanceof SignExtendNode && offsetField == null) {
                SignExtendNode signExtendNode = (SignExtendNode) valueNodeUsage;
                for (Node signExtendNodeUsage : signExtendNode.usages()) {
                    if (signExtendNodeUsage instanceof StoreFieldNode && offsetField == null) {
                        offsetField = ((StoreFieldNode) signExtendNodeUsage).field();
                    } else if (isAllowedUnsafeValueSink(signExtendNodeUsage)) {
                        continue;
                    } else {
                        illegalUseFound = true;
                        break outer;
                    }
                }
            } else if (isAllowedUnsafeValueSink(valueNodeUsage)) {
                continue;
            } else {
                illegalUseFound = true;
                break;
            }
        }

        if (offsetField != null && !illegalUseFound) {
            if (offsetField.isStatic() && offsetField.isFinal()) {
                /* Success! We found the static final field where this value is stored. */
                return SearchResult.foundField(offsetField);
            } else {
                String message = "The field " + offsetField.format("%H.%n") + ", where the value produced by the " + kindAsString(substitutionKind) +
                                " computation is stored, is not" + (!offsetField.isStatic() ? " static" : "") + (!offsetField.isFinal() ? " final" : "") + ".";
                unsuccessfulReasons.add(message);
                /* Value is stored to a non static final field. */
                return SearchResult.foundIllegalUse();
            }
        }

        if (illegalUseFound) {
            /* No static final store field was found and the value has illegal usages. */
            String producer;
            String operation;
            if (valueNode instanceof Invoke) {
                Invoke invokeNode = (Invoke) valueNode;
                producer = "call to " + invokeNode.callTarget().targetMethod().format("%H.%n(%p)");
                operation = "call";
            } else if (valueNode instanceof SubNode) {
                producer = "subtraction operation " + valueNode;
                operation = "subtraction";
            } else {
                throw VMError.shouldNotReachHere();
            }
            String message = "Could not determine the field where the value produced by the " + producer +
                            " for the " + kindAsString(substitutionKind) + " computation is stored. The " + operation +
                            " is not directly followed by a field store or by a sign extend node followed directly by a field store. ";
            unsuccessfulReasons.add(message);
            return SearchResult.foundIllegalUse();
        }

        /* No static final store field was found but value does have any illegal usages. */
        return SearchResult.didNotFindIllegalUse();
    }

    /**
     * Determine if the valueNodeUsage parameter is an allowed usage of an offset, indexScale or
     * indexShift unsafe value.
     */
    private static boolean isAllowedUnsafeValueSink(Node valueNodeUsage) {
        if (valueNodeUsage instanceof FrameState) {
            /*
             * The frame state keeps track of the local variables and operand stack at a particular
             * point in the abstract interpretation. This usage can be ignored for the purpose of
             * this analysis.
             */
            return true;
        }
        if (valueNodeUsage instanceof MethodCallTargetNode) {
            /*
             * Passing the value as a parameter to certain methods, like Unsafe methods that read
             * and write memory based on it, is allowed. Passing an unsafe value as a parameter is
             * sound as long as the called method doesn't propagate the value to a dissalowed usage,
             * e.g., like a store to a field that we would then miss.
             */
            MethodCallTargetNode methodCallTarget = (MethodCallTargetNode) valueNodeUsage;
            ResolvedJavaMethod targetMethod = methodCallTarget.targetMethod();
            if (targetMethod.getDeclaringClass().equals(resolvedUnsafeClass)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Try to register the automatic substitution for a field. Bail if the field was deleted or
     * another substitution is detected.
     */
    private boolean tryAutomaticRecomputation(ResolvedJavaField field, Kind kind, Supplier<ComputedValueField> substitutionSupplier) {
        if (annotationSubstitutions.isDeleted(field)) {
            String conflictingSubstitution = "The field " + field.format("%H.%n") + " is marked as deleted. ";
            reportConflictingSubstitution(field, kind, conflictingSubstitution);
            return false;
        } else {
            Optional<ResolvedJavaField> annotationSubstitution = annotationSubstitutions.findSubstitution(field);
            if (annotationSubstitution.isPresent()) {
                /* An annotation substitutions detected. */
                ResolvedJavaField substitutionField = annotationSubstitution.get();
                if (substitutionField instanceof ComputedValueField) {
                    ComputedValueField computedSubstitutionField = (ComputedValueField) substitutionField;
                    if (computedSubstitutionField.getRecomputeValueKind().equals(kind)) {
                        reportUnnecessarySubstitution(substitutionField, computedSubstitutionField);
                        return false;
                    } else if (computedSubstitutionField.getRecomputeValueKind().equals(Kind.None)) {
                        /*
                         * This is essentially and @Alias field. An @Alias for a field with an
                         * automatic recomputed value is allowed but the alias needs to be
                         * overwritten otherwise would read the value from the original field. To do
                         * this a new recomputed value field is registered in the automatic
                         * substitution processor, which follows the annotation substitution
                         * processor in the substitutions chain. Thus, every time the substitutions
                         * chain is queried for the original field, e.g., in
                         * AnalysisUniverse.lookupAllowUnresolved(JavaField), the alias field is
                         * forwarded to the the automatic substitution.
                         */
                        addSubstitutionField(computedSubstitutionField, substitutionSupplier.get());
                        reportOvewrittenSubstitution(substitutionField, kind, computedSubstitutionField.getAnnotated(), computedSubstitutionField.getRecomputeValueKind());
                        return true;
                    } else {
                        String conflictingSubstitution = "Detected RecomputeFieldValue." + computedSubstitutionField.getRecomputeValueKind() +
                                        " " + computedSubstitutionField.getAnnotated().format("%H.%n") + " substitution field. ";
                        reportConflictingSubstitution(substitutionField, kind, conflictingSubstitution);
                        return false;
                    }
                } else {
                    String conflictingSubstitution = "Detected " + substitutionField.format("%H.%n") + " substitution field. ";
                    reportConflictingSubstitution(substitutionField, kind, conflictingSubstitution);
                    return false;
                }
            } else {
                /* No other substitutions detected. */
                addSubstitutionField(field, substitutionSupplier.get());
                return true;
            }
        }
    }

    private static void reportSkippedSubstitution(ResolvedJavaType type) {
        if (Options.UnsafeAutomaticSubstitutionsLogLevel.getValue() >= DEBUG_LEVEL) {
            String msg = "Warning: Skipped automatic unsafe substitutions analysis for type " + type.getName() +
                            ". The entire type is substituted, therefore its class initializer is eliminated.";
            System.out.println(msg);
        }
    }

    private static void reportUnnecessarySubstitution(ResolvedJavaField offsetField, ComputedValueField computedSubstitutionField) {
        if (Options.UnsafeAutomaticSubstitutionsLogLevel.getValue() >= BASIC_LEVEL) {
            Kind kind = computedSubstitutionField.getRecomputeValueKind();
            String kindStr = RecomputeFieldValue.class.getSimpleName() + "." + kind;
            String annotatedFieldStr = computedSubstitutionField.getAnnotated().format("%H.%n");
            String offsetFieldStr = offsetField.format("%H.%n");

            String msg = "Warning: Detected unnecessary " + kindStr + " " + annotatedFieldStr + " substitution field for " + offsetFieldStr + ". ";
            msg += "The annotated field can be removed. This " + kind + " computation can be detected automatically. ";
            msg += "Use option -H:+" + Options.UnsafeAutomaticSubstitutionsLogLevel.getName() + "=" + INFO_LEVEL + " to print all automatically detected substitutions. ";

            System.out.println(msg);
        }
    }

    private static void reportSuccessfulAutomaticRecomputation(Kind substitutionKind, ResolvedJavaField substitutedField, String target) {
        if (Options.UnsafeAutomaticSubstitutionsLogLevel.getValue() >= INFO_LEVEL) {
            String substitutionKindStr = RecomputeFieldValue.class.getSimpleName() + "." + substitutionKind;
            String substitutedFieldStr = substitutedField.format("%H.%n");

            String msg = "Info:" + substitutionKindStr + " substitution automatically registered for " + substitutedFieldStr + ", target element " + target + ".";
            System.out.println(msg);
        }
    }

    private static void reportOvewrittenSubstitution(ResolvedJavaField offsetField, Kind newKind, ResolvedJavaField overwrittenField, Kind overwrittenKind) {
        if (Options.UnsafeAutomaticSubstitutionsLogLevel.getValue() >= INFO_LEVEL) {
            String newKindStr = RecomputeFieldValue.class.getSimpleName() + "." + newKind;
            String overwrittenKindStr = RecomputeFieldValue.class.getSimpleName() + "." + overwrittenKind;
            String offsetFieldStr = offsetField.format("%H.%n");
            String overwrittenFieldStr = overwrittenField.format("%H.%n");

            String msg = "Info: The " + overwrittenKindStr + " " + overwrittenFieldStr + " substitution was overwritten. ";
            msg += "A " + newKindStr + " substitution for " + offsetFieldStr + " was automatically registered.";

            System.out.println(msg);
        }
    }

    private static void reportConflictingSubstitution(ResolvedJavaField field, Kind substitutionKind, String conflictingSubstitution) {
        if (Options.UnsafeAutomaticSubstitutionsLogLevel.getValue() >= BASIC_LEVEL) {
            String fieldStr = field.format("%H.%n");
            String substitutionKindStr = RecomputeFieldValue.class.getSimpleName() + "." + substitutionKind;

            String msg = "Warning: The " + substitutionKindStr + " substitution for " + fieldStr + " could not be recomputed automatically because a conflicting substitution was detected. ";
            msg += "Conflicting substitution: " + conflictingSubstitution;
            msg += "Add a " + substitutionKindStr + " manual substitution for " + fieldStr + ". ";

            System.out.println(msg);
        }
    }

    private void reportUnsuccessfulAutomaticRecomputation(ResolvedJavaType type, ResolvedJavaField computedField, Invoke invoke, Kind substitutionKind, List<String> reasons) {
        String msg = "";
        if (Options.UnsafeAutomaticSubstitutionsLogLevel.getValue() >= BASIC_LEVEL) {
            if (!suppressWarningsFor(type) || Options.UnsafeAutomaticSubstitutionsLogLevel.getValue() >= DEBUG_LEVEL) {
                String substitutionKindStr = RecomputeFieldValue.class.getSimpleName() + "." + substitutionKind;
                String invokeStr = invoke.callTarget().targetMethod().format("%H.%n(%p)");

                msg += substitutionKindStr + " automatic substitution failed. ";
                msg += "The automatic substitution registration was attempted because ";
                if (substitutionKind == ArrayIndexShift) {
                    msg += "an " + ArrayIndexScale + " computation followed by a call to " + invokeStr + " ";
                } else {
                    msg += "a call to " + invokeStr + " ";
                }
                msg += "was detected in the static initializer of " + type.toJavaName() + ". ";
                if (computedField != null) {
                    /* If the computed field is null then reasons will contain the details. */
                    msg += "Add a " + substitutionKindStr + " manual substitution for " + computedField.format("%H.%n") + ". ";
                }
                msg += "Detailed failure reason(s): " + reasons.stream().collect(Collectors.joining(", "));
            }
        }

        if (Options.UnsafeAutomaticSubstitutionsLogLevel.getValue() >= DEBUG_LEVEL) {
            if (suppressWarningsFor(type)) {
                msg += "(This warning is suppressed by default because this type ";
                if (warningsAreWhiteListed(type)) {
                    msg += "is manually added to a white list";
                } else if (isAliased(type)) {
                    msg += "is aliased";
                } else {
                    ResolvedJavaType substitutionType = findSubstitutionType(type);
                    msg += "is substituted by " + substitutionType.toJavaName();
                }
                msg += ".)";
            }
        }

        if (!msg.isEmpty()) {
            System.out.println("Warning: " + msg);
        }
    }

    private static String kindAsString(Kind substitutionKind) {
        switch (substitutionKind) {
            case FieldOffset:
                return "field offset";
            case ArrayBaseOffset:
                return "array base offset";
            case ArrayIndexScale:
                return "array index scale";
            case ArrayIndexShift:
                return "array index shift";
            default:
                throw VMError.shouldNotReachHere("Unexpected substitution kind: " + substitutionKind);
        }
    }

    private boolean suppressWarningsFor(ResolvedJavaType type) {
        return warningsAreWhiteListed(type) || isAliased(type) || findSubstitutionType(type) != null;
    }

    private boolean warningsAreWhiteListed(ResolvedJavaType type) {
        return suppressWarnings.contains(type);
    }

    private ResolvedJavaType findSubstitutionType(ResolvedJavaType type) {
        Optional<ResolvedJavaType> substTypeOptional = annotationSubstitutions.findSubstitution(type);
        return substTypeOptional.orElse(null);
    }

    private boolean isAliased(ResolvedJavaType type) {
        return annotationSubstitutions.isAliased(type);
    }

    private StructuredGraph getStaticInitializerGraph(ResolvedJavaMethod clinit, OptionValues options, DebugContext debug) {
        assert clinit.hasBytecodes();

        StructuredGraph graph = new StructuredGraph.Builder(options, debug).method(clinit).build();
        HighTierContext context = new HighTierContext(GraalAccess.getOriginalProviders(), null, OptimisticOptimizations.NONE);
        graph.setGuardsStage(GuardsStage.FIXED_DEOPTS);

        GraphBuilderPhase.Instance builderPhase = new ClassInitializerGraphBuilderPhase(context, GraphBuilderConfiguration.getDefault(plugins).withEagerResolving(true),
                        context.getOptimisticOptimizations());
        builderPhase.apply(graph, context);

        /*
         * We know that the Unsafe methods that we look for don't throw any checked exceptions.
         * Replace the InvokeWithExceptionNode with InvokeNode.
         */
        for (InvokeWithExceptionNode invoke : graph.getNodes().filter(InvokeWithExceptionNode.class)) {
            if (noCheckedExceptionsSet.contains(invoke.callTarget().targetMethod())) {
                invoke.replaceWithInvoke();
            }
        }
        /* Disable canonicalization of LoadFieldNodes to avoid constant folding of unsafe values. */
        CanonicalizerPhase.createWithoutReadCanonicalization().apply(graph, context);

        return graph;
    }

    private static boolean isInvokeTo(Invoke invoke, ResolvedJavaMethod method) {
        if (method == null) {
            return false;
        }
        ResolvedJavaMethod targetMethod = invoke.callTarget().targetMethod();
        return method.equals(targetMethod);
    }

    static class StaticInitializerInlineInvokePlugin implements InlineInvokePlugin {

        static final int maxDepth = 1;
        static final int maxCodeSize = 500;

        private final HashSet<ResolvedJavaMethod> neverInline;

        StaticInitializerInlineInvokePlugin(HashSet<ResolvedJavaMethod> neverInline) {
            this.neverInline = neverInline;
        }

        @Override
        public InlineInfo shouldInlineInvoke(GraphBuilderContext builder, ResolvedJavaMethod original, ValueNode[] arguments) {

            if (neverInline.contains(original)) {
                return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
            }

            if (original.getCode() != null && original.getCodeSize() < maxCodeSize && builder.getDepth() <= maxDepth) {
                return createStandardInlineInfo(original);
            }

            return null;
        }
    }
}
