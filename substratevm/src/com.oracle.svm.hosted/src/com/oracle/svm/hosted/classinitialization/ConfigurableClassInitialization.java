/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatures;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.NativeImageGenerator;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.meta.HostedType;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;
import sun.misc.Unsafe;

/**
 * The core class for deciding whether a class should be initialized during image building or class
 * initialization should be delayed to runtime.
 */
public class ConfigurableClassInitialization implements ClassInitializationSupport {

    private static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();

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
    private final Map<Class<?>, InitKind> classInitKinds = new ConcurrentHashMap<>();

    private final ImageClassLoader loader;

    /**
     * Non-null while the static analysis is running to allow reporting of class initialization
     * errors without immediately aborting image building.
     */
    private UnsupportedFeatures unsupportedFeatures;
    protected MetaAccessProvider metaAccess;

    public ConfigurableClassInitialization(MetaAccessProvider metaAccess, ImageClassLoader loader) {
        this.metaAccess = metaAccess;
        this.loader = loader;
    }

    @Override
    public void setUnsupportedFeatures(UnsupportedFeatures unsupportedFeatures) {
        this.unsupportedFeatures = unsupportedFeatures;
    }

    private InitKind computeInitKindAndMaybeInitializeClass(Class<?> clazz) {
        return computeInitKindAndMaybeInitializeClass(clazz, true);
    }

    @Override
    public InitKind specifiedInitKindFor(Class<?> clazz) {
        return classInitializationConfiguration.lookupKind(clazz.getTypeName());
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
        return computeInitKindAndMaybeInitializeClass(toAnalysisType(type).getJavaClass()) != InitKind.BUILD_TIME;
    }

    @Override
    public boolean shouldInitializeAtRuntime(Class<?> clazz) {
        return computeInitKindAndMaybeInitializeClass(clazz) != InitKind.BUILD_TIME;
    }

    @Override
    public void maybeInitializeHosted(ResolvedJavaType type) {
        computeInitKindAndMaybeInitializeClass(toAnalysisType(type).getJavaClass());
    }

    /**
     * Ensure class is initialized. Report class initialization errors in a user-friendly way if
     * class initialization fails.
     */
    private InitKind ensureClassInitialized(Class<?> clazz, boolean allowErrors) {
        try {
            UNSAFE.ensureClassInitialized(clazz);
            return InitKind.BUILD_TIME;
        } catch (NoClassDefFoundError ex) {
            if (NativeImageOptions.AllowIncompleteClasspath.getValue() || NativeImageOptions.ReportUnsupportedElementsAtRuntime.getValue()) {
                if (!allowErrors) {
                    System.out.println("Warning: class initialization of class " + clazz.getTypeName() + " failed with exception " +
                                    ex.getClass().getTypeName() + (ex.getMessage() == null ? "" : ": " + ex.getMessage()) + ". This class will be initialized at run time because option " +
                                    SubstrateOptionsParser.commandArgument(NativeImageOptions.AllowIncompleteClasspath, "+") + " or " +
                                    SubstrateOptionsParser.commandArgument(NativeImageOptions.ReportUnsupportedElementsAtRuntime, "+") + "is used for image building. " +
                                    instructionsToInitializeAtRuntime(clazz));
                }
                return InitKind.RUN_TIME;
            } else {
                return reportInitializationError(allowErrors, clazz, ex);

            }
        } catch (Throwable t) {
            return reportInitializationError(allowErrors, clazz, t);
        }
    }

    private InitKind reportInitializationError(boolean allowErrors, Class<?> clazz, Throwable t) {
        if (allowErrors) {
            return InitKind.RUN_TIME;
        } else {
            String msg = "Class initialization of " + clazz.getTypeName() + " failed. " + instructionsToInitializeAtRuntime(clazz);
            if (unsupportedFeatures != null) {
                /*
                 * Report an unsupported feature during static analysis, so that we can collect
                 * multiple error messages without aborting analysis immediately. Returning
                 * InitKind.RUN_TIME ensures that analysis can continue, even though eventually an
                 * error is reported (so no image will be created).
                 */
                unsupportedFeatures.addMessage(clazz.getTypeName(), null, msg, null, t);
                return InitKind.RUN_TIME;
            } else {
                throw UserError.abort(msg, t);
            }
        }
    }

    private static String instructionsToInitializeAtRuntime(Class<?> clazz) {
        return "Use the option " + SubstrateOptionsParser.commandArgument(ClassInitializationFeature.Options.ClassInitialization, clazz.getTypeName(), "initialize-at-run-time") +
                        " to explicitly request delayed initialization of this class.";
    }

    private static AnalysisType toAnalysisType(ResolvedJavaType type) {
        return type instanceof HostedType ? ((HostedType) type).getWrapped() : (AnalysisType) type;
    }

