/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.graalvm.compiler.core.GraalCompilerOptions;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.test.SubprocessUtil;
import org.graalvm.compiler.test.SubprocessUtil.Subprocess;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import org.junit.Assert;
import org.junit.Test;

/**
 * Support for executing Truffle tests in a sub-process with filtered compilation failure options.
 * This support is useful for tests that explicitly set
 * {@link PolyglotCompilerOptions#CompilationFailureAction} and can be affected by junit arguments.
 * It's also useful for tests expecting compiler failure to prevent useless graal graph dumping.
 *
 * Usage example:
 *
 * <pre>
 * &#64;Test
 * public void testCompilationFailure() throws Exception {
 *     Runnable testToExecuteInSubprocess = () -> {
 *         setupContext(Context.newBuilder().allowExperimentalOptions(true).option("engine.CompileImmediately", "true").option("engine.BackgroundCompilation", "false").option(
 *                         "engine.CompilationFailureAction", "Throw"));
 *         testCallTarget.call();
 *     };
 *     SubprocessTestUtils.executeInSubprocess(getClass(), testToExecuteInSubprocess);
 * }
 * </pre>
 */
public final class SubprocessTestUtils {

    private static final String CONFIGURED_PROPERTY = SubprocessTestUtils.class.getSimpleName() + ".configured";

    private static final String TO_REMOVE_PREFIX = "~~";

    private SubprocessTestUtils() {
    }

    /**
     * Executes action in a sub-process with filtered compilation failure options.
     *
     * @param testClass the test enclosing class.
     * @param action the test to execute.
     * @param additionalVmOptions additional vm option added to java arguments. Prepend
     *            {@link #TO_REMOVE_PREFIX} to remove item from existing vm options.
     * @return {@link Subprocess} if it's called by a test that is not executing in a sub-process.
     *         Returns {@code null} for a caller run in a sub-process.
     * @see SubprocessTestUtils
     */
    public static Subprocess executeInSubprocess(Class<?> testClass, Runnable action, String... additionalVmOptions) throws IOException, InterruptedException {
        return executeInSubprocess(testClass, action, true, additionalVmOptions);
    }

    /**
     * Executes action in a sub-process with filtered compilation failure options.
     *
     * @param testClass the test enclosing class.
     * @param action the test to execute.
     * @param failOnNonZeroExitCode if {@code true}, the test fails if the sub-process ends with a
     *            non-zero return value.
     * @param additionalVmOptions additional vm option added to java arguments. Prepend
     *            {@link #TO_REMOVE_PREFIX} to remove item from existing vm options.
     * @return {@link Subprocess} if it's called by a test that is not executing in a sub-process.
     *         Returns {@code null} for a caller run in a sub-process.
     * @see SubprocessTestUtils
     */
    public static Subprocess executeInSubprocess(Class<?> testClass, Runnable action, boolean failOnNonZeroExitCode, String... additionalVmOptions) throws IOException, InterruptedException {
        if (isSubprocess()) {
            action.run();
            return null;
        } else {
            return execute(findTestMethod(testClass), failOnNonZeroExitCode, additionalVmOptions);
        }
    }

    /**
     * Returns {@code true} if it's called by a test that is already executing in a sub-process.
     */
    public static boolean isSubprocess() {
        return Boolean.getBoolean(CONFIGURED_PROPERTY);
    }

    private static Method findTestMethod(Class<?> testClass) {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        if (stack != null) {
            for (int i = stack.length - 1; i >= 0; i--) {
                StackTraceElement element = stack[i];
                if (testClass.getName().equals(element.getClassName())) {
                    try {
                        Method method = testClass.getDeclaredMethod(element.getMethodName());
                        if (method.getAnnotation(Test.class) != null) {
                            return method;
                        }
                    } catch (NoSuchMethodException noSuchMethodException) {
                        // skip methods with arguments.
                    }
                }
            }
        }
        throw new IllegalStateException("Failed to find current test method in class " + testClass);
    }

    private static Subprocess execute(Method testMethod, boolean failOnNonZeroExitCode, String... additionalVmOptions) throws IOException, InterruptedException {
        String enclosingElement = testMethod.getDeclaringClass().getName();
        String testName = testMethod.getName();
        SubprocessUtil.Subprocess subprocess = SubprocessUtil.java(
                        configure(getVmArgs(), additionalVmOptions),
                        "com.oracle.mxtool.junit.MxJUnitWrapper",
                        String.format("%s#%s", enclosingElement, testName));
        if (failOnNonZeroExitCode && subprocess.exitCode != 0) {
            Assert.fail(String.join("\n", subprocess.output));
        }
        return subprocess;
    }

    private static List<String> configure(List<String> vmArgs, String... additionalVmOptions) {
        List<String> newVmArgs = new ArrayList<>();
        newVmArgs.addAll(vmArgs.stream().filter(vmArg -> {
            for (String toRemove : getForbiddenVmOptions()) {
                if (vmArg.startsWith(toRemove)) {
                    return false;
                }
            }
            for (String additionalVmOption : additionalVmOptions) {
                if (additionalVmOption.startsWith(TO_REMOVE_PREFIX) && vmArg.startsWith(additionalVmOption.substring(2))) {
                    return false;
                }
            }
            return true;
        }).collect(Collectors.toList()));
        for (String additionalVmOption : additionalVmOptions) {
            if (!additionalVmOption.startsWith(TO_REMOVE_PREFIX)) {
                newVmArgs.add(1, additionalVmOption);
            }
        }
        newVmArgs.add(1, String.format("-D%s=%s", CONFIGURED_PROPERTY, "true"));
        return newVmArgs;
    }

    private static List<String> getVmArgs() {
        List<String> vmArgs = SubprocessUtil.getVMCommandLine(true);
        vmArgs.add(SubprocessUtil.PACKAGE_OPENING_OPTIONS);
        return vmArgs;
    }

    private static String[] getForbiddenVmOptions() {
        return new String[]{
                        graalOption(GraalCompilerOptions.CompilationFailureAction.getName()),
                        graalOption(GraalCompilerOptions.CompilationBailoutAsFailure.getName()),
                        graalOption(GraalCompilerOptions.CrashAt.getName()),
                        graalOption(DebugOptions.DumpOnError.getName()),
                        // Filter out the LogFile option to prevent overriding of the unit tests log
                        // file by a sub-process.
                        graalOption("LogFile"), // HotSpotTTYStreamProvider.Options#LogFile
                        "-Dpolyglot.log.file",
                        engineOption("CompilationFailureAction"),
                        engineOption("TraceCompilation"),
                        engineOption("TraceCompilationDetails")
        };
    }

    private static String graalOption(String optionName) {
        return "-Dgraal." + optionName;
    }

    private static String engineOption(String optionName) {
        return "-Dpolyglot.engine." + optionName;
    }
}
