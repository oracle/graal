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
package jdk.compiler.graal.core.test;

import static jdk.compiler.graal.test.SubprocessUtil.getVMCommandLine;
import static jdk.compiler.graal.test.SubprocessUtil.java;
import static jdk.compiler.graal.test.SubprocessUtil.withoutDebuggerArguments;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import jdk.compiler.graal.test.SubprocessUtil;
import jdk.compiler.graal.test.SubprocessUtil.Subprocess;

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
        return launchSubprocess(null, null, true, getClass(), runnable, args);
    }

    public SubprocessUtil.Subprocess launchSubprocess(Predicate<String> vmArgsFilter, Runnable runnable, String... args) throws InterruptedException, IOException {
        return launchSubprocess(null, vmArgsFilter, true, getClass(), runnable, args);
    }

    public static SubprocessUtil.Subprocess launchSubprocess(Class<? extends GraalCompilerTest> testClass, Runnable runnable, String... args) throws InterruptedException, IOException {
        return launchSubprocess(null, null, true, testClass, runnable, args);
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
                    Class<? extends GraalCompilerTest> testClass, Runnable runnable, String... args)
                    throws InterruptedException, IOException {
        String recursionPropName = testClass.getName() + ".subprocess";
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

            String verboseProperty = testClass.getName() + ".verbose";
            boolean verbose = Boolean.getBoolean(verboseProperty);
            if (verbose) {
                System.err.println(String.join(" ", vmArgs));
            }
            SubprocessUtil.Subprocess proc = java(vmArgs, "com.oracle.mxtool.junit.MxJUnitWrapper", testClass.getName());
            if (testPredicate != null) {
                assertTrue(testPredicate.test(proc.output), proc.toString() + " produced unexpected output:\n\n" + String.join("\n", proc.output));
            }
            if (verbose) {
                for (String line : proc.output) {
                    System.err.println(line);
                }
            }
            String suffix = "";
            if (!Boolean.getBoolean(SubprocessUtil.KEEP_TEMPORARY_ARGUMENT_FILES_PROPERTY_NAME)) {
                suffix = String.format("%s%n%nSet -D%s=true to preserve subprocess temp files.", suffix, SubprocessUtil.KEEP_TEMPORARY_ARGUMENT_FILES_PROPERTY_NAME);
            }
            if (!verbose) {
                suffix = String.format("%s%n%nSet -D%s=true for verbose output.", suffix, verboseProperty);
            }
            int exitCode = proc.exitCode;
            if (expectNormalExit) {
                assertTrue(exitCode == 0, String.format("%s produced exit code %d, but expected 0.%s", proc, exitCode, suffix));
            } else {
                assertTrue(exitCode != 0, String.format("%s produced normal exit code %d, but expected abnormal exit%s", proc, exitCode, suffix));
            }
            return proc;
        }
    }
}
