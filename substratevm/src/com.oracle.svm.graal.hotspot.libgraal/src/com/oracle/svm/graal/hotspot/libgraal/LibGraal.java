/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hotspot.libgraal;

import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaType;
import jdk.vm.ci.services.Services;

/**
 * Access to libgraal, a shared library containing an AOT compiled version of Graal produced by
 * GraalVM Native Image. The libgraal library is only available if:
 * <ul>
 * <li>the {@linkplain #inLibGraal() current runtime} is libgraal, or</li>
 * <li>the HotSpot {@code UseJVMCINativeLibrary} flag is true and the current runtime includes the
 * relevant JVMCI API additions for accessing libgraal.</li>
 * </ul>
 *
 * The {@link #isAvailable()} method is provided to test these conditions. It must be used to guard
 * usage of all other methods in this class. In addition, only the following methods can be called
 * from within libgraal:
 * <ul>
 * <li>{@link #isAvailable()}</li>
 * <li>{@link #inLibGraal()}</li>
 * <li>{@link #translate(Object)}</li>
 * <li>{@link #unhand(Class, long)}</li>
 * </ul>
 */
public final class LibGraal {

    static final long initialIsolate = Services.IS_BUILDING_NATIVE_IMAGE ? 0L : initializeLibgraal();
    static final boolean available = initialIsolate != 0L;

    // NOTE: The use of reflection to access JVMCI API is to support
    // compiling on JDKs with varying versions of JVMCI.

    static {
        // Initialize JVMCI to ensure JVMCI opens its packages to Graal.
        Services.initializeJVMCI();
    }

    private static final Method unhand = methodOrNull(HotSpotJVMCIRuntime.class, "unhand", sig(Class.class, Long.TYPE));
    private static final Method attachCurrentThread = methodIf(unhand, HotSpotJVMCIRuntime.class, "attachCurrentThread", sig(Boolean.TYPE, long[].class), sig(Boolean.TYPE));
    private static final Method detachCurrentThread = methodIf(unhand, HotSpotJVMCIRuntime.class, "detachCurrentThread", sig(Boolean.TYPE), sig());
    private static final Method asResolvedJavaType = methodOrNull(HotSpotJVMCIRuntime.class, "asResolvedJavaType", sig(Long.TYPE));
    private static final Method getJObjectValue = methodIf(asResolvedJavaType, HotSpotJVMCIRuntime.class, "getJObjectValue", sig(HotSpotObjectConstant.class));

    /**
     * Determines if libgraal is available for use.
     */
    public static boolean isAvailable() {
        return inLibGraal() || available;
    }

    /**
     * Determines if the current runtime is libgraal.
     */
    public static boolean inLibGraal() {
        return Services.IS_IN_NATIVE_IMAGE;
    }

    /**
     * Creates or retrieves an object in the peer runtime that mirrors {@code obj}.
     *
     * This mechanism can be used to pass and return values between the HotSpot and libgraal
     * runtimes. In the receiving runtime, the value can be converted back to an object with
     * {@link #unhand}.
     *
     * @param obj an object for which an equivalent instance in the peer runtime is requested
     * @return a JNI global reference to the mirror of {@code obj} in the peer runtime
     * @throws IllegalArgumentException if {@code obj} is not of a translatable type
     */
    public static long translate(Object obj) {
        return HotSpotJVMCIRuntime.runtime().translate(obj);
    }

    /**
     * Dereferences and returns the object referred to by the JNI global reference {@code handle}.
     * The global reference is deleted prior to returning. Any further use of {@code handle} is
     * invalid.
     *
     * @param handle a JNI global reference to an object in the current runtime
     * @return the object referred to by {@code handle}
     * @throws ClassCastException if the returned object cannot be cast to {@code type}
     */
    @SuppressWarnings("unchecked")
    public static <T> T unhand(Class<T> type, long handle) {
        return HotSpotJVMCIRuntime.runtime().unhand(type, handle);
    }

