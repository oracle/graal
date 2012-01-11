/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler;

import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.max.cri.xir.*;
import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.phases.*;
import com.oracle.max.graal.compiler.target.*;
import com.oracle.max.graal.cri.*;

public class GraalCompiler {

    public final GraalContext context;

    /**
     * The target that this compiler has been configured for.
     */
    public final CiTarget target;

    /**
     * The runtime that this compiler has been configured for.
     */
    public final GraalRuntime runtime;

    /**
     * The XIR generator that lowers Java operations to machine operations.
     */
    public final RiXirGenerator xir;

    /**
     * The backend that this compiler has been configured for.
     */
    public final Backend backend;

    public GraalCompiler(GraalContext context, GraalRuntime runtime, CiTarget target, RiXirGenerator xirGen) {
        this.context = context;
        this.runtime = runtime;
        this.target = target;
        this.xir = xirGen;
        this.backend = Backend.create(target.arch, runtime, target);
        xir.initialize(backend.newXirAssembler());
    }

    public CiTargetMethod compileMethod(RiResolvedMethod method, int osrBCI, CiStatistics stats, CiCompiler.DebugInfoLevel debugInfoLevel) {
        return compileMethod(method, osrBCI, stats, debugInfoLevel, PhasePlan.DEFAULT);
    }

    public CiTargetMethod compileMethod(RiResolvedMethod method, int osrBCI, CiStatistics stats, CiCompiler.DebugInfoLevel debugInfoLevel, PhasePlan plan) {
        context.timers.startScope(getClass());
        try {
            long startTime = 0;
            int index = context.metrics.CompiledMethods++;
            final boolean printCompilation = GraalOptions.PrintCompilation && !TTY.isSuppressed();
            if (printCompilation) {
                TTY.println(String.format("Graal %4d %-70s %-45s %-50s ...",
                                index,
                                method.holder().name(),
                                method.name(),
                                method.signature().asString()));
                startTime = System.nanoTime();
            }

            CiTargetMethod result = null;
            TTY.Filter filter = new TTY.Filter(GraalOptions.PrintFilter, method);
            GraalCompilation compilation = new GraalCompilation(context, this, method, osrBCI, stats, debugInfoLevel);
            try {
                result = compilation.compile(plan);
            } finally {
                filter.remove();
                if (printCompilation) {
                    long time = (System.nanoTime() - startTime) / 100000;
                    TTY.println(String.format("Graal %4d %-70s %-45s %-50s | %3d.%dms %4dnodes %5dB",
                                    index,
                                    "",
                                    "",
                                    "",
                                    time / 10,
                                    time % 10,
                                    compilation.graph.getNodeCount(),
                                    (result != null ? result.targetCodeSize() : -1)));
                }
            }

            return result;
        } finally {
            context.timers.endScope();
        }
    }
}
