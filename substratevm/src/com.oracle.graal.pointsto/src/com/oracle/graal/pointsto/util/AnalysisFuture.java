/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.util;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import org.graalvm.compiler.debug.GraalError;

/** Extend FutureTask for custom error reporting. */
public class AnalysisFuture<V> extends FutureTask<V> {

    public AnalysisFuture(Callable<V> callable) {
        super(callable);
    }

    public AnalysisFuture(Runnable runnable, V result) {
        super(runnable, result);
    }

    @Override
    protected void setException(Throwable t) {
        /* First complete the task. */
        super.setException(t);
        /* Then report the error as a GraalError. */
        throw new GraalError(t);
    }

    /** Run the task, wait for it to complete if necessary, then retrieve its result. */
    public V ensureDone() {
        try {
            /*
             * Trigger the task execution and call get(), which waits for the computation to
             * complete, if necessary.
             *
             * A task is done even if it failed with an exception. The exception is only reported
             * when get() is invoked. We report any error eagerly as a GraalError as soon as it is
             * encountered.
             */
            run();
            return get();
        } catch (InterruptedException | ExecutionException e) {
            throw AnalysisError.shouldNotReachHere(e);
        }
    }

    public V guardedGet() {
        try {
            return get();
        } catch (InterruptedException | ExecutionException e) {
            throw AnalysisError.shouldNotReachHere(e);
        }
    }

}
