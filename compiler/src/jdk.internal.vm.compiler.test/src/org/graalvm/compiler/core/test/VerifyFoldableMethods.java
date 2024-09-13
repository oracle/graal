/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.nodes.PluginReplacementNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.GeneratedInvocationPlugin;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.VerifyPhase;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Verifies that all {@link Fold} annotated methods have at least one caller from non-generated
 * code. Ideally, the class should verify that foldable methods are only called from snippets but
 * that requires a more global analysis (i.e., to know whether a caller is used within a snippet).
 */
public class VerifyFoldableMethods extends VerifyPhase<CoreProviders> {

    @Override
    public boolean checkContract() {
        return false;
    }

    /**
     * Map from a foldable method to one of its callers. The absence of a caller is represented a
     * foldable method mapping to itself.
     */
    private final Map<ResolvedJavaMethod, ResolvedJavaMethod> foldableCallers = new ConcurrentHashMap<>();

    /*
     * Super types or interfaces for generated classes. Calls from methods in these classes are
     * ignored.
     */
    Set<ResolvedJavaType> generatedClassSupertypes;

    /**
     * Determines if {@code method} is in a generated class.
     */
    private boolean isGenerated(ResolvedJavaMethod method, CoreProviders context) {
        if (generatedClassSupertypes == null) {
            generatedClassSupertypes = Set.of(
                            context.getMetaAccess().lookupJavaType(GeneratedInvocationPlugin.class),
                            context.getMetaAccess().lookupJavaType(PluginReplacementNode.ReplacementFunction.class));
        }
        ResolvedJavaType declaringClass = method.getDeclaringClass();
        for (ResolvedJavaType t : generatedClassSupertypes) {
            if (t.isAssignableFrom(declaringClass)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        ResolvedJavaMethod method = graph.method();
        if (method.getAnnotation(Fold.class) != null) {
            foldableCallers.putIfAbsent(method, method);
        } else {
            if (!isGenerated(method, context)) {
                for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
                    ResolvedJavaMethod callee = t.targetMethod();
                    if (callee.getAnnotation(Fold.class) != null) {
                        foldableCallers.put(callee, method);
                    }
                }
            }
        }
    }

    public void finish() {
        String uncalled = foldableCallers.entrySet().stream()//
                        .filter(e -> e.getValue() == e.getKey())//
                        .map(e -> e.getKey().format("%H.%n(%p)"))//
                        .collect(Collectors.joining(System.lineSeparator() + "  "));
        if (!uncalled.isEmpty()) {
            throw new VerificationError(String.format("Methods annotated with @" + Fold.class.getSimpleName() + " appear to have no usages:%n  %s", uncalled));
        }
    }
}
