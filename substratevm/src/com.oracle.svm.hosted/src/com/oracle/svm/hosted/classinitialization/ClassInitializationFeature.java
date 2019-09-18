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

import java.lang.reflect.Modifier;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.graal.pointsto.util.Timer;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.hub.ClassInitializationInfo;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.option.APIOption;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.ExceptionSynthesizer;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.meta.MethodPointer;
import com.oracle.svm.hosted.phases.SubstrateClassInitializationPlugin;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

@AutomaticFeature
public class ClassInitializationFeature implements Feature {

    private ClassInitializationSupport classInitializationSupport;
    private AnalysisMethod ensureInitializedMethod;
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

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        ensureInitializedMethod = ((FeatureImpl.BeforeAnalysisAccessImpl) access).getBigBang().getMetaAccess().lookupJavaMethod(SubstrateClassInitializationPlugin.ENSURE_INITIALIZED_METHOD);
    }

    /**
     * Initializes classes that can be proven safe and prints class initialization statistics.
     */
    @Override
    @SuppressWarnings("try")
    public void beforeCompilation(BeforeCompilationAccess access) {
        String imageName = ((FeatureImpl.BeforeCompilationAccessImpl) access).getUniverse().getBigBang().getHostVM().getImageName();
        try (Timer.StopTimer ignored = new Timer(imageName, "(clinit)").start()) {
            classInitializationSupport.setUnsupportedFeatures(null);

            String path = Paths.get(Paths.get(SubstrateOptions.Path.getValue()).toString(), "reports").toAbsolutePath().toString();
            assert ensureInitializedMethod != null;
            assert classInitializationSupport.checkDelayedInitialization();

            TypeInitializerGraph initGraph = new TypeInitializerGraph(universe, ensureInitializedMethod);
            initGraph.computeInitializerSafety();

            Set<AnalysisType> provenSafe = initializeSafeDelayedClasses(initGraph);

            if (Options.PrintClassInitialization.getValue()) {
                List<ClassOrPackageConfig> allConfigs = classInitializationSupport.getClassInitializationConfiguration();
                allConfigs.sort(Comparator.comparing(ClassOrPackageConfig::getName));
                ReportUtils.report("initializer configuration", path, "initializer_configuration", "txt", writer -> {
                    for (ClassOrPackageConfig config : allConfigs) {
                        writer.append(config.getName()).append(" -> ").append(config.getKind().toString()).append(" reasons: ")
                                        .append(String.join(" and ", config.getReasons())).append(System.lineSeparator());
                    }
                });
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
                        .filter(t -> classInitializationSupport.specifiedInitKindFor(t) == null)
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
        ClassInitializationInfo info = null;
        if (classInitializationSupport.shouldInitializeAtRuntime(type)) {
            assert !type.isInitialized();
            AnalysisMethod classInitializer = type.getClassInitializer();
            if (type.isLinked()) {
                if (classInitializer != null) {
                    assert classInitializer.getCode() != null;
                    access.registerAsCompiled(classInitializer);
                }
                info = new ClassInitializationInfo(MethodPointer.factory(classInitializer));
            } else {
                try {
                    /*
                     * Workaround to force linking the type which is not provided by the JVMCI API.
                     * This throws verification errors even if linking was attempted and had failed
                     * beforehand
                     */
                    type.getDeclaredConstructors();
                    type.getDeclaredMethods();
                } catch (VerifyError e) {
                    /* Synthesize a VerifyError to be thrown at run time. */
                    AnalysisMethod throwVerifyError = access.getMetaAccess().lookupJavaMethod(ExceptionSynthesizer.throwVerifyErrorMethod);
                    access.registerAsCompiled(throwVerifyError);
                    info = new ClassInitializationInfo(MethodPointer.factory(throwVerifyError));
                } catch (Throwable t) {
                    // silently ignore other errors
                }

                if (info == null) {
                    /*
                     * The type failed to link due to verification issues triggered by missing
                     * types.
                     */
                    assert classInitializer == null || classInitializer.getCode() == null;
                    info = ClassInitializationInfo.FAILED_INFO_SINGLETON;
                }
            }
        } else {
            assert type.isInitialized();
            info = ClassInitializationInfo.INITIALIZED_INFO_SINGLETON;
        }

        hub.setClassInitializationInfo(info, hasDefaultMethods(type), declaresDefaultMethods(type));
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

    static boolean declaresDefaultMethods(ResolvedJavaType type) {
        if (!type.isInterface()) {
            /* Only interfaces can declare default methods. */
            return false;
        }
        /*
         * We call getDeclaredMethods() directly on the wrapped type. We avoid calling it on the
         * AnalysisType because it resolves all the methods in the AnalysisUniverse.
         */
        for (ResolvedJavaMethod method : Inflation.toWrappedType(type).getDeclaredMethods()) {
            if (method.isDefault()) {
                assert !Modifier.isStatic(method.getModifiers()) : "Default method that is static?";
                return true;
            }
        }
        return false;
    }

}
