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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatures;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.meta.HostedType;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;
import sun.misc.Unsafe;

public abstract class CommonClassInitializationSupport implements ClassInitializationSupport {

    static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();

    /**
     * The initialization kind for all classes seen during image building. Classes are inserted into
     * this map when 1) they are registered explicitly by the user as
     * {@link ClassInitializationSupport.InitKind#RERUN} or
     * {@link ClassInitializationSupport.InitKind#MUST_DELAY}, or 2) the first time the information
     * was queried and used during image building.
     */
    final Map<Class<?>, InitKind> classInitKinds = new ConcurrentHashMap<>();

    /**
     * Non-null while the static analysis is running to allow reporting of class initialization
     * errors without immediately aborting image building.
     */
    private UnsupportedFeatures unsupportedFeatures;
    protected MetaAccessProvider metaAccess;

    CommonClassInitializationSupport(MetaAccessProvider metaAccess) {
        this.metaAccess = metaAccess;
    }

    @Override
    public void setUnsupportedFeatures(UnsupportedFeatures unsupportedFeatures) {
        this.unsupportedFeatures = unsupportedFeatures;
    }

    /**
     * Computes the class initialization kind of the provided class, all superclasses, and all
     * interfaces that the provided class depends on (i.e., interfaces implemented by the provided
     * class that declare default methods).
     *
     * Also defines class initialization based on a policy of the subclass.
     */
    abstract InitKind computeInitKindAndMaybeInitializeClass(Class<?> clazz, boolean memoize);

    InitKind computeInitKindAndMaybeInitializeClass(Class<?> clazz) {
        return computeInitKindAndMaybeInitializeClass(clazz, true);
    }

    @Override
    public InitKind initKindFor(Class<?> clazz) {
        assert classInitKinds.containsKey(clazz);
        return classInitKinds.get(clazz);
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
        return computeInitKindAndMaybeInitializeClass(toAnalysisType(type).getJavaClass()) != InitKind.EAGER;
    }

    @Override
    public boolean shouldInitializeAtRuntime(Class<?> clazz) {
        return computeInitKindAndMaybeInitializeClass(clazz) != InitKind.EAGER;
    }

    @Override
    public void maybeInitializeHosted(ResolvedJavaType type) {
        computeInitKindAndMaybeInitializeClass(toAnalysisType(type).getJavaClass());
    }

    @Override
    public void forceInitializeHosted(ResolvedJavaType type) {
        forceInitializeHosted(toAnalysisType(type).getJavaClass());
    }

