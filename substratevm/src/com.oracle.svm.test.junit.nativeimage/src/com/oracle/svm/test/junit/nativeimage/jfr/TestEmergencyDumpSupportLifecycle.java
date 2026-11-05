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

package com.oracle.svm.test.jfr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

import com.oracle.svm.core.jfr.AbstractJfrEmergencyDumpSupport;

public class TestEmergencyDumpSupportLifecycle extends JfrEmergencyDumpTest {
    @Test
    public void testRepeatedInitializeReusesPathBuffer() {
        AbstractJfrEmergencyDumpSupport support = getEmergencyDumpSupport();
        boolean wasInitialized = getPathBufferAddress(support) != 0L;
        support.teardown();
        try {
            support.initialize();
            long firstAddress = getPathBufferAddress(support);
            assertTrue(firstAddress != 0L);

            support.initialize();
            long secondAddress = getPathBufferAddress(support);
            assertEquals(firstAddress, secondAddress);
        } finally {
            support.teardown();
            if (wasInitialized) {
                support.initialize();
            }
        }
    }

    @Test
    public void testNullUserDirUsesRelativeDumpPath() throws Exception {
        AbstractJfrEmergencyDumpSupport support = getEmergencyDumpSupport();
        String originalUserDir = System.getProperty("user.dir");
        Path dumpFile = Path.of("svm_oom_pid_" + ProcessHandle.current().pid() + ".jfr");
        try {
            support.initialize();
            Files.deleteIfExists(dumpFile);

            clearRepositoryLocation(support);
            System.clearProperty("user.dir");
            clearCachedCwd(support);
            support.setDumpPath(null);
            support.setRepositoryLocation("missing-jfr-repository-" + ProcessHandle.current().pid());

            support.onVmError();

            assertTrue("emergency dump file does not exist.", Files.exists(dumpFile));
        } finally {
            Files.deleteIfExists(dumpFile);
            if (originalUserDir == null) {
                System.clearProperty("user.dir");
            } else {
                System.setProperty("user.dir", originalUserDir);
            }
            clearCachedCwd(support);
            support.initialize();
            support.setDumpPath(null);
            clearRepositoryLocation(support);
        }
    }
}
