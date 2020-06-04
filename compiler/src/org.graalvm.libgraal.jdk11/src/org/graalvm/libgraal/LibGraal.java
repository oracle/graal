/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;
import static org.graalvm.libgraal.LibGraalScope.methodIf;
import static org.graalvm.libgraal.LibGraalScope.methodOrNull;
import static org.graalvm.libgraal.LibGraalScope.sig;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotSpeculationLog;
import jdk.vm.ci.services.Services;

/**
 * JDK11 version of {@code LibGraal}.
 */
public class LibGraal {

    static {
        // Initialize JVMCI to ensure JVMCI opens its packages to Graal.
        Services.initializeJVMCI();
    }

    private static final Method unhand = methodOrNull(HotSpotJVMCIRuntime.class, "unhand", sig(Class.class, Long.TYPE));
    private static final Method translate = methodIf(unhand, HotSpotJVMCIRuntime.class, "translate", sig(Object.class));
    private static final Method registerNativeMethods = methodIf(unhand, HotSpotJVMCIRuntime.class, "registerNativeMethods", sig(Class.class));
    private static final Method isCurrentThreadAttached = methodIf(unhand, HotSpotJVMCIRuntime.class, "isCurrentThreadAttached");
    private static final Method attachCurrentThread = methodIf(unhand, HotSpotJVMCIRuntime.class, "attachCurrentThread", sig(Boolean.TYPE, long[].class), sig(Boolean.TYPE));
    private static final Method detachCurrentThread = methodIf(unhand, HotSpotJVMCIRuntime.class, "detachCurrentThread", sig(Boolean.TYPE), sig());
    private static final Method getFailedSpeculationsAddress = methodIf(unhand, HotSpotSpeculationLog.class, "getFailedSpeculationsAddress");

    public static boolean isAvailable() {
        return inLibGraal() || available;
    }

    public static boolean isSupported() {
        return getFailedSpeculationsAddress != null;
    }

    public static boolean inLibGraal() {
        return Services.IS_IN_NATIVE_IMAGE;
    }

    public static void registerNativeMethods(Class<?> clazz) {
        if (clazz.isPrimitive()) {
            throw new IllegalArgumentException();
        }
        if (inLibGraal() || !isAvailable()) {
            throw new IllegalStateException();
        }
        try {
            registerNativeMethods.invoke(runtime(), clazz);
        } catch (Error e) {
            throw e;
        } catch (Throwable throwable) {
            throw new InternalError(throwable);
        }
    }

    public static long translate(Object obj) {
        if (!isAvailable()) {
            throw new IllegalStateException();
        }
        if (!inLibGraal() && LibGraalScope.currentScope.get() == null) {
            throw new IllegalStateException("Not within a " + LibGraalScope.class.getName());
        }
        try {
            return (long) translate.invoke(runtime(), obj);
        } catch (Throwable throwable) {
            throw new InternalError(throwable);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T unhand(Class<T> type, long handle) {
        if (!isAvailable()) {
            throw new IllegalStateException();
        }
        if (!inLibGraal() && LibGraalScope.currentScope.get() == null) {
            throw new IllegalStateException("Not within a " + LibGraalScope.class.getName());
        }
        try {
            return (T) unhand.invoke(runtime(), type, handle);
        } catch (Throwable throwable) {
            throw new InternalError(throwable);
        }
    }

    private static long initializeLibgraal() {
        if (registerNativeMethods == null) {
            return 0L;
        }
        try {
            long[] javaVMInfo = (long[]) registerNativeMethods.invoke(runtime(), LibGraalScope.class);
            long isolate = javaVMInfo[1];
            return isolate;
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof UnsupportedOperationException) {
                return 0L;
            }
            throw new InternalError(e);
        } catch (Throwable throwable) {
            throw new InternalError(throwable);
        }
    }

    static final long initialIsolate = Services.IS_BUILDING_NATIVE_IMAGE ? 0L : initializeLibgraal();
    static final boolean available = initialIsolate != 0L;

    static boolean isCurrentThreadAttached() {
        try {
            return (boolean) isCurrentThreadAttached.invoke(runtime());
        } catch (Throwable throwable) {
            throw new InternalError(throwable);
        }
    }

    public static boolean attachCurrentThread(boolean isDaemon, long[] isolate) {
        try {
            if (attachCurrentThread.getParameterCount() == 2) {
                long[] javaVMInfo = isolate != null ? new long[4] : null;
                boolean res = (boolean) attachCurrentThread.invoke(runtime(), isDaemon, javaVMInfo);
                if (isolate != null) {
                    isolate[0] = javaVMInfo[1];
                }
                return res;
            } else {
                if (isolate != null) {
                    isolate[0] = initialIsolate;
                }
                return (boolean) attachCurrentThread.invoke(runtime(), isDaemon);
            }
        } catch (Throwable throwable) {
            throw new InternalError(throwable);
        }
    }

    public static boolean detachCurrentThread(boolean release) {
        try {
            if (detachCurrentThread.getParameterCount() == 1) {
                return (Boolean) detachCurrentThread.invoke(runtime(), release);
            } else {
                detachCurrentThread.invoke(runtime());
                return false;
            }
        } catch (Throwable throwable) {
            throw new InternalError(throwable);
        }
    }

    public static long getFailedSpeculationsAddress(HotSpotSpeculationLog log) {
        if (getFailedSpeculationsAddress != null) {
            try {
                return (long) getFailedSpeculationsAddress.invoke(log);
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        throw new UnsupportedOperationException();
    }
}
