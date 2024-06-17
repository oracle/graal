/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.lambda;

import com.oracle.graal.pointsto.infrastructure.OriginalMethodProvider;
import com.oracle.svm.core.bootstrap.BootstrapMethodConfiguration;

import jdk.graal.compiler.java.BytecodeParser;
import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.IntrinsicContext;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.vm.ci.meta.ConstantPool.BootstrapMethodInvocation;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class LambdaSubstrateGraphBuilderPhase extends GraphBuilderPhase {
    public LambdaSubstrateGraphBuilderPhase(GraphBuilderConfiguration config) {
        super(config);
    }

    @Override
    public GraphBuilderPhase copyWithConfig(GraphBuilderConfiguration config) {
        return new LambdaSubstrateGraphBuilderPhase(config);
    }

    @Override
    protected Instance createInstance(CoreProviders providers, GraphBuilderConfiguration instanceGBConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext) {
        return new LambdaSubstrateGraphBuilderInstance(providers, instanceGBConfig, optimisticOpts, initialIntrinsicContext);
    }

    public static class LambdaSubstrateGraphBuilderInstance extends GraphBuilderPhase.Instance {
        public LambdaSubstrateGraphBuilderInstance(CoreProviders theProviders, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts,
                        IntrinsicContext initialIntrinsicContext) {
            super(theProviders, graphBuilderConfig, optimisticOpts, initialIntrinsicContext);
        }

        @Override
        protected BytecodeParser createBytecodeParser(StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI, IntrinsicContext intrinsicContext) {
            return new LambdaSubstrateBytecodeParser(this, graph, parent, method, entryBCI, intrinsicContext);
        }
    }

    public static class LambdaSubstrateBytecodeParser extends BytecodeParser {
        protected LambdaSubstrateBytecodeParser(Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI,
                        IntrinsicContext intrinsicContext) {
            super(graphBuilderInstance, graph, parent, method, entryBCI, intrinsicContext);
        }

        @Override
        protected void genLoadConstant(int cpi, int opcode) {
            Object con = lookupConstant(cpi, opcode, false);
            BootstrapMethodInvocation bootstrap = constantPool.lookupBootstrapMethodInvocation(cpi, -1);
            if (con == null && bootstrap != null && BootstrapMethodConfiguration.singleton().isCondyTrusted(OriginalMethodProvider.getJavaMethod(bootstrap.getMethod()))) {
                /*
                 * With the current implementation of LambdaUtils#findStableLambdaName, each lambda
                 * must contain at least one method invocation to compute its name. Looking up the
                 * constant with allowBootstrapMethodInvocation set to false will produce null if
                 * the constant is dynamic. Returning it will cause a runtime exception and if the
                 * lambda starts with the constant lookup, the lambda will contain no method
                 * invocation. At this stage, the AnalysisBytecodeParser cannot be created, so the
                 * graph with the bootstrap method call cannot be created. Thus, at the moment,
                 * allowing those bootstrap method to be executed at build time is the only way to
                 * obtain correct lambda names.
                 */
                con = lookupConstant(cpi, opcode, true);
            }
            genLoadConstantHelper(con, opcode);
        }
    }
}
