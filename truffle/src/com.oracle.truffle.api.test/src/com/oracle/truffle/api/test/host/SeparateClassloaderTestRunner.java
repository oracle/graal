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
