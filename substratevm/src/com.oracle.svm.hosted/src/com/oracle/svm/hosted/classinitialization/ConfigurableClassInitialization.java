/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.SubstrateOptions.TraceClassInitialization;
import static com.oracle.svm.core.SubstrateOptions.TraceObjectInstantiation;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatures;
import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.LinkAtBuildTimeSupport;

import jdk.internal.misc.Unsafe;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * The core class for deciding whether a class should be initialized during image building or class
 * initialization should be delayed to runtime.
 */
public class ConfigurableClassInitialization implements ClassInitializationSupport {

    /**
     * Setup for class initialization: configured through features and command line input. It
     * represents the user desires about class initialization and helps in finding configuration
     * issues.
     */
    private final ClassInitializationConfiguration classInitializationConfiguration = new ClassInitializationConfiguration();

    /**
     * The initialization kind for all classes seen during image building. Classes are inserted into
     * this map the first time information was queried and used during image building. This is the
     * ground truth about what got initialized during image building.
     */
    private final ConcurrentMap<Class<?>, InitKind> classInitKinds = new ConcurrentHashMap<>();

    /**
     * These two are intentionally static to keep the reference to objects and classes that were
     * initialized in the JDK.
     */
    private static final Map<Class<?>, StackTraceElement[]> initializedClasses = new ConcurrentHashMap<>();
    /**
     * Instantiated objects must be traced using their identities as their hashCode may change
     * during the execution. We also want two objects of the same class that have the same hash and
     * are equal to be mapped as two distinct entries.
     */
    private static final Map<Object, StackTraceElement[]> instantiatedObjects = Collections.synchronizedMap(new IdentityHashMap<>());

    private boolean configurationSealed;

    final ImageClassLoader loader;

    /**
     * Non-null while the static analysis is running to allow reporting of class initialization
     * errors without immediately aborting image building.
     */
    private UnsupportedFeatures unsupportedFeatures;
    protected MetaAccessProvider metaAccess;

    private final EarlyClassInitializerAnalysis earlyClassInitializerAnalysis;
    private Set<Class<?>> provenSafeEarly = Collections.synchronizedSet(new HashSet<>());
    private Set<Class<?>> provenSafeLate = Collections.synchronizedSet(new HashSet<>());

    public ConfigurableClassInitialization(MetaAccessProvider metaAccess, ImageClassLoader loader) {
        this.metaAccess = metaAccess;
        this.loader = loader;
        this.earlyClassInitializerAnalysis = new EarlyClassInitializerAnalysis(this);
    }

    @Override
    public void setConfigurationSealed(boolean sealed) {
        configurationSealed = sealed;
        if (configurationSealed && ClassInitializationOptions.PrintClassInitialization.getValue()) {
            List<ClassOrPackageConfig> allConfigs = classInitializationConfiguration.allConfigs();
            allConfigs.sort(Comparator.comparing(ClassOrPackageConfig::getName));
            String path = Paths.get(Paths.get(SubstrateOptions.Path.getValue()).toString(), "reports").toAbsolutePath().toString();
            ReportUtils.report("class initialization configuration", path, "class_initialization_configuration", "csv", writer -> {
                writer.println("Class or Package Name, Initialization Kind, Reasons");
                for (ClassOrPackageConfig config : allConfigs) {
                    writer.append(config.getName()).append(", ").append(config.getKind().toString()).append(", ")
                                    .append(String.join(" and ", config.getReasons())).append(System.lineSeparator());
                }
            });
        }
    }

    @Override
    public void setUnsupportedFeatures(UnsupportedFeatures unsupportedFeatures) {
        this.unsupportedFeatures = unsupportedFeatures;
    }

    private InitKind computeInitKindAndMaybeInitializeClass(Class<?> clazz) {
        return computeInitKindAndMaybeInitializeClass(clazz, true, null);
    }

    @Override
    public InitKind specifiedInitKindFor(Class<?> clazz) {
        return classInitializationConfiguration.lookupKind(clazz.getTypeName()).getLeft();
    }

    @Override
    public boolean canBeProvenSafe(Class<?> clazz) {
        InitKind initKind = specifiedInitKindFor(clazz);
        return initKind == null || (initKind.isRunTime() && !isStrictlyDefined(clazz));
    }

