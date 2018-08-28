/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.RuntimeClassInitialization;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatures;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.UnsafeAccess;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.hub.ClassInitializationInfo;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.option.APIOption;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl.AfterImageWriteAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.MethodPointer;

import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This class contains the hosted code to support initialization of select classes at runtime. It
 * handles the registration (implementing the functionality of the
 * {@link RuntimeClassInitialization} API) and prepares the {@link ClassInitializationInfo} objects
 * that are used at runtime to do the initialization.
 */
@AutomaticFeature
public final class ClassInitializationFeature implements Feature, RuntimeClassInitializationSupport {

    public static class Options {
        @APIOption(name = "delay-class-initialization-to-runtime")//
        @Option(help = "A comma-separate list of classes (and implicitly all of their subclasses) that are initialized at runtime and not during image building", type = OptionType.User)//
        public static final HostedOptionKey<String> DelayClassInitialization = new HostedOptionKey<>("");

        @APIOption(name = "rerun-class-initialization-at-runtime") //
        @Option(help = "A comma-separate list of classes (and implicitly all of their subclasses) that are initialized both at runtime and during image building", type = OptionType.User)//
        public static final HostedOptionKey<String> RerunClassInitialization = new HostedOptionKey<>("");
    }

    /**
     * The initialization kind for a class. The order of the enum values matters, {@link #max}
     * depends on it.
     */
    enum InitKind {
        /** Class is initialized during image building, so it is already initialized at runtime. */
        EAGER,
        /** Class is initialized both at runtime and during image building. */
        RERUN,
        /** Class is initialized at runtime and not during image building. */
        DELAY;

        InitKind max(InitKind other) {
            return this.ordinal() > other.ordinal() ? this : other;
        }
    }

    /**
     * The initialization kind for all classes seen during image building. Classes are inserted into
     * this map when 1) they are registered explicitly by the user as {@link InitKind#RERUN} or
     * {@link InitKind#DELAY}, or 2) the first time the information was queried and used during
     * image building (fixing the state to {@link InitKind#EAGER}).
     */
    private final Map<Class<?>, InitKind> classInitKinds = new ConcurrentHashMap<>();

    /**
     * Non-null while the static analysis is running to allow reporting of class initialization
     * errors without immediately aborting image building.
     */
    private UnsupportedFeatures unsupportedFeatures;

    private static ClassInitializationFeature singleton() {
        return (ClassInitializationFeature) ImageSingletons.lookup(RuntimeClassInitializationSupport.class);
    }

    /**
     * Returns true if the provided class should be initialized at runtime, i.e., has
     * {@link InitKind#RERUN} or {@link InitKind#DELAY}.
     */
    public static boolean shouldInitializeAtRuntime(ResolvedJavaType type) {
        AnalysisType aType = toAnalysisType(type);
        return singleton().computeInitKindAndMaybeInitializeClass(aType.getJavaClass()) != InitKind.EAGER;
    }

    /**
     * Initializes the class during image building, unless initialization must be delayed to
     * runtime.
     */
    public static void maybeInitializeHosted(ResolvedJavaType type) {
        singleton().computeInitKindAndMaybeInitializeClass(toAnalysisType(type).getJavaClass());
    }

    /**
     * Initializes the class during image building, and reports an error if the user requested to
     * delay initialization to runtime.
     */
    public static void forceInitializeHosted(ResolvedJavaType type) {
        forceInitializeHosted(toAnalysisType(type).getJavaClass());
    }

    /**
     * Initializes the class during image building, and reports an error if the user requested to
     * delay initialization to runtime.
     */
    public static void forceInitializeHosted(Class<?> clazz) {
        InitKind initKind = singleton().computeInitKindAndMaybeInitializeClass(clazz);
        if (initKind == InitKind.DELAY) {
            throw UserError.abort("Cannot delay running the class initializer because class must be initialized for internal purposes: " + clazz.getTypeName());
        }
    }

    private static AnalysisType toAnalysisType(ResolvedJavaType type) {
        return type instanceof HostedType ? ((HostedType) type).getWrapped() : (AnalysisType) type;
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(RuntimeClassInitializationSupport.class, this);

        processOption(access, Options.DelayClassInitialization, this::delayClassInitialization);
        processOption(access, Options.RerunClassInitialization, this::rerunClassInitialization);
    }

