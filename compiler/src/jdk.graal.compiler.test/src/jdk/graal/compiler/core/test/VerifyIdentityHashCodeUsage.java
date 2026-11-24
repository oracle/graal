/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import jdk.graal.compiler.core.test.CheckGraalInvariants.InvariantsTool;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.replacements.nodes.IdentityHashCodeNode;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Keeps compiler identity-hash folding in the canonicalization path that checks the compilation
 * policy. Constant-reflection implementations may delegate the raw query to another provider.
 * VM invariant tools may allow specific non-folding queries, such as image-heap serialization.
 */
public class VerifyIdentityHashCodeUsage extends VerifyPhase<CoreProviders> {
    private final InvariantsTool tool;

    public VerifyIdentityHashCodeUsage(InvariantsTool tool) {
        this.tool = tool;
    }

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        ResolvedJavaType reflectionProviderType = context.getMetaAccess().lookupJavaType(ConstantReflectionProvider.class);
        ResolvedJavaMethod caller = graph.method();
        if (caller.getDeclaringClass().equals(context.getMetaAccess().lookupJavaType(IdentityHashCodeNode.class)) && caller.isStatic() && caller.getName().equals("canonical")) {
            return;
        }
        if (isIdentityHashCode(caller, reflectionProviderType)) {
            // Provider delegation does not itself fold an operation into a compiler graph.
            return;
        }
        for (MethodCallTargetNode call : graph.getNodes(MethodCallTargetNode.TYPE)) {
            if (isIdentityHashCode(call.targetMethod(), reflectionProviderType) && !tool.isIdentityHashCodeQueryAllowed(caller)) {
                throw new VerificationError(call, "Identity-hash folding must use IdentityHashCodeNode canonicalization so that the compilation policy is checked.");
            }
        }
    }

    private static boolean isIdentityHashCode(ResolvedJavaMethod method, ResolvedJavaType reflectionProviderType) {
        return !method.isStatic() && reflectionProviderType.isAssignableFrom(method.getDeclaringClass()) &&
                        method.format("%n(%P):%R").equals("identityHashCode(jdk.vm.ci.meta.JavaConstant):int");
    }
}
