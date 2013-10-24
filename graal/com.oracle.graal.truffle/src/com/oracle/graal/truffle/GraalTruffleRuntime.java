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
package com.oracle.graal.truffle;

import static com.oracle.graal.api.code.CodeUtil.*;
import static com.oracle.graal.truffle.TruffleCompilerOptions.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.PhasePlan.PhasePosition;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.runtime.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.impl.*;
import com.oracle.truffle.api.nodes.*;

/**
 * Implementation of the Truffle runtime when running on top of Graal.
 */
public final class GraalTruffleRuntime implements TruffleRuntime {

    public static GraalTruffleRuntime makeInstance() {
        return new GraalTruffleRuntime();
    }

    private TruffleCompiler truffleCompiler;
    private Replacements truffleReplacements;
    private ArrayList<String> includes;
    private ArrayList<String> excludes;

    private GraalTruffleRuntime() {
        installOptimizedCallTargetCallMethod();
    }

    public String getName() {
        return "Graal Truffle Runtime";
    }

    public CallTarget createCallTarget(RootNode rootNode) {
        return createCallTarget(rootNode, new FrameDescriptor());
    }

    @Override
    public CallTarget createCallTarget(RootNode rootNode, FrameDescriptor frameDescriptor) {
        if (!acceptForCompilation(rootNode)) {
            return new DefaultCallTarget(rootNode, frameDescriptor);
        }
        if (truffleCompiler == null) {
            truffleCompiler = new TruffleCompilerImpl();
        }
        return new OptimizedCallTarget(rootNode, frameDescriptor, truffleCompiler, TruffleMinInvokeThreshold.getValue(), TruffleCompilationThreshold.getValue());
    }

    @Override
    public VirtualFrame createVirtualFrame(PackedFrame caller, Arguments arguments, FrameDescriptor frameDescriptor) {
        return OptimizedCallTarget.createFrame(frameDescriptor, caller, arguments);
    }

    @Override
    public MaterializedFrame createMaterializedFrame(Arguments arguments) {
        return createMaterializedFrame(arguments);
    }

    @Override
    public MaterializedFrame createMaterializedFrame(Arguments arguments, FrameDescriptor frameDescriptor) {
        return new FrameWithoutBoxing(frameDescriptor, null, arguments);
    }

    @Override
    public Assumption createAssumption() {
        return createAssumption(null);
    }

    @Override
    public Assumption createAssumption(String name) {
        return new OptimizedAssumption(name);
    }

    public Replacements getReplacements() {
        if (truffleReplacements == null) {
            truffleReplacements = TruffleReplacements.makeInstance();
        }
        return truffleReplacements;
    }

    private boolean acceptForCompilation(RootNode rootNode) {
        if (TruffleCompileOnly.getValue() != null) {
            if (includes == null) {
                parseCompileOnly();
            }

            String name = rootNode.toString();
            boolean included = includes.isEmpty();
            for (int i = 0; !included && i < includes.size(); i++) {
                if (name.contains(includes.get(i))) {
                    included = true;
                }
            }
            if (!included) {
                return false;
            }
            for (String exclude : excludes) {
                if (name.contains(exclude)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void parseCompileOnly() {
        includes = new ArrayList<>();
        excludes = new ArrayList<>();

        String[] items = TruffleCompileOnly.getValue().split(",");
        for (String item : items) {
            if (item.startsWith("~")) {
                excludes.add(item.substring(1));
            } else {
                includes.add(item);
            }
        }
    }

    public static void installOptimizedCallTargetCallMethod() {
        Providers providers = getGraalProviders();
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        ResolvedJavaMethod resolvedCallMethod = metaAccess.lookupJavaMethod(getCallMethod());
        providers.getCodeCache().setDefaultMethod(resolvedCallMethod, compileMethod(resolvedCallMethod));
    }

    private static Method getCallMethod() {
        Method method;
        try {
            method = OptimizedCallTarget.class.getDeclaredMethod("call", new Class[]{PackedFrame.class, Arguments.class});
        } catch (NoSuchMethodException | SecurityException e) {
            throw GraalInternalError.shouldNotReachHere();
        }
        return method;
    }

    private static Backend instrumentBackend(Backend original) {
        String arch = original.getTarget().arch.getName();

        for (TruffleBackendFactory factory : ServiceLoader.loadInstalled(TruffleBackendFactory.class)) {
            if (factory.getArchitecture().equals(arch)) {
                return factory.createBackend(original);
            }
        }

        return original;
    }

    private static CompilationResult compileMethod(ResolvedJavaMethod javaMethod) {
        Providers providers = getGraalProviders();
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        Suites suites = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getSuites().createSuites();
        suites.getHighTier().findPhase(InliningPhase.class).remove();
        StructuredGraph graph = new StructuredGraph(javaMethod);
        ForeignCallsProvider foreignCalls = providers.getForeignCalls();
        new GraphBuilderPhase(metaAccess, foreignCalls, GraphBuilderConfiguration.getEagerDefault(), OptimisticOptimizations.ALL).apply(graph);
        PhasePlan phasePlan = new PhasePlan();
        GraphBuilderPhase graphBuilderPhase = new GraphBuilderPhase(metaAccess, foreignCalls, GraphBuilderConfiguration.getDefault(), OptimisticOptimizations.ALL);
        phasePlan.addPhase(PhasePosition.AFTER_PARSING, graphBuilderPhase);
        CallingConvention cc = getCallingConvention(providers.getCodeCache(), Type.JavaCallee, graph.method(), false);
        Backend backend = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend();
        return GraalCompiler.compileGraph(graph, cc, javaMethod, providers, instrumentBackend(backend), providers.getCodeCache().getTarget(), null, phasePlan, OptimisticOptimizations.ALL,
                        new SpeculationLog(), suites, new CompilationResult());
    }

    private static Providers getGraalProviders() {
        RuntimeProvider runtimeProvider = Graal.getRequiredCapability(RuntimeProvider.class);
        return runtimeProvider.getHostBackend().getProviders();
    }
}
