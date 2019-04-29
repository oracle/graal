/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import java.io.File;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import java.net.URL;
import java.net.URLClassLoader;

import org.junit.Test;

import java.lang.reflect.Field;
import static org.junit.Assert.assertTrue;

public class TruffleOptionsByClassLoaderTest {

    @Test
    public void loadTruffleOptionsByOwnClassLoader() throws Exception {
        String cp = System.getProperty("java.class.path");
        File truffleAPI = null;
        for (String p : cp.split(File.pathSeparator)) {
            if (p.endsWith("truffle-api.jar")) {
                truffleAPI = new File(p);
                assertTrue("File really exists: " + truffleAPI, truffleAPI.exists());
                break;
            }
        }

        assertNotNull("Found Truffle API: " + truffleAPI, truffleAPI);
        final String truffleOptionsName = "com.oracle.truffle.api.TruffleOptions";
        ClassLoader loader = new URLClassLoader(new URL[]{truffleAPI.toURI().toURL()}) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (name.startsWith(truffleOptionsName)) {
                    return super.findClass(name);
                }
                return super.loadClass(name);
            }
        };
        Class<?> truffleOptions = loader.loadClass(truffleOptionsName);
        assertEquals("Right classloader", loader, truffleOptions.getClassLoader());

        final Field aotField = truffleOptions.getField("AOT");
        Object aot = aotField.get(null);
        assertEquals("Not running in AOT", Boolean.FALSE, aot);
    }
}
