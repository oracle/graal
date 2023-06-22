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
package com.oracle.svm.graal.hotspot.libgraal.truffle;

import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaType;

final class LibGraal {

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
    static long translate(Object obj) {
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
    static <T> T unhand(Class<T> type, long handle) {
        return HotSpotJVMCIRuntime.runtime().unhand(type, handle);
    }

    /**
     * @see HotSpotJVMCIRuntime#getJObjectValue(HotSpotObjectConstant)
     */
    static <T extends PointerBase> T getJObjectValue(HotSpotObjectConstant constant) {
        return WordFactory.pointer(HotSpotJVMCIRuntime.runtime().getJObjectValue(constant));
    }

    /**
     * @see HotSpotJVMCIRuntime#asResolvedJavaType(long)
     */
    static HotSpotResolvedJavaType asResolvedJavaType(PointerBase pointer) {
        return HotSpotJVMCIRuntime.runtime().asResolvedJavaType(pointer.rawValue());
    }

}
