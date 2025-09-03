/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.ProtectionDomain;

import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.ReflectionUtils;
import com.oracle.truffle.api.test.SubprocessTestUtils;
import com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage;
import com.oracle.truffle.api.test.common.TestUtils;
import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.polyglot.Engine;
import org.graalvm.word.WordFactory;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class SeparatedClassLoadersTest {

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    private ClassLoader loader;

    @Before
    public void storeLoader() {
        loader = Thread.currentThread().getContextClassLoader();
    }

    @Test
    public void sdkAndTruffleAPIInSeparateClassLoaders() {
        ClassLoaders classLoaders = createContextClassLoaders();
        Object contextClassLoaderEngine = createEngineInContextClassLoader(classLoaders);
        try {
            assertNotNull("Engine has been created", contextClassLoaderEngine);
        } finally {
            close(contextClassLoaderEngine);
        }
    }

    /**
     * Verifies that TruffleLogger does not cache language IDs.
     */
    @Test
    @SuppressWarnings("try")
    public void testLoggers() throws Exception {
        /*
         * This test is specific to HotSpot, as native-image has a fixed set of languages determined
         * at build time.
         */
        Assume.assumeFalse(ImageInfo.inImageCode());
        SubprocessTestUtils.newBuilder(SeparatedClassLoadersTest.class, () -> {
            /*
             * The test uses an engine with a custom classloader that does not contain TestLanguage.
             * Therefore, getLogger is expected to fail.
             */
            ClassLoaders classLoaders = createContextClassLoaders();
            Object contextClassLoaderEngine = createEngineInContextClassLoader(classLoaders);
            try {
                AbstractPolyglotTest.assertFails(() -> getLoggerReflective(classLoaders.truffleLoader, TestUtils.getDefaultLanguageId(TestLanguage.class)),
                                IllegalArgumentException.class,
                                (e) -> assertTrue(e.getMessage().startsWith("Unknown language or instrument")));
            } finally {
                close(contextClassLoaderEngine);
            }

            Thread.currentThread().setContextClassLoader(null);
            try (Engine systemClassLoaderEngine = Engine.create()) {
                // Engine using a system classloader must contain TestLanguage
                assertNotNull(TruffleLogger.getLogger(TestUtils.getDefaultLanguageId(TestLanguage.class)));
            }
        }).run();
    }

    private static Object getLoggerReflective(ClassLoader loader, String loggerId) {
        try {
            Class<?> truffleLogger = Class.forName(TruffleLogger.class.getName(), false, loader);
            Method m = truffleLogger.getDeclaredMethod("getLogger", String.class);
            ReflectionUtils.setAccessible(m, true);
            return m.invoke(null, loggerId);
        } catch (InvocationTargetException invocationException) {
            throw sthrow(invocationException.getCause());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends RuntimeException> T sthrow(Throwable t) throws T {
        throw (T) t;
    }

    private record ClassLoaders(ClassLoader sdkLoader, ClassLoader truffleLoader) {
    }

    private static ClassLoaders createContextClassLoaders() {
        final ProtectionDomain polyglotDomain = Engine.class.getProtectionDomain();
        Assume.assumeNotNull(polyglotDomain);
        Assume.assumeNotNull(polyglotDomain.getCodeSource());
        URL polyglotURL = polyglotDomain.getCodeSource().getLocation();
        Assume.assumeNotNull(polyglotURL);

        URL collectionsURL = EconomicMap.class.getProtectionDomain().getCodeSource().getLocation();
        Assume.assumeNotNull(collectionsURL);

        URL wordURL = WordFactory.class.getProtectionDomain().getCodeSource().getLocation();
        Assume.assumeNotNull(wordURL);

        URL nativeURL = ImageInfo.class.getProtectionDomain().getCodeSource().getLocation();
        Assume.assumeNotNull(nativeURL);

        URL truffleURL = Truffle.class.getProtectionDomain().getCodeSource().getLocation();
        Assume.assumeNotNull(truffleURL);

        ClassLoader parent = Engine.class.getClassLoader().getParent();

        URLClassLoader sdkLoader = new URLClassLoader(new URL[]{collectionsURL, wordURL, nativeURL, polyglotURL}, parent);
        URLClassLoader truffleLoader = new URLClassLoader(new URL[]{truffleURL}, sdkLoader);
        return new ClassLoaders(sdkLoader, truffleLoader);
    }

    private static Object createEngineInContextClassLoader(ClassLoaders classLoaders) {
        Thread.currentThread().setContextClassLoader(classLoaders.truffleLoader);
        try {
            Class<?> engineClass = classLoaders.sdkLoader.loadClass(Engine.class.getName());
            return engineClass.getMethod("create").invoke(null);
        } catch (ReflectiveOperationException roe) {
            throw new AssertionError(roe);
        }
    }

    private static void close(Object engine) {
        ReflectionUtils.invoke(engine, "close");
    }

    @After
    public void resetLoader() {
        Thread.currentThread().setContextClassLoader(loader);
    }

    @Registration
    public static final class TestLanguage extends AbstractExecutableTestLanguage {

        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            return true;
        }
    }
}
