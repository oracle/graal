/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.test.backend;

import jdk.vm.ci.code.Architecture;

import com.oracle.graal.compiler.GraalCompiler;
import com.oracle.graal.compiler.test.GraalCompilerTest;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.phases.OptimisticOptimizations;

public abstract class BackendTest extends GraalCompilerTest {

    public BackendTest() {
        super();
    }

    public BackendTest(Class<? extends Architecture> arch) {
        super(arch);
    }

    @SuppressWarnings("try")
    protected LIRGenerationResult getLIRGenerationResult(final StructuredGraph graph) {
        try (Scope s = Debug.scope("FrontEnd")) {
            GraalCompiler.emitFrontEnd(getProviders(), getBackend(), graph, getDefaultGraphBuilderSuite(), OptimisticOptimizations.NONE, graph.getProfilingInfo(), getSuites());
        } catch (Throwable e) {
            throw Debug.handle(e);
        }

        LIRGenerationResult lirGen = GraalCompiler.emitLIR(getBackend(), graph, null, null, getLIRSuites(), null);
        return lirGen;
    }

}
