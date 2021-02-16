/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
        final String locatorName = "com.oracle.truffle.polyglot.EngineAccessor";
        ClassLoader loader = new URLClassLoader(new URL[]{truffleURL}) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (name.startsWith(locatorName)) {
                    return super.findClass(name);
                }
                return super.loadClass(name);
            }
        };
        Class<?> locatorClass = loader.loadClass(locatorName);
        assertEquals("Right classloader", loader, locatorClass.getClassLoader());

        final Method loadersMethod = locatorClass.getDeclaredMethod("locatorOrDefaultLoaders");
        ReflectionUtils.setAccessible(loadersMethod, true);
        @SuppressWarnings("unchecked")
        Set<ClassLoader> loaders = ((List<Supplier<ClassLoader>>) loadersMethod.invoke(null)).stream().map(Supplier::get).collect(Collectors.toSet());
        assertTrue("Contains locator's loader: " + loaders, loaders.contains(loader));
        assertTrue("Contains system loader: " + loader, loaders.contains(ClassLoader.getSystemClassLoader()));
    }
}
