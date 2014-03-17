/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.java.decompiler.test;

import static com.oracle.graal.api.code.CodeUtil.*;
import static com.oracle.graal.compiler.GraalCompiler.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.java.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.runtime.*;

public class TestUtil {

    public static void compileMethod(ResolvedJavaMethod method) {
        Backend backend = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend();
        Providers providers = backend.getProviders();
        SuitesProvider suitesProvider = backend.getSuites();
        Suites suites = suitesProvider.getDefaultSuites();
        StructuredGraph graph = new StructuredGraph(method);
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        new GraphBuilderPhase.Instance(metaAccess, GraphBuilderConfiguration.getEagerDefault(), OptimisticOptimizations.ALL).apply(graph);
        PhaseSuite<HighTierContext> graphBuilderSuite = suitesProvider.getDefaultGraphBuilderSuite();
        CallingConvention cc = getCallingConvention(providers.getCodeCache(), Type.JavaCallee, graph.method(), false);
        compileGraph(graph, null, cc, method, providers, backend, providers.getCodeCache().getTarget(), null, graphBuilderSuite, OptimisticOptimizations.ALL, getProfilingInfo(graph), null, suites,
                        new CompilationResult(), CompilationResultBuilderFactory.Default);
    }
}
