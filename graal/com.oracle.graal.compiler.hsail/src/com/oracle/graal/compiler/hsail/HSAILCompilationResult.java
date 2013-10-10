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

import java.lang.reflect.*;
import java.util.logging.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hsail.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.PhasePlan.PhasePosition;
import com.oracle.graal.phases.tiers.*;

/**
 * Class that represents a HSAIL compilation result. Includes the compiled HSAIL code.
 */
public class HSAILCompilationResult {

    private CompilationResult compResult;
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
        MetaAccessProvider metaAccess = Graal.getRequiredCapability(MetaAccessProvider.class);
        ResolvedJavaMethod javaMethod = metaAccess.lookupJavaMethod(meth);
        return getHSAILCompilationResult(javaMethod);
    }

    public static HSAILCompilationResult getHSAILCompilationResult(ResolvedJavaMethod javaMethod) {
        MetaAccessProvider metaAccess = Graal.getRequiredCapability(MetaAccessProvider.class);
        StructuredGraph graph = new StructuredGraph(javaMethod);
        new GraphBuilderPhase(metaAccess, GraphBuilderConfiguration.getEagerDefault(), OptimisticOptimizations.ALL).apply(graph);
        return getHSAILCompilationResult(graph);
    }

    /**
     * HSAIL doesn't have a calling convention as such. Function arguments are actually passed in
     * memory but then loaded into registers in the function body. This routine makes sure that
     * arguments to a kernel or function are loaded (by the kernel or function body) into registers
     * of the appropriate sizes. For example, int and float parameters should appear in S registers,
     * whereas double and long parameters should appear in d registers.
     */
    public static CallingConvention getHSAILCallingConvention(CallingConvention.Type type, TargetDescription target, ResolvedJavaMethod method, boolean stackOnly) {
        Signature sig = method.getSignature();
        JavaType retType = sig.getReturnType(null);
        int sigCount = sig.getParameterCount(false);
        JavaType[] argTypes;
        int argIndex = 0;
        if (!Modifier.isStatic(method.getModifiers())) {
            argTypes = new JavaType[sigCount + 1];
            argTypes[argIndex++] = method.getDeclaringClass();
        } else {
            argTypes = new JavaType[sigCount];
        }
        for (int i = 0; i < sigCount; i++) {
            argTypes[argIndex++] = sig.getParameterType(i, null);
        }

        RegisterConfig registerConfig = new HSAILRegisterConfig();
        return registerConfig.getCallingConvention(type, retType, argTypes, target, stackOnly);
    }

    public static HSAILCompilationResult getHSAILCompilationResult(StructuredGraph graph) {
        Debug.dump(graph, "Graph");
        TargetDescription target = new TargetDescription(new HSAIL(), true, 8, 0, true);
        MetaAccessProvider metaAccess = Graal.getRequiredCapability(MetaAccessProvider.class);
        GraalCodeCacheProvider codeCache = Graal.getRequiredCapability(GraalCodeCacheProvider.class);
        HSAILBackend hsailBackend = new HSAILBackend(metaAccess, codeCache, target);
        PhasePlan phasePlan = new PhasePlan();
        GraphBuilderPhase graphBuilderPhase = new GraphBuilderPhase(metaAccess, GraphBuilderConfiguration.getDefault(), OptimisticOptimizations.NONE);
        phasePlan.addPhase(PhasePosition.AFTER_PARSING, graphBuilderPhase);
        phasePlan.addPhase(PhasePosition.AFTER_PARSING, new HSAILPhase());
        new HSAILPhase().apply(graph);
        CallingConvention cc = getHSAILCallingConvention(Type.JavaCallee, target, graph.method(), false);
        Replacements replacements = Graal.getRequiredCapability(Replacements.class);
        SuitesProvider suitesProvider = Graal.getRequiredCapability(SuitesProvider.class);
        try {
            CompilationResult compResult = GraalCompiler.compileGraph(graph, cc, graph.method(), metaAccess, codeCache, replacements, hsailBackend, target, null, phasePlan,
                            OptimisticOptimizations.NONE, new SpeculationLog(), suitesProvider.getDefaultSuites(), new CompilationResult());
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
