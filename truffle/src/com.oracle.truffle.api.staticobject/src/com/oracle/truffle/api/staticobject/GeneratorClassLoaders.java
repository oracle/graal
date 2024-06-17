/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.staticobject;

final class GeneratorClassLoaders {

    private final class StorageClassLoader extends ClassLoader {
        private final Class<?> referenceClass;

        StorageClassLoader(Class<?> referenceClass) {
            super(referenceClass.getClassLoader());
            this.referenceClass = referenceClass;
        }

        Class<?> defineGeneratedClass(String name, byte[] b, int off, int len) throws ClassFormatError {
            return defineClass(name, b, off, len, referenceClass.getProtectionDomain());
        }
    }

    private final class FactoryClassLoader extends ClassLoader {
        FactoryClassLoader(StorageClassLoader parent) {
            super(parent);
        }

        @SuppressWarnings("unchecked")
        Class<?> defineGeneratedClass(String name, byte[] b, int off, int len) throws ClassFormatError {
            return defineClass(name, b, off, len, ((StorageClassLoader) getParent()).referenceClass.getProtectionDomain());
        }
    }

    private final StorageClassLoader storageCL;
    private final FactoryClassLoader factoryCL;

    GeneratorClassLoaders(Class<?> referenceClass) {
        storageCL = new StorageClassLoader(referenceClass);
        factoryCL = new FactoryClassLoader(storageCL);
    }

    Class<?> defineGeneratedClass(String name, byte[] b, int off, int len, boolean isStorage) throws ClassFormatError {
        if (isStorage) {
            return storageCL.defineGeneratedClass(name, b, off, len);
        } else {
            return factoryCL.defineGeneratedClass(name, b, off, len);
        }
    }
}
