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

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotSpeculationLog;

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

    private static InternalError shouldNotReachHere() {
        throw new InternalError("JDK specific overlay missing");
    }

    /**
     * Determines if libgraal is available for use.
     */
    public static boolean isAvailable() {
        throw shouldNotReachHere();
    }

    /**
     * Determines if the current runtime supports building a libgraal image.
     */
    public static boolean isSupported() {
        throw shouldNotReachHere();
    }

    /**
     * Determines if the current runtime is libgraal.
     */
    public static boolean inLibGraal() {
        throw shouldNotReachHere();
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
    @SuppressWarnings("unused")
    public static void registerNativeMethods(Class<?> clazz) {
        throw shouldNotReachHere();
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
    @SuppressWarnings("unused")
    public static long translate(Object obj) {
        throw shouldNotReachHere();
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
    @SuppressWarnings("unused")
    public static <T> T unhand(Class<T> type, long handle) {
        throw shouldNotReachHere();
    }

    /**
     * Determines if the current thread is {@linkplain #attachCurrentThread attached} to the peer
     * runtime.
     */
    @SuppressWarnings("unused")
    static boolean isCurrentThreadAttached(HotSpotJVMCIRuntime runtime) {
        throw shouldNotReachHere();
    }

    /**
     * Ensures the current thread is attached to the peer runtime.
     *
     * @param isDaemon if the thread is not yet attached, should it be attached as a daemon
     * @param isolate if non-null, the isolate for the current thread is returned in element 0
     * @return {@code true} if this call attached the current thread, {@code false} if the current
     *         thread was already attached
     */
    @SuppressWarnings("unused")
    public static boolean attachCurrentThread(boolean isDaemon, long[] isolate) {
        throw shouldNotReachHere();
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
    @SuppressWarnings("unused")
    public static boolean detachCurrentThread(boolean release) {
        throw shouldNotReachHere();
    }

    /**
     * Invokes the method {@code getFailedSpeculationsAddress} on a {@link HotSpotSpeculationLog},
     * if the method exists.
     *
     * @return the address of the pointer to the native failed speculations list.
     * @exception UnsupportedOperationException if unsupported
     */
    @SuppressWarnings("unused")
    public static long getFailedSpeculationsAddress(HotSpotSpeculationLog log) {
        throw shouldNotReachHere();
    }
}
