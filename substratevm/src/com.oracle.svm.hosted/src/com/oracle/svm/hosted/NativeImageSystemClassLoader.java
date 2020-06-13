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
import java.net.URL;
import java.security.SecureClassLoader;
import java.util.jar.JarFile;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

/**
 * NativeImageCustomSystemClassLoader is a minimal {@link ClassLoader} that forwards loading of a
 * class to a {@link NativeImageSystemClassLoader#delegate} {@link ClassLoader}. If such delegate is
 * null, then NativeImageSystemClassLoader forwards the class loading operation to the default
 * system class loader
 * 
 * This ClassLoader is necessary to enable the loading of classes/resources during image build-time.
 * This class must be used as a replacement for {@link ClassLoader#getSystemClassLoader()} and its
 * parent must be the default system class loader. The delegate is set to an instance of
 * {@link NativeImageClassLoader}.
 */
public final class NativeImageSystemClassLoader extends SecureClassLoader {

    private NativeImageClassLoader delegate = null;
    private final ClassLoader defaultSystemClassLoader;
    /**
     * {@link ClassLoader#loadClass(String, boolean)} is the terminal method that gets invoked when
     * resolving a class, unfortunately it is protected method meant to be overridden. Since this
     * class delegates to the appropriate ClassLoader, the method needs to be called via reflection
     * to by pass the protected visibility
     */
    private static final Method loadClass = ReflectionUtil.lookupMethod(ClassLoader.class, "loadClass",
                    String.class, boolean.class);
    /**
     * {@link ClassLoader#findResource(String)} is the terminal method that gets invoked when
     * finding a resource, unfortunately it is protected method meant to be overridden. Since this
     * class delegates to the appropriate ClassLoader, the method needs to be called via reflection
     * to by pass the protected visibility
     */
    private static final Method findResource = ReflectionUtil.lookupMethod(ClassLoader.class, "findResource",
                    String.class);

    private static final Method getClassLoadingLock = ReflectionUtil.lookupMethod(ClassLoader.class, "getClassLoadingLock",
                    String.class);

    public NativeImageSystemClassLoader(ClassLoader defaultSystemClassLoader) {
        super(defaultSystemClassLoader);
        this.defaultSystemClassLoader = defaultSystemClassLoader;
    }

    public void setDelegate(NativeImageClassLoader delegateClassLoader) {
        this.delegate = delegateClassLoader;
    }

    public ClassLoader getDefaultSystemClassLoader() {
        return defaultSystemClassLoader;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        ClassLoader classLoader = null;
        Class<?> loadedClass = null;
        try {
            classLoader = getActiveClassLoader();
            final Object lock = getClassLoadingLock.invoke(classLoader, name);
            synchronized (lock) {
                // invoke the "loadClass" method on the current class loader
                loadedClass = ((Class<?>) loadClass.invoke(classLoader, name, resolve));
            }
        } catch (Exception e) {
            if (e.getCause() instanceof ClassNotFoundException) {
                throw ((ClassNotFoundException) e.getCause());
            }
            String message = String.format("Can not load class: %s, with class loader: %s", name, classLoader);
            VMError.shouldNotReachHere(message, e);
        }
        return loadedClass;
    }

    @Override
    protected URL findResource(String name) {
        ClassLoader classLoader = null;
        try {
            classLoader = getActiveClassLoader();
            // invoke the "findResourceMethod" method on the current class loader
            return (URL) findResource.invoke(classLoader, name);
        } catch (ReflectiveOperationException e) {
            String message = String.format("Can not find resource: %s using class loader: %s", name, classLoader);
            VMError.shouldNotReachHere(message, e);
        }
        return null;
    }

    @Override
    public String toString() {
        final String clString = super.toString();
        return clString + " {" +
                        "delegate=" + delegate +
                        ", defaultSystemClassLoader=" + defaultSystemClassLoader +
                        '}';
    }

    private ClassLoader getActiveClassLoader() {
        return delegate != null
                        ? delegate
                        : defaultSystemClassLoader;
    }

    /**
     * This method is necessary for all custom system class loaders. It allows for the load of the
     * agent during startup. See
     * {@link java.lang.instrument.Instrumentation#appendToSystemClassLoaderSearch(JarFile)} }
     * 
     * @param classPathEntry the classpath entry that will be added to the class path
     */
    @SuppressWarnings("unused")
    private void appendToClassPathForInstrumentation(String classPathEntry) {
        try {
            Method method = ReflectionUtil.lookupMethod(getParent().getClass(), "appendToClassPathForInstrumentation", String.class);
            method.invoke(getParent(), classPathEntry);
        } catch (ReflectiveOperationException e) {
            String message = String.format("Can not add jar: %s to class path. Due to %s", classPathEntry, e);
            VMError.shouldNotReachHere(message, e);
        }
    }

}
