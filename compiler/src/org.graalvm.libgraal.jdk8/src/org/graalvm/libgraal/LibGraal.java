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

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.services.Services;

/**
 * JDK8 version of {@code LibGraal}.
 */
public class LibGraal {

    public static boolean isAvailable() {
        return isCurrentRuntime() || libgraalIsolate != 0L;
    }

    public static boolean isCurrentRuntime() {
        return Services.IS_IN_NATIVE_IMAGE;
    }

    public static long getIsolate() {
        if (isCurrentRuntime() || !isAvailable()) {
            throw new IllegalStateException();
        }
        return libgraalIsolate;
    }

    public static long getIsolateThread() {
        if (isCurrentRuntime()) {
            throw new IllegalStateException();
        }
        return CURRENT_ISOLATE_THREAD.get();
    }

    public static long[] registerNativeMethods(HotSpotJVMCIRuntime runtime, Class<?> clazz) {
        if (clazz.isPrimitive()) {
            throw new IllegalArgumentException();
        }
        if (isCurrentRuntime() || !isAvailable()) {
            throw new IllegalStateException();
        }
        return runtime.registerNativeMethods(clazz);
    }

    public static long translate(HotSpotJVMCIRuntime runtime, Object obj) {
        if (!isAvailable()) {
            throw new IllegalStateException();
        }
        return runtime.translate(obj);
    }

    public static <T> T unhand(HotSpotJVMCIRuntime runtime, Class<T> type, long handle) {
        if (!isAvailable()) {
            throw new IllegalStateException();
        }
        return runtime.unhand(type, handle);
    }

    private static final ThreadLocal<Long> CURRENT_ISOLATE_THREAD = new ThreadLocal<Long>() {
        @Override
        protected Long initialValue() {
            return attachThread(libgraalIsolate);
        }
    };

    private static final long libgraalIsolate = Services.IS_BUILDING_NATIVE_IMAGE ? 0L : initializeLibgraal();

    private static long initializeLibgraal() {
        try {
            HotSpotJVMCIRuntime runtime = HotSpotJVMCIRuntime.runtime();
            long[] nativeInterface = runtime.registerNativeMethods(LibGraal.class);
            return nativeInterface[1];
        } catch (UnsatisfiedLinkError e) {
            // Remove once Graal depends on a jvmci release including GR-14941
            // since we can then rely solely on UnsupportedOperationException
            // to indicate whether libgraal is available. All other exceptions
            // raised here should then cause fail-fast behavior.
            return 0L;
        } catch (UnsupportedOperationException e) {
            return 0L;
        }
    }

    /**
     * Attaches the current thread to a thread in {@code isolate}.
     *
     * @param isolate
     */
    private static native long attachThread(long isolate);
}
