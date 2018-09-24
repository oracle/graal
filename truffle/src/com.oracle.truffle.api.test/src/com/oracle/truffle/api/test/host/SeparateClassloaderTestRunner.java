/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.host;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;

import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;

public final class SeparateClassloaderTestRunner extends BlockJUnit4ClassRunner {
    public SeparateClassloaderTestRunner(Class<?> clazz) throws InitializationError {
        super(getFromTestClassloader(clazz));
    }

    private static final String JAVA_INTEROP_PACKAGE = "com.oracle.truffle.api.interop.java";

    private static final boolean JDK8OrEarlier = System.getProperty("java.specification.version").compareTo("1.9") < 0;

    private static Class<?> getFromTestClassloader(Class<?> clazz) throws InitializationError {
        try {
            ClassLoader testClassLoader = JDK8OrEarlier ? new PreJDK9TestClassLoader() : new JDK9TestClassLoader();
            return Class.forName(clazz.getName(), true, testClassLoader);
        } catch (ClassNotFoundException e) {
            throw new InitializationError(e);
        }
    }

    public static final class Theories extends org.junit.experimental.theories.Theories {
        public Theories(Class<?> clazz) throws InitializationError {
            super(getFromTestClassloader(clazz));
        }
    }

    private static class PreJDK9TestClassLoader extends URLClassLoader {
        PreJDK9TestClassLoader() {
            super(((URLClassLoader) getSystemClassLoader()).getURLs());
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (name.startsWith(JAVA_INTEROP_PACKAGE)) {
                return super.findClass(name);
            }
            return super.loadClass(name);
        }
    }

    /**
     * As of JDK9, Truffle API is deployed in a module (possibly the unnamed module) so to retrieve
     * the class file bytes for separate class loading requires using module API. The latter is done
     * via reflection so that this class still compiles on JDK8.
     */
    private static class JDK9TestClassLoader extends ClassLoader {

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (name.startsWith(JAVA_INTEROP_PACKAGE)) {
                return findClassInModule(name);
            }
            return super.loadClass(name);
        }

        /**
         * Reflective call to {@code java.lang.Class.getModule()}.
         */
        private static Object getModule(Class<?> cls) {
            try {
                return Class.class.getMethod("getModule").invoke(cls);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                throw new AssertionError(e);
            }
        }

        /**
         * Reflective call to {@code java.lang.reflect.Module.getResourceAsStream(String name)}.
         */
        private static InputStream getResourceAsStream(Object module, String name) {
            try {
                return (InputStream) module.getClass().getMethod("getResourceAsStream", String.class).invoke(module, name);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                throw new AssertionError(e);
            }
        }

        protected Class<?> findClassInModule(String name) throws ClassNotFoundException {
            Class<?> classInModule = super.loadClass(name);
            Object module = getModule(classInModule);
            String res = name.replace('.', '/') + ".class";
            InputStream is = getResourceAsStream(module, res);
            if (is == null) {
                throw new ClassNotFoundException(name);
            }
            byte[] arr = readAllBytes(name, is);
            return defineClass(name, arr, 0, arr.length, getClass().getProtectionDomain());
        }
    }

    static byte[] readAllBytes(String name, InputStream is) throws ClassNotFoundException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] arr = new byte[4096];
        for (;;) {
            int len;
            try {
                len = is.read(arr);
            } catch (IOException ex) {
                throw new ClassNotFoundException(name);
            }
            if (len < 0) {
                break;
            }
            os.write(arr, 0, len);
        }
        arr = os.toByteArray();
        return arr;
    }
}
