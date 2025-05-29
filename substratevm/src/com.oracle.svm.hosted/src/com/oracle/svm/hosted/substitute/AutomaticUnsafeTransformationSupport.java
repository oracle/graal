/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.StaticFieldBase;
import static jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo.createStandardInlineInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jdk.graal.compiler.nodes.calc.NarrowNode;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.phases.NoClassInitializationPlugin;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.fieldvaluetransformer.ArrayBaseOffsetFieldValueTransformer;
import com.oracle.svm.core.fieldvaluetransformer.ArrayIndexScaleFieldValueTransformer;
import com.oracle.svm.core.fieldvaluetransformer.ArrayIndexShiftFieldValueTransformer;
import com.oracle.svm.core.fieldvaluetransformer.FieldOffsetFieldValueTransformer;
import com.oracle.svm.core.fieldvaluetransformer.StaticFieldBaseFieldValueTransformer;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FallbackFeature;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.ameta.FieldValueInterceptionSupport;
import com.oracle.svm.hosted.classinitialization.ClassInitializerGraphBuilderPhase;
import com.oracle.svm.hosted.phases.ConstantFoldLoadFieldPlugin;
import com.oracle.svm.hosted.snippets.ReflectionPlugins;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Builder;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.util.ConstantFoldUtil;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This class tries to registered field value transformer for field offset, array base, array index
 * scale and array index shift unsafe computations.
 */
public class AutomaticUnsafeTransformationSupport {

    private static final int BASIC_LEVEL = 1;
    private static final int INFO_LEVEL = 2;
    private static final int DEBUG_LEVEL = 3;

    static class Options {
        @Option(help = "Automatic unsafe field value transformation logging level: Disabled=0, Basic=1, Info=2, Debug=3.)") //
        static final HostedOptionKey<Integer> AutomaticUnsafeTransformationLogLevel = new HostedOptionKey<>(BASIC_LEVEL);
    }

    private final AnnotationSubstitutionProcessor annotationSubstitutions;

    private final List<ResolvedJavaType> suppressWarnings;

    private final ResolvedJavaType jdkInternalUnsafeType;
    private final ResolvedJavaType sunMiscUnsafeType;

    private final ResolvedJavaMethod unsafeStaticFieldOffsetMethod;
    private final ResolvedJavaMethod unsafeStaticFieldBaseMethod;
    private final ResolvedJavaMethod unsafeObjectFieldOffsetFieldMethod;
    private final ResolvedJavaMethod sunMiscUnsafeObjectFieldOffsetMethod;
    private final ResolvedJavaMethod unsafeObjectFieldOffsetClassStringMethod;
    private final ResolvedJavaMethod unsafeArrayBaseOffsetMethod;
    private final ResolvedJavaMethod unsafeArrayIndexScaleMethod;
    private final ResolvedJavaMethod integerNumberOfLeadingZerosMethod;

    private final HashSet<ResolvedJavaMethod> neverInlineSet = new HashSet<>();
    private final HashSet<ResolvedJavaMethod> noCheckedExceptionsSet = new HashSet<>();

    private final Plugins plugins;

    private final OptionValues options;

