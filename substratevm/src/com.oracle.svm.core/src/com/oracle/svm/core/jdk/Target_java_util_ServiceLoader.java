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

package com.oracle.svm.core.jdk;

import java.lang.reflect.Constructor;
import java.net.URL;
import java.security.AccessControlContext;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;

/**
 * Disable the module based iteration in favour of classpath based iteration. See
 * ServiceLoaderFeature for explanation why.
 */
@TargetClass(value = java.util.ServiceLoader.class)
final class Target_java_util_ServiceLoader {
    @Alias Class<?> service;

    @Alias AccessControlContext acc;

    @Alias
    static native void fail(Class<?> service, String msg);

    @Alias
    native Constructor<?> getConstructor(Class<?> clazz);

    @Alias @RecomputeFieldValue(declClass = ArrayList.class, kind = RecomputeFieldValue.Kind.NewInstance)//
    private List<?> instantiatedProviders;
}

@TargetClass(value = java.util.ServiceLoader.class, innerClass = "ModuleServicesLookupIterator")
final class Target_java_util_ServiceLoader_ModuleServicesLookupIterator {
    @SuppressWarnings("unused")
    @Substitute
    Target_java_util_ServiceLoader_ModuleServicesLookupIterator(Target_java_util_ServiceLoader outer) {
    }

    @SuppressWarnings("static-method")
    @Substitute
    boolean hasNext() {
        return false;
    }
}

@TargetClass(value = java.util.ServiceLoader.class, innerClass = "LazyClassPathLookupIterator")
final class Target_java_util_ServiceLoader_LazyClassPathLookupIterator {
    @Alias//
    ServiceLoader.Provider<?> nextProvider;
    @Alias//
    ServiceConfigurationError nextError;

    /* Has to be initialized here, because we're substituting the constructor. */
    @SuppressWarnings("unused")//
    @Alias//
    Set<String> providerNames = new HashSet<>();

    /* Manage correct ref to the enclosing object ourselves so that we can access it. */
    @Alias//
    @TargetElement(name = "this$0")//
    Target_java_util_ServiceLoader outer;

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Enumeration<URL> configs;

    @Substitute
    Target_java_util_ServiceLoader_LazyClassPathLookupIterator(Target_java_util_ServiceLoader outer) {
        this.outer = outer;
    }

    @Alias
    private native Class<?> nextProviderClass();

    /**
     * Modified version of java.util.ServiceLoader.LazyClassPathLookupIterator#hasNextService.
     */
    @Substitute
    private boolean hasNextService() {
        while (nextProvider == null && nextError == null) {
            try {
                Class<?> clazz = nextProviderClass();
                if (clazz == null) {
                    return false;
                }

                /*-
                 * if (clazz.getModule().isNamed()) {
                 *   // ignore class if in named module
                 *  continue;
                 *  }
                 */

                if (outer.service.isAssignableFrom(clazz)) {
                    Constructor<?> ctor = outer.getConstructor(clazz);
                    nextProvider = (ServiceLoader.Provider<?>) ((Object) (new Target_java_util_ServiceLoader_ProviderImpl(outer.service, clazz, ctor, outer.acc)));
                } else {
                    Target_java_util_ServiceLoader.fail(outer.service, clazz.getName() + " not a subtype");
                }
            } catch (ServiceConfigurationError e) {
                nextError = e;
            }
        }
        return true;
    }
}

@TargetClass(value = java.util.ServiceLoader.class, innerClass = "ProviderImpl")
final class Target_java_util_ServiceLoader_ProviderImpl {

    @SuppressWarnings("unused")
    @Alias
    Target_java_util_ServiceLoader_ProviderImpl(Class<?> service,
                    Class<?> type,
                    Constructor<?> ctor,
                    AccessControlContext acc) {
    }

}
