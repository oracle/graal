/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import java.lang.reflect.Method;
import java.security.SecureClassLoader;
import java.util.jar.JarFile;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

/**
 * NativeImageCustomSystemClassLoader is a minimal {@link ClassLoader} that forwards loading of a
 * class to a {@link NativeImageSystemClassLoader#delegate} {@link ClassLoader}. If such
 * delegate is null, then NativeImageSystemClassLoader forwards the class loading operation to the default
 * system class loader
 * 
 * This ClassLoader is necessary to enable the loading of classes/resources during image build-time. This
 * class must be used a replacement for {@link ClassLoader#getSystemClassLoader()} and its parent
 * must be the default system class loader. The delegate is set to an instance of
 * {@link NativeImageClassLoader}.
 */
public final class NativeImageSystemClassLoader extends SecureClassLoader {

    private ClassLoader delegate = null;
    private final ClassLoader defaultSystemClassLoader;

    public NativeImageSystemClassLoader(ClassLoader defaultSystemClassLoader) {
        super(defaultSystemClassLoader);
        this.defaultSystemClassLoader = defaultSystemClassLoader;
    }

    public void setDelegate(ClassLoader delegateClassLoader) {
        this.delegate = delegateClassLoader;
    }

    public ClassLoader getDefaultSystemClassLoader() {
        return defaultSystemClassLoader;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        ClassLoader cl = delegate != null ? delegate : defaultSystemClassLoader;
        return cl.loadClass(name);
    }

    /**
     * This method is necessary for all custom system class loaders. It allows for the load of the
     * agent during startup. See
     * {@link java.lang.instrument.Instrumentation#appendToSystemClassLoaderSearch(JarFile)} }
     * 
     * @param filePath the path to the jar file that will be added to the class path
     */
    @SuppressWarnings("unused")
    private void appendToClassPathForInstrumentation(String filePath) {
        try {
            Method method = ReflectionUtil.lookupMethod(getParent().getClass(), "appendToClassPathForInstrumentation", String.class);
            method.invoke(getParent(), filePath);
        } catch (ReflectiveOperationException e) {
            String message = String.format("Can not add jar: %s to class path. Due to %s", filePath, e);
            VMError.shouldNotReachHere(message, e);
        }
    }
}
