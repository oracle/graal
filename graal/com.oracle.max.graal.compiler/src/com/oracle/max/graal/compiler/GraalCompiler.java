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

import java.util.*;

import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.globalstub.*;
import com.oracle.max.graal.compiler.observer.*;
import com.oracle.max.graal.compiler.target.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;
import com.sun.cri.xir.*;

public class GraalCompiler extends ObservableCompiler {

    public final Map<Object, GlobalStub> stubs = new HashMap<Object, GlobalStub>();

    /**
     * The target that this compiler has been configured for.
     */
    public final CiTarget target;

    /**
     * The runtime that this compiler has been configured for.
     */
    public final RiRuntime runtime;

    /**
     * The XIR generator that lowers Java operations to machine operations.
     */
    public final RiXirGenerator xir;

    /**
     * The backend that this compiler has been configured for.
     */
    public final Backend backend;

    public final RiRegisterConfig globalStubRegisterConfig;

    private GraalCompilation currentCompilation;

    public GraalCompiler(RiRuntime runtime, CiTarget target, RiXirGenerator xirGen, RiRegisterConfig globalStubRegisterConfig) {
        this.runtime = runtime;
        this.target = target;
        this.xir = xirGen;
        this.globalStubRegisterConfig = globalStubRegisterConfig;
        this.backend = Backend.create(target.arch, this);
        init();

        Graph.verificationListeners.add(new VerificationListener() {
            @Override
            public void verificationFailed(Node n, String message) {
                GraalCompiler.this.fireCompilationEvent(new CompilationEvent(currentCompilation, "Verification Error on Node " + n.id(), currentCompilation.graph, true, false, true));
                TTY.println(n.toString());
                if (n.predecessor() != null) {
                    TTY.println("predecessor: " + n.predecessor());
                }
                for (Node p : n.usages()) {
                    TTY.println("usage: " + p);
                }
                assert false : "Verification of node " + n + " failed: " + message;
            }
        });
    }

    public CiResult compileMethod(RiMethod method, int osrBCI, RiXirGenerator xirGenerator, CiStatistics stats) {
        GraalTimers.TOTAL.start();
        long startTime = 0;
        int index = GraalMetrics.CompiledMethods++;
        if (GraalOptions.PrintCompilation) {
            TTY.print(String.format("Graal %4d %-70s %-45s | ", index, method.holder().name(), method.name()));
            startTime = System.nanoTime();
        }

        CiResult result = null;
        TTY.Filter filter = new TTY.Filter(GraalOptions.PrintFilter, CiUtil.format("%H.%n", method, false));
        GraalCompilation compilation = new GraalCompilation(this, method, osrBCI, stats);
        currentCompilation = compilation;
        try {
            result = compilation.compile();
        } finally {
            filter.remove();
            compilation.close();
            if (GraalOptions.PrintCompilation && !TTY.isSuppressed()) {
                long time = (System.nanoTime() - startTime) / 100000;
                TTY.println(String.format("%3d.%dms", time / 10, time % 10));
            }
            GraalTimers.TOTAL.stop();
        }

        return result;
    }

    private void init() {
        final List<XirTemplate> xirTemplateStubs = xir.buildTemplates(backend.newXirAssembler());
        final GlobalStubEmitter emitter = backend.newGlobalStubEmitter();

        if (xirTemplateStubs != null) {
            for (XirTemplate template : xirTemplateStubs) {
                TTY.Filter filter = new TTY.Filter(GraalOptions.PrintFilter, template.name);
                try {
                    stubs.put(template, emitter.emit(template, runtime));
                } finally {
                    filter.remove();
                }
            }
        }

        for (GlobalStub.Id id : GlobalStub.Id.values()) {
            TTY.Filter suppressor = new TTY.Filter(GraalOptions.PrintFilter, id);
            try {
                stubs.put(id, emitter.emit(id, runtime));
            } finally {
                suppressor.remove();
            }
        }

        if (GraalOptions.PrintCFGToFile) {
            addCompilationObserver(new CFGPrinterObserver());
        }
        if (GraalOptions.PrintDOTGraphToFile) {
            addCompilationObserver(new GraphvizPrinterObserver(false));
        }
        if (GraalOptions.PrintDOTGraphToPdf) {
            addCompilationObserver(new GraphvizPrinterObserver(true));
        }
        if (GraalOptions.PrintIdealGraphLevel != 0 || GraalOptions.Plot || GraalOptions.PlotOnError) {
            CompilationObserver observer;
            if (GraalOptions.PrintIdealGraphFile) {
                observer = new IdealGraphPrinterObserver();
            } else {
                observer = new IdealGraphPrinterObserver(GraalOptions.PrintIdealGraphAddress, GraalOptions.PrintIdealGraphPort);
            }
            addCompilationObserver(observer);
        }
    }

    public GlobalStub lookupGlobalStub(GlobalStub.Id id) {
        GlobalStub globalStub = stubs.get(id);
        assert globalStub != null : "no stub for global stub id: " + id;
        return globalStub;
    }

    public GlobalStub lookupGlobalStub(XirTemplate template) {
        GlobalStub globalStub = stubs.get(template);
        assert globalStub != null : "no stub for XirTemplate: " + template;
        return globalStub;
    }

    public GlobalStub lookupGlobalStub(CiRuntimeCall runtimeCall) {
        GlobalStub globalStub = stubs.get(runtimeCall);
        if (globalStub == null) {
            globalStub = backend.newGlobalStubEmitter().emit(runtimeCall, runtime);
            stubs.put(runtimeCall, globalStub);
        }

        assert globalStub != null : "could not find global stub for runtime call: " + runtimeCall;
        return globalStub;
    }
}
