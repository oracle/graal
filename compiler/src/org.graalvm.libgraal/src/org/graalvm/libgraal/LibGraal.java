/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.libgraal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.IsolateContext;
import org.graalvm.nativeimage.c.function.CEntryPoint.IsolateThreadContext;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.services.Services;

/**
 * Access to the libgraal isolate.
 *
 * All usage of the methods in this class must be guarded by {@link #isAvailable()}.
 */
public class LibGraal {

    /**
     * Determines if libgraal is available.
     */
    public static boolean isAvailable() {
        return libgraalIsolate != 0L;
    }

    /**
     * Gets the libgraal isolate.
     *
     * @returns a value that can be used for the {@link IsolateContext} argument of a {@code native}
     *          method bound to a {@link CEntryPoint} function in libgraal
     * @throws UnsatisfiedLinkError if libgraal is not {@linkplain #isAvailable() available}
     */
    public static long getIsolate() {
        if (initializationError != null) {
            throw initializationError;
        }
        return libgraalIsolate;
    }

    /**
     * Gets a libgraal isolate thread associated with the current thread. This method attaches the
     * current thread to an isolate thread first if necessary.
     *
     * @returns a value that can be used for the {@link IsolateThreadContext} argument of a
     *          {@code native} method bound to a {@link CEntryPoint} function in libgraal
     * @throws UnsatisfiedLinkError if libgraal is not {@linkplain #isAvailable() available}
     */
    public static long getIsolateThread() {
        return CURRENT_ISOLATE_THREAD.get();
    }

    private static final ThreadLocal<Long> CURRENT_ISOLATE_THREAD = new ThreadLocal<Long>() {
        @Override
        protected Long initialValue() {
            if (initializationError != null) {
                throw initializationError;
            }
            return attachThread(libgraalIsolate);
        }
    };

    private static final long libgraalIsolate = Services.IS_BUILDING_NATIVE_IMAGE ? 0L : initializeLibgraal();
    private static LinkageError initializationError;

    private static long initializeLibgraal() {
        try {
            // Initialize JVMCI to ensure JVMCI opens its packages to
            // Graal otherwise the call to HotSpotJVMCIRuntime.runtime()
            // below will fail on JDK9+.
            Services.initializeJVMCI();

            HotSpotJVMCIRuntime runtime = HotSpotJVMCIRuntime.runtime();

            // Use reflection so this code can be compiled on JDKs with missing
            // or incompatible signature of registerNativeMethods.
            Method method = runtime.getClass().getDeclaredMethod("registerNativeMethods", Class.class);
            if (method.getReturnType() == long[].class) {
                try {
                    long[] nativeInterface = (long[]) method.invoke(runtime, LibGraal.class);
                    return nativeInterface[1];
                } catch (InvocationTargetException e) {
                    Throwable targetException = e.getTargetException();
                    if (targetException instanceof UnsatisfiedLinkError) {
                        initializationError = (LinkageError) targetException;
                        return 0L;
                    }
                    if (targetException instanceof Error) {
                        throw (Error) targetException;
                    }
                    if (targetException instanceof RuntimeException) {
                        throw (RuntimeException) targetException;
                    }
                    throw new InternalError(targetException);
                }
            } else {
                // The signature of HotSpotJVMCIRuntime.registerNativeMethods changed
                // to support libgraal. JDKs that don't have full JVMCI support for
                // libgraal will have the old signature.
                initializationError = new NoSuchMethodError("long[] " + HotSpotJVMCIRuntime.class.getName() + ".registerNativeMethods(Class)");
                return 0L;
            }
        } catch (UnsatisfiedLinkError e) {
            initializationError = e;
            return 0L;
        } catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException e) {
            throw new InternalError(e);
        }
    }

    /**
     * Attaches the current thread to a thread in {@code isolate}.
     *
     * @param isolate
     */
    private static native long attachThread(long isolate);
}
