/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import static com.oracle.graal.pointsto.util.AnalysisError.shouldNotReachHere;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.MethodFilter;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.java.GraphBuilderPhase.Instance;
import org.graalvm.compiler.nodes.StaticDeoptimizingNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.UnsafeAccessNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.java.AccessFieldNode;
import org.graalvm.compiler.nodes.java.AccessMonitorNode;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.common.BoxNodeIdentityPhase;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;
import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.RelocatedPointer;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.FieldValueComputer;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysisGraphDecoder;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysisPolicy;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.NeverInlineTrivial;
import com.oracle.svm.core.RuntimeAssertionsSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateOptions.OptimizationLevel;
import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallLinkage;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.stackvalue.StackValueNode;
import com.oracle.svm.core.graal.thread.VMThreadLocalAccess;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.heap.Target_java_lang_ref_Reference;
import com.oracle.svm.core.heap.UnknownClass;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.heap.UnknownPrimitiveField;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.HubType;
import com.oracle.svm.core.hub.ReferenceType;
import com.oracle.svm.core.jdk.InternalVMMethod;
import com.oracle.svm.core.jdk.LambdaFormHiddenMethod;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.thread.Continuation;
import com.oracle.svm.core.util.HostedStringDeduplication;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.analysis.CustomTypeFieldHandler;
import com.oracle.svm.hosted.analysis.SVMParsingSupport;
import com.oracle.svm.hosted.classinitialization.ClassInitializationOptions;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.classinitialization.SimulateClassInitializerSupport;
import com.oracle.svm.hosted.code.InliningUtilities;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives;
import com.oracle.svm.hosted.code.UninterruptibleAnnotationChecker;
import com.oracle.svm.hosted.heap.PodSupport;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.phases.AnalysisGraphBuilderPhase;
import com.oracle.svm.hosted.phases.ImplicitAssertionsPhase;
import com.oracle.svm.hosted.phases.InlineBeforeAnalysisGraphDecoderImpl;
import com.oracle.svm.hosted.phases.InlineBeforeAnalysisPolicyImpl;
import com.oracle.svm.hosted.phases.InlineBeforeAnalysisPolicyUtils;
import com.oracle.svm.hosted.substitute.UnsafeAutomaticSubstitutionProcessor;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class SVMHost extends HostVM {
    private final ConcurrentHashMap<AnalysisType, DynamicHub> typeToHub = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<DynamicHub, AnalysisType> hubToType = new ConcurrentHashMap<>();

    private final Map<String, EnumSet<AnalysisType.UsageKind>> forbiddenTypes;
    private final Platform platform;
    private final ClassInitializationSupport classInitializationSupport;
    private final LinkAtBuildTimeSupport linkAtBuildTimeSupport;
    private final HostedStringDeduplication stringTable;
    private final UnsafeAutomaticSubstitutionProcessor automaticSubstitutions;

    /**
     * Optionally keep the Graal graphs alive during analysis. This increases the memory footprint
     * and should therefore never be enabled in production.
     */
    private ConcurrentMap<AnalysisMethod, StructuredGraph> analysisGraphs;

    /*
     * Information that is collected from the Graal graphs parsed during analysis, so that we do not
     * need to keep the whole graphs alive.
     */
    private final ConcurrentMap<AnalysisMethod, Boolean> containsStackValueNode = new ConcurrentHashMap<>();
    private final ConcurrentMap<AnalysisMethod, Boolean> classInitializerSideEffect = new ConcurrentHashMap<>();
    private final ConcurrentMap<AnalysisMethod, Set<AnalysisType>> initializedClasses = new ConcurrentHashMap<>();
    private final ConcurrentMap<AnalysisMethod, Boolean> analysisTrivialMethods = new ConcurrentHashMap<>();

    private final Set<AnalysisField> finalFieldsInitializedOutsideOfConstructor = ConcurrentHashMap.newKeySet();
    private final MultiMethodAnalysisPolicy multiMethodAnalysisPolicy;
    private final SVMParsingSupport parsingSupport;
    private final InlineBeforeAnalysisPolicy inlineBeforeAnalysisPolicy;

    @SuppressWarnings("this-escape")
    public SVMHost(OptionValues options, ClassLoader classLoader, ClassInitializationSupport classInitializationSupport,
                    UnsafeAutomaticSubstitutionProcessor automaticSubstitutions, Platform platform) {
        super(options, classLoader);
        this.classInitializationSupport = classInitializationSupport;
        this.stringTable = HostedStringDeduplication.singleton();
        this.forbiddenTypes = setupForbiddenTypes(options);
        this.automaticSubstitutions = automaticSubstitutions;
        this.platform = platform;
        this.linkAtBuildTimeSupport = LinkAtBuildTimeSupport.singleton();
        if (ImageSingletons.contains(MultiMethodAnalysisPolicy.class)) {
            multiMethodAnalysisPolicy = ImageSingletons.lookup(MultiMethodAnalysisPolicy.class);
        } else {
            /* Install the default so no other policy can be installed. */
            ImageSingletons.add(HostVM.MultiMethodAnalysisPolicy.class, DEFAULT_MULTIMETHOD_ANALYSIS_POLICY);
            multiMethodAnalysisPolicy = DEFAULT_MULTIMETHOD_ANALYSIS_POLICY;
        }
        InlineBeforeAnalysisPolicyUtils inliningUtils = getInlineBeforeAnalysisPolicyUtils();
        inlineBeforeAnalysisPolicy = new InlineBeforeAnalysisPolicyImpl(this, inliningUtils);
        if (ImageSingletons.contains(SVMParsingSupport.class)) {
            parsingSupport = ImageSingletons.lookup(SVMParsingSupport.class);
            parsingSupport.initializeInlineBeforeAnalysisPolicy(this, inliningUtils);
        } else {
            parsingSupport = null;
        }
    }

    protected InlineBeforeAnalysisPolicyUtils getInlineBeforeAnalysisPolicyUtils() {
        return new InlineBeforeAnalysisPolicyUtils();
    }

    private static Map<String, EnumSet<AnalysisType.UsageKind>> setupForbiddenTypes(OptionValues options) {
        List<String> forbiddenTypesOptionValues = SubstrateOptions.ReportAnalysisForbiddenType.getValue(options).values();
        Map<String, EnumSet<AnalysisType.UsageKind>> forbiddenTypes = new HashMap<>();
        for (String forbiddenTypesOptionValue : forbiddenTypesOptionValues) {
            String[] typeNameUsageKind = forbiddenTypesOptionValue.split(":", 2);
            EnumSet<AnalysisType.UsageKind> usageKinds;
            if (typeNameUsageKind.length == 1) {
                usageKinds = EnumSet.allOf(AnalysisType.UsageKind.class);
            } else {
                usageKinds = EnumSet.noneOf(AnalysisType.UsageKind.class);
                String[] usageKindValues = typeNameUsageKind[1].split(",");
                for (String usageKindValue : usageKindValues) {
                    usageKinds.add(AnalysisType.UsageKind.valueOf(usageKindValue));
                }

            }
            forbiddenTypes.put(typeNameUsageKind[0], usageKinds);
        }
        return forbiddenTypes.isEmpty() ? null : forbiddenTypes;
    }

    @Override
    public void checkForbidden(AnalysisType type, AnalysisType.UsageKind kind) {
        if (SubstrateOptions.VerifyNamingConventions.getValue()) {
            NativeImageGenerator.checkName(type.getWrapped().toJavaName(), null, null);
        }

        if (forbiddenTypes == null) {
            return;
        }

        /*
         * We check the class hierarchy, because putting a restriction on a superclass should cover
         * all subclasses too.
         *
         * We do not check the interface hierarchy for now, although it would be possible. But it
         * seems less likely that someone registers an interface as forbidden.
         */
        for (AnalysisType cur = type; cur != null; cur = cur.getSuperclass()) {
            EnumSet<AnalysisType.UsageKind> forbiddenType = forbiddenTypes.get(cur.getWrapped().toJavaName());
            if (forbiddenType != null && forbiddenType.contains(kind)) {
                throw new UnsupportedFeatureException("Forbidden type " + cur.getWrapped().toJavaName() +
                                (cur.equals(type) ? "" : " (superclass of " + type.getWrapped().toJavaName() + ")") +
                                " UsageKind: " + kind);
            }
        }
    }

    @Override
    public Instance createGraphBuilderPhase(HostedProviders builderProviders, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts,
                    IntrinsicContext initialIntrinsicContext) {
        return new AnalysisGraphBuilderPhase(builderProviders, graphBuilderConfig, optimisticOpts, initialIntrinsicContext, builderProviders.getWordTypes(), this);
    }

    @Override
    public String getImageName() {
        return SubstrateOptions.Name.getValue(options);
    }

    @Override
    public boolean isRelocatedPointer(UniverseMetaAccess metaAccess, JavaConstant constant) {
        return metaAccess.isInstanceOf(constant, RelocatedPointer.class);
    }

    @Override
    public void installInThread(Object vmConfig) {
        super.installInThread(vmConfig);
        assert vmConfig == ImageSingletonsSupportImpl.HostedManagement.get();
    }

    @Override
    public Object getConfiguration() {
        return ImageSingletonsSupportImpl.HostedManagement.getAndAssertExists();
    }

    @Override
    public void registerType(AnalysisType analysisType) {

        DynamicHub hub = createHub(analysisType);
        /* Register the hub->type and type->hub mappings. */
        Object existing = typeToHub.put(analysisType, hub);
        assert existing == null;
        existing = hubToType.put(hub, analysisType);
        assert existing == null;

    }

    @Override
    public void onTypeReachable(AnalysisType analysisType) {
        if (!analysisType.isReachable()) {
            throw VMError.shouldNotReachHere("Registering and initializing a type that was not yet marked as reachable: " + analysisType);
        }
        if (BuildPhaseProvider.isAnalysisFinished()) {
            throw VMError.shouldNotReachHere("Initializing type after analysis: " + analysisType);
        }

        /* Decide when the type should be initialized. */
        classInitializationSupport.maybeInitializeAtBuildTime(analysisType);

        /* Compute the automatic substitutions. */
        automaticSubstitutions.computeSubstitutions(this, GraalAccess.getOriginalProviders().getMetaAccess().lookupJavaType(analysisType.getJavaClass()));
    }

    @Override
    public boolean isInitialized(AnalysisType type) {
        boolean initializedAtBuildTime = classInitializationSupport.maybeInitializeAtBuildTime(type);
        assert !initializedAtBuildTime || type.getWrapped().isInitialized() : "Types that are not marked for runtime initializations must have been initialized: " + type;

        return initializedAtBuildTime;
    }

    private final boolean parseOnce = SubstrateOptions.parseOnce();

    @Override
    public GraphBuilderConfiguration updateGraphBuilderConfiguration(GraphBuilderConfiguration config, AnalysisMethod method) {
        GraphBuilderConfiguration updatedConfig = config.withRetainLocalVariables(retainLocalVariables()).withUnresolvedIsError(linkAtBuildTimeSupport.linkAtBuildTime(method.getDeclaringClass()))
                        .withFullInfopoints(
                                        SubstrateOptions.getSourceLevelDebug() && SubstrateOptions.getSourceLevelDebugFilter().test(method.getDeclaringClass().toJavaName()));
        if (parsingSupport != null) {
            return parsingSupport.updateGraphBuilderConfiguration(updatedConfig, method);
        }
        return updatedConfig;
    }

    private boolean retainLocalVariables() {
        if (parseOnce) {
            /*
             * Disabling liveness analysis preserves the values of local variables beyond the
             * bytecode-liveness. This greatly helps debugging. Note that when local variable
             * numbers are reused by javac, local variables can still be assigned to illegal values.
             */
            return SubstrateOptions.optimizationLevel() == OptimizationLevel.O0;

        } else {
            /*
             * We want to always disable the liveness analysis, since we want the points-to analysis
             * to be as conservative as possible. The analysis results can then be used with the
             * liveness analysis enabled or disabled.
             */
            return true;
        }
    }

    @Override
    public Optional<AnalysisMethod> handleForeignCall(ForeignCallDescriptor foreignCallDescriptor, ForeignCallsProvider foreignCallsProvider) {
        SubstrateForeignCallsProvider foreignCalls = (SubstrateForeignCallsProvider) foreignCallsProvider;
        /* In unit tests, we run with no registered foreign calls. */
        Optional<AnalysisMethod> targetMethod = Optional.empty();
        if (foreignCalls.getForeignCalls().size() > 0) {
            SubstrateForeignCallLinkage linkage = foreignCalls.lookupForeignCall(foreignCallDescriptor);
            targetMethod = Optional.of((AnalysisMethod) linkage.getMethod());
        }
        return targetMethod;
    }

    public DynamicHub dynamicHub(ResolvedJavaType type) {
        AnalysisType aType;
        if (type instanceof AnalysisType) {
            aType = (AnalysisType) type;
        } else if (type instanceof HostedType) {
            aType = ((HostedType) type).getWrapped();
        } else {
            throw VMError.shouldNotReachHere("Found unsupported type: " + type);
        }
        /* Ensure that the hub is registered in both typeToHub and hubToType. */
        return typeToHub.get(aType);
    }

    public AnalysisType lookupType(DynamicHub hub) {
        assert hub != null : "Hub must not be null";
        return hubToType.get(hub);
    }

    private DynamicHub createHub(AnalysisType type) {
        DynamicHub superHub = null;
        if ((type.isInstanceClass() && type.getSuperclass() != null) || type.isArray()) {
            superHub = dynamicHub(type.getSuperclass());
        }
        DynamicHub componentHub = null;
        if (type.isArray()) {
            componentHub = dynamicHub(type.getComponentType());
        }
        Class<?> javaClass = type.getJavaClass();
        int modifiers = javaClass.getModifiers();

        /*
         * If the class is an application class then it was loaded by NativeImageClassLoader. The
         * ClassLoaderFeature object replacer will unwrap the original AppClassLoader from the
         * NativeImageClassLoader.
         */
        ClassLoader hubClassLoader = javaClass.getClassLoader();

        /* Class names must be interned strings according to the Java specification. */
        String className = type.toClassName().intern();
        /*
         * There is no need to have file names and simple binary names as interned strings. So we
         * perform our own de-duplication.
         */
        String sourceFileName = stringTable.deduplicate(type.getSourceFileName(), true);
        String simpleBinaryName = stringTable.deduplicate(getSimpleBinaryName(javaClass), true);

        Class<?> nestHost = javaClass.getNestHost();
        boolean isHidden = javaClass.isHidden();
        boolean isRecord = javaClass.isRecord();
        boolean assertionStatus = RuntimeAssertionsSupport.singleton().desiredAssertionStatus(javaClass);
        boolean isSealed = javaClass.isSealed();
        boolean isVMInternal = type.isAnnotationPresent(InternalVMMethod.class);
        boolean isLambdaFormHidden = type.isAnnotationPresent(LambdaFormHiddenMethod.class);
        boolean isLinked = type.isLinked();

        return new DynamicHub(javaClass, className, computeHubType(type), computeReferenceType(type), superHub, componentHub, sourceFileName, modifiers, hubClassLoader,
                        isHidden, isRecord, nestHost, assertionStatus, type.hasDefaultMethods(), type.declaresDefaultMethods(), isSealed, isVMInternal, isLambdaFormHidden, isLinked, simpleBinaryName,
                        getDeclaringClass(javaClass));
    }

    private final Method getDeclaringClass0 = ReflectionUtil.lookupMethod(Class.class, "getDeclaringClass0");

    private Object getDeclaringClass(Class<?> javaClass) {
        try {
            return getDeclaringClass0.invoke(javaClass);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof LinkageError) {
                if (cause instanceof IncompatibleClassChangeError) {
                    /*
                     * While `IncompatibleClassChangeError` is a `LinkageError`, it doesn't actually
                     * mean that the class that is supposed to declare `javaClass` cannot be linked,
                     * but rather that there is some sort of mismatch between that class and
                     * `javaClass`, so we just rethrow the error at run time.
                     *
                     * For example, there have been cases where the Kotlin compiler generates
                     * anonymous classes that do not agree with their declaring classes on the
                     * `InnerClasses` attribute.
                     */
                    return cause;
                }
                return handleLinkageError(javaClass, getDeclaringClass0.getName(), (LinkageError) cause);
            }
            throw VMError.shouldNotReachHere(e);
        } catch (IllegalAccessException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private LinkageError handleLinkageError(Class<?> javaClass, String methodName, LinkageError linkageError) {
        if (!linkAtBuildTimeSupport.linkAtBuildTime(javaClass)) {
            return linkageError; /* It's rethrown at run time. */
        }
        String message = "Discovered a type for which " + methodName + " cannot be called: " + javaClass.getTypeName() + ". " +
                        linkAtBuildTimeSupport.errorMessageFor(javaClass);
        throw new UnsupportedFeatureException(message, linkageError);
    }

    private final Method getSimpleBinaryName0 = ReflectionUtil.lookupMethod(Class.class, "getSimpleBinaryName0");

    private String getSimpleBinaryName(Class<?> javaClass) {
        try {
            return (String) getSimpleBinaryName0.invoke(javaClass);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    public static boolean isUnknownClass(ResolvedJavaType resolvedJavaType) {
        return resolvedJavaType.getAnnotation(UnknownClass.class) != null;
    }

    public ClassInitializationSupport getClassInitializationSupport() {
        return classInitializationSupport;
    }

    public UnsafeAutomaticSubstitutionProcessor getAutomaticSubstitutionProcessor() {
        return automaticSubstitutions;
    }

    private static int computeHubType(AnalysisType type) {
        if (type.isArray()) {
            if (type.getComponentType().isPrimitive() || type.getComponentType().isWordType()) {
                return HubType.PRIMITIVE_ARRAY;
            } else {
                return HubType.OBJECT_ARRAY;
            }
        } else if (type.isInstanceClass()) {
            if (Reference.class.isAssignableFrom(type.getJavaClass())) {
                return HubType.REFERENCE_INSTANCE;
            } else if (PodSupport.isPresent() && PodSupport.singleton().isPodClass(type.getJavaClass())) {
                return HubType.POD_INSTANCE;
            } else if (Continuation.isSupported() && type.getJavaClass() == StoredContinuation.class) {
                return HubType.STORED_CONTINUATION_INSTANCE;
            }
            assert !Target_java_lang_ref_Reference.class.isAssignableFrom(type.getJavaClass()) : "should not see substitution type here";
            return HubType.INSTANCE;
        }
        return HubType.OTHER;
    }

    private static ReferenceType computeReferenceType(AnalysisType type) {
        Class<?> clazz = type.getJavaClass();
        if (Reference.class.isAssignableFrom(clazz)) {
            if (PhantomReference.class.isAssignableFrom(clazz)) {
                return ReferenceType.Phantom;
            } else if (SoftReference.class.isAssignableFrom(clazz)) {
                return ReferenceType.Soft;
            } else {
                /* Treat all other java.lang.Reference subclasses as weak references. */
                return ReferenceType.Weak;
            }
        }
        return ReferenceType.None;
    }

    @Override
    public void checkType(ResolvedJavaType type, AnalysisUniverse universe) {
        Class<?> originalClass = OriginalClassProvider.getJavaClass(type);
        ClassLoader originalClassLoader = originalClass.getClassLoader();
        if (NativeImageSystemClassLoader.singleton().isDisallowedClassLoader(originalClassLoader)) {
            String message = "Class " + originalClass.getName() + " was loaded by " + originalClassLoader + " and not by the current image class loader " + classLoader + ". ";
            message += "This usually means that some objects from a previous build leaked in the current build. ";
            message += "This can happen when using the image build server. ";
            message += "To fix the issue you must reset all static state from the bootclasspath and application classpath that points to the application objects. ";
            message += "If the offending code is in JDK code please file a bug with GraalVM. ";
            throw new UnsupportedFeatureException(message);
        }
        if (originalClass.isRecord()) {
            for (var recordComponent : originalClass.getRecordComponents()) {
                if (WordBase.class.isAssignableFrom(recordComponent.getType())) {
                    throw UserError.abort("Records cannot use Word types. " +
                                    "The equals/hashCode/toString implementation of records uses method handles, and Word types are not supported as parameters of method handle invocations. " +
                                    "Record type: `" + originalClass.getTypeName() + "`, component: `" + recordComponent.getName() + "` of type `" + recordComponent.getType().getTypeName() + "`");
                }
            }
        }
    }

    protected boolean deoptsForbidden(AnalysisMethod method) {
        /*
         * Runtime compiled methods can deoptimize.
         */
        return method.getMultiMethodKey() != SubstrateCompilationDirectives.RUNTIME_COMPILED_METHOD;
    }

    @Override
    public void methodAfterParsingHook(BigBang bb, AnalysisMethod method, StructuredGraph graph) {
        if (graph != null) {
            if (deoptsForbidden(method)) {
                graph.getGraphState().configureExplicitExceptionsNoDeoptIfNecessary();
            }

            if (parseOnce) {
                if (parsingSupport != null) {
                    parsingSupport.afterParsingHook(method, graph);
                }

                if (!SubstrateCompilationDirectives.isRuntimeCompiledMethod(method)) {
                    /*
                     * Runtime compiled methods should not have assertions. If they do, then they
                     * should be caught via the blocklist instead of being converted to bytecode
                     * exceptions.
                     */
                    new ImplicitAssertionsPhase().apply(graph, getProviders(method.getMultiMethodKey()));
                }
                UninterruptibleAnnotationChecker.checkAfterParsing(method, graph);

                optimizeAfterParsing(bb, method, graph);
                /*
                 * Do a complete Canonicalizer run once before graph encoding, to clean up any
                 * leftover uncanonicalized nodes.
                 */
                CanonicalizerPhase.create().apply(graph, getProviders(method.getMultiMethodKey()));
                /*
                 * To avoid keeping the whole Graal graphs alive in production use cases, we extract
                 * the necessary bits of information and store them in secondary storage maps.
                 */
                if (InliningUtilities.isTrivialMethod(graph)) {
                    analysisTrivialMethods.put(method, true);
                }

            }

            super.methodAfterParsingHook(bb, method, graph);
        }
    }

    protected void optimizeAfterParsing(BigBang bb, AnalysisMethod method, StructuredGraph graph) {
        if (PointstoOptions.EscapeAnalysisBeforeAnalysis.getValue(bb.getOptions())) {
            if (method.isOriginalMethod()) {
                /*
                 * Deoptimization Targets cannot have virtual objects in frame states.
                 *
                 * Also, more work is needed to enable PEA in Runtime Compiled Methods.
                 */
                new BoxNodeIdentityPhase().apply(graph, getProviders(method.getMultiMethodKey()));
                new PartialEscapePhase(false, false, CanonicalizerPhase.create(), null, options).apply(graph, getProviders(method.getMultiMethodKey()));
            }
        }
    }

    @Override
    public void methodBeforeTypeFlowCreationHook(BigBang bb, AnalysisMethod method, StructuredGraph graph) {
        if (method.isEntryPoint() && !Modifier.isStatic(graph.method().getModifiers())) {
            ValueNode receiver = graph.start().stateAfter().localAt(0);
            if (receiver != null && receiver.hasUsages()) {
                /*
                 * Entry point methods should be static. However, for unit testing we also use JUnit
                 * test methods as entry points, and they are by convention non-static. If the
                 * receiver was used, the execution would crash because the receiver is null (or
                 * undefined).
                 */
                bb.getUnsupportedFeatures().addMessage(method.format("%H.%n(%p)"), method, "Entry point is non-static and uses its receiver: " + method.format("%r %H.%n(%p)"));
            }
        }

        if (!NativeImageOptions.ReportUnsupportedElementsAtRuntime.getValue()) {
            for (Node n : graph.getNodes()) {
                if (n instanceof StaticDeoptimizingNode) {
                    StaticDeoptimizingNode node = (StaticDeoptimizingNode) n;

                    if (node.getReason() == DeoptimizationReason.JavaSubroutineMismatch) {
                        bb.getUnsupportedFeatures().addMessage(method.format("%H.%n(%p)"), method, "The bytecodes of the method " + method.format("%H.%n(%p)") +
                                        " contain a JSR/RET structure that could not be simplified by the compiler. The JSR bytecode is unused and deprecated since Java 6. Please recompile your application with a newer Java compiler." +
                                        System.lineSeparator() + "To diagnose the issue, you can add the option " +
                                        SubstrateOptionsParser.commandArgument(NativeImageOptions.ReportUnsupportedElementsAtRuntime, "+") +
                                        ". The error is then reported at run time when the JSR/RET is executed.");
                    }
                }
            }
        }

        if (analysisGraphs != null) {
            /*
             * Keeping the whole Graal graphs alive is reserved for special use cases, e.g.,
             * verification features.
             */
            analysisGraphs.put(method, graph);
        }
        /*
         * To avoid keeping the whole Graal graphs alive in production use cases, we extract the
         * necessary bits of information and store them in secondary storage maps.
         */
        if (!parseOnce) {
            if (InliningUtilities.isTrivialMethod(graph)) {
                analysisTrivialMethods.put(method, true);
            }
        }
        for (Node n : graph.getNodes()) {
            if (n instanceof StackValueNode) {
                containsStackValueNode.put(method, true);
            } else if (n instanceof ReachabilityRegistrationNode node) {
                bb.postTask(debug -> node.getRegistrationTask().ensureDone());
            }
            checkClassInitializerSideEffect(method, n);
        }
    }

    /**
     * Classes are only safe for automatic initialization if the class initializer has no side
     * effect on other classes and cannot be influenced by other classes. Otherwise there would be
     * observable side effects. For example, if a class initializer of class A writes a static field
     * B.f in class B, then someone could rely on reading the old value of B.f before triggering
     * initialization of A. Similarly, if a class initializer of class A reads a static field B.f,
     * then an early automatic initialization of class A could read a non-yet-set value of B.f.
     *
     * Note that it is not necessary to disallow instance field accesses: Objects allocated by the
     * class initializer itself can always be accessed because they are independent from other
     * initializers; all other objects must be loaded transitively from a static field.
     *
     * Currently, we are conservative and mark all methods that access static fields as unsafe for
     * automatic class initialization (unless the class initializer itself accesses a static field
     * of its own class - the common way of initializing static fields). The check could be relaxed
     * by tracking the call chain, i.e., allowing static field accesses when the root method of the
     * call chain is the class initializer. But this does not fit well into the current approach
     * where each method has a `Safety` flag.
     */
    private void checkClassInitializerSideEffect(AnalysisMethod method, Node n) {
        if (n instanceof AccessFieldNode) {
            ResolvedJavaField field = ((AccessFieldNode) n).field();
            if (field.isStatic() && (!method.isClassInitializer() || !field.getDeclaringClass().equals(method.getDeclaringClass()))) {
                classInitializerSideEffect.put(method, true);
            }
        } else if (n instanceof UnsafeAccessNode || n instanceof VMThreadLocalAccess) {
            /*
             * Unsafe memory access nodes are rare, so it does not pay off to check what kind of
             * field they are accessing.
             *
             * Methods that access a thread-local value cannot be initialized at image build time
             * because such values are not available yet.
             */
            classInitializerSideEffect.put(method, true);
        } else if (n instanceof EnsureClassInitializedNode) {
            ResolvedJavaType type = ((EnsureClassInitializedNode) n).constantTypeOrNull(getProviders(method.getMultiMethodKey()).getConstantReflection());
            if (type != null) {
                initializedClasses.computeIfAbsent(method, k -> new HashSet<>()).add((AnalysisType) type);
            } else {
                classInitializerSideEffect.put(method, true);
            }
        } else if (n instanceof AccessMonitorNode) {
            classInitializerSideEffect.put(method, true);
        }
    }

    public void keepAnalysisGraphs() {
        if (analysisGraphs == null) {
            analysisGraphs = new ConcurrentHashMap<>();
        }
    }

    public StructuredGraph getAnalysisGraph(AnalysisMethod method) {
        VMError.guarantee(analysisGraphs != null, "Keeping of analysis graphs must be enabled manually");
        return analysisGraphs.get(method);
    }

    public boolean containsStackValueNode(AnalysisMethod method) {
        return containsStackValueNode.containsKey(method);
    }

    public boolean hasClassInitializerSideEffect(AnalysisMethod method) {
        return classInitializerSideEffect.containsKey(method);
    }

    public Set<AnalysisType> getInitializedClasses(AnalysisMethod method) {
        Set<AnalysisType> result = initializedClasses.get(method);
        if (result != null) {
            return result;
        } else {
            return Collections.emptySet();
        }
    }

    public boolean isAnalysisTrivialMethod(AnalysisMethod method) {
        return analysisTrivialMethods.containsKey(method);
    }

    @Override
    public boolean hasNeverInlineDirective(ResolvedJavaMethod method) {
        if (AnnotationAccess.isAnnotationPresent(method, NeverInline.class)) {
            return true;
        }

        if (!SubstrateOptions.NeverInline.hasBeenSet()) {
            return false;
        }

        return SubstrateOptions.NeverInline.getValue().values().stream().anyMatch(re -> MethodFilter.parse(re).matches(method));
    }

    private InlineBeforeAnalysisPolicy inlineBeforeAnalysisPolicy(MultiMethod.MultiMethodKey multiMethodKey) {
        if (parsingSupport != null) {
            return parsingSupport.inlineBeforeAnalysisPolicy(multiMethodKey, inlineBeforeAnalysisPolicy);
        }
        return inlineBeforeAnalysisPolicy;
    }

    @Override
    public InlineBeforeAnalysisGraphDecoder createInlineBeforeAnalysisGraphDecoder(BigBang bb, AnalysisMethod method, StructuredGraph resultGraph) {
        return new InlineBeforeAnalysisGraphDecoderImpl(bb, inlineBeforeAnalysisPolicy(method.getMultiMethodKey()), resultGraph, bb.getProviders(method));
    }

    public static class Options {
        @Option(help = "Enable the behavior of old GraalVM versions. When enabled, interfaces not available for the current platform are filtered.")//
        public static final HostedOptionKey<Boolean> PlatformInterfaceCompatibilityMode = new HostedOptionKey<>(false);
    }

    @Override
    public boolean skipInterface(AnalysisUniverse universe, ResolvedJavaType interfaceType, ResolvedJavaType implementingType) {
        if (!platformSupported(interfaceType)) {
            String message = "The interface " + interfaceType.toJavaName(true) + " is not available in the current platform, but used by " + implementingType.toJavaName(true) + ". " +
                            "GraalVM before version 21.2 ignored such interfaces, but this was an oversight.";

            String commandArgument = SubstrateOptionsParser.commandArgument(Options.PlatformInterfaceCompatibilityMode, "+");
            if (Options.PlatformInterfaceCompatibilityMode.getValue()) {
                LogUtils.warning("%s The interface is filtered because the compatibility option %s is used. This option will be removed in a future GraalVM version.", message, commandArgument);
                return true;
            } else {
                throw new UnsupportedFeatureException(
                                message + " The old behavior can be temporarily restored using the option " + commandArgument + ". This option will be removed in a future GraalVM version.");
            }
        }
        return false;
    }

    @Override
    public boolean platformSupported(AnnotatedElement element) {
        if (element instanceof ResolvedJavaType) {
            ResolvedJavaType javaType = (ResolvedJavaType) element;
            Package p = OriginalClassProvider.getJavaClass(javaType).getPackage();
            if (p != null && !platformSupported(p)) {
                return false;
            }
            ResolvedJavaType enclosingType;
            try {
                enclosingType = javaType.getEnclosingType();
            } catch (LinkageError e) {
                enclosingType = null;
            }
            if (enclosingType != null && !platformSupported(enclosingType)) {
                return false;
            }
        }
        if (element instanceof Class) {
            Class<?> clazz = (Class<?>) element;
            Package p = clazz.getPackage();
            if (p != null && !platformSupported(p)) {
                return false;
            }
            Class<?> enclosingClass;
            try {
                enclosingClass = clazz.getEnclosingClass();
            } catch (LinkageError e) {
                enclosingClass = null;
            }
            if (enclosingClass != null && !platformSupported(enclosingClass)) {
                return false;
            }
        }

        Platforms platformsAnnotation = AnnotationAccess.getAnnotation(element, Platforms.class);
        if (platform == null || platformsAnnotation == null) {
            return true;
        }
        for (Class<? extends Platform> platformGroup : platformsAnnotation.value()) {
            if (platformGroup.isInstance(platform)) {
                return true;
            }
        }
        return false;
    }

    private final List<BiPredicate<AnalysisMethod, AnalysisMethod>> neverInlineTrivialHandlers = new CopyOnWriteArrayList<>();

    public void registerNeverInlineTrivialHandler(BiPredicate<AnalysisMethod, AnalysisMethod> handler) {
        neverInlineTrivialHandlers.add(handler);
    }

    public boolean neverInlineTrivial(AnalysisMethod caller, AnalysisMethod callee) {
        if (!callee.canBeInlined() || AnnotationAccess.isAnnotationPresent(callee, NeverInlineTrivial.class)) {
            return true;
        }
        for (var handler : neverInlineTrivialHandlers) {
            if (handler.test(caller, callee)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Comparator<? super ResolvedJavaType> getTypeComparator() {
        return (Comparator<ResolvedJavaType>) (o1, o2) -> {
            return HostedUniverse.TYPE_COMPARATOR.compare((HostedType) o1, (HostedType) o2);
        };
    }

    /**
     * According to the Java VM specification, final instance fields are only allowed to be written
     * in a constructor. But some compilers violate the spec, notably the Scala compiler. This means
     * final fields of image heap objects can be modified at image run time, and constant folding
     * such fields at image build time would fold in the wrong value. As a workaround, we record
     * which final instance fields are written outside of a constructor, and disable constant
     * folding for those fields.
     *
     * Note that there can be races: A constant folding can happen during bytecode parsing before
     * the store was recorded. Currently, we do not encounter that problem because constant folding
     * only happens after static analysis. But there would not be a good solution other than
     * aborting the image build, because a constant folding cannot be reversed.
     */
    public void recordFieldStore(ResolvedJavaField field, ResolvedJavaMethod method) {
        if (!field.isStatic() && field.isFinal() && (!method.isConstructor() || !field.getDeclaringClass().equals(method.getDeclaringClass()))) {
            AnalysisField aField = field instanceof HostedField ? ((HostedField) field).getWrapped() : (AnalysisField) field;
            finalFieldsInitializedOutsideOfConstructor.add(aField);
        }
    }

    public boolean allowConstantFolding(ResolvedJavaField field) {
        AnalysisField aField = field instanceof HostedField ? ((HostedField) field).getWrapped() : (AnalysisField) field;
        return !finalFieldsInitializedOutsideOfConstructor.contains(aField);
    }

    @Override
    public Object parseGraph(BigBang bb, DebugContext debug, AnalysisMethod method) {
        if (parsingSupport != null) {
            return parsingSupport.parseGraph(bb, debug, method);
        } else {
            return super.parseGraph(bb, debug, method);
        }
    }

    @Override
    public boolean validateGraph(PointsToAnalysis bb, StructuredGraph graph) {
        if (parsingSupport != null) {
            return parsingSupport.validateGraph(bb, graph);
        } else {
            return super.validateGraph(bb, graph);
        }
    }

    @Override
    public StructuredGraph.AllowAssumptions allowAssumptions(AnalysisMethod method) {
        if (parsingSupport != null) {
            if (parsingSupport.allowAssumptions(method)) {
                return StructuredGraph.AllowAssumptions.YES;
            }
        }
        return super.allowAssumptions(method);
    }

    @Override
    public boolean recordInlinedMethods(AnalysisMethod method) {
        if (parsingSupport != null) {
            return parsingSupport.recordInlinedMethods(method);
        }
        return super.recordInlinedMethods(method);
    }

    @Override
    public HostedProviders getProviders(MultiMethod.MultiMethodKey key) {
        if (parsingSupport != null) {
            HostedProviders providers = parsingSupport.getHostedProviders(key);
            if (providers != null) {
                return providers;
            }
        }
        return super.getProviders(key);
    }

    @Override
    public MultiMethodAnalysisPolicy getMultiMethodAnalysisPolicy() {
        return multiMethodAnalysisPolicy;
    }

    @Override
    public boolean ignoreInstanceOfTypeDisallowed() {
        if (ClassInitializationOptions.AllowDeprecatedInitializeAllClassesAtBuildTime.getValue()) {
            /*
             * Compatibility mode for Helidon MP: It initializes all classes at build time, and
             * relies on the side effect of a class initializer of a class that is only used in an
             * instanceof. See https://github.com/oracle/graal/pull/5224#issuecomment-1279586997 for
             * details.
             */
            return true;
        }
        return super.ignoreInstanceOfTypeDisallowed();
    }

    @Override
    public Function<AnalysisType, ResolvedJavaType> getStrengthenGraphsToTargetFunction(MultiMethod.MultiMethodKey key) {
        if (parsingSupport != null) {
            var result = parsingSupport.getStrengthenGraphsToTargetFunction(key);
            if (result != null) {
                return result;
            }
        }
        return super.getStrengthenGraphsToTargetFunction(key);
    }

    @Override
    public boolean allowConstantFolding(AnalysisMethod method) {
        /*
         * Currently constant folding is only enabled for original methods which do not deoptimize.
         * More work is needed to support it within deoptimization targets and runtime-compiled
         * methods.
         */
        return method.isOriginalMethod() && !SubstrateCompilationDirectives.singleton().isRegisteredForDeoptTesting(method);
    }

    @Override
    public FieldValueComputer createFieldValueComputer(AnalysisField field) {
        UnknownObjectField unknownObjectField = field.getAnnotation(UnknownObjectField.class);
        if (unknownObjectField != null) {
            return createObjectFieldValueComputer(field, unknownObjectField);
        }
        UnknownPrimitiveField unknownPrimitiveField = field.getAnnotation(UnknownPrimitiveField.class);
        if (unknownPrimitiveField != null) {
            return createPrimitiveFieldValueComputer(field, unknownPrimitiveField);
        }
        return null;
    }

    private static FieldValueComputer createObjectFieldValueComputer(AnalysisField field, UnknownObjectField unknownValueField) {
        return new FieldValueComputer() {
            final BooleanSupplier availability = ReflectionUtil.newInstance(unknownValueField.availability());

            @Override
            public boolean isAvailable() {
                return availability.getAsBoolean();
            }

            @Override
            public Class<?>[] types() {
                return extractAnnotationTypes(field, unknownValueField.types(), unknownValueField.fullyQualifiedTypes());
            }

            @Override
            public boolean canBeNull() {
                return unknownValueField.canBeNull();
            }
        };
    }

    private static FieldValueComputer createPrimitiveFieldValueComputer(AnalysisField field, UnknownPrimitiveField unknownValueField) {
        return new FieldValueComputer() {
            final BooleanSupplier availability = ReflectionUtil.newInstance(unknownValueField.availability());

            @Override
            public boolean isAvailable() {
                return availability.getAsBoolean();
            }

            @Override
            public Class<?>[] types() {
                return new Class<?>[]{field.getType().getJavaClass()};
            }
        };
    }

    private static Class<?>[] extractAnnotationTypes(AnalysisField field, Class<?>[] types, String[] fullyQualifiedTypes) {
        List<Class<?>> annotationTypes = new ArrayList<>(Arrays.asList(types));
        for (String annotationTypeName : fullyQualifiedTypes) {
            try {
                Class<?> annotationType = Class.forName(annotationTypeName);
                annotationTypes.add(annotationType);
            } catch (ClassNotFoundException e) {
                throw shouldNotReachHere("Specified computed value type not found " + annotationTypeName);
            }
        }

        if (annotationTypes.isEmpty()) {
            /* If no types are specified fallback to the field declared type. */
            AnalysisType fieldType = field.getType();
            VMError.guarantee(CustomTypeFieldHandler.isConcreteType(fieldType), "Illegal use of @UnknownObjectField annotation on field %s. " +
                            "The field type must be concrete or the annotation must declare a concrete type.", field);
            annotationTypes.add(fieldType.getJavaClass());
        }
        return annotationTypes.toArray(new Class<?>[0]);
    }

    public SimulateClassInitializerSupport createSimulateClassInitializerSupport(AnalysisMetaAccess aMetaAccess) {
        return new SimulateClassInitializerSupport(aMetaAccess, this);
    }
}
