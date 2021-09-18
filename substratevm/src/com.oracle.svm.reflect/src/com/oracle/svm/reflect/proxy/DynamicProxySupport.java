/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.reflect.proxy;

// Checkstyle: allow reflection

import java.lang.reflect.InvocationHandler;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.PredefinedClassesSupport;
import com.oracle.svm.core.jdk.proxy.DynamicProxyRegistry;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.util.ClassUtil;
import com.oracle.svm.util.ReflectionUtil;

public class DynamicProxySupport implements DynamicProxyRegistry {

    private static final String proxyConfigFilesOption = SubstrateOptionsParser.commandArgument(ConfigurationFiles.Options.DynamicProxyConfigurationFiles, "<comma-separated-config-files>");
    private static final String proxyConfigResourcesOption = SubstrateOptionsParser.commandArgument(ConfigurationFiles.Options.DynamicProxyConfigurationResources,
                    "<comma-separated-config-resources>");

    static final class ProxyCacheKey {

        private final Class<?>[] interfaces;

        private ProxyCacheKey(Class<?>... interfaces) {
            this.interfaces = interfaces;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ProxyCacheKey) {
                ProxyCacheKey that = (ProxyCacheKey) obj;
                return Arrays.equals(this.interfaces, that.interfaces);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(interfaces);
        }

        @Override
        public String toString() {
            return Arrays.toString(interfaces);
        }
    }

    private final ClassLoader classLoader;
    private final Map<ProxyCacheKey, Object> proxyCache;

    public DynamicProxySupport(ClassLoader classLoader) {
        this.classLoader = classLoader;
        this.proxyCache = new ConcurrentHashMap<>();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @Override
    public void addProxyClass(Class<?>... interfaces) {
        /*
         * Make a defensive copy of the interfaces array to protect against the caller modifying the
         * array.
         */
        final Class<?>[] intfs = interfaces.clone();
        ProxyCacheKey key = new ProxyCacheKey(intfs);
        proxyCache.computeIfAbsent(key, k -> {
            Class<?> clazz;
            try {
                clazz = getJdkProxyClass(classLoader, intfs);
            } catch (Throwable e) {
                if (NativeImageOptions.AllowIncompleteClasspath.getValue()) {
                    return e;
                } else {
                    throw e;
                }
            }

            /*
             * Treat the proxy as a predefined class so that we can set its class loader to the
             * loader passed at runtime. If one of the interfaces is a predefined class, this can be
             * required so that the classes can actually see each other according to the runtime
             * class loader hierarchy.
             */
            PredefinedClassesSupport.registerClass(clazz);

            /*
             * The constructor of the generated dynamic proxy class that takes a
             * `java.lang.reflect.InvocationHandler` argument, i.e., the one reflectively invoked by
             * `java.lang.reflect.Proxy.newProxyInstance(ClassLoader, Class<?>[],
             * InvocationHandler)`, is registered for reflection so that dynamic proxy instances can
             * be allocated at run time.
             */
            RuntimeReflection.register(ReflectionUtil.lookupConstructor(clazz, InvocationHandler.class));

            /*
             * The proxy class reflectively looks up the methods of the interfaces it implements to
             * pass a Method object to InvocationHandler.
             */
            for (Class<?> intf : intfs) {
                RuntimeReflection.register(intf.getMethods());
            }

            return clazz;
        });
    }

    @Override
    public Class<?> getProxyClass(ClassLoader loader, Class<?>... interfaces) {
        ProxyCacheKey key = new ProxyCacheKey(interfaces);
        Object clazzOrError = proxyCache.get(key);
        if (clazzOrError == null) {
            throw VMError.unsupportedFeature("Proxy class defined by interfaces " + Arrays.toString(interfaces) + " not found. " +
                            "Generating proxy classes at runtime is not supported. " +
                            "Proxy classes need to be defined at image build time by specifying the list of interfaces that they implement. " +
                            "To define proxy classes use " + proxyConfigFilesOption + " and " + proxyConfigResourcesOption + " options.");
        }
        if (clazzOrError instanceof Throwable) {
            throw new GraalError((Throwable) clazzOrError);
        }
        Class<?> clazz = (Class<?>) clazzOrError;
        if (!DynamicHub.fromClass(clazz).isLoaded()) {
            /*
             * NOTE: we might race with another thread in loading this proxy class.
             *
             * We ignore that the proxy class should be defined precisely by the provided class
             * loader and instead define it using that class loader which all interfaces have in
             * common. This prevents that later we would be unable to return the proxy class if we
             * are passed a parent loader of the initially specified loader.
             */
            ClassLoader commonLoader = null;
            for (Class<?> intf : interfaces) {
                ClassLoader intfLoader = intf.getClassLoader();
                if (ClassUtil.isSameOrParentLoader(commonLoader, intfLoader)) {
                    commonLoader = intfLoader;
                } else if (!ClassUtil.isSameOrParentLoader(intfLoader, commonLoader)) {
                    /*
                     * This should be caught when the proxy class is generated during the image
                     * build, but can occur with predefined classes which are "loaded" by different
                     * class loaders at runtime.
                     */
                    throw incompatibleClassLoaders(loader, interfaces);
                }
            }
            if (!ClassUtil.isSameOrParentLoader(commonLoader, loader)) {
                throw incompatibleClassLoaders(loader, interfaces);
            }
            boolean loaded = PredefinedClassesSupport.loadClassIfNotLoaded(commonLoader, null, clazz);
            if (!loaded && !ClassUtil.isSameOrParentLoader(clazz.getClassLoader(), loader)) {
                throw incompatibleClassLoaders(loader, interfaces);
            }
        } else if (!ClassUtil.isSameOrParentLoader(clazz.getClassLoader(), loader)) {
            throw incompatibleClassLoaders(loader, interfaces);
        }
        return clazz;
    }

    private static RuntimeException incompatibleClassLoaders(ClassLoader provided, Class<?>[] interfaces) {
        StringBuilder b = new StringBuilder("Interface(s) not visible to the provided class loader: ");
        describeLoaderChain(b, provided);
        for (Class<?> intf : interfaces) {
            b.append("; interface ").append(intf.getName()).append(" loaded by ");
            describeLoaderChain(b, intf.getClassLoader());
        }
        throw new IllegalArgumentException(b.toString());
    }

    private static void describeLoaderChain(StringBuilder b, ClassLoader loader) {
        ClassLoader l = loader;
        for (;;) {
            if (l != loader) {
                b.append(", child of ");
            }
            b.append(l);
            if (l == null) {
                break;
            }
            l = l.getParent();
        }
    }

    @Override
    public boolean isProxyClass(Class<?> clazz) {
        return proxyCache.containsValue(clazz);
    }

    @SuppressWarnings("deprecation")
    @Platforms(Platform.HOSTED_ONLY.class)
    public static Class<?> getJdkProxyClass(ClassLoader loader, Class<?>... interfaces) {
        return java.lang.reflect.Proxy.getProxyClass(loader, interfaces);
    }
}
