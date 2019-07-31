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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.services.Services;

/**
 * JDK11 version of {@code LibGraal}.
 */
public class LibGraal {

    static final MethodHandle runtimeUnhandMethod;
    static final MethodHandle runtimeTranslateMethod;
    static final MethodHandle runtimeRegisterNativeMethods;
    static final MethodHandle runtimeIsCurrentThreadAttached;
    static final MethodHandle runtimeAttachCurrentThread;
    static final MethodHandle runtimeDetachCurrentThread;

    static {
        MethodHandle unhand = null;
        MethodHandle translate = null;
        MethodHandle registerNativeMethods = null;
        MethodHandle isCurrentThreadAttached = null;
        MethodHandle attachCurrentThread = null;
        MethodHandle detachCurrentThread = null;
        boolean firstFound = false;
        try {
            Class<?> runtimeClass = HotSpotJVMCIRuntime.class;
            unhand = MethodHandles.lookup().unreflect(runtimeClass.getDeclaredMethod("unhand", Class.class, Long.TYPE));
            firstFound = true;
            translate = MethodHandles.lookup().unreflect(runtimeClass.getDeclaredMethod("translate", Object.class));
            registerNativeMethods = MethodHandles.lookup().unreflect(runtimeClass.getDeclaredMethod("registerNativeMethods", Class.class));
            isCurrentThreadAttached = MethodHandles.lookup().unreflect(runtimeClass.getDeclaredMethod("isCurrentThreadAttached"));
            attachCurrentThread = MethodHandles.lookup().unreflect(runtimeClass.getDeclaredMethod("attachCurrentThread", Boolean.TYPE));
            detachCurrentThread = MethodHandles.lookup().unreflect(runtimeClass.getDeclaredMethod("detachCurrentThread"));
        } catch (Exception e) {
            // If the very first method is unavailable assume nothing is available. Otherwise only
            // some are missing so complain about it.
            if (firstFound) {
                throw new InternalError("some methods are unavailable", e);
            }
        }

        runtimeUnhandMethod = unhand;
        runtimeTranslateMethod = translate;
        runtimeRegisterNativeMethods = registerNativeMethods;
        runtimeIsCurrentThreadAttached = isCurrentThreadAttached;
        runtimeAttachCurrentThread = attachCurrentThread;
        runtimeDetachCurrentThread = detachCurrentThread;
    }

    public static boolean isAvailable() {
        return inLibGraal() || isolate != 0L;
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
            return (long) runtimeTranslateMethod.invoke(runtime, obj);
        } catch (Throwable throwable) {
            throw new InternalError(throwable);
        }
    }

    public static <T> T unhand(HotSpotJVMCIRuntime runtime, Class<T> type, long handle) {
        if (!isAvailable()) {
            throw new IllegalStateException();
        }
        if (!inLibGraal() && LibGraalScope.currentScope.get() == null) {
            throw new IllegalStateException("Not within a " + LibGraalScope.class.getName());
        }
        try {
            return (T) runtimeUnhandMethod.invoke(runtime, type, handle);
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
        } catch (UnsupportedOperationException e) {
            return 0L;
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

    static boolean attachCurrentThread(HotSpotJVMCIRuntime runtime) {
        try {
            return (boolean) runtimeAttachCurrentThread.invoke(runtime, false);
        } catch (Throwable throwable) {
            throw new InternalError(throwable);
        }
    }

    static void detachCurrentThread(HotSpotJVMCIRuntime runtime) {
        try {
            runtimeDetachCurrentThread.invoke(runtime);
        } catch (Throwable throwable) {
            throw new InternalError(throwable);
        }
    }

    static native long getCurrentIsolateThread(long iso);
}
