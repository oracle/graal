/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import static jdk.graal.compiler.test.SubprocessUtil.getProcessCommandLine;
import static jdk.graal.compiler.test.SubprocessUtil.getVMCommandLine;
import static jdk.graal.compiler.test.SubprocessUtil.java;
import static jdk.graal.compiler.test.SubprocessUtil.withoutDebuggerArguments;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;

import jdk.graal.compiler.test.GraalTest;
import jdk.graal.compiler.test.SubprocessUtil;
import jdk.graal.compiler.test.SubprocessUtil.Subprocess;

/**
 * Utility class for executing Graal compiler tests in a subprocess. This can be useful for tests
 * that need special VM arguments or that produce textual output or a special process termination
 * status that need to be analyzed. Another use case is a test that behaves very differently when
 * run after many other tests (that fill up the heap and pollute profiles). The class to be executed
 * may be the current class or any other unit test class.
 * <p/>
 * If the test class contains multiple {@code @Test} methods, they will all be executed in the
 * subprocess, except when using one of the methods that take a {@code testSelector} argument. All
 * methods in this class take a {@link Runnable} argument. If the test class is the same as the
 * calling class, this runnable defines the operation to be executed in the subprocess. If the test
 * class is not the same as the calling class, the runnable is irrelevant and can be a nop.
 * <p/>
 * The subprocess will inherit any {@code -JUnitVerbose} flag (typically set through
 * {@code mx unittest --verbose}) from the parent process. If this flag is set, the standard output
 * of the child process will be echoed to the standard output of the parent process. If the child
 * process terminates with an error, its standard output will always be printed.
 */
public abstract class SubprocessTest extends GraalCompilerTest {

    /**
     * Calls {@link #launchSubprocess(Predicate, boolean, Class, String, Runnable, String...)} with
     * the given args, {@code vmArgsFilter=null}, {@code check=true}, {@code testClass=getClass()}
     * and {@code testSelector=currentUnitTestName()}.
     */
    public SubprocessUtil.Subprocess launchSubprocess(Runnable runnable, String... extraVmArgs) throws InterruptedException, IOException {
        return launchSubprocess(null, true, getClass(), currentUnitTestName(), runnable, extraVmArgs);
    }

    /**
     * Calls {@link #launchSubprocess(Predicate, boolean, Class, String, Runnable, String...)} with
     * the given args, {@code vmArgsFilter=null} and {@code check=true}.
     */
    public static SubprocessUtil.Subprocess launchSubprocess(
                    Class<? extends GraalCompilerTest> testClass,
                    String testSelector,
                    Runnable runnable,
                    String... extraVmArgs) throws InterruptedException, IOException {
        return launchSubprocess(null, true, testClass, testSelector, runnable, extraVmArgs);
    }

    private static List<String> filter(List<String> args, Predicate<String> vmArgsFilter) {
        List<String> result = new ArrayList<>(args.size());
        for (String arg : args) {
            if (vmArgsFilter.test(arg)) {
                result.add(arg);
            }
        }
        return result;
    }

    /**
     * Sentinel value meaning all tests in the specified test class are to be run.
     */
    public static final String ALL_TESTS = "ALL_TESTS";

    public boolean isRecursiveLaunch() {
        return isRecursiveLaunch(getClass());
    }

    private static boolean isRecursiveLaunch(Class<? extends GraalCompilerTest> testClass) {
        return Boolean.getBoolean(getRecursionPropName(testClass));
    }

    private static String getRecursionPropName(Class<? extends GraalCompilerTest> testClass) {
        return "test." + testClass.getName() + ".subprocess";
    }

    /**
     * Launches {@code runnable} in a subprocess.
     *
     * @param runnable task to be run in the subprocess
     * @param vmArgsFilter filters the VM args to only those matching this predicate
     * @param check if true, and the process exits with a non-zero exit code, an AssertionError
     *            exception will be thrown
     * @param testClass the class defining the test
     * @param testSelector name of the current test. This is typically provided by
     *            {@link GraalTest#currentUnitTestName()}. Use {@link #ALL_TESTS} to denote that all
     *            tests in {@code testClass} are to be run.
     * @param extraVmArgs extra VM args to pass to the subprocess
     * @return returns {@code null} when run in the subprocess. Outside the subprocess, returns a
     *         {@link Subprocess} instance describing the process after its successful termination.
     */
    public static SubprocessUtil.Subprocess launchSubprocess(
                    Predicate<String> vmArgsFilter,
                    boolean check,
                    Class<? extends GraalCompilerTest> testClass,
                    String testSelector,
                    Runnable runnable,
                    String... extraVmArgs) throws InterruptedException, IOException {
        if (isRecursiveLaunch(testClass)) {
            runnable.run();
            return null;
        } else {
            List<String> vmArgs = withoutDebuggerArguments(getVMCommandLine());
            vmArgs.add(SubprocessUtil.PACKAGE_OPENING_OPTIONS);
            vmArgs.add("-D" + getRecursionPropName(testClass) + "=true");
            vmArgs.addAll(Arrays.asList(extraVmArgs));
            if (vmArgsFilter != null) {
                vmArgs = filter(vmArgs, vmArgsFilter);
            }

            List<String> mainClassAndArgs = new LinkedList<>();
            mainClassAndArgs.add("com.oracle.mxtool.junit.MxJUnitWrapper");
            assert testSelector != null : "must pass the name of the current unit test";
            String testName = testSelector.equals(ALL_TESTS) ? testClass.getName() : testClass.getName() + "#" + testSelector;
            mainClassAndArgs.add(testName);
            boolean junitVerbose = String.valueOf(getProcessCommandLine()).contains("-JUnitVerbose");
            if (junitVerbose) {
                mainClassAndArgs.add("-JUnitVerbose");
            }
            SubprocessUtil.Subprocess proc = java(vmArgs, mainClassAndArgs);

            int exitCode = proc.exitCode;
            if (check && exitCode != 0) {
                fail("Subprocess produced non-0 exit code %d%n%s", exitCode, proc.preserveArgfile());
            }

            // Test passed
            if (junitVerbose) {
                System.out.printf("%n%s%n", proc.preserveArgfile());
            }
            return proc;
        }
    }
}