    /**
     * @see HotSpotJVMCIRuntime#getJObjectValue(HotSpotObjectConstant)
     */
    public static <T extends PointerBase> T getJObjectValue(HotSpotObjectConstant constant) {
        if (getJObjectValue == null) {
            return WordFactory.nullPointer();
        }
        try {
            return WordFactory.pointer((long) getJObjectValue.invoke(runtime(), constant));
        } catch (Throwable throwable) {
            throw new InternalError(throwable);
        }
    }

    /**
     * @see HotSpotJVMCIRuntime#asResolvedJavaType(long)
     */
    public static HotSpotResolvedJavaType asResolvedJavaType(PointerBase pointer) {
        if (asResolvedJavaType == null) {
            return null;
        }
        try {
            return (HotSpotResolvedJavaType) asResolvedJavaType.invoke(runtime(), pointer.rawValue());
        } catch (Throwable throwable) {
            throw new InternalError(throwable);
        }
    }

    private static long initializeLibgraal() {
        return HotSpotJVMCIRuntime.runtime().registerNativeMethods(LibGraal.class)[1];
    }

    /**
     * Ensures the current thread is attached to the peer runtime.
     *
     * @param isDaemon if the thread is not yet attached, should it be attached as a daemon
     * @param isolate if non-null, the isolate for the current thread is returned in element 0
     * @return {@code true} if this call attached the current thread, {@code false} if the current
     *         thread was already attached
     */
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

    /**
     * Detaches the current thread from the peer runtime.
     *
     * @param release if {@code true} and the VM supports releasing the {@code JavaVM} associated
     *            with libgraal runtimes and this is the last thread attached to a libgraal runtime,
     *            then this call destroys the associated {@code JavaVM} instance, releasing its
     *            resources
     * @return {@code true} if the {@code JavaVM} associated with the libgraal runtime was destroyed
     *         as a result of this call
     */
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

    /**
     * Convenience function for wrapping varargs into an array for use in calls to
     * {@link #method(Class, String, Class[][])}.
     */
    private static Class<?>[] sig(Class<?>... types) {
        return types;
    }

    /**
     * Gets the method in {@code declaringClass} with the unique name {@code name}.
     *
     * @param sigs the signatures the method may have
     */
    private static Method method(Class<?> declaringClass, String name, Class<?>[]... sigs) {
        if (sigs.length == 1 || sigs.length == 0) {
            try {
                Class<?>[] sig = sigs.length == 1 ? sigs[0] : new Class<?>[0];
                return declaringClass.getDeclaredMethod(name, sig);
            } catch (NoSuchMethodException | SecurityException e) {
                throw (NoSuchMethodError) new NoSuchMethodError(name).initCause(e);
            }
        }
        Method match = null;
        for (Method m : declaringClass.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                if (match != null) {
                    throw new InternalError(String.format("Expected single method named %s, found %s and %s",
                                    name, match, m));
                }
                match = m;
            }
        }
        if (match == null) {
            throw new NoSuchMethodError("Cannot find method " + name + " in " + declaringClass.getName());
        }
        Class<?>[] parameterTypes = match.getParameterTypes();
        for (Class<?>[] sig : sigs) {
            if (Arrays.equals(parameterTypes, sig)) {
                return match;
            }
        }
        throw new NoSuchMethodError(String.format("Unexpected signature for %s: %s", name, Arrays.toString(parameterTypes)));
    }

    /**
     * Gets the method in {@code declaringClass} with the unique name {@code name} or {@code null}
     * if not found.
     *
     * @param sigs the signatures the method may have
     */
    private static Method methodOrNull(Class<?> declaringClass, String name, Class<?>[]... sigs) {
        try {
            return method(declaringClass, name, sigs);
        } catch (NoSuchMethodError e) {
            return null;
        }
    }

    /**
     * Gets the method in {@code declaringClass} with the unique name {@code name} or {@code null}
     * if {@code guard == null}.
     *
     * @param sigs the signatures the method may have
     */
    private static Method methodIf(Object guard, Class<?> declaringClass, String name, Class<?>[]... sigs) {
        if (guard == null) {
            return null;
        }
        return method(declaringClass, name, sigs);
    }

}