    /**
     * Ensure class is initialized. Report class initialization errors in a user-friendly way if
     * class initialization fails.
     */
    InitKind ensureClassInitialized(Class<?> clazz) {
        try {
            UNSAFE.ensureClassInitialized(clazz);
            return InitKind.EAGER;
        } catch (Throwable ex) {
            if (NativeImageOptions.ReportUnsupportedElementsAtRuntime.getValue() || NativeImageOptions.AllowIncompleteClasspath.getValue()) {
                System.out.println("Warning: class initialization of class " + clazz.getTypeName() + " failed with exception " +
                                ex.getClass().getTypeName() + (ex.getMessage() == null ? "" : ": " + ex.getMessage()) + ". This class will be initialized at run time because either option " +
                                SubstrateOptionsParser.commandArgument(NativeImageOptions.ReportUnsupportedElementsAtRuntime, "+") + " or option " +
                                SubstrateOptionsParser.commandArgument(NativeImageOptions.AllowIncompleteClasspath, "+") + " is used for image building. " +
                                "Use the option " + SubstrateOptionsParser.commandArgument(ClassInitializationFeature.Options.DelayClassInitialization, clazz.getTypeName()) +
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
            return InitKind.MUST_DELAY;
        }
    }

    private static AnalysisType toAnalysisType(ResolvedJavaType type) {
        return type instanceof HostedType ? ((HostedType) type).getWrapped() : (AnalysisType) type;
    }

    @Override
    public void delayClassInitialization(Class<?>[] classes) {
        for (Class<?> clazz : classes) {
            /* make the type available */
            metaAccess.lookupJavaType(clazz);
            checkEagerInitialization(clazz);

            if (!UNSAFE.shouldBeInitialized(clazz)) {
                throw UserError.abort("Class is already initialized, so it is too late to register delaying class initialization: " + clazz.getTypeName());
            }

            if (clazz.isAnnotation()) {
                throw UserError.abort("Class initialization of annotation classes cannot be delayed to runtime. Culprit: " + clazz.getTypeName());
            }

            /*
             * Propagate possible existing DELAY registration from a superclass, so that we can
             * check for user errors below.
             */
            computeInitKindAndMaybeInitializeClass(clazz, false);

            InitKind previousKind = classInitKinds.put(clazz, InitKind.MUST_DELAY);

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
            /* make the type available */
            metaAccess.lookupJavaType(clazz);
            checkEagerInitialization(clazz);

            try {
                UNSAFE.ensureClassInitialized(clazz);
            } catch (Throwable ex) {
                throw UserError.abort("Class initialization failed: " + clazz.getTypeName(), ex);
            }

            /*
             * Propagate possible existing DELAY registration from a superclass, so that we can
             * check for user errors below.
             */
            computeInitKindAndMaybeInitializeClass(clazz, false);

            InitKind previousKind = classInitKinds.put(clazz, InitKind.RERUN);

            if (previousKind != null) {
                if (previousKind == InitKind.EAGER) {
                    throw UserError.abort("The information that the class should be initialized during image building has already been used, " +
                                    "so it is too late to register re-running the class initializer: " + clazz.getTypeName());
                } else if (previousKind.isDelayed()) {
                    throw UserError.abort("Class or a superclass is already registered for delaying the class initializer, " +
                                    "so it is too late to register re-running the class initializer: " + clazz.getTypeName());
                }
            }
        }
    }

    @Override
    public void forceInitializeHierarchy(Class<?> clazz) {
        if (clazz == null) {
            return;
        }
        if (classInitKinds.containsKey(clazz) && classInitKinds.get(clazz) == InitKind.MUST_DELAY) {
            throw UserError.abort("Class " + clazz.getTypeName() + " is being force initialized, but it is already delayed to runtime.");
        }

        ensureClassInitialized(clazz);

        classInitKinds.put(clazz, InitKind.EAGER);

        forceInitializeHierarchy(clazz.getSuperclass());
        forceInitializeInterfaces(clazz.getInterfaces());
    }

    private void forceInitializeInterfaces(Class<?>[] interfaces) {
        for (Class<?> iface : interfaces) {
            if (ClassInitializationFeature.declaresDefaultMethods(metaAccess.lookupJavaType(iface))) {
                if (classInitKinds.containsKey(iface) && classInitKinds.get(iface) == InitKind.MUST_DELAY) {
                    throw UserError.abort("Class " + iface.getTypeName() + " is being force initialized, but it is must be delayed to runtime.");
                }
                ensureClassInitialized(iface);

                classInitKinds.put(iface, InitKind.EAGER);
            }
            forceInitializeInterfaces(iface.getInterfaces());
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
                throw UserError.abort("Class that is marked for delaying initialization to run time got initialized during image building: " + entry.getKey().getTypeName());
            }
        }
        return true;
    }

    private static void checkEagerInitialization(Class<?> clazz) {
        if (clazz.isPrimitive() || clazz.isArray()) {
            throw UserError.abort("Primitive types and array classes are initialized eagerly because initialization is side-effect free. " +
                            "It is not possible (and also not useful) to register them for run time initialization: " + clazz.getTypeName());
        }
    }

    @Override
    public void eagerClassInitialization(Class<?>[] classes) {
        for (Class<?> clazz : classes) {
            /* make the type available */
            metaAccess.lookupJavaType(clazz);
            forceInitializeHierarchy(clazz);
        }
    }
}
