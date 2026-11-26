/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.nativeimage.ImageInfo;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.svm.core.jdk.localization.LocalizationSupport;

public class FileEncodingCompatTest {
    private static final String FILE_ENCODING_COMPAT = "COMPAT";
    private static final String CHILD_PROCESS_PROPERTY = "com.oracle.svm.test.fileEncodingCompatChild";
    private static final String EXPECTED_NATIVE_ENCODING = "com.oracle.svm.test.expectedNativeEncoding";
    private static final String EXPECTED_JNU_ENCODING = "com.oracle.svm.test.expectedJnuEncoding";
    private static final String EXPECTED_FILE_ENCODING = "com.oracle.svm.test.expectedFileEncoding";
    private static final String EXPECTED_DEFAULT_CHARSET = "com.oracle.svm.test.expectedDefaultCharset";
    private static final String LINUX = "Linux";
    private static final String C_LOCALE = "C";

    @Test
    public void testFileEncodingCompatIsNormalized() throws IOException, InterruptedException {
        Assume.assumeTrue("native image runtime only", ImageInfo.inImageRuntimeCode());

        String childMode = System.getProperty(CHILD_PROCESS_PROPERTY);
        if (childMode == null) {
            runChild(false);
            runChild(true);
        } else if ("compat".equals(childMode)) {
            assertFileEncodingCompatIsNormalized();
        } else {
            assertEncodingPropertiesUnchanged();
        }
    }

    private static void runChild(boolean compat) throws IOException, InterruptedException {
        String executable = ProcessHandle.current().info().command().orElseThrow(() -> new AssertionError("Could not locate the native unittest executable"));
        List<String> command = new ArrayList<>();
        command.add(executable);
        if (compat) {
            command.add("-Dfile.encoding=" + FILE_ENCODING_COMPAT);
        } else {
            command.add("-D" + EXPECTED_NATIVE_ENCODING + "=" + System.getProperty("native.encoding"));
            command.add("-D" + EXPECTED_JNU_ENCODING + "=" + System.getProperty("sun.jnu.encoding"));
            command.add("-D" + EXPECTED_FILE_ENCODING + "=" + System.getProperty("file.encoding"));
            command.add("-D" + EXPECTED_DEFAULT_CHARSET + "=" + Charset.defaultCharset().name());
        }
        command.add("-D" + CHILD_PROCESS_PROPERTY + "=" + (compat ? "compat" : "baseline"));
        command.add("--verbose");
        command.add("--run-explicit");
        command.add(FileEncodingCompatTest.class.getName());
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (LINUX.equals(System.getProperty("os.name"))) {
            processBuilder.environment().put("LC_ALL", C_LOCALE);
        }
        Process process = processBuilder.redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        Assert.assertEquals("native image startup failed:\n" + output, 0, exitCode);
        Assert.assertTrue("native image child did not execute the test:\n" + output, output.contains("testFileEncodingCompatIsNormalized: Passed"));
        if (!compat) {
            Assert.assertFalse("native encoding selection ran without COMPAT:\n" + output, output.contains("Warning: The expected native.encoding"));
        }
    }

    private static void assertEncodingPropertiesUnchanged() {
        Assert.assertEquals(System.getProperty(EXPECTED_NATIVE_ENCODING), System.getProperty("native.encoding"));
        Assert.assertEquals(System.getProperty(EXPECTED_JNU_ENCODING), System.getProperty("sun.jnu.encoding"));
        Assert.assertEquals(System.getProperty(EXPECTED_FILE_ENCODING), System.getProperty("file.encoding"));
        Assert.assertEquals(System.getProperty(EXPECTED_DEFAULT_CHARSET), Charset.defaultCharset().name());
    }

    private static void assertFileEncodingCompatIsNormalized() {
        String nativeEncoding = System.getProperty("native.encoding");
        String fileEncoding = System.getProperty("file.encoding");
        Assert.assertNotNull("native.encoding must be initialized", nativeEncoding);
        Assert.assertNotNull("native.encoding must identify a charset included in the image", LocalizationSupport.lookupCharset(nativeEncoding));
        Assert.assertEquals(StandardCharsets.UTF_8.name(), LocalizationSupport.canonicalizeCharsetName("UTF8"));
        if (LINUX.equals(System.getProperty("os.name")) && C_LOCALE.equals(System.getenv("LC_ALL"))) {
            Assert.assertEquals(StandardCharsets.US_ASCII.name(), LocalizationSupport.canonicalizeCharsetName(nativeEncoding));
        }
        Assert.assertNotEquals("file.encoding must be resolved before application startup", FILE_ENCODING_COMPAT, fileEncoding);
        Assert.assertEquals(nativeEncoding, fileEncoding);
        Assert.assertEquals(LocalizationSupport.canonicalizeCharsetName(fileEncoding), Charset.defaultCharset().name());
    }
}
