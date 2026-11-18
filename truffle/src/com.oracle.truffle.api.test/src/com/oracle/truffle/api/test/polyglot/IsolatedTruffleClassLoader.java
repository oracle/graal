/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertSame;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.function.Supplier;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.impl.DefaultTruffleRuntime;
import com.oracle.truffle.api.test.ReflectionUtils;
import com.oracle.truffle.api.test.TestAPIAccessor;
import com.oracle.truffle.polyglot.PolyglotImpl;

/**
 * Defines Truffle and Polyglot classes locally while delegating Truffle compiler interfaces to the
 * parent class loader. Classes are read from the test class path when available and otherwise from
 * the legacy GraalVM JDK image.
 */
final class IsolatedTruffleClassLoader extends URLClassLoader {

    private static final String TRUFFLE_PACKAGE_PREFIX = "com.oracle.truffle.";
    private static final String TRUFFLE_COMPILER_PACKAGE_PREFIX = "com.oracle.truffle.compiler.";
    private static final String POLYGLOT_PACKAGE_PREFIX = "org.graalvm.polyglot.";

    private final LoadedTestClasses testClasses;
    private final String polyglotImplResource;
    private final URL polyglotImplUrl;

    IsolatedTruffleClassLoader(String name, URL[] urls) {
        super(name, urls, ClassLoader.getPlatformClassLoader());
        testClasses = loadTestClasses();
        polyglotImplResource = "META-INF/services/" + testClasses.abstractPolyglotImpl().getName();
        polyglotImplUrl = createServiceProviderUrl(polyglotImplResource, testClasses.polyglotImpl().getName());
    }

    LoadedTestClasses testClasses() {
        return testClasses;
    }

    @Override
    public URL findResource(String name) {
        URL resource = super.findResource(name);
        if (resource == null) {
            if (name.equals(polyglotImplResource)) {
                resource = polyglotImplUrl;
            } else {
                resource = ClassLoader.getSystemResource(name);
            }
        }
        return resource;
    }

    @Override
    public Enumeration<URL> findResources(String name) throws IOException {
        Enumeration<URL> resources = super.findResources(name);
        LinkedHashSet<URL> result = new LinkedHashSet<>(Collections.list(resources));
        Enumeration<URL> systemResources = ClassLoader.getSystemResources(name);
        while (systemResources.hasMoreElements()) {
            result.add(systemResources.nextElement());
        }
        if (result.isEmpty() && name.equals(polyglotImplResource)) {
            result.add(polyglotImplUrl);
        }
        return Collections.enumeration(result);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        boolean isolatedTruffleClass = (name.startsWith(TRUFFLE_PACKAGE_PREFIX) && !name.startsWith(TRUFFLE_COMPILER_PACKAGE_PREFIX)) ||
                        name.startsWith(POLYGLOT_PACKAGE_PREFIX);
        if (!isolatedTruffleClass) {
            return super.loadClass(name, resolve);
        }
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                try {
                    loaded = findClass(name);
                } catch (ClassNotFoundException e) {
                    loaded = findSystemClassResource(name);
                }
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

    private LoadedTestClasses loadTestClasses() {
        try {
            Class<?> holder = Class.forName(TestClasses.class.getName(), true, this);
            if (holder.getClassLoader() != this) {
                throw new AssertionError(String.format("%s was loaded by %s instead of %s", TestClasses.class.getName(), holder.getClassLoader(), this));
            }
            return new LoadedTestClasses(
                            readTestClass(holder, "TRUFFLE"),
                            (Supplier<?>) ReflectionUtils.getStaticField(holder, "GET_MODULES_ACCESSOR"),
                            readTestClass(holder, "ABSTRACT_POLYGLOT_IMPL"),
                            readTestClass(holder, "POLYGLOT_IMPL"),
                            readTestClass(holder, "POLYGLOT_CONTEXT"));
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    private static Class<?> readTestClass(Class<?> holder, String fieldName) {
        return (Class<?>) ReflectionUtils.getStaticField(holder, fieldName);
    }

    record LoadedTestClasses(
                    Class<?> truffle,
                    Supplier<?> getModulesAccessor,
                    Class<?> abstractPolyglotImpl,
                    Class<?> polyglotImpl,
                    Class<?> polyglotContext) {
    }

    static final class TestClasses {
        static final Class<?> TRUFFLE = Truffle.class;
        static final Supplier<Accessor.ModulesAccessor> GET_MODULES_ACCESSOR = TestClasses::getModulesAccessor;
        static final Class<?> ABSTRACT_POLYGLOT_IMPL = AbstractPolyglotImpl.class;
        static final Class<?> POLYGLOT_IMPL = PolyglotImpl.class;
        static final Class<?> POLYGLOT_CONTEXT = Context.class;

        private TestClasses() {
        }

        /**
         * Invokes {@link Accessor.EngineSupport#getModulesAccessor()} which loads the
         * {@code truffle_attach} library.
         */
        private static Accessor.ModulesAccessor getModulesAccessor() {
            ClassLoader loader = TestClasses.class.getClassLoader();
            Thread.currentThread().setContextClassLoader(loader);
            assertSame(loader, Truffle.class.getClassLoader());
            TruffleRuntime runtime = Truffle.getRuntime();
            assertSame(DefaultTruffleRuntime.class, runtime.getClass());
            assertSame(loader, runtime.getClass().getClassLoader());
            Accessor.EngineSupport engineSupport = TestAPIAccessor.ENGINE;
            assertSame(loader, engineSupport.getClass().getClassLoader());
            Accessor.ModulesAccessor accessor = engineSupport.getModulesAccessor();
            assertNotNull("libtruffleattach failed to load for " + loader.getName() + ": " +
                            engineSupport.getModuleAccessorInitializationError(), accessor);
            return accessor;
        }
    }

    @SuppressWarnings("deprecation")
    private static URL createServiceProviderUrl(String resourceName, String providerName) {
        byte[] content = (providerName + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        try {
            return new URL(null, "memory:/" + resourceName, new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(URL url) {
                    return new URLConnection(url) {
                        @Override
                        public void connect() {
                        }

                        @Override
                        public InputStream getInputStream() {
                            return new ByteArrayInputStream(content);
                        }
                    };
                }
            });
        } catch (MalformedURLException e) {
            throw new AssertionError(e);
        }
    }

    private Class<?> findSystemClassResource(String name) throws ClassNotFoundException {
        String resourceName = name.replace('.', '/') + ".class";
        try (InputStream in = ClassLoader.getSystemResourceAsStream(resourceName)) {
            if (in == null) {
                throw new ClassNotFoundException(name);
            }
            byte[] bytes = in.readAllBytes();
            return defineClass(name, bytes, 0, bytes.length);
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
    }
}