    public AutomaticUnsafeTransformationSupport(OptionValues options, AnnotationSubstitutionProcessor annotationSubstitutions, ImageClassLoader loader) {
        this.options = options;
        this.annotationSubstitutions = annotationSubstitutions;

        MetaAccessProvider originalMetaAccess = GraalAccess.getOriginalProviders().getMetaAccess();
        try {
            Method fieldSetAccessible = Field.class.getMethod("setAccessible", boolean.class);
            ResolvedJavaMethod fieldSetAccessibleMethod = originalMetaAccess.lookupJavaMethod(fieldSetAccessible);
            neverInlineSet.add(fieldSetAccessibleMethod);

            Method fieldGet = Field.class.getMethod("get", Object.class);
            ResolvedJavaMethod fieldGetMethod = originalMetaAccess.lookupJavaMethod(fieldGet);
            neverInlineSet.add(fieldGetMethod);

            /*
             * Various factory methods in VarHandle query the array base/index or field offsets.
             * There is no need to analyze these calls because VarHandle accesses are intrinsified
             * to simple array and field access nodes during inlining before analysis.
             */
            for (Method method : ReflectionUtil.lookupClass(false, "java.lang.invoke.VarHandles").getDeclaredMethods()) {
                neverInlineSet.add(originalMetaAccess.lookupJavaMethod(method));
            }

            Class<?> sunMiscUnsafeClass = ReflectionUtil.lookupClass(false, "sun.misc.Unsafe");
            sunMiscUnsafeType = originalMetaAccess.lookupJavaType(sunMiscUnsafeClass);
            Class<?> jdkInternalUnsafeClass = jdk.internal.misc.Unsafe.class;
            jdkInternalUnsafeType = originalMetaAccess.lookupJavaType(jdkInternalUnsafeClass);

            Method unsafeStaticFieldOffset = jdkInternalUnsafeClass.getMethod("staticFieldOffset", Field.class);
            unsafeStaticFieldOffsetMethod = originalMetaAccess.lookupJavaMethod(unsafeStaticFieldOffset);
            noCheckedExceptionsSet.add(unsafeStaticFieldOffsetMethod);
            neverInlineSet.add(unsafeStaticFieldOffsetMethod);

            Method unsafeStaticFieldBase = jdkInternalUnsafeClass.getMethod("staticFieldBase", Field.class);
            unsafeStaticFieldBaseMethod = originalMetaAccess.lookupJavaMethod(unsafeStaticFieldBase);
            noCheckedExceptionsSet.add(unsafeStaticFieldBaseMethod);
            neverInlineSet.add(unsafeStaticFieldBaseMethod);

            Method unsafeObjectFieldOffset = jdkInternalUnsafeClass.getMethod("objectFieldOffset", java.lang.reflect.Field.class);
            unsafeObjectFieldOffsetFieldMethod = originalMetaAccess.lookupJavaMethod(unsafeObjectFieldOffset);
            noCheckedExceptionsSet.add(unsafeObjectFieldOffsetFieldMethod);
            neverInlineSet.add(unsafeObjectFieldOffsetFieldMethod);

            Method unsafeObjectClassStringOffset = jdkInternalUnsafeClass.getMethod("objectFieldOffset", java.lang.Class.class, String.class);
            unsafeObjectFieldOffsetClassStringMethod = originalMetaAccess.lookupJavaMethod(unsafeObjectClassStringOffset);
            noCheckedExceptionsSet.add(unsafeObjectFieldOffsetClassStringMethod);
            neverInlineSet.add(unsafeObjectFieldOffsetClassStringMethod);

            /*
             * The JDK checks for hidden classes and record classes in sun.misc.Unsafe before
             * delegating to jdk.internal.misc.Unsafe. When inlined, the checks make control flow
             * too complex to detect offset field assignments.
             */
            Method sunMiscUnsafeObjectFieldOffset = sunMiscUnsafeClass.getMethod("objectFieldOffset", java.lang.reflect.Field.class);
            sunMiscUnsafeObjectFieldOffsetMethod = originalMetaAccess.lookupJavaMethod(sunMiscUnsafeObjectFieldOffset);
            noCheckedExceptionsSet.add(sunMiscUnsafeObjectFieldOffsetMethod);
            neverInlineSet.add(sunMiscUnsafeObjectFieldOffsetMethod);

            Method unsafeArrayBaseOffset = jdkInternalUnsafeClass.getMethod("arrayBaseOffset", java.lang.Class.class);
            unsafeArrayBaseOffsetMethod = originalMetaAccess.lookupJavaMethod(unsafeArrayBaseOffset);
            noCheckedExceptionsSet.add(unsafeArrayBaseOffsetMethod);
            neverInlineSet.add(unsafeArrayBaseOffsetMethod);

            Method unsafeArrayIndexScale = jdkInternalUnsafeClass.getMethod("arrayIndexScale", java.lang.Class.class);
            unsafeArrayIndexScaleMethod = originalMetaAccess.lookupJavaMethod(unsafeArrayIndexScale);
            noCheckedExceptionsSet.add(unsafeArrayIndexScaleMethod);
            neverInlineSet.add(unsafeArrayIndexScaleMethod);

            Method integerNumberOfLeadingZeros = java.lang.Integer.class.getMethod("numberOfLeadingZeros", int.class);
            integerNumberOfLeadingZerosMethod = originalMetaAccess.lookupJavaMethod(integerNumberOfLeadingZeros);
            neverInlineSet.add(integerNumberOfLeadingZerosMethod);

            Method atomicIntegerFieldUpdaterNewUpdater = java.util.concurrent.atomic.AtomicIntegerFieldUpdater.class.getMethod("newUpdater", Class.class, String.class);
            ResolvedJavaMethod atomicIntegerFieldUpdaterNewUpdaterMethod = originalMetaAccess.lookupJavaMethod(atomicIntegerFieldUpdaterNewUpdater);
            neverInlineSet.add(atomicIntegerFieldUpdaterNewUpdaterMethod);

            Method atomicLongFieldUpdaterNewUpdater = java.util.concurrent.atomic.AtomicLongFieldUpdater.class.getMethod("newUpdater", Class.class, String.class);
            ResolvedJavaMethod atomicLongFieldUpdaterNewUpdaterMethod = originalMetaAccess.lookupJavaMethod(atomicLongFieldUpdaterNewUpdater);
            neverInlineSet.add(atomicLongFieldUpdaterNewUpdaterMethod);

            Method atomicReferenceFieldUpdaterNewUpdater = java.util.concurrent.atomic.AtomicReferenceFieldUpdater.class.getMethod("newUpdater", Class.class, Class.class, String.class);
            ResolvedJavaMethod atomicReferenceFieldUpdaterNewUpdaterMethod = originalMetaAccess.lookupJavaMethod(atomicReferenceFieldUpdaterNewUpdater);
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
        NoClassInitializationPlugin classInitializationPlugin = new NoClassInitializationPlugin();
        plugins.setClassInitializationPlugin(classInitializationPlugin);

        FallbackFeature fallbackFeature = ImageSingletons.contains(FallbackFeature.class) ? ImageSingletons.lookup(FallbackFeature.class) : null;
        ReflectionPlugins.registerInvocationPlugins(loader, annotationSubstitutions, classInitializationPlugin, plugins.getInvocationPlugins(), null,
                        ParsingReason.AutomaticUnsafeTransformation, fallbackFeature);

        /*
         * Note: ConstantFoldLoadFieldPlugin should not be installed because it will disrupt
         * patterns that we try to match, e.g., like processArrayIndexShiftFromField() which relies
         * on the fact that the array index shift computation can be tracked back to the matching
         * array index scale.
         */

        /*
         * Analyzing certain classes leads to false errors. We disable reporting for those classes
         * by default.
         */
        suppressWarnings = List.of(originalMetaAccess.lookupJavaType(ReflectionUtil.lookupClass(false, "sun.security.provider.ByteArrayAccess")));
    }

    @SuppressWarnings("try")
    public void computeTransformations(BigBang bb, SVMHost hostVM, ResolvedJavaType hostType) {
        if (hostType.isArray()) {
            return;
        }
        if (!hostVM.getClassInitializationSupport().maybeInitializeAtBuildTime(hostType)) {
            /*
             * The class initializer of this type is executed at run time. The methods in Unsafe are
             * substituted to return the correct value at image runtime, or fail if the field was
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

        if (annotationSubstitutions.findFullSubstitution(hostType).isPresent()) {
            /* If the class is substituted clinit will be eliminated, so bail early. */
            reportSkippedTransformation(hostType);
            return;
        }

        /* Detect field offset computation in static initializers. */
        ResolvedJavaMethod clinit = hostType.getClassInitializer();

        if (clinit != null && clinit.hasBytecodes()) {
            /*
             * Since this analysis is run after the AnalysisType is created at this point the class
             * should already be linked and clinit should be available.
             */
            DebugContext debug = new Builder(options).build();
            try (DebugContext.Scope s = debug.scope("Field offset computation", clinit)) {
                StructuredGraph clinitGraph = getStaticInitializerGraph(clinit, debug);

                for (Invoke invoke : clinitGraph.getInvokes()) {
                    if (invoke.callTarget() instanceof MethodCallTargetNode) {
                        if (isInvokeTo(invoke, unsafeStaticFieldBaseMethod)) {
                            processUnsafeFieldComputation(bb, hostType, invoke, StaticFieldBase);
                        } else if (isInvokeTo(invoke, unsafeObjectFieldOffsetFieldMethod) || isInvokeTo(invoke, sunMiscUnsafeObjectFieldOffsetMethod) ||
                                        isInvokeTo(invoke, unsafeStaticFieldOffsetMethod)) {
                            processUnsafeFieldComputation(bb, hostType, invoke, FieldOffset);
                        } else if (isInvokeTo(invoke, unsafeObjectFieldOffsetClassStringMethod)) {
                            processUnsafeObjectFieldOffsetClassStringInvoke(bb, hostType, invoke);
                        } else if (isInvokeTo(invoke, unsafeArrayBaseOffsetMethod)) {
                            processUnsafeArrayBaseOffsetInvoke(bb, hostType, invoke);
                        } else if (isInvokeTo(invoke, unsafeArrayIndexScaleMethod)) {
                            processUnsafeArrayIndexScaleInvoke(bb, hostType, invoke, clinitGraph);
                        }
                    }
                }

            } catch (Throwable e) {
                throw debug.handle(e);
            }
        }

    }

    /**
     * Process calls to <code>Unsafe.objectFieldOffset(Field)</code>,
     * <code>Unsafe.staticFieldOffset(Field)</code> and <code>Unsafe.staticFieldBase(Field)</code>.
     * The matching logic below applies to the following code patterns:
     * <p>
     * <code> static final long fieldOffset = Unsafe.getUnsafe().objectFieldOffset(X.class.getDeclaredField("f")); </code>
     * <p>
     * <code> static final long fieldOffset = Unsafe.getUnsafe().staticFieldOffset(X.class.getDeclaredField("f")); </code>
     * <p>
     * <code> static final long fieldOffset = Unsafe.getUnsafe().staticFieldBase(X.class.getDeclaredField("f")); </code>
     */
    private void processUnsafeFieldComputation(BigBang bb, ResolvedJavaType type, Invoke invoke, Kind kind) {
        List<Supplier<String>> unsuccessfulReasons = new ArrayList<>();

        Field targetField = null;
        String methodFormat = invoke.callTarget().targetMethod().format("%H.%n(%P)");
        ValueNode fieldArgumentNode = invoke.callTarget().arguments().get(1);
        JavaConstant fieldArgument = nodeAsConstant(fieldArgumentNode);
        if (fieldArgument != null) {
            Field field = GraalAccess.getOriginalSnippetReflection().asObject(Field.class, fieldArgument);
            if (isValidField(invoke, field, unsuccessfulReasons, methodFormat)) {
                targetField = field;
            }
        } else {
            unsuccessfulReasons.add(() -> "The argument of " + methodFormat + " is not a constant value or a field load that can be constant-folded.");
        }
        processUnsafeFieldComputation(bb, type, invoke, kind, unsuccessfulReasons, targetField);
    }

    /**
     * Try to extract a {@link JavaConstant} from a {@link ValueNode}. If the node is a
     * {@link LoadFieldNode} it attempts to constant fold it. We manually constant fold just
     * specific nodes instead of globally installing {@link ConstantFoldLoadFieldPlugin} to avoid
     * folding load filed nodes that could disrupt other patterns that we try to match, e.g., like
     * {@link #processArrayIndexShiftFromField}.
     */
    private JavaConstant nodeAsConstant(ValueNode node) {
        if (node.isConstant()) {
            return node.asJavaConstant();
        } else if (node instanceof LoadFieldNode) {
            LoadFieldNode loadFieldNode = (LoadFieldNode) node;
            ResolvedJavaField field = loadFieldNode.field();
            JavaConstant receiver = null;
            if (!field.isStatic()) {
                ValueNode receiverNode = loadFieldNode.object();
                if (receiverNode.isConstant()) {
                    receiver = receiverNode.asJavaConstant();
                }
            }
            Providers p = GraalAccess.getOriginalProviders();
            ConstantNode result = ConstantFoldUtil.tryConstantFold(p.getConstantFieldProvider(), p.getConstantReflection(), p.getMetaAccess(),
                            field, receiver, options, loadFieldNode.getNodeSourcePosition());
            if (result != null) {
                return result.asJavaConstant();
            }
        }
        return null;
    }

    private boolean isValidField(Invoke invoke, Field field, List<Supplier<String>> unsuccessfulReasons, String methodFormat) {
        if (field == null) {
            unsuccessfulReasons.add(() -> "The argument of " + methodFormat + " is a null constant.");
            return false;
        }

        boolean valid = true;
        if (isInvokeTo(invoke, sunMiscUnsafeObjectFieldOffsetMethod)) {
            Class<?> declaringClass = field.getDeclaringClass();
            if (declaringClass.isRecord()) {
                unsuccessfulReasons.add(() -> "The argument to " + methodFormat + " is a field of a record.");
                valid = false;
            }
            if (declaringClass.isHidden()) {
                unsuccessfulReasons.add(() -> "The argument to " + methodFormat + " is a field of a hidden class.");
                valid = false;
            }
        }
        return valid;
    }

    /**
     * Process call to <code>Unsafe.objectFieldOffset(Class<?> class, String name)</code>. The
     * matching logic below applies to the following code pattern:
     *
     * <code> static final long fieldOffset = Unsafe.getUnsafe().objectFieldOffset(X.class, "f"); </code>
     */
    private void processUnsafeObjectFieldOffsetClassStringInvoke(BigBang bb, ResolvedJavaType type, Invoke unsafeObjectFieldOffsetInvoke) {
        List<Supplier<String>> unsuccessfulReasons = new ArrayList<>();

        Class<?> targetFieldHolder = null;
        String targetFieldName = null;

        ValueNode classArgument = unsafeObjectFieldOffsetInvoke.callTarget().arguments().get(1);
        if (classArgument.isConstant()) {
            Class<?> clazz = GraalAccess.getOriginalSnippetReflection().asObject(Class.class, classArgument.asJavaConstant());
            if (clazz == null) {
                unsuccessfulReasons.add(() -> "The Class argument of Unsafe.objectFieldOffset(Class, String) is a null constant.");
            } else {
                targetFieldHolder = clazz;
            }
        } else {
            unsuccessfulReasons.add(() -> "The Class argument of Unsafe.objectFieldOffset(Class, String) is not a constant class.");
        }

        ValueNode nameArgument = unsafeObjectFieldOffsetInvoke.callTarget().arguments().get(2);
        if (nameArgument.isConstant()) {
            String fieldName = GraalAccess.getOriginalSnippetReflection().asObject(String.class, nameArgument.asJavaConstant());
            if (fieldName == null) {
                unsuccessfulReasons.add(() -> "The String argument of Unsafe.objectFieldOffset(Class, String) is a null String.");
            } else {
                targetFieldName = fieldName;
            }
        } else {
            unsuccessfulReasons.add(() -> "The name argument of Unsafe.objectFieldOffset(Class, String) is not a constant String.");
        }
        Field targetField = null;
        if (unsuccessfulReasons.isEmpty()) {
            targetField = ReflectionUtil.lookupField(true, targetFieldHolder, targetFieldName);
            if (targetField == null) {
                unsuccessfulReasons.add(() -> "The arguments of Unsafe.objectFieldOffset(Class, String) do not reference an existing field.");
            }
        }
        processUnsafeFieldComputation(bb, type, unsafeObjectFieldOffsetInvoke, FieldOffset, unsuccessfulReasons, targetField);
    }

    private void processUnsafeFieldComputation(BigBang bb, ResolvedJavaType type, Invoke invoke, Kind kind, List<Supplier<String>> unsuccessfulReasons, Field targetField) {
        assert kind == FieldOffset || kind == StaticFieldBase;
        /*
         * If the value returned by the call to Unsafe.objectFieldOffset() is stored into a field
         * then that must be the offset field.
         */
        SearchResult result = extractValueStoreField(invoke.asNode(), kind, unsuccessfulReasons);

        /* No field, but the value doesn't have illegal usages, ignore. */
        if (result.valueStoreField == null && !result.illegalUseFound) {
            return;
        }

        ResolvedJavaField valueStoreField = result.valueStoreField;
        /*
         * If the target field holder and name, and the offset field were found try to register a
         * transformation.
         */
        if (targetField != null && valueStoreField != null) {
            if (tryAutomaticTransformation(bb, valueStoreField, kind, null, targetField)) {
                reportSuccessfulAutomaticRecomputation(kind, valueStoreField, targetField.getDeclaringClass().getName() + "." + targetField.getName());
            }
        } else {
            reportUnsuccessfulAutomaticRecomputation(type, valueStoreField, invoke, kind, unsuccessfulReasons);
        }
    }

    /**
     * Process call to <code>Unsafe.arrayBaseOffset(Class)</code>. The matching logic below applies
     * to the following code pattern:
     *
     * <code> static final long arrayBaseOffsets = Unsafe.getUnsafe().arrayBaseOffset(byte[].class); </code>
     */
    private void processUnsafeArrayBaseOffsetInvoke(BigBang bb, ResolvedJavaType type, Invoke unsafeArrayBaseOffsetInvoke) {
        List<Supplier<String>> unsuccessfulReasons = new ArrayList<>();

        Class<?> arrayClass = null;

        ValueNode arrayClassArgument = unsafeArrayBaseOffsetInvoke.callTarget().arguments().get(1);
        if (arrayClassArgument.isJavaConstant()) {
            arrayClass = GraalAccess.getOriginalSnippetReflection().asObject(Class.class, arrayClassArgument.asJavaConstant());
        } else {
            unsuccessfulReasons.add(() -> "The argument of the call to Unsafe.arrayBaseOffset() is not a constant.");
        }

        /*
         * If the value returned by the call to Unsafe.arrayBaseOffset() is stored into a field then
         * that must be the offset field.
         */
        SearchResult result = extractValueStoreField(unsafeArrayBaseOffsetInvoke.asNode(), ArrayBaseOffset, unsuccessfulReasons);

        ResolvedJavaField offsetField = result.valueStoreField;
        if (arrayClass != null && offsetField != null) {
            Class<?> finalArrayClass = arrayClass;
            if (tryAutomaticTransformation(bb, offsetField, ArrayBaseOffset, finalArrayClass, null)) {
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
    private void processUnsafeArrayIndexScaleInvoke(BigBang bb, ResolvedJavaType type, Invoke unsafeArrayIndexScale, StructuredGraph clinitGraph) {
        List<Supplier<String>> unsuccessfulReasons = new ArrayList<>();

        Class<?> arrayClass = null;

        ValueNode arrayClassArgument = unsafeArrayIndexScale.callTarget().arguments().get(1);
        if (arrayClassArgument.isJavaConstant()) {
            arrayClass = GraalAccess.getOriginalSnippetReflection().asObject(Class.class, arrayClassArgument.asJavaConstant());
        } else {
            unsuccessfulReasons.add(() -> "The argument of the call to Unsafe.arrayIndexScale() is not a constant.");
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
                if (tryAutomaticTransformation(bb, indexScaleField, ArrayIndexScale, arrayClass, null)) {
                    reportSuccessfulAutomaticRecomputation(ArrayIndexScale, indexScaleField, arrayClass.getCanonicalName());
                    indexScaleComputed = true;
                    /* Try transformation for the array index shift computation if present. */
                    indexShiftComputed = processArrayIndexShiftFromField(bb, type, indexScaleField, arrayClass, clinitGraph);
                }
            } else {
                /*
                 * The index scale is not stored into a field, it might be used to compute the index
                 * shift.
                 */
                indexShiftComputed = processArrayIndexShiftFromLocal(bb, type, unsafeArrayIndexScale, arrayClass);
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
     *
     * It is important that constant folding is not enabled for the byteArrayIndexScale load because
     * it would break the link between the scale and shift computations.
     */
    private boolean processArrayIndexShiftFromField(BigBang bb, ResolvedJavaType type, ResolvedJavaField indexScaleField, Class<?> arrayClass, StructuredGraph clinitGraph) {
        for (LoadFieldNode load : clinitGraph.getNodes().filter(LoadFieldNode.class)) {
            if (load.field().equals(indexScaleField)) {
                /*
                 * Try to determine index shift computation without reporting errors, an index scale
                 * field was already found. The case where both scale and shift are computed is
                 * uncommon.
                 */
                if (processArrayIndexShift(bb, type, arrayClass, load, true)) {
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
    private boolean processArrayIndexShiftFromLocal(BigBang bb, ResolvedJavaType type, Invoke unsafeArrayIndexScale, Class<?> arrayClass) {
        /* Try to compute index shift. Report errors since the index scale field was not found. */
        return processArrayIndexShift(bb, type, arrayClass, unsafeArrayIndexScale.asNode(), false);
    }

    /**
     * Try to compute the arrayIndexShift. Return true if successful, false otherwise.
     */
    private boolean processArrayIndexShift(BigBang bb, ResolvedJavaType type, Class<?> arrayClass, ValueNode indexScaleValue, boolean silentFailure) {
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
                List<Supplier<String>> unsuccessfulReasons = new ArrayList<>();
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
                        unsuccessfulReasons.add(() -> "The index array scale value provided by " + indexScaleValue + " is not used to calculate the array index shift.");
                    }
                } else {
                    unsuccessfulReasons.add(() -> "The call to " + methodCallTarget.targetMethod().format("%H.%n(%p)") + " has multiple uses.");
                }

                if (indexShiftField != null) {
                    if (tryAutomaticTransformation(bb, indexShiftField, ArrayIndexShift, arrayClass, null)) {
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
        /**
         * The field where the value is stored, if found.
         */
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
    private SearchResult extractValueStoreField(ValueNode valueNode, Kind recomputeKind, List<Supplier<String>> unsuccessfulReasons) {
        ResolvedJavaField valueStoreField = null;
        boolean illegalUseFound = false;

        /*
         * Cycle through all usages looking for the field where the value may be stored. The search
         * continues until all usages are exhausted or an illegal use is found.
         */
        outer: for (Node valueNodeUsage : valueNode.usages()) {
            if (valueNodeUsage instanceof StoreFieldNode storeFieldNode && valueStoreField == null) {
                valueStoreField = storeFieldNode.field();
            } else if (valueNodeUsage instanceof SignExtendNode signExtendNode && valueStoreField == null) {
                for (Node signExtendNodeUsage : signExtendNode.usages()) {
                    if (signExtendNodeUsage instanceof StoreFieldNode storeFieldNode && valueStoreField == null) {
                        valueStoreField = storeFieldNode.field();
                    } else if (!isAllowedUnsafeValueSink(signExtendNodeUsage)) {
                        illegalUseFound = true;
                        break outer;
                    }
                }
            } else if (recomputeKind == ArrayBaseOffset && valueNodeUsage instanceof NarrowNode narrowNode &&
                            narrowNode.getInputBits() == 64 && narrowNode.getResultBits() == 32 && valueStoreField == null) {
                /*
                 * JDK-8344168 changes the return type of Unsafe.arrayBaseOffset from int to long.
                 * The rationale is to avoid 32 bit overflows when deriving values from the result.
                 * However, the offset is still known to fit into in 32 bits. Usages of the function
                 * might therefore cast the result to int, which is fine as it does not lose
                 * information in this case. To support these patterns, we treat casts from long to
                 * int as transparent, similar to sign extend from int to long.
                 */
                for (Node narrowNodeUsage : narrowNode.usages()) {
                    if (narrowNodeUsage instanceof StoreFieldNode storeFieldNode && valueStoreField == null) {
                        valueStoreField = storeFieldNode.field();
                    } else if (narrowNodeUsage instanceof SignExtendNode signExtendNode && valueStoreField == null) {
                        for (Node signExtendNodeUsage : signExtendNode.usages()) {
                            if (signExtendNodeUsage instanceof StoreFieldNode storeFieldNode && valueStoreField == null) {
                                valueStoreField = storeFieldNode.field();
                            } else if (!isAllowedUnsafeValueSink(signExtendNodeUsage)) {
                                illegalUseFound = true;
                                break outer;
                            }
                        }
                    } else if (!isAllowedUnsafeValueSink(narrowNodeUsage)) {
                        illegalUseFound = true;
                        break outer;
                    }
                }
            } else if (!isAllowedUnsafeValueSink(valueNodeUsage)) {
                illegalUseFound = true;
                break;
            }
        }

        if (valueStoreField != null && !illegalUseFound) {
            if (valueStoreField.isStatic() && valueStoreField.isFinal()) {
                /* Success! We found the static final field where this value is stored. */
                return SearchResult.foundField(valueStoreField);
            } else {
                ResolvedJavaField valueStoreFieldFinal = valueStoreField;
                Supplier<String> message = () -> "The field " + valueStoreFieldFinal.format("%H.%n") + ", where the value produced by the " + kindAsString(recomputeKind) +
                                " computation is stored, is not" + (!valueStoreFieldFinal.isStatic() ? " static" : "") + (!valueStoreFieldFinal.isFinal() ? " final" : "") + ".";
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
                throw VMError.shouldNotReachHereUnexpectedInput(valueNode); // ExcludeFromJacocoGeneratedReport
            }
            Supplier<String> message = () -> "Could not determine the field where the value produced by the " + producer +
                            " for the " + kindAsString(recomputeKind) + " computation is stored. The " + operation +
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
    private boolean isAllowedUnsafeValueSink(Node valueNodeUsage) {
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
            ResolvedJavaType declaringClass = methodCallTarget.targetMethod().getDeclaringClass();
            if (declaringClass.equals(jdkInternalUnsafeType) || declaringClass.equals(sunMiscUnsafeType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Try to register the automatic transformation for a field. Bail if the field was deleted or a
     * conflicting substitution is detected.
     */
    private boolean tryAutomaticTransformation(BigBang bb, ResolvedJavaField field, RecomputeFieldValue.Kind kind, Class<?> targetClass, Field targetField) {
        if (annotationSubstitutions.isDeleted(field)) {
            String conflictingSubstitution = "The field " + field.format("%H.%n") + " is marked as deleted. ";
            reportConflictingSubstitution(field, kind, conflictingSubstitution);
            return false;
        } else {
            if (targetField != null && annotationSubstitutions.isDeleted(targetField)) {
                String conflictingSubstitution = "The target field of " + field.format("%H.%n") + " is marked as deleted. ";
                reportSkippedSubstitution(field, kind, conflictingSubstitution);
                return false;
            }

            FieldValueTransformer newTransformer = switch (kind) {
                case ArrayBaseOffset -> new ArrayBaseOffsetFieldValueTransformer(targetClass, field.getType().getJavaKind());
                case ArrayIndexScale -> new ArrayIndexScaleFieldValueTransformer(targetClass, field.getType().getJavaKind());
                case ArrayIndexShift -> new ArrayIndexShiftFieldValueTransformer(targetClass, field.getType().getJavaKind());
                case FieldOffset -> new FieldOffsetFieldValueTransformer(targetField, field.getType().getJavaKind());
                case StaticFieldBase -> new StaticFieldBaseFieldValueTransformer(targetField);
                default -> throw VMError.shouldNotReachHere("Unexpected kind: " + kind);
            };

            FieldValueTransformer existingTransformer = FieldValueInterceptionSupport.singleton().lookupAlreadyRegisteredTransformer(field);
            if (existingTransformer != null) {
                if (existingTransformer.equals(newTransformer)) {
                    reportUnnecessarySubstitution(field, kind);
                } else if (existingTransformer.getClass() == newTransformer.getClass()) {
                    /*
                     * Skip the warning when, for example, the target field of the found manual
                     * substitution differs from the target field of the discovered original offset
                     * computation. This will avoid printing false positives for substitutions like
                     * Target_java_lang_Class_Atomic.*Offset.
                     */
                } else {
                    String conflictingSubstitution = "Detected and existing field value transformer: " + existingTransformer;
                    reportConflictingSubstitution(field, kind, conflictingSubstitution);
                }
                return false;
            }

            Optional<ResolvedJavaField> annotationSubstitution = annotationSubstitutions.findSubstitution(field);
            if (annotationSubstitution.isPresent()) {
                ResolvedJavaField substitutionField = annotationSubstitution.get();
                if (substitutionField instanceof AliasField) {
                    /* An @Alias for a field with an automatic recomputed value is allowed. */
                } else {
                    String conflictingSubstitution = "Detected " + substitutionField.format("%H.%n") + " substitution field. ";
                    reportConflictingSubstitution(field, kind, conflictingSubstitution);
                    return false;
                }
            }

            if (kind == FieldOffset) {
                bb.postTask(debugContext -> bb.getMetaAccess().lookupJavaField(targetField).registerAsUnsafeAccessed(field));
            }
            FieldValueInterceptionSupport.singleton().registerFieldValueTransformer(field, newTransformer);
            return true;
        }
    }

    private static void reportSkippedTransformation(ResolvedJavaType type) {
        if (Options.AutomaticUnsafeTransformationLogLevel.getValue() >= DEBUG_LEVEL) {
            LogUtils.warning("Skipped automatic unsafe transformation analysis for type " + type.getName() +
                            ". The entire type is substituted, therefore its class initializer is eliminated.");
        }
    }

    private static void reportUnnecessarySubstitution(ResolvedJavaField field, Kind kind) {
        if (Options.AutomaticUnsafeTransformationLogLevel.getValue() >= BASIC_LEVEL) {
            String optionStr = SubstrateOptionsParser.commandArgument(Options.AutomaticUnsafeTransformationLogLevel, "+");
            LogUtils.warning("Detected unnecessary %s.%s substitution field for %s. The annotated field can be removed. " +
                            "This %s computation can be detected automatically. Use option -H:+%s=%s to print all automatically detected substitutions.",
                            RecomputeFieldValue.class.getSimpleName(), kind, field.format("%H.%n"), kind, optionStr, INFO_LEVEL);
        }
    }

    private static void reportSuccessfulAutomaticRecomputation(Kind recomputeKind, ResolvedJavaField substitutedField, String target) {
        if (Options.AutomaticUnsafeTransformationLogLevel.getValue() >= INFO_LEVEL) {
            String recomputeKindStr = RecomputeFieldValue.class.getSimpleName() + "." + recomputeKind;
            String substitutedFieldStr = substitutedField.format("%H.%n");
            LogUtils.info("%s substitution automatically registered for %s, target element %s.", recomputeKindStr, substitutedFieldStr, target);
        }
    }

    private static void reportConflictingSubstitution(ResolvedJavaField field, Kind recomputeKind, String conflictingSubstitution) {
        if (Options.AutomaticUnsafeTransformationLogLevel.getValue() >= BASIC_LEVEL) {
            String fieldStr = field.format("%H.%n");
            String recomputeKindStr = RecomputeFieldValue.class.getSimpleName() + "." + recomputeKind;
            LogUtils.warning("The %s substitution for %s could not be recomputed automatically because a conflicting substitution was detected. " +
                            "Conflicting substitution: %s. Add a %s manual substitution for %s.",
                            recomputeKindStr, fieldStr, conflictingSubstitution, recomputeKindStr, fieldStr);
        }
    }

    private static void reportSkippedSubstitution(ResolvedJavaField field, Kind recomputeKind, String conflictingSubstitution) {
        if (Options.AutomaticUnsafeTransformationLogLevel.getValue() >= BASIC_LEVEL) {
            String fieldStr = field.format("%H.%n");
            String recomputeKindStr = RecomputeFieldValue.class.getSimpleName() + "." + recomputeKind;
            LogUtils.warning("The %s substitution for %s could not be recomputed automatically because a conflicting substitution was detected. Conflicting substitution: %s.",
                            recomputeKindStr, fieldStr, conflictingSubstitution);
        }
    }

    private void reportUnsuccessfulAutomaticRecomputation(ResolvedJavaType type, ResolvedJavaField computedField, Invoke invoke, Kind recomputeKind, List<Supplier<String>> reasons) {
        String msg = "";
        if (Options.AutomaticUnsafeTransformationLogLevel.getValue() >= BASIC_LEVEL) {
            if (!suppressWarningsFor(type) || Options.AutomaticUnsafeTransformationLogLevel.getValue() >= DEBUG_LEVEL) {
                String recomputeKindStr = RecomputeFieldValue.class.getSimpleName() + "." + recomputeKind;
                String invokeStr = invoke.callTarget().targetMethod().format("%H.%n(%p)");

                msg += recomputeKindStr + " automatic field value transformation failed. ";
                msg += "The automatic registration was attempted because ";
                if (recomputeKind == ArrayIndexShift) {
                    msg += "an " + ArrayIndexScale + " computation followed by a call to " + invokeStr + " ";
                } else {
                    msg += "a call to " + invokeStr + " ";
                }
                msg += "was detected in the class initializer of " + type.toJavaName() + ". ";
                if (computedField != null) {
                    /* If the computed field is null then reasons will contain the details. */
                    msg += "Add a " + recomputeKindStr + " manual substitution for " + computedField.format("%H.%n") + ". ";
                }
                msg += "Detailed failure reason(s): " + reasons.stream().map(s -> s.get()).collect(Collectors.joining(", "));
            }
        }

        if (Options.AutomaticUnsafeTransformationLogLevel.getValue() >= DEBUG_LEVEL) {
            if (suppressWarningsFor(type)) {
                msg += "(This warning is suppressed by default because this type ";
                if (warningsAreWhiteListed(type)) {
                    msg += "is manually added to a white list";
                } else if (isAliased(type)) {
                    msg += "is aliased";
                } else {
                    ResolvedJavaType substitutionType = findFullSubstitutionType(type);
                    msg += "is fully substituted by " + substitutionType.toJavaName();
                }
                msg += ".)";
            }
        }

        if (!msg.isEmpty()) {
            LogUtils.warning(msg);
        }
    }

    private static String kindAsString(Kind recomputeKind) {
        switch (recomputeKind) {
            case FieldOffset:
                return "field offset";
            case StaticFieldBase:
                return "static field base";
            case ArrayBaseOffset:
                return "array base offset";
            case ArrayIndexScale:
                return "array index scale";
            case ArrayIndexShift:
                return "array index shift";
            default:
                throw VMError.shouldNotReachHere("Unexpected kind: " + recomputeKind);
        }
    }

    private boolean suppressWarningsFor(ResolvedJavaType type) {
        return warningsAreWhiteListed(type) || isAliased(type) || findFullSubstitutionType(type) != null;
    }

    private boolean warningsAreWhiteListed(ResolvedJavaType type) {
        return suppressWarnings.contains(type);
    }

    private ResolvedJavaType findFullSubstitutionType(ResolvedJavaType type) {
        Optional<ResolvedJavaType> substTypeOptional = annotationSubstitutions.findFullSubstitution(type);
        return substTypeOptional.orElse(null);
    }

    private boolean isAliased(ResolvedJavaType type) {
        return annotationSubstitutions.isAliased(type);
    }

    private StructuredGraph getStaticInitializerGraph(ResolvedJavaMethod clinit, DebugContext debug) {
        assert clinit.hasBytecodes();

        HighTierContext context = new HighTierContext(GraalAccess.getOriginalProviders(), null, OptimisticOptimizations.NONE);
        StructuredGraph graph = new StructuredGraph.Builder(options, debug).method(clinit).recordInlinedMethods(false).build();
        graph.getGraphState().configureExplicitExceptionsNoDeopt();

        GraphBuilderPhase.Instance builderPhase = new ClassInitializerGraphBuilderPhase(context, GraphBuilderConfiguration.getDefault(plugins).withEagerResolving(true),
                        context.getOptimisticOptimizations());
        builderPhase.apply(graph, context);

        /*
         * We know that the Unsafe methods that we look for don't throw any checked exceptions.
         * Replace the InvokeWithExceptionNode with InvokeNode.
         */
        for (InvokeWithExceptionNode invoke : graph.getNodes(InvokeWithExceptionNode.TYPE)) {
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
