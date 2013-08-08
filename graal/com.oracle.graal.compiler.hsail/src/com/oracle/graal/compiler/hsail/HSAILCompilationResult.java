/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.compiler.hsail;

import static com.oracle.graal.api.code.CodeUtil.*;

import java.util.logging.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CallingConvention.*;
import com.oracle.graal.api.runtime.Graal;
import com.oracle.graal.compiler.GraalCompiler;
import com.oracle.graal.java.GraphBuilderConfiguration;
import com.oracle.graal.java.GraphBuilderPhase;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.spi.GraalCodeCacheProvider;
import com.oracle.graal.phases.OptimisticOptimizations;
import com.oracle.graal.phases.PhasePlan;
import com.oracle.graal.phases.PhasePlan.PhasePosition;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.graph.GraalInternalError;
import com.oracle.graal.debug.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.nodes.spi.Replacements;
import com.oracle.graal.compiler.target.Backend;
import com.oracle.graal.hsail.*;

import java.lang.reflect.Method;

/**
 * Class that represents a HSAIL compilation result. Includes the compiled HSAIL code.
 */
public class HSAILCompilationResult {

    private CompilationResult compResult;
    protected static GraalCodeCacheProvider runtime = Graal.getRequiredCapability(GraalCodeCacheProvider.class);
    protected static Replacements replacements = Graal.getRequiredCapability(Replacements.class);
    protected static Backend backend = Graal.getRequiredCapability(Backend.class);
    protected static SuitesProvider suitesProvider = Graal.getRequiredCapability(SuitesProvider.class);
    private static final String propPkgName = HSAILCompilationResult.class.getPackage().getName();
    private static Level logLevel;
    private static ConsoleHandler consoleHandler;
    public static Logger logger;
    static {
        logger = Logger.getLogger(propPkgName);
        logLevel = Level.FINE;
        // This block configures the logger with handler and formatter.
        consoleHandler = new ConsoleHandler();
        logger.addHandler(consoleHandler);
        logger.setUseParentHandlers(false);
        SimpleFormatter formatter = new SimpleFormatter() {

            @SuppressWarnings("sync-override")
            @Override
            public String format(LogRecord record) {
                return (record.getMessage() + "\n");
            }
        };
        consoleHandler.setFormatter(formatter);
        logger.setLevel(logLevel);
        consoleHandler.setLevel(logLevel);
    }

    public static HSAILCompilationResult getHSAILCompilationResult(Method meth) {
        ResolvedJavaMethod javaMethod = runtime.lookupJavaMethod(meth);
        return getHSAILCompilationResult(javaMethod);
    }

    public static HSAILCompilationResult getHSAILCompilationResult(ResolvedJavaMethod javaMethod) {
        StructuredGraph graph = new StructuredGraph(javaMethod);
        new GraphBuilderPhase(runtime, GraphBuilderConfiguration.getEagerDefault(), OptimisticOptimizations.ALL).apply(graph);
        return getHSAILCompilationResult(graph);
    }

    public static HSAILCompilationResult getHSAILCompilationResult(StructuredGraph graph) {
        Debug.dump(graph, "Graph");
        TargetDescription target = new TargetDescription(new HSAIL(), true, 8, 0, true);
        HSAILBackend hsailBackend = new HSAILBackend(Graal.getRequiredCapability(GraalCodeCacheProvider.class), target);
        PhasePlan phasePlan = new PhasePlan();
        GraphBuilderPhase graphBuilderPhase = new GraphBuilderPhase(runtime, GraphBuilderConfiguration.getDefault(), OptimisticOptimizations.NONE);
        phasePlan.addPhase(PhasePosition.AFTER_PARSING, graphBuilderPhase);
        phasePlan.addPhase(PhasePosition.AFTER_PARSING, new HSAILPhase());
        new HSAILPhase().apply(graph);
        CallingConvention cc = getCallingConvention(runtime, Type.JavaCallee, graph.method(), false);
        try {
            CompilationResult compResult = GraalCompiler.compileGraph(graph, cc, graph.method(), runtime, replacements, hsailBackend, target, null, phasePlan, OptimisticOptimizations.NONE,
                            new SpeculationLog(), suitesProvider.getDefaultSuites(), new CompilationResult());
            return new HSAILCompilationResult(compResult);
        } catch (GraalInternalError e) {
            String partialCode = hsailBackend.getPartialCodeString();
            if (partialCode != null && !partialCode.equals("")) {
                logger.fine("-------------------\nPartial Code Generation:\n--------------------");
                logger.fine(partialCode);
                logger.fine("-------------------\nEnd of Partial Code Generation\n--------------------");
            }
            throw e;
        }
    }

    private static class HSAILPhase extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            for (LocalNode local : graph.getNodes(LocalNode.class)) {
                if (local.stamp() instanceof ObjectStamp) {
                    local.setStamp(StampFactory.declaredNonNull(((ObjectStamp) local.stamp()).type()));
                }
            }
        }
    }

    protected HSAILCompilationResult(CompilationResult compResultInput) {
        compResult = compResultInput;
    }

    public CompilationResult getCompilationResult() {
        return compResult;
    }

    public String getHSAILCode() {
        return new String(compResult.getTargetCode(), 0, compResult.getTargetCodeSize());
    }

    public void dumpCompilationResult() {
        logger.fine("targetCodeSize=" + compResult.getTargetCodeSize());
        logger.fine(getHSAILCode());
    }

}
