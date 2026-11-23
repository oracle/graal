/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2025, Red Hat Inc. All rights reserved.
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
package com.oracle.svm.test.junit.shared.jmx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

import org.graalvm.nativeimage.ImageInfo;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.svm.core.VMInspectionOptions;

public class PlatformMXBeanTest {
    @BeforeClass
    public static void requireJmxSupport() {
        assumeTrue("Platform MXBean proxies require JMX support", !ImageInfo.inImageRuntimeCode() || (VMInspectionOptions.hasJmxClientSupport() && VMInspectionOptions.hasJmxServerSupport()));
    }

    @Test
    public void testLoadedClassCount() throws IOException {
        ClassLoadingMXBean bean = ManagementFactory.getPlatformMXBean(ManagementFactory.getPlatformMBeanServer(), ClassLoadingMXBean.class);
        int loadedClassCount = bean.getLoadedClassCount();
        if (ImageInfo.inImageRuntimeCode()) {
            assertEquals(0, loadedClassCount);
        } else {
            assertTrue("HotSpot should report loaded classes", loadedClassCount > 0);
        }
    }

    @Test
    public void testCurrentThreadUserTime() throws IOException {
        ThreadMXBean bean = ManagementFactory.getPlatformMXBean(ManagementFactory.getPlatformMBeanServer(), ThreadMXBean.class);
        assumeTrue("Current thread CPU time must be supported and enabled", bean.isCurrentThreadCpuTimeSupported() && bean.isThreadCpuTimeEnabled());
        assertTrue("Current thread user time should be nonnegative", bean.getCurrentThreadUserTime() >= 0);
    }
}
