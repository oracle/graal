/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.libs.libjava.impl;

import java.time.Instant;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.libs.InformationLeak;
import com.oracle.truffle.espresso.libs.LibsState;
import com.oracle.truffle.espresso.libs.libjava.LibJava;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.Substitution;

@EspressoSubstitutions(type = "Ljava/lang/ProcessHandleImpl;", group = LibJava.class)
public final class Target_java_lang_ProcessHandleImpl {
    @Substitution
    public static void initNative() {
        // nop
    }

    @Substitution
    public static long getCurrentPid0(@Inject EspressoContext ctx) {
        return ctx.getInformationLeak().getPid();
    }

    @Substitution
    @TruffleBoundary
    public static long isAlive0(long pid, @Inject InformationLeak iL, @Inject LibsState libsState) {
        libsState.checkCreateProcessAllowed();
        ProcessHandle.Info info = iL.getProcessHandleInfo(pid);
        if (info != null) {
            return info.startInstant().map(Instant::toEpochMilli).orElse(-1L);
        } else {
            return -1;
        }
    }
}
