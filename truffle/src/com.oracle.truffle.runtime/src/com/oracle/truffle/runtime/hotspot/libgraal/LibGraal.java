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

import org.graalvm.nativeimage.ImageInfo;
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
 * GraalVM Native Image. The libgraal library is only available if the HotSpot
 * {@code UseJVMCINativeLibrary} flag is true and the current runtime includes the relevant JVMCI
 * API additions for accessing libgraal.
 *
 * The typical usage of this class is to {@linkplain #registerNativeMethods link} and call
 * {@link CEntryPoint}s in libgraal. Each call to a {@link CEntryPoint} requires an
 * {@link IsolateThread} argument which can be {@linkplain LibGraalScope#getIsolateThread obtained}
 * from a {@link LibGraalScope}.
 */
public class LibGraal {

    static {
        // Initialize JVMCI to ensure JVMCI opens its packages to Graal.
        ModulesSupport.exportJVMCI(LibGraal.class);
        Services.initializeJVMCI();
    }

    static final long INITIAL_ISOLATE = ImageInfo.inImageBuildtimeCode() ? 0L : initializeLibgraal();
    static final boolean AVAILABLE = INITIAL_ISOLATE != 0L;

    private static long initializeLibgraal() {
        try {
            long[] javaVMInfo = runtime().registerNativeMethods(LibGraalScope.class);
            long isolate = javaVMInfo[1];
            return isolate;
        } catch (UnsupportedOperationException e) {
            return 0L;
        }
    }

    /**
     * Determines if libgraal is available for use.
     */
    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /**
     * Links each native method in {@code clazz} to a {@link CEntryPoint} in libgraal.
     *
     *
     * @throws NullPointerException if {@code clazz == null}
     * @throws UnsupportedOperationException if libgraal is not enabled (i.e.
     *             {@code -XX:-UseJVMCINativeLibrary})
     * @throws IllegalArgumentException if {@code clazz} is {@link Class#isPrimitive()}
     * @throws IllegalStateException if libgraal is {@linkplain #isAvailable() unavailable}
     * @throws UnsatisfiedLinkError if there's a problem linking a native method in {@code clazz}
     *             (no matching JNI symbol or the native method is already linked to a different
     *             address)
     */
    public static void registerNativeMethods(Class<?> clazz) {
        if (clazz.isPrimitive()) {
            throw new IllegalArgumentException();
        }
        if (!isAvailable()) {
            throw new IllegalStateException();
        }
        runtime().registerNativeMethods(clazz);
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
        return runtime().translate(obj);
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
        return runtime().unhand(type, handle);
    }

    /**
     * Determines if the current thread is {@linkplain #attachCurrentThread attached} to the peer
     * runtime.
     */
    static boolean isCurrentThreadAttached() {
        return runtime().isCurrentThreadAttached();
    }

    /**
     * @see HotSpotJVMCIRuntime#getJObjectValue(HotSpotObjectConstant)
     */
    public static <T extends PointerBase> T getJObjectValue(HotSpotObjectConstant constant) {
        return WordFactory.pointer(runtime().getJObjectValue(constant));
    }

    /**
     * @see HotSpotJVMCIRuntime#asResolvedJavaType(long)
     */
    public static HotSpotResolvedJavaType asResolvedJavaType(PointerBase pointer) {
        return runtime().asResolvedJavaType(pointer.rawValue());
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
        long[] javaVMInfo = isolate != null ? new long[4] : null;
        boolean res = runtime().attachCurrentThread(isDaemon, javaVMInfo);
        if (isolate != null) {
            isolate[0] = javaVMInfo[1];
        }
        return res;
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
        return runtime().detachCurrentThread(release);
    }

    /**
     * Invokes the method {@code getFailedSpeculationsAddress} on a {@link HotSpotSpeculationLog},
     * if the method exists.
     *
     * @return the address of the pointer to the native failed speculations list.
     * @exception UnsupportedOperationException if unsupported
     */
    public static long getFailedSpeculationsAddress(HotSpotSpeculationLog log) {
        return log.getFailedSpeculationsAddress();
    }

}
