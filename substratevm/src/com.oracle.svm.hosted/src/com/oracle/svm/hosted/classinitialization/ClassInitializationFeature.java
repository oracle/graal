/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.svm.hosted.classinitialization.InitKind.RUN_TIME;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.graalvm.nativeimage.impl.clinit.ClassInitializationTracking;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.graal.pointsto.util.Timer;
import com.oracle.graal.pointsto.util.TimerCollection;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.classinitialization.EnsureClassInitializedSnippets;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.option.OptionOrigin;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.FeatureImpl.AfterAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.util.LogUtils;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.java.LambdaUtils;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;

@AutomaticallyRegisteredFeature
public class ClassInitializationFeature implements InternalFeature {
    private static final String NATIVE_IMAGE_CLASS_REASON = "Native Image classes are always initialized at build time";

    private ClassInitializationSupport classInitializationSupport;

    public static void processClassInitializationOptions(ClassInitializationSupport initializationSupport) {
        initializeNativeImagePackagesAtBuildTime(initializationSupport);
        ClassInitializationOptions.ClassInitialization.getValue().getValuesWithOrigins().forEach(entry -> {
            for (String optionValue : entry.value().split(",")) {
                processClassInitializationOption(initializationSupport, optionValue, entry.origin());
            }
        });
    }

    private static void processClassInitializationOption(ClassInitializationSupport initializationSupport, String optionValue, OptionOrigin origin) {
        boolean initializeAtRunTime;
        if (optionValue.endsWith(ClassInitializationOptions.SUFFIX_BUILD_TIME)) {
            initializeAtRunTime = false;
        } else if (optionValue.endsWith(ClassInitializationOptions.SUFFIX_RUN_TIME)) {
            initializeAtRunTime = true;
        } else if (optionValue.endsWith(ClassInitializationOptions.SEPARATOR + "rerun")) {
            /*
             * There is no more difference between initializing a class at run-time and re-running
             * the class initializer at run time. But we still want to support it on the command
             * line, in order to not break backward compatibility.
             */
            LogUtils.warning("Re-running class initializer is deprecated. It is equivalent with registering the class for initialization at run time. Found: %s (from %s)", optionValue, origin);
            initializeAtRunTime = true;
        } else {
            throw UserError.abort("Element in class initialization configuration must end in %s, or %s. Found: %s (from %s)",
                            ClassInitializationOptions.SUFFIX_BUILD_TIME, ClassInitializationOptions.SUFFIX_RUN_TIME, optionValue, origin);
        }

        String name = optionValue.substring(0, optionValue.lastIndexOf(ClassInitializationOptions.SEPARATOR));
        String reason = "from " + origin + " with '" + name + "'";
        if (initializeAtRunTime) {
            initializationSupport.initializeAtRunTime(name, reason);
        } else {
            if (name.equals("") && !origin.commandLineLike()) {
                String msg = "--initialize-at-build-time without arguments is not allowed." + System.lineSeparator() +
                                "Origin of the option: " + origin + System.lineSeparator() +
                                "The reason for deprecation is that --initalize-at-build-time does not compose, i.e., a single library can make assumptions that the whole classpath can be safely initialized at build time;" +
                                " that assumption is often incorrect.";
                if (ClassInitializationOptions.AllowDeprecatedInitializeAllClassesAtBuildTime.getValue()) {
                    LogUtils.warning(msg);
                } else {
                    throw UserError.abort("%s%nAs a workaround, %s allows turning this error into a warning. Note that this option is deprecated and will be removed in a future version.",
                                    msg, SubstrateOptionsParser.commandArgument(ClassInitializationOptions.AllowDeprecatedInitializeAllClassesAtBuildTime, "+"));
                }
            }
            initializationSupport.initializeAtBuildTime(name, reason);
        }
    }

