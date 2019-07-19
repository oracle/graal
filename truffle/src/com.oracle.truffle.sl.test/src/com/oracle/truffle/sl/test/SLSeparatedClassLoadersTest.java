/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.sl.SLLanguage;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.ProtectionDomain;
import java.util.Map;
import org.graalvm.polyglot.Engine;
import org.junit.After;
import static org.junit.Assert.assertNotNull;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

public class SLSeparatedClassLoadersTest {
    private ClassLoader loader;

    @Before
    public void storeLoader() {
        loader = Thread.currentThread().getContextClassLoader();
    }

    @Test
    public void sdkAndTruffleLanguageAPIAndSLInSeparateClassLoaders() throws Exception {
        final ProtectionDomain sdkDomain = Engine.class.getProtectionDomain();
        Assume.assumeNotNull(sdkDomain);
        Assume.assumeNotNull(sdkDomain.getCodeSource());
        URL sdkURL = sdkDomain.getCodeSource().getLocation();
        Assume.assumeNotNull(sdkURL);

        URL truffleURL = Truffle.class.getProtectionDomain().getCodeSource().getLocation();
        Assume.assumeNotNull(truffleURL);

        URL slURL = SLLanguage.class.getProtectionDomain().getCodeSource().getLocation();
        Assume.assumeNotNull(slURL);

        ClassLoader parent = Engine.class.getClassLoader().getParent();

        URLClassLoader sdkLoader = new URLClassLoader(new URL[]{sdkURL}, parent);
        boolean sdkLoaderLoadsTruffleLanguage;
        try {
            Class.forName("com.oracle.truffle.api.TruffleLanguage", false, sdkLoader);
            sdkLoaderLoadsTruffleLanguage = true;
        } catch (ClassNotFoundException cnf) {
            sdkLoaderLoadsTruffleLanguage = false;
        }
        Assume.assumeFalse(sdkLoaderLoadsTruffleLanguage);
        URLClassLoader truffleLoader = new URLClassLoader(new URL[]{truffleURL}, sdkLoader);
        URLClassLoader slLoader = new URLClassLoader(new URL[]{slURL}, truffleLoader);
        Thread.currentThread().setContextClassLoader(slLoader);

        Class<?> engineClass = sdkLoader.loadClass(Engine.class.getName());
        Object engine = engineClass.getMethod("create").invoke(null);
        assertNotNull("Engine has been created", engine);

        Map<?, ?> languages = (Map<?, ?>) engineClass.getMethod("getLanguages").invoke(engine);
        Object lang = languages.get("sl");
        assertNotNull("SL language found: " + languages, lang);
    }

    @After
    public void resetLoader() {
        Thread.currentThread().setContextClassLoader(loader);
    }
}
