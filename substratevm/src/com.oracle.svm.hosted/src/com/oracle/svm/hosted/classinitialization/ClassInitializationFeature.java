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
package com.oracle.svm.hosted.classinitialization;

import static com.oracle.svm.hosted.classinitialization.InitKind.BUILD_TIME;
import static com.oracle.svm.hosted.classinitialization.InitKind.RERUN;
import static com.oracle.svm.hosted.classinitialization.InitKind.RUN_TIME;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.graal.pointsto.util.Timer;
import com.oracle.graal.pointsto.util.TimerCollection;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.classinitialization.EnsureClassInitializedSnippets;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.option.OptionOrigin;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import org.graalvm.nativeimage.impl.clinit.ClassInitializationTracking;

@AutomaticallyRegisteredFeature
public class ClassInitializationFeature implements InternalFeature {
    private static final String NATIVE_IMAGE_CLASS_REASON = "Native Image classes are always initialized at build time";

    private ClassInitializationSupport classInitializationSupport;
    private AnalysisUniverse universe;
    private AnalysisMetaAccess metaAccess;

    public static void processClassInitializationOptions(ClassInitializationSupport initializationSupport) {
        initializeNativeImagePackagesAtBuildTime(initializationSupport);
        ClassInitializationOptions.ClassInitialization.getValue().getValuesWithOrigins().forEach(entry -> {
            for (String info : entry.getLeft().split(",")) {
                boolean noMatches = Arrays.stream(InitKind.values()).noneMatch(v -> info.endsWith(v.suffix()));
                OptionOrigin origin = entry.getRight();
                if (noMatches) {
                    throw UserError.abort("Element in class initialization configuration must end in %s, %s, or %s. Found: %s (from %s)",
                                    RUN_TIME.suffix(), RERUN.suffix(), BUILD_TIME.suffix(), info, origin);
                }

                Pair<String, InitKind> elementType = InitKind.strip(info);
                elementType.getRight().stringConsumer(initializationSupport, origin).accept(elementType.getLeft());
            }
        });
    }

    private static void initializeNativeImagePackagesAtBuildTime(ClassInitializationSupport initializationSupport) {
        initializationSupport.initializeAtBuildTime("com.oracle.svm", NATIVE_IMAGE_CLASS_REASON);
        initializationSupport.initializeAtBuildTime("com.oracle.graal", NATIVE_IMAGE_CLASS_REASON);
        initializationSupport.initializeAtBuildTime("com.oracle.objectfile", NATIVE_IMAGE_CLASS_REASON);

        initializationSupport.initializeAtBuildTime("org.graalvm.collections", NATIVE_IMAGE_CLASS_REASON);
        initializationSupport.initializeAtBuildTime("org.graalvm.compiler", NATIVE_IMAGE_CLASS_REASON);
        initializationSupport.initializeAtBuildTime("org.graalvm.word", NATIVE_IMAGE_CLASS_REASON);
        initializationSupport.initializeAtBuildTime("org.graalvm.nativeimage", NATIVE_IMAGE_CLASS_REASON);
        initializationSupport.initializeAtBuildTime("org.graalvm.nativebridge", NATIVE_IMAGE_CLASS_REASON);
        initializationSupport.initializeAtBuildTime("org.graalvm.util", NATIVE_IMAGE_CLASS_REASON);
        initializationSupport.initializeAtBuildTime("org.graalvm.home", NATIVE_IMAGE_CLASS_REASON);
        initializationSupport.initializeAtBuildTime("org.graalvm.polyglot", NATIVE_IMAGE_CLASS_REASON);
        initializationSupport.initializeAtBuildTime("org.graalvm.options", NATIVE_IMAGE_CLASS_REASON);
        initializationSupport.initializeAtBuildTime("org.graalvm.graphio", NATIVE_IMAGE_CLASS_REASON);
        initializationSupport.initializeAtBuildTime("org.graalvm.jniutils", NATIVE_IMAGE_CLASS_REASON);
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        FeatureImpl.DuringSetupAccessImpl access = (FeatureImpl.DuringSetupAccessImpl) a;
        classInitializationSupport = access.getHostVM().getClassInitializationSupport();
        classInitializationSupport.setUnsupportedFeatures(access.getBigBang().getUnsupportedFeatures());
        access.registerObjectReplacer(this::checkImageHeapInstance);
        universe = ((FeatureImpl.DuringSetupAccessImpl) a).getBigBang().getUniverse();
        metaAccess = ((FeatureImpl.DuringSetupAccessImpl) a).getBigBang().getMetaAccess();
    }

