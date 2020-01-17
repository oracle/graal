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
package com.oracle.svm.core.jdk.proxy;

// Checkstyle: allow reflection

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.JDK8OrEarlier;
import com.oracle.svm.core.jdk.JDK11OrLater;

@TargetClass(java.lang.reflect.Proxy.class)
final class Target_java_lang_reflect_Proxy {

    /** We have our own proxy cache so mark the original one as deleted. */
    @Delete //
    @TargetElement(onlyWith = JDK8OrEarlier.class) //
    private static Target_java_lang_reflect_WeakCache proxyClassCache;

    @Substitute
    @TargetElement(onlyWith = JDK8OrEarlier.class) //
    private static Class<?> getProxyClass0(@SuppressWarnings("unused") ClassLoader loader, Class<?>... interfaces) {
        return ImageSingletons.lookup(DynamicProxyRegistry.class).getProxyClass(interfaces);
    }

    /** We have our own proxy cache so mark the original one as deleted. */
    @Delete //
    @TargetElement(onlyWith = JDK11OrLater.class) //
    private static Target_jdk_internal_loader_ClassLoaderValue proxyCache;

    @Substitute
    @TargetElement(onlyWith = JDK11OrLater.class)
    @SuppressWarnings("unused")
    private static Constructor<?> getProxyConstructor(Class<?> caller, ClassLoader loader, Class<?>... interfaces) {
        final Class<?> cl = ImageSingletons.lookup(DynamicProxyRegistry.class).getProxyClass(interfaces);
        try {
            final Constructor<?> cons = cl.getConstructor(InvocationHandler.class);
            if (!Modifier.isPublic(cl.getModifiers())) {
                cons.setAccessible(true);
            }
            return cons;
        } catch (NoSuchMethodException e) {
            throw new InternalError(e.toString(), e);
        }
    }

    @Substitute
    public static boolean isProxyClass(Class<?> cl) {
        return Proxy.class.isAssignableFrom(cl) && ImageSingletons.lookup(DynamicProxyRegistry.class).isProxyClass(cl);
    }

    /*
     * We are defensive and handle native methods by marking them as deleted. If they are reachable,
     * the user is certainly doing something wrong. But we do not want to fail with a linking error.
     */
    @Delete //
    @TargetElement(onlyWith = JDK8OrEarlier.class) //
    private static native Class<?> defineClass0(ClassLoader loader, String name, byte[] b, int off, int len);
}

@TargetClass(className = "java.lang.reflect.WeakCache", onlyWith = JDK8OrEarlier.class)
final class Target_java_lang_reflect_WeakCache {
}

@TargetClass(className = "jdk.internal.loader.ClassLoaderValue", onlyWith = JDK11OrLater.class)
final class Target_jdk_internal_loader_ClassLoaderValue {
}

public class ProxySubstitutions {
}
