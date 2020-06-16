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
import static org.graalvm.libgraal.LibGraalScope.method;
import static org.graalvm.libgraal.LibGraalScope.sig;

import java.lang.reflect.Method;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotSpeculationLog;
import jdk.vm.ci.services.Services;

/**
 * JDK8 version of {@code LibGraal}.
 */
public class LibGraal {

    private static final Method attachCurrentThread = method(HotSpotJVMCIRuntime.class, "attachCurrentThread", sig(Boolean.TYPE), sig(Boolean.TYPE, long[].class));
    private static final Method detachCurrentThread = method(HotSpotJVMCIRuntime.class, "detachCurrentThread", sig(), sig(Boolean.TYPE));

    public static boolean isAvailable() {
        return inLibGraal() || available;
    }

    public static boolean isSupported() {
        return true;
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
        runtime().registerNativeMethods(clazz);
    }

    public static long translate(Object obj) {
        if (!isAvailable()) {
            throw new IllegalStateException();
        }
        if (!inLibGraal() && LibGraalScope.currentScope.get() == null) {
            throw new IllegalStateException("Not within a " + LibGraalScope.class.getName());
        }
        return runtime().translate(obj);
    }

    public static <T> T unhand(Class<T> type, long handle) {
        if (!isAvailable()) {
            throw new IllegalStateException();
        }
        if (!inLibGraal() && LibGraalScope.currentScope.get() == null) {
            throw new IllegalStateException("Not within a " + LibGraalScope.class.getName());
        }
        return runtime().unhand(type, handle);
    }

    private static long initializeLibgraal() {
        try {
            long[] javaVMInfo = runtime().registerNativeMethods(LibGraalScope.class);
            long isolate = javaVMInfo[1];
            return isolate;
        } catch (UnsupportedOperationException e) {
            return 0L;
        }
    }

    static final long initialIsolate = Services.IS_BUILDING_NATIVE_IMAGE ? 0L : initializeLibgraal();
    static final boolean available = initialIsolate != 0L;

    static boolean isCurrentThreadAttached() {
        return runtime().isCurrentThreadAttached();
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
        return log.getFailedSpeculationsAddress();
    }
}
