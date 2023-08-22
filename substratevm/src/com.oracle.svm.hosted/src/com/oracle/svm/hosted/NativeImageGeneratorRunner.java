/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.core.riscv64.ShadowedRISCV64;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;

import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AnalysisError.ParsingError;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.graal.pointsto.util.ParallelExecutionException;
import com.oracle.graal.pointsto.util.Timer;
import com.oracle.graal.pointsto.util.Timer.StopTimer;
import com.oracle.graal.pointsto.util.TimerCollection;
import com.oracle.svm.core.FallbackExecutor;
import com.oracle.svm.core.JavaMainWrapper;
import com.oracle.svm.core.JavaMainWrapper.JavaMainSupport;
import com.oracle.svm.core.OS;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.ExitStatus;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.UserError.UserException;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.analysis.NativeImagePointsToAnalysis;
import com.oracle.svm.hosted.code.CEntryPointData;
import com.oracle.svm.hosted.image.AbstractImage.NativeImageKind;
import com.oracle.svm.hosted.option.HostedOptionParser;
import com.oracle.svm.util.ClassUtil;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.util.ReflectionUtil.ReflectionUtilError;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.runtime.JVMCI;

public class NativeImageGeneratorRunner {

    private volatile NativeImageGenerator generator;
    public static final String IMAGE_BUILDER_ARG_FILE_OPTION = "--image-args-file=";

    public static void main(String[] args) {
        new NativeImageGeneratorRunner().start(args);
    }

