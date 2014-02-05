/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nfi.hotspot.amd64.util;

import static com.oracle.graal.api.code.CodeUtil.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.meta.ProfilingInfo.TriState;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.printer.*;

/**
 * Utility to install the code of the native call stub.
 * 
 */
public class InstallUtil {

    protected final HotSpotProviders providers;
    protected final Backend backend;

    public InstallUtil(HotSpotProviders providers, Backend backend) {
        DebugEnvironment.initialize(System.out);
        this.providers = providers;
        this.backend = backend;
    }

    /**
     * Attaches a graph to a method libCall. Compiles the graph and installs it.
     * 
     * @param g the graph to be attached to a method and compiled
     * @return returns the installed code that also holds a copy of graph g
     */
    public InstalledCode install(final StructuredGraph g) {
        try {
            Suites suites = providers.getSuites().createSuites();
            PhaseSuite<HighTierContext> phaseSuite = backend.getSuites().getDefaultGraphBuilderSuite().copy();
            CallingConvention cc = getCallingConvention(providers.getCodeCache(), Type.JavaCallee, g.method(), false);
            CompilationResult compResult = GraalCompiler.compileGraph(g, cc, g.method(), providers, backend, backend.getTarget(), null, phaseSuite, OptimisticOptimizations.ALL,
                            DefaultProfilingInfo.get(TriState.UNKNOWN), null, suites, true, new CompilationResult(), CompilationResultBuilderFactory.Default);
            InstalledCode installedCode;
            try (Scope s = Debug.scope("CodeInstall", providers.getCodeCache(), g.method())) {
                installedCode = providers.getCodeCache().addMethod(g.method(), compResult, null);
            }

            return installedCode;
        } catch (SecurityException e) {
            throw GraalInternalError.shouldNotReachHere("Installation of GNFI Callstub failed.");
        }
    }
}
