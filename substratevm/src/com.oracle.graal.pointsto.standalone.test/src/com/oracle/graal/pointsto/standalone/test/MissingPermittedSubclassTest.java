/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, 2026, IBM Inc. All rights reserved.
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

package com.oracle.graal.pointsto.standalone.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

import org.junit.Test;

import com.oracle.graal.pointsto.standalone.test.classes.MissingPermittedSubclassCase;

public class MissingPermittedSubclassTest extends StandaloneAnalysisTest {
    @Test
    public void testMissingPermittedSubclass() throws Exception {
        assumeTrue("The fixture requires an isolated host class loader.",
                        "host".equals(System.getProperty("com.oracle.graal.pointsto.standalone.vmaccess.name", "host")));
        Path classPath = createTestTmpDir();
        String name = MissingPermittedSubclassCase.class.getName();
        for (String suffix : new String[]{"", "$Monitor", "$PresentMonitor"}) {
            String resource = name.replace('.', '/') + suffix + ".class";
            saveFileFromResource("/" + resource, classPath.resolve(resource));
        }
        /* Isolate the fixture so the omitted subclass cannot be found in the test class path. */
        try (URLClassLoader loader = new URLClassLoader(new URL[]{classPath.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Class<?> entryClass = loader.loadClass(name);
            Class<?> monitor = loader.loadClass(name + "$Monitor");
            Class<?> presentMonitor = loader.loadClass(name + "$PresentMonitor");
            assertTrue(monitor.isSealed());
            assertEquals(1, monitor.getPermittedSubclasses().length);
            entryClass.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            assertEquals(3, entryClass.getField("result").getInt(null));

            /* Keep the shared VMAccess configuration while supplying an isolated direct root. */
            String targetClassPath = MissingPermittedSubclassCase.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            runAnalysisMethod(entryClass, "main", new Class<?>[]{String[].class}, "-H:StandaloneAnalysisTargetAppCP=" + targetClassPath);
            var dispatch = findMethod(entryClass, "invoke", monitor);
            assertInvokeCallees(dispatch, findOnlyInvokeBci(dispatch),
                            findMethod(monitor, "value"), findMethod(presentMonitor, "value"));
            assertEquals(1, findClass(monitor).getPermittedSubclasses().size());
            assertEquals(findClass(presentMonitor), findClass(monitor).getPermittedSubclasses().getFirst());
        }
    }
}
