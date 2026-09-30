/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.classinitialization;

import static com.oracle.svm.core.SubstrateOptions.TraceObjectInstantiation;
import static com.oracle.svm.core.configure.ConfigurationFiles.Options.TrackTypeReachedOnInterfaces;
import static com.oracle.svm.core.configure.ConfigurationFiles.Options.TreatAllUserSpaceTypesAsTrackedForTypeReached;

import java.lang.reflect.Proxy;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.clinit.ClassInitializationTracking;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.BaseLayerType;
import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.svm.core.AssertionsSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.LinkAtBuildTimeSupport;
import com.oracle.svm.hosted.reflect.proxy.ProxyRenamingSubstitutionProcessor;
import com.oracle.svm.shared.option.AccumulatingLocatableMultiOptionValue;
import com.oracle.svm.shared.option.SubstrateOptionsParser;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.PartiallyLayerAware;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.LogUtils;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.GuestAccess;
import com.oracle.svm.util.HostedModuleSupport;
import com.oracle.svm.util.JVMCIReflectionUtil;
import com.oracle.svm.util.JVMCIRuntimeClassInitializationSupport;
import com.oracle.svm.util.OriginalClassProvider;

import jdk.graal.compiler.java.LambdaUtils;
import jdk.graal.compiler.vmaccess.InvocationException;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * The core class for deciding whether a class should be initialized during image building or class
 * initialization should be delayed to runtime.
 * <p>
 * The initialization kind for all classes is encoded in the two registries:
 * {@link #classInitializationConfiguration}, the user-configured initialization state, and
 * {@link #classInitKinds}, the actual computed initialization state.
 * <p>
 * If for example the configured initialization kind, as registered in
 * {@link #classInitializationConfiguration}, is {@link InitKind#BUILD_TIME} but invoking
 * {@link #ensureClassInitialized(ResolvedJavaType, boolean)} results in an error, e.g., a
 * {@link NoClassDefFoundError}, then the actual initialization kind registered in
 * {@link #classInitKinds} may be {@link InitKind#RUN_TIME} depending on the error resolution policy
 * dictated by {@link LinkAtBuildTimeSupport#linkAtBuildTime(ResolvedJavaType)}.
 * <p>
 * Classes with a simulated class initializer are neither registered as initialized at
 * {@link InitKind#RUN_TIME} nor {@link InitKind#BUILD_TIME}. Instead
 * {@link SimulateClassInitializerSupport} queries their initialization state to decide if
 * simulation should be tried. If a class has a computed {@link InitKind#BUILD_TIME} initialization
 * kind, i.e., its {@link AnalysisType#isInitialized()} returns true, simulation is skipped since
 * the type is already considered as starting out as initialized at image run time (see
 * {@link SimulateClassInitializerSupport#trySimulateClassInitializer(BigBang, AnalysisType)}). If a
 * class was explicitly configured as {@link InitKind#RUN_TIME} initialized this will prevent it
 * from being simulated.
 * <p>
 * There are some similarities and differences between simulated and build-time initialized classes.
 * At image execution time they both start out as initialized: there are no run-time class
 * initialization checks and the class initializer itself is not even present, it was not AOT
 * compiled. However, for a simulated class its initialization status in the guest context used by
 * the image generator does not matter; it may or may not have been initialized. Whereas, a
 * build-time initialized class is by definition initialized in the guest context. Consequently,
 * the static fields of a simulated class reference image heap values computed by the class
 * initializer simulation, but they do not correspond to values read from initialized guest static
 * fields. In contrast, static fields of a build-time initialized class reference image heap values
 * copied from the corresponding fields in the guest context.
 */
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = PartiallyLayerAware.class)
public class ClassInitializationSupport implements JVMCIRuntimeClassInitializationSupport {

    private static final String GUEST_CLASS_INITIALIZATION_UTIL_CLASS_NAME = "com.oracle.svm.guest.hosted.classinitialization.ClassInitializationUtil";

    /**
     * Setup for class initialization: configured through features and command line input. It
     * represents the user desires about class initialization and helps in finding configuration
     * issues.
     */
    final ClassInitializationConfiguration classInitializationConfiguration = new ClassInitializationConfiguration();

    /**
     * The initialization kind for all classes seen during image building. Classes are inserted into
     * this map the first time information was queried and used during image building. This is the
     * ground truth about what got initialized during image building.
     */
    final ConcurrentMap<ResolvedJavaType, InitKind> classInitKinds = new ConcurrentHashMap<>();

    /**
     * Store classes that were configured with --initialize-at-build-time but for which
     * initialization failed and were registered as initialized at run time either because
     * initialization errors during registration were allowed or they were not configured with
     * --link-at-build-time.
     */
    final Set<ResolvedJavaType> requestedAtBuildTimeButFailedInit = ConcurrentHashMap.newKeySet();

    /**
     * We need always-reached types to avoid users injecting class initialization checks in our VM
     * implementation and hot paths and to prevent users from making the whole class hierarchy
     * require initialization nodes.
     */
    private final Set<ResolvedJavaType> alwaysReachedTypes;

    final Set<ResolvedJavaType> typesRequiringReachability = ConcurrentHashMap.newKeySet();

    boolean configurationSealed;

    /**
     * Type-reached tracking is an input to class-initializer simulation. Adding a tracking
     * requirement can make a class initializer that was previously considered simulatable no
     * longer simulatable. Since published simulation results and decoded graphs are not
     * invalidated, this input must not change once analysis has started.
     */
    private boolean typeReachedTrackingSealed;

    final ImageClassLoader loader;

    /* Guest methods and exception types used by builder-side class-initialization policy. */
    private final ResolvedJavaMethod guestEnsureClassInitialized;
    private final ResolvedJavaMethod guestFormatExceptionInInitializerError;
    private final ResolvedJavaType guestNoClassDefFoundError;
    private final ResolvedJavaType guestExceptionInInitializerError;
    private final ResolvedJavaType guestThrowable;

    public static ClassInitializationSupport singleton() {
        return (ClassInitializationSupport) ImageSingletons.lookup(JVMCIRuntimeClassInitializationSupport.class);
    }

    public ClassInitializationSupport(ImageClassLoader loader) {
        this.loader = loader;
        GuestAccess access = GuestAccess.get();
        this.alwaysReachedTypes = createAlwaysReachedTypes(access);

        /* Resolve the class-initialization helpers in the active guest context. */
        ResolvedJavaType guestUtilType = access.lookupType(GUEST_CLASS_INITIALIZATION_UTIL_CLASS_NAME);
        this.guestEnsureClassInitialized = access.lookupMethod(guestUtilType, "ensureClassInitialized", Class.class, ClassLoader.class);
        this.guestFormatExceptionInInitializerError = access.lookupMethod(guestUtilType, "formatExceptionInInitializerError", ExceptionInInitializerError.class);
        this.guestNoClassDefFoundError = access.elements.java_lang_NoClassDefFoundError;
        this.guestExceptionInInitializerError = access.elements.java_lang_ExceptionInInitializerError;
        this.guestThrowable = access.elements.java_lang_Throwable;
    }

    /** Creates the guest types that are always reachable in every image. */
    private static Set<ResolvedJavaType> createAlwaysReachedTypes(GuestAccess access) {
        var elements = access.elements;
        return Set.of(
                        elements.java_lang_Object, elements.java_lang_Class, elements.java_lang_String,
                        elements.java_lang_Character, elements.java_lang_Byte, elements.java_lang_Short, elements.java_lang_Integer, elements.java_lang_Long,
                        elements.java_lang_Float, elements.java_lang_Double, elements.java_lang_Boolean,
                        elements.java_lang_Enum, elements.java_lang_Cloneable, elements.java_util_Formattable, elements.java_lang_Throwable, elements.java_io_Serializable,
                        elements.java_lang_AutoCloseable, elements.java_lang_Runnable, elements.java_lang_Iterable, elements.java_util_Collection, elements.java_util_Set,
                        elements.java_util_List, elements.java_util_Map, elements.java_lang_System, elements.java_lang_Thread, elements.java_lang_ref_Reference,
                        elements.java_lang_ref_SoftReference, elements.java_lang_StackWalker, elements.java_lang_ref_ReferenceQueue);
    }

    /// Gets the assertion status for the declaring class of `field` if `field` is the synthetic field
    /// injected by javac to implement assertions (i.e. `static final boolean $assertionsDisabled`)
    /// and the assertion status is a build-time constant (i.e., cannot change at runtime).
    ///
    /// @return the fixed assertion status for the declaring class of `field` (`true` if enabled, `false` if disabled)
    /// or `null` if it is not build-time constant
    public static Boolean foldedAssertionStatus(ResolvedJavaField field) {
        if (field.isFinal() && field.isStatic() && field.isSynthetic() && field.getName().startsWith(AssertionsSupport.SYNTHETIC_ASSERTIONS_DISABLED_FIELD_NAME)) {
            if (singleton().shouldFoldAssertionStatus(field.getDeclaringClass())) {
                return AssertionsSupport.singleton().desiredAssertionStatus(field.getDeclaringClass());
            }
        }
        return null;
    }

    /**
     * Seal the configuration, blocking if another thread is trying to seal the configuration or an
     * unsealed-configuration window is currently open in another thread.
     * </p>
     * If -H:+PrintClassInitialization is set, dumps all class-initialization config into a file.
     */
    public synchronized void sealConfiguration() {
        setConfigurationSealed(true);
        typeReachedTrackingSealed = true;
        if (ClassInitializationOptions.PrintClassInitialization.getValue()) {
            List<ClassOrPackageConfig> allConfigs = classInitializationConfiguration.allConfigs();
            allConfigs.sort(Comparator.comparing(ClassOrPackageConfig::getName));
            ReportUtils.report("class initialization configuration", SubstrateOptions.reportsPath(), "class_initialization_configuration", "csv", writer -> {
                writer.println("Class or Package Name, Initialization Kind, Reasons");
                for (ClassOrPackageConfig config : allConfigs) {
                    writer.append(config.getName()).append(", ").append(config.getKind().toString()).append(", ").append(String.join(" and ", config.getReasons())).append(System.lineSeparator());
                }
            });
        }
    }

    /**
     * Run the action in an unsealed-configuration window, blocking if another thread is trying to
     * seal the configuration or an unsealed-configuration window is currently open in another
     * thread. The window is reentrant, i.e., it will not block the thread that opened the window
     * from trying to reenter the window. Note that if the configuration was not sealed when the
     * window was opened this will not affect the seal status.
     */
    public synchronized void withUnsealedConfiguration(Runnable action) {
        var previouslySealed = configurationSealed;
        setConfigurationSealed(false);
        try {
            action.run();
        } finally {
            setConfigurationSealed(previouslySealed);
        }
    }

    private void setConfigurationSealed(boolean sealed) {
        configurationSealed = sealed;
    }

    /** Returns the canonical guest-owned type represented by {@code type}. */
    private static ResolvedJavaType normalizeType(ResolvedJavaType type) {
        VMError.guarantee(!(type instanceof BaseLayerType) && !(type instanceof AnalysisType analysisType && analysisType.getWrapped() instanceof BaseLayerType),
                        "Class initialization policy cannot use incomplete base-layer type %s", type);
        ResolvedJavaType originalType = OriginalClassProvider.getOriginalType(type);
        VMError.guarantee(GuestAccess.get().owns(originalType), "Class initialization policy requires a guest-owned type: %s", type);
        return originalType;
    }

    /** Returns the configured init kind for {@code type}. */
    InitKind specifiedInitKindFor(ResolvedJavaType type) {
        return specifiedInitKindFor0(normalizeType(type));
    }

    /** Returns configured policy for an already canonical guest type. */
    private InitKind specifiedInitKindFor0(ResolvedJavaType type) {
        /* Class-initialization configuration is keyed by the equivalent of Class#getTypeName(). */
        return classInitializationConfiguration.lookupKind(JVMCIReflectionUtil.getTypeName(type)).getLeft();
    }

    /**
     * Returns the computed init kind for {@code type}, which can differ from the configured init
     * kind returned by {@link #specifiedInitKindFor(ResolvedJavaType)}.
     */
    InitKind computedInitKindFor(ResolvedJavaType type) {
        return classInitKinds.get(normalizeType(type));
    }

    public boolean isFailedInitialization(ResolvedJavaType type) {
        ResolvedJavaType guestType = normalizeType(type);
        boolean failedInit = requestedAtBuildTimeButFailedInit.contains(guestType);
        VMError.guarantee(!failedInit || specifiedInitKindFor0(guestType) == InitKind.BUILD_TIME && classInitKinds.get(guestType) == InitKind.RUN_TIME);
        return failedInit;
    }

    Boolean isStrictlyDefined(ResolvedJavaType type) {
        return classInitializationConfiguration.lookupKind(JVMCIReflectionUtil.getTypeName(normalizeType(type))).getRight();
    }

    /** Returns all types of a single {@link InitKind}. */
    Set<ResolvedJavaType> classesWithKind(InitKind kind) {
        return classInitKinds.entrySet().stream().filter(e -> e.getValue() == kind).map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    /**
     * Returns true if the provided type is initialized at image build time.
     * <p>
     * If the return value is true, then the class is also guaranteed to be initialized already.
     * This means that calling this method might trigger class initialization, i.e., execute
     * arbitrary user code.
     */
    public boolean maybeInitializeAtBuildTime(ResolvedJavaType type) {
        if (type instanceof AnalysisType analysisType && analysisType.getWrapped() instanceof BaseLayerType baseLayerType) {
            return baseLayerType.isInitialized();
        }
        return computeInitKindAndMaybeInitializeClass(normalizeType(type)) == InitKind.BUILD_TIME;
    }

    /// Returns whether the assertion status for `type` can be fixed during image building.
    public boolean shouldFoldAssertionStatus(ResolvedJavaType type) {
        /*
         * A class whose initializer is not folded retains the JDK assertion-status lookup, which
         * prevents class-initializer simulation. Consequently, this predicate agrees with the
         * runtime ClassInitializationInfo build-time flag used by DynamicHub.
         */
        return !SubstrateOptions.StrictRuntimeJavaOptions.getValue() || maybeInitializeAtBuildTime(type);
    }

    /**
     * Returns {@code true} if the provided type is registered to be initialized at build time.
     * <p>
     * In contrast to {@link #maybeInitializeAtBuildTime}, this method <b>does not</b> perform the
     * class initialization as a side effect, which makes it useful in cases where one wants to only
     * check the configuration without performing the actual initialization.
     */
    public boolean shouldInitializeAtBuildTime(ResolvedJavaType type) {
        return specifiedInitKindFor(type) == InitKind.BUILD_TIME;
    }

    /**
     * Ensure class is initialized. Report class initialization errors in a user-friendly way if
     * class initialization fails.
     */
    InitKind ensureClassInitialized(ResolvedJavaType type, boolean allowErrors) {
        GuestAccess access = GuestAccess.get();
        JavaConstant clazz = access.getProviders().getConstantReflection().asJavaClass(type);
        VMError.guarantee(clazz != null && !clazz.isNull(), "Cannot obtain the guest Class object for %s", type);
        try {
            loader.watchdog.recordActivity();
            /*
             * This can run arbitrary user code, i.e., it can deadlock or get stuck in an endless
             * loop when there is a bug in the user's code. Our deadlock watchdog detects and
             * reports such cases. To make that as deterministic as possible, we record watchdog
             * activity just before and after the initialization.
             */
            access.invokeStatic(guestEnsureClassInitialized, clazz, loader.classLoaderSupport.getLibGraalLoader());
            loader.watchdog.recordActivity();
            return InitKind.BUILD_TIME;
        } catch (InvocationException ex) {
            JavaConstant exception = ex.getExceptionObject();
            VMError.guarantee(exception != null && !exception.isNull(), "Guest class initialization of %s failed without a guest exception object", type);
            ResolvedJavaType exceptionType = access.getProviders().getMetaAccess().lookupJavaType(exception);
            VMError.guarantee(exceptionType != null && guestThrowable.isAssignableFrom(exceptionType), "Guest class initialization of %s failed with invalid exception object %s", type,
                            exception);

            if (guestNoClassDefFoundError.isAssignableFrom(exceptionType)) {
                if (allowErrors || !LinkAtBuildTimeSupport.singleton().linkAtBuildTime(type)) {
                    return InitKind.RUN_TIME;
                }
                String msg = "Class initialization of " + JVMCIReflectionUtil.getTypeName(type) + " failed. " +
                                LinkAtBuildTimeSupport.singleton().errorMessageFor(type) + " " +
                                instructionsToInitializeAtRuntime(type);
                throw UserError.abort(ex, "%s", msg);
            }

            if (allowErrors) {
                return InitKind.RUN_TIME;
            }
            String msg = "Class initialization of " + JVMCIReflectionUtil.getTypeName(type) + " failed. " + instructionsToInitializeAtRuntime(type);
            if (guestExceptionInInitializerError.isAssignableFrom(exceptionType)) {
                JavaConstant details = access.invokeStatic(guestFormatExceptionInInitializerError, exception);
                msg += access.asHostString(details);
            }
            throw UserError.abort(ex, "%s", msg);
        }
    }

    private static String instructionsToInitializeAtRuntime(ResolvedJavaType type) {
        return "Use the option " + SubstrateOptionsParser.commandArgument(ClassInitializationOptions.ClassInitialization, JVMCIReflectionUtil.getTypeName(type), "initialize-at-run-time", true, true) +
                        " to explicitly request initialization of this class at run time.";
    }

    @Override
    public void initializeAtRunTime(Class<?> clazz, String reason) {
        initializeAtRunTime(GuestAccess.get().lookupType(clazz), reason);
    }

    @Override
    public void initializeAtRunTime(ResolvedJavaType type, String reason) {
        initializeAtRunTime(JVMCIReflectionUtil.getTypeName(normalizeType(type)), reason, true);
    }

    @Override
    public void initializeAtRunTime(String name, String reason) {
        initializeAtRunTime(name, reason, loader.guestTypes.findType(name).isPresent());
    }

    public void initializeAtRunTime(String name, String reason, boolean strict) {
        UserError.guarantee(!configurationSealed, "The class initialization configuration can be changed only before the phase analysis.");
        classInitializationConfiguration.insert(name, InitKind.RUN_TIME, reason, strict);
    }

    @Override
    public void initializeAtBuildTime(ResolvedJavaType type, String reason) {
        UserError.guarantee(!configurationSealed, "The class initialization configuration can be changed only before the phase analysis.");
        forceInitializeHosted(normalizeType(type), reason, false);
    }

    @Override
    public void initializeAtBuildTime(Class<?> clazz, String reason) {
        initializeAtBuildTime(GuestAccess.get().lookupType(clazz), reason);
    }

    @Override
    public void initializeAtBuildTime(String name, String reason) {
        UserError.guarantee(!configurationSealed, "The class initialization configuration can be changed only before the phase analysis.");

        ResolvedJavaType type = loader.guestTypes.findType(name).get();
        classInitializationConfiguration.insert(name, InitKind.BUILD_TIME, reason, type != null);
        if (type != null) {
            forceInitializeHosted(type, reason, false);
        }
    }

    static boolean isClassListedInStringOption(AccumulatingLocatableMultiOptionValue.Strings option, Class<?> clazz) {
        return option.values().contains(clazz.getName());
    }

    private static boolean isObjectInstantiationForClassTracked(Class<?> clazz) {
        return TraceObjectInstantiation.hasBeenSet() && isClassListedInStringOption(TraceObjectInstantiation.getValue(), clazz);
    }

    public String objectInstantiationTraceMessage(Object obj, String prefix, Function<String, String> action) {
        Map<Object, StackTraceElement[]> instantiatedObjects = ClassInitializationTracking.instantiatedObjects;
        if (isProxyOrLambda(obj)) {
            return prefix + "If these objects should not be stored in the image heap, please try to infer from the source code how the culprit object got instantiated." + System.lineSeparator();
        } else if (!isObjectInstantiationForClassTracked(obj.getClass())) {
            return prefix + "If these objects should not be stored in the image heap, you can use " +
                            SubstrateOptionsParser.commandArgument(TraceObjectInstantiation, obj.getClass().getName(), true, true) +
                            "to find classes that instantiate these objects. " +
                            "Once you found such a class, you can mark it explicitly for run time initialization with " +
                            SubstrateOptionsParser.commandArgument(ClassInitializationOptions.ClassInitialization, "<culprit>", "initialize-at-run-time", true, true) +
                            "to prevent the instantiation of the object." + System.lineSeparator();
        } else if (instantiatedObjects.containsKey(obj)) {
            String culprit = null;
            StackTraceElement[] trace = instantiatedObjects.get(obj);
            for (StackTraceElement stackTraceElement : trace) {
                if (stackTraceElement.getMethodName().equals("<clinit>")) {
                    culprit = stackTraceElement.getClassName();
                }
            }
            if (culprit != null) {
                return prefix + action.apply(culprit) + System.lineSeparator() + "The culprit object has been instantiated by the '" + culprit + "' class initializer with the following trace:" +
                                System.lineSeparator() + getTraceString(instantiatedObjects.get(obj));
            } else {
                return prefix + action.apply(culprit) + System.lineSeparator() + "The culprit object has been instantiated with the following trace:" + System.lineSeparator() +
                                getTraceString(instantiatedObjects.get(obj)) + action;
            }
        } else {
            return prefix + "Object has been initialized in a core JDK class that is not instrumented for class initialization tracking. Therefore, a stack trace cannot be provided." +
                            System.lineSeparator() +
                            "Please try to infer from the source code how the culprit object got instantiated." + System.lineSeparator();
        }
    }

    static boolean isProxyOrLambda(Object obj) {
        return obj.getClass().getName().contains(LambdaUtils.LAMBDA_CLASS_NAME_SUBSTRING) || Proxy.isProxyClass(obj.getClass());
    }

    static String getTraceString(StackTraceElement[] trace) {
        StringBuilder b = new StringBuilder();

        for (StackTraceElement stackTraceElement : trace) {
            b.append("\tat ").append(stackTraceElement.toString()).append(System.lineSeparator());
        }

        return b.toString();
    }

    /**
     * Initializes the class during image building, and reports an error if the user requested to
     * delay initialization to runtime.
     */
    public void forceInitializeHosted(ResolvedJavaType type, String reason, boolean allowInitializationErrors) {
        forceInitializeHosted0(normalizeType(type), reason, allowInitializationErrors);
    }

    /** Recursively forces initialization of an already canonical guest type. */
    private void forceInitializeHosted0(ResolvedJavaType type, String reason, boolean allowInitializationErrors) {
        if (type == null) {
            return;
        }

        classInitializationConfiguration.insert(JVMCIReflectionUtil.getTypeName(type), InitKind.BUILD_TIME, reason, true);
        InitKind initKind = ensureClassInitialized(type, allowInitializationErrors);
        if (initKind == InitKind.RUN_TIME) {
            assert allowInitializationErrors || !LinkAtBuildTimeSupport.singleton().linkAtBuildTime(type);
            if (ImageLayerBuildingSupport.buildingImageLayer()) {
                /*
                 * Record class configured with --initialize-at-build-time but for which
                 * initialization failed so it's registered as initialized at run time. To ensure
                 * that the state of class initialization registries is consistent between layers
                 * we'll attempt to init it again in the next layer and verify that it fails. Class
                 * initialization in layered images will be further refined by (GR-65405).
                 */
                requestedAtBuildTimeButFailedInit.add(type);
            }
        }
        classInitKinds.put(type, initKind);

        forceInitializeHosted0(type.getSuperclass(), "super type of " + JVMCIReflectionUtil.getTypeName(type), allowInitializationErrors);
        if (!type.isInterface()) {
            /*
             * Initialization of an interface does not trigger initialization of superinterfaces.
             * Regardless whether any of the involved interfaces declare default methods.
             */
            forceInitializeInterfaces(type.getInterfaces(), "super type of " + JVMCIReflectionUtil.getTypeName(type));
        }
    }

    private void forceInitializeInterfaces(ResolvedJavaType[] interfaces, String reason) {
        for (ResolvedJavaType iface : interfaces) {
            if (iface.declaresDefaultMethods()) {
                classInitializationConfiguration.insert(JVMCIReflectionUtil.getTypeName(iface), InitKind.BUILD_TIME, reason, true);

                InitKind initKind = ensureClassInitialized(iface, false);
                VMError.guarantee(initKind == InitKind.BUILD_TIME, "Initialization of %s failed so all interfaces with default methods must be already initialized.",
                                JVMCIReflectionUtil.getTypeName(iface));
                classInitKinds.put(iface, InitKind.BUILD_TIME);
            }
            forceInitializeInterfaces(iface.getInterfaces(), "super type of " + JVMCIReflectionUtil.getTypeName(iface));
        }
    }

    InitKind computeInitKindAndMaybeInitializeClass(ResolvedJavaType type) {
        return computeInitKindAndMaybeInitializeClass(type, true);
    }

    /**
     * Computes the class initialization kind of the provided class, all superclasses, and all
     * interfaces that the provided class depends on (i.e., interfaces implemented by the provided
     * class that declare default methods).
     * <p>
     * Also defines class initialization based on a policy of the subclass.
     */
    InitKind computeInitKindAndMaybeInitializeClass(ResolvedJavaType type, boolean memoize) {
        InitKind existing = classInitKinds.get(type);
        if (existing != null) {
            return existing;
        }

        if (type.isPrimitive()) {
            forceInitializeHosted0(type, "primitive types are initialized at build time", false);
            return InitKind.BUILD_TIME;
        }

        if (type.isArray()) {
            forceInitializeHosted0(type, "arrays are initialized at build time", false);
            return InitKind.BUILD_TIME;
        }

        InitKind specifiedInitKind = specifiedInitKindFor0(type);
        InitKind clazzResult = specifiedInitKind != null ? specifiedInitKind : InitKind.RUN_TIME;

        InitKind superResult = InitKind.BUILD_TIME;
        if (type.getSuperclass() != null) {
            superResult = superResult.max(computeInitKindAndMaybeInitializeClass(type.getSuperclass(), memoize));
        }
        superResult = superResult.max(processInterfaces(type, memoize));

        if (superResult == InitKind.BUILD_TIME && (ProxyRenamingSubstitutionProcessor.isProxyType(type) || LambdaUtils.isLambdaType(type))) {
            /*
             * To simplify class initialization configuration for proxy and lambda types,
             * registering all of their implemented interfaces as "initialize at build time" is
             * equivalent to registering the proxy/lambda type itself. This is safe because we know
             * that proxy/lambda types themselves have no problematic code in the class initializer
             * (they are generated classes).
             *
             * Note that we must look at all interfaces, including transitive dependencies.
             */
            boolean allInterfacesSpecifiedAsBuildTime = true;
            for (ResolvedJavaType iface : allInterfaces0(type)) {
                if (specifiedInitKindFor0(iface) != InitKind.BUILD_TIME) {
                    allInterfacesSpecifiedAsBuildTime = false;
                    break;
                }
            }
            if (allInterfacesSpecifiedAsBuildTime) {
                forceInitializeHosted0(type, "proxy/lambda classes with all interfaces explicitly marked as --initialize-at-build-time are also initialized at build time", false);
                return InitKind.BUILD_TIME;
            }
        }

        InitKind result = superResult.max(clazzResult);

        if (memoize) {
            if (result != InitKind.RUN_TIME) {
                result = result.max(ensureClassInitialized(type, false));
            }

            InitKind previous = classInitKinds.putIfAbsent(type, result);
            if (previous != null && previous != result) {
                throw VMError.shouldNotReachHere("Conflicting class initialization kind: " + previous + " != " + result + " for " + type);
            }
        }
        return result;
    }

    private InitKind processInterfaces(ResolvedJavaType type, boolean memoizeEager) {
        /*
         * Note that we do not call computeInitKindForClass(clazz) on purpose: if clazz is the root
         * class or an interface declaring default methods, then
         * computeInitKindAndMaybeInitializeClass() already calls computeInitKindForClass. If the
         * interface does not declare default methods, than we must not take the InitKind of that
         * interface into account, because interfaces without default methods are independent from a
         * class initialization point of view.
         */
        InitKind result = InitKind.BUILD_TIME;

        for (ResolvedJavaType iface : type.getInterfaces()) {
            if (iface.declaresDefaultMethods()) {
                /*
                 * An interface that declares default methods is initialized when a class
                 * implementing it is initialized. So we need to inherit the InitKind from such an
                 * interface.
                 */
                result = result.max(computeInitKindAndMaybeInitializeClass(iface, memoizeEager));
            } else {
                /*
                 * An interface that does not declare default methods is independent from a class
                 * that implements it, i.e., the interface can still be uninitialized even when the
                 * class is initialized.
                 */
                result = result.max(processInterfaces(iface, memoizeEager));
            }
        }
        return result;
    }

    String reasonForClass(ResolvedJavaType type) {
        ResolvedJavaType guestType = normalizeType(type);
        InitKind initKind = classInitKinds.get(guestType);
        String reason = classInitializationConfiguration.lookupReason(JVMCIReflectionUtil.getTypeName(guestType));
        if (initKind == InitKind.RUN_TIME) {
            return "classes are initialized at run time by default";
        } else if (reason != null) {
            return reason;
        } else {
            throw VMError.shouldNotReachHere("Must be either proven or specified");
        }
    }

    public static EconomicSet<ResolvedJavaType> allInterfaces(ResolvedJavaType type) {
        return allInterfaces0(normalizeType(type));
    }

    /** Collects interfaces from an already canonical guest type. */
    private static EconomicSet<ResolvedJavaType> allInterfaces0(ResolvedJavaType type) {
        EconomicSet<ResolvedJavaType> result = EconomicSet.create();
        addAllInterfaces(type, result);
        return result;
    }

    private static void addAllInterfaces(ResolvedJavaType type, EconomicSet<ResolvedJavaType> result) {
        for (ResolvedJavaType interf : type.getInterfaces()) {
            if (result.add(interf)) {
                addAllInterfaces(interf, result);
            }
        }
    }

    public synchronized void addForTypeReachedTracking(Class<?> clazz) {
        addForTypeReachedTracking(GuestAccess.get().lookupType(clazz));
    }

    public synchronized void addForTypeReachedTracking(ResolvedJavaType type) {
        ResolvedJavaType guestType = normalizeType(type);
        if (TrackTypeReachedOnInterfaces.getValue() && guestType.isInterface() && !guestType.declaresDefaultMethods()) {
            LogUtils.info("Detected 'typeReached' on interface type without default methods: %s", guestType.toClassName());
        }

        if (!isAlwaysReached0(guestType)) {
            UserError.guarantee(!typeReachedTrackingSealed || typesRequiringReachability.contains(guestType),
                            "It is not possible to register types for reachability tracking after the analysis has started if they were not registered before analysis started. Trying to register: %s",
                            guestType.toClassName());
            typesRequiringReachability.add(guestType);
        }
    }

    public boolean isAlwaysReached(ResolvedJavaType type) {
        return isAlwaysReached0(normalizeType(type));
    }

    /** Tests always-reached policy for an already canonical guest type. */
    private boolean isAlwaysReached0(ResolvedJavaType type) {
        if (type.isPrimitive() || type.isArray() || alwaysReachedTypes.contains(type)) {
            return true;
        }

        Set<String> jdkModules = Set.of("java.base", "jdk.management", "java.management", "org.graalvm.collections");
        String moduleName = GuestAccess.get().getModule(type).getName();
        return moduleName != null && (HostedModuleSupport.SYSTEM_MODULES.contains(moduleName) || jdkModules.contains(moduleName));
    }

    /**
     * If any type in the type hierarchy was marked as "type reached", we have to track
     * initialization for all its subtypes. Otherwise, marking the supertype as reached could be
     * missed when the initializer of the subtype is computed at build time.
     */
    public boolean requiresInitializationNodeForTypeReached(ResolvedJavaType type) {
        return type != null && requiresInitializationNodeForTypeReached0(normalizeType(type));
    }

    /** Traverses an already canonical guest type without repeating ownership checks. */
    private boolean requiresInitializationNodeForTypeReached0(ResolvedJavaType type) {
        if (isAlwaysReached0(type)) {
            return false;
        }

        if (TreatAllUserSpaceTypesAsTrackedForTypeReached.getValue()) {
            return true;
        }

        if (typesRequiringReachability.contains(type) ||
                        type.getSuperclass() != null && requiresInitializationNodeForTypeReached0(type.getSuperclass())) {
            return true;
        }

        for (ResolvedJavaType anInterface : type.getInterfaces()) {
            if (requiresInitializationNodeForTypeReached0(anInterface)) {
                return true;
            }
        }
        return false;
    }
}
