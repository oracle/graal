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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.truffle.substitutions.*;

/**
 * Custom {@link Replacements} for Truffle compilation.
 */
public final class TruffleReplacements extends ReplacementsImpl {

    private final Replacements graalReplacements;

    private TruffleReplacements(MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection, CodeCacheProvider codeCache, ForeignCallsProvider foreignCalls, LoweringProvider lowerer,
                    Assumptions assumptions, TargetDescription target, Replacements graalReplacements) {
        super(metaAccess, constantReflection, codeCache, foreignCalls, lowerer, assumptions, target.wordKind);
        this.graalReplacements = graalReplacements;
    }

    static Replacements makeInstance() {
        MetaAccessProvider metaAccess = Graal.getRequiredCapability(MetaAccessProvider.class);
        CodeCacheProvider codeCache = Graal.getRequiredCapability(CodeCacheProvider.class);
        ConstantReflectionProvider constantReflection = Graal.getRequiredCapability(ConstantReflectionProvider.class);
        ForeignCallsProvider foreignCalls = Graal.getRequiredCapability(ForeignCallsProvider.class);
        LoweringProvider lowerer = Graal.getRequiredCapability(LoweringProvider.class);
        TargetDescription targetDescription = Graal.getRequiredCapability(CodeCacheProvider.class).getTarget();
        Replacements graalReplacements = Graal.getRequiredCapability(Replacements.class);
        Replacements truffleReplacements = new TruffleReplacements(metaAccess, constantReflection, codeCache, foreignCalls, lowerer, graalReplacements.getAssumptions(), targetDescription,
                        graalReplacements);

        truffleReplacements.registerSubstitutions(CompilerAssertsSubstitutions.class);
        truffleReplacements.registerSubstitutions(CompilerDirectivesSubstitutions.class);
        truffleReplacements.registerSubstitutions(ExactMathSubstitutions.class);
        truffleReplacements.registerSubstitutions(UnexpectedResultExceptionSubstitutions.class);
        truffleReplacements.registerSubstitutions(FrameWithoutBoxingSubstitutions.class);
        truffleReplacements.registerSubstitutions(OptimizedAssumptionSubstitutions.class);
        truffleReplacements.registerSubstitutions(OptimizedCallTargetSubstitutions.class);
        truffleReplacements.registerSubstitutions(DefaultCallTargetSubstitutions.class);

        return truffleReplacements;
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
