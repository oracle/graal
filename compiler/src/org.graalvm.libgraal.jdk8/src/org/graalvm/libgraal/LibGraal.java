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

import java.lang.reflect.Method;
import java.util.Arrays;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotSpeculationLog;
import jdk.vm.ci.services.Services;

/**
 * JDK8 version of {@code LibGraal}.
 */
public class LibGraal {

    static final Method runtimeAttachCurrentThread;

    static {
        Method attachCurrentThread = null;

        Class<?> runtimeClass = HotSpotJVMCIRuntime.class;
        for (Method m : runtimeClass.getDeclaredMethods()) {
            if (m.getName().equals("attachCurrentThread")) {
                if (attachCurrentThread != null) {
                    throw new InternalError(String.format("Expected single method named attachCurrentThread, found %s and %s",
                                    attachCurrentThread, m));
                }
                attachCurrentThread = m;
            }
        }
        if (attachCurrentThread == null) {
            throw new InternalError("Cannot find attachCurrentThread in " + runtimeClass);
        }
        runtimeAttachCurrentThread = attachCurrentThread;

        Class<?>[] v1 = {Boolean.TYPE};
        Class<?>[] v2 = {Boolean.TYPE, long[].class};
        Class<?>[] parameterTypes = attachCurrentThread.getParameterTypes();
        if (!Arrays.equals(parameterTypes, v1) && !Arrays.equals(parameterTypes, v2)) {
            throw new InternalError(String.format("Unexpected signature for attachCurrentThread: %s",
                            Arrays.toString(parameterTypes)));
        }
    }

    public static boolean isAvailable() {
        return inLibGraal() || available;
    }

    public static boolean isSupported() {
        return true;
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
        runtime.registerNativeMethods(clazz);
    }

    public static long translate(HotSpotJVMCIRuntime runtime, Object obj) {
        if (!isAvailable()) {
            throw new IllegalStateException();
        }
        if (!inLibGraal() && LibGraalScope.currentScope.get() == null) {
            throw new IllegalStateException("Not within a " + LibGraalScope.class.getName());
        }
        return runtime.translate(obj);
    }

    public static <T> T unhand(HotSpotJVMCIRuntime runtime, Class<T> type, long handle) {
        if (!isAvailable()) {
            throw new IllegalStateException();
        }
        if (!inLibGraal() && LibGraalScope.currentScope.get() == null) {
            throw new IllegalStateException("Not within a " + LibGraalScope.class.getName());
        }
        return runtime.unhand(type, handle);
    }

    private static long initializeLibgraal() {
        try {
            HotSpotJVMCIRuntime runtime = HotSpotJVMCIRuntime.runtime();
            long[] javaVMInfo = runtime.registerNativeMethods(LibGraal.class);
            long isolate = javaVMInfo[1];
            return isolate;
        } catch (UnsupportedOperationException e) {
            return 0L;
        }
    }

    static final long initialIsolate = Services.IS_BUILDING_NATIVE_IMAGE ? 0L : initializeLibgraal();
    static final boolean available = initialIsolate != 0L;

    static boolean isCurrentThreadAttached(HotSpotJVMCIRuntime runtime) {
        return runtime.isCurrentThreadAttached();
    }

    public static boolean attachCurrentThread(HotSpotJVMCIRuntime runtime, boolean isDaemon, long[] isolate) {
        try {
            if (runtimeAttachCurrentThread.getParameterCount() == 2) {
                long[] javaVMInfo = isolate != null ? new long[4] : null;
                boolean res = (boolean) runtimeAttachCurrentThread.invoke(runtime, isDaemon, javaVMInfo);
                if (isolate != null) {
                    isolate[0] = javaVMInfo[1];
                }
                return res;
            } else {
                if (isolate != null) {
                    isolate[0] = initialIsolate;
                }
                return (boolean) runtimeAttachCurrentThread.invoke(runtime, isDaemon);
            }
        } catch (Throwable throwable) {
            throw new InternalError(throwable);
        }
    }

    public static void detachCurrentThread(HotSpotJVMCIRuntime runtime) {
        runtime.detachCurrentThread();
    }

    static native long getCurrentIsolateThread(long iso);

    public static long getFailedSpeculationsAddress(HotSpotSpeculationLog log) {
        return log.getFailedSpeculationsAddress();
    }
}
