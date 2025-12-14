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
import java.util.function.Function;
import java.util.regex.Pattern;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.dynamicaccess.AccessCondition;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;
import org.graalvm.nativeimage.impl.TypeReachabilityCondition;

import com.oracle.svm.core.configure.ConditionalRuntimeValue;
import com.oracle.svm.core.configure.RuntimeDynamicAccessMetadata;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.PredefinedClassesSupport;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.jdk.proxy.DynamicProxyRegistry;
import com.oracle.svm.core.metadata.MetadataTracer;
import com.oracle.svm.core.reflect.MissingReflectionRegistrationUtils;
import com.oracle.svm.core.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.core.traits.BuiltinTraits.Duplicable;
import com.oracle.svm.core.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.core.util.ImageHeapMap;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ClassUtil;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.debug.GraalError;

@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Independent.class, other = Duplicable.class)
public class DynamicProxySupport implements DynamicProxyRegistry {

    public static final Pattern PROXY_CLASS_NAME_PATTERN = Pattern.compile(".*\\$Proxy[0-9]+");

    @Platforms(Platform.HOSTED_ONLY.class) //
    public static DynamicProxySupport singleton() {
        return (DynamicProxySupport) ImageSingletons.lookup(DynamicProxyRegistry.class);
    }

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

    @Platforms(Platform.HOSTED_ONLY.class) //
    private final EconomicMap<Class<?>, ClassLoader> proxyClassClassloaders = EconomicMap.create();

    @Platforms(Platform.HOSTED_ONLY.class) //
    private final Function<Class<?>, ClassLoader> loaderAccessor;

    @Platforms(Platform.HOSTED_ONLY.class)
    public DynamicProxySupport(Function<Class<?>, ClassLoader> loaderAccessor) {
        this.loaderAccessor = loaderAccessor;
    }

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    public synchronized void addProxyClass(AccessCondition condition, boolean preserved, Class<?>... interfaces) {
        VMError.guarantee(condition instanceof TypeReachabilityCondition && ((TypeReachabilityCondition) condition).isRuntimeChecked(), "The condition used must be a runtime condition.");
        /*
         * Make a defensive copy of the interfaces array to protect against the caller modifying the
         * array.
         */
        Class<?>[] intfs = interfaces.clone();
        ProxyCacheKey key = new ProxyCacheKey(intfs);
        ConditionalRuntimeValue<Object> conditionalValue = proxyCache.get(key);
        if (conditionalValue == null) {
            conditionalValue = new ConditionalRuntimeValue<>(RuntimeDynamicAccessMetadata.emptySet(preserved), createProxyClass(intfs, preserved));
            proxyCache.put(key, conditionalValue);
        } else if (!preserved) {
            conditionalValue.getDynamicAccessMetadata().setNotPreserved();
        }
        conditionalValue.getDynamicAccessMetadata().addCondition(condition);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private Object createProxyClass(Class<?>[] interfaces, boolean preserved) {
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
            RuntimeReflectionSupport reflectionSupport = ImageSingletons.lookup(RuntimeReflectionSupport.class);
            reflectionSupport.register(AccessCondition.unconditional(), false, preserved, ReflectionUtil.lookupConstructor(clazz, InvocationHandler.class));

            /*
             * The proxy class reflectively looks up the methods of the interfaces it implements to
             * pass a Method object to InvocationHandler.
             */
            for (Class<?> intf : interfaces) {
                reflectionSupport.register(AccessCondition.unconditional(), false, preserved, intf.getMethods());
            }

            /*
             * When the dynamic hubs for proxy classes are generated we have to make sure they get
             * the correct runtime classloader. Remember which classloader is need for DynamicHub.
             * See getProxyClassClassloader below.
             */
            ClassLoader proxyRuntimeLoader = getCommonClassLoaderOrFail(null, loaderAccessor, interfaces);
            /* We only add entries for proxy classes where we need to adjust the loader value */
            if (proxyRuntimeLoader != clazz.getClassLoader()) {
                synchronized (proxyClassClassloaders) {
                    proxyClassClassloaders.put(clazz, proxyRuntimeLoader);
                }
            }

            return clazz;
        } catch (Throwable t) {
            return t;
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public ClassLoader getProxyClassClassloader(Class<?> clazz, Function<Class<?>, ClassLoader> defaultSupplier) {
        /*
         * Using synchronized on proxyClassClassloaders, since it is very rare that it gets written
         * to in createProxyClass.
         */
        synchronized (proxyClassClassloaders) {
            if (proxyClassClassloaders.containsKey(clazz)) {
                /*
                 * If this is a proxy class we generated with createProxyClass, make sure it gets
                 * the correct runtime-classloader (based on what runtime-classloader the interfaces
                 * have that it was created for). Note that `null` is a valid classloader as well.
                 */
                return proxyClassClassloaders.get(clazz);
            }
        }
        return defaultSupplier.apply(clazz);
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
        return Proxy.getProxyClass(getCommonClassLoaderOrFail(null, Class::getClassLoader, interfaces), interfaces);
    }

    private static ClassLoader getCommonClassLoaderOrFail(ClassLoader loader, Function<Class<?>, ClassLoader> loaderAccessor, Class<?>... intfs) {
        ClassLoader commonLoader = null;
        for (Class<?> intf : intfs) {
            ClassLoader intfLoader = loaderAccessor.apply(intf);
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

        if (clazzOrError == null || !clazzOrError.getDynamicAccessMetadata().satisfied()) {
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
            ClassLoader commonLoader = getCommonClassLoaderOrFail(loader, Class::getClassLoader, interfaces);
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

    public boolean isProxyPreserved(Class<?>... interfaces) {
        ProxyCacheKey key = new ProxyCacheKey(interfaces);
        if (proxyCache.get(key) instanceof ConditionalRuntimeValue<Object> entry) {
            return entry.getDynamicAccessMetadata().isPreserved();
        }
        return false;
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
}
