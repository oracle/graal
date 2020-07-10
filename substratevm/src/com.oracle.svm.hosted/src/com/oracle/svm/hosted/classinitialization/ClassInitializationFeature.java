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
import static com.oracle.svm.hosted.classinitialization.InitKind.SEPARATOR;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.graal.pointsto.util.Timer;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.classinitialization.ClassInitializationInfo;
import com.oracle.svm.core.classinitialization.EnsureClassInitializedSnippets;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.option.APIOption;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.ExceptionSynthesizer;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.meta.MethodPointer;

@AutomaticFeature
public class ClassInitializationFeature implements GraalFeature {

    private ClassInitializationSupport classInitializationSupport;
    private AnalysisUniverse universe;
    private AnalysisMetaAccess metaAccess;

    public static class Options {

        private static class InitializationValueTransformer implements Function<Object, Object> {
            private final String val;

            InitializationValueTransformer(String val) {
                this.val = val;
            }

            @Override
            public Object apply(Object o) {
                String[] elements = o.toString().split(",");
                if (elements.length == 0) {
                    return SEPARATOR + val;
                }
                String[] results = new String[elements.length];
                for (int i = 0; i < elements.length; i++) {
                    results[i] = elements[i] + SEPARATOR + val;
                }
                return String.join(",", results);
            }
        }

        private static class InitializationValueDelay extends InitializationValueTransformer {
            InitializationValueDelay() {
                super(RUN_TIME.name().toLowerCase());
            }
        }

        private static class InitializationValueRerun extends InitializationValueTransformer {
            InitializationValueRerun() {
                super(RERUN.name().toLowerCase());
            }
        }

        private static class InitializationValueEager extends InitializationValueTransformer {
            InitializationValueEager() {
                super(BUILD_TIME.name().toLowerCase());
            }
        }

        @APIOption(name = "initialize-at-run-time", valueTransformer = InitializationValueDelay.class, defaultValue = "", //
                        customHelp = "A comma-separated list of packages and classes (and implicitly all of their subclasses) that must be initialized at runtime and not during image building. An empty string is currently not supported.")//
        @APIOption(name = "initialize-at-build-time", valueTransformer = InitializationValueEager.class, defaultValue = "", //
                        customHelp = "A comma-separated list of packages and classes (and implicitly all of their superclasses) that are initialized during image generation. An empty string designates all packages.")//
        @APIOption(name = "delay-class-initialization-to-runtime", valueTransformer = InitializationValueDelay.class, deprecated = "Use --initialize-at-run-time.", //
                        defaultValue = "", customHelp = "A comma-separated list of classes (and implicitly all of their subclasses) that are initialized at runtime and not during image building")//
        @APIOption(name = "rerun-class-initialization-at-runtime", valueTransformer = InitializationValueRerun.class, //
                        deprecated = "Currently there is no replacement for this option. Try using --initialize-at-run-time or use the non-API option -H:ClassInitialization directly.", //
                        defaultValue = "", customHelp = "A comma-separated list of classes (and implicitly all of their subclasses) that are initialized both at runtime and during image building") //
        @Option(help = "A comma-separated list of classes appended with their initialization strategy (':build_time', ':rerun', or ':run_time')", type = OptionType.User)//
        public static final HostedOptionKey<String[]> ClassInitialization = new HostedOptionKey<>(new String[0]);

        @Option(help = "Prints class initialization info for all classes detected by analysis.", type = OptionType.Debug)//
        public static final HostedOptionKey<Boolean> PrintClassInitialization = new HostedOptionKey<>(false);
    }

    public static void processClassInitializationOptions(ClassInitializationSupport initializationSupport) {
        initializeNativeImagePackagesAtBuildTime(initializationSupport);
        String[] initializationInfo = Options.ClassInitialization.getValue();
        for (String infos : initializationInfo) {
            for (String info : infos.split(",")) {
                boolean noMatches = Arrays.stream(InitKind.values()).noneMatch(v -> info.endsWith(v.suffix()));
                if (noMatches) {
                    throw UserError.abort(
                                    "Element in class initialization configuration must end in " + RUN_TIME.suffix() + ", " + RERUN.suffix() + ", or " + BUILD_TIME.suffix() + ". Found: " + info);
                }

                Pair<String, InitKind> elementType = InitKind.strip(info);
                elementType.getRight().stringConsumer(initializationSupport).accept(elementType.getLeft());
            }
        }
    }

