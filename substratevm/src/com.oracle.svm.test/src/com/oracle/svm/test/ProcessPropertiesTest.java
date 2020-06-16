/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.ProcessProperties;
import org.junit.Assert;
import org.junit.Test;

public class ProcessPropertiesTest {
    @Test
    public void testGetExecutableName() {
        String execName = ProcessProperties.getExecutableName();
        Assert.assertNotNull("Executable name cannot be null.", execName);
        Assert.assertTrue(execName.contains("junit"));
    }

    @Test
    public void testGetPID() {
        long pid = ProcessProperties.getProcessID();
        Assert.assertTrue("Invalid pid.", pid > 0);
    }

    /**
     * Test ProcessProperties.setLocale(). See
     * <a href="http://pubs.opengroup.org/onlinepubs/9699919799/functions/setlocale.html">setLocale
     * specification</a> for details.
     */
    @Test
    public void testSetLocale() {
        /* Get the default locale. */
        String before = ProcessProperties.setLocale("LC_ALL", null);
        Assert.assertTrue("Default locale is wrong.", before.equals("C") || before.equals("POSIX"));

        /* Set locale to a new value. */
        ProcessProperties.setLocale("LC_ALL", "en_US.UTF-8");

        /* Get the locale value again. */
        String after = ProcessProperties.setLocale("LC_ALL", null);
        Assert.assertEquals("Locale is wrong.", "en_US.UTF-8", after);
    }
}
