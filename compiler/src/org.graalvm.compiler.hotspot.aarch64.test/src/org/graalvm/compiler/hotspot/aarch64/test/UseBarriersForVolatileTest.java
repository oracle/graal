/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, Red Hat Inc. All rights reserved.
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

package org.graalvm.compiler.hotspot.aarch64.test;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.MembarNode;
import org.graalvm.compiler.nodes.memory.MemoryCheckpoint;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.test.SubprocessUtil;
import org.graalvm.word.LocationIdentity;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.graalvm.compiler.test.SubprocessUtil.getProcessCommandLine;
import static org.graalvm.compiler.test.SubprocessUtil.withoutDebuggerArguments;

/**
 * A test class to exercise AArch64 volatile reads and writes in a subordinate JVM where OpenJDK
 * config setting UseBarriersForVolatile is set to true.
 *
 * The single test in this class creates a new JVM to run only the tests in a specific secondary
 * test class. It uses the same JVM configuration, test runner and test configuration that it was
 * started with, modulo a small set of options added to the command line. The important changes are
 * to reset the OpenJDK option UseBarriersForVolatile from the default value false to true amd to
 * replace the original list of test classes with the secondary test class.
 *
 * The secondary test class is actually a static inner class of this class. This presents the
 * problem that test annotations on the inner class make it eligible for execution in the original
 * JVM as well as in the subordinate JVM. This problem is finessed by defining a system property
 * whose name derives from the test class name and making the test succeed without executing when
 * the property is not set. The relevant system property is not set in the outer JVM but is set by
 * the command line used to create the subordinate JVM.
 */
public class UseBarriersForVolatileTest extends GraalCompilerTest {
    /**
     * Tests compilation requested by the VM.
     */
    static class Probe {
        final String substring;
        final int expectedOccurrences;
        int actualOccurrences;
        String lastMatchingLine;

        Probe(String substring, int expectedOccurrences) {
            this.substring = substring;
            this.expectedOccurrences = expectedOccurrences;
        }

        boolean matches(String line) {
            if (line.contains(substring)) {
                actualOccurrences++;
                lastMatchingLine = line;
                return true;
            }
            return false;
        }

        String test() {
            return expectedOccurrences == actualOccurrences ? null : String.format("expected %d, got %d occurrences", expectedOccurrences, actualOccurrences);
        }
    }

    @Test
    public void testVarHandlesInSubJVM() throws IOException, InterruptedException {
        // the criterion for success is that all 4 tests finish ok
        Probe successProbe = new Probe("OK (2 tests)", 1);
        Probe testReadProbe = new Probe("Testing: testReadSnippet", 1);
        Probe testWriteProbe = new Probe("Testing: testWriteSnippet", 1);
        List<Probe> probes = Arrays.asList(testReadProbe, testWriteProbe, successProbe);
        List<String> extraOpts = Arrays.asList("-XX:+UseBarriersForVolatile");
        // run the tests belonging to inner class Internal in a subordinate test JVM
        testHelper(probes, extraOpts, Internal.class.getName());
    }

    private static final boolean VERBOSE = Boolean.getBoolean(UseBarriersForVolatileTest.class.getSimpleName() + ".verbose");

    // the name of the JUnit runner class we expect to find on the original command line
    private static final String JUNIT_MAIN_CLASS_NAME = "com.oracle.mxtool.junit.MxJUnitWrapper";

    // the property used to inhibit execution of desired tests in the main
    // JVM and enable execution of desired tests in the subordinate JVM
    private static final String ENABLE_SUBORDINATE_TESTS_PROPERTY = UseBarriersForVolatileTest.class.getName() + ".enable.subordinate.tests";

    /**
     * Gets the command line used to start the current tests, including all VM arguments, the test
     * runner class and its option flags. This can be used to spawn an identical test run for the
     * secondary test class.
     */
    public static List<String> getVMTestCommandLine() {
        List<String> args = getProcessCommandLine();
        if (args == null) {
            throw new InternalError("empty command line not expected!");
        } else {
            int index = findJUnitTestFilesIndex(args);
            return args.subList(0, index);
        }
    }

    private static int findJUnitTestFilesIndex(List<String> commandLine) {
        int i = 1; // Skip the java executable
        // inlcude all args up to and including the test runner class
        while (i < commandLine.size()) {
            String s = commandLine.get(i);
            if (JUNIT_MAIN_CLASS_NAME.equals(s)) {
                break;
            } else if (hasArg(s)) {
                i += 2;
            } else {
                i++;
            }
        }
        if (i == commandLine.size()) {
            throw new InternalError("Could not find " + JUNIT_MAIN_CLASS_NAME + " on the command line for the current process");
        }
        i++;
        // skip all option args to the test runner class
        while (i < commandLine.size()) {
            String s = commandLine.get(i);
            if (s.charAt(0) != '-') {
                // this index identifies the first test class or an @file spec
                return i;
            } else if (hasArg(s)) {
                i += 2;
            } else {
                i++;
            }
        }
        throw new InternalError("Could not find index of test class or @file argument");
    }

    private static boolean hasArg(String optionName) {
        if (optionName.equals("-cp") ||
                        optionName.equals("-classpath") ||
                        optionName.equals("--classpath") ||
                        optionName.equals("-p") ||
                        optionName.equals("--module-path") ||
                        optionName.equals("--ugrade-module-path") ||
                        optionName.equals("--add-modules")) {
            return true;
        }
        return false;
    }