    @Override
    public void initializeAtRunTime(String name, String reason) {
        classInitializationConfiguration.insert(name, InitKind.RUN_TIME, reason);
        Class<?> clazz = loader.findClassByName(name, false);
        if (clazz != null) {
            initializeAtRunTime(clazz, reason);
        }
    }

    @Override
    public void initializeAtBuildTime(String name, String reason) {
        classInitializationConfiguration.insert(name, InitKind.BUILD_TIME, reason);
        Class<?> clazz = loader.findClassByName(name, false);
        if (clazz != null) {
            initializeAtBuildTime(clazz, reason);
        }
    }

    @Override
    public void rerunInitialization(String name, String reason) {
        classInitializationConfiguration.insert(name, InitKind.RERUN, reason);
        Class<?> clazz = loader.findClassByName(name, false);
        if (clazz != null) {
            rerunInitialization(clazz, reason);
        }
    }

    @Override
    public void initializeAtRunTime(Class<?> clazz, String reason) {
        classInitializationConfiguration.insert(clazz.getTypeName(), InitKind.RUN_TIME, reason);
        setKindForSubclasses(clazz, InitKind.RUN_TIME);
        checkEagerInitialization(clazz);

        if (!UNSAFE.shouldBeInitialized(clazz)) {
            throw UserError.abort("Class is already initialized, so it is too late to register delaying class initialization: " + clazz.getTypeName() + " for reason: " + reason);
        }
        /*
         * Propagate possible existing RUN_TIME registration from a superclass, so that we can check
         * for user errors below.
         */
        computeInitKindAndMaybeInitializeClass(clazz, false);

        InitKind previousKind = classInitKinds.put(clazz, InitKind.RUN_TIME);
        if (previousKind == InitKind.BUILD_TIME) {
            throw UserError.abort("Class is already initialized, so it is too late to register delaying class initialization: " + clazz.getTypeName() + " for reason: " + reason);
        } else if (previousKind == InitKind.RERUN) {
            throw UserError.abort("Class is registered both for delaying and rerunning the class initializer: " + clazz.getTypeName() + " for reason: " + reason);
        }
    }

    @Override
    public void rerunInitialization(Class<?> clazz, String reason) {
        classInitializationConfiguration.insert(clazz.getTypeName(), InitKind.RERUN, reason);
        checkEagerInitialization(clazz);

        try {
            UNSAFE.ensureClassInitialized(clazz);
        } catch (Throwable ex) {
            throw UserError.abort("Class initialization failed: " + clazz.getTypeName(), ex);
        }

        /*
         * Propagate possible existing RUN_TIME registration from a superclass, so that we can check
         * for user errors below.
         */
        computeInitKindAndMaybeInitializeClass(clazz, false);

        InitKind previousKind = classInitKinds.put(clazz, InitKind.RERUN);
        if (previousKind != null) {
            if (previousKind == InitKind.BUILD_TIME) {
                throw UserError.abort("The information that the class should be initialized during image building has already been used, " +
                                "so it is too late to register re-running the class initializer: " + clazz.getTypeName() + " for reason: " + reason);
            } else if (previousKind.isDelayed()) {
                throw UserError.abort("Class or a superclass is already registered for delaying the class initializer, " +
                                "so it is too late to register re-running the class initializer: " + clazz.getTypeName() + " for reason: " + reason);
            }
        }
    }

    @Override
    public void initializeAtBuildTime(Class<?> aClass, String reason) {
        classInitializationConfiguration.insert(aClass.getTypeName(), InitKind.BUILD_TIME, reason);
        forceInitializeHosted(aClass, reason, false);
    }

    private void setKindForSubclasses(Class<?> clazz, InitKind kind) {
        loader.findSubclasses(clazz, false).stream()
                        .filter(c -> !c.equals(clazz))
                        .filter(c -> !(c.isInterface() && !ClassInitializationFeature.declaresDefaultMethods(metaAccess.lookupJavaType(c))))
                        .forEach(c -> classInitializationConfiguration.insert(c.getTypeName(), kind, "subtype of " + clazz.getTypeName()));
    }

    @Override
    public void forceInitializeHosted(Class<?> clazz, String reason, boolean allowInitializationErrors) {
        if (clazz == null) {
            return;
        }
        classInitializationConfiguration.insert(clazz.getTypeName(), InitKind.BUILD_TIME, reason);

        InitKind initKind = ensureClassInitialized(clazz, allowInitializationErrors);
        classInitKinds.put(clazz, initKind);

        forceInitializeHosted(clazz.getSuperclass(), "super type of " + clazz.getTypeName(), allowInitializationErrors);
        forceInitializeInterfaces(clazz.getInterfaces(), "super type of " + clazz.getTypeName());
    }

