/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test.backend;

import jdk.graal.compiler.core.GraalCompiler;
import jdk.graal.compiler.core.gen.LIRCompilerBackend;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.vm.ci.code.Architecture;

public abstract class BackendTest extends GraalCompilerTest {

    public BackendTest() {
        super();
    }

    public BackendTest(Class<? extends Architecture> arch) {
        super(arch);
    }

    protected LIRGenerationResult getLIRGenerationResult(final StructuredGraph graph, OptimisticOptimizations optimizations) {
        DebugContext debug = graph.getDebug();
        try (DebugContext.Scope _ = debug.scope("FrontEnd")) {
            GraalCompiler.emitFrontEnd(getProviders(), getBackend(), graph, getDefaultGraphBuilderSuite(), optimizations, graph.getProfilingInfo(), createSuites(graph.getOptions()));
        } catch (Throwable e) {
            throw debug.handle(e);
        }

        try (DebugContext.Scope _ = debug.scope("BackEnd", graph, graph.getLastSchedule())) {
            return LIRCompilerBackend.emitLIR(getBackend(), graph, null, null, createLIRSuites(graph.getOptions()), null);
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    protected LIRGenerationResult getLIRGenerationResult(final StructuredGraph graph) {
        return getLIRGenerationResult(graph, OptimisticOptimizations.NONE);
    }
}
