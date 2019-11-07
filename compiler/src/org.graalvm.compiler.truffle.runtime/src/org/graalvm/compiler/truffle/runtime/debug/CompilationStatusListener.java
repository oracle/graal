/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime.debug;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.instrumentation.CompilationState;
import com.oracle.truffle.api.instrumentation.CompilationStateBackdoor;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener.CompilationResultInfo;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener.GraphInfo;
import org.graalvm.compiler.truffle.runtime.*;

public final class CompilationStatusListener extends AbstractGraalTruffleRuntimeListener {

    private int finished;
    private int failures;
    private int dequeues;
    private int deoptimizations;

    private CompilationStatusListener(GraalTruffleRuntime runtime) {
        super(runtime);
        CompilationStateBackdoor.ACCESSOR = this::sampleCompilationState;
    }

    public static void install(GraalTruffleRuntime runtime) {
        runtime.addListener(new CompilationStatusListener(runtime));
    }

    @Override
    public synchronized void onCompilationDequeued(OptimizedCallTarget target, Object source, CharSequence reason) {
        dequeues++;
    }

    @Override
    public synchronized void onCompilationInvalidated(OptimizedCallTarget target, Object source, CharSequence reason) {
        // We count invalidations as deoptimizations
        deoptimizations++;
    }

    @Override
    public synchronized void onCompilationSuccess(OptimizedCallTarget target, TruffleInlining inliningDecision, GraphInfo graph, CompilationResultInfo result) {
        finished++;
    }

    @Override
    public synchronized void onCompilationFailed(OptimizedCallTarget target, String reason, boolean bailout, boolean permanentBailout) {
        failures++;
    }

    @Override
    public synchronized void onCompilationDeoptimized(OptimizedCallTarget target, Frame frame) {
        deoptimizations++;
    }

    public synchronized CompilationState sampleCompilationState() {
        return new CompilationStateImpl(
                runtime.getCompilationQueueSize(),
                runtime.getCompilationsRunning(),
                finished,
                failures,
                dequeues,
                deoptimizations);
    }

}
