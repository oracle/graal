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

import com.oracle.svm.core.hub.ClassInitializationInfo;
import com.oracle.svm.core.util.UserError;

import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * This class contains the hosted code to support initialization of select classes at runtime. It
 * handles the registration (implementing the functionality of the
 * {@link ClassInitializationSupport} API) and prepares the {@link ClassInitializationInfo} objects
 * that are used at runtime to do the initialization.
 */
public class EagerClassInitialization extends CommonClassInitializationSupport {

    public EagerClassInitialization(MetaAccessProvider metaAccess) {
        super(metaAccess);
    }

    /**
     * Computes the class initialization kind of the provided class, all superclasses, and all
     * interfaces that the provided class depends on (i.e., interfaces implemented by the provided
     * class that declare default methods).
     *
     * Also defines class initialization based on a policy of the subclass.
     */
    @Override
    InitKind computeInitKindAndMaybeInitializeClass(Class<?> clazz, boolean memoizeEager) {
        InitKind result = classInitKinds.get(clazz);
        if (result != null) {
            return result;
        }

        result = InitKind.EAGER;
        if (clazz.getSuperclass() != null) {
            result = result.max(computeInitKindAndMaybeInitializeClass(clazz.getSuperclass(), memoizeEager));
        }
        result = result.max(processInterfaces(clazz, memoizeEager));

        if (result != InitKind.EAGER || memoizeEager) {
            if (!result.isDelayed()) {
                result = result.max(ensureClassInitialized(clazz));
            }

            InitKind previous = classInitKinds.put(clazz, result);
            assert previous == null || previous == result : "Overwriting existing value";
        }
        return result;
    }

    private InitKind processInterfaces(Class<?> clazz, boolean memoizeEager) {
        InitKind result = InitKind.EAGER;
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

    @Override
    public void forceInitializeHosted(Class<?> clazz) {
        InitKind initKind = computeInitKindAndMaybeInitializeClass(clazz);
        if (initKind.isDelayed()) {
            throw UserError.abort("Cannot delay running the class initializer because class must be initialized for internal purposes: " + clazz.getTypeName());
        }
    }

}
