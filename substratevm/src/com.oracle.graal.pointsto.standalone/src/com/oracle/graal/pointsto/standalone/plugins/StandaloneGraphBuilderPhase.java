/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.standalone.plugins;

import jdk.graal.compiler.java.BytecodeParser;
import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.IntrinsicContext;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class StandaloneGraphBuilderPhase extends GraphBuilderPhase {
    public StandaloneGraphBuilderPhase(GraphBuilderConfiguration config) {
        super(config);
    }

    @Override
    public GraphBuilderPhase copyWithConfig(GraphBuilderConfiguration config) {
        return new StandaloneGraphBuilderPhase(config);
    }

    @Override
    protected Instance createInstance(CoreProviders providers, GraphBuilderConfiguration instanceGBConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext) {
        return new Instance(providers, instanceGBConfig, optimisticOpts, initialIntrinsicContext);
    }

    public static class Instance extends GraphBuilderPhase.Instance {

        public Instance(CoreProviders providers, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext) {
            super(providers, graphBuilderConfig, optimisticOpts, initialIntrinsicContext);
        }

        @Override
        protected BytecodeParser createBytecodeParser(StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI, IntrinsicContext intrinsicContext) {
            return new StandaloneBytecodeParser(this, graph, parent, method, entryBCI, intrinsicContext);
        }
    }

    /**
     * A non-abstract subclass of {@link BytecodeParser}. This exists mainly the use of non-platform
     * specific {@link BytecodeParser} can be audited.
     */
    static class StandaloneBytecodeParser extends BytecodeParser {
        protected StandaloneBytecodeParser(GraphBuilderPhase.Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI,
                        IntrinsicContext intrinsicContext) {
            super(graphBuilderInstance, graph, parent, method, entryBCI, intrinsicContext);
        }
    }
}
