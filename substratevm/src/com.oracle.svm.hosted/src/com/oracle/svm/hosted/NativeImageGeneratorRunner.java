/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import static com.oracle.svm.hosted.NativeImageGenerator.defaultPlatform;
import static com.oracle.svm.hosted.server.NativeImageBuildServer.IMAGE_CLASSPATH_PREFIX;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;

import jdk.vm.ci.amd64.AMD64;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;
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
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.UserError.UserException;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.c.GraalAccess;
import com.oracle.svm.hosted.code.CEntryPointData;
import com.oracle.svm.hosted.image.AbstractBootImage;
import com.oracle.svm.hosted.option.HostedOptionParser;

public class NativeImageGeneratorRunner implements ImageBuildTask {

    public static void main(String[] args) {
        ArrayList<String> arguments = new ArrayList<>(Arrays.asList(args));
        final String[] classpath = extractImageClassPath(arguments);
        URLClassLoader bootImageClassLoader = installURLClassLoader(classpath);

        int exitStatus = new NativeImageGeneratorRunner().build(arguments.toArray(new String[arguments.size()]), classpath, bootImageClassLoader);
        System.exit(exitStatus == 0 ? 0 : 1);
    }

    public static URLClassLoader installURLClassLoader(String[] classpath) {
        URLClassLoader bootImageClassLoader;
        ClassLoader applicationClassLoader = Thread.currentThread().getContextClassLoader();
        bootImageClassLoader = new URLClassLoader(verifyClassPathAndConvertToURLs(classpath), applicationClassLoader);
        Thread.currentThread().setContextClassLoader(bootImageClassLoader);
        return bootImageClassLoader;
    }

    public static String[] extractImageClassPath(List<String> arguments) {
        int cpIndex = arguments.indexOf(IMAGE_CLASSPATH_PREFIX);
        if (cpIndex == -1) {
            throw UserError.abort("Classpath must be provided as an argument after '" + IMAGE_CLASSPATH_PREFIX + "'.");
        }
        if (cpIndex + 1 >= arguments.size()) {
            throw UserError.abort("Classpath must be provided after the '" + IMAGE_CLASSPATH_PREFIX + "' argument.\n");
        }

        String[] classpath = arguments.get(cpIndex + 1).split(File.pathSeparator);
        arguments.remove(cpIndex);
        arguments.remove(cpIndex);
        return classpath;
    }

    private static URL[] verifyClassPathAndConvertToURLs(String[] classpath) {
        return new HashSet<>(Arrays.asList(classpath)).stream().map(v -> {
            try {
                return Paths.get(v).toAbsolutePath().toUri().toURL();
            } catch (MalformedURLException e) {
                throw UserError.abort("Invalid classpath element '" + v + "'. Make sure that all paths provided with '" + IMAGE_CLASSPATH_PREFIX + "' are correct.");
            }
        }).toArray(URL[]::new);
    }

    public static boolean isValidJavaVersion() {
        String versionString = getJavaVersion();
        if (versionString.startsWith("1.8")) {
            String[] splitVersion = versionString.split("_");
            int update = Integer.valueOf(splitVersion[1]);
            return update > 40;
        } else {
            return false;
        }
    }

    private static void reportToolUserError(String msg) {
        reportUserError("native-image" + msg);
    }

    private static boolean isValidArchitecture() {
        return GraalAccess.getOriginalTarget().arch instanceof AMD64;
    }

    private static boolean isValidOperatingSystem() {
        return OS.getCurrent() == OS.LINUX || OS.getCurrent() == OS.DARWIN;
    }

