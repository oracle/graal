/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api;

import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import com.oracle.truffle.api.impl.DefaultTruffleRuntime;

/**
 * Class for obtaining the Truffle runtime singleton object of this virtual machine.
 *
 * @since 0.8 or earlier
 */
public final class Truffle {

    private Truffle() {
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
                Iterator<TruffleRuntimeAccess> it = lookup.iterator();
                while (it.hasNext()) {
                    TruffleRuntimeAccess access;
                    try {
                        access = it.next();
                    } catch (ServiceConfigurationError err) {
                        continue;
                    }
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
                if (runtimeClassName != null && runtimeClassName.length() > 0) {
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
