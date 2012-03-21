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

import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.phases.*;
import com.oracle.graal.compiler.phases.PhasePlan.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.java.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.max.criutils.*;


public final class CompilationTask implements Runnable {

    private final Compiler compiler;
    private final PhasePlan plan;
    private final RiResolvedMethod method;

    public static CompilationTask create(Compiler compiler, PhasePlan plan, RiResolvedMethod method) {
        return new CompilationTask(compiler, plan, method);
    }

    private CompilationTask(Compiler compiler, PhasePlan plan, RiResolvedMethod method) {
        this.compiler = compiler;
        this.plan = plan;
        this.method = method;
    }

    public void run() {
        try {
            GraphBuilderPhase graphBuilderPhase = new GraphBuilderPhase(compiler.getRuntime());
            plan.addPhase(PhasePosition.AFTER_PARSING, graphBuilderPhase);
            final boolean printCompilation = GraalOptions.PrintCompilation && !TTY.isSuppressed();
            if (printCompilation) {
                TTY.println(String.format("Graal %-70s %-45s %-50s ...", method.holder().name(), method.name(), method.signature().asString()));
            }

            CiTargetMethod result = null;
            TTY.Filter filter = new TTY.Filter(GraalOptions.PrintFilter, method);
            try {
                result = Debug.scope("Compiling", new Callable<CiTargetMethod>() {

                    @Override
                    public CiTargetMethod call() throws Exception {
                        return compiler.getCompiler().compileMethod(method, -1, plan);
                    }
                });
            } finally {
                filter.remove();
                if (printCompilation) {
                    TTY.println(String.format("Graal %-70s %-45s %-50s | %4dnodes %5dB", "", "", "", 0, (result != null ? result.targetCodeSize() : -1)));
                }
            }
            compiler.getRuntime().installMethod(method, result);
        } catch (CiBailout bailout) {
            Debug.metric("Bailouts").increment();
            if (GraalOptions.ExitVMOnBailout) {
                bailout.printStackTrace(TTY.cachedOut);
                System.exit(-1);
            }
        } catch (Throwable t) {
            if (GraalOptions.ExitVMOnException) {
                t.printStackTrace(TTY.cachedOut);
                System.exit(-1);
            }
        }
    }

}
