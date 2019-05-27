/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;

import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.ParallelExecutionException;
import com.oracle.graal.pointsto.util.Timer;
import com.oracle.graal.pointsto.util.Timer.StopTimer;
import com.oracle.svm.core.JavaMainWrapper;
import com.oracle.svm.core.JavaMainWrapper.JavaMainSupport;
import com.oracle.svm.core.OS;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.UserError.UserException;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.c.GraalAccess;
import com.oracle.svm.hosted.code.CEntryPointData;
import com.oracle.svm.hosted.image.AbstractBootImage;
import com.oracle.svm.hosted.option.HostedOptionParser;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.util.ReflectionUtil.ReflectionUtilError;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;

public class NativeImageGeneratorRunner implements ImageBuildTask {

    private volatile NativeImageGenerator generator;

    public static void main(String[] args) {
        ArrayList<String> arguments = new ArrayList<>(Arrays.asList(args));
        final String[] classpath = extractImageClassPath(arguments);
        int watchPID = extractWatchPID(arguments);
        TimerTask timerTask = null;
        if (watchPID >= 0) {
            VMError.guarantee(OS.getCurrent().hasProcFS, SubstrateOptions.WATCHPID_PREFIX + " <pid> requires system with /proc");
            timerTask = new TimerTask() {
                int cmdlineHashCode = 0;

                @Override
                public void run() {
                    try {
                        int currentCmdlineHashCode = Arrays.hashCode(Files.readAllBytes(Paths.get("/proc/" + watchPID + "/cmdline")));
                        if (cmdlineHashCode == 0) {
                            cmdlineHashCode = currentCmdlineHashCode;
                        } else if (currentCmdlineHashCode != cmdlineHashCode) {
                            System.exit(1);
                        }
                    } catch (IOException e) {
                        System.exit(1);
                    }
                }
            };
            java.util.Timer timer = new java.util.Timer("native-image pid watcher");
            timer.scheduleAtFixedRate(timerTask, 0, 1000);
        }
        int exitStatus = 1;
        try {
            NativeImageClassLoader nativeImageClassLoader = installNativeImageClassLoader(classpath);
            exitStatus = new NativeImageGeneratorRunner().build(arguments.toArray(new String[arguments.size()]), classpath, nativeImageClassLoader);
        } finally {
            if (timerTask != null) {
                timerTask.cancel();
            }
        }
        System.exit(exitStatus);
    }

    public static NativeImageClassLoader installNativeImageClassLoader(String[] classpath) {
        NativeImageClassLoader nativeImageClassLoader;
        ClassLoader applicationClassLoader = Thread.currentThread().getContextClassLoader();
        nativeImageClassLoader = new NativeImageClassLoader(verifyClassPathAndConvertToURLs(classpath), applicationClassLoader);
        Thread.currentThread().setContextClassLoader(nativeImageClassLoader);
        return nativeImageClassLoader;
    }

    public static String[] extractImageClassPath(List<String> arguments) {
        int cpArgIndex = arguments.indexOf(SubstrateOptions.IMAGE_CLASSPATH_PREFIX);
        String msgTail = " '" + SubstrateOptions.IMAGE_CLASSPATH_PREFIX + " <image classpath>' argument.";
        if (cpArgIndex == -1) {
            throw UserError.abort("Missing" + msgTail);
        }
        arguments.remove(cpArgIndex);
        try {
            String imageClasspath = arguments.remove(cpArgIndex);
            return imageClasspath.split(File.pathSeparator, Integer.MAX_VALUE);
        } catch (IndexOutOfBoundsException e) {
            throw UserError.abort("Missing <image classpath> for" + msgTail);
        }
    }

    public static int extractWatchPID(List<String> arguments) {
        int cpIndex = arguments.indexOf(SubstrateOptions.WATCHPID_PREFIX);
        if (cpIndex >= 0) {
            if (cpIndex + 1 >= arguments.size()) {
                throw UserError.abort("ProcessID must be provided after the '" + SubstrateOptions.WATCHPID_PREFIX + "' argument.\n");
            }
            arguments.remove(cpIndex);
            String pidStr = arguments.get(cpIndex);
            arguments.remove(cpIndex);
            return Integer.parseInt(pidStr);
        }
        return -1;
    }

