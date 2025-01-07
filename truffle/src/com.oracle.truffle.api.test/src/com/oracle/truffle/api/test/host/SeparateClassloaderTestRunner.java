/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;

public final class SeparateClassloaderTestRunner extends BlockJUnit4ClassRunner {
    public SeparateClassloaderTestRunner(Class<?> clazz) throws InitializationError {
        super(getFromTestClassloader(clazz));
    }

    private static final String JAVA_INTEROP_PACKAGE = "com.oracle.truffle.api.interop.java";

    private static Class<?> getFromTestClassloader(Class<?> clazz) throws InitializationError {
        try {
            ClassLoader testClassLoader = new JDK9TestClassLoader();
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

    /**
     * As of JDK9, Truffle API is deployed in a module (possibly the unnamed module) so to retrieve
     * the class file bytes for separate class loading requires using module API.
     */
    private static final class JDK9TestClassLoader extends ClassLoader {

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (name.startsWith(JAVA_INTEROP_PACKAGE)) {
                return findClassInModule(name);
            }
            return super.loadClass(name);
        }

        protected Class<?> findClassInModule(String name) throws ClassNotFoundException {
            Class<?> classInModule = super.loadClass(name);
            Module module = classInModule.getModule();
            String res = name.replace('.', '/') + ".class";
            InputStream is;
            try {
                is = module.getResourceAsStream(res);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
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
