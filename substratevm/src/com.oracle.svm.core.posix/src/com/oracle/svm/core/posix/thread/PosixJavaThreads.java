/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.thread;

import com.oracle.svm.core.posix.headers.Pthread;

/**
 * GR-34749: for legacy code, remove as soon as no longer needed.
 *
 * Quarkus has an @Alias on the methods below in its DiagnosticPrinter (Target_PosixJavaThreads).
 * This was already removed in Quarkus master, so we can remove it once the microservice benchmarks
 * are updated to a Quarkus version that doesn't access those methods anymore.
 *
 * @see PosixPlatformThreads
 */
@Deprecated(forRemoval = true)
public final class PosixJavaThreads {
    private PosixJavaThreads() {
    }

    @Deprecated(forRemoval = true)
    static Pthread.pthread_t getPthreadIdentifier(Thread thread) {
        return PosixPlatformThreads.getPthreadIdentifier(thread);
    }

    @Deprecated(forRemoval = true)
    static boolean hasThreadIdentifier(Thread thread) {
        return PosixPlatformThreads.hasThreadIdentifier(thread);
    }
}