    private static void initializeNativeImagePackagesAtBuildTime(ClassInitializationSupport initializationSupport) {
        initializationSupport.initializeAtBuildTime("com.oracle.svm", NATIVE_IMAGE_CLASS_REASON);
        initializationSupport.initializeAtBuildTime("com.oracle.graal", NATIVE_IMAGE_CLASS_REASON);
        initializationSupport.initializeAtBuildTime("com.oracle.objectfile", NATIVE_IMAGE_CLASS_REASON);

        initializationSupport.initializeAtBuildTime("org.graalvm.collections", NATIVE_IMAGE_CLASS_REASON);
        initializationSupport.initializeAtBuildTime("jdk.graal.compiler", NATIVE_IMAGE_CLASS_REASON);
        initializationSupport.initializeAtBuildTime("org.graalvm.nativeimage.libgraal", NATIVE_IMAGE_CLASS_REASON);
        initializationSupport.initializeAtBuildTime("org.graalvm.word", NATIVE_IMAGE_CLASS_REASON);
        initializationSupport.initializeAtBuildTime("org.graalvm.nativeimage", NATIVE_IMAGE_CLASS_REASON);
        initializationSupport.initializeAtBuildTime("org.graalvm.nativebridge", NATIVE_IMAGE_CLASS_REASON);
        initializationSupport.initializeAtBuildTime("org.graalvm.home", NATIVE_IMAGE_CLASS_REASON);
        initializationSupport.initializeAtBuildTime("org.graalvm.polyglot", NATIVE_IMAGE_CLASS_REASON);
        initializationSupport.initializeAtBuildTime("org.graalvm.options", NATIVE_IMAGE_CLASS_REASON);
        initializationSupport.initializeAtBuildTime("org.graalvm.jniutils", NATIVE_IMAGE_CLASS_REASON);
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        FeatureImpl.DuringSetupAccessImpl access = (FeatureImpl.DuringSetupAccessImpl) a;
        classInitializationSupport = access.getHostVM().getClassInitializationSupport();
    }

