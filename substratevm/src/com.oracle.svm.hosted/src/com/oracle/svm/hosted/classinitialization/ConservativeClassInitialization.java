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
package com.oracle.svm.hosted.classinitialization;

import java.lang.reflect.Proxy;

import org.graalvm.compiler.options.OptionKey;

import com.oracle.svm.hosted.NativeImageGenerator;

import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * This implementation delays initialization of all classes on the image classpath to runtime. It
 * still obeys the user requirements about delayed initialization of classes.
 */
public class ConservativeClassInitialization extends CommonClassInitializationSupport {

    public ConservativeClassInitialization(MetaAccessProvider metaAccess) {
        super(metaAccess);
    }

    @Override
    public void forceInitializeHosted(Class<?> clazz) {
        ensureClassInitialized(clazz);
    }

    @Override
    InitKind computeInitKindAndMaybeInitializeClass(Class<?> clazz, boolean memoize) {
        if (classInitKinds.containsKey(clazz)) {
            return classInitKinds.get(clazz);
        }

        /* Without doubt initialize all annotations. */
        if (clazz.isAnnotation()) {
            forceInitializeHierarchy(clazz);
            return InitKind.EAGER;
        }

        /* Well, and enums that got initialized while annotations are parsed. */
        if (clazz.isEnum() && !UNSAFE.shouldBeInitialized(clazz)) {
            if (memoize) {
                forceInitializeHierarchy(clazz);
            }
            return InitKind.EAGER;
        }

        /* GR-14698 Lambdas get eagerly initialized in the method code. */
        if (clazz.getTypeName().contains("$$Lambda$")) {
            if (memoize) {
                forceInitializeHierarchy(clazz);
            }
            return InitKind.EAGER;
        }

        InitKind result = defaultInitKindForClass(clazz);

        if (clazz.getSuperclass() != null) {
            result = result.max(computeInitKindAndMaybeInitializeClass(clazz.getSuperclass(), memoize));
        }
        result = result.max(processInterfaces(clazz, memoize));

        if (memoize) {
            if (!result.isDelayed()) {
                result = result.max(ensureClassInitialized(clazz));
            }
            InitKind previous = classInitKinds.put(clazz, result);
            assert previous == null || previous == result : "Overwriting existing value";
        }
        return result;
    }

    private InitKind processInterfaces(Class<?> clazz, boolean memoizeEager) {
        InitKind result = defaultInitKindForClass(clazz);
        for (Class<?> iface : clazz.getInterfaces()) {
            if (ClassInitializationFeature.declaresDefaultMethods(metaAccess.lookupJavaType(iface))) {
                /*
                 * An interface that declares default methods is initialized when a class
                 * implementing it is initialized. So we need to inherit the InitKind from such an
                 * interface.
                 */
                result = result.max(computeInitKindAndMaybeInitializeClass(iface, memoizeEager));
            } else {
                /*
                 * An interface that does not declare default methods is independent from a class
                 * that implements it, i.e., the interface can still be uninitialized even when the
                 * class is initialized.
                 */
                result = result.max(processInterfaces(iface, memoizeEager));
            }
        }
        return result;
    }

    private static InitKind defaultInitKindForClass(Class<?> clazz) {
        if (clazz.isPrimitive() || clazz.isArray()) {
            return InitKind.EAGER;
        } else if (clazz.isAnnotation()) {
            return InitKind.EAGER;
        } else if (Proxy.isProxyClass(clazz)) {
            /* Proxy classes end up as constants in heap. */
            return InitKind.EAGER;
        } else if (clazz.getTypeName().contains("$$Lambda$")) {
            /* GR-14698 Lambdas get eagerly initialized in the method code. */
            return InitKind.EAGER;
        } else {
            ClassLoader typeClassLoader = clazz.getClassLoader();
            if (typeClassLoader == null ||
                            typeClassLoader == NativeImageGenerator.class.getClassLoader() ||
                            typeClassLoader == com.sun.crypto.provider.SunJCE.class.getClassLoader() ||
                            /* JDK 11 */
                            typeClassLoader == OptionKey.class.getClassLoader()) {
                return InitKind.EAGER;
            }
        }
        return InitKind.DELAY;
    }

}