    private static URL[] verifyClassPathAndConvertToURLs(String[] classpath) {
        return new HashSet<>(Arrays.asList(classpath)).stream().flatMap(ImageClassLoader::toClassPathEntries).map(v -> {
            try {
                return v.toAbsolutePath().toUri().toURL();
            } catch (MalformedURLException e) {
                throw UserError.abort("Invalid classpath element '" + v + "'. Make sure that all paths provided with '" + SubstrateOptions.IMAGE_CLASSPATH_PREFIX + "' are correct.");
            }
        }).toArray(URL[]::new);
    }

    /** Unless the check should be ignored, check that I am running on JDK-8. */
    public static boolean isValidJavaVersion() {
        return (Boolean.getBoolean("substratevm.IgnoreGraalVersionCheck") || JavaVersionUtil.JAVA_SPEC <= 8);
    }

    private static void reportToolUserError(String msg) {
        reportUserError("native-image " + msg);
    }

    private static boolean isValidArchitecture() {
        final Architecture originalTargetArch = GraalAccess.getOriginalTarget().arch;
        return originalTargetArch instanceof AMD64 || originalTargetArch instanceof AArch64;
    }

    private static boolean isValidOperatingSystem() {
        final OS currentOs = OS.getCurrent();
        return currentOs == OS.LINUX || currentOs == OS.DARWIN || currentOs == OS.WINDOWS;
    }

