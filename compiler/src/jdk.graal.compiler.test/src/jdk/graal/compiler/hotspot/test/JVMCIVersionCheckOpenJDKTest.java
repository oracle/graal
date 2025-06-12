/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.hotspot.JVMCIVersionCheck.DEFAULT_VENDOR_ENTRY;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.hotspot.JVMCIVersionCheck;
import jdk.graal.compiler.util.CollectionsUtil;

/**
 * Tests that {@link JVMCIVersionCheck} handles OpenJDK versions correctly.
 */
@RunWith(Parameterized.class)
public class JVMCIVersionCheckOpenJDKTest extends GraalCompilerTest {

    @Parameterized.Parameters(name = "{0} <= {1} = {2}")
    public static Collection<Object[]> data() {
        return List.of(
                        /*
                         * If comparing a LabsJDK version against an OpenJDK version, ignore the
                         * JVMCI build number.
                         */
                        expectFail("99+99-jvmci-b02", "99+98"),
                        expectPass("99+99-jvmci-b02", "99+99"),
                        expectPass("99+99-jvmci-b02", "99+100"),
                        /*
                         * Also if comparing against an OpenJDK early access version.
                         */
                        expectFail("99+99-jvmci-b02", "99-ea+98"),
                        expectPass("99+99-jvmci-b02", "99-ea+99"),
                        expectPass("99+99-jvmci-b02", "99-ea+100"),
                        /*
                         * OpenJDK version with unknown $PRE value are ignored.
                         */
                        expectPass("99+99-jvmci-b02", "99-something+98"),
                        expectPass("99+99-jvmci-b02", "99-something+99"),
                        expectPass("99+99-jvmci-b02", "99-something+100"),
                        /*
                         * If comparing a LabsJDK version against a LabsJDK version, respect the
                         * JVMCI build.
                         */
                        expectFail("99+99-jvmci-b02", "99+98-jvmci-b01"),
                        expectFail("99+99-jvmci-b02", "99+98-jvmci-b02"),
                        expectFail("99+99-jvmci-b02", "99+98-jvmci-b03"),

                        expectFail("99+99-jvmci-b02", "99+99-jvmci-b01"),
                        expectPass("99+99-jvmci-b02", "99+99-jvmci-b02"),
                        expectPass("99+99-jvmci-b02", "99+99-jvmci-b03"),

                        expectPass("99+99-jvmci-b02", "99+100-jvmci-b01"),
                        expectPass("99+99-jvmci-b02", "99+100-jvmci-b02"),
                        expectPass("99+99-jvmci-b02", "99+100-jvmci-b03"),

                        /* Comparing an OpenJDK version against an OpenJDK version. */
                        expectFail("99+99", "99+98"),
                        expectPass("99+99", "99+99"),
                        expectPass("99+99", "99+100"),

                        /* Comparing an OpenJDK version against a LabsJDK version. */
                        expectFail("99+99", "99+98-jvmci-b01"),
                        expectPass("99+99", "99+99-jvmci-b01"),
                        expectPass("99+99", "99+100-jvmci-b01"));
    }

    private static Object[] expectPass(String minVersion, String javaVmVersion) {
        return new Object[]{minVersion, javaVmVersion, true};
    }

    private static Object[] expectFail(String minVersion, String javaVmVersion) {
        return new Object[]{minVersion, javaVmVersion, false};
    }

    @Parameterized.Parameter(0) public String minVersion;
    @Parameterized.Parameter(1) public String javaVmVersion;
    @Parameterized.Parameter(2) public boolean expectSuccess;

    @Test
    public void compareToMinVersion() {
        if (checkVersionProperties(getMinVersionMap(minVersion), javaVmVersion)) {
            if (!expectSuccess) {
                Assert.fail(String.format("Expected %s to be older than %s", javaVmVersion, minVersion));
            }
        } else {
            if (expectSuccess) {
                Assert.fail(String.format("Expected %s not to be older than %s", javaVmVersion, minVersion));
            }

        }
    }

    private static Map<String, Map<String, JVMCIVersionCheck.Version>> getMinVersionMap(String minVersion) {
        Runtime.Version version = Runtime.Version.parse(minVersion);
        if (version.optional().isEmpty()) {
            // OpenJDK version
            return CollectionsUtil.mapOf(Integer.toString(version.feature()), CollectionsUtil.mapOf(
                            DEFAULT_VENDOR_ENTRY, JVMCIVersionCheck.createOpenJDKVersion(minVersion)));
        } else {
            // LabsJDK version
            String optional = version.optional().get();
            // get the jvmci build number
            Assert.assertTrue("expected jvmci build number", optional.startsWith("jvmci-b"));
            int jvmciBuild = Integer.parseInt(optional.split("jvmci-b", 2)[1]);
            // get the version string without the option part
            String versionWithoutOptional = version.toString().split("-" + optional, 2)[0];
            return CollectionsUtil.mapOf(Integer.toString(version.feature()), CollectionsUtil.mapOf(
                            DEFAULT_VENDOR_ENTRY, JVMCIVersionCheck.createLabsJDKVersion(versionWithoutOptional, jvmciBuild)));
        }
    }

    private static boolean checkVersionProperties(Map<String, Map<String, JVMCIVersionCheck.Version>> minVersionMap, String javaVmVersion) {
        String javaSpecVersion = Integer.toString(Runtime.Version.parse(javaVmVersion).feature());
        var props = JVMCIVersionCheckTest.createTestProperties(javaSpecVersion, javaVmVersion, null);
        try {
            JVMCIVersionCheck.check(props, false, null, minVersionMap);
            return true;
        } catch (InternalError e) {
            return false;
        }
    }

}
