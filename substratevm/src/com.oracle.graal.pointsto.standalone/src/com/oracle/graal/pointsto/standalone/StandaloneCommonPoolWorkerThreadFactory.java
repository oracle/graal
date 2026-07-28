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

package com.oracle.graal.pointsto.standalone;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

/**
 * Standalone analysis relies on worker-local guest state that is initialized lazily the first time
 * a worker calls into Espresso-backed VMAccess. The default common-pool worker implementation uses
 * innocuous workers that may clear ordinary {@link ThreadLocal} state between top-level tasks,
 * which can drop that guest state before the next analysis task re-enters Espresso. See GR-75091
 * for the standalone failure mode this mitigates.
 *
 * This factory creates normal {@link ForkJoinWorkerThread} instances so top-level standalone
 * analysis tasks can reuse the same worker-local state across common-pool submissions.
 *
 * Because it is installed through the process-wide
 * {@code java.util.concurrent.ForkJoinPool.common.threadFactory} property, this factory can also
 * affect unrelated common-pool users in the same JVM. That broader scope is intentional for the
 * current standalone embedding setup.
 */
public final class StandaloneCommonPoolWorkerThreadFactory implements ForkJoinPool.ForkJoinWorkerThreadFactory {

    @Override
    public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
        return new StandaloneCommonPoolWorkerThread(pool);
    }

    /**
     * Uses the preserving constructor so the shared common pool no longer wipes {@link ThreadLocal}
     * state between top-level analysis tasks.
     */
    static final class StandaloneCommonPoolWorkerThread extends ForkJoinWorkerThread {
        StandaloneCommonPoolWorkerThread(ForkJoinPool pool) {
            super(pool);
        }
    }
}