    private Boolean isStrictlyDefined(Class<?> clazz) {
        return classInitializationConfiguration.lookupKind(clazz.getTypeName()).getRight();
    }

    @Override
    public Set<Class<?>> classesWithKind(InitKind kind) {
        return classInitKinds.entrySet().stream()
                        .filter(e -> e.getValue() == kind)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet());
    }

    @Override
    public boolean shouldInitializeAtRuntime(ResolvedJavaType type) {
        return computeInitKindAndMaybeInitializeClass(getJavaClass(type)) != InitKind.BUILD_TIME;
    }

    @Override
    public boolean shouldInitializeAtRuntime(Class<?> clazz) {
        return computeInitKindAndMaybeInitializeClass(clazz) != InitKind.BUILD_TIME;
    }

    @Override
    public void maybeInitializeHosted(ResolvedJavaType type) {
        computeInitKindAndMaybeInitializeClass(getJavaClass(type));
    }

    /**
     * Ensure class is initialized. Report class initialization errors in a user-friendly way if
     * class initialization fails.
     */
    private InitKind ensureClassInitialized(Class<?> clazz, boolean allowErrors) {
        try {
            Unsafe.getUnsafe().ensureClassInitialized(clazz);
            return InitKind.BUILD_TIME;
        } catch (NoClassDefFoundError ex) {
            if (allowErrors) {
                return InitKind.RUN_TIME;
            } else if (!LinkAtBuildTimeSupport.singleton().linkAtBuildTime(clazz)) {
                System.out.println("Warning: class initialization of class " + clazz.getTypeName() + " failed with exception " +
                                ex.getClass().getTypeName() + (ex.getMessage() == null ? "" : ": " + ex.getMessage()) + ". This class will be initialized at run time. " +
                                instructionsToInitializeAtRuntime(clazz));
                return InitKind.RUN_TIME;
            } else {
                return reportInitializationError("Class initialization of " + clazz.getTypeName() + " failed. " +
                                LinkAtBuildTimeSupport.singleton().errorMessageFor(clazz) + " " +
                                instructionsToInitializeAtRuntime(clazz), clazz, ex);
            }
        } catch (Throwable t) {
            if (allowErrors) {
                return InitKind.RUN_TIME;
            } else {
                return reportInitializationError("Class initialization of " + clazz.getTypeName() + " failed. " +
                                instructionsToInitializeAtRuntime(clazz), clazz, t);
            }
        }
    }

    private InitKind reportInitializationError(String msg, Class<?> clazz, Throwable t) {
        if (unsupportedFeatures != null) {
            /*
             * Report an unsupported feature during static analysis, so that we can collect multiple
             * error messages without aborting analysis immediately. Returning InitKind.RUN_TIME
             * ensures that analysis can continue, even though eventually an error is reported (so
             * no image will be created).
             */
            unsupportedFeatures.addMessage(clazz.getTypeName(), null, msg, null, t);
            return InitKind.RUN_TIME;
        } else {
            throw UserError.abort(t, "%s", msg);
        }
    }

    private static String instructionsToInitializeAtRuntime(Class<?> clazz) {
        return "Use the option " + SubstrateOptionsParser.commandArgument(ClassInitializationOptions.ClassInitialization, clazz.getTypeName(), "initialize-at-run-time") +
                        " to explicitly request delayed initialization of this class.";
    }

    static Class<?> getJavaClass(ResolvedJavaType type) {
        return OriginalClassProvider.getJavaClass(GraalAccess.getOriginalSnippetReflection(), type);
    }

    @Override
    public void initializeAtRunTime(String name, String reason) {
        UserError.guarantee(!configurationSealed, "The class initialization configuration can be changed only before the phase analysis.");
        Class<?> clazz = loader.findClass(name).get();
        if (clazz != null) {
            classInitializationConfiguration.insert(name, InitKind.RUN_TIME, reason, true);
            initializeAtRunTime(clazz, reason);
        } else {
            classInitializationConfiguration.insert(name, InitKind.RUN_TIME, reason, false);
        }
    }

    @Override
    public void initializeAtBuildTime(String name, String reason) {
        UserError.guarantee(!configurationSealed, "The class initialization configuration can be changed only before the phase analysis.");

        Class<?> clazz = loader.findClass(name).get();
        if (clazz != null) {
            classInitializationConfiguration.insert(name, InitKind.BUILD_TIME, reason, true);
            initializeAtBuildTime(clazz, reason);
        } else {
            classInitializationConfiguration.insert(name, InitKind.BUILD_TIME, reason, false);
        }
    }

    @Override
    public void rerunInitialization(String name, String reason) {
        UserError.guarantee(!configurationSealed, "The class initialization configuration can be changed only before the phase analysis.");
        Class<?> clazz = loader.findClass(name).get();
        if (clazz != null) {
            classInitializationConfiguration.insert(name, InitKind.RERUN, reason, true);
            rerunInitialization(clazz, reason);
        } else {
            classInitializationConfiguration.insert(name, InitKind.RERUN, reason, false);
        }
    }

    @Override
    public void initializeAtRunTime(Class<?> clazz, String reason) {
        UserError.guarantee(!configurationSealed, "The class initialization configuration can be changed only before the phase analysis.");
        classInitializationConfiguration.insert(clazz.getTypeName(), InitKind.RUN_TIME, reason, true);
        setSubclassesAsRunTime(clazz);
        checkEagerInitialization(clazz);

        if (!Unsafe.getUnsafe().shouldBeInitialized(clazz)) {
            throw UserError.abort("The class %1$s has already been initialized (%2$s); it is too late to register %1$s for build-time initialization. %3$s",
                            clazz.getTypeName(), reason,
                            classInitializationErrorMessage(clazz, "Try avoiding this conflict by avoiding to initialize the class that caused initialization of " + clazz.getTypeName() +
                                            " or by not marking " + clazz.getTypeName() + " for build-time initialization."));
        }
        /*
         * Propagate possible existing RUN_TIME registration from a superclass, so that we can check
         * for user errors below.
         */
        computeInitKindAndMaybeInitializeClass(clazz, false, null);

        InitKind previousKind = classInitKinds.put(clazz, InitKind.RUN_TIME);
        if (previousKind == InitKind.BUILD_TIME) {
            throw UserError.abort("Class is already initialized, so it is too late to register delaying class initialization: %s for reason: %s", clazz.getTypeName(), reason);
        } else if (previousKind == InitKind.RERUN) {
            throw UserError.abort("Class is registered both for delaying and rerunning the class initializer: %s for reason: %s", clazz.getTypeName(), reason);
        }
    }

    private static boolean isClassListedInStringOption(String option, Class<?> clazz) {
        return Arrays.asList(option.split(",")).contains(clazz.getName());
    }

    private static boolean isClassInitializationTracked(Class<?> clazz) {
        return TraceClassInitialization.hasBeenSet() && isClassListedInStringOption(TraceClassInitialization.getValue(), clazz);
    }

    private static boolean isObjectInstantiationForClassTracked(Class<?> clazz) {
        return TraceObjectInstantiation.hasBeenSet() && isClassListedInStringOption(TraceObjectInstantiation.getValue(), clazz);
    }

    private static String classInitializationErrorMessage(Class<?> clazz, String action) {
        if (!isClassInitializationTracked(clazz)) {
            return "To see why " + clazz.getName() + " got initialized use " + SubstrateOptionsParser.commandArgument(TraceClassInitialization, clazz.getName());
        } else if (initializedClasses.containsKey(clazz)) {

            StackTraceElement[] trace = initializedClasses.get(clazz);
            String culprit = null;
            for (StackTraceElement stackTraceElement : trace) {
                if (stackTraceElement.getMethodName().equals("<clinit>")) {
                    culprit = stackTraceElement.getClassName();
                }
            }
            if (culprit != null) {
                return culprit + " caused initialization of this class with the following trace: \n" + classInitializationTrace(clazz);
            } else {
                return clazz.getTypeName() + " has been initialized through the following trace:\n" + classInitializationTrace(clazz);
            }
        } else {
            return clazz.getTypeName() + " has been initialized without the native-image initialization instrumentation and the stack trace can't be tracked. " + action;
        }
    }

    @Override
    public String objectInstantiationTraceMessage(Object obj, String action) {
        if (!isObjectInstantiationForClassTracked(obj.getClass())) {
            return " To see how this object got instantiated use " + SubstrateOptionsParser.commandArgument(TraceObjectInstantiation, obj.getClass().getName()) + ".";
        } else if (instantiatedObjects.containsKey(obj)) {
            String culprit = null;
            StackTraceElement[] trace = instantiatedObjects.get(obj);
            boolean containsLambdaMetaFactory = false;
            for (StackTraceElement stackTraceElement : trace) {
                if (stackTraceElement.getMethodName().equals("<clinit>")) {
                    culprit = stackTraceElement.getClassName();
                }
                if (stackTraceElement.getClassName().equals("java.lang.invoke.LambdaMetafactory")) {
                    containsLambdaMetaFactory = true;
                }
            }
            if (containsLambdaMetaFactory) {
                return " Object was instantiated through a lambda (https://github.com/oracle/graal/issues/1218). Try marking " + obj.getClass().getTypeName() +
                                " for build-time initialization with " + SubstrateOptionsParser.commandArgument(
                                                ClassInitializationOptions.ClassInitialization, obj.getClass().getTypeName(), "initialize-at-build-time") +
                                ".";
            } else if (culprit != null) {
                return " Object has been initialized by the " + culprit + " class initializer with a trace: \n " + getTraceString(instantiatedObjects.get(obj)) + ". " + action;
            } else {
                return " Object has been initialized through the following trace:\n" + getTraceString(instantiatedObjects.get(obj)) + ". " + action;
            }
        } else {
            return " Object has been initialized without the native-image initialization instrumentation and the stack trace can't be tracked.";
        }
    }

    @Override
    public String reasonForClass(Class<?> clazz) {
        InitKind initKind = classInitKinds.get(clazz);
        String reason = classInitializationConfiguration.lookupReason(clazz.getTypeName());
        if (initKind == InitKind.BUILD_TIME && provenSafeEarly.contains(clazz)) {
            return "class proven as side-effect free before analysis";
        } else if (initKind == InitKind.BUILD_TIME && provenSafeLate.contains(clazz)) {
            return "class proven as side-effect free after analysis";
        } else if (initKind.isRunTime()) {
            return "classes are initialized at run time by default";
        } else if (reason != null) {
            return reason;
        } else {
            throw VMError.shouldNotReachHere("Must be either proven or specified");
        }
    }

    private static String classInitializationTrace(Class<?> clazz) {
        return getTraceString(initializedClasses.get(clazz));
    }

    public static Map<Class<?>, StackTraceElement[]> getInitializedClasses() {
        return initializedClasses;
    }

    public static String getTraceString(StackTraceElement[] trace) {
        StringBuilder b = new StringBuilder();

        for (int i = 0; i < trace.length; i++) {
            StackTraceElement stackTraceElement = trace[i];
            b.append("\tat ").append(stackTraceElement.toString()).append("\n");
        }

        return b.toString();
    }

    @Override
    public void rerunInitialization(Class<?> clazz, String reason) {
        UserError.guarantee(!configurationSealed, "The class initialization configuration can be changed only before the phase analysis.");
        classInitializationConfiguration.insert(clazz.getTypeName(), InitKind.RERUN, reason, true);
        checkEagerInitialization(clazz);

        try {
            Unsafe.getUnsafe().ensureClassInitialized(clazz);
        } catch (Throwable ex) {
            throw UserError.abort(ex, "Class initialization failed for %s. The class is requested for re-running (reason: %s)", clazz.getTypeName(), reason);
        }

        /*
         * Propagate possible existing RUN_TIME registration from a superclass, so that we can check
         * for user errors below.
         */
        computeInitKindAndMaybeInitializeClass(clazz, false, null);

        InitKind previousKind = classInitKinds.put(clazz, InitKind.RERUN);
        if (previousKind != null) {
            if (previousKind == InitKind.BUILD_TIME) {
                throw UserError.abort("The information that the class should be initialized during image building has already been used, " +
                                "so it is too late to register the class initializer of %s for re-running. The reason for re-run request is %s",
                                clazz.getTypeName(), reason);
            } else if (previousKind.isRunTime()) {
                throw UserError.abort("Class or a superclass is already registered for delaying the class initializer, " +
                                "so it is too late to register the class initializer of %s for re-running. The reason for re-run request is %s",
                                clazz.getTypeName(), reason);
            }
        }
    }

    @Override
    public void initializeAtBuildTime(Class<?> aClass, String reason) {
        UserError.guarantee(!configurationSealed, "The class initialization configuration can be changed only before the phase analysis.");
        classInitializationConfiguration.insert(aClass.getTypeName(), InitKind.BUILD_TIME, reason, true);
        forceInitializeHosted(aClass, reason, false);
    }

    private void setSubclassesAsRunTime(Class<?> clazz) {
        if (clazz.isInterface() && !metaAccess.lookupJavaType(clazz).declaresDefaultMethods()) {
            /*
             * An interface that does not declare a default method is independent from a class
             * initialization point of view, i.e., it is not initialized when a class implementing
             * that interface is initialized.
             */
            return;
        }
        loader.findSubclasses(clazz, false).stream()
                        .filter(c -> !c.equals(clazz))
                        .filter(c -> !(c.isInterface() && !metaAccess.lookupJavaType(c).declaresDefaultMethods()))
                        .forEach(c -> classInitializationConfiguration.insert(c.getTypeName(), InitKind.RUN_TIME, "subtype of " + clazz.getTypeName(), true));
    }

    @Override
    public void reportClassInitialized(Class<?> clazz, StackTraceElement[] stackTrace) {
        assert TraceClassInitialization.hasBeenSet();
        initializedClasses.put(clazz, relevantStackTrace(stackTrace));
        /*
         * We don't do early failing here. Lambdas tend to initialize many classes that should not
         * be initialized, but effectively they do not change the behavior of the final image.
         *
         * Failing early here creates many unnecessary constraints and reduces usability.
         */
    }

    @Override
    public void reportObjectInstantiated(Object o, StackTraceElement[] stackTrace) {
        assert TraceObjectInstantiation.hasBeenSet();
        instantiatedObjects.putIfAbsent(o, relevantStackTrace(stackTrace));
    }

    /**
     * If the stack trace contains class initializaiton takes the stack up to the last
     * initialization. Otherwise returns the whole stack trace. The method never returns the stack
     * from the instrumented part.
     *
     * This method can be refined on a case-by-case basis to print nicer traces.
     *
     * @return a stack trace that led to erroneous situation
     */
    private static StackTraceElement[] relevantStackTrace(StackTraceElement[] stack) {
        ArrayList<StackTraceElement> filteredStack = new ArrayList<>();
        int lastClinit = 0;
        boolean containsLambdaMetaFactory = false;
        for (int i = 0; i < stack.length; i++) {
            StackTraceElement stackTraceElement = stack[i];
            if ("<clinit>".equals(stackTraceElement.getMethodName())) {
                lastClinit = i;
            }
            if (stackTraceElement.getClassName().equals("java.lang.invoke.LambdaMetafactory")) {
                containsLambdaMetaFactory = true;
            }
            filteredStack.add(stackTraceElement);
        }
        List<StackTraceElement> finalStack = lastClinit != 0 && !containsLambdaMetaFactory ? filteredStack.subList(0, lastClinit + 1) : filteredStack;
        return finalStack.toArray(new StackTraceElement[0]);
    }

    @Override
    public void forceInitializeHosted(Class<?> clazz, String reason, boolean allowInitializationErrors) {
        if (clazz == null) {
            return;
        }
        classInitializationConfiguration.insert(clazz.getTypeName(), InitKind.BUILD_TIME, reason, true);
        InitKind initKind = ensureClassInitialized(clazz, allowInitializationErrors);
        classInitKinds.put(clazz, initKind);

        forceInitializeHosted(clazz.getSuperclass(), "super type of " + clazz.getTypeName(), allowInitializationErrors);
        forceInitializeInterfaces(clazz.getInterfaces(), "super type of " + clazz.getTypeName());
    }

    private void forceInitializeInterfaces(Class<?>[] interfaces, String reason) {
        for (Class<?> iface : interfaces) {
            if (metaAccess.lookupJavaType(iface).declaresDefaultMethods()) {
                classInitializationConfiguration.insert(iface.getTypeName(), InitKind.BUILD_TIME, reason, true);

                ensureClassInitialized(iface, false);
                classInitKinds.put(iface, InitKind.BUILD_TIME);
            }
            forceInitializeInterfaces(iface.getInterfaces(), "super type of " + iface.getTypeName());
        }
    }

    @Override
    public boolean checkDelayedInitialization() {
        /*
         * We check all registered classes here, regardless if the AnalysisType got actually marked
         * as used. Class initialization can have side effects on other classes without the class
         * being used itself, e.g., a class initializer can write a static field in another class.
         */
        Set<Class<?>> illegalyInitialized = new HashSet<>();
        for (Map.Entry<Class<?>, InitKind> entry : classInitKinds.entrySet()) {
            if (entry.getValue().isRunTime() && !Unsafe.getUnsafe().shouldBeInitialized(entry.getKey())) {
                illegalyInitialized.add(entry.getKey());
            }
        }

        if (illegalyInitialized.size() > 0) {
            StringBuilder detailedMessage = new StringBuilder("Classes that should be initialized at run time got initialized during image building:\n ");
            illegalyInitialized.forEach(c -> {
                InitKind specifiedKind = specifiedInitKindFor(c);
                /* not specified by the user so it is an accident => try to fix it */
                if (specifiedKind == null) {
                    detailedMessage.append(c.getTypeName()).append(" was unintentionally initialized at build time. ");
                    detailedMessage.append(classInitializationErrorMessage(c,
                                    "Try marking this class for build-time initialization with " + SubstrateOptionsParser.commandArgument(ClassInitializationOptions.ClassInitialization,
                                                    c.getTypeName(), "initialize-at-build-time")))
                                    .append("\n");
                } else {
                    assert specifiedKind.isRunTime() : "Specified kind must be the same as actual kind for type " + c.getTypeName();
                    String reason = classInitializationConfiguration.lookupReason(c.getTypeName());
                    detailedMessage.append(c.getTypeName()).append(" the class was requested to be initialized at run time (").append(reason).append("). ")
                                    .append(classInitializationErrorMessage(c, "Try avoiding to initialize the class that caused initialization of " + c.getTypeName()))
                                    .append("\n");
                }
            });

            if (!TraceClassInitialization.hasBeenSet()) {
                String traceClassInitArguments = illegalyInitialized.stream().map(Class::getName).collect(Collectors.joining(","));
                System.out.println("To see how the classes got initialized, use " + SubstrateOptionsParser.commandArgument(TraceClassInitialization, traceClassInitArguments));
            }

            throw UserError.abort("%s", detailedMessage);
        }
        return true;
    }

    private static void checkEagerInitialization(Class<?> clazz) {
        if (clazz.isPrimitive() || clazz.isArray()) {
            throw UserError.abort("Primitive types and array classes are initialized at build time because initialization is side-effect free. " +
                            "It is not possible (and also not useful) to register them for run time initialization. Culprit: %s", clazz.getTypeName());
        }
    }

    /**
     * Computes the class initialization kind of the provided class, all superclasses, and all
     * interfaces that the provided class depends on (i.e., interfaces implemented by the provided
     * class that declare default methods).
     *
     * Also defines class initialization based on a policy of the subclass.
     */
    InitKind computeInitKindAndMaybeInitializeClass(Class<?> clazz, boolean memoize, Set<Class<?>> earlyClassInitializerAnalyzedClasses) {
        InitKind existing = classInitKinds.get(clazz);
        if (existing != null) {
            return existing;
        }

        if (clazz.isPrimitive()) {
            forceInitializeHosted(clazz, "primitive types are initialized at build time", false);
            return InitKind.BUILD_TIME;
        }

        if (clazz.isArray()) {
            forceInitializeHosted(clazz, "arrays are initialized at build time", false);
            return InitKind.BUILD_TIME;
        }

        if (clazz.getTypeName().contains("$$StringConcat")) {
            forceInitializeHosted(clazz, "string concatenation classes are initialized at build time", false);
            return InitKind.BUILD_TIME;
        }

        InitKind specifiedInitKind = specifiedInitKindFor(clazz);
        InitKind clazzResult = specifiedInitKind != null ? specifiedInitKind : InitKind.RUN_TIME;

        InitKind superResult = InitKind.BUILD_TIME;
        if (clazz.getSuperclass() != null) {
            superResult = superResult.max(computeInitKindAndMaybeInitializeClass(clazz.getSuperclass(), memoize, earlyClassInitializerAnalyzedClasses));
        }
        superResult = superResult.max(processInterfaces(clazz, memoize, earlyClassInitializerAnalyzedClasses));

        if (memoize && superResult != InitKind.RUN_TIME && clazzResult == InitKind.RUN_TIME && canBeProvenSafe(clazz)) {
            /*
             * Check if the class initializer is side-effect free using a simple intraprocedural
             * analysis.
             */
            if (earlyClassInitializerAnalysis.canInitializeWithoutSideEffects(clazz, earlyClassInitializerAnalyzedClasses)) {
                /*
                 * Note that even if the class initializer is side-effect free, running it can still
                 * fail with an exception. In that case we ignore the exception and initialize the
                 * class at run time (at which time the same exception is probably thrown again).
                 */
                clazzResult = ensureClassInitialized(clazz, true);
                if (clazzResult == InitKind.BUILD_TIME) {
                    addProvenEarly(clazz);
                }
            }
        }

        InitKind result = superResult.max(clazzResult);

        if (memoize) {
            if (!result.isRunTime()) {
                result = result.max(ensureClassInitialized(clazz, false));
            }

            /*
             * Unfortunately, the computation of canInitializeWithoutSideEffects is not completely
             * deterministic: Consider a class A whose class initializer depends on class B. Assume
             * class B has no other dependencies and can therefore be initialized at build time.
             * When class A is analyzed after class B has been initialized, it can also be
             * initialized at build time. But when class A is analyzed before class B has been
             * initialized, it cannot. Since two threads can analyze class A at the same time (there
             * is no per-class locking) and another thread can initialize B at the same time, we can
             * have a conflicting initialization status. In that case, BUILD_TIME must win over
             * RUN_TIME because one thread has already initialized class A.
             */
            result = classInitKinds.merge(clazz, result, InitKind::min);
        }
        return result;
    }

    private InitKind processInterfaces(Class<?> clazz, boolean memoizeEager, Set<Class<?>> earlyClassInitializerAnalyzedClasses) {
        /*
         * Note that we do not call computeInitKindForClass(clazz) on purpose: if clazz is the root
         * class or an interface declaring default methods, then
         * computeInitKindAndMaybeInitializeClass() already calls computeInitKindForClass. If the
         * interface does not declare default methods, than we must not take the InitKind of that
         * interface into account, because interfaces without default methods are independent from a
         * class initialization point of view.
         */
        InitKind result = InitKind.BUILD_TIME;

        for (Class<?> iface : clazz.getInterfaces()) {
            if (metaAccess.lookupJavaType(iface).declaresDefaultMethods()) {
                /*
                 * An interface that declares default methods is initialized when a class
                 * implementing it is initialized. So we need to inherit the InitKind from such an
                 * interface.
                 */
                result = result.max(computeInitKindAndMaybeInitializeClass(iface, memoizeEager, earlyClassInitializerAnalyzedClasses));
            } else {
                /*
                 * An interface that does not declare default methods is independent from a class
                 * that implements it, i.e., the interface can still be uninitialized even when the
                 * class is initialized.
                 */
                result = result.max(processInterfaces(iface, memoizeEager, earlyClassInitializerAnalyzedClasses));
            }
        }
        return result;
    }

    void addProvenEarly(Class<?> clazz) {
        provenSafeEarly.add(clazz);
    }

    @Override
    public void setProvenSafeLate(Set<Class<?>> classes) {
        provenSafeLate = new HashSet<>(classes);
    }
}
