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

package com.oracle.svm.hosted;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ForkJoinPool;

/**
 * An uncaught exception handler for the {@linkplain ForkJoinPool#commonPool() common pool} as used
 * in the context of image building. This handler is responsible for immediate abort in the case of
 * uncaught exceptions in any thread managed by the common pool. The native-image driver installs it
 * by setting the {@code java.util.concurrent.ForkJoinPool.common.exceptionHandler} system property.
 * Failure to install this handler is detected to ensure we can rely on every image build being
 * performed with this handler enabled. See {@link NativeImageGeneratorRunner#main(String[])}.
 */
public class CommonPoolUncaughtExceptionHandler implements UncaughtExceptionHandler {

    @Override
    public synchronized void uncaughtException(Thread t, Throwable e) {
        System.err.print("Aborting image build. Uncaught Exception in ForkJoinPool#commonPool() thread " + t + ' ');
        e.printStackTrace(System.err);
        NativeImageGenerator.exitBuilderWithError();
    }
}
