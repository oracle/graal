/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test.preserve.incomplete;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeSerialization;
import org.junit.Test;

import com.oracle.svm.test.NativeImageBuildArgs;

@NativeImageBuildArgs({
                "--features=com.oracle.svm.test.preserve.incomplete.PreserveIncompleteExternalizableTest$TestFeature",
                "--initialize-at-build-time=com.oracle.svm.test.preserve.incomplete.PreserveIncompleteExternalizableTest$IncompleteClassLoader",
})
public class PreserveIncompleteExternalizableTest {
    private static final String INCOMPLETE_CLASS_NAME = "com.oracle.svm.test.preserve.incomplete.IncompleteExternalizable";
    private static final String MISSING_CLASS_NAME = "com.oracle.svm.test.preserve.incomplete.MissingType";

    public static final class TestFeature implements Feature {
        @Override
        public void duringSetup(DuringSetupAccess access) {
            byte[] classBytes;
            try (InputStream stream = Objects.requireNonNull(PreserveIncompleteExternalizableTest.class.getResourceAsStream("IncompleteExternalizable.class"))) {
                classBytes = stream.readAllBytes();
            } catch (IOException e) {
                throw new AssertionError(e);
            }

            ClassLoader incompleteClassLoader = new IncompleteClassLoader(classBytes);

            try {
                Class<?> incompleteClass = incompleteClassLoader.loadClass(INCOMPLETE_CLASS_NAME);
                try {
                    incompleteClass.getDeclaredConstructors();
                    throw new AssertionError("Expected constructor lookup to fail because its parameter type is unavailable");
                } catch (NoClassDefFoundError expected) {
                    /* Expected. */
                }
                RuntimeSerialization.register(incompleteClass);
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
        }
    }

    static final class IncompleteClassLoader extends ClassLoader {
        private final byte[] classBytes;

        IncompleteClassLoader(byte[] classBytes) {
            super(PreserveIncompleteExternalizableTest.class.getClassLoader());
            this.classBytes = classBytes;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals(MISSING_CLASS_NAME)) {
                throw new ClassNotFoundException(name);
            }
            if (name.equals(INCOMPLETE_CLASS_NAME)) {
                Class<?> clazz = findLoadedClass(name);
                if (clazz == null) {
                    clazz = defineClass(name, classBytes, 0, classBytes.length);
                }
                if (resolve) {
                    resolveClass(clazz);
                }
                return clazz;
            }
            return super.loadClass(name, resolve);
        }
    }

    @Test
    public void preserveIncompleteExternalizable() {
        /* Image generation is the regression test. */
    }
}
