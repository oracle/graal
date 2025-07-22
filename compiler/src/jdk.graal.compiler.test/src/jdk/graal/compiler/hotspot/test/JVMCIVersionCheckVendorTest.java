/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.hotspot.JVMCIVersionCheck;
import jdk.graal.compiler.util.CollectionsUtil;

/**
 * Tests that {@link JVMCIVersionCheck} can have multiple minimum versions for a given
 * {@code java.specification.version}. The different entries are distinguished by the
 * {@code java.vm.vendor} property.
 */
public class JVMCIVersionCheckVendorTest extends GraalCompilerTest {

    private static final Map<String, Map<String, JVMCIVersionCheck.Version>> VERSION_MAP = CollectionsUtil.mapOf("99",
                    CollectionsUtil.mapOf(
                                    DEFAULT_VENDOR_ENTRY, JVMCIVersionCheck.createLabsJDKVersion("99+99", 1),
                                    "Vendor Specific", JVMCIVersionCheck.createLabsJDKVersion("99.0.1", 1)));

    private static void expect(String javaVmVendor, String expected) {
        var props = JVMCIVersionCheckTest.createTestProperties("99", null, javaVmVendor);
        JVMCIVersionCheck.Version minVersion = JVMCIVersionCheck.getMinVersion(props, VERSION_MAP);
        Assert.assertEquals(expected, minVersion.toString());
    }

    @Test
    public void testVendorDefault() {
        expect("Vendor Default", "99+99-jvmci-b01");
    }

    @Test
    public void testVendorSpecific() {
        expect("Vendor Specific", "99.0.1-jvmci-b01");
    }

}