    private static void processOption(AfterRegistrationAccess access, HostedOptionKey<String> option, Consumer<Class<?>[]> handler) {
        for (String className : option.getValue().split(",")) {
            if (className.length() > 0) {
                Class<?> clazz = access.findClassByName(className);
                if (clazz == null) {
                    throw UserError.abort("Could not find class " + className +
                                    " that is provided by the option " + SubstrateOptionsParser.commandArgument(option, className));
                }
                handler.accept(new Class<?>[]{clazz});
            }
        }
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        DuringSetupAccessImpl access = (DuringSetupAccessImpl) a;

        unsupportedFeatures = access.getBigBang().getUnsupportedFeatures();
        access.registerObjectReplacer(this::checkImageHeapInstance);
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess a) {
        DuringAnalysisAccessImpl access = (DuringAnalysisAccessImpl) a;

        /*
         * Check early and often during static analysis if any class that must not have been
         * initialized during image building got initialized. We want to fail as early as possible,
         * even though we cannot pinpoint the exact time and reason why initialization happened.
         */
        checkDelayedInitialization(access.getUniverse());

        for (AnalysisType type : access.getUniverse().getTypes()) {
            if (type.isInTypeCheck() || type.isInstantiated()) {
                DynamicHub hub = access.getHostVM().dynamicHub(type);
                if (hub.getClassInitializationInfo() == null) {
                    buildClassInitializationInfo(access, type, hub);
                    access.requireAnalysisIteration();
                }
            }
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        unsupportedFeatures = null;
    }

    @Override
    public void afterImageWrite(AfterImageWriteAccess a) {
        AfterImageWriteAccessImpl access = (AfterImageWriteAccessImpl) a;

        /*
         * This is the final time to check if any class that must not have been initialized during
         * image building got initialized.
         */
        checkDelayedInitialization(access.getUniverse().getBigBang().getUniverse());
    }

    private Object checkImageHeapInstance(Object obj) {
        /*
         * Note that computeInitKind also memoizes the class as InitKind.EAGER, which means that the
         * user cannot later manually register it as RERUN or DELAY.
         */
        if (obj != null && computeInitKindAndMaybeInitializeClass(obj.getClass()) != InitKind.EAGER) {
            throw new UnsupportedFeatureException("No instances are allowed in the image heap for a class that is initialized or reinitialzied at image runtime: " + obj.getClass().getTypeName());
        }
        return obj;
    }

    private void checkDelayedInitialization(AnalysisUniverse universe) {
        for (AnalysisType type : universe.getTypes()) {
            if (type.isInTypeCheck() || type.isInstantiated()) {
                if (computeInitKindAndMaybeInitializeClass(type.getJavaClass()) == InitKind.DELAY && !UnsafeAccess.UNSAFE.shouldBeInitialized(type.getJavaClass())) {
                    throw UserError.abort("Class that is marked for delaying initialzation to runtime got initialized during image building: " + type.toJavaName(true));
                }
            }
        }
    }

    private static ClassInitializationInfo buildClassInitializationInfo(DuringAnalysisAccessImpl access, AnalysisType type, DynamicHub hub) {
        ClassInitializationInfo info;
        if (shouldInitializeAtRuntime(type)) {
            AnalysisMethod classInitializer = type.getClassInitializer();
            if (classInitializer != null) {
                access.registerAsCompiled(classInitializer);
            }
            info = new ClassInitializationInfo(MethodPointer.factory(classInitializer));

        } else {
            info = ClassInitializationInfo.INITIALIZED_INFO_SINGLETON;
        }

        hub.setClassInitializationInfo(info, hasDefaultMethods(type), declaresDefaultMethods(type));
        return info;
    }

    private static boolean hasDefaultMethods(ResolvedJavaType type) {
        if (!type.isInterface() && type.getSuperclass() != null && hasDefaultMethods(type.getSuperclass())) {
            return true;
        }
        for (ResolvedJavaType iface : type.getInterfaces()) {
            if (hasDefaultMethods(iface)) {
                return true;
            }
        }
        return declaresDefaultMethods(type);
    }

    private static boolean declaresDefaultMethods(ResolvedJavaType type) {
        return declaresDefaultMethods(toAnalysisType(type).getJavaClass());
    }

    private static boolean declaresDefaultMethods(Class<?> clazz) {
        if (!clazz.isInterface()) {
            /* Only interfaces can declare default methods. */
            return false;
        }
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isDefault()) {
                assert !Modifier.isStatic(method.getModifiers()) : "Default method that is static?";
                return true;
            }
        }
        return false;
    }

    private InitKind computeInitKindAndMaybeInitializeClass(Class<?> clazz) {
        return computeInitKindAndMaybeInitializeClass(clazz, true);
    }

    /**
     * Computes the class initialization kind of the provided class, all superclasses, and all
     * interfaces that the provided class depends on (i.e., interfaces implemented by the provided
     * class that declare default methods).
     *
     * Also triggers class initialization unless class initialization is delayed to runtime.
     */
    private InitKind computeInitKindAndMaybeInitializeClass(Class<?> clazz, boolean memoizeEager) {
        InitKind result = classInitKinds.get(clazz);
        if (result != null) {
            return result;
        }

        result = InitKind.EAGER;
        if (clazz.getSuperclass() != null) {
            result = result.max(computeInitKindAndMaybeInitializeClass(clazz.getSuperclass(), memoizeEager));
        }
        result = result.max(processInterfaces(clazz, memoizeEager));

        if (result != InitKind.EAGER || memoizeEager) {
            if (result != InitKind.DELAY) {
                result = result.max(ensureClassInitialized(clazz));
            }

            InitKind previous = classInitKinds.put(clazz, result);
            assert previous == null || previous == result : "Overwriting existing value";
        }
        return result;
    }

    private InitKind processInterfaces(Class<?> clazz, boolean memoizeEager) {
        InitKind result = InitKind.EAGER;
        for (Class<?> iface : clazz.getInterfaces()) {
            if (declaresDefaultMethods(iface)) {
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

    /**
     * Ensure class is initialized. Report class initialization errors in a user-friendly way if
     * class initialization fails.
     */
    private InitKind ensureClassInitialized(Class<?> clazz) {
        try {
            UnsafeAccess.UNSAFE.ensureClassInitialized(clazz);
            return InitKind.EAGER;

        } catch (Throwable ex) {
            if (NativeImageOptions.ReportUnsupportedElementsAtRuntime.getValue()) {
                System.out.println("Warning: class initialization of class " + clazz.getTypeName() + " failed with exception " +
                                ex.getClass().getTypeName() + (ex.getMessage() == null ? "" : ": " + ex.getMessage()) + ". This class will be initialized at runtime because option " +
                                SubstrateOptionsParser.commandArgument(NativeImageOptions.ReportUnsupportedElementsAtRuntime, "+") + " is used for image building. " +
                                "Use the option " + SubstrateOptionsParser.commandArgument(Options.DelayClassInitialization, clazz.getTypeName()) +
                                " to explicitly request delayed initialization of this class.");

            } else {
                String msg = "Class initialization failed: " + clazz.getTypeName();
                if (unsupportedFeatures != null) {
                    /*
                     * Report an unsupported feature during static analysis, so that we can collect
                     * multiple error messages without aborting analysis immediately. Returning
                     * InitKind.Delay ensures that analysis can continue, even though eventually an
                     * error is reported (so no image will be created).
                     */
                    unsupportedFeatures.addMessage(clazz.getTypeName(), null, msg, null, ex);
                } else {
                    /* Fail immediately if we are before or after static analysis. */
                    throw UserError.abort(msg, ex);
                }
            }
            return InitKind.DELAY;
        }
    }

    @Override
    public void delayClassInitialization(Class<?>[] classes) {
        for (Class<?> clazz : classes) {
            checkEagerInitialization(clazz);

            if (!UnsafeAccess.UNSAFE.shouldBeInitialized(clazz)) {
                throw UserError.abort("Class is already initialized, so it is too late to register delaying class initialization: " + clazz.getTypeName());
            }

            /*
             * Propagate possible existing RERUN registration from a superclass, so that we can
             * check for user errors below.
             */
            computeInitKindAndMaybeInitializeClass(clazz, false);

            InitKind previousKind = classInitKinds.put(clazz, InitKind.DELAY);

            if (previousKind == InitKind.EAGER) {
                throw UserError.abort("Class is already initialized, so it is too late to register delaying class initialization: " + clazz.getTypeName());

            } else if (previousKind == InitKind.RERUN) {
                throw UserError.abort("Class is registered both for delaying and rerunning the class initializer: " + clazz.getTypeName());
            }
        }
    }

    @Override
    public void rerunClassInitialization(Class<?>[] classes) {
        for (Class<?> clazz : classes) {
            checkEagerInitialization(clazz);

            try {
                UnsafeAccess.UNSAFE.ensureClassInitialized(clazz);
            } catch (Throwable ex) {
                throw UserError.abort("Class initialization failed: " + clazz.getTypeName(), ex);
            }

            /*
             * Propagate possible existing DELAY registration from a superclass, so that we can
             * check for user errors below.
             */
            computeInitKindAndMaybeInitializeClass(clazz, false);

            InitKind previousKind = classInitKinds.put(clazz, InitKind.RERUN);

            if (previousKind == InitKind.EAGER) {
                throw UserError.abort("The information that the class should be initialized during image building has already been used, " +
                                "so it is too late to register re-running the class initializer: " + clazz.getTypeName());
            } else if (previousKind == InitKind.DELAY) {
                throw UserError.abort("Class or a superclass is already registered for delaying the class initializer, " +
                                "so it is too late to register re-running the class initializer: " + clazz.getTypeName());
            }
        }
    }

    private static void checkEagerInitialization(Class<?> clazz) {
        if (clazz.isPrimitive() || clazz.isArray()) {
            throw UserError.abort("Primitive types and array classes are initialized eagerly because initialization is side-effect free. " +
                            "It is not possible (and also not useful) to register them for runtime initialization: " + clazz.getTypeName());
        }
    }
}
