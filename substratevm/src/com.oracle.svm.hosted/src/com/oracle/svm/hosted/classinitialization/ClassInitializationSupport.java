/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Serializable;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Comparator;
import java.util.Formattable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;
import org.graalvm.nativeimage.impl.clinit.ClassInitializationTracking;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.LinkAtBuildTimeSupport;
import com.oracle.svm.util.LogUtils;

import jdk.graal.compiler.java.LambdaUtils;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * The core class for deciding whether a class should be initialized during image building or class
 * initialization should be delayed to runtime.
 */
public class ClassInitializationSupport implements RuntimeClassInitializationSupport {

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
    final ConcurrentMap<Class<?>, InitKind> classInitKinds = new ConcurrentHashMap<>();

    /**
     * We need always-reached types to avoid users injecting class initialization checks in our VM
     * implementation and hot paths and to prevent users from making the whole class hierarchy
     * require initialization nodes.
     */
    @SuppressWarnings("DataFlowIssue")//
    private static final Set<Class<?>> alwaysReachedTypes = Set.of(
                    Object.class, Class.class, String.class,
                    Character.class, Byte.class, Short.class, Integer.class, Long.class, Float.class, Double.class, Boolean.class,
                    Enum.class, Cloneable.class, Formattable.class, Throwable.class, Serializable.class, AutoCloseable.class, Runnable.class,
                    Iterable.class, Collection.class, Set.class, List.class, Map.class,
                    System.class, Thread.class,
                    Reference.class, SoftReference.class, StackWalker.class, ReferenceQueue.class);

    final Set<Class<?>> typesRequiringReachability = ConcurrentHashMap.newKeySet();

    boolean configurationSealed;

    final ImageClassLoader loader;

    /**
     * Non-null while the static analysis is running to allow reporting of class initialization
     * errors without immediately aborting image building.
     */
    final MetaAccessProvider metaAccess;

    public static ClassInitializationSupport singleton() {
        return (ClassInitializationSupport) ImageSingletons.lookup(RuntimeClassInitializationSupport.class);
    }

    public ClassInitializationSupport(MetaAccessProvider metaAccess, ImageClassLoader loader) {
        this.metaAccess = metaAccess;
        this.loader = loader;
    }

    public void setConfigurationSealed(boolean sealed) {
        configurationSealed = sealed;
        if (configurationSealed && ClassInitializationOptions.PrintClassInitialization.getValue()) {
            List<ClassOrPackageConfig> allConfigs = classInitializationConfiguration.allConfigs();
            allConfigs.sort(Comparator.comparing(ClassOrPackageConfig::getName));
            ReportUtils.report("class initialization configuration", SubstrateOptions.reportsPath(), "class_initialization_configuration", "csv", writer -> {
                writer.println("Class or Package Name, Initialization Kind, Reasons");
                for (ClassOrPackageConfig config : allConfigs) {
                    writer.append(config.getName()).append(", ").append(config.getKind().toString()).append(", ")
                                    .append(String.join(" and ", config.getReasons())).append(System.lineSeparator());
                }
            });
        }
    }

    /**
     * Returns an init kind for {@code clazz}.
     */
    InitKind specifiedInitKindFor(Class<?> clazz) {
        return classInitializationConfiguration.lookupKind(clazz.getTypeName()).getLeft();
    }

    Boolean isStrictlyDefined(Class<?> clazz) {
        return classInitializationConfiguration.lookupKind(clazz.getTypeName()).getRight();
    }

