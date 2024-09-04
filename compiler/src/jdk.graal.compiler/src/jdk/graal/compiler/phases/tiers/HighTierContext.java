/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.tiers;

import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.ExplicitOOMEExceptionEdges;
import jdk.graal.compiler.nodes.spi.CoreProvidersDelegate;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.util.Providers;

public class HighTierContext extends CoreProvidersDelegate {

    private final PhaseSuite<HighTierContext> graphBuilderSuite;

    private final OptimisticOptimizations optimisticOpts;

    public HighTierContext(Providers providers, PhaseSuite<HighTierContext> graphBuilderSuite, OptimisticOptimizations optimisticOpts) {
        super(providers);
        this.graphBuilderSuite = graphBuilderSuite;
        this.optimisticOpts = optimisticOpts;
    }

    public PhaseSuite<HighTierContext> getGraphBuilderSuite() {
        return graphBuilderSuite;
    }

    public PhaseSuite<HighTierContext> getGraphBuilderSuiteForCallee(Invoke invoke) {
        PhaseSuite<HighTierContext> regularGraphBuilder = graphBuilderSuite;
        if (invoke.isInOOMETry()) {
            PhaseSuite<HighTierContext> copied = regularGraphBuilder.copy();
            GraphBuilderPhase originalBuilder = (GraphBuilderPhase) (copied.findPhase(GraphBuilderPhase.class).previous());
            GraphBuilderConfiguration newConfig = originalBuilder.getGraphBuilderConfig().copy().withOOMEExceptionEdges(ExplicitOOMEExceptionEdges.ForceOOMEExceptionEdges);
            copied.findPhase(GraphBuilderPhase.class).set(originalBuilder.copyWithConfig(newConfig));
            return copied;
        }
        return regularGraphBuilder;
    }

    public OptimisticOptimizations getOptimisticOptimizations() {
        return optimisticOpts;
    }

    @Override
    public Providers getProviders() {
        return (Providers) super.getProviders();
    }
}