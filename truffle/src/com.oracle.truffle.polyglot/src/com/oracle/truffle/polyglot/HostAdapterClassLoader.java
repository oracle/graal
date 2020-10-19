/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.AllPermission;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.Permissions;
import java.security.ProtectionDomain;
import java.security.SecureClassLoader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.function.Supplier;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

/**
 * This class encapsulates the bytecode of the adapter class and can be used to load it into the JVM
 * as an actual Class. It can be invoked repeatedly to create multiple adapter classes from the same
 * bytecode; adapter classes that have class-level overrides must be re-created for every set of
 * such overrides. Instances of this class are normally created by
 * {@link HostAdapterBytecodeGenerator}.
 */
final class HostAdapterClassLoader {
    static final ProtectionDomain GENERATED_PROTECTION_DOMAIN = createGeneratedProtectionDomain();
    static final Collection<String> VISIBLE_INTERNAL_CLASS_NAMES = Collections.unmodifiableCollection(
                    new HashSet<>(Arrays.asList(Value.class.getName())));
    static final String SERVICE_CLASS_NAME = "com.oracle.truffle.polyglot.hostadapters.HostAdapterServices";

    interface LazyClassBytes {
        byte[] SERVICE_CLASS_BYTES = loadClassBytes(SERVICE_CLASS_NAME);
    }

    private final String className;
    private final byte[] classBytes;

    HostAdapterClassLoader(String className, byte[] classBytes) {
        this.className = className.replace('/', '.');
        this.classBytes = classBytes;
    }

    /**
     * Loads the generated adapter class into the JVM.
     *
     * @param parentLoader the parent class loader for the generated class loader
     * @return the generated adapter class
     */
    Class<?> generateClass(ClassLoader parentLoader, Object classOverrides) {
        try {
            return Class.forName(className, true, createClassLoader(parentLoader, classOverrides));
        } catch (final ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private ClassLoader createClassLoader(final ClassLoader parentLoader, final Object classOverrides) {
        return new GeneratedClassLoader(parentLoader, classOverrides);
    }

    static boolean isAdapterInstance(Object adapter) {
        return isGeneratedClass(adapter.getClass());
    }

    static boolean isGeneratedClass(Class<?> clazz) {
        return isGeneratedClassLoader(clazz.getClassLoader());
    }

    static boolean isGeneratedClassLoader(ClassLoader classLoader) {
        return classLoader instanceof GeneratedClassLoader;
    }

    final class GeneratedClassLoader extends SecureClassLoader implements Supplier<Value> {
        private final ClassLoader internalLoader;
        private final Object classOverrides;

        private GeneratedClassLoader(final ClassLoader parentLoader, final Object classOverrides) {
            super(parentLoader);
            this.internalLoader = GeneratedClassLoader.class.getClassLoader();
            this.classOverrides = classOverrides;
        }

        @Override
        public Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
            // bypass the parent class loader for the generated class and allowed internal classes.
            if (isGeneratedClassName(name)) {
                return loadGeneratedClass(name, resolve);
            }
            if (VISIBLE_INTERNAL_CLASS_NAMES.contains(name)) {
                return loadInternalClass(name);
            }
            return super.loadClass(name, resolve);
        }

        private Class<?> loadGeneratedClass(final String name, final boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                if (c == null) {
                    c = findClass(name);
                }
                if (resolve) {
                    resolveClass(c);
                }
                return c;
            }
        }

        private Class<?> loadInternalClass(final String name) throws ClassNotFoundException {
            assert VISIBLE_INTERNAL_CLASS_NAMES.contains(name);
            return internalLoader != null ? internalLoader.loadClass(name) : Class.forName(name, false, internalLoader);
        }

        private boolean isGeneratedClassName(final String name) {
            return name.equals(className) || name.equals(SERVICE_CLASS_NAME);
        }

        @Override
        protected Class<?> findClass(final String name) throws ClassNotFoundException {
            if (name.equals(className)) {
                return defineClass(name, classBytes, 0, classBytes.length, GENERATED_PROTECTION_DOMAIN);
            }
            if (name.equals(SERVICE_CLASS_NAME)) {
                byte[] bytes = LazyClassBytes.SERVICE_CLASS_BYTES;
                return defineClass(name, bytes, 0, bytes.length, GENERATED_PROTECTION_DOMAIN);
            }
            throw new ClassNotFoundException(name);
        }

        @Override
        public Value get() {
            return Context.getCurrent().asValue(classOverrides);
        }
    }

    private static ProtectionDomain createGeneratedProtectionDomain() {
        /*
         * Generated classes need to have AllPermission. Since we require the "createClassLoader"
         * RuntimePermission, we can create a class loader that'll load new classes with any
         * permissions. Our generated classes are just delegating adapters, so having AllPermission
         * can't cause anything wrong; the effective set of permissions for the executing script
         * functions will still be limited by the permissions of the caller and the permissions of
         * the script.
         */
        final Permissions permissions = new Permissions();
        permissions.add(new AllPermission());
        return new ProtectionDomain(new CodeSource(null, (CodeSigner[]) null), permissions);
    }

    @SuppressWarnings("unchecked")
    static Value getClassOverrides(ClassLoader classLoader) {
        return ((Supplier<Value>) classLoader).get();
    }

    static byte[] loadClassBytes(String className) {
        String classFileName = "/" + className.replace('.', '/') + ".class";
        try (InputStream in = Objects.requireNonNull(HostAdapterClassLoader.class.getResourceAsStream(classFileName), className);
                        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4000];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

}
