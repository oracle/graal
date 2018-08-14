/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ServiceLoader;

import com.oracle.truffle.api.impl.DefaultTruffleRuntime;

/**
 * Class for obtaining the Truffle runtime singleton object of this virtual machine.
 *
 * @since 0.8 or earlier
 */
public class Truffle {
    /**
     * @deprecated Accidentally public - don't use.
     * @since 0.8 or earlier
     */
    @Deprecated
    public Truffle() {
    }

    private static final TruffleRuntime RUNTIME = initRuntime();

    /**
     * Gets the singleton {@link TruffleRuntime} object.
     *
     * @since 0.8 or earlier
     */
    public static TruffleRuntime getRuntime() {
        return RUNTIME;
    }

    @SafeVarargs
    private static TruffleRuntimeAccess selectTruffleRuntimeAccess(Iterable<TruffleRuntimeAccess>... lookups) {
        TruffleRuntimeAccess selectedAccess = null;
        for (Iterable<TruffleRuntimeAccess> lookup : lookups) {
            if (lookup != null) {
                for (TruffleRuntimeAccess access : lookup) {
                    if (selectedAccess == null) {
                        selectedAccess = access;
                    } else {
                        if (selectedAccess != access && selectedAccess.getClass() != access.getClass()) {
                            if (selectedAccess.getPriority() == access.getPriority()) {
                                throw new InternalError(String.format("Providers for %s with same priority %d: %s (loader: %s) vs. %s (loader: %s)",
                                                TruffleRuntimeAccess.class.getName(), access.getPriority(),
                                                selectedAccess, selectedAccess.getClass().getClassLoader(),
                                                access, access.getClass().getClassLoader()));
                            }
                            if (selectedAccess.getPriority() < access.getPriority()) {
                                selectedAccess = access;
                            }
                        }
                    }
                }
            }
        }
        return selectedAccess;
    }

    @SuppressWarnings("unchecked")
    private static Iterable<TruffleRuntimeAccess> reflectiveServiceLoaderLoad(Class<?> servicesClass) {
        try {
            Method m = servicesClass.getDeclaredMethod("load", Class.class);
            return (Iterable<TruffleRuntimeAccess>) m.invoke(null, TruffleRuntimeAccess.class);
        } catch (Throwable e) {
            // Fail fast for other errors
            throw (InternalError) new InternalError().initCause(e);
        }
    }

    /**
     * Gets the {@link TruffleRuntimeAccess} providers available on the JVMCI class path.
     */
    private static Iterable<TruffleRuntimeAccess> getJVMCIProviders() {
        ClassLoader cl = Truffle.class.getClassLoader();
        ClassLoader scl = ClassLoader.getSystemClassLoader();
        while (cl != null) {
            if (cl == scl) {
                /*
                 * If Truffle can see the app class loader, then it is not on the JVMCI class path.
                 * This means providers of TruffleRuntimeAccess on the JVMCI class path must be
                 * ignored as they will bind to the copy of Truffle resolved on the JVMCI class
                 * path. Failing to ignore will result in ServiceConfigurationErrors (e.g.,
                 * https://github.com/oracle/graal/issues/385#issuecomment-385313521).
                 */
                return null;
            }
            cl = cl.getParent();
        }

        // Go back through JVMCI renaming history...
        String[] serviceClassNames = {
                        "jdk.vm.ci.services.Services",
                        "jdk.vm.ci.service.Services",
                        "jdk.internal.jvmci.service.Services",
                        "com.oracle.jvmci.service.Services"
        };
        for (String serviceClassName : serviceClassNames) {
            try {
                return reflectiveServiceLoaderLoad(Class.forName(serviceClassName));
            } catch (ClassNotFoundException e) {
            }
        }
        return null;
    }

    private static TruffleRuntime initRuntime() {
        return AccessController.doPrivileged(new PrivilegedAction<TruffleRuntime>() {
            public TruffleRuntime run() {
                String runtimeClassName = System.getProperty("truffle.TruffleRuntime");
                if (runtimeClassName != null) {
                    try {
                        ClassLoader cl = Thread.currentThread().getContextClassLoader();
                        Class<?> runtimeClass = Class.forName(runtimeClassName, false, cl);
                        return (TruffleRuntime) runtimeClass.getDeclaredConstructor().newInstance();
                    } catch (Throwable e) {
                        // Fail fast for other errors
                        throw (InternalError) new InternalError().initCause(e);
                    }
                }

                TruffleRuntimeAccess access;
                boolean jdk8OrEarlier = System.getProperty("java.specification.version").compareTo("1.9") < 0;
                if (!jdk8OrEarlier) {
                    // As of JDK9, the JVMCI Services class should only be used for service types
                    // defined by JVMCI. Other services types should use ServiceLoader directly.
                    ServiceLoader<TruffleRuntimeAccess> standardProviders = ServiceLoader.load(TruffleRuntimeAccess.class);
                    access = selectTruffleRuntimeAccess(standardProviders);
                } else {
                    Iterable<TruffleRuntimeAccess> jvmciProviders = getJVMCIProviders();
                    if (Boolean.getBoolean("truffle.TrustAllTruffleRuntimeProviders")) {
                        ServiceLoader<TruffleRuntimeAccess> standardProviders = ServiceLoader.load(TruffleRuntimeAccess.class);
                        access = selectTruffleRuntimeAccess(jvmciProviders, standardProviders);
                    } else {
                        access = selectTruffleRuntimeAccess(jvmciProviders);
                    }
                }

                if (access != null) {
                    return access.getRuntime();
                }
                return new DefaultTruffleRuntime();
            }
        });
    }
}
