/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

import org.graalvm.compiler.core.CompilerThreadFactory;
import org.graalvm.compiler.nodes.Cancellable;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.truffle.common.TruffleCompilerOptions;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleCompilerThreads;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.getOptions;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.overrideOptions;

public class BackgroundCompileQueue {
    private final ExecutorService compilationExecutorService;

    public static class Request implements Runnable {
        private final GraalTruffleRuntime runtime;
        private final OptionValues optionOverrides;
        private final WeakReference<OptimizedCallTarget> weakCallTarget;
        private final Cancellable cancellable;

        public Request(GraalTruffleRuntime runtime, OptionValues optionOverrides, OptimizedCallTarget callTarget, Cancellable cancellable) {
            this.runtime = runtime;
            this.optionOverrides = optionOverrides;
            this.weakCallTarget = new WeakReference<>(callTarget);
            this.cancellable = cancellable;
        }

        @SuppressWarnings("try")
        @Override
        public void run() {
            OptimizedCallTarget callTarget = weakCallTarget.get();
            try (TruffleCompilerOptions.TruffleOptionsOverrideScope scope = optionOverrides != null ? overrideOptions(optionOverrides.getMap()) : null) {
                // if (!TruffleLowGradeCompilation.getValue(getOptions())) {
                //     TTY.println("submitted htc: " + optimizedCallTarget + ", " + callTarget);
                // }
                if (callTarget != null) {
                    // if (!TruffleLowGradeCompilation.getValue(getOptions())) {
                    //     TTY.println("ehm.. if: " + optimizedCallTarget + ", " + callTarget + ", " + optionOverrides);
                    // }
                    OptionValues options = getOptions();
                    runtime.doCompile(options, callTarget, cancellable);
                }
            } finally {
                if (callTarget != null) {
                    callTarget.resetCompilationTask();
                }
            }
        }
    }

    public BackgroundCompileQueue() {
        CompilerThreadFactory factory = new CompilerThreadFactory("TruffleCompilerThread");

        int selectedProcessors = TruffleCompilerOptions.getValue(TruffleCompilerThreads);
        if (selectedProcessors == 0) {
            // No manual selection made, check how many processors are available.
            int availableProcessors = Runtime.getRuntime().availableProcessors();
            if (availableProcessors >= 4) {
                selectedProcessors = 2;
            }
        }
        selectedProcessors = Math.max(1, selectedProcessors);
        compilationExecutorService = Executors.newFixedThreadPool(selectedProcessors, factory);
    }

    public CancellableCompileTask submitCompilationRequest(GraalTruffleRuntime runtime, OptimizedCallTarget optimizedCallTarget) {
        // TODO
        final OptionValues optionOverrides = TruffleCompilerOptions.getCurrentOptionOverrides();
        CancellableCompileTask cancellable = new CancellableCompileTask();
        // try (TruffleOptionsOverrideScope scope = optionOverrides != null ? overrideOptions(optionOverrides.getMap()) : null) {
        //     if (!TruffleLowGradeCompilation.getValue(getOptions())) {
        //         TTY.println("submitting htc...: " + optimizedCallTarget);
        //     }
        // }
        cancellable.setFuture(compilationExecutorService.submit(new Request(runtime, optionOverrides, optimizedCallTarget, cancellable)));
        // task and future must never diverge from each other
        assert cancellable.future != null;
        return cancellable;
    }

    public int getQueueSize() {
        // TODO
        if (compilationExecutorService instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) compilationExecutorService).getQueue().size();
        } else {
            return 0;
        }
    }

    public void shutdownAndAwaitTermination(long timeout) {
        // TODO
        compilationExecutorService.shutdownNow();
        try {
            compilationExecutorService.awaitTermination(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException("Could not terminate compiler threads. Check if there are runaway compilations that don't handle Thread#interrupt.", e);
        }
    }
}
