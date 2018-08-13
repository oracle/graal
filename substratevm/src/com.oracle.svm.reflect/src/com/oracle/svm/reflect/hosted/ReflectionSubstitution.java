/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

/* Allow imports of java.lang.reflect and sun.misc.ProxyGenerator: Checkstyle: allow reflection. */
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.compiler.serviceprovider.GraalServices;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.annotation.CustomSubstitution;
import com.oracle.svm.reflect.hosted.ReflectionSubstitutionType.ReflectionSubstitutionMethod;
import com.oracle.svm.reflect.helpers.ReflectionProxy;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

final class ReflectionSubstitution extends CustomSubstitution<ReflectionSubstitutionType> {

    private final Method defineClass;
    private final Method resolveClass;

    private final ResolvedJavaType reflectionProxy;
    private final ResolvedJavaType javaLangReflectProxy;

    private final HashMap<Member, Class<?>> proxyMap = new HashMap<>();
    private final HashMap<ResolvedJavaType, Member> typeToMember = new HashMap<>();

    private final ImageClassLoader imageClassLoader;

    private static final AtomicInteger proxyNr = new AtomicInteger();

    private static Method lookupPrivateMethod(Class<?> clazz, String name, Class<?>... args) {
        try {
            Method m = clazz.getDeclaredMethod(name, args);
            m.setAccessible(true);
            return m;
        } catch (Exception ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    ReflectionSubstitution(MetaAccessProvider metaAccess, ImageClassLoader classLoader) {
        super(metaAccess);
        defineClass = lookupPrivateMethod(ClassLoader.class, "defineClass", String.class, byte[].class, int.class, int.class);
        resolveClass = lookupPrivateMethod(ClassLoader.class, "resolveClass", Class.class);
        reflectionProxy = metaAccess.lookupJavaType(ReflectionProxy.class);
        javaLangReflectProxy = metaAccess.lookupJavaType(java.lang.reflect.Proxy.class);
        imageClassLoader = classLoader;
    }

    private static <T> String getSimpleNameSafe(Class<T> clazz) {
        return clazz.getName().substring(clazz.getName().lastIndexOf('.') + 1);
    }

    private static String getProxyClassname(Member member) {
        String className = getSimpleNameSafe(member.getDeclaringClass());
        String memberName;
        if (member instanceof Constructor) {
            memberName = className;
        } else {
            memberName = member.getName();
        }
        return "com.oracle.svm.reflect.proxies.Proxy_" + proxyNr.incrementAndGet() + "_" + className + "_" + memberName;
    }

    private static Class<?> getAccessorInterface(Member member) {
        if (member instanceof Field) {
            return packageJdkInternalReflectClassForName("FieldAccessor");
        } else if (member instanceof Method) {
            return packageJdkInternalReflectClassForName("MethodAccessor");
        } else if (member instanceof Constructor) {
            return packageJdkInternalReflectClassForName("ConstructorAccessor");
        }
        throw VMError.shouldNotReachHere();
    }

    /** Track classes in the `reflect` package across JDK versions. */
    private static Class<?> packageJdkInternalReflectClassForName(String className) {
        final String packageName = (GraalServices.Java8OrEarlier ? "sun.reflect." : "jdk.internal.reflect.");
        try {
            /* { Allow reflection in hosted code. Checkstyle: stop. */
            return Class.forName(packageName + className);
            /* } Allow reflection in hosted code. Checkstyle: resume. */
        } catch (ClassNotFoundException cnfe) {
            throw VMError.shouldNotReachHere(cnfe);
        }
    }

    private static Method generateProxyMethod;

    private static byte[] generateProxyClass(final String name, Class<?>[] interfaces) {
        /* { Allow reflection in hosted code. Checkstyle: stop. */
        try {
            if (generateProxyMethod == null) {
                final String packageName = (GraalServices.Java8OrEarlier ? "sun.misc." : "java.lang.reflect.");
                generateProxyMethod = Class.forName(packageName + "ProxyGenerator").getDeclaredMethod("generateProxyClass", String.class, Class[].class);
                generateProxyMethod.setAccessible(true);
            }
            return (byte[]) generateProxyMethod.invoke(null, name, interfaces);
        } catch (Throwable e) {
            throw new InternalError(e);
        }
        /* } Allow reflection in hosted code. Checkstyle: resume. */
    }

    Class<?> getProxyClass(Member member) {
        Class<?> ret = proxyMap.get(member);
        if (ret == null) {
            String name = getProxyClassname(member);
            Class<?> iface = getAccessorInterface(member);

            byte[] proxyBC = generateProxyClass(name, new Class<?>[]{iface, ReflectionProxy.class});

            try {
                ret = (Class<?>) defineClass.invoke(imageClassLoader.getClassLoader(), name, proxyBC, 0, proxyBC.length);
                resolveClass.invoke(imageClassLoader.getClassLoader(), ret);
                proxyMap.put(member, ret);

                ResolvedJavaType type = metaAccess.lookupJavaType(ret);
                typeToMember.put(type, member);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                throw VMError.shouldNotReachHere(ex);
            }
        }
        return ret;
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

    private ReflectionSubstitutionType getSubstitution(ResolvedJavaType original) {
        ReflectionSubstitutionType subst = getSubstitutionType(original);
        if (subst == null) {
            Member member = typeToMember.get(original);
            subst = new ReflectionSubstitutionType(original, member);
            addSubstitutionType(original, subst);
        }
        return subst;
    }
}
