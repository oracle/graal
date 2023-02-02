/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.java.LambdaUtils;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ImageClassLoader;

import jdk.vm.ci.meta.MetaAccessProvider;

class AllowAllHostedUsagesClassInitializationSupport extends ClassInitializationSupport {

    AllowAllHostedUsagesClassInitializationSupport(MetaAccessProvider metaAccess, ImageClassLoader loader) {
        super(metaAccess, loader);
    }

    @Override
    public void initializeAtBuildTime(Class<?> aClass, String reason) {
        UserError.guarantee(!configurationSealed, "The class initialization configuration can be changed only before the phase analysis.");
        Class<?> cur = aClass;
        do {
            classInitializationConfiguration.insert(cur.getTypeName(), InitKind.BUILD_TIME, cur == aClass ? reason : "super type of " + aClass.getTypeName(), true);
            initializeInterfacesAtBuildTime(cur.getInterfaces(), "interface of " + aClass.getTypeName());
            cur = cur.getSuperclass();
        } while (cur != null);
    }

    private void initializeInterfacesAtBuildTime(Class<?>[] interfaces, String reason) {
        for (Class<?> iface : interfaces) {
            if (metaAccess.lookupJavaType(iface).declaresDefaultMethods()) {
                classInitializationConfiguration.insert(iface.getTypeName(), InitKind.BUILD_TIME, reason, true);
            }
            initializeInterfacesAtBuildTime(iface.getInterfaces(), reason);
        }
    }

    @Override
    public void initializeAtRunTime(Class<?> clazz, String reason) {
        UserError.guarantee(!configurationSealed, "The class initialization configuration can be changed only before the phase analysis.");
        classInitializationConfiguration.insert(clazz.getTypeName(), InitKind.RUN_TIME, reason, true);
    }

    @Override
    public void rerunInitialization(Class<?> clazz, String reason) {
        /* There is no more difference between RUN_TIME and RERUN. */
        initializeAtRunTime(clazz, reason);
    }

    @Override
    public void rerunInitialization(String name, String reason) {
        /* There is no more difference between RUN_TIME and RERUN. */
        initializeAtRunTime(name, reason);
    }

    @Override
    String reasonForClass(Class<?> clazz) {
        InitKind initKind = classInitKinds.get(clazz);
        String reason = classInitializationConfiguration.lookupReason(clazz.getTypeName());
        if (initKind.isRunTime()) {
            return "classes are initialized at run time by default";
        } else if (reason != null) {
            return reason;
        } else {
            throw VMError.shouldNotReachHere("Must be either proven or specified");
        }
    }

    @Override
    public void forceInitializeHosted(Class<?> clazz, String reason, boolean allowInitializationErrors) {
        if (clazz == null) {
            return;
        }
        classInitializationConfiguration.insert(clazz.getTypeName(), InitKind.BUILD_TIME, reason, true);
        InitKind initKind = ensureClassInitialized(clazz, allowInitializationErrors);
        classInitKinds.put(clazz, initKind);

        forceInitializeHosted(clazz.getSuperclass(), "super type of " + clazz.getTypeName(), allowInitializationErrors);
        forceInitializeInterfaces(clazz.getInterfaces(), "super type of " + clazz.getTypeName());
    }

    private void forceInitializeInterfaces(Class<?>[] interfaces, String reason) {
        for (Class<?> iface : interfaces) {
            if (metaAccess.lookupJavaType(iface).declaresDefaultMethods()) {
                classInitializationConfiguration.insert(iface.getTypeName(), InitKind.BUILD_TIME, reason, true);

                ensureClassInitialized(iface, false);
                classInitKinds.put(iface, InitKind.BUILD_TIME);
            }
            forceInitializeInterfaces(iface.getInterfaces(), "super type of " + iface.getTypeName());
        }
    }

    @Override
    boolean checkDelayedInitialization() {
        /* Nothing to check, all classes are allowed to be initialized in the image builder VM. */
        return true;
    }

    @Override
    InitKind computeInitKindAndMaybeInitializeClass(Class<?> clazz) {
        return computeInitKindAndMaybeInitializeClass(clazz, true);
    }

    /**
     * Computes the class initialization kind of the provided class, all superclasses, and all
     * interfaces that the provided class depends on (i.e., interfaces implemented by the provided
     * class that declare default methods).
     *
     * Also defines class initialization based on a policy of the subclass.
     */
    InitKind computeInitKindAndMaybeInitializeClass(Class<?> clazz, boolean memoize) {
        InitKind existing = classInitKinds.get(clazz);
        if (existing != null) {
            return existing;
        }

        if (clazz.isPrimitive()) {
            forceInitializeHosted(clazz, "primitive types are initialized at build time", false);
            return InitKind.BUILD_TIME;
        }

        if (clazz.isArray()) {
            forceInitializeHosted(clazz, "arrays are initialized at build time", false);
            return InitKind.BUILD_TIME;
        }

        InitKind specifiedInitKind = specifiedInitKindFor(clazz);
        InitKind clazzResult = specifiedInitKind != null ? specifiedInitKind : InitKind.RUN_TIME;

        InitKind superResult = InitKind.BUILD_TIME;
        if (clazz.getSuperclass() != null) {
            superResult = superResult.max(computeInitKindAndMaybeInitializeClass(clazz.getSuperclass(), memoize));
        }
        superResult = superResult.max(processInterfaces(clazz, memoize));

        if (superResult == InitKind.BUILD_TIME && (Proxy.isProxyClass(clazz) || LambdaUtils.isLambdaType(metaAccess.lookupJavaType(clazz)))) {
            forceInitializeHosted(clazz, "proxy/lambda classes with interfaces initialized at build time are also initialized at build time", false);
            return InitKind.BUILD_TIME;
        }

        InitKind result = superResult.max(clazzResult);

        if (memoize) {
            if (!result.isRunTime()) {
                result = result.max(ensureClassInitialized(clazz, false));
            }

            InitKind previous = classInitKinds.putIfAbsent(clazz, result);
            if (previous != null && previous != result) {
                throw VMError.shouldNotReachHere("Conflicting class initialization kind: " + previous + " != " + result + " for " + clazz);
            }
        }
        return result;
    }

    private InitKind processInterfaces(Class<?> clazz, boolean memoizeEager) {
        /*
         * Note that we do not call computeInitKindForClass(clazz) on purpose: if clazz is the root
         * class or an interface declaring default methods, then
         * computeInitKindAndMaybeInitializeClass() already calls computeInitKindForClass. If the
         * interface does not declare default methods, than we must not take the InitKind of that
         * interface into account, because interfaces without default methods are independent from a
         * class initialization point of view.
         */
        InitKind result = InitKind.BUILD_TIME;

        for (Class<?> iface : clazz.getInterfaces()) {
            if (metaAccess.lookupJavaType(iface).declaresDefaultMethods()) {
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

    @Override
    void doLateInitialization(AnalysisUniverse aUniverse, AnalysisMetaAccess aMetaAccess) {
        /* Nothing for now. */
    }
}