    @SuppressWarnings("unused")
    public void checkImageHeapInstance(Object obj) {
        /*
         * Note that initializeAtBuildTime also memoizes the class as InitKind.BUILD_TIME, which
         * means that the user cannot later manually register it as RERUN or RUN_TIME.
         */
        if (obj != null && !classInitializationSupport.maybeInitializeAtBuildTime(obj.getClass())) {
            String typeName = obj.getClass().getTypeName();
            String proxyLambdaInterfaceCSV = null;
            String proxyLambdaInterfaceList = null;
            boolean proxyOrLambda = ClassInitializationSupport.isProxyOrLambda(obj);
            if (proxyOrLambda) {
                proxyLambdaInterfaceCSV = StreamSupport.stream(ClassInitializationSupport.allInterfaces(obj.getClass()).spliterator(), false)
                                .map(Class::getTypeName)
                                .collect(Collectors.joining(","));
                proxyLambdaInterfaceList = "[" + proxyLambdaInterfaceCSV.replaceAll(",", ", ") + "]";
            }

            String msg = """
                            An object of type '%s' was found in the image heap. This type, however, is marked for initialization at image run time for the following reason: %s.
                            This is not allowed for correctness reasons: All objects that are stored in the image heap must be initialized at build time.

                            You now have two options to resolve this:

                            1) If it is intended that objects of type '%s' are persisted in the image heap, add %sto the native-image arguments.\
                             Note that initializing new types can store additional objects to the heap. It is advised to check the static fields of %s to see if they are safe for build-time initialization,\
                              and that they do not contain any sensitive data that should not become part of the image.

                            """
                            .replaceAll("\n", System.lineSeparator()).formatted(
                                            typeName,
                                            classInitializationSupport.reasonForClass(obj.getClass()),
                                            typeName,
                                            SubstrateOptionsParser.commandArgument(ClassInitializationOptions.ClassInitialization, proxyOrLambda ? proxyLambdaInterfaceCSV : typeName,
                                                            "initialize-at-build-time", true, true),
                                            proxyOrLambda ? proxyLambdaInterfaceList : "'" + typeName + "'");

            msg += classInitializationSupport.objectInstantiationTraceMessage(obj, "2) ", culprit -> {
                if (culprit == null) {
                    return "If it is not intended that objects of type '" + typeName + "' are persisted in the image heap, examine the stack trace and use " +
                                    SubstrateOptionsParser.commandArgument(ClassInitializationOptions.ClassInitialization, "<culprit>", "initialize-at-run-time", true, true) +
                                    "to prevent instantiation of this object." + System.lineSeparator();
                } else {
                    return "If it is not intended that objects of type '" + typeName + "' are persisted in the image heap, examine the stack trace and use " +
                                    SubstrateOptionsParser.commandArgument(ClassInitializationOptions.ClassInitialization, culprit, "initialize-at-run-time", true, true) +
                                    "to prevent instantiation of the culprit object.";
                }
            });

            msg += System.lineSeparator();
            msg += """
                            If you are seeing this message after upgrading to a new GraalVM release, this means that some objects ended up in the image heap without their type being marked with --initialize-at-build-time.
                            To fix this, include %s in your configuration. If the classes do not originate from your code, it is advised to update all library or framework dependencies to the latest version before addressing this error.
                            """
                            .replaceAll("\n", System.lineSeparator())
                            .formatted(SubstrateOptionsParser.commandArgument(ClassInitializationOptions.ClassInitialization, proxyOrLambda ? proxyLambdaInterfaceCSV : typeName,
                                            "initialize-at-build-time", true, false));

            msg += System.lineSeparator() + "The following detailed trace displays from which field in the code the object was reached.";
            throw new UnsupportedFeatureException(msg);
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        BeforeAnalysisAccessImpl access = (BeforeAnalysisAccessImpl) a;
        for (SnippetRuntime.SubstrateForeignCallDescriptor descriptor : EnsureClassInitializedSnippets.FOREIGN_CALLS) {
            access.getBigBang().addRootMethod((AnalysisMethod) descriptor.findMethod(access.getMetaAccess()), true,
                            "Class initialization foreign call, registered in " + ClassInitializationFeature.class);
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

    /**
     * Initializes classes that can be proven safe and prints class initialization statistics.
     */
    @Override
    @SuppressWarnings("try")
    public void afterAnalysis(AfterAnalysisAccess a) {
        AfterAnalysisAccessImpl access = (AfterAnalysisAccessImpl) a;
        try (Timer.StopTimer ignored = TimerCollection.createTimerAndStart(TimerCollection.Registry.CLINIT)) {

            if (ClassInitializationOptions.PrintClassInitialization.getValue()) {
                reportClassInitializationInfo(access, SubstrateOptions.reportsPath());
            }
            if (SubstrateOptions.TraceClassInitialization.hasBeenSet()) {
                reportTrackedClassInitializationTraces(SubstrateOptions.reportsPath());
            }

            if (ClassInitializationOptions.AssertInitializationSpecifiedForAllClasses.getValue()) {
                /*
                 * This option enables a check that all application classes have an explicitly
                 * specified initialization status. This is useful to ensure that most classes (all
                 * classes for which it is feasible) are marked as "initialize at image build time"
                 * to avoid the overhead of class initialization checks at run time.
                 *
                 * We exclude JDK classes from the check: the application should not interfere with
                 * the class initialization status of the JDK because the application cannot know
                 * which JDK classes are safe for initialization at image build time.
                 */
                List<String> unspecifiedClasses = classInitializationSupport.classesWithKind(RUN_TIME).stream()
                                .filter(c -> c.getClassLoader() != null && c.getClassLoader() != ClassLoader.getPlatformClassLoader())
                                .filter(c -> classInitializationSupport.specifiedInitKindFor(c) == null)
                                .map(Class::getTypeName)
                                .filter(name -> !name.contains(LambdaUtils.LAMBDA_CLASS_NAME_SUBSTRING))
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
    private void reportClassInitializationInfo(AfterAnalysisAccessImpl access, String path) {
        ReportUtils.report("class initialization report", path, "class_initialization_report", "csv", writer -> {
            writer.println("Class Name, Initialization Kind, Reason for Initialization");
            reportKind(access, writer, BUILD_TIME);
            reportKind(access, writer, RUN_TIME);
        });
    }

    private void reportKind(AfterAnalysisAccessImpl access, PrintWriter writer, InitKind kind) {
        List<Class<?>> allClasses = new ArrayList<>(classInitializationSupport.classesWithKind(kind));
        allClasses.sort(Comparator.comparing(Class::getTypeName));
        allClasses.forEach(clazz -> {
            writer.print(clazz.getTypeName() + ", ");
            boolean simulated = false;
            if (kind != BUILD_TIME) {
                Optional<AnalysisType> type = access.getMetaAccess().optionalLookupJavaType(clazz);
                if (type.isPresent()) {
                    simulated = SimulateClassInitializerSupport.singleton().isSimulatedOrInitializedAtBuildTime(type.get());
                }
            }
            if (simulated) {
                writer.print("SIMULATED, ");
            } else {
                writer.print(kind + ", ");
            }
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
                                writer.println(ClassInitializationSupport.getTraceString(v));
                                writer.println();
                            }));
        }
    }
}
