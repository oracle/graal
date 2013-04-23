/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.intrinsics;

/**
 * Predefined Truffle intrinsics that allow direct influence of the generated machine code.
 */
public final class TruffleIntrinsics {

    /**
     * Specifies that the compiler should put a deoptimization point at this position that will
     * continue execution in the interpreter. Should be used to cut off cold paths that should not
     * be part of the compiled machine code.
     */
    public static void deoptimize() {
    }

    /**
     * Checks whether the Thread has been interrupted in the interpreter in order to avoid endless
     * loops. The compiled code may choose a more efficient implementation.
     */
    public static void checkThreadInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new RuntimeException("Timeout");
        }
    }

    public static void mustNotReachHere() {
    }

    public static void interpreterOnly(Runnable runnable) {
        runnable.run();
    }
}
