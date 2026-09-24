/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test.jni.invocation;

/**
 * The Java side of {@code jnidestroyvm.c}: the {@code main} a launcher calls over JNI before it
 * calls {@code DestroyJavaVM}.
 * <p>
 * It leaves behind a daemon thread that swallows {@link InterruptedException}, as
 * {@link java.util.Timer}'s thread does. HotSpot's {@code DestroyJavaVM} does not wait for daemon
 * threads, so a launcher can exit; {@code DestroyJavaVM} must not block on this one.
 */
public class DaemonMain {

    public static void main(String[] args) {
        Thread daemon = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(100_000);
                } catch (InterruptedException e) {
                    /* Ignore the interrupt, and keep running. */
                }
            }
        }, "interrupt-swallowing-daemon");
        daemon.setDaemon(true);
        daemon.start();
        System.out.println("main returns");
    }
}
