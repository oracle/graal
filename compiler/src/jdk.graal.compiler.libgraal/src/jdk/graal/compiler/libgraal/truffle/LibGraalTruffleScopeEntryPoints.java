/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.libgraal.truffle;

import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.word.PointerBase;

import jdk.graal.compiler.libgraal.LibGraalFeature;
import jdk.graal.compiler.serviceprovider.IsolateUtil;

/**
 * Truffle specific {@link CEntryPoint} implementations.
 */
final class LibGraalTruffleScopeEntryPoints {

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_LibGraalScope_getIsolateThreadIn", builtin = CEntryPoint.Builtin.GET_CURRENT_THREAD, include = LibGraalFeature.IsEnabled.class)
    private static native IsolateThread getIsolateThreadIn(PointerBase env, PointerBase hsClazz, @CEntryPoint.IsolateContext Isolate isolate);

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_LibGraalScope_attachThreadTo", builtin = CEntryPoint.Builtin.ATTACH_THREAD, include = LibGraalFeature.IsEnabled.class)
    static native long attachThreadTo(PointerBase env, PointerBase hsClazz, @CEntryPoint.IsolateContext long isolate);

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_LibGraalScope_detachThreadFrom", builtin = CEntryPoint.Builtin.DETACH_THREAD, include = LibGraalFeature.IsEnabled.class)
    static native void detachThreadFrom(PointerBase env, PointerBase hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadAddress);

    @CEntryPoint(name = "Java_com_oracle_truffle_runtime_hotspot_libgraal_LibGraalScope_getIsolateId", include = LibGraalFeature.IsEnabled.class)
    @SuppressWarnings("unused")
    public static long getIsolateId(PointerBase env, PointerBase jclass, @CEntryPoint.IsolateThreadContext long isolateThreadAddress) {
        return IsolateUtil.getIsolateID();
    }
}
