/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.webimage.substitute.system;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.webimage.functionintrinsics.JSFunctionIntrinsics;
import jdk.internal.misc.Signal;

@TargetClass(className = "jdk.internal.misc.VM")
@SuppressWarnings("unused")
final class Target_jdk_internal_misc_VM_Web {
    @Substitute
    public static String[] getRuntimeArguments() {
        throw new UnsupportedOperationException("VM.getRuntimeArguments");
    }

    @Substitute
    public static long getNanoTimeAdjustment(long offsetInSeconds) {
        // This method is supposed to receive some number of seconds X that represents a certain
        // time index, and it is supposed to return the difference between the current time and X,
        // measured in nanoseconds.
        final long maxDiffSecs = 0x0100000000L;
        final long minDiffSecs = -maxDiffSecs;

        long unixEpochMillis = JSFunctionIntrinsics.currentTimeMillis();
        long seconds = unixEpochMillis / 1000;
        long nanos = (unixEpochMillis % 1000) * 1_000_000;

        long diff = seconds - offsetInSeconds;
        if (diff >= maxDiffSecs || diff <= minDiffSecs) {
            return -1;
        }
        return diff * 1_000_000_000 + nanos;
    }
}

@TargetClass(className = "jdk.internal.misc.Signal")
final class Target_jdk_internal_misc_Signal_Web {

    /**
     * Uses POSIX signal numbers. May be incomplete, extend as necessary.
     * <p>
     * Signals are not supported in Web Image, but instances of {@code Signal} may still exist, so
     * this method must return something.
     * <p>
     * Signal numbers taken from {@code man 7 signal} for the intel architecture.
     */
    @Substitute
    private static int findSignal0(String sigName) {
        return switch (sigName) {
            case "HUP" -> 1;
            case "INT" -> 2;
            case "QUIT" -> 3;
            case "ILL" -> 4;
            case "TRAP" -> 5;
            case "ABRT" -> 6;
            case "BUS" -> 7;
            case "FPE" -> 8;
            case "KILL" -> 9;
            case "USR1" -> 10;
            case "SEGV" -> 11;
            case "USR2" -> 12;
            case "PIPE" -> 13;
            case "ALRM" -> 14;
            case "TERM" -> 15;
            case "STKFLT" -> 16;
            case "CHLD" -> 17;
            case "CONT" -> 18;
            case "STOP" -> 19;
            case "TSTP" -> 20;
            case "TTIN" -> 21;
            case "TTOU" -> 22;
            case "URG" -> 23;
            case "XCPU" -> 24;
            case "XFSZ" -> 25;
            case "VTALRM" -> 26;
            case "PROF" -> 27;
            case "WINCH" -> 28;
            case "IO" -> 29;
            case "PWR" -> 30;
            case "SYS" -> 31;
            default -> -1;
        };
    }

    @Substitute
    @SuppressWarnings("unused")
    public static Signal.Handler handle(Signal sig, Signal.Handler handler) throws IllegalArgumentException {
        throw new IllegalArgumentException("cannot register signal handles in webimage.");
    }
}

/** Dummy class to have a class with the file's name. */
public final class WebImageSunMiscSubstitutions {
}