    protected void start(String[] args) {
        List<String> arguments = new ArrayList<>(Arrays.asList(args));
        arguments = extractDriverArguments(arguments);
        final String[] classPath = extractImagePathEntries(arguments, SubstrateOptions.IMAGE_CLASSPATH_PREFIX);
        final String[] modulePath = extractImagePathEntries(arguments, SubstrateOptions.IMAGE_MODULEPATH_PREFIX);
        int watchPID = extractWatchPID(arguments);
        TimerTask timerTask = null;
        if (watchPID >= 0) {
            UserError.guarantee(OS.getCurrent().hasProcFS, "%s <pid> requires system with /proc", SubstrateOptions.WATCHPID_PREFIX);
            timerTask = new TimerTask() {
                int cmdlineHashCode = 0;

                @Override
                public void run() {
                    try {
                        int currentCmdlineHashCode = Arrays.hashCode(Files.readAllBytes(Paths.get("/proc/" + watchPID + "/cmdline")));
                        if (cmdlineHashCode == 0) {
                            cmdlineHashCode = currentCmdlineHashCode;
                        } else if (currentCmdlineHashCode != cmdlineHashCode) {
                            System.exit(ExitStatus.WATCHDOG_EXIT.getValue());
                        }
                    } catch (IOException e) {
                        System.exit(ExitStatus.WATCHDOG_EXIT.getValue());
                    }
                }
            };
            java.util.Timer timer = new java.util.Timer("native-image pid watcher");
            timer.scheduleAtFixedRate(timerTask, 0, 1000);
        }
        int exitStatus;
        ClassLoader applicationClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            ImageClassLoader imageClassLoader = installNativeImageClassLoader(classPath, modulePath, arguments);
            List<String> remainingArguments = imageClassLoader.classLoaderSupport.getRemainingArguments();
            if (!remainingArguments.isEmpty()) {
                throw UserError.abort("Unknown options: %s", String.join(" ", remainingArguments));
            }

            Integer checkDependencies = SubstrateOptions.CheckBootModuleDependencies.getValue(imageClassLoader.classLoaderSupport.getParsedHostedOptions());
            if (checkDependencies > 0) {
                checkBootModuleDependencies(checkDependencies > 1);
            }
            exitStatus = build(imageClassLoader);
        } catch (UserException e) {
            reportUserError(e.getMessage());
            exitStatus = ExitStatus.BUILDER_ERROR.getValue();
        } catch (InterruptImageBuilding e) {
            if (e.getReason().isPresent()) {
                if (!e.getReason().get().isEmpty()) {
                    LogUtils.info(e.getReason().get());
                }
                exitStatus = ExitStatus.OK.getValue();
            } else {
                exitStatus = ExitStatus.BUILDER_INTERRUPT_WITHOUT_REASON.getValue();
            }
        } catch (Throwable err) {
            reportFatalError(err);
            exitStatus = ExitStatus.BUILDER_ERROR.getValue();
        } finally {
            uninstallNativeImageClassLoader();
            Thread.currentThread().setContextClassLoader(applicationClassLoader);
            if (timerTask != null) {
                timerTask.cancel();
            }
        }
        System.exit(exitStatus);
    }

    private static void checkBootModuleDependencies(boolean verbose) {
        Set<Module> allModules = ModuleLayer.boot().modules();
        List<Module> builderModules = allModules.stream().filter(m -> m.isNamed() && m.getName().startsWith("org.graalvm.nativeimage.")).toList();
        Set<Module> transitiveBuilderModules = new LinkedHashSet<>();
        for (Module svmModule : builderModules) {
            transitiveReaders(svmModule, allModules, transitiveBuilderModules);
        }
        if (verbose) {
            System.out.println(transitiveBuilderModules.stream()
                            .map(Module::getName)
                            .collect(Collectors.joining("\n", "All builder modules: \n", "\n")));
        }

        Set<Module> modulesBuilderDependsOn = new LinkedHashSet<>();
        for (Module builderModule : transitiveBuilderModules) {
            transitiveRequires(verbose, builderModule, allModules, modulesBuilderDependsOn);
        }
        modulesBuilderDependsOn.removeAll(transitiveBuilderModules);
        if (verbose) {
            System.out.println(modulesBuilderDependsOn.stream()
                            .map(Module::getName)
                            .collect(Collectors.joining("\n", "All modules the builder modules depend on: \n", "\n")));
        }

        Set<String> expectedBuilderDependencies = Set.of(
                        "java.base",
                        "java.management",
                        "java.logging",
                        // workaround for GR-47773 on the module-path which requires java.sql (like
                        // truffle) or java.xml
                        "java.sql",
                        "java.xml",
                        "java.transaction.xa",
                        "jdk.management",
                        "java.compiler",
                        "jdk.jfr",
                        "jdk.zipfs",
                        "jdk.management.jfr");

        Set<String> unexpectedBuilderDependencies = modulesBuilderDependsOn.stream().map(Module::getName).collect(Collectors.toSet());
        unexpectedBuilderDependencies.removeAll(expectedBuilderDependencies);
        if (!unexpectedBuilderDependencies.isEmpty()) {
            throw VMError.shouldNotReachHere("Unexpected image builder module-dependencies: " + String.join(", ", unexpectedBuilderDependencies));
        }
    }

    private static void transitiveReaders(Module readModule, Set<Module> potentialReaders, Set<Module> actualReaders) {
        for (Module potentialReader : potentialReaders) {
            if (potentialReader.canRead(readModule)) {
                if (actualReaders.add(potentialReader)) {
                    transitiveReaders(potentialReader, potentialReaders, actualReaders);
                }
            }
        }
    }

    private static void transitiveRequires(boolean verbose, Module requiringModule, Set<Module> potentialNeededModules, Set<Module> actualNeededModules) {
        for (Module potentialNeedModule : potentialNeededModules) {
            if (requiringModule.canRead(potentialNeedModule)) {
                /* Filter out GraalVM modules */
                if (potentialNeedModule.getName().startsWith("jdk.internal.vm.c") || /* JVMCI */
                                /* graal */
                                potentialNeedModule.getName().startsWith("org.graalvm.") ||
                                /* enterprise graal */
                                potentialNeedModule.getName().startsWith("com.oracle.graal.") ||
                                /* exclude all truffle modules */
                                potentialNeedModule.getName().startsWith("com.oracle.truffle.") ||
                                /* llvm-backend optional dependencies */
                                potentialNeedModule.getName().startsWith("com.oracle.svm.shadowed.")) {
                    continue;
                }
                if (actualNeededModules.add(potentialNeedModule)) {
                    if (verbose) {
                        System.out.println(requiringModule + " reads " + potentialNeedModule);
                    }
                    transitiveRequires(verbose, potentialNeedModule, potentialNeededModules, actualNeededModules);
                }
            }
        }
    }

    public static void uninstallNativeImageClassLoader() {
        ClassLoader loader = ClassLoader.getSystemClassLoader();
        if (loader instanceof NativeImageSystemClassLoader) {
            ((NativeImageSystemClassLoader) loader).setNativeImageClassLoader(null);
        }
    }

    /**
     * Installs a class loader hierarchy that resolves classes and resources available in
     * {@code classpath} and {@code modulepath}. The parent for the installed {@code ClassLoader} is
     * the default system class loader (jdk.internal.loader.ClassLoaders.AppClassLoader and
     * sun.misc.Launcher.AppClassLoader for JDK11, 8 respectively).
     *
     * We use a custom system class loader {@link NativeImageSystemClassLoader} that delegates to
     * the {@code ClassLoader} that {@link NativeImageClassLoaderSupport} creates, thus allowing the
     * resolution of classes in {@code classpath} and {@code modulepath} via system class loader.
     *
     * @param classpath for the application and image should be built for.
     * @param modulepath for the application and image should be built for (only for Java >= 11).
     * @param arguments
     * @return NativeImageClassLoaderSupport that exposes the {@code ClassLoader} for image building
     *         via {@link NativeImageClassLoaderSupport#getClassLoader()}.
     */
    public static ImageClassLoader installNativeImageClassLoader(String[] classpath, String[] modulepath, List<String> arguments) {
        NativeImageSystemClassLoader nativeImageSystemClassLoader = NativeImageSystemClassLoader.singleton();
        NativeImageClassLoaderSupport nativeImageClassLoaderSupport = new NativeImageClassLoaderSupport(nativeImageSystemClassLoader.defaultSystemClassLoader, classpath, modulepath);
        nativeImageClassLoaderSupport.setupHostedOptionParser(arguments);
        /* Perform additional post-processing with the created nativeImageClassLoaderSupport */
        for (NativeImageClassLoaderPostProcessing postProcessing : ServiceLoader.load(NativeImageClassLoaderPostProcessing.class)) {
            postProcessing.apply(nativeImageClassLoaderSupport);
        }
        ClassLoader nativeImageClassLoader = nativeImageClassLoaderSupport.getClassLoader();
        Thread.currentThread().setContextClassLoader(nativeImageClassLoader);
        /*
         * Make NativeImageSystemClassLoader delegate to the classLoader provided by
         * NativeImageClassLoaderSupport, enabling resolution of classes and resources during image
         * build-time present on the image classpath and modulepath.
         */
        nativeImageSystemClassLoader.setNativeImageClassLoader(nativeImageClassLoader);

        /*
         * Iterating all classes can already trigger class initialization: We need annotation
         * information, which triggers class initialization of annotation classes and enum classes
         * referenced by annotations. Therefore, we need to have the system properties that indicate
         * "during image build" set up already at this time.
         */
        NativeImageGenerator.setSystemPropertiesForImageEarly();

        return new ImageClassLoader(NativeImageGenerator.getTargetPlatform(nativeImageClassLoader), nativeImageClassLoaderSupport);
    }

    public static List<String> extractDriverArguments(List<String> args) {
        ArrayList<String> result = args.stream().filter(arg -> !arg.startsWith(IMAGE_BUILDER_ARG_FILE_OPTION)).collect(Collectors.toCollection(ArrayList::new));
        Optional<String> argsFile = args.stream().filter(arg -> arg.startsWith(IMAGE_BUILDER_ARG_FILE_OPTION)).findFirst();

        if (argsFile.isPresent()) {
            String argFilePath = argsFile.get().substring(IMAGE_BUILDER_ARG_FILE_OPTION.length());
            try {
                String options = new String(Files.readAllBytes(Paths.get(argFilePath)));
                result.addAll(Arrays.asList(options.split("\0")));
            } catch (IOException e) {
                throw VMError.shouldNotReachHere("Exception occurred during image builder argument file processing", e);
            }
        }
        return result;
    }

    public static String[] extractImagePathEntries(List<String> arguments, String pathPrefix) {
        int cpArgIndex = arguments.indexOf(pathPrefix);
        String msgTail = " '" + pathPrefix + " <Path entries separated by File.pathSeparator>' argument.";
        if (cpArgIndex == -1) {
            return new String[0];
        }
        arguments.remove(cpArgIndex);
        try {
            String imageClasspath = arguments.remove(cpArgIndex);
            return imageClasspath.split(File.pathSeparator, Integer.MAX_VALUE);
        } catch (IndexOutOfBoundsException e) {
            throw UserError.abort("Missing path entries for %s", msgTail);
        }
    }

    public static int extractWatchPID(List<String> arguments) {
        int cpIndex = arguments.indexOf(SubstrateOptions.WATCHPID_PREFIX);
        if (cpIndex >= 0) {
            if (cpIndex + 1 >= arguments.size()) {
                throw UserError.abort("ProcessID must be provided after the '%s' argument", SubstrateOptions.WATCHPID_PREFIX);
            }
            arguments.remove(cpIndex);
            String pidStr = arguments.get(cpIndex);
            arguments.remove(cpIndex);
            return Integer.parseInt(pidStr);
        }
        return -1;
    }

    private static void reportToolUserError(String msg) {
        reportUserError("native-image " + msg);
    }

    private static boolean isValidArchitecture() {
        final Architecture originalTargetArch = GraalAccess.getOriginalTarget().arch;
        return originalTargetArch instanceof AMD64 || originalTargetArch instanceof AArch64 || ShadowedRISCV64.instanceOf(originalTargetArch);
    }

    private static boolean isValidOperatingSystem() {
        return OS.LINUX.isCurrent() || OS.DARWIN.isCurrent() || OS.WINDOWS.isCurrent();
    }

    @SuppressWarnings("try")
    private int buildImage(ImageClassLoader classLoader) {
        if (!verifyValidJavaVersionAndPlatform()) {
            return ExitStatus.BUILDER_ERROR.getValue();
        }

        HostedOptionParser optionParser = classLoader.classLoaderSupport.getHostedOptionParser();
        OptionValues parsedHostedOptions = classLoader.classLoaderSupport.getParsedHostedOptions();

        String imageName = SubstrateOptions.Name.getValue(parsedHostedOptions);
        TimerCollection timerCollection = new TimerCollection();
        Timer totalTimer = timerCollection.get(TimerCollection.Registry.TOTAL);

        if (NativeImageOptions.ListCPUFeatures.getValue(parsedHostedOptions)) {
            printCPUFeatures(classLoader.platform);
            return ExitStatus.OK.getValue();
        }

        ForkJoinPool analysisExecutor = null;
        ForkJoinPool compilationExecutor = null;

        ProgressReporter reporter = new ProgressReporter(parsedHostedOptions);
        Throwable vmError = null;
        boolean wasSuccessfulBuild = false;
        try (StopTimer ignored = totalTimer.start()) {
            Timer classlistTimer = timerCollection.get(TimerCollection.Registry.CLASSLIST);
            try (StopTimer ignored1 = classlistTimer.start()) {
                classLoader.loadAllClasses();
            }

            DebugContext debug = new DebugContext.Builder(parsedHostedOptions, new GraalDebugHandlersFactory(GraalAccess.getOriginalSnippetReflection())).build();

            if (imageName.length() == 0) {
                throw UserError.abort("No output file name specified. Use '%s'", SubstrateOptionsParser.commandArgument(SubstrateOptions.Name, "<output-file>"));
            }
            try {
                Map<Method, CEntryPointData> entryPoints = new HashMap<>();
                Pair<Method, CEntryPointData> mainEntryPointData = Pair.empty();
                JavaMainSupport javaMainSupport = null;

                NativeImageKind imageKind;
                boolean isStaticExecutable = SubstrateOptions.StaticExecutable.getValue(parsedHostedOptions);
                boolean isSharedLibrary = SubstrateOptions.SharedLibrary.getValue(parsedHostedOptions);
                if (isStaticExecutable && isSharedLibrary) {
                    throw UserError.abort("Cannot pass both option: %s and %s", SubstrateOptionsParser.commandArgument(SubstrateOptions.SharedLibrary, "+"),
                                    SubstrateOptionsParser.commandArgument(SubstrateOptions.StaticExecutable, "+"));
                } else if (isSharedLibrary) {
                    imageKind = NativeImageKind.SHARED_LIBRARY;
                } else if (isStaticExecutable) {
                    imageKind = NativeImageKind.STATIC_EXECUTABLE;
                } else {
                    imageKind = NativeImageKind.EXECUTABLE;
                }

                String className = SubstrateOptions.Class.getValue(parsedHostedOptions);
                String moduleName = SubstrateOptions.Module.getValue(parsedHostedOptions);
                if (imageKind.isExecutable && moduleName.isEmpty() && className.isEmpty()) {
                    throw UserError.abort("Must specify main entry point class when building %s native image. Use '%s'.", imageKind,
                                    SubstrateOptionsParser.commandArgument(SubstrateOptions.Class, "<fully-qualified-class-name>"));
                }

                reporter.printStart(imageName, imageKind);

                if (!className.isEmpty() || !moduleName.isEmpty()) {
                    Method mainEntryPoint;
                    Class<?> mainClass;
                    try {
                        Module mainModule = null;
                        if (!moduleName.isEmpty()) {
                            mainModule = classLoader.findModule(moduleName)
                                            .orElseThrow(() -> UserError.abort("Module " + moduleName + " for mainclass not found."));
                        }
                        if (className.isEmpty()) {
                            className = classLoader.getMainClassFromModule(mainModule)
                                            .orElseThrow(() -> UserError.abort("Module %s does not have a ModuleMainClass attribute, use -m <module>/<main-class>", moduleName));
                        }
                        mainClass = classLoader.forName(className, mainModule);
                        if (mainClass == null) {
                            throw UserError.abort(classLoader.getMainClassNotFoundErrorMessage(className));
                        }
                    } catch (ClassNotFoundException ex) {
                        throw UserError.abort(classLoader.getMainClassNotFoundErrorMessage(className));
                    } catch (UnsupportedClassVersionError ex) {
                        if (ex.getMessage().contains("compiled by a more recent version of the Java Runtime")) {
                            throw UserError.abort("Unable to load '%s' due to a Java version mismatch.%n" +
                                            "Please take one of the following actions:%n" +
                                            " 1) Recompile the source files for your application using Java %s, then try running native-image again%n" +
                                            " 2) Use a version of native-image corresponding to the version of Java with which you compiled the source files for your application%n%n" +
                                            "Root cause: %s",
                                            className, Runtime.version().feature(), ex);
                        } else {
                            throw UserError.abort(ex.getMessage());
                        }
                    }
                    String mainEntryPointName = SubstrateOptions.Method.getValue(parsedHostedOptions);
                    if (mainEntryPointName.isEmpty()) {
                        throw UserError.abort("Must specify main entry point method when building %s native image. Use '%s'.", imageKind,
                                        SubstrateOptionsParser.commandArgument(SubstrateOptions.Method, "<method-name>"));
                    }
                    try {
                        /*
                         * First look for an main method with the C-level signature for arguments.
                         */
                        mainEntryPoint = mainClass.getDeclaredMethod(mainEntryPointName, int.class, CCharPointerPointer.class);
                    } catch (NoSuchMethodException ignored2) {
                        Method javaMainMethod;
                        /*
                         * If no C-level main method was found, look for a Java-level main method
                         * and use our wrapper to invoke it.
                         */
                        if ("main".equals(mainEntryPointName) && JavaMainWrapper.instanceMainMethodSupported()) {
                            // Instance main method only supported for "main" method name
                            try {
                                /*
                                 * JDK-8306112: Implementation of JEP 445: Unnamed Classes and
                                 * Instance Main Methods (Preview)
                                 *
                                 * MainMethodFinder will perform all the necessary checks
                                 */
                                Class<?> mainMethodFinder = ReflectionUtil.lookupClass(false, "jdk.internal.misc.MainMethodFinder");
                                Method findMainMethod = ReflectionUtil.lookupMethod(mainMethodFinder, "findMainMethod", Class.class);
                                javaMainMethod = (Method) findMainMethod.invoke(null, mainClass);
                            } catch (InvocationTargetException ex) {
                                assert ex.getTargetException() instanceof NoSuchMethodException;
                                throw UserError.abort(ex.getCause(),
                                                "Method '%s.%s' is declared as the main entry point but it can not be found. " +
                                                                "Make sure that class '%s' is on the classpath and that non-private " +
                                                                "method '%s()' or '%s(String[])'.",
                                                mainClass.getName(),
                                                mainEntryPointName,
                                                mainClass.getName(),
                                                mainEntryPointName,
                                                mainEntryPointName);
                            }
                        } else {
                            try {
                                javaMainMethod = ReflectionUtil.lookupMethod(mainClass, mainEntryPointName, String[].class);
                                final int mainMethodModifiers = javaMainMethod.getModifiers();
                                if (!Modifier.isStatic(mainMethodModifiers)) {
                                    throw UserError.abort("Java main method '%s.%s(String[])' is not static.", mainClass.getName(), mainEntryPointName);
                                }
                                if (!Modifier.isPublic(mainMethodModifiers)) {
                                    throw UserError.abort("Java main method '%s.%s(String[])' is not public.", mainClass.getName(), mainEntryPointName);
                                }
                            } catch (ReflectionUtilError ex) {
                                throw UserError.abort(ex.getCause(),
                                                "Method '%s.%s' is declared as the main entry point but it can not be found. " +
                                                                "Make sure that class '%s' is on the classpath and that method '%s(String[])' exists in that class.",
                                                mainClass.getName(),
                                                mainEntryPointName,
                                                mainClass.getName(),
                                                mainEntryPointName);
                            }
                        }

                        if (javaMainMethod.getReturnType() != void.class) {
                            throw UserError.abort("Java main method '%s.%s(%s)' does not have the return type 'void'.", mainClass.getName(), mainEntryPointName,
                                            javaMainMethod.getParameterCount() == 1 ? "String[]" : "");
                        }
                        javaMainSupport = createJavaMainSupport(javaMainMethod, classLoader);
                        mainEntryPoint = getMainEntryMethod(classLoader);
                    }
                    verifyMainEntryPoint(mainEntryPoint);

                    mainEntryPointData = createMainEntryPointData(imageKind, mainEntryPoint);
                }

                int maxConcurrentThreads = NativeImageOptions.getMaximumNumberOfConcurrentThreads(parsedHostedOptions);
                analysisExecutor = NativeImagePointsToAnalysis.createExecutor(debug, NativeImageOptions.getMaximumNumberOfAnalysisThreads(parsedHostedOptions));
                compilationExecutor = NativeImagePointsToAnalysis.createExecutor(debug, maxConcurrentThreads);
                generator = createImageGenerator(classLoader, optionParser, mainEntryPointData, reporter);
                generator.run(entryPoints, javaMainSupport, imageName, imageKind, SubstitutionProcessor.IDENTITY,
                                compilationExecutor, analysisExecutor, optionParser.getRuntimeOptionNames(), timerCollection);
                wasSuccessfulBuild = true;
            } finally {
                if (!wasSuccessfulBuild) {
                    reporter.printUnsuccessfulInitializeEnd();
                }
            }
        } catch (InterruptImageBuilding e) {
            if (analysisExecutor != null) {
                analysisExecutor.shutdownNow();
            }
            if (compilationExecutor != null) {
                compilationExecutor.shutdownNow();
            }
            throw e;
        } catch (FallbackFeature.FallbackImageRequest e) {
            if (FallbackExecutor.class.getName().equals(SubstrateOptions.Class.getValue())) {
                NativeImageGeneratorRunner.reportFatalError(e, "FallbackImageRequest while building fallback image.");
                return ExitStatus.BUILDER_ERROR.getValue();
            }
            reportUserException(e, parsedHostedOptions, LogUtils::warning);
            return ExitStatus.FALLBACK_IMAGE.getValue();
        } catch (ParsingError e) {
            NativeImageGeneratorRunner.reportFatalError(e);
            return ExitStatus.BUILDER_ERROR.getValue();
        } catch (UserException | AnalysisError e) {
            reportUserError(e, parsedHostedOptions);
            return ExitStatus.BUILDER_ERROR.getValue();
        } catch (ParallelExecutionException pee) {
            boolean hasUserError = false;
            for (Throwable exception : pee.getExceptions()) {
                if (exception instanceof UserException) {
                    reportUserError(exception, parsedHostedOptions);
                    hasUserError = true;
                } else if (exception instanceof AnalysisError && !(exception instanceof ParsingError)) {
                    reportUserError(exception, parsedHostedOptions);
                    hasUserError = true;
                } else if (exception.getCause() instanceof UserException) {
                    reportUserError(exception.getCause(), parsedHostedOptions);
                    hasUserError = true;
                }
            }
            if (hasUserError) {
                return ExitStatus.BUILDER_ERROR.getValue();
            }

            if (pee.getExceptions().size() > 1) {
                System.err.println(pee.getExceptions().size() + " fatal errors detected:");
            }
            for (Throwable exception : pee.getExceptions()) {
                NativeImageGeneratorRunner.reportFatalError(exception);
            }
            return ExitStatus.BUILDER_ERROR.getValue();
        } catch (Throwable e) {
            vmError = e;
            return ExitStatus.BUILDER_ERROR.getValue();
        } finally {
            reportEpilog(imageName, reporter, classLoader, vmError, parsedHostedOptions);
            NativeImageGenerator.clearSystemPropertiesForImage();
            ImageSingletonsSupportImpl.HostedManagement.clear();
        }
        return ExitStatus.OK.getValue();
    }

    protected void reportEpilog(String imageName, ProgressReporter reporter, ImageClassLoader classLoader, Throwable vmError, OptionValues parsedHostedOptions) {
        reporter.printEpilog(Optional.ofNullable(imageName), Optional.ofNullable(generator), classLoader, Optional.ofNullable(vmError), parsedHostedOptions);
    }

    protected NativeImageGenerator createImageGenerator(ImageClassLoader classLoader, HostedOptionParser optionParser, Pair<Method, CEntryPointData> mainEntryPointData, ProgressReporter reporter) {
        return new NativeImageGenerator(classLoader, optionParser, mainEntryPointData, reporter);
    }

    protected Pair<Method, CEntryPointData> createMainEntryPointData(NativeImageKind imageKind, Method mainEntryPoint) {
        Pair<Method, CEntryPointData> mainEntryPointData;
        Class<?>[] pt = mainEntryPoint.getParameterTypes();
        if (pt.length != 2 || pt[0] != int.class || pt[1] != CCharPointerPointer.class || mainEntryPoint.getReturnType() != int.class) {
            throw UserError.abort("Main entry point must have signature 'int main(int argc, CCharPointerPointer argv)'.");
        }
        mainEntryPointData = Pair.create(mainEntryPoint, CEntryPointData.create(mainEntryPoint, imageKind.mainEntryPointName));
        return mainEntryPointData;
    }

    protected Method getMainEntryMethod(@SuppressWarnings("unused") ImageClassLoader classLoader) throws NoSuchMethodException {
        return JavaMainWrapper.class.getDeclaredMethod("run", int.class, CCharPointerPointer.class);
    }

    protected JavaMainSupport createJavaMainSupport(Method javaMainMethod, @SuppressWarnings("unused") ImageClassLoader classLoader) throws IllegalAccessException {
        return new JavaMainSupport(javaMainMethod);
    }

    protected void verifyMainEntryPoint(Method mainEntryPoint) {
        CEntryPoint annotation = mainEntryPoint.getAnnotation(CEntryPoint.class);
        if (annotation == null) {
            throw UserError.abort("Entry point must have the '@%s' annotation", CEntryPoint.class.getSimpleName());
        }
    }

    public static boolean verifyValidJavaVersionAndPlatform() {
        if (!isValidArchitecture()) {
            reportToolUserError("Runs on AMD64, AArch64 and RISCV64 only. Detected architecture: " + ClassUtil.getUnqualifiedName(GraalAccess.getOriginalTarget().arch.getClass()));
        }
        if (!isValidOperatingSystem()) {
            reportToolUserError("Runs on Linux, Mac OS X and Windows only. Detected OS: " + System.getProperty("os.name"));
            return false;
        }

        return true;
    }

    public static void printCPUFeatures(Platform platform) {
        StringBuilder message = new StringBuilder();
        Architecture arch = JVMCI.getRuntime().getHostJVMCIBackend().getTarget().arch;
        if (NativeImageGenerator.includedIn(platform, Platform.AMD64.class)) {
            message.append("All AMD64 CPUFeatures: ").append(Arrays.toString(AMD64.CPUFeature.values()));
            if (arch instanceof AMD64) {
                message.append(System.lineSeparator()).append("Host machine AMD64 CPUFeatures: ").append(((AMD64) arch).getFeatures().toString());
            }
        } else {
            assert NativeImageGenerator.includedIn(platform, Platform.AARCH64.class);
            message.append("All AArch64 CPUFeatures: ").append(Arrays.toString(AArch64.CPUFeature.values()));
            if (arch instanceof AArch64) {
                message.append(System.lineSeparator()).append("Host machine AArch64 CPUFeatures: ").append(((AArch64) arch).getFeatures().toString());
            }
        }
        System.out.println(message);
    }

    /**
     * Reports an unexpected error caused by a crash in the SVM image builder.
     *
     * @param e error to be reported.
     */
    protected static void reportFatalError(Throwable e) {
        System.err.print("Fatal error: ");
        e.printStackTrace();
    }

    /**
     * Reports an unexpected error caused by a crash in the SVM image builder.
     *
     * @param e error to be reported.
     * @param msg message to report.
     */
    protected static void reportFatalError(Throwable e, String msg) {
        System.err.print("Fatal error: " + msg);
        e.printStackTrace();
    }

    /**
     * Function for reporting all fatal errors in SVM.
     *
     * @param msg error message that is printed.
     */
    public static void reportUserError(String msg) {
        System.err.println("Error: " + msg);
    }

    /**
     * Function for reporting all fatal errors in SVM.
     *
     * @param e error message that is printed.
     * @param parsedHostedOptions
     */
    public static void reportUserError(Throwable e, OptionValues parsedHostedOptions) {
        reportUserException(e, parsedHostedOptions, NativeImageGeneratorRunner::reportUserError);
    }

    private static void reportUserException(Throwable e, OptionValues parsedHostedOptions, Consumer<String> report) {
        if (e instanceof UserException ue) {
            for (String message : ue.getMessages()) {
                report.accept(message);
            }
        } else {
            report.accept(e.getMessage());
        }
        if (parsedHostedOptions != null && NativeImageOptions.ReportExceptionStackTraces.getValue(parsedHostedOptions)) {
            e.printStackTrace();
        }
    }

    public int build(ImageClassLoader imageClassLoader) {
        return buildImage(imageClassLoader);
    }

    /**
     * Command line entry point when running on JDK9+. This is required to dynamically export Graal
     * to SVM and it requires {@code --add-exports=java.base/jdk.internal.module=ALL-UNNAMED} to be
     * on the VM command line.
     *
     * Note: This is a workaround until GR-16855 is resolved.
     */
    public static class JDK9Plus {

        public static void main(String[] args) {
            setModuleAccesses();
            NativeImageGeneratorRunner.main(args);
        }

        public static void setModuleAccesses() {
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "org.graalvm.word");
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "org.graalvm.nativeimage");
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "org.graalvm.collections");
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "org.graalvm.polyglot");
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "org.graalvm.truffle");
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "jdk.internal.vm.ci");
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "jdk.internal.vm.compiler");
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, true, "jdk.internal.vm.compiler.management");
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, true, "com.oracle.graal.graal_enterprise");
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "java.base", "jdk.internal.loader");
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "java.base", "jdk.internal.misc");
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "java.base", "sun.text.spi");
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "java.base", "jdk.internal.org.objectweb.asm");
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "java.base", "sun.reflect.annotation");
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "java.base", "sun.security.jca");
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "jdk.jdeps", "com.sun.tools.classfile");
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "org.graalvm.truffle.runtime");
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "org.graalvm.truffle.compiler");
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, true, "com.oracle.truffle.enterprise");
        }
    }
}
