/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.api.profiles;

import com.oracle.truffle.api.source.Source;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;

public final class SeparateClassloaderTestRunner extends BlockJUnit4ClassRunner {
    public SeparateClassloaderTestRunner(Class<?> clazz) throws InitializationError {
        super(getFromTestClassloader(clazz));
    }

    private static Class<?> getFromTestClassloader(Class<?> clazz) throws InitializationError {
        try {
            ClassLoader testClassLoader = new TestClassLoader();
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

    private static class TestClassLoader extends ClassLoader {
        TestClassLoader() {
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (name.startsWith(Profile.class.getPackage().getName())) {
                return findClass(name);
            }
            if (name.contains("ContextStore")) {
                return findClass(name);
            }
            if (name.contains(Source.class.getPackage().getName())) {
                return findClass(name);
            }
            return super.loadClass(name);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            String res = name.replace('.', '/') + ".class";
            InputStream is = getResourceAsStream(res);
            if (is == null) {
                throw new ClassNotFoundException(name);
            }
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
            return defineClass(name, arr, 0, arr.length, getClass().getProtectionDomain());
        }
    }
}