    @SuppressWarnings("try")
    private int buildImage(String[] arguments, String[] classpath, NativeImageClassLoader classLoader) {
        if (!verifyValidJavaVersionAndPlatform()) {
            return 1;
        }
        Timer totalTimer = new Timer("[total]", false);
        ForkJoinPool analysisExecutor = null;
        ForkJoinPool compilationExecutor = null;
        OptionValues parsedHostedOptions = null;
        try (StopTimer ignored = totalTimer.start()) {
            ImageClassLoader imageClassLoader;
            Timer classlistTimer = new Timer("classlist", false);
            try (StopTimer ignored1 = classlistTimer.start()) {
                imageClassLoader = ImageClassLoader.create(NativeImageGenerator.defaultPlatform(classLoader), classpath, classLoader);
            }

            HostedOptionParser optionParser = new HostedOptionParser(imageClassLoader);
            String[] remainingArgs = optionParser.parse(arguments);
            if (remainingArgs.length > 0) {
                throw UserError.abort("Unknown options: " + Arrays.toString(remainingArgs));
            }

            /*
             * We do not have the VMConfiguration and the HostedOptionValues set up yet, so we need
             * to pass the OptionValues explicitly when accessing options.
             */
            parsedHostedOptions = new OptionValues(optionParser.getHostedValues());
            DebugContext debug = DebugContext.create(parsedHostedOptions, new GraalDebugHandlersFactory(GraalAccess.getOriginalSnippetReflection()));

            String imageName = SubstrateOptions.Name.getValue(parsedHostedOptions);
            if (imageName.length() == 0) {
                throw UserError.abort("No output file name specified. " +
                                "Use '" + SubstrateOptionsParser.commandArgument(SubstrateOptions.Name, "<output-file>") + "'.");
            }

            totalTimer.setPrefix(imageName);
            classlistTimer.setPrefix(imageName);

            // print the time here to avoid interactions with flags processing
            classlistTimer.print();

            Map<Method, CEntryPointData> entryPoints = new HashMap<>();
            Method mainEntryPoint = null;
            JavaMainSupport javaMainSupport = null;

            AbstractBootImage.NativeImageKind imageKind;
            boolean isStaticExecutable = SubstrateOptions.StaticExecutable.getValue(parsedHostedOptions);
            boolean isSharedLibrary = SubstrateOptions.SharedLibrary.getValue(parsedHostedOptions);
            if (isStaticExecutable && isSharedLibrary) {
                throw UserError.abort("Cannot pass both option: " +
                                SubstrateOptionsParser.commandArgument(SubstrateOptions.SharedLibrary, "+") + " and " +
                                SubstrateOptionsParser.commandArgument(SubstrateOptions.StaticExecutable, "+"));
            } else if (isSharedLibrary) {
                imageKind = AbstractBootImage.NativeImageKind.SHARED_LIBRARY;
            } else if (isStaticExecutable) {
                imageKind = AbstractBootImage.NativeImageKind.STATIC_EXECUTABLE;
            } else {
                imageKind = AbstractBootImage.NativeImageKind.EXECUTABLE;
            }

            String className = SubstrateOptions.Class.getValue(parsedHostedOptions);
            if (imageKind.executable && className.isEmpty()) {
                throw UserError.abort("Must specify main entry point class when building " + imageKind + " native image. " +
                                "Use '" + SubstrateOptionsParser.commandArgument(SubstrateOptions.Class, "<fully-qualified-class-name>") + "'.");
            }

            if (!className.isEmpty()) {
                Class<?> mainClass;
                try {
                    mainClass = Class.forName(className, false, classLoader);
                } catch (ClassNotFoundException ex) {
                    throw UserError.abort("Main entry point class '" + className + "' not found.");
                }
                String mainEntryPointName = SubstrateOptions.Method.getValue(parsedHostedOptions);
                if (mainEntryPointName.isEmpty()) {
                    throw UserError.abort("Must specify main entry point method when building " + imageKind + " native image. " +
                                    "Use '" + SubstrateOptionsParser.commandArgument(SubstrateOptions.Method, "<method-name>") + "'.");
                }
                try {
                    /* First look for an main method with the C-level signature for arguments. */
                    mainEntryPoint = mainClass.getDeclaredMethod(mainEntryPointName, int.class, CCharPointerPointer.class);
                } catch (NoSuchMethodException ignored2) {
                    Method javaMainMethod;
                    try {
                        /*
                         * If no C-level main method was found, look for a Java-level main method
                         * and use our wrapper to invoke it.
                         */
                        javaMainMethod = ReflectionUtil.lookupMethod(mainClass, mainEntryPointName, String[].class);
                    } catch (ReflectionUtilError ex) {
                        throw UserError.abort("Method '" + mainClass.getName() + "." + mainEntryPointName + "' is declared as the main entry point but it can not be found. " +
                                        "Make sure that class '" + mainClass.getName() + "' is on the classpath and that method '" + mainEntryPointName + "(String[])' exists in that class.",
                                        ex.getCause());
                    }

                    if (javaMainMethod.getReturnType() != void.class) {
                        throw UserError.abort("Java main method must have return type void. Change the return type of method '" + mainClass.getName() + "." + mainEntryPointName + "(String[])'.");
                    }
                    final int mainMethodModifiers = javaMainMethod.getModifiers();
                    if (!Modifier.isPublic(mainMethodModifiers)) {
                        throw UserError.abort("Method '" + mainClass.getName() + "." + mainEntryPointName + "(String[])' is not accessible.  Please make it 'public'.");
                    }
                    javaMainSupport = new JavaMainSupport(javaMainMethod);
                    mainEntryPoint = JavaMainWrapper.class.getDeclaredMethod("run", int.class, CCharPointerPointer.class);
                }
                CEntryPoint annotation = mainEntryPoint.getAnnotation(CEntryPoint.class);
                if (annotation == null) {
                    throw UserError.abort("Entry point must have the '@" + CEntryPoint.class.getSimpleName() + "' annotation");
                }
                entryPoints.put(mainEntryPoint, CEntryPointData.create(mainEntryPoint));

                Class<?>[] pt = mainEntryPoint.getParameterTypes();
                if (pt.length != 2 || pt[0] != int.class || pt[1] != CCharPointerPointer.class || mainEntryPoint.getReturnType() != int.class) {
                    throw UserError.abort("Main entry point must have signature 'int main(int argc, CCharPointerPointer argv)'.");
                }
            }

            int maxConcurrentThreads = NativeImageOptions.getMaximumNumberOfConcurrentThreads(parsedHostedOptions);
            analysisExecutor = Inflation.createExecutor(debug, NativeImageOptions.getMaximumNumberOfAnalysisThreads(parsedHostedOptions));
            compilationExecutor = Inflation.createExecutor(debug, maxConcurrentThreads);
            generator = new NativeImageGenerator(imageClassLoader, optionParser);
            generator.run(entryPoints, mainEntryPoint, javaMainSupport, imageName, imageKind, SubstitutionProcessor.IDENTITY,
                            compilationExecutor, analysisExecutor, optionParser.getRuntimeOptionNames());
        } catch (InterruptImageBuilding e) {
            if (analysisExecutor != null) {
                analysisExecutor.shutdownNow();
            }
            if (compilationExecutor != null) {
                compilationExecutor.shutdownNow();
            }
            e.getReason().ifPresent(NativeImageGeneratorRunner::info);
            return 3;
        } catch (FallbackFeature.FallbackImageRequest e) {
            reportUserException(e, parsedHostedOptions, NativeImageGeneratorRunner::warn);
            return 2;
        } catch (UserException e) {
            reportUserError(e, parsedHostedOptions);
            return 1;
        } catch (AnalysisError e) {
            reportUserError(e, parsedHostedOptions);
            return 1;
        } catch (ParallelExecutionException pee) {
            boolean hasUserError = false;
            for (Throwable exception : pee.getExceptions()) {
                if (exception instanceof UserException) {
                    reportUserError(exception, parsedHostedOptions);
                    hasUserError = true;
                } else if (exception instanceof AnalysisError) {
                    reportUserError(exception, parsedHostedOptions);
                    hasUserError = true;
                }
            }
            if (hasUserError) {
                return 1;
            }

            if (pee.getExceptions().size() > 1) {
                System.err.println(pee.getExceptions().size() + " fatal errors detected:");
            }
            for (Throwable exception : pee.getExceptions()) {
                NativeImageGeneratorRunner.reportFatalError(exception);
            }
            return 1;
        } catch (Throwable e) {
            NativeImageGeneratorRunner.reportFatalError(e);
            return 1;
        } finally {
            ImageSingletonsSupportImpl.HostedManagement.clearInThread();
        }
        totalTimer.print();
        return 0;
    }