    private Object checkImageHeapInstance(Object obj) {
        /*
         * Note that computeInitKind also memoizes the class as InitKind.BUILD_TIME, which means
         * that the user cannot later manually register it as RERUN or RUN_TIME.
         */
        if (obj != null && classInitializationSupport.shouldInitializeAtRuntime(obj.getClass())) {
            String msg = "No instances of " + obj.getClass().getTypeName() + " are allowed in the image heap as this class should be initialized at image runtime.";
            msg += classInitializationSupport.objectInstantiationTraceMessage(obj,
                            " To fix the issue mark " + obj.getClass().getTypeName() + " for build-time initialization with " +
                                            SubstrateOptionsParser.commandArgument(ClassInitializationOptions.ClassInitialization, obj.getClass().getTypeName(), "initialize-at-build-time") +
                                            " or use the the information from the trace to find the culprit and " +
                                            SubstrateOptionsParser.commandArgument(ClassInitializationOptions.ClassInitialization, "<culprit>", "initialize-at-run-time") +
                                            " to prevent its instantiation.\n");
            throw new UnsupportedFeatureException(msg);
        }
        return obj;
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        BeforeAnalysisAccessImpl access = (BeforeAnalysisAccessImpl) a;
        for (SnippetRuntime.SubstrateForeignCallDescriptor descriptor : EnsureClassInitializedSnippets.FOREIGN_CALLS) {
            access.getBigBang().addRootMethod((AnalysisMethod) descriptor.findMethod(access.getMetaAccess()), true);
        }
    }

    @Override
    public void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(EnsureClassInitializedSnippets.FOREIGN_CALLS);
    }

    @Override
    @SuppressWarnings("unused")
    public void registerLowerings(RuntimeConfiguration runtimeConfig, OptionValues options, Providers providers,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, boolean hosted) {
        EnsureClassInitializedSnippets.registerLowerings(options, providers, lowerings);
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        /*
         * Check early and often during static analysis if any class that must not have been
         * initialized during image building got initialized. We want to fail as early as possible,
         * even though we cannot pinpoint the exact time and reason why initialization happened.
         */
        classInitializationSupport.checkDelayedInitialization();
    }

    /**
     * Initializes classes that can be proven safe and prints class initialization statistics.
     */
    @Override
    @SuppressWarnings("try")
    public void afterAnalysis(AfterAnalysisAccess access) {
        try (Timer.StopTimer ignored = TimerCollection.createTimerAndStart(TimerCollection.Registry.CLINIT)) {
            classInitializationSupport.setUnsupportedFeatures(null);

            assert classInitializationSupport.checkDelayedInitialization();

            classInitializationSupport.doLateInitialization(universe, metaAccess);

            if (ClassInitializationOptions.PrintClassInitialization.getValue()) {
                reportClassInitializationInfo(SubstrateOptions.reportsPath());
            }
            if (SubstrateOptions.TraceClassInitialization.hasBeenSet()) {
                reportTrackedClassInitializationTraces(SubstrateOptions.reportsPath());
            }

            if (ClassInitializationOptions.AssertInitializationSpecifiedForAllClasses.getValue()) {
                List<String> unspecifiedClasses = classInitializationSupport.classesWithKind(RUN_TIME).stream()
                                .filter(c -> classInitializationSupport.specifiedInitKindFor(c) == null)
                                .map(Class::getTypeName)
                                .collect(Collectors.toList());
                if (!unspecifiedClasses.isEmpty()) {
                    System.err.println("The following classes have unspecified initialization policy:" + System.lineSeparator() + String.join(System.lineSeparator(), unspecifiedClasses));
                    UserError.abort("To fix the error either specify the initialization policy for given classes or set %s",
                                    SubstrateOptionsParser.commandArgument(ClassInitializationOptions.AssertInitializationSpecifiedForAllClasses, "-"));
                }
            }
        }
    }

    /**
     * Prints a file for every type of class initialization. Each file contains a list of classes
     * that belong to it.
     */
    private void reportClassInitializationInfo(String path) {
        ReportUtils.report("class initialization report", path, "class_initialization_report", "csv", writer -> {
            writer.println("Class Name, Initialization Kind, Reason for Initialization");
            reportKind(writer, BUILD_TIME);
            reportKind(writer, RERUN);
            reportKind(writer, RUN_TIME);
        });
    }

    private void reportKind(PrintWriter writer, InitKind kind) {
        List<Class<?>> allClasses = new ArrayList<>(classInitializationSupport.classesWithKind(kind));
        allClasses.sort(Comparator.comparing(Class::getTypeName));
        allClasses.forEach(clazz -> {
            writer.print(clazz.getTypeName() + ", ");
            writer.print(kind + ", ");
            writer.println(classInitializationSupport.reasonForClass(clazz));
        });
    }

    private static void reportTrackedClassInitializationTraces(String path) {
        Map<Class<?>, StackTraceElement[]> initializedClasses = ClassInitializationTracking.initializedClasses;
        int size = initializedClasses.size();
        if (size > 0) {
            ReportUtils.report(size + " class initialization trace(s) of class(es) traced by " + SubstrateOptions.TraceClassInitialization.getName(), path, "traced_class_initialization", "txt",
                            writer -> initializedClasses.forEach((k, v) -> {
                                writer.println(k.getName());
                                writer.println("---------------------------------------------");
                                writer.println(ProvenSafeClassInitializationSupport.getTraceString(v));
                                writer.println();
                            }));
        }
    }

    @Override
    public void afterImageWrite(AfterImageWriteAccess a) {
        /*
         * This is the final time to check if any class that must not have been initialized during
         * image building got initialized.
         */
        classInitializationSupport.checkDelayedInitialization();
    }
}
