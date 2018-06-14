/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Set;

import org.junit.Test;

import com.oracle.truffle.api.impl.TruffleLocator;

public class IsolatedClassLoaderTest {
    private static final boolean JDK8 = System.getProperty("java.specification.version").compareTo("1.9") < 0;

    @Test
    public void loadLanguageByOwnClassLoaderOnJDK8() throws Exception {
        final ProtectionDomain domain = TruffleLocator.class.getProtectionDomain();
        final CodeSource source = domain.getCodeSource();
        if (source == null || !JDK8) {
            // skip the test
            return;
        }
        final URL truffleURL = source.getLocation();
        final String locatorName = "com.oracle.truffle.api.impl.TruffleLocator";
        ClassLoader loader = new URLClassLoader(new URL[]{truffleURL}) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (name.startsWith(locatorName)) {
                    return super.findClass(name);
                }
                return super.loadClass(name);
            }
        };
        Class<?> locator = loader.loadClass(locatorName);
        assertEquals("Right classloader", loader, locator.getClassLoader());

        final Method loadersMethod = locator.getDeclaredMethod("loaders");
        ReflectionUtils.setAccessible(loadersMethod, true);
        Set<?> loaders = (Set<?>) loadersMethod.invoke(null);
        assertTrue("Contains locator's loader: " + loaders, loaders.contains(loader));
        assertTrue("Contains system loader: " + loader, loaders.contains(ClassLoader.getSystemClassLoader()));
    }
}
