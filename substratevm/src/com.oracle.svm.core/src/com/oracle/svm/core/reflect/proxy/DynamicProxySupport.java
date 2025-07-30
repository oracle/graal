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
package com.oracle.svm.core.reflect.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.regex.Pattern;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.ConfigurationCondition;

import com.oracle.svm.core.configure.ConditionalRuntimeValue;
import com.oracle.svm.core.configure.RuntimeConditionSet;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.PredefinedClassesSupport;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.jdk.proxy.DynamicProxyRegistry;
import com.oracle.svm.core.layeredimagesingleton.DuplicableImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.metadata.MetadataTracer;
import com.oracle.svm.core.reflect.MissingReflectionRegistrationUtils;
import com.oracle.svm.core.util.ImageHeapMap;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ClassUtil;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.debug.GraalError;

public class DynamicProxySupport implements DynamicProxyRegistry, DuplicableImageSingleton {

    public static final Pattern PROXY_CLASS_NAME_PATTERN = Pattern.compile(".*\\$Proxy[0-9]+");

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
            if (ImageLayerBuildingSupport.buildingImageLayer()) {
                /*
                 * The hash code cannot be computed using the interfaces' hash code in Layered Image
                 * because the hash code of classes cannot be injected in the application layer.
                 * This causes the internal structure of the proxyCache to be unusable in the
                 * application layer.
                 */
                return Arrays.hashCode(Arrays.stream(interfaces).map(Class::getName).toArray());
            } else {
                return Arrays.hashCode(interfaces);
            }
        }

        @Override
        public String toString() {
            return Arrays.toString(interfaces);
        }
    }

    private final EconomicMap<ProxyCacheKey, ConditionalRuntimeValue<Object>> proxyCache = ImageHeapMap.create("proxyCache");

    @Platforms(Platform.HOSTED_ONLY.class)
    public DynamicProxySupport() {
    }

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    public synchronized void addProxyClass(ConfigurationCondition condition, Class<?>... interfaces) {
        VMError.guarantee(condition.isRuntimeChecked(), "The condition used must be a runtime condition.");
        /*
         * Make a defensive copy of the interfaces array to protect against the caller modifying the
         * array.
         */
        Class<?>[] intfs = interfaces.clone();
        ProxyCacheKey key = new ProxyCacheKey(intfs);

        if (!proxyCache.containsKey(key)) {
            proxyCache.put(key, new ConditionalRuntimeValue<>(RuntimeConditionSet.emptySet(), createProxyClass(intfs)));
        }
        proxyCache.get(key).getConditions().addCondition(condition);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static Object createProxyClass(Class<?>[] interfaces) {
        try {
            Class<?> clazz = createProxyClassFromImplementedInterfaces(interfaces);

            boolean isPredefinedProxy = Arrays.stream(interfaces).anyMatch(PredefinedClassesSupport::isPredefined);
            if (isPredefinedProxy) {
                /*
                 * Treat the proxy as a predefined class so that we can set its class loader to the
                 * loader passed at runtime. If one of the interfaces is a predefined class, this
                 * can be required so that the classes can actually see each other according to the
                 * runtime class loader hierarchy.
                 */
                PredefinedClassesSupport.registerClass(clazz);
                RuntimeClassInitialization.initializeAtRunTime(clazz);
            }

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
            for (Class<?> intf : interfaces) {
                RuntimeReflection.register(intf.getMethods());
            }
            return clazz;
        } catch (Throwable t) {
            LogUtils.warning("Could not create a proxy class from list of interfaces: %s. Reason: %s", Arrays.toString(interfaces), t.getMessage());
            return t;
        }
    }

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    public Class<?> getProxyClassHosted(Class<?>... interfaces) {
        final Class<?>[] intfs = interfaces.clone();
        return createProxyClassFromImplementedInterfaces(intfs);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @SuppressWarnings("deprecation")
    private static Class<?> createProxyClassFromImplementedInterfaces(Class<?>[] interfaces) {
        return Proxy.getProxyClass(getCommonClassLoaderOrFail(null, interfaces), interfaces);
    }

    private static ClassLoader getCommonClassLoaderOrFail(ClassLoader loader, Class<?>... intfs) {
        ClassLoader commonLoader = null;
        for (Class<?> intf : intfs) {
            ClassLoader intfLoader = intf.getClassLoader();
            if (ClassUtil.isSameOrParentLoader(commonLoader, intfLoader)) {
                commonLoader = intfLoader;
            } else if (!ClassUtil.isSameOrParentLoader(intfLoader, commonLoader)) {
                throw incompatibleClassLoaders(loader, intfs);
            }
        }
        return commonLoader;
    }

    @Override
    public Class<?> getProxyClass(ClassLoader loader, Class<?>... interfaces) {
        if (MetadataTracer.enabled()) {
            MetadataTracer.singleton().traceProxyType(interfaces);
        }

        ProxyCacheKey key = new ProxyCacheKey(interfaces);
        ConditionalRuntimeValue<Object> clazzOrError = proxyCache.get(key);

        if (clazzOrError == null || !clazzOrError.getConditions().satisfied()) {
            throw MissingReflectionRegistrationUtils.reportProxyAccess(interfaces);
        }
        if (clazzOrError.getValue() instanceof Throwable) {
            throw new GraalError((Throwable) clazzOrError.getValue());
        }
        Class<?> clazz = (Class<?>) clazzOrError.getValue();
        if (!DynamicHub.fromClass(clazz).isLoaded()) {
            /*
             * NOTE: we might race with another thread in loading this proxy class.
             *
             * We ignore that the proxy class should be defined precisely by the provided class
             * loader and instead define it using that class loader which all interfaces have in
             * common. This prevents that later we would be unable to return the proxy class if we
             * are passed a parent loader of the initially specified loader.
             */
            ClassLoader commonLoader = getCommonClassLoaderOrFail(loader, interfaces);
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

    public static String proxyTypeDescriptor(String... interfaceNames) {
        return "Proxy[" + String.join(", ", interfaceNames) + "]";
    }

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.ALL_ACCESS;
    }

    @Override
    public PersistFlags preparePersist(ImageSingletonWriter writer) {
        return PersistFlags.NOTHING;
    }
}
