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

import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.IsolateContext;
import org.graalvm.nativeimage.c.function.CEntryPoint.IsolateThreadContext;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;

/**
 * Access to libgraal, a shared library containing an AOT compiled version of Graal produced by SVM.
 * The libgraal library is only available if:
 * <ul>
 * <li>the current runtime is libgraal, or</li>
 * <li>the HotSpot {@code UseJVMCINativeLibrary} flag is true and the current runtime includes the
 * relevant JVMCI API additions for accessing libgraal.</li>
 * </ul>
 *
 * The {@link #isAvailable()} method is provided to test these conditions. It must be used to guard
 * usage of all other methods in this class.
 */
public class LibGraal {

    private static InternalError shouldNotReachHere() {
        throw new InternalError("JDK specific overlay missing");
    }

    /**
     * Determines if libgraal is available.
     */
    public static boolean isAvailable() {
        throw shouldNotReachHere();
    }

    /**
     * Determines if the current runtime is libgraal.
     */
    public static boolean isCurrentRuntime() {
        throw shouldNotReachHere();
    }

    /**
     * Gets the libgraal isolate.
     *
     * This cannot be called from {@linkplain #isCurrentRuntime() within} libgraal.
     *
     * @returns a value that can be used for the {@link IsolateContext} argument of a {@code native}
     *          method {@link #registerNativeMethods linked} to a {@link CEntryPoint} function in
     *          libgraal
     * @throws IllegalStateException if libgraal is {@linkplain #isAvailable() unavailable} or
     *             {@link #isCurrentRuntime()} returns true
     */
    public static long getIsolate() {
        throw shouldNotReachHere();
    }

    /**
     * Gets a libgraal isolate thread associated with the current thread. This method attaches the
     * current thread to an isolate thread first if necessary.
     *
     * This cannot be called from {@linkplain #isCurrentRuntime() within} libgraal.
     *
     * @returns a value that can be used for the {@link IsolateThreadContext} argument of a
     *          {@code native} method {@link #registerNativeMethods linked} to a {@link CEntryPoint}
     *          function in libgraal
     * @throws IllegalStateException if libgraal is {@linkplain #isAvailable() unavailable} or
     *             {@link #isCurrentRuntime()} returns true
     */
    public static long getIsolateThread() {
        throw shouldNotReachHere();
    }

    /**
     * Links each native method in {@code clazz} to a {@link CEntryPoint} in libgraal.
     *
     * This cannot be called from {@linkplain #isCurrentRuntime() within} libgraal.
     *
     * @return an array of 4 longs where the first value is the {@code JavaVM*} value representing
     *         the libgraal Java VM, and the remaining values are the first 3 pointers in the
     *         Invocation API function table (i.e., {@code JNIInvokeInterface})
     *
     * @throws NullPointerException if {@code clazz == null}
     * @throws UnsupportedOperationException if libgraal is not enabled (i.e.
     *             {@code -XX:-UseJVMCINativeLibrary})
     * @throws IllegalArgumentException if{@code clazz} is {@link Class#isPrimitive()}
     * @throws IllegalStateException if libgraal is {@linkplain #isAvailable() unavailable} or
     *             {@link #isCurrentRuntime()} returns true
     * @throws UnsatisfiedLinkError if there's a problem linking a native method in {@code clazz}
     *             (no matching JNI symbol or the native method is already linked to a different
     *             address)
     */
    @SuppressWarnings("unused")
    public static long[] registerNativeMethods(HotSpotJVMCIRuntime runtime, Class<?> clazz) {
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
    public static long translate(HotSpotJVMCIRuntime runtime, Object obj) {
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
    public static <T> T unhand(HotSpotJVMCIRuntime runtime, Class<T> type, long handle) {
        throw shouldNotReachHere();
    }
}
