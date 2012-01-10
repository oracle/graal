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

import static com.oracle.max.graal.debug.Debug.*;

import java.util.*;

import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.max.cri.xir.*;
import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.phases.*;
import com.oracle.max.graal.compiler.stub.*;
import com.oracle.max.graal.compiler.target.*;
import com.oracle.max.graal.cri.*;
import com.oracle.max.graal.debug.*;
import com.oracle.max.graal.nodes.*;

public class GraalCompiler {

    public final Map<Object, CompilerStub> stubs = new HashMap<>();

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

    public final RiRegisterConfig compilerStubRegisterConfig;

    public GraalCompiler(GraalContext context, GraalRuntime runtime, CiTarget target, RiXirGenerator xirGen, RiRegisterConfig compilerStubRegisterConfig) {
        this.context = context;
        this.runtime = runtime;
        this.target = target;
        this.xir = xirGen;
        this.compilerStubRegisterConfig = compilerStubRegisterConfig;
        this.backend = Backend.create(target.arch, this);
        init();
    }

    public CiTargetMethod compileMethod(RiResolvedMethod method, int osrBCI, CiStatistics stats, CiCompiler.DebugInfoLevel debugInfoLevel) {
        return compileMethod(method, osrBCI, stats, debugInfoLevel, PhasePlan.DEFAULT);
    }

    public CiTargetMethod compileMethod(RiResolvedMethod method, int osrBCI, CiStatistics stats, CiCompiler.DebugInfoLevel debugInfoLevel, PhasePlan plan) {
        return compileMethod(method, new StructuredGraph(method), osrBCI, stats, debugInfoLevel, plan);
    }

    public CiTargetMethod compileMethod(RiResolvedMethod method, StructuredGraph graph, int osrBCI, CiStatistics stats, CiCompiler.DebugInfoLevel debugInfoLevel, PhasePlan plan) {
        try (Scope scope = openScope("CompileMethod", method)) {
            CiTargetMethod result = null;
            TTY.Filter filter = new TTY.Filter(GraalOptions.PrintFilter, method);
            GraalCompilation compilation = new GraalCompilation(context, this, method, graph, osrBCI, stats, debugInfoLevel);
            try {
                result = compilation.compile(plan);
            } finally {
                filter.remove();
            }
            return result;
        }
    }

    private void init() {
        final List<XirTemplate> xirTemplateStubs = xir.makeTemplates(backend.newXirAssembler());

        if (xirTemplateStubs != null) {
            for (XirTemplate template : xirTemplateStubs) {
                TTY.Filter filter = new TTY.Filter(GraalOptions.PrintFilter, template.name);
                try {
                    stubs.put(template, backend.emit(context, template));
                } finally {
                    filter.remove();
                }
            }
        }

        for (CompilerStub.Id id : CompilerStub.Id.values()) {
            TTY.Filter suppressor = new TTY.Filter(GraalOptions.PrintFilter, id);
            try {
                stubs.put(id, backend.emit(context, id));
            } finally {
                suppressor.remove();
            }
        }
    }

    public CompilerStub lookupStub(CompilerStub.Id id) {
        CompilerStub stub = stubs.get(id);
        assert stub != null : "no stub for global stub id: " + id;
        return stub;
    }

    public CompilerStub lookupStub(XirTemplate template) {
        CompilerStub stub = stubs.get(template);
        assert stub != null : "no stub for XirTemplate: " + template;
        return stub;
    }

    public CompilerStub lookupStub(CiRuntimeCall runtimeCall) {
        CompilerStub stub = stubs.get(runtimeCall);
        if (stub == null) {
            stub = backend.emit(context, runtimeCall);
            stubs.put(runtimeCall, stub);
        }

        assert stub != null : "could not find global stub for runtime call: " + runtimeCall;
        return stub;
    }
}
