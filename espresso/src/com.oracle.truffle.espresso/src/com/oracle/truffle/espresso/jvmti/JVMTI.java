/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.truffle.espresso.jvmti;

import java.util.ArrayList;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.espresso.ffi.NativeSignature;
import com.oracle.truffle.espresso.ffi.NativeType;
import com.oracle.truffle.espresso.ffi.Pointer;
import com.oracle.truffle.espresso.runtime.EspressoContext;

public final class JVMTI {
    private final EspressoContext context;
    private final @Pointer TruffleObject initializeJvmtiContext;
    private final @Pointer TruffleObject disposeJvmtiContext;

    private final ArrayList<JVMTIEnv> activeEnvironments = new ArrayList<>();

    private JvmtiPhase phase;

    public JVMTI(EspressoContext context, TruffleObject mokapotLibrary) {
        this.context = context;

        this.initializeJvmtiContext = context.getNativeAccess().lookupAndBindSymbol(mokapotLibrary,
                        "initializeJvmtiContext",
                        NativeSignature.create(NativeType.POINTER, NativeType.POINTER, NativeType.INT));
        this.disposeJvmtiContext = context.getNativeAccess().lookupAndBindSymbol(mokapotLibrary,
                        "disposeJvmtiContext",
                        NativeSignature.create(NativeType.VOID, NativeType.POINTER, NativeType.INT, NativeType.POINTER));
    }

    public static boolean isJvmtiVersion(int version) {
        return JvmtiVersion.isJvmtiVersion(version);
    }

    private static boolean isSupportedJvmtiVersion(int version) {
        return JvmtiVersion.isSupportedJvmtiVersion(version);
    }

    @TruffleBoundary
    public synchronized TruffleObject create(int version) {
        if (!isSupportedJvmtiVersion(version)) {
            return null;
        }
        JVMTIEnv jvmtiEnv = new JVMTIEnv(context, initializeJvmtiContext, version);
        activeEnvironments.add(jvmtiEnv);
        return jvmtiEnv.getEnv();
    }

    public synchronized void dispose() {
        for (JVMTIEnv jvmtiEnv : activeEnvironments) {
            jvmtiEnv.dispose(disposeJvmtiContext);
        }
        activeEnvironments.clear();
    }

    @TruffleBoundary
    synchronized void dispose(JVMTIEnv env) {
        CompilerAsserts.neverPartOfCompilation();
        if (activeEnvironments.contains(env)) {
            env.dispose(disposeJvmtiContext);
            activeEnvironments.remove(env);
        }
    }

    public synchronized int getPhase() {
        return phase.value();
    }

    public synchronized void enterPhase(JvmtiPhase jvmtiPhase) {
        this.phase = jvmtiPhase;
    }

    public synchronized void postVmStart() {
        enterPhase(JvmtiPhase.START);
    }

    public synchronized void postVmInit() {
        enterPhase(JvmtiPhase.LIVE);
    }

    public synchronized void postVmDeath() {
        enterPhase(JvmtiPhase.DEAD);
    }

}
