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
package jdk.graal.compiler.hotspot.test;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.hotspot.JVMCIVersionCheck;

/**
 * Test handling of version components bigger than Integer.MAX_VALUE.
 */
public class JVMCIVersionCheckMaxValueTest extends GraalCompilerTest {

    public static final String EXPECTED_MSG = "Cannot read JVMCI version from java.vm.version property";

    @Test
    public void testLegacyVersion() {
        for (String version : new String[]{"20.0-b" + Long.MAX_VALUE, "20." + Long.MAX_VALUE + "-b1", Long.MAX_VALUE + ".0-b1"}) {
            testVersion(String.format("prefix-jvmci-%s-suffix", version));
        }
    }

    @Test
    public void testNewVersion() {
        // We only want to test jvmciBuild, not Runtime.Version, so we use a fixed jdkVersion string
        testVersion(String.format("99.0.1-jvmci-b%s-suffix", Long.MAX_VALUE));
    }

    private static void testVersion(String javaVmVersion) {
        try {
            JVMCIVersionCheck.Version minVersion = new JVMCIVersionCheck.Version(20, 0, 1);
            // Use a javaSpecVersion that will likely not fail in the near future
            String javaSpecVersion = "99";
            var props = JVMCIVersionCheckTest.createTestProperties(javaSpecVersion, javaVmVersion, null);
            var jvmciMinVersions = Map.of(
                            javaSpecVersion, Map.of(JVMCIVersionCheck.DEFAULT_VENDOR_ENTRY, minVersion));
            JVMCIVersionCheck.check(props, false, null, jvmciMinVersions);
            String value = System.getenv("JVMCI_VERSION_CHECK");
            if (!"warn".equals(value) && !"ignore".equals(value)) {
                Assert.fail("expected to fail checking " + javaVmVersion + " against " + minVersion);
            }
        } catch (InternalError e) {
            if (!e.getMessage().contains(EXPECTED_MSG)) {
                throw new AssertionError("Unexpected exception message. Expected: " + EXPECTED_MSG, e);
            }
        }
    }
}
