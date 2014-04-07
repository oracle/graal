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

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.truffle.substitutions.*;

/**
 * Custom {@link Replacements} for Truffle compilation.
 */
public abstract class TruffleReplacements extends ReplacementsImpl {

    private final Replacements graalReplacements;

    protected TruffleReplacements(Providers providers, SnippetReflectionProvider snippetReflection) {
        super(providers, snippetReflection, providers.getReplacements().getAssumptions(), providers.getCodeCache().getTarget());
        this.graalReplacements = providers.getReplacements();

        registerTruffleSubstitutions();
    }

    protected void registerTruffleSubstitutions() {
        registerSubstitutions(CompilerAssertsSubstitutions.class);
        registerSubstitutions(CompilerDirectivesSubstitutions.class);
        registerSubstitutions(ExactMathSubstitutions.class);
        registerSubstitutions(OptimizedAssumptionSubstitutions.class);
        registerSubstitutions(OptimizedCallTargetSubstitutions.class);
    }

    @Override
    public StructuredGraph getSnippet(ResolvedJavaMethod method) {
        return graalReplacements.getSnippet(method);
    }

    @Override
    public StructuredGraph getMethodSubstitution(ResolvedJavaMethod method) {
        StructuredGraph graph = graalReplacements.getMethodSubstitution(method);
        if (graph == null) {
            return super.getMethodSubstitution(method);
        }
        return graph;
    }

    @Override
    public Class<? extends FixedWithNextNode> getMacroSubstitution(ResolvedJavaMethod method) {
        Class<? extends FixedWithNextNode> clazz = graalReplacements.getMacroSubstitution(method);
        if (clazz == null) {
            return super.getMacroSubstitution(method);
        }
        return clazz;
    }

    @Override
    public Collection<ResolvedJavaMethod> getAllReplacements() {
        throw GraalInternalError.shouldNotReachHere();
    }

    @Override
    public boolean isForcedSubstitution(ResolvedJavaMethod method) {
        return graalReplacements.isForcedSubstitution(method) || super.isForcedSubstitution(method);
    }

    @Override
    public void registerSnippetTemplateCache(SnippetTemplateCache templates) {
        throw GraalInternalError.shouldNotReachHere();
    }

    @Override
    public <T extends SnippetTemplateCache> T getSnippetTemplateCache(Class<T> templatesClass) {
        return graalReplacements.getSnippetTemplateCache(templatesClass);
    }
}