    @SuppressWarnings("try")
    private static int buildImage(String[] arguments, String[] classpath, ClassLoader classLoader) {
        if (!verifyValidJavaVersionAndPlatform()) {
            return -1;
        }
        Timer totalTimer = new Timer("[total]", false);
        try (StopTimer ignored = totalTimer.start()) {
            ImageClassLoader imageClassLoader;
            Timer classlistTimer = new Timer("classlist", false);
            try (StopTimer ignored1 = classlistTimer.start()) {
                imageClassLoader = ImageClassLoader.create(defaultPlatform(), classpath, classLoader);
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
            OptionValues parsedHostedOptions = new OptionValues(optionParser.getHostedValues());
            DebugContext debug = DebugContext.create(parsedHostedOptions, new GraalDebugHandlersFactory(GraalAccess.getOriginalSnippetReflection()));

            String imageName = NativeImageOptions.Name.getValue(parsedHostedOptions);
            if (imageName.length() == 0) {
                throw UserError.abort("No output file name specified. " +
                                "Use '" + HostedOptionParser.commandArgument(NativeImageOptions.Name, "<output-file>") + "'.");
            }

            // print the time here to avoid interactions with flags processing
            classlistTimer.print();

            Map<Method, CEntryPointData> entryPoints = new HashMap<>();
            Method mainEntryPoint = null;
            JavaMainSupport javaMainSupport = null;

            AbstractBootImage.NativeImageKind k = AbstractBootImage.NativeImageKind.valueOf(NativeImageOptions.Kind.getValue(parsedHostedOptions));
            if (k == AbstractBootImage.NativeImageKind.EXECUTABLE) {
                String className = NativeImageOptions.Class.getValue(parsedHostedOptions);
                if (className == null || className.length() == 0) {
                    throw UserError.abort("Must specify main entry point class when building " + AbstractBootImage.NativeImageKind.EXECUTABLE + " native image. " +
                                    "Use '" + HostedOptionParser.commandArgument(NativeImageOptions.Class, "<fully-qualified-class-name>") + "'.");
                }
                Class<?> mainClass;
                try {
                    mainClass = Class.forName(className, false, classLoader);
                } catch (ClassNotFoundException ex) {
                    throw UserError.abort("Main entry point class '" + className + "' not found.");
                }

                String mainEntryPointName = NativeImageOptions.Method.getValue(parsedHostedOptions);
                if (mainEntryPointName == null || mainEntryPointName.length() == 0) {
                    throw UserError.abort("Must specify main entry point method when building " + AbstractBootImage.NativeImageKind.EXECUTABLE + " native image. " +
                                    "Use '" + HostedOptionParser.commandArgument(NativeImageOptions.Method, "<method-name>") + "'.");
                }

                try {
                    /* First look for an main method with the C-level signature for arguments. */
                    mainEntryPoint = mainClass.getDeclaredMethod(mainEntryPointName, int.class, CCharPointerPointer.class);
                } catch (NoSuchMethodException ignored2) {
                    try {
                        /*
                         * If no C-level main method was found, look for a Java-level main method
                         * and use our wrapper to invoke it.
                         */
                        Method javaMainMethod = mainClass.getDeclaredMethod(mainEntryPointName, String[].class);
                        javaMainMethod.setAccessible(true);
                        if (javaMainMethod.getReturnType() != void.class) {
                            throw UserError.abort("Java main method must have return type void. Change the return type of method '" + mainClass.getName() + "." + mainEntryPointName + "(String[])'.");
                        }
                        final int mainMethodModifiers = javaMainMethod.getModifiers();
                        if (!Modifier.isPublic(mainMethodModifiers)) {
                            throw UserError.abort("Method '" + mainClass.getName() + "." + mainEntryPointName + "(String[])' is not accessible.  Please make it 'public'.");
                        }
                        javaMainSupport = new JavaMainSupport(MethodHandles.lookup().unreflect(javaMainMethod));
                        mainEntryPoint = JavaMainWrapper.class.getDeclaredMethod("run", int.class, CCharPointerPointer.class);
                    } catch (NoSuchMethodException ex) {
                        throw UserError.abort("Method '" + mainClass.getName() + "." + mainEntryPointName + "' is declared as the main entry point but it can not be found. " +
                                        "Make sure that class '" + mainClass.getName() + "' is on the classpath and that method '" + mainEntryPointName + "(String[])' exists in that class.");
                    }
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
            ForkJoinPool analysisExecutor = Inflation.createExecutor(debug, NativeImageOptions.getMaximumNumberOfAnalysisThreads(parsedHostedOptions));
            ForkJoinPool compilationExecutor = Inflation.createExecutor(debug, maxConcurrentThreads);
            NativeImageGenerator generator = new NativeImageGenerator(imageClassLoader);
            generator.run(optionParser, entryPoints, mainEntryPoint, javaMainSupport, imageName, k, SubstitutionProcessor.IDENTITY,
                            analysisExecutor, compilationExecutor, optionParser.getRuntimeOptionNames());
        } catch (InterruptImageBuilding e) {
            e.getReason().ifPresent(NativeImageGeneratorRunner::info);
            return 0;
        } catch (UserException e) {
            e.getMessages().iterator().forEachRemaining(NativeImageGeneratorRunner::reportUserError);
            return -1;
        } catch (AnalysisError e) {
            NativeImageGeneratorRunner.reportUserError(e.getMessage());
            return -1;
        } catch (ParallelExecutionException pee) {
            boolean hasUserError = false;
            for (Throwable exception : pee.getExceptions()) {
                if (exception instanceof UserException) {
                    ((UserException) exception).getMessages().iterator().forEachRemaining(NativeImageGeneratorRunner::reportUserError);
                    hasUserError = true;
                } else if (exception instanceof AnalysisError) {
                    NativeImageGeneratorRunner.reportUserError(exception.getMessage());
                    hasUserError = true;
                }
            }
            if (hasUserError) {
                return -1;
            }

            if (pee.getExceptions().size() > 1) {
                System.err.println(pee.getExceptions().size() + " fatal errors detected:");
            }
            for (Throwable exception : pee.getExceptions()) {
                NativeImageGeneratorRunner.reportFatalError(exception);
            }
            return -1;
        } catch (Throwable e) {
            NativeImageGeneratorRunner.reportFatalError(e);
            return -1;
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
            reportToolUserError("runs on Linux and Mac OS X only. Detected OS: " + System.getProperty("os.name"));
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
        System.err.print("fatal error: ");
        e.printStackTrace();
    }

    /**
     * Function for reporting all fatal errors in SVM.
     *
     * @param msg error message that is printed.
     */
    public static void reportUserError(String msg) {
        System.err.println("error: " + msg);
    }

    /**
     * Report an informational message in SVM.
     *
     * @param msg error message that is printed.
     */
    private static void info(String msg) {
        System.out.println("info: " + msg);
    }

    @Override
    public int build(String[] args, String[] classpath, ClassLoader compilationClassLoader) {
        return buildImage(args, classpath, compilationClassLoader);
    }
}
