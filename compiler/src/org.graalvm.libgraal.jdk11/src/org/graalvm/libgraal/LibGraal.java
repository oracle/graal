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

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotSpeculationLog;
import jdk.vm.ci.services.Services;

/**
 * JDK11 version of {@code LibGraal}.
 */
public class LibGraal {

    static final Method runtimeUnhand;
    static final Method runtimeTranslate;
    static final Method runtimeRegisterNativeMethods;
    static final Method runtimeIsCurrentThreadAttached;
    static final Method runtimeAttachCurrentThread;
    static final Method runtimeDetachCurrentThread;
    static final Method speculationLogGetFailedSpeculationsAddress;

    static {
        Method unhand = null;
        Method translate = null;
        Method registerNativeMethods = null;
        Method isCurrentThreadAttached = null;
        Method attachCurrentThread = null;
        Method detachCurrentThread = null;
        Method getFailedSpeculationsAddress = null;
        boolean firstFound = false;
        // Initialize JVMCI to ensure JVMCI opens its packages to
        // Graal otherwise the call to HotSpotJVMCIRuntime.runtime()
        // below will fail on JDK13+.
        Services.initializeJVMCI();

        Class<?> runtimeClass = HotSpotJVMCIRuntime.class;
        try {
            unhand = runtimeClass.getDeclaredMethod("unhand", Class.class, Long.TYPE);
            firstFound = true;
            translate = runtimeClass.getDeclaredMethod("translate", Object.class);
            registerNativeMethods = runtimeClass.getDeclaredMethod("registerNativeMethods", Class.class);
            isCurrentThreadAttached = runtimeClass.getDeclaredMethod("isCurrentThreadAttached");
            attachCurrentThread = runtimeClass.getDeclaredMethod("attachCurrentThread", Boolean.TYPE);
            detachCurrentThread = runtimeClass.getDeclaredMethod("detachCurrentThread");
            getFailedSpeculationsAddress = HotSpotSpeculationLog.class.getDeclaredMethod("getFailedSpeculationsAddress");
        } catch (NoSuchMethodException | SecurityException e) {
            // If the very first method is unavailable assume nothing is available. Otherwise only
            // some are missing so complain about it.
            if (firstFound) {
                throw new InternalError("some methods are unavailable", e);
            }
        }

        runtimeUnhand = unhand;
        runtimeTranslate = translate;
        runtimeRegisterNativeMethods = registerNativeMethods;
        runtimeIsCurrentThreadAttached = isCurrentThreadAttached;
        runtimeAttachCurrentThread = attachCurrentThread;
        runtimeDetachCurrentThread = detachCurrentThread;
        speculationLogGetFailedSpeculationsAddress = getFailedSpeculationsAddress;
    }

    public static boolean isAvailable() {
        return inLibGraal() || isolate != 0L;
    }

    public static boolean isSupported() {
        return speculationLogGetFailedSpeculationsAddress != null;
    }

    public static boolean inLibGraal() {
        return Services.IS_IN_NATIVE_IMAGE;
    }

    public static void registerNativeMethods(HotSpotJVMCIRuntime runtime, Class<?> clazz) {
        if (clazz.isPrimitive()) {
            throw new IllegalArgumentException();
        }
        if (inLibGraal() || !isAvailable()) {
            throw new IllegalStateException();
        }
        try {
            runtimeRegisterNativeMethods.invoke(runtime, clazz);
        } catch (Error e) {
            throw e;
        } catch (Throwable throwable) {
            throw new InternalError(throwable);
        }
    }

    public static long translate(HotSpotJVMCIRuntime runtime, Object obj) {
        if (!isAvailable()) {
            throw new IllegalStateException();
        }
        if (!inLibGraal() && LibGraalScope.currentScope.get() == null) {
            throw new IllegalStateException("Not within a " + LibGraalScope.class.getName());
        }
        try {
            return (long) runtimeTranslate.invoke(runtime, obj);
        } catch (Throwable throwable) {
            throw new InternalError(throwable);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T unhand(HotSpotJVMCIRuntime runtime, Class<T> type, long handle) {
        if (!isAvailable()) {
            throw new IllegalStateException();
        }
        if (!inLibGraal() && LibGraalScope.currentScope.get() == null) {
            throw new IllegalStateException("Not within a " + LibGraalScope.class.getName());
        }
        try {
            return (T) runtimeUnhand.invoke(runtime, type, handle);
        } catch (Throwable throwable) {
            throw new InternalError(throwable);
        }
    }

    private static long initializeLibgraal() {
        if (runtimeRegisterNativeMethods == null) {
            return 0L;
        }
        try {
            // Initialize JVMCI to ensure JVMCI opens its packages to
            // Graal otherwise the call to HotSpotJVMCIRuntime.runtime()
            // below might fail
            Services.initializeJVMCI();

            HotSpotJVMCIRuntime runtime = HotSpotJVMCIRuntime.runtime();

            long[] nativeInterface = (long[]) runtimeRegisterNativeMethods.invoke(runtime, LibGraal.class);
            return nativeInterface[1];
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof UnsupportedOperationException) {
                return 0L;
            }
            throw new InternalError(e);
        } catch (Throwable throwable) {
            throw new InternalError(throwable);
        }
    }

    static final long isolate = Services.IS_BUILDING_NATIVE_IMAGE ? 0L : initializeLibgraal();

    static boolean isCurrentThreadAttached(HotSpotJVMCIRuntime runtime) {
        try {
            return (boolean) runtimeIsCurrentThreadAttached.invoke(runtime);
        } catch (Throwable throwable) {
            throw new InternalError(throwable);
        }
    }

    public static boolean attachCurrentThread(HotSpotJVMCIRuntime runtime, boolean isDaemon) {
        try {
            return (boolean) runtimeAttachCurrentThread.invoke(runtime, isDaemon);
        } catch (Throwable throwable) {
            throw new InternalError(throwable);
        }
    }

    public static void detachCurrentThread(HotSpotJVMCIRuntime runtime) {
        try {
            runtimeDetachCurrentThread.invoke(runtime);
        } catch (Throwable throwable) {
            throw new InternalError(throwable);
        }
    }

    static native long getCurrentIsolateThread(long iso);

    public static long getFailedSpeculationsAddress(HotSpotSpeculationLog log) {
        if (speculationLogGetFailedSpeculationsAddress != null) {
            try {
                return (long) speculationLogGetFailedSpeculationsAddress.invoke(log);
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        throw new UnsupportedOperationException();
    }
}
