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
package com.oracle.graal.phases.tiers;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.util.*;

public class HighTierContext extends PhaseContext {

    private final PhaseSuite<HighTierContext> graphBuilderSuite;

    private final Map<ResolvedJavaMethod, StructuredGraph> cache;
    private final OptimisticOptimizations optimisticOpts;

    public HighTierContext(MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection, LoweringProvider lowerer, Replacements replacements, Assumptions assumptions,
                    Map<ResolvedJavaMethod, StructuredGraph> cache, PhaseSuite<HighTierContext> graphBuilderSuite, OptimisticOptimizations optimisticOpts) {
        super(metaAccess, constantReflection, lowerer, replacements, assumptions);
        this.cache = cache;
        this.graphBuilderSuite = graphBuilderSuite;
        this.optimisticOpts = optimisticOpts;
    }

    public HighTierContext(Providers providers, Assumptions assumptions, Map<ResolvedJavaMethod, StructuredGraph> cache, PhaseSuite<HighTierContext> graphBuilderSuite,
                    OptimisticOptimizations optimisticOpts) {
        this(providers.getMetaAccess(), providers.getConstantReflection(), providers.getLowerer(), providers.getReplacements(), assumptions, cache, graphBuilderSuite, optimisticOpts);
    }

    public PhaseSuite<HighTierContext> getGraphBuilderSuite() {
        return graphBuilderSuite;
    }

    public Map<ResolvedJavaMethod, StructuredGraph> getGraphCache() {
        return cache;
    }

    public OptimisticOptimizations getOptimisticOptimizations() {
        return optimisticOpts;
    }

    public HighTierContext replaceAssumptions(Assumptions newAssumptions) {
        return new HighTierContext(getMetaAccess(), getConstantReflection(), getLowerer(), getReplacements(), newAssumptions, getGraphCache(), getGraphBuilderSuite(), getOptimisticOptimizations());
    }
}
