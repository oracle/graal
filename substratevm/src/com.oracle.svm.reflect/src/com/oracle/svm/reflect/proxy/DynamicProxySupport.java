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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.jdk.proxy.DynamicProxyRegistry;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.VMError;

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
    private final Map<ProxyCacheKey, Class<?>> proxyCache;

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
        Class<?> proxyClass = proxyCache.computeIfAbsent(key, (k) -> DynamicProxyRegistry.getProxyClass(classLoader, intfs));
        try {
            /*
             * The constructor of the generated dynamic proxy class that takes a
             * `java.lang.reflect.InvocationHandler` argument, i.e., the one reflectively invoked by
             * `java.lang.reflect.Proxy.newProxyInstance(ClassLoader, Class<?>[],
             * InvocationHandler)`, is registered for reflection so that dynamic proxy instances can
             * be allocated at run time.
             */
            RuntimeReflection.register(proxyClass.getConstructor(InvocationHandler.class));
        } catch (NoSuchMethodException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    @Override
    public Class<?> getProxyClass(Class<?>... interfaces) {
        ProxyCacheKey key = new ProxyCacheKey(interfaces);
        if (!proxyCache.containsKey(key)) {
            throw VMError.unsupportedFeature("Proxy class defined by interfaces " + Arrays.toString(interfaces) + " not found. " +
                            "Generating proxy classes at runtime is not supported. " +
                            "Proxy classes need to be defined at image build time by specifying the list of interfaces that they implement. " +
                            "To define proxy classes use " + proxyConfigFilesOption + " and " + proxyConfigResourcesOption + " options.");
        }
        return proxyCache.get(key);
    }

    @Override
    public boolean isProxyClass(Class<?> clazz) {
        return proxyCache.containsValue(clazz);
    }
}
