/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
package com.oracle.svm.test.jmx;

import java.lang.management.ManagementFactory;
import java.util.List;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ProcessProperties;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class GetInputArgumentsTest {
    private static final String PROPERTY_KEY = "svm.test.inputArguments";
    private static final String PROPERTY_VALUE = "test-value";
    private static final String PROPERTY_ARGUMENT = "-D" + PROPERTY_KEY + "=" + PROPERTY_VALUE;
    private static final String MIN_HEAP_SIZE = "-XX:MinHeapSize=100m";
    private static final String MAX_HEAP_SIZE = "-XX:MaxHeapSize=100m";

    @Test
    public void testGetInputArguments() throws Exception {
        Assume.assumeTrue("native image runtime only", ImageInfo.inImageRuntimeCode());

        if (PROPERTY_VALUE.equals(System.getProperty(PROPERTY_KEY))) {
            // This code will only be executed in the spawned child process (see below).
            List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
            Assert.assertTrue("Must have three arguments", args.size() == 3);
            Assert.assertTrue("First argument != " + MIN_HEAP_SIZE, MIN_HEAP_SIZE.equals(args.get(0)));
            Assert.assertTrue("Second argument != " + PROPERTY_ARGUMENT, PROPERTY_ARGUMENT.equals(args.get(1)));
            Assert.assertTrue("Third argument != " + MAX_HEAP_SIZE, MAX_HEAP_SIZE.equals(args.get(2)));
            return;
        }

        // Recursively execute this test with specific command line arguments and verify that
        // RuntimeMXBean().getInputArguments() returns them correctly.
        Process process = new ProcessBuilder(
                        ProcessProperties.getExecutableName(),
                        MIN_HEAP_SIZE,
                        PROPERTY_ARGUMENT,
                        MAX_HEAP_SIZE,
                        "--run-explicit",
                        GetInputArgumentsTest.class.getName())
                        .redirectErrorStream(true)
                        .start();

        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        Assert.assertEquals("Child native image failed:\n" + output, 0, exitCode);
    }
}
