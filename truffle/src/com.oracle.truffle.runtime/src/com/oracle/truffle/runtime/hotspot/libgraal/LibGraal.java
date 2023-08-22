/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.runtime.hotspot.libgraal;

import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.truffle.runtime.ModulesSupport;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaType;
import jdk.vm.ci.hotspot.HotSpotSpeculationLog;
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
 *
 * The typical usage of this class is to {@linkplain #registerNativeMethods link} and call
 * {@link CEntryPoint}s in libgraal. Each call to a {@link CEntryPoint} requires an
 * {@link IsolateThread} argument which can be {@linkplain LibGraalScope#getIsolateThread obtained}
 * from a {@link LibGraalScope}.
 */
public class LibGraal {

    // NOTE: The use of reflection to access JVMCI API is to support
    // compiling on JDKs with varying versions of JVMCI.

    static {
        // Initialize JVMCI to ensure JVMCI opens its packages to Graal.
        ModulesSupport.exportJVMCI(LibGraal.class);
        Services.initializeJVMCI();
    }

    private static final Method unhand = methodOrNull(HotSpotJVMCIRuntime.class, "unhand", sig(Class.class, Long.TYPE));
    private static final Method translate = methodIf(unhand, HotSpotJVMCIRuntime.class, "translate", sig(Object.class));
    private static final Method registerNativeMethods = methodIf(unhand, HotSpotJVMCIRuntime.class, "registerNativeMethods", sig(Class.class));
    private static final Method isCurrentThreadAttached = methodIf(unhand, HotSpotJVMCIRuntime.class, "isCurrentThreadAttached");
    private static final Method attachCurrentThread = methodIf(unhand, HotSpotJVMCIRuntime.class, "attachCurrentThread", sig(Boolean.TYPE, long[].class), sig(Boolean.TYPE));
    private static final Method detachCurrentThread = methodIf(unhand, HotSpotJVMCIRuntime.class, "detachCurrentThread", sig(Boolean.TYPE), sig());
    private static final Method getFailedSpeculationsAddress = methodIf(unhand, HotSpotSpeculationLog.class, "getFailedSpeculationsAddress");

    private static final Method asResolvedJavaType = methodOrNull(HotSpotJVMCIRuntime.class, "asResolvedJavaType", sig(Long.TYPE));
    private static final Method getJObjectValue = methodIf(asResolvedJavaType, HotSpotJVMCIRuntime.class, "getJObjectValue", sig(HotSpotObjectConstant.class));

    /**
     * Determines if libgraal is available for use.
     */
    public static boolean isAvailable() {
        return inLibGraal() || available;
    }

    /**
     * Determines if the current runtime supports building a libgraal image.
     */
    public static boolean isSupported() {
        return getFailedSpeculationsAddress != null;
    }

    /**
     * Determines if the current runtime is libgraal.
     */
    public static boolean inLibGraal() {
        return Services.IS_IN_NATIVE_IMAGE;
    }

    /**
     * Links each native method in {@code clazz} to a {@link CEntryPoint} in libgraal.
     *
     * This cannot be called from {@linkplain #inLibGraal() within} libgraal.
     *
     * @throws NullPointerException if {@code clazz == null}
     * @throws UnsupportedOperationException if libgraal is not enabled (i.e.
     *             {@code -XX:-UseJVMCINativeLibrary})
     * @throws IllegalArgumentException if {@code clazz} is {@link Class#isPrimitive()}
     * @throws IllegalStateException if libgraal is {@linkplain #isAvailable() unavailable} or
     *             {@link #inLibGraal()} returns true
     * @throws UnsatisfiedLinkError if there's a problem linking a native method in {@code clazz}
     *             (no matching JNI symbol or the native method is already linked to a different
     *             address)
     */
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
        if (!isAvailable()) {
            throw new IllegalStateException();
        }
        try {
            return (long) translate.invoke(runtime(), obj);
        } catch (Throwable throwable) {
            throw new InternalError(throwable);
        }
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
        if (!isAvailable()) {
            throw new IllegalStateException();
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
                // libgraal available but not enabled
                return 0L;
            }
            throw new InternalError(e);
        } catch (Throwable throwable) {
            throw new InternalError(throwable);
        }
    }

    static final long initialIsolate = Services.IS_BUILDING_NATIVE_IMAGE ? 0L : initializeLibgraal();
    static final boolean available = initialIsolate != 0L;

    /**
     * Determines if the current thread is {@linkplain #attachCurrentThread attached} to the peer
     * runtime.
     */
    static boolean isCurrentThreadAttached() {
        try {
            return (boolean) isCurrentThreadAttached.invoke(runtime());
        } catch (Throwable throwable) {
            throw new InternalError(throwable);
        }
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
     * Invokes the method {@code getFailedSpeculationsAddress} on a {@link HotSpotSpeculationLog},
     * if the method exists.
     *
     * @return the address of the pointer to the native failed speculations list.
     * @exception UnsupportedOperationException if unsupported
     */
    public static long getFailedSpeculationsAddress(HotSpotSpeculationLog log) {
        if (getFailedSpeculationsAddress != null) {
            try {
                return (long) getFailedSpeculationsAddress.invoke(log);
            } catch (Throwable e) {
                throw new InternalError(e);
            }
        }
        throw new UnsupportedOperationException();
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
