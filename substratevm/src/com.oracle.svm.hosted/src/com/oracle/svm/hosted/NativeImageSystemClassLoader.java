/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.SecureClassLoader;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;
import java.util.WeakHashMap;

import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

/**
 * NativeImageCustomSystemClassLoader is a minimal {@link ClassLoader} that forwards loading of a
 * class to a {@link NativeImageSystemClassLoader#nativeImageClassLoader} {@link ClassLoader}. If
 * such delegate is null, then NativeImageSystemClassLoader forwards the class loading operation to
 * the default system class loader.
 *
 * This ClassLoader is necessary to enable the loading of classes/resources during image build-time.
 * This class must be used as a replacement for {@link ClassLoader#getSystemClassLoader()} and its
 * parent must be the default system class loader. The delegate is set to an instance of
 * {@link NativeImageClassLoaderSupport}.
 */
public final class NativeImageSystemClassLoader extends SecureClassLoader {

    public final ClassLoader defaultSystemClassLoader;
    final NativeImageSystemIOWrappers systemIOWrappers;

    private volatile ClassLoader nativeImageClassLoader = null;

    private Set<ClassLoader> disallowedClassLoaders = Collections.newSetFromMap(new WeakHashMap<>());

    public NativeImageSystemClassLoader(ClassLoader defaultSystemClassLoader) {
        super(defaultSystemClassLoader);
        this.defaultSystemClassLoader = defaultSystemClassLoader;
        systemIOWrappers = new NativeImageSystemIOWrappers();
        /* Image building console output requires custom System.out and System.err */
        systemIOWrappers.replaceSystemOutErr();
    }

    public static NativeImageSystemClassLoader singleton() {
        ClassLoader loader = ClassLoader.getSystemClassLoader();
        if (loader instanceof NativeImageSystemClassLoader) {
            return ((NativeImageSystemClassLoader) loader);
        }

        throw UserError.abort("NativeImageSystemClassLoader is not the default system class loader. This might create problems when using reflection during class initialization at build-time." +
                        "To fix this error add -Djava.system.class.loader=%s", NativeImageSystemClassLoader.class.getCanonicalName());
    }

    public void setNativeImageClassLoader(ClassLoader nativeImageClassLoader) {
        if (nativeImageClassLoader == null && this.nativeImageClassLoader != null) {
            /*
             * If the active nativeImageClassLoader gets uninstalled (by setting null) remember it
             * in the disallowedClassLoaders map to allow checking for left-over instances from
             * previous builds. See {@code SVMHost.checkType}.
             */
            disallowedClassLoaders.add(this.nativeImageClassLoader);
        }
        this.nativeImageClassLoader = nativeImageClassLoader;
    }

    private boolean isNativeImageClassLoader(ClassLoader current, ClassLoader c) {
        ClassLoader loader = current;
        do {
            if (loader == c) {
                return true;
            }
            loader = loader.getParent();
        } while (loader != defaultSystemClassLoader);
        return false;
    }

    public boolean isNativeImageClassLoader(ClassLoader c) {
        ClassLoader loader = nativeImageClassLoader;
        if (loader == null) {
            return false;
        }
        return isNativeImageClassLoader(nativeImageClassLoader, c);
    }