    public static boolean verifyValidJavaVersionAndPlatform() {
        if (!isValidJavaVersion()) {
            reportToolUserError("supports only Java 1.8 with an update version 40+. Detected Java version is: " + getJavaVersion());
            return false;
        }
        if (!isValidArchitecture()) {
            reportToolUserError("runs only on architecture AMD64. Detected architecture: " + GraalAccess.getOriginalTarget().arch.getClass().getSimpleName());
        }
        if (!isValidOperatingSystem()) {
            reportToolUserError("runs on Linux, Mac OS X and Windows only. Detected OS: " + System.getProperty("os.name"));
            return false;
        }

        return true;
    }

    public static String getJavaVersion() {
        return System.getProperty("java.version");
    }

    /**
     * Reports an unexpected error caused by a crash in the SVM image builder.
     *
     * @param e error to be reported.
     */
    private static void reportFatalError(Throwable e) {
        System.err.print("Fatal error: ");
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
        if (e instanceof UserException) {
            UserException ue = (UserException) e;
            for (String message : ue.getMessages()) {
                report.accept(message);
            }
        } else {
            report.accept(e.getMessage());
        }
        if (parsedHostedOptions != null && NativeImageOptions.ReportExceptionStackTraces.getValue(parsedHostedOptions)) {
            e.printStackTrace();
        } else {
            report.accept("Use " + SubstrateOptionsParser.commandArgument(NativeImageOptions.ReportExceptionStackTraces, "+") +
                            " to print stacktrace of underlying exception");
        }
    }

    /**
     * Report an informational message in SVM.
     *
     * @param msg message that is printed.
     */
    private static void info(String msg) {
        System.out.println("Info: " + msg);
    }

    /**
     * Report a warning message in SVM.
     *
     * @param msg warning message that is printed.
     */
    private static void warn(String msg) {
        System.err.println("Warning: " + msg);
    }

    @Override
    public int build(String[] args, String[] classpath, NativeImageClassLoader imageClassLoader) {
        return buildImage(args, classpath, imageClassLoader);
    }

    @Override
    public void interruptBuild() {
        final NativeImageGenerator generatorInstance = generator;
        if (generatorInstance != null) {
            generatorInstance.interruptBuild();
        }
    }
}
