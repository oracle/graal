/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.reflect.hosted;

// Checkstyle: allow synchronization

/* Allow imports of java.lang.reflect and sun.misc.ProxyGenerator: Checkstyle: allow reflection. */

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicInteger;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.annotation.CustomSubstitution;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.reflect.helpers.InvokeSpecialReflectionProxy;
import com.oracle.svm.reflect.helpers.ReflectionProxy;
import com.oracle.svm.reflect.helpers.ReflectionProxyHelper;
import com.oracle.svm.reflect.hosted.ReflectionSubstitutionType.ReflectionSubstitutionMethod;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Maintains a mapping between reflection-accessible {@link Executable}s and {@link Proxy} classes
 * used to invoke the method/constructor reflectively. Also holds a mapping between {@link Proxy}
 * classes and {@link ReflectionSubstitutionType}s that are used as substitutions for the proxy
 * classes at image build time.
 * <p>
 * The proxy classes are generated dynamically at build time, in the hosted environment. There is
 * one proxy class per reflection-accessible {@link Executable} (but there may be multiple instances
 * of each proxy class). Instances of the proxy classes are used for the accessor fields of
 * {@link Executable} instances in Native Image (see {@link ExecutableAccessorComputer}). The proxy
 * instances dispatch calls to a placeholder invocation handler that only implements
 * {@code toString()} (see {@link ReflectionProxyHelper#setDefaultInvocationHandler}).
 * <p>
 * At image build time, the proxy instances are substituted with {@link ReflectionSubstitutionType}s
 * that either implement {@code FieldAccessor}, {@code ConstructorAccessor} or
 * {@code MethodAccessor} (depending on the type of {@link Executable}).
 */
final class ReflectionSubstitution extends CustomSubstitution<ReflectionSubstitutionType> {

    private static final String PROXY_NAME_SEPARATOR = "_";

    private final ClassInitializationSupport classInitializationSupport;

    private static final int ACC_PUBLIC = 0x00000001;
    private static final int ACC_FINAL = 0x00000010;
    private static final int ACC_SUPER = 0x00000020;

    private final Method defineClass;
    private final Method resolveClass;

    private final ResolvedJavaType reflectionProxy;
    private final ResolvedJavaType javaLangReflectProxy;

    /**
     * Maps each {@link Executable} accessible by reflection to the dynamic proxy class that should
     * be used to access it reflectively.
     */
    private final HashMap<Executable, Class<?>> proxyMap = new HashMap<>();

    /**
     * Maps the type of each generated dynamic proxy class to the {@link Executable} that it
     * accesses reflectively.
     */
    private final HashMap<ResolvedJavaType, Executable> typeToMember = new HashMap<>();

    private static final AtomicInteger proxyNr = new AtomicInteger(0);

    private final ImageClassLoader imageClassLoader;

    ReflectionSubstitution(MetaAccessProvider metaAccess, ClassInitializationSupport initializationSupport, ImageClassLoader classLoader) {
        super(metaAccess);
        defineClass = ReflectionUtil.lookupMethod(ClassLoader.class, "defineClass", String.class, byte[].class, int.class, int.class);
        resolveClass = ReflectionUtil.lookupMethod(ClassLoader.class, "resolveClass", Class.class);
        reflectionProxy = metaAccess.lookupJavaType(ReflectionProxy.class);
        javaLangReflectProxy = metaAccess.lookupJavaType(java.lang.reflect.Proxy.class);
        classInitializationSupport = initializationSupport;
        imageClassLoader = classLoader;
    }

    /**
     * Gets a unique, stable, fully qualified name for the {@link Proxy} class used to access a
     * given member.
     */
    static String getStableProxyName(Executable member) {
        return "com.oracle.svm.reflect." + SubstrateUtil.uniqueShortName(member);
    }

    private static Class<?> getAccessorInterface(Executable member) {
        if (member instanceof Method) {
            return packageJdkInternalReflectClassForName("MethodAccessor");
        } else if (member instanceof Constructor) {
            return packageJdkInternalReflectClassForName("ConstructorAccessor");
        }
        throw VMError.shouldNotReachHere();
    }

    /**
     * Track classes in the {@code reflect} package across JDK versions.
     */
    private static Class<?> packageJdkInternalReflectClassForName(String className) {
        final String packageName = (JavaVersionUtil.JAVA_SPEC <= 8 ? "sun.reflect." : "jdk.internal.reflect.");
        try {
            /* { Allow reflection in hosted code. Checkstyle: stop. */
            return Class.forName(packageName + className);
            /* } Allow reflection in hosted code. Checkstyle: resume. */
        } catch (ClassNotFoundException cnfe) {
            throw VMError.shouldNotReachHere(cnfe);
        }
    }

    private static Method generateProxyMethod;

    /**
     * Generates bytecode for a dynamic proxy class that implements the given interfaces, for JDK 13
     * or earlier.
     *
     * @param name name of the proxy class to generate
     * @param interfaces interfaces that the proxy class should implement
     * @return bytecode of the generated class
     * @see <a href=
     *      "https://docs.oracle.com/javase/8/docs/technotes/guides/reflection/proxy.html">Dynamic
     *      Proxy class documentation</a>
     */
    private static byte[] generateProxyClass(final String name, Class<?>[] interfaces) {
        /* { Allow reflection in hosted code. Checkstyle: stop. */
        try {
            if (generateProxyMethod == null) {
                final String packageName = (JavaVersionUtil.JAVA_SPEC <= 8 ? "sun.misc." : "java.lang.reflect.");
                generateProxyMethod = ReflectionUtil.lookupMethod(Class.forName(packageName + "ProxyGenerator"), "generateProxyClass", String.class, Class[].class);
            }
            return (byte[]) generateProxyMethod.invoke(null, name, interfaces);
        } catch (ReflectiveOperationException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    /**
     * Generates bytecode for a dynamic proxy class that implements the given interfaces, for JDK 14
     * or later.
     *
     * @param name name of the proxy class to generate
     * @param interfaces interfaces that the proxy class should implement
     * @return bytecode of the generated class
     * @see <a href=
     *      "https://docs.oracle.com/javase/8/docs/technotes/guides/reflection/proxy.html">Dynamic
     *      Proxy class documentation</a>
     */
    private static byte[] generateProxyClass14(final String name, Class<?>[] interfaces, ClassLoader loader) {
        assert JavaVersionUtil.JAVA_SPEC >= 14;
        /*
         * We use reflection to invoke ProxyGenerator.generateProxyClass in a cross-compatible way
         * across JDK versions.
         *
         * { Allow reflection in hosted code. Checkstyle: stop.
         */
        try {
            if (generateProxyMethod == null) {
                generateProxyMethod = ReflectionUtil.lookupMethod(Class.forName("java.lang.reflect.ProxyGenerator"), "generateProxyClass", ClassLoader.class, String.class, List.class, int.class);
            }
            List<Class<?>> ilist = new ArrayList<>(Arrays.asList(interfaces));
            return (byte[]) generateProxyMethod.invoke(null, loader, name, ilist, (ACC_PUBLIC | ACC_FINAL | ACC_SUPER));
        } catch (ReflectiveOperationException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
        /* } Allow reflection in hosted code. Checkstyle: resume. */
    }

    /**
     * Gets or creates a proxy class for accessing a given {@link Executable} reflectively.
     * <p>
     * The proxy class extends {@link Proxy} and implements either {@code FieldAccessor},
     * {@code MethodAccessor} or {@code ConstructorAccessor}.
     */
    synchronized Class<?> getProxyClass(Executable member) {
        Class<?> proxyClass = proxyMap.get(member);
        if (proxyClass == null) {
            /* the unique ID is added for unit tests that don't change the class loader */
            ClassLoader loader = imageClassLoader.getClassLoader();
            String name = getStableProxyName(member) + PROXY_NAME_SEPARATOR + proxyNr.incrementAndGet();
            Class<?>[] ifaces;
            if (member instanceof Method && !Modifier.isStatic(member.getModifiers()) && !Modifier.isAbstract(member.getModifiers())) {
                ifaces = new Class<?>[]{getAccessorInterface(member), ReflectionProxy.class, InvokeSpecialReflectionProxy.class};
            } else {
                ifaces = new Class<?>[]{getAccessorInterface(member), ReflectionProxy.class};
            }
            byte[] proxyBC;
            if (JavaVersionUtil.JAVA_SPEC < 14) {
                proxyBC = generateProxyClass(name, ifaces);
            } else {
                proxyBC = generateProxyClass14(name, ifaces, loader);
            }
            try {
                proxyClass = (Class<?>) defineClass.invoke(loader, name, proxyBC, 0, proxyBC.length);
                resolveClass.invoke(loader, proxyClass);
                proxyMap.put(member, proxyClass);

                ResolvedJavaType type = metaAccess.lookupJavaType(proxyClass);
                typeToMember.put(type, member);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                throw VMError.shouldNotReachHere(ex);
            }
        }
        /* Always initialize proxy classes. */
        classInitializationSupport.forceInitializeHosted(proxyClass, "all proxy classes are initialized", false);
        return proxyClass;
    }

    private boolean isReflectionProxy(ResolvedJavaType type) {
        return reflectionProxy.isAssignableFrom(type) && javaLangReflectProxy.isAssignableFrom(type);
    }

    @Override
    public ResolvedJavaType lookup(ResolvedJavaType type) {
        if (isReflectionProxy(type)) {
            return getSubstitution(type);
        } else {
            return type;
        }
    }

    @Override
    public ResolvedJavaMethod lookup(ResolvedJavaMethod method) {
        if (isReflectionProxy(method.getDeclaringClass())) {
            ReflectionSubstitutionType declaringClass = getSubstitution(method.getDeclaringClass());
            ReflectionSubstitutionMethod result = declaringClass.getSubstitutionMethod(method);
            if (result != null) {
                return result;
            }
        }

        return method;
    }

    @Override
    public ResolvedJavaType resolve(ResolvedJavaType type) {
        if (type instanceof ReflectionSubstitutionType) {
            return ((ReflectionSubstitutionType) type).getOriginal();
        } else {
            return type;
        }
    }

    @Override
    public ResolvedJavaMethod resolve(ResolvedJavaMethod method) {
        if (method instanceof ReflectionSubstitutionMethod) {
            return ((ReflectionSubstitutionMethod) method).getOriginal();
        } else {
            return method;
        }
    }

    private synchronized ReflectionSubstitutionType getSubstitution(ResolvedJavaType proxyClass) {
        ReflectionSubstitutionType subst = getSubstitutionType(proxyClass);
        if (subst == null) {
            Executable member = typeToMember.get(proxyClass);
            subst = ImageSingletons.lookup(ReflectionSubstitutionType.Factory.class).create(proxyClass, member);
            addSubstitutionType(proxyClass, subst);
        }
        return subst;
    }
}