    private void forceInitializeInterfaces(Class<?>[] interfaces, String reason) {
        for (Class<?> iface : interfaces) {
            if (ClassInitializationFeature.declaresDefaultMethods(metaAccess.lookupJavaType(iface))) {
                classInitializationConfiguration.insert(iface.getTypeName(), InitKind.BUILD_TIME, reason);

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
        for (Map.Entry<Class<?>, InitKind> entry : classInitKinds.entrySet()) {
            if (entry.getValue().isDelayed() && !UNSAFE.shouldBeInitialized(entry.getKey())) {
                throw UserError.abort("Class that is marked for delaying initialization to run time got initialized during image building: " + entry.getKey().getTypeName() +
                                ". Try marking this class for build-time initialization with " +
                                SubstrateOptionsParser.commandArgument(ClassInitializationFeature.Options.ClassInitialization, entry.getKey().getTypeName(), "initialize-at-build-time"));
            }
        }
        return true;
    }

    private static void checkEagerInitialization(Class<?> clazz) {
        if (clazz.isPrimitive() || clazz.isArray()) {
            throw UserError.abort("Primitive types and array classes are initialized eagerly because initialization is side-effect free. " +
                            "It is not possible (and also not useful) to register them for run time initialization: " + clazz.getTypeName());
        }
        if (clazz.isAnnotation()) {
            throw UserError.abort("Class initialization of annotation classes cannot be delayed to runtime. Culprit: " + clazz.getTypeName());
        }
    }

    @Override
    public List<ClassOrPackageConfig> getClassInitializationConfiguration() {
        return classInitializationConfiguration.allConfigs();
    }

    /**
     * Computes the class initialization kind of the provided class, all superclasses, and all
     * interfaces that the provided class depends on (i.e., interfaces implemented by the provided
     * class that declare default methods).
     *
     * Also defines class initialization based on a policy of the subclass.
     */
    private InitKind computeInitKindAndMaybeInitializeClass(Class<?> clazz, boolean memoize) {
        if (classInitKinds.containsKey(clazz)) {
            return classInitKinds.get(clazz);
        }

        /* Without doubt initialize all annotations. */
        if (clazz.isAnnotation()) {
            forceInitializeHosted(clazz, "all annotations are initialized", false);
            return InitKind.BUILD_TIME;
        }

        /* Well, and enums that got initialized while annotations are parsed. */
        if (clazz.isEnum() && !UNSAFE.shouldBeInitialized(clazz)) {
            if (memoize) {
                forceInitializeHosted(clazz, "enums referred in annotations must be initialized", false);
            }
            return InitKind.BUILD_TIME;
        }

        /* GR-14698 Lambdas get eagerly initialized in the method code. */
        if (clazz.getTypeName().contains("$$Lambda$")) {
            if (memoize) {
                forceInitializeHosted(clazz, "lambdas must be initialized", false);
            }
            return InitKind.BUILD_TIME;
        }

        InitKind result = computeInitKindForClass(clazz);

        if (clazz.getSuperclass() != null) {
            result = result.max(computeInitKindAndMaybeInitializeClass(clazz.getSuperclass(), memoize));
        }
        result = result.max(processInterfaces(clazz, memoize));

        if (memoize) {
            if (!result.isDelayed()) {
                result = result.max(ensureClassInitialized(clazz, false));
            }
            InitKind previous = classInitKinds.put(clazz, result);
            assert previous == null || previous == result : "Overwriting existing value";
        }
        return result;
    }

    private InitKind processInterfaces(Class<?> clazz, boolean memoizeEager) {
        InitKind result = computeInitKindForClass(clazz);
        for (Class<?> iface : clazz.getInterfaces()) {
            if (ClassInitializationFeature.declaresDefaultMethods(metaAccess.lookupJavaType(iface))) {
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

    private InitKind computeInitKindForClass(Class<?> clazz) {
        if (clazz.isPrimitive() || clazz.isArray()) {
            return InitKind.BUILD_TIME;
        } else if (clazz.isAnnotation()) {
            return InitKind.BUILD_TIME;
        } else if (Proxy.isProxyClass(clazz)) {
            /* Proxy classes end up as constants in heap. */
            return InitKind.BUILD_TIME;
        } else if (clazz.getTypeName().contains("$$Lambda$")) {
            /* GR-14698 Lambdas get eagerly initialized in the method code. */
            return InitKind.BUILD_TIME;
        } else if (clazz.getTypeName().contains("$$StringConcat")) {
            return InitKind.BUILD_TIME;
        } else if (specifiedInitKindFor(clazz) != null) {
            return specifiedInitKindFor(clazz);
        } else {
            ClassLoader typeClassLoader = clazz.getClassLoader();
            if (typeClassLoader == null ||
                            typeClassLoader == NativeImageGenerator.class.getClassLoader() ||
                            typeClassLoader == com.sun.crypto.provider.SunJCE.class.getClassLoader() ||
                            /* JDK 11 */
                            typeClassLoader == OptionKey.class.getClassLoader()) {
                return InitKind.BUILD_TIME;
            }
        }

        return InitKind.RUN_TIME;
    }

}
