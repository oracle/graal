/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.test;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.hotspot.JVMCIVersionCheck;
import jdk.graal.compiler.util.CollectionsUtil;

/**
 * Tests code branches not covered by other {@link JVMCIVersionCheck} tests.
 */
public class JVMCIVersionCheckMiscTest extends GraalCompilerTest {

    public static final String EXPECTED_RELEASE = "Graal expected a JVMCI release version";
    public static final String EXPECTED_MINIMUM_JDK = "Graal requires JDK";
    public static final String EXPECTED_VM_JDK = "The VM does not support JDK";
    public static final String EXPECTED_NO_JVMCI = "Cannot compare JVMCI version";

    /**
     * Gets the base JDK version without the optional "-jvmci-" part.
     */
    private static String getJDKVersion() {
        String versionString = Runtime.version().toString();
        var idx = versionString.indexOf("-jvmci-");
        if (idx >= 0) {
            return versionString.substring(0, idx);
        }
        return versionString;
    }

    @Test
    public void testMinRelease() {
        var props = JVMCIVersionCheckTest.createTestProperties("1", null, null);
        var message = JVMCIVersionCheck.check(props);
        Assert.assertNotNull("unexpectedly passed", message);
        Assert.assertTrue("expected error %s, got %s".formatted(EXPECTED_MINIMUM_JDK, message), message.startsWith(EXPECTED_MINIMUM_JDK));
    }

    @Test
    public void testSnapshot() {
        // Use javaSpecVersion and jdkVersion from the runtime
        String javaSpecVersion = Integer.toString(Runtime.version().feature());
        String jdkVersion = getJDKVersion();
        // expected to pass, because it is a snapshot build
        var props = JVMCIVersionCheckTest.createTestProperties(javaSpecVersion, jdkVersion + "-SNAPSHOT", null);
        var message = JVMCIVersionCheck.check(props);
        Assert.assertNull("unexpected failure: " + message, message);
    }

    @Test
    public void testNonJvmciJdk() {
        // Use javaSpecVersion and jdkVersion from the runtime
        String javaSpecVersion = Integer.toString(Runtime.version().feature());
        String jdkVersion = getJDKVersion();
        // expected to fail because not a JVMCI build
        var props = JVMCIVersionCheckTest.createTestProperties(javaSpecVersion, jdkVersion, null);
        var message = JVMCIVersionCheck.check(props);
        Assert.assertNotNull("unexpectedly passed", message);
        Assert.assertTrue("expected error '%s', got '%s'".formatted(EXPECTED_NO_JVMCI, message), message.contains(EXPECTED_NO_JVMCI));
    }

    @Test
    public void testNoMinVersion() {
        // Use javaSpecVersion and jdkVersion from the runtime
        String javaSpecVersion = Integer.toString(Runtime.version().feature());
        String jdkVersion = getJDKVersion();
        // expected to pass, because it is a snapshot build
        var props = JVMCIVersionCheckTest.createTestProperties(javaSpecVersion, jdkVersion, null);
        JVMCIVersionCheck.check(props, false, null, CollectionsUtil.mapOf());
    }

    @Test
    public void testOpenJDKEmptyMinVersion() {
        // Use javaSpecVersion and jdkVersion from the runtime
        String javaSpecVersion = Integer.toString(Runtime.version().feature());
        String jdkVersion = getJDKVersion();
        // expected to pass, because it is a snapshot build
        var props = JVMCIVersionCheckTest.createTestProperties(javaSpecVersion, jdkVersion, null);
        try {
            JVMCIVersionCheck.check(props, false, null, CollectionsUtil.mapOf("99", CollectionsUtil.mapOf()));
            Assert.fail("unexpectedly passing");
        } catch (InternalError e) {
            var message = e.getMessage();
            Assert.assertTrue("expected error '%s', got '%s'".formatted(EXPECTED_VM_JDK, message), message.contains(EXPECTED_VM_JDK));
        }
    }

    @Test
    public void testInternal() {
        // Use javaSpecVersion and jdkVersion from the runtime
        String javaSpecVersion = Integer.toString(Runtime.version().feature());
        String jdkVersion = getJDKVersion();
        // expected to pass, because it is an internal build
        var props = JVMCIVersionCheckTest.createTestProperties(javaSpecVersion, jdkVersion + "-internal", null);
        var message = JVMCIVersionCheck.check(props);
        Assert.assertNull("unexpected failure: " + message, message);
    }

    /**
     * Tests {@link JVMCIVersionCheck#check(Map)}, which is only used by truffle.
     */
    @Test
    public void testTruffleCheck01() {
        // Use javaSpecVersion and jdkVersion from the runtime
        String javaSpecVersion = Integer.toString(Runtime.version().feature());
        String jdkVersion = getJDKVersion();
        // expected to fail because the release name does not match
        var props = JVMCIVersionCheckTest.createTestProperties(javaSpecVersion, jdkVersion + "-jvmci-11.1-b02", null);
        var message = JVMCIVersionCheck.check(props);
        Assert.assertNotNull("unexpectedly passed", message);
        Assert.assertTrue("expected error '%s', got '%s'".formatted(EXPECTED_RELEASE, message), message.contains(EXPECTED_RELEASE));
    }

    /**
     * Tests {@link JVMCIVersionCheck#check(Map)}, which is only used by truffle.
     */
    @Test
    public void testTruffleCheck02() {
        // Use javaSpecVersion and jdkVersion from the runtime
        String javaSpecVersion = Integer.toString(Runtime.version().feature());
        // expected to pass, because it is newer then specified
        // test case just appends a number to the jvmci build number
        var props = JVMCIVersionCheckTest.createTestProperties(javaSpecVersion, Runtime.version().toString() + "9", null);
        var message = JVMCIVersionCheck.check(props);
        Assert.assertNull("unexpected failure: " + message, message);
    }

}
