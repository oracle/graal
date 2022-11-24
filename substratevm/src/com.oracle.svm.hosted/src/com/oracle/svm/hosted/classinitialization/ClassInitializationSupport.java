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

import static com.oracle.svm.core.SubstrateOptions.TraceObjectInstantiation;

import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;
import org.graalvm.nativeimage.impl.clinit.ClassInitializationTracking;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatures;
import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.LinkAtBuildTimeSupport;

import jdk.internal.misc.Unsafe;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * The core class for deciding whether a class should be initialized during image building or class
 * initialization should be delayed to runtime.
 */
public abstract class ClassInitializationSupport implements RuntimeClassInitializationSupport {

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

    boolean configurationSealed;

    final ImageClassLoader loader;

    /**
     * Non-null while the static analysis is running to allow reporting of class initialization
     * errors without immediately aborting image building.
     */
    UnsupportedFeatures unsupportedFeatures;
    final MetaAccessProvider metaAccess;

    public static ClassInitializationSupport create(MetaAccessProvider metaAccess, ImageClassLoader loader) {
        if (ClassInitializationOptions.UseNewExperimentalClassInitialization.getValue()) {
            System.out.println("WARNING: using new experimental class initialization strategy. Image size and peak performance are not optimized yet!");
            return new AllowAllHostedUsagesClassInitializationSupport(metaAccess, loader);
        }
        return new ProvenSafeClassInitializationSupport(metaAccess, loader);
    }

    ClassInitializationSupport(MetaAccessProvider metaAccess, ImageClassLoader loader) {
        this.metaAccess = metaAccess;
        this.loader = loader;
    }

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

    void setUnsupportedFeatures(UnsupportedFeatures unsupportedFeatures) {
        this.unsupportedFeatures = unsupportedFeatures;
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
     * Returns true if the provided type should be initialized at runtime.
     */
    public boolean shouldInitializeAtRuntime(ResolvedJavaType type) {
        return computeInitKindAndMaybeInitializeClass(getJavaClass(type)) != InitKind.BUILD_TIME;
    }

    /**
     * Returns true if the provided class should be initialized at runtime.
     */
    public boolean shouldInitializeAtRuntime(Class<?> clazz) {
        return computeInitKindAndMaybeInitializeClass(clazz) != InitKind.BUILD_TIME;
    }

    /**
     * Initializes the class during image building, unless initialization must be delayed to
     * runtime.
     */
    public void maybeInitializeHosted(ResolvedJavaType type) {
        computeInitKindAndMaybeInitializeClass(getJavaClass(type));
    }

    /**
     * Ensure class is initialized. Report class initialization errors in a user-friendly way if
     * class initialization fails.
     */
    InitKind ensureClassInitialized(Class<?> clazz, boolean allowErrors) {
        try {
            Unsafe.getUnsafe().ensureClassInitialized(clazz);
            loader.watchdog.recordActivity();
            return InitKind.BUILD_TIME;
        } catch (NoClassDefFoundError ex) {
            if (allowErrors || !LinkAtBuildTimeSupport.singleton().linkAtBuildTime(clazz)) {
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

    static boolean isClassListedInStringOption(LocatableMultiOptionValue.Strings option, Class<?> clazz) {
        return option.values().contains(clazz.getName());
    }

    private static boolean isObjectInstantiationForClassTracked(Class<?> clazz) {
        return TraceObjectInstantiation.hasBeenSet() && isClassListedInStringOption(TraceObjectInstantiation.getValue(), clazz);
    }

    public String objectInstantiationTraceMessage(Object obj, String action) {
        Map<Object, StackTraceElement[]> instantiatedObjects = ClassInitializationTracking.instantiatedObjects;

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
    public abstract void forceInitializeHosted(Class<?> clazz, String reason, boolean allowInitializationErrors);

    abstract InitKind computeInitKindAndMaybeInitializeClass(Class<?> clazz);

    abstract String reasonForClass(Class<?> clazz);

    /**
     * Check that all registered classes are here, regardless if the AnalysisType got actually
     * marked as used. Class initialization can have side effects on other classes without the class
     * being used itself, e.g., a class initializer can write a static field in another class.
     */
    abstract boolean checkDelayedInitialization();

    abstract void doLateInitialization(AnalysisUniverse universe, AnalysisMetaAccess aMetaAccess);
}
