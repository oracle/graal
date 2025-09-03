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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.hotspot.JVMCIVersionCheck;
import jdk.graal.compiler.hotspot.JVMCIVersionCheck.Version;
import jdk.graal.compiler.util.CollectionsUtil;
import jdk.graal.compiler.util.EconomicHashMap;

@RunWith(Parameterized.class)
public class JVMCIVersionCheckTest extends GraalCompilerTest {

    private static final String[] JDK_VERSIONS = {
                    null,
                    "99",
                    "99+3",
                    "99.0.1+3",
                    "99-ea+11-790"
    };

    static final Map<String, String> PROPS;
    static {
        Properties sprops = System.getProperties();
        Map<String, String> propsLocal = new EconomicHashMap<>(sprops.size());
        for (String name : sprops.stringPropertyNames()) {
            propsLocal.put(name, sprops.getProperty(name));
        }
        PROPS = Collections.unmodifiableMap(propsLocal);
    }

    static Map<String, String> createTestProperties(String javaSpecVersion, String javaVmVersion, String javaVmVendor) {
        var props = (Map<String, String>) new EconomicHashMap<>(JVMCIVersionCheckTest.PROPS);
        if (javaSpecVersion != null) {
            props.put("java.specification.version", javaSpecVersion);
        }
        if (javaVmVersion != null) {
            props.put("java.vm.version", javaVmVersion);
        }
        if (javaVmVendor != null) {
            props.put("java.vm.vendor", javaVmVendor);
        }
        return Collections.unmodifiableMap(props);
    }

    @Parameters(name = "{0} vs {1}")
    public static Collection<Object[]> data() {
        List<Object[]> ret = new ArrayList<>();
        Random random = getRandomInstance();

        for (String minJdkVersion : JDK_VERSIONS) {
            for (String jdkVersion : JDK_VERSIONS) {
                for (int i = 0; i < (minJdkVersion == null ? 50 : 1); i++) {
                    int minMajor = i;
                    int minMinor = 50 - i;
                    for (int j = 0; j < (jdkVersion == null ? 50 : 1); j++) {
                        int major = j;
                        int minor = 50 - j;

                        for (int k = 0; k < 30; k++) {
                            int minBuild = random.nextInt(100);
                            int build = random.nextInt(100);

                            Version version = getVersion(jdkVersion, major, minor, build);
                            Version minVersion = getVersion(minJdkVersion, minMajor, minMinor, minBuild);
                            ret.add(new Object[]{version, minVersion});
                        }
                    }
                }
            }
        }
        return ret;
    }

    private static Version getVersion(String jdkVersion, int major, int minor, int build) {
        if (jdkVersion != null) {
            // new version scheme
            return JVMCIVersionCheck.createLabsJDKVersion(jdkVersion, build);
        } else {
            // legacy version scheme
            return JVMCIVersionCheck.createLegacyVersion(major, minor, build);
        }
    }

    @Parameter(value = 0) public Version version;
    @Parameter(value = 1) public Version minVersion;

    @Test
    public void test01() {
        String legacyPrefix = version.toString().startsWith("jvmci") ? "prefix-" : "";
        String javaVmVersion = legacyPrefix + version.toString() + "Suffix";
        String javaSpecVersion = "99";
        var props = createTestProperties(javaSpecVersion, javaVmVersion, null);
        var jvmciMinVersions = CollectionsUtil.mapOf(
                        javaSpecVersion, CollectionsUtil.mapOf(JVMCIVersionCheck.DEFAULT_VENDOR_ENTRY, minVersion));
        if (!version.isLessThan(minVersion)) {
            try {
                JVMCIVersionCheck.check(props, false, null, jvmciMinVersions);
            } catch (InternalError e) {
                throw new AssertionError("Failed " + JVMCIVersionCheckTest.class.getSimpleName(), e);
            }
        } else {
            try {
                JVMCIVersionCheck.check(props, false, null, jvmciMinVersions);
                String value = System.getenv("JVMCI_VERSION_CHECK");
                if (!"warn".equals(value) && !"ignore".equals(value)) {
                    Assert.fail("expected to fail checking " + javaVmVersion + " against " + minVersion);
                }
            } catch (InternalError e) {
                // pass
            }
        }
    }
}