    // run test on the provided class in a subordinate JVM with the extra
    // VM args provided and validate the output using the supplied probes
    private static void testHelper(List<Probe> probes, List<String> extraVmArgs, String... mainClassAndArgs) throws IOException, InterruptedException {
        List<String> vmArgs = withoutDebuggerArguments(getVMTestCommandLine());

        int s = extraVmArgs.size();
        // extra JVM options need to be added before the test runner class
        int idx = vmArgs.indexOf(JUNIT_MAIN_CLASS_NAME);
        Assert.assertTrue(idx > 0);

        for (int i = 0; i < s; i++) {
            vmArgs.add(idx + i, extraVmArgs.get(i));
        }
        // setting this property enables tests on Internal in the sub-process
        vmArgs.add(idx + s, "-D" + ENABLE_SUBORDINATE_TESTS_PROPERTY);

        SubprocessUtil.Subprocess proc = SubprocessUtil.java(vmArgs, mainClassAndArgs);
        if (VERBOSE) {
            System.out.println(proc);
        }

        for (String line : proc.output) {
            for (UseBarriersForVolatileTest.Probe probe : probes) {
                if (probe.matches(line)) {
                    break;
                }
            }
        }
        for (UseBarriersForVolatileTest.Probe probe : probes) {
            String error = probe.test();
            if (error != null) {
                Assert.fail(String.format("Did not find expected occurences of '%s' in output of command: %s%n%s", probe.substring, error, proc));
            }
        }
    }

    /**
     * An inner class used as the target for test runs in the subordinate JVM.
     *
     * The tests in this class are automatically added to the list of tests to be run in the main
     * JVM. A system property is used to disable them in that JVM and enable them in the subordinate
     * JVM
     */
    public static class Internal extends GraalCompilerTest {

        static class Holder {
            volatile int volatileField = 42;
        }

        public static int testReadSnippet(Internal.Holder h) {
            return h.volatileField;
        }

        public static void testWriteSnippet(Internal.Holder h) {
            h.volatileField = 123;
        }

        void testAccess(String name, int expectedReads, int expectedWrites, int expectedMembars, int expectedAnyKill) {
            ResolvedJavaMethod method = getResolvedJavaMethod(name);
            StructuredGraph graph = parseForCompile(method);
            compile(method, graph);
            Assert.assertEquals(expectedReads, graph.getNodes().filter(ReadNode.class).count());
            Assert.assertEquals(expectedWrites, graph.getNodes().filter(WriteNode.class).count());
            Assert.assertEquals(expectedMembars, graph.getNodes().filter(MembarNode.class).count());
            Assert.assertEquals(expectedAnyKill, countAnyKill(graph));
        }

        @Test
        public void testReadAArch64() {
            // run this test on AArch64
            Assume.assumeTrue(getTarget().arch instanceof AArch64);
            // only run when enabled in subordinate JVM
            Assume.assumeTrue(System.getProperty(ENABLE_SUBORDINATE_TESTS_PROPERTY) != null);
            RuntimeProvider runtimeProvider = Graal.getRequiredCapability(RuntimeProvider.class);
            HotSpotGraalRuntimeProvider hotSpotProvider = (HotSpotGraalRuntimeProvider) runtimeProvider;
            Assert.assertTrue(hotSpotProvider.getVMConfig().useBarriersForVolatile);
            System.out.println("Testing: testReadSnippet");
            testAccess("testReadSnippet", 1, 0, 2, 2);
        }

        @Test
        public void testWriteAArch64() {
            // run this test on AArch64
            Assume.assumeTrue(getTarget().arch instanceof AArch64);
            // only run when enabled in subordinate JVM
            Assume.assumeTrue(System.getProperty(ENABLE_SUBORDINATE_TESTS_PROPERTY) != null);
            RuntimeProvider runtimeProvider = Graal.getRequiredCapability(RuntimeProvider.class);
            HotSpotGraalRuntimeProvider hotSpotProvider = (HotSpotGraalRuntimeProvider) runtimeProvider;
            Assert.assertTrue(hotSpotProvider.getVMConfig().useBarriersForVolatile);
            System.out.println("Testing: testWriteSnippet");
            testAccess("testWriteSnippet", 0, 1, 2, 2);
        }

        private static int countAnyKill(StructuredGraph graph) {
            int anyKillCount = 0;
            int startNodes = 0;
            for (Node n : graph.getNodes()) {
                if (n instanceof StartNode) {
                    startNodes++;
                } else if (n instanceof MemoryCheckpoint.Single) {
                    MemoryCheckpoint.Single single = (MemoryCheckpoint.Single) n;
                    if (single.getLocationIdentity().isAny()) {
                        anyKillCount++;
                    }
                } else if (n instanceof MemoryCheckpoint.Multi) {
                    MemoryCheckpoint.Multi multi = (MemoryCheckpoint.Multi) n;
                    for (LocationIdentity loc : multi.getLocationIdentities()) {
                        if (loc.isAny()) {
                            anyKillCount++;
                            break;
                        }
                    }
                }
            }
            // Ignore single StartNode.
            Assert.assertEquals(1, startNodes);
            return anyKillCount;
        }
    }
}
