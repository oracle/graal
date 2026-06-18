/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.guest.staging;

import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;

import com.oracle.svm.guest.staging.c.function.CEntryPointOptions;
import com.oracle.svm.guest.staging.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.guest.staging.c.function.CEntryPointOptions.NoPrologue;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.VMError;

/**
 * Temporary guest-owned Java main entry point placeholder for fully isolated Terminus builds.
 * <p>
 * GR-76695 uses this placeholder as a narrow fallback while only Java-main method state is installed
 * in guest staging. GR-72850 tracks moving the real {@code JavaMainWrapper} startup path, including
 * the required isolate, thread, argument, and shutdown setup, into guest-reachable code. Once that
 * startup path is available from the guest context, fully isolated builds can resolve the real Java
 * main wrapper entry point directly and this placeholder can be removed.
 */
public final class JavaMainWrapperStub {
    /**
     * This class only exposes static entry points.
     */
    private JavaMainWrapperStub() {
    }

    /**
     * Provides an intentionally inert guest-staging entry point with the Java main entry point
     * signature. Fully isolated builds resolve this guest-owned method during setup; normal builds
     * use {@code com.oracle.svm.core.JavaMainWrapper#run}.
     * <p>
     * This stub deliberately omits {@code LayeredCompilationBehavior}. That annotation lives in
     * {@code com.oracle.svm.sdk}, and this placeholder avoids adding that dependency to guest
     * staging.
     */
    @Uninterruptible(reason = "Thread state not set up yet.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class)
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class)
    public static int run(@SuppressWarnings("unused") int argc, @SuppressWarnings("unused") CCharPointerPointer argv) {
        throw VMError.shouldNotReachHere("Temporary Java main wrapper stub must not be executed.");
    }
}
