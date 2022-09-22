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
import static com.oracle.svm.hosted.classinitialization.InitKind.RUN_TIME;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.impl.clinit.ClassInitializationTracking;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.classinitialization.ClassInitializationInfo;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.util.ReflectionUtil;

import jdk.internal.misc.Unsafe;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * The core class for deciding whether a class should be initialized during image building or class
 * initialization should be delayed to runtime.
 */
class ProvenSafeClassInitializationSupport extends ClassInitializationSupport {

    private static final Field dynamicHubClassInitializationInfoField = ReflectionUtil.lookupField(DynamicHub.class, "classInitializationInfo");

    private final EarlyClassInitializerAnalysis earlyClassInitializerAnalysis;
    private final Set<Class<?>> provenSafeEarly = ConcurrentHashMap.newKeySet();
    private Set<Class<?>> provenSafeLate = ConcurrentHashMap.newKeySet();

    ProvenSafeClassInitializationSupport(MetaAccessProvider metaAccess, ImageClassLoader loader) {
        super(metaAccess, loader);
        this.earlyClassInitializerAnalysis = new EarlyClassInitializerAnalysis(this);
    }

    @Override
    InitKind computeInitKindAndMaybeInitializeClass(Class<?> clazz) {
        return computeInitKindAndMaybeInitializeClass(clazz, true, null);
    }

    boolean canBeProvenSafe(Class<?> clazz) {
        InitKind initKind = specifiedInitKindFor(clazz);
        return initKind == null || (initKind.isRunTime() && !isStrictlyDefined(clazz));
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

    private static boolean isClassInitializationTracked(Class<?> clazz) {
        return TraceClassInitialization.hasBeenSet() && isClassListedInStringOption(TraceClassInitialization.getValue(), clazz);
    }

    private static String classInitializationErrorMessage(Class<?> clazz, String action) {
        Map<Class<?>, StackTraceElement[]> initializedClasses = ClassInitializationTracking.initializedClasses;
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
            String initializationTrace = getTraceString(initializedClasses.get(clazz));
            if (culprit != null) {
                return culprit + " caused initialization of this class with the following trace: \n" + initializationTrace;
            } else {
                return clazz.getTypeName() + " has been initialized through the following trace:\n" + initializationTrace;
            }
        } else {
            return clazz.getTypeName() + " has been initialized without the native-image initialization instrumentation and the stack trace can't be tracked. " + action;
        }
    }

    @Override
    String reasonForClass(Class<?> clazz) {
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
    boolean checkDelayedInitialization() {
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

            String traceClassInitArguments = illegalyInitialized.stream().filter(c -> !isClassInitializationTracked(c)).map(Class::getName).collect(Collectors.joining(","));
            if (!"".equals(traceClassInitArguments)) {
                detailedMessage.append("To see how the classes got initialized, use ").append(SubstrateOptionsParser.commandArgument(TraceClassInitialization, traceClassInitArguments));
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
    void doLateInitialization(AnalysisUniverse aUniverse, AnalysisMetaAccess aMetaAccess) {
        TypeInitializerGraph initGraph = new TypeInitializerGraph(this, aUniverse);
        initGraph.computeInitializerSafety();
        provenSafeLate = initializeSafeDelayedClasses(initGraph, aUniverse, aMetaAccess);
        if (ClassInitializationOptions.PrintClassInitialization.getValue()) {
            reportInitializerDependencies(aUniverse, initGraph, SubstrateOptions.reportsPath());
        }
    }

    private static void reportInitializerDependencies(AnalysisUniverse universe, TypeInitializerGraph initGraph, String path) {
        ReportUtils.report("class initialization dependencies", path, "class_initialization_dependencies", "dot", writer -> {
            writer.println("digraph class_initializer_dependencies {");
            universe.getTypes().stream()
                            .filter(ProvenSafeClassInitializationSupport::isRelevantForPrinting)
                            .forEach(t -> writer.println(quote(t.toClassName()) + "[fillcolor=" + (initGraph.isUnsafe(t) ? "red" : "green") + "]"));
            universe.getTypes().stream()
                            .filter(ProvenSafeClassInitializationSupport::isRelevantForPrinting)
                            .forEach(t -> initGraph.getDependencies(t)
                                            .forEach(t1 -> writer.println(quote(t.toClassName()) + " -> " + quote(t1.toClassName()))));
            writer.println("}");
        });
    }

    private static boolean isRelevantForPrinting(AnalysisType type) {
        return !type.isPrimitive() && !type.isArray() && type.isReachable();
    }

    private static String quote(String className) {
        return "\"" + className + "\"";
    }

    /**
     * Initializes all classes that are considered delayed by the system. Classes specified by the
     * user will not be delayed.
     */
    private Set<Class<?>> initializeSafeDelayedClasses(TypeInitializerGraph initGraph, AnalysisUniverse aUniverse, AnalysisMetaAccess aMetaAccess) {
        Set<Class<?>> provenSafe = new HashSet<>();
        setConfigurationSealed(false);
        classesWithKind(RUN_TIME).stream()
                        .filter(t -> aMetaAccess.optionalLookupJavaType(t).isPresent())
                        .filter(t -> aMetaAccess.lookupJavaType(t).isReachable())
                        .filter(this::canBeProvenSafe)
                        .forEach(c -> {
                            AnalysisType type = aMetaAccess.lookupJavaType(c);
                            if (!initGraph.isUnsafe(type)) {
                                forceInitializeHosted(c, "proven safe to initialize", true);
                                /*
                                 * See if initialization worked--it can fail due to implicit
                                 * exceptions.
                                 */
                                if (!shouldInitializeAtRuntime(c)) {
                                    provenSafe.add(c);
                                    ClassInitializationInfo initializationInfo = type.getClassInitializer() == null ? ClassInitializationInfo.NO_INITIALIZER_INFO_SINGLETON
                                                    : ClassInitializationInfo.INITIALIZED_INFO_SINGLETON;
                                    DynamicHub hub = ((SVMHost) aUniverse.hostVM()).dynamicHub(type);
                                    hub.setClassInitializationInfo(initializationInfo);
                                    aUniverse.getHeapScanner().rescanField(hub, dynamicHubClassInitializationInfoField);
                                }
                            }
                        });
        return provenSafe;
    }
}