    private static void initializeNativeImagePackagesAtBuildTime(ClassInitializationSupport initializationSupport) {
        initializationSupport.initializeAtBuildTime("com.oracle.svm", "Native Image classes are always initialized at build time");
        initializationSupport.initializeAtBuildTime("com.oracle.graal", "Native Image classes are always initialized at build time");

        initializationSupport.initializeAtBuildTime("org.graalvm.collections", "Native Image classes are always initialized at build time");
        initializationSupport.initializeAtBuildTime("org.graalvm.compiler", "Native Image classes are always initialized at build time");
        initializationSupport.initializeAtBuildTime("org.graalvm.word", "Native Image classes are always initialized at build time");
        initializationSupport.initializeAtBuildTime("org.graalvm.nativeimage", "Native Image classes are always initialized at build time");
        initializationSupport.initializeAtBuildTime("org.graalvm.util", "Native Image classes are always initialized at build time");
        initializationSupport.initializeAtBuildTime("org.graalvm.home", "Native Image classes are always initialized at build time");
        initializationSupport.initializeAtBuildTime("org.graalvm.polyglot", "Native Image classes are always initialized at build time");
        initializationSupport.initializeAtBuildTime("org.graalvm.options", "Native Image classes are always initialized at build time");
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
                                            SubstrateOptionsParser.commandArgument(ClassInitializationFeature.Options.ClassInitialization, obj.getClass().getTypeName(), "initialize-at-build-time") +
                                            " or use the the information from the trace to find the culprit and " +
                                            SubstrateOptionsParser.commandArgument(ClassInitializationFeature.Options.ClassInitialization, "<culprit>", "initialize-at-run-time") +
                                            " to prevent its instantiation.\n");
            throw new UnsupportedFeatureException(msg);
        }
        return obj;
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        BeforeAnalysisAccessImpl access = (BeforeAnalysisAccessImpl) a;
        for (SnippetRuntime.SubstrateForeignCallDescriptor descriptor : EnsureClassInitializedSnippets.FOREIGN_CALLS) {
            access.getBigBang().addRootMethod((AnalysisMethod) descriptor.findMethod(access.getMetaAccess()));
        }
    }

    @Override
    public void registerForeignCalls(RuntimeConfiguration runtimeConfig, Providers providers, SnippetReflectionProvider snippetReflection, SubstrateForeignCallsProvider foreignCalls, boolean hosted) {
        foreignCalls.register(providers, EnsureClassInitializedSnippets.FOREIGN_CALLS);
    }

    @Override
    @SuppressWarnings("unused")
    public void registerLowerings(RuntimeConfiguration runtimeConfig, OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers,
                    SnippetReflectionProvider snippetReflection, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, boolean hosted) {
        EnsureClassInitializedSnippets.registerLowerings(options, factories, providers, snippetReflection, lowerings);
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess a) {
        FeatureImpl.DuringAnalysisAccessImpl access = (FeatureImpl.DuringAnalysisAccessImpl) a;

        /*
         * Check early and often during static analysis if any class that must not have been
         * initialized during image building got initialized. We want to fail as early as possible,
         * even though we cannot pinpoint the exact time and reason why initialization happened.
         */
        classInitializationSupport.checkDelayedInitialization();

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

    /**
     * Initializes classes that can be proven safe and prints class initialization statistics.
     */
    @Override
    @SuppressWarnings("try")
    public void afterAnalysis(AfterAnalysisAccess access) {
        String imageName = ((FeatureImpl.AfterAnalysisAccessImpl) access).getBigBang().getHostVM().getImageName();
        try (Timer.StopTimer ignored = new Timer(imageName, "(clinit)").start()) {
            classInitializationSupport.setUnsupportedFeatures(null);

            String path = Paths.get(Paths.get(SubstrateOptions.Path.getValue()).toString(), "reports").toAbsolutePath().toString();
            assert classInitializationSupport.checkDelayedInitialization();

            TypeInitializerGraph initGraph = new TypeInitializerGraph(universe);
            initGraph.computeInitializerSafety();

            Set<AnalysisType> provenSafe = initializeSafeDelayedClasses(initGraph);

            if (Options.PrintClassInitialization.getValue()) {
                reportSafeTypeInitiazliation(universe, initGraph, path, provenSafe);
                reportMethodInitializationInfo(path);
            }
        }
    }

    private static void reportSafeTypeInitiazliation(AnalysisUniverse universe, TypeInitializerGraph initGraph, String path, Set<AnalysisType> provenSafe) {
        ReportUtils.report("initializer dependencies", path, "initializer_dependencies", "dot", writer -> {
            writer.println("digraph initializer_dependencies {");
            universe.getTypes().stream()
                            .filter(ClassInitializationFeature::isRelevantForPrinting)
                            .forEach(t -> writer.println(quote(t.toClassName()) + "[fillcolor=" + (initGraph.isUnsafe(t) ? "red" : "green") + "]"));
            universe.getTypes().stream()
                            .filter(ClassInitializationFeature::isRelevantForPrinting)
                            .forEach(t -> initGraph.getDependencies(t)
                                            .forEach(t1 -> writer.println(quote(t.toClassName()) + " -> " + quote(t1.toClassName()))));
            writer.println("}");
        });

        ReportUtils.report(provenSafe.size() + " classes that are considered as safe for build-time initialization", path, "safe_classes", "txt",
                        printWriter -> provenSafe.forEach(t -> printWriter.println(t.toClassName())));
    }

    /**
     * Prints a file for every type of class initialization. Each file contains a list of classes
     * that belong to it.
     */
    private void reportMethodInitializationInfo(String path) {
        for (InitKind kind : InitKind.values()) {
            Set<Class<?>> classes = classInitializationSupport.classesWithKind(kind);
            ReportUtils.report(classes.size() + " classes of type " + kind, path, kind.toString().toLowerCase() + "_classes", "txt",
                            writer -> classes.stream()
                                            .map(Class::getTypeName)
                                            .sorted()
                                            .forEach(writer::println));
        }
    }

    private static boolean isRelevantForPrinting(AnalysisType type) {
        return !type.isPrimitive() && !type.isArray() && type.isInTypeCheck();
    }

    private static String quote(String className) {
        return "\"" + className + "\"";
    }

    /**
     * Initializes all classes that are considered delayed by the system. Classes specified by the
     * user will not be delayed.
     */
    private Set<AnalysisType> initializeSafeDelayedClasses(TypeInitializerGraph initGraph) {
        Set<AnalysisType> provenSafe = new HashSet<>();
        classInitializationSupport.setConfigurationSealed(false);
        classInitializationSupport.classesWithKind(RUN_TIME).stream()
                        .filter(t -> metaAccess.optionalLookupJavaType(t).isPresent())
                        .filter(t -> metaAccess.lookupJavaType(t).isInTypeCheck())
                        .filter(t -> classInitializationSupport.canBeProvenSafe(t))
                        .forEach(c -> {
                            AnalysisType type = metaAccess.lookupJavaType(c);
                            if (!initGraph.isUnsafe(type)) {
                                classInitializationSupport.forceInitializeHosted(c, "proven safe to initialize", true);
                                /*
                                 * See if initialization worked--it can fail due to implicit
                                 * exceptions.
                                 */
                                if (!classInitializationSupport.shouldInitializeAtRuntime(c)) {
                                    provenSafe.add(type);
                                    ((SVMHost) universe.hostVM()).dynamicHub(type).setClassInitializationInfo(ClassInitializationInfo.INITIALIZED_INFO_SINGLETON);
                                }
                            }
                        });
        return provenSafe;
    }

    @Override
    public void afterImageWrite(AfterImageWriteAccess a) {
        /*
         * This is the final time to check if any class that must not have been initialized during
         * image building got initialized.
         */
        classInitializationSupport.checkDelayedInitialization();
    }

    private void buildClassInitializationInfo(FeatureImpl.DuringAnalysisAccessImpl access, AnalysisType type, DynamicHub hub) {
        ClassInitializationInfo info;
        if (classInitializationSupport.shouldInitializeAtRuntime(type)) {
            info = buildRuntimeInitializationInfo(access, type);
        } else {
            assert type.isInitialized();
            info = ClassInitializationInfo.INITIALIZED_INFO_SINGLETON;
        }
        hub.setClassInitializationInfo(info, type.hasDefaultMethods(), type.declaresDefaultMethods());
    }

    private static ClassInitializationInfo buildRuntimeInitializationInfo(FeatureImpl.DuringAnalysisAccessImpl access, AnalysisType type) {
        assert !type.isInitialized();
        try {
            /*
             * Check if there are any linking errors. This method throws an error even if linking
             * already failed in a previous attempt.
             */
            type.link();

        } catch (VerifyError e) {
            /* Synthesize a VerifyError to be thrown at run time. */
            AnalysisMethod throwVerifyError = access.getMetaAccess().lookupJavaMethod(ExceptionSynthesizer.throwVerifyErrorMethod);
            access.registerAsCompiled(throwVerifyError);
            return new ClassInitializationInfo(MethodPointer.factory(throwVerifyError));
        } catch (Throwable t) {
            /*
             * All other linking errors will be reported as NoClassDefFoundError when initialization
             * is attempted at run time.
             */
            return ClassInitializationInfo.FAILED_INFO_SINGLETON;
        }

        /*
         * Now we now that there are no linking errors, we can register the class initialization
         * information.
         */
        assert type.isLinked();
        AnalysisMethod classInitializer = type.getClassInitializer();
        if (classInitializer != null) {
            assert classInitializer.getCode() != null;
            access.registerAsCompiled(classInitializer);
        }
        return new ClassInitializationInfo(MethodPointer.factory(classInitializer));
    }
}