    public boolean isDisallowedClassLoader(ClassLoader c) {
        for (ClassLoader disallowedClassLoader : disallowedClassLoaders) {
            if (isNativeImageClassLoader(disallowedClassLoader, c)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Several classloader methods are terminal methods that get invoked when resolving a class or
     * accessing resources, unfortunately they are protected methods meant to be overridden. Since
     * this class delegates to the appropriate ClassLoader, the methods need to be called via
     * reflection to by pass the protected visibility
     */
    private static final Method loadClass = ReflectionUtil.lookupMethod(ClassLoader.class, "loadClass",
                    String.class, boolean.class);
    private static final Method findResource = ReflectionUtil.lookupMethod(ClassLoader.class, "findResource",
                    String.class);
    private static final Method findResources = ReflectionUtil.lookupMethod(ClassLoader.class, "findResources",
                    String.class);
    private static final Method defineClass = ReflectionUtil.lookupMethod(ClassLoader.class, "defineClass",
                    String.class, byte[].class, int.class, int.class);

    private static Class<?> loadClass(ClassLoader loader, String name, boolean resolve) throws ClassNotFoundException {
        ClassNotFoundException classNotFoundException = null;
        try {
            /* invoke the "loadClass" method on the current class loader */
            return ((Class<?>) loadClass.invoke(loader, name, resolve));
        } catch (Exception e) {
            if (e.getCause() instanceof ClassNotFoundException) {
                classNotFoundException = ((ClassNotFoundException) e.getCause());
            } else {
                String message = String.format("Can not load class: %s, with class loader: %s", name, loader);
                VMError.shouldNotReachHere(message, e);
            }
        }
        VMError.guarantee(classNotFoundException != null);
        throw classNotFoundException;
    }

    private static URL findResource(ClassLoader loader, String name) {
        try {
            // invoke the "findResource" method on the current class loader
            Object url = findResource.invoke(loader, name);
            if (url != null) {
                return (URL) url;
            }
        } catch (ReflectiveOperationException | ClassCastException e) {
            String message = String.format("Can not find resource: %s using class loader: %s", name, loader);
            VMError.shouldNotReachHere(message, e);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Enumeration<URL> findResources(ClassLoader classLoader, String name) {
        try {
            // invoke the "findResources" method on the current class loader
            return (Enumeration<URL>) findResources.invoke(classLoader, name);
        } catch (ReflectiveOperationException e) {
            String message = String.format("Can not find resources: %s using class loader: %s", name, classLoader);
            VMError.shouldNotReachHere(message, e);
        }

        return null;
    }

    static Class<?> defineClass(ClassLoader classLoader, String name, byte[] b, int offset, int length) {
        try {
            return (Class<?>) defineClass.invoke(classLoader, name, b, offset, length);
        } catch (ReflectiveOperationException e) {
            String message = String.format("Cannot define class %s from byte[%d..%d] using class loader: %s", name, offset, offset + length, classLoader);
            VMError.shouldNotReachHere(message, e);
        }
        return null;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        return loadClass(getActiveClassLoader(), name, resolve);
    }

    @Override
    protected URL findResource(String name) {
        return findResource(getActiveClassLoader(), name);
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        return findResources(getActiveClassLoader(), name);
    }

    public Class<?> forNameOrNull(String name, boolean initialize) {
        try {
            return Class.forName(name, initialize, getActiveClassLoader());
        } catch (LinkageError | ClassNotFoundException ignored) {
            return null;
        }
    }

    public Class<?> predefineClass(String name, byte[] array, int offset, int length) {
        VMError.guarantee(name != null, "The class name must be specified");
        if (forNameOrNull(name, false) != null) {
            throw VMError.shouldNotReachHere("The class loader hierarchy already provides a class with the same name as the class submitted for predefinition: " + name);
        }
        return defineClass(getActiveClassLoader(), name, array, offset, length);
    }

    @Override
    public String toString() {
        final String clString = super.toString();
        return clString + " {" +
                        "delegate=" + nativeImageClassLoader +
                        ", defaultSystemClassLoader=" + defaultSystemClassLoader +
                        '}';
    }

    private ClassLoader getActiveClassLoader() {
        ClassLoader activeClassLoader = nativeImageClassLoader;
        if (activeClassLoader != null) {
            return activeClassLoader;
        } else {
            return defaultSystemClassLoader;
        }
    }

    /**
     * This method is necessary for all custom system class loaders. It allows for the load of the
     * agent during startup. See
     * {@code java.lang.instrument.Instrumentation#appendToSystemClassLoaderSearch(JarFile)} }
     *
     * @param classPathEntry the classpath entry that will be added to the class path
     */
    @SuppressWarnings("unused") // no direct use from Java
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
