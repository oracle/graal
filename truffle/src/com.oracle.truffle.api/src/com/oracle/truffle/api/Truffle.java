/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;

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

    private static TruffleRuntimeAccess selectTruffleRuntimeAccess(List<Iterable<TruffleRuntimeAccess>> lookups) {
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
                        throw new InternalError(e);
                    }
                }

                List<Iterable<TruffleRuntimeAccess>> loaders = LanguageAccessor.jdkServicesAccessor().getTruffleRuntimeLoaders(TruffleRuntimeAccess.class);
                TruffleRuntimeAccess access = selectTruffleRuntimeAccess(loaders);

                if (access != null) {
                    LanguageAccessor.jdkServicesAccessor().exportTo(access.getClass());
                    return access.getRuntime();
                }
                return new DefaultTruffleRuntime();
            }
        });
    }
}
