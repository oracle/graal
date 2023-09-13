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
package org.graalvm.compiler.hotspot.test;

import java.util.Map;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.hotspot.JVMCIVersionCheck;
import org.junit.Assert;
import org.junit.Test;

public class JVMCIVersionCheckMaxValueTest extends GraalCompilerTest {
    @Test
    public void test01() {
        Map<String, String> props = JVMCIVersionCheckTest.props;
        // Test handling of version components bigger than Integer.MAX_VALUE
        for (String version : new String[]{"20.0." + Long.MAX_VALUE, "20." + Long.MAX_VALUE + ".0", Long.MAX_VALUE + ".0.0"}) {
            String javaVmVersion = String.format("prefix-jvmci-%s-suffix", version);
            try {
                JVMCIVersionCheck.Version minVersion = new JVMCIVersionCheck.Version(20, 0, 1);
                // Use a javaSpecVersion that will likely not fail in the near future
                JVMCIVersionCheck.check(props, minVersion, "99", javaVmVersion, false);
                Assert.fail("expected to fail checking " + javaVmVersion + " against " + minVersion);
            } catch (InternalError e) {
                String expectedMsg = "Cannot read JVMCI version from java.vm.version property";
                if (!e.getMessage().contains(expectedMsg)) {
                    throw new AssertionError("Unexpected exception message. Expected: " + expectedMsg, e);
                }
            }
        }
    }
}
