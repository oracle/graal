/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.java.GraphBuilderPhase.Instance;
import org.graalvm.compiler.nodes.StaticDeoptimizingNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.UnsafeAccessNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.java.AccessFieldNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.c.function.RelocatedPointer;
import org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.UnknownClass;
import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.annotate.UnknownPrimitiveField;
import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallLinkage;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.stackvalue.StackValueNode;
import com.oracle.svm.core.heap.Target_java_lang_ref_Reference;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.HubType;
import com.oracle.svm.core.hub.ReferenceType;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.HostedStringDeduplication;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.GraalAccess;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.code.InliningUtilities;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.phases.AnalysisGraphBuilderPhase;
import com.oracle.svm.hosted.substitute.UnsafeAutomaticSubstitutionProcessor;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class SVMHost implements HostVM {
    private final ConcurrentHashMap<AnalysisType, DynamicHub> typeToHub = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<DynamicHub, AnalysisType> hubToType = new ConcurrentHashMap<>();

    private final Map<String, EnumSet<AnalysisType.UsageKind>> forbiddenTypes;

    private final OptionValues options;
    private final ClassLoader classLoader;
    private final ClassInitializationSupport classInitializationSupport;
    private final HostedStringDeduplication stringTable;
    private final UnsafeAutomaticSubstitutionProcessor automaticSubstitutions;
    private final List<BiConsumer<DuringAnalysisAccess, Class<?>>> classReachabilityListeners;

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

    private static final Method isHiddenMethod = JavaVersionUtil.JAVA_SPEC >= 15 ? ReflectionUtil.lookupMethod(Class.class, "isHidden") : null;
    private static final Method getNestHostMethod = JavaVersionUtil.JAVA_SPEC >= 11 ? ReflectionUtil.lookupMethod(Class.class, "getNestHost") : null;

    public SVMHost(OptionValues options, ClassLoader classLoader, ClassInitializationSupport classInitializationSupport, UnsafeAutomaticSubstitutionProcessor automaticSubstitutions) {
        this.options = options;
        this.classLoader = classLoader;
        this.classInitializationSupport = classInitializationSupport;
        this.stringTable = HostedStringDeduplication.singleton();
        this.classReachabilityListeners = new ArrayList<>();
        this.forbiddenTypes = setupForbiddenTypes(options);
        this.automaticSubstitutions = automaticSubstitutions;
    }

    private static Map<String, EnumSet<AnalysisType.UsageKind>> setupForbiddenTypes(OptionValues options) {
        String[] forbiddenTypesOptionValues = SubstrateOptions.ReportAnalysisForbiddenType.getValue(options);
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
    public OptionValues options() {
        return options;
    }

    @Override
    public Instance createGraphBuilderPhase(HostedProviders providers, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext) {
        return new AnalysisGraphBuilderPhase(providers, graphBuilderConfig, optimisticOpts, initialIntrinsicContext, providers.getWordTypes());
    }

    @Override
    public String inspectServerContentPath() {
        return PointstoOptions.InspectServerContentPath.getValue(options);
    }

    @Override
    public void warn(String message) {
        System.err.println("warning: " + message);
    }

    @Override
    public String getImageName() {
        return SubstrateOptions.Name.getValue(options);
    }

    @Override
    public boolean isRelocatedPointer(Object originalObject) {
        return originalObject instanceof RelocatedPointer;
    }

    @Override
    public void clearInThread() {
        Thread.currentThread().setContextClassLoader(SVMHost.class.getClassLoader());
        ImageSingletonsSupportImpl.HostedManagement.clearInThread();
    }

    @Override
    public void installInThread(Object vmConfig) {
        Thread.currentThread().setContextClassLoader(classLoader);
        ImageSingletonsSupportImpl.HostedManagement.installInThread((ImageSingletonsSupportImpl.HostedManagement) vmConfig);
    }

    @Override
    public Object getConfiguration() {
        return ImageSingletonsSupportImpl.HostedManagement.getAndAssertExists();
    }

    @Override
    public void registerType(AnalysisType analysisType) {
        classInitializationSupport.maybeInitializeHosted(analysisType);

        DynamicHub hub = createHub(analysisType);
        Object existing = typeToHub.put(analysisType, hub);
        assert existing == null;
        existing = hubToType.put(hub, analysisType);
        assert existing == null;

        /* Compute the automatic substitutions. */
        automaticSubstitutions.computeSubstitutions(this, GraalAccess.getOriginalProviders().getMetaAccess().lookupJavaType(analysisType.getJavaClass()), options);
    }

    @Override
    public boolean isInitialized(AnalysisType type) {
        boolean shouldInitializeAtRuntime = classInitializationSupport.shouldInitializeAtRuntime(type);
        assert shouldInitializeAtRuntime || type.getWrapped().isInitialized() : "Types that are not marked for runtime initializations must have been initialized: " + type;

        return !shouldInitializeAtRuntime;
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
         * There is no need to have file names as interned strings. So we perform our own
         * de-duplication.
         */
        String sourceFileName = stringTable.deduplicate(type.getSourceFileName(), true);

        /*
         * JDK 15 added support for Hidden Classes. Record if this javaClass is hidden.
         */
        boolean isHidden = false;
        if (JavaVersionUtil.JAVA_SPEC >= 15) {
            try {
                isHidden = (boolean) isHiddenMethod.invoke(javaClass);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw VMError.shouldNotReachHere(e);
            }
        }

        Class<?> nestHost = null;
        if (JavaVersionUtil.JAVA_SPEC >= 11) {
            try {
                nestHost = (Class<?>) getNestHostMethod.invoke(javaClass);
            } catch (ReflectiveOperationException ex) {
                throw VMError.shouldNotReachHere(ex);
            }
        }

        final DynamicHub dynamicHub = new DynamicHub(className, computeHubType(type), computeReferenceType(type), type.isLocal(), isAnonymousClass(javaClass), superHub, componentHub, sourceFileName,
                        modifiers, hubClassLoader, isHidden, nestHost);
        if (JavaVersionUtil.JAVA_SPEC > 8) {
            ModuleAccess.extractAndSetModule(dynamicHub, javaClass);
        }
        return dynamicHub;
    }

    /**
     * @return boolean if class is available or NoClassDefFoundError if class' parents are not on
     *         the classpath or InternalError if the class is invalid.
     */
    private static Object isAnonymousClass(Class<?> javaClass) {
        try {
            return javaClass.isAnonymousClass();
        } catch (InternalError e) {
            return e;
        } catch (NoClassDefFoundError e) {
            if (NativeImageOptions.AllowIncompleteClasspath.getValue()) {
                return e;
            } else {
                String message = "Discovered a type for which isAnonymousClass can't be called: " + javaClass.getTypeName() +
                                ". To avoid this issue at build time use the " +
                                SubstrateOptionsParser.commandArgument(NativeImageOptions.AllowIncompleteClasspath, "+") +
                                " option. The NoClassDefFoundError will then be reported at run time when this method is called for the first time.";
                throw new UnsupportedFeatureException(message);
            }
        }
    }

    public static boolean isUnknownClass(ResolvedJavaType resolvedJavaType) {
        return resolvedJavaType.getAnnotation(UnknownClass.class) != null;
    }

    public static boolean isUnknownObjectField(ResolvedJavaField resolvedJavaField) {
        return resolvedJavaField.getAnnotation(UnknownObjectField.class) != null;
    }

    public static boolean isUnknownPrimitiveField(AnalysisField field) {
        return field.getAnnotation(UnknownPrimitiveField.class) != null;
    }

    public void registerClassReachabilityListener(BiConsumer<DuringAnalysisAccess, Class<?>> listener) {
        classReachabilityListeners.add(listener);
    }

    void notifyClassReachabilityListener(AnalysisUniverse universe, DuringAnalysisAccess access) {
        for (AnalysisType type : universe.getTypes()) {
            if ((type.isInTypeCheck() || type.isInstantiated()) && !type.getReachabilityListenerNotified()) {
                type.setReachabilityListenerNotified(true);

                for (BiConsumer<DuringAnalysisAccess, Class<?>> listener : classReachabilityListeners) {
                    listener.accept(access, type.getJavaClass());
                }
            }
        }
    }

    public ClassInitializationSupport getClassInitializationSupport() {
        return classInitializationSupport;
    }

    public UnsafeAutomaticSubstitutionProcessor getAutomaticSubstitutionProcessor() {
        return automaticSubstitutions;
    }

    private static HubType computeHubType(AnalysisType type) {
        if (type.isArray()) {
            if (type.getComponentType().isPrimitive() || type.getComponentType().isWordType()) {
                return HubType.TypeArray;
            } else {
                return HubType.ObjectArray;
            }
        } else if (type.isInstanceClass()) {
            if (Reference.class.isAssignableFrom(type.getJavaClass())) {
                return HubType.InstanceReference;
            }
            assert !Target_java_lang_ref_Reference.class.isAssignableFrom(type.getJavaClass()) : "should not see substitution type here";
            return HubType.Instance;
        } else {
            return HubType.Other;
        }
    }

    private static ReferenceType computeReferenceType(AnalysisType type) {
        Class<?> clazz = type.getJavaClass();
        if (PhantomReference.class.isAssignableFrom(clazz)) {
            return ReferenceType.Phantom;
        } else if (WeakReference.class.isAssignableFrom(clazz)) {
            return ReferenceType.Weak;
        } else if (SoftReference.class.isAssignableFrom(clazz)) {
            return ReferenceType.Soft;
        } else if (Reference.class.isAssignableFrom(clazz)) {
            return ReferenceType.Other;
        }
        return ReferenceType.None;
    }

    @Override
    public void checkType(ResolvedJavaType type, AnalysisUniverse universe) {
        Class<?> originalClass = OriginalClassProvider.getJavaClass(universe.getOriginalSnippetReflection(), type);
        ClassLoader originalClassLoader = originalClass.getClassLoader();
        if (originalClassLoader instanceof NativeImageClassLoader && !originalClassLoader.equals(classLoader)) {
            String message = "Class " + originalClass.getName() + " was loaded by " + originalClassLoader + " and not by the current image class loader " + classLoader + ". ";
            message += "This usually means that some objects from a previous build leaked in the current build. ";
            message += "This can happen when using the image build server. ";
            message += "To fix the issue you must reset all static state from the bootclasspath and application classpath that points to the application objects. ";
            message += "If the offending code is in JDK code please file a bug with GraalVM. ";
            message += "As an workaround you can disable the image build server by adding " + SubstrateOptions.NO_SERVER + " to the command line. ";

            throw new UnsupportedFeatureException(message);
        }
    }

    @Override
    public void checkMethod(BigBang bb, AnalysisMethod method, StructuredGraph graph) {
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
         * necessary bits of information here and store them in secondary storage maps.
         */
        if (InliningUtilities.isTrivialMethod(graph)) {
            analysisTrivialMethods.put(method, true);
        }
        for (Node n : graph.getNodes()) {
            if (n instanceof StackValueNode) {
                containsStackValueNode.put(method, true);
            }
            checkClassInitializerSideEffect(bb, method, n);
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
    private void checkClassInitializerSideEffect(BigBang bb, AnalysisMethod method, Node n) {
        if (n instanceof AccessFieldNode) {
            ResolvedJavaField field = ((AccessFieldNode) n).field();
            if (field.isStatic() && (!method.isClassInitializer() || !field.getDeclaringClass().equals(method.getDeclaringClass()))) {
                classInitializerSideEffect.put(method, true);
            }
        } else if (n instanceof UnsafeAccessNode) {
            /*
             * Unsafe memory access nodes are rare, so it does not pay off to check what kind of
             * field they are accessing.
             */
            classInitializerSideEffect.put(method, true);
        } else if (n instanceof EnsureClassInitializedNode) {
            Constant constantHub = ((EnsureClassInitializedNode) n).getHub().asConstant();
            if (constantHub != null) {
                AnalysisType type = (AnalysisType) bb.getProviders().getConstantReflection().asJavaType(constantHub);
                initializedClasses.computeIfAbsent(method, k -> new HashSet<>()).add(type);
            } else {
                classInitializerSideEffect.put(method, true);
            }
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
}
