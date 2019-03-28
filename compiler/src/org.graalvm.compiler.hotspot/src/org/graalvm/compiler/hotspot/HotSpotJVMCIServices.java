/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;

/**
 * Interface to functionality that abstracts over different HotSpot JVMCI versions.
 */
public class HotSpotJVMCIServices {

    /**
     * The signature of {@code HotSpotJVMCIRuntime.registerNativeMethods} changed to support
     * libgraal. JDKs that don't have full JVMCI support for libgraal will have the old signature.
     *
     * @param runtime
     * @param clazz
     */
    public static long[] registerNativeMethods(HotSpotJVMCIRuntime runtime, Class<?> clazz) {
        throw new NoSuchMethodError("long[] " + HotSpotJVMCIRuntime.class.getName() + ".registerNativeMethods(Class)");
    }

    /**
     * {@code HotSpotJVMCIRuntime.translate} was added in JDK 8 (jvmci-0.50) and JDK 13.
     *
     * @param runtime
     * @param obj
     */
    public static long translate(HotSpotJVMCIRuntime runtime, Object obj) {
        throw new NoSuchMethodError("long " + HotSpotJVMCIRuntime.class.getName() + ".translate(Object)");
    }

    /**
     * {@code HotSpotJVMCIRuntime.unhand} was added in JDK 8 (jvmci-0.50) and JDK 13.
     *
     * @param runtime
     * @param type
     * @param handle
     */
    public static <T> T unhand(HotSpotJVMCIRuntime runtime, Class<T> type, long handle) {
        throw new NoSuchMethodError("<T> T " + HotSpotJVMCIRuntime.class.getName() + ".unhand(Class<T>, long)");
    }
}
