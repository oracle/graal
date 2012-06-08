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
package com.oracle.graal.hotspot;

import java.util.concurrent.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.phases.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.hotspot.ri.*;
import com.oracle.graal.nodes.*;
import com.oracle.max.criutils.*;


public final class CompilationTask implements Runnable, Comparable<CompilationTask> {


    public static final ThreadLocal<Boolean> withinEnqueue = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.valueOf(Thread.currentThread() instanceof CompilerThread);
        }
    };


    private volatile boolean cancelled;

    private final HotSpotGraalRuntime compiler;
    private final PhasePlan plan;
    private final HotSpotMethodResolved method;
    private final OptimisticOptimizations optimisticOpts;
    private final int id;
    private final int priority;

    public static CompilationTask create(HotSpotGraalRuntime compiler, PhasePlan plan, OptimisticOptimizations optimisticOpts, HotSpotMethodResolved method, int id, int priority) {
        return new CompilationTask(compiler, plan, optimisticOpts, method, id, priority);
    }

    private CompilationTask(HotSpotGraalRuntime compiler, PhasePlan plan, OptimisticOptimizations optimisticOpts, HotSpotMethodResolved method, int id, int priority) {
        this.compiler = compiler;
        this.plan = plan;
        this.method = method;
        this.optimisticOpts = optimisticOpts;
        this.id = id;
        this.priority = priority;
    }

    public RiResolvedMethod method() {
        return method;
    }

    public int priority() {
        return priority;
    }

    public void cancel() {
        cancelled = true;
    }

    public void run() {
        withinEnqueue.set(Boolean.FALSE);
        try {
            if (cancelled) {
                return;
            }
            if (GraalOptions.DynamicCompilePriority) {
                int threadPriority = priority < GraalOptions.SlowQueueCutoff ? Thread.NORM_PRIORITY : Thread.MIN_PRIORITY;
                if (Thread.currentThread().getPriority() != threadPriority) {
                    Thread.currentThread().setPriority(threadPriority);
                }
            }
            runCompilation();
            if (method.currentTask() == this) {
                method.setCurrentTask(null);
            }
        } finally {
            withinEnqueue.set(Boolean.TRUE);
        }
    }

    public void runCompilation() {
        CiCompilationStatistics stats = CiCompilationStatistics.create(method);
        try {
            final boolean printCompilation = GraalOptions.PrintCompilation && !TTY.isSuppressed();
            if (printCompilation) {
                TTY.println(String.format("%-6d Graal %-70s %-45s %-50s ...", id, method.holder().name(), method.name(), method.signature().asString()));
            }

            CiTargetMethod result = null;
            TTY.Filter filter = new TTY.Filter(GraalOptions.PrintFilter, method);
            try {
                result = Debug.scope("Compiling", new DebugDumpScope(String.valueOf(id), true), new Callable<CiTargetMethod>() {

                    @Override
                    public CiTargetMethod call() throws Exception {
                        compiler.evictDeoptedGraphs();
                        StructuredGraph graph = new StructuredGraph(method);
                        return compiler.getCompiler().compileMethod(method, graph, -1, compiler.getCache(), plan, optimisticOpts);
                    }
                });
            } finally {
                filter.remove();
                if (printCompilation) {
                    TTY.println(String.format("%-6d Graal %-70s %-45s %-50s | %4dnodes %5dB", id, "", "", "", 0, (result != null ? result.targetCodeSize() : -1)));
                }
            }

            installMethod(result);
        } catch (CiBailout bailout) {
            Debug.metric("Bailouts").increment();
            if (GraalOptions.ExitVMOnBailout) {
                TTY.cachedOut.println(CiUtil.format("Bailout in %H.%n(%p)", method));
                bailout.printStackTrace(TTY.cachedOut);
                System.exit(-1);
            } else if (GraalOptions.PrintBailout) {
                TTY.cachedOut.println(CiUtil.format("Bailout in %H.%n(%p)", method));
                bailout.printStackTrace(TTY.cachedOut);
            }
        } catch (Throwable t) {
            if (GraalOptions.ExitVMOnException) {
                t.printStackTrace(TTY.cachedOut);
                System.exit(-1);
            }
        }
        stats.finish(method);
    }

    private void installMethod(final CiTargetMethod tm) {
        Debug.scope("CodeInstall", new Object[] {new DebugDumpScope(String.valueOf(id), true), compiler.getCompiler(), method}, new Runnable() {
            @Override
            public void run() {
                final RiCodeInfo[] info = Debug.isDumpEnabled() ? new RiCodeInfo[1] : null;
                compiler.getRuntime().installMethod(method, tm, info);
                if (info != null) {
                    Debug.dump(info[0], "After code installation");
                }
            }

        });
    }

    @Override
    public int compareTo(CompilationTask o) {
        if (priority < o.priority) {
            return -1;
        }
        if (priority > o.priority) {
            return 1;
        }
        return id < o.id ? -1 : (id > o.id ? 1 : 0);
    }

}
