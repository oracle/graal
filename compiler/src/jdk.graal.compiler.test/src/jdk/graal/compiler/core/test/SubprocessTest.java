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

import jdk.graal.compiler.test.SubprocessUtil;
import jdk.graal.compiler.test.SubprocessUtil.Subprocess;

/**
 * Utility class for executing Graal compiler tests in a subprocess. This can be useful for tests
 * that need special VM arguments or that produce textual output or a special process termination
 * status that need to be analyzed. The class to be executed may be the current class or any other
 * unit test class.
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
     * Launches the {@code runnable} in a subprocess, with any extra {@code args} passed as
     * arguments to the subprocess VM. Checks that the subprocess terminated successfully, i.e., an
     * exit code different from 0 raises an error.
     *
     * @return Inside the subprocess, returns {@code null}. Outside the subprocess, returns a
     *         {@link Subprocess} instance describing the process after its successful termination.
     */
    public SubprocessUtil.Subprocess launchSubprocess(Runnable runnable, String... args) throws InterruptedException, IOException {
        return launchSubprocess(null, null, true, getClass(), null, runnable, args);
    }

    /**
     * Like {@link #launchSubprocess(Runnable, String...)}, but with an extra {@code testSelector}
     * to specify a specific test method to execute in the test class.
     */
    public SubprocessUtil.Subprocess launchSubprocess(String testSelector, Runnable runnable, String... args) throws InterruptedException, IOException {
        return launchSubprocess(null, null, true, getClass(), testSelector, runnable, args);
    }

    public SubprocessUtil.Subprocess launchSubprocess(Predicate<String> vmArgsFilter, Runnable runnable, String... args) throws InterruptedException, IOException {
        return launchSubprocess(null, vmArgsFilter, true, getClass(), null, runnable, args);
    }

    public static SubprocessUtil.Subprocess launchSubprocess(Class<? extends GraalCompilerTest> testClass, Runnable runnable, String... args) throws InterruptedException, IOException {
        return launchSubprocess(null, null, true, testClass, null, runnable, args);
    }

    public static SubprocessUtil.Subprocess launchSubprocess(Predicate<List<String>> testPredicate, Predicate<String> vmArgsFilter, boolean expectNormalExit,
                    Class<? extends GraalCompilerTest> testClass, Runnable runnable, String... args)
                    throws InterruptedException, IOException {
        return launchSubprocess(testPredicate, vmArgsFilter, expectNormalExit, testClass, null, runnable, args);
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

    public static SubprocessUtil.Subprocess launchSubprocess(Predicate<List<String>> testPredicate, Predicate<String> vmArgsFilter, boolean expectNormalExit,
                    Class<? extends GraalCompilerTest> testClass, String testSelector, Runnable runnable, String... args)
                    throws InterruptedException, IOException {
        String recursionPropName = "test." + testClass.getName() + ".subprocess";
        if (Boolean.getBoolean(recursionPropName)) {
            runnable.run();
            return null;
        } else {
            List<String> vmArgs = withoutDebuggerArguments(getVMCommandLine());
            vmArgs.add(SubprocessUtil.PACKAGE_OPENING_OPTIONS);
            vmArgs.add("-D" + recursionPropName + "=true");
            vmArgs.addAll(Arrays.asList(args));
            if (vmArgsFilter != null) {
                vmArgs = filter(vmArgs, vmArgsFilter);
            }

            List<String> mainClassAndArgs = new LinkedList<>();
            mainClassAndArgs.add("com.oracle.mxtool.junit.MxJUnitWrapper");
            String testName = testClass.getName();
            if (testSelector != null) {
                testName += "#" + testSelector;
            }
            mainClassAndArgs.add(testName);
            boolean junitVerbose = getProcessCommandLine().contains("-JUnitVerbose");
            if (junitVerbose) {
                mainClassAndArgs.add("-JUnitVerbose");
            }
            SubprocessUtil.Subprocess proc = java(vmArgs, mainClassAndArgs);

            if (testPredicate != null && !testPredicate.test(proc.output)) {
                fail("Subprocess produced unexpected output:%n%s", proc.preserveArgfile());
            }
            int exitCode = proc.exitCode;
            if ((exitCode == 0) != expectNormalExit) {
                String expectExitCode = expectNormalExit ? "0" : "non-0";
                fail("Subprocess produced exit code %d, but expected %s%n%s", exitCode, expectExitCode, proc.preserveArgfile());
            }

            // Test passed
            if (junitVerbose) {
                System.out.printf("%n%s%n", proc.preserveArgfile());
            }
            return proc;
        }
    }
}