    /**
     * Returns all classes of a single {@link InitKind}.
     */
    Set<Class<?>> classesWithKind(InitKind kind) {
        return classInitKinds.entrySet().stream()
                        .filter(e -> e.getValue() == kind)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet());
    }

    /**
     * Returns true if the provided type is initialized at image build time.
     * <p>
     * If the return value is true, then the class is also guaranteed to be initialized already.
     * This means that calling this method might trigger class initialization, i.e., execute
     * arbitrary user code.
     */
    public boolean maybeInitializeAtBuildTime(ResolvedJavaType type) {
        return maybeInitializeAtBuildTime(OriginalClassProvider.getJavaClass(type));
    }

    /**
     * Returns true if the provided type is initialized at image build time.
     * <p>
     * If the return value is true, then the class is also guaranteed to be initialized already.
     * This means that calling this method might trigger class initialization, i.e., execute
     * arbitrary user code.
     */
    public boolean maybeInitializeAtBuildTime(Class<?> clazz) {
        return computeInitKindAndMaybeInitializeClass(clazz) == InitKind.BUILD_TIME;
    }

    /**
     * Ensure class is initialized. Report class initialization errors in a user-friendly way if
     * class initialization fails.
     */
    InitKind ensureClassInitialized(Class<?> clazz, boolean allowErrors) {
        try {
            loader.watchdog.recordActivity();
            /*
             * This can run arbitrary user code, i.e., it can deadlock or get stuck in an endless
             * loop when there is a bug in the user's code. Our deadlock watchdog detects and
             * reports such cases. To make that as deterministic as possible, we record watchdog
             * activity just before and after the initialization.
             */
            Unsafe.getUnsafe().ensureClassInitialized(clazz);
            loader.watchdog.recordActivity();
            return InitKind.BUILD_TIME;
        } catch (NoClassDefFoundError ex) {
            if (allowErrors || !LinkAtBuildTimeSupport.singleton().linkAtBuildTime(clazz)) {
                return InitKind.RUN_TIME;
            } else {
                String msg = "Class initialization of " + clazz.getTypeName() + " failed. " +
                                LinkAtBuildTimeSupport.singleton().errorMessageFor(clazz) + " " +
                                instructionsToInitializeAtRuntime(clazz);
                throw UserError.abort(ex, "%s", msg);
            }
        } catch (Throwable t) {
            if (allowErrors) {
                return InitKind.RUN_TIME;
            } else {
                String msg = "Class initialization of " + clazz.getTypeName() + " failed. " +
                                instructionsToInitializeAtRuntime(clazz);

                if (t instanceof ExceptionInInitializerError) {
                    Throwable cause = t;
                    while (cause.getCause() != null) {
                        cause = cause.getCause();
                    }
                    msg = msg + " Exception thrown by the class initializer:" + System.lineSeparator() + System.lineSeparator() + cause + System.lineSeparator();
                    for (var element : cause.getStackTrace()) {
                        if (getClass().getName().equals(element.getClassName())) {
                            msg = msg + "\t(internal stack frames of the image generator are omitted)" + System.lineSeparator();
                            break;
                        }
                        msg = msg + "\tat " + element + System.lineSeparator();
                    }
                    msg = msg + System.lineSeparator();
                }

                throw UserError.abort(t, "%s", msg);
            }
        }
    }

    private static String instructionsToInitializeAtRuntime(Class<?> clazz) {
        return "Use the option " + SubstrateOptionsParser.commandArgument(ClassInitializationOptions.ClassInitialization, clazz.getTypeName(), "initialize-at-run-time", true, true) +
                        " to explicitly request initialization of this class at run time.";
    }

    @Override
    public void initializeAtRunTime(Class<?> clazz, String reason) {
        UserError.guarantee(!configurationSealed, "The class initialization configuration can be changed only before the phase analysis.");
        classInitializationConfiguration.insert(clazz.getTypeName(), InitKind.RUN_TIME, reason, true);
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
    public void initializeAtBuildTime(Class<?> aClass, String reason) {
        UserError.guarantee(!configurationSealed, "The class initialization configuration can be changed only before the phase analysis.");
        forceInitializeHosted(aClass, reason, false);
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

    static boolean isClassListedInStringOption(LocatableMultiOptionValue.Strings option, Class<?> clazz) {
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
            b.append("\tat ").append(stackTraceElement.toString()).append("\n");
        }

        return b.toString();
    }

    /**
     * Initializes the class during image building, and reports an error if the user requested to
     * delay initialization to runtime.
     */
    public void forceInitializeHosted(Class<?> clazz, String reason, boolean allowInitializationErrors) {
        if (clazz == null) {
            return;
        }
        classInitializationConfiguration.insert(clazz.getTypeName(), InitKind.BUILD_TIME, reason, true);
        InitKind initKind = ensureClassInitialized(clazz, allowInitializationErrors);
        classInitKinds.put(clazz, initKind);

        forceInitializeHosted(clazz.getSuperclass(), "super type of " + clazz.getTypeName(), allowInitializationErrors);
        if (!clazz.isInterface()) {
            /*
             * Initialization of an interface does not trigger initialization of superinterfaces.
             * Regardless whether any of the involved interfaces declare default methods.
             */
            forceInitializeInterfaces(clazz.getInterfaces(), "super type of " + clazz.getTypeName());
        }
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

    InitKind computeInitKindAndMaybeInitializeClass(Class<?> clazz) {
        return computeInitKindAndMaybeInitializeClass(clazz, true);
    }

    /**
     * Computes the class initialization kind of the provided class, all superclasses, and all
     * interfaces that the provided class depends on (i.e., interfaces implemented by the provided
     * class that declare default methods).
     * <p>
     * Also defines class initialization based on a policy of the subclass.
     */
    InitKind computeInitKindAndMaybeInitializeClass(Class<?> clazz, boolean memoize) {
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

        InitKind specifiedInitKind = specifiedInitKindFor(clazz);
        InitKind clazzResult = specifiedInitKind != null ? specifiedInitKind : InitKind.RUN_TIME;

        InitKind superResult = InitKind.BUILD_TIME;
        if (clazz.getSuperclass() != null) {
            superResult = superResult.max(computeInitKindAndMaybeInitializeClass(clazz.getSuperclass(), memoize));
        }
        superResult = superResult.max(processInterfaces(clazz, memoize));

        if (superResult == InitKind.BUILD_TIME && (Proxy.isProxyClass(clazz) || LambdaUtils.isLambdaType(metaAccess.lookupJavaType(clazz)))) {
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
            for (Class<?> iface : allInterfaces(clazz)) {
                if (specifiedInitKindFor(iface) != InitKind.BUILD_TIME) {
                    allInterfacesSpecifiedAsBuildTime = false;
                    break;
                }
            }
            if (allInterfacesSpecifiedAsBuildTime) {
                forceInitializeHosted(clazz, "proxy/lambda classes with all interfaces explicitly marked as --initialize-at-build-time are also initialized at build time", false);
                return InitKind.BUILD_TIME;
            }
        }

        InitKind result = superResult.max(clazzResult);

        if (memoize) {
            if (!(result == InitKind.RUN_TIME)) {
                result = result.max(ensureClassInitialized(clazz, false));
            }

            InitKind previous = classInitKinds.putIfAbsent(clazz, result);
            if (previous != null && previous != result) {
                throw VMError.shouldNotReachHere("Conflicting class initialization kind: " + previous + " != " + result + " for " + clazz);
            }
        }
        return result;
    }

    private InitKind processInterfaces(Class<?> clazz, boolean memoizeEager) {
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

    String reasonForClass(Class<?> clazz) {
        InitKind initKind = classInitKinds.get(clazz);
        String reason = classInitializationConfiguration.lookupReason(clazz.getTypeName());
        if (initKind == InitKind.RUN_TIME) {
            return "classes are initialized at run time by default";
        } else if (reason != null) {
            return reason;
        } else {
            throw VMError.shouldNotReachHere("Must be either proven or specified");
        }
    }

    public static EconomicSet<Class<?>> allInterfaces(Class<?> clazz) {
        EconomicSet<Class<?>> result = EconomicSet.create();
        addAllInterfaces(clazz, result);
        return result;
    }

    private static void addAllInterfaces(Class<?> clazz, EconomicSet<Class<?>> result) {
        for (var interf : clazz.getInterfaces()) {
            if (result.add(interf)) {
                addAllInterfaces(interf, result);
            }
        }
    }

    public void addForTypeReachedTracking(Class<?> clazz) {
        if (TrackTypeReachedOnInterfaces.getValue() && clazz.isInterface() && !metaAccess.lookupJavaType(clazz).declaresDefaultMethods()) {
            LogUtils.info("Detected 'typeReached' on interface type without default methods: %s", clazz.getName());
        }

        if (!isAlwaysReached(clazz)) {
            UserError.guarantee(!configurationSealed || typesRequiringReachability.contains(clazz),
                            "It is not possible to register types for reachability tracking after the analysis has started if they were not registered before analysis started. Trying to register: %s",
                            clazz.getName());
            typesRequiringReachability.add(clazz);
        }
    }

    public boolean isAlwaysReached(Class<?> jClass) {
        Set<String> systemModules = Set.of("org.graalvm.nativeimage.builder", "org.graalvm.nativeimage", "org.graalvm.nativeimage.base", "com.oracle.svm.svm_enterprise",
                        "org.graalvm.word", "jdk.internal.vm.ci", "jdk.graal.compiler", "com.oracle.graal.graal_enterprise");
        Set<String> jdkModules = Set.of("java.base", "jdk.management", "java.management", "org.graalvm.collections");

        String classModuleName = jClass.getModule().getName();
        boolean alwaysReachedModule = classModuleName != null && (systemModules.contains(classModuleName) || jdkModules.contains(classModuleName));
        return jClass.isPrimitive() ||
                        jClass.isArray() ||
                        alwaysReachedModule ||
                        alwaysReachedTypes.contains(jClass);
    }

    /**
     * If any type in the type hierarchy was marked as "type reached", we have to track
     * initialization for all its subtypes. Otherwise, marking the supertype as reached could be
     * missed when the initializer of the subtype is computed at build time.
     */
    public boolean requiresInitializationNodeForTypeReached(ResolvedJavaType type) {
        if (type == null) {
            return false;
        }
        var jClass = OriginalClassProvider.getJavaClass(type);
        if (isAlwaysReached(jClass)) {
            return false;
        }

        if (TreatAllUserSpaceTypesAsTrackedForTypeReached.getValue()) {
            return true;
        }

        if (typesRequiringReachability.contains(jClass) ||
                        requiresInitializationNodeForTypeReached(type.getSuperclass())) {
            return true;
        }

        for (ResolvedJavaType anInterface : type.getInterfaces()) {
            if (requiresInitializationNodeForTypeReached(anInterface)) {
                return true;
            }
        }
        return false;
    }
}
