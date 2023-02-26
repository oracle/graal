/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.Annotation;

import org.graalvm.collections.EconomicSet;
import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.api.replacements.Snippet.NonNullParameter;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.ProfileData.BranchProbabilityData;
import org.graalvm.compiler.nodes.ProfileData.ProfileSource;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.VerifyPhase;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class VerifySnippetProbabilities extends VerifyPhase<CoreProviders> {

    private static final Object[] KNOWN_PROFILE_INTRINSICS = {
                    BranchProbabilityNode.class, "probability", //
                    BranchProbabilityNode.class, "unknownProbability", //
                    GraalDirectives.class, "injectBranchProbability", //
                    GraalDirectives.class, "injectIterationCount"};

    @Override
    public boolean checkContract() {
        return false;
    }

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        if (!graph.isSubstitution()) {
            // only process snippets
            return;
        }
        ResolvedJavaMethod method = graph.method();
        Snippet snippet = method.getAnnotation(Snippet.class);
        if (snippet.allowMissingProbabilities()) {
            // no checks possible
            return;
        }
        EconomicSet<ResolvedJavaMethod> knownIntrinsicMethods = EconomicSet.create();
        for (int i = 0; i < KNOWN_PROFILE_INTRINSICS.length; i += 2) {
            Class<?> receiverClass = (Class<?>) KNOWN_PROFILE_INTRINSICS[i];
            String methodName = (String) KNOWN_PROFILE_INTRINSICS[i + 1];
            ResolvedJavaType type = context.getMetaAccess().lookupJavaType(receiverClass);
            for (ResolvedJavaMethod typeMethod : type.getDeclaredMethods()) {
                if (typeMethod.getName().contains(methodName)) {
                    knownIntrinsicMethods.add(typeMethod);
                }
            }
        }
        boolean[] specialParameters = new boolean[method.getParameters().length];
        Annotation[][] parameterAnnotations = graph.method().getParameterAnnotations();
        for (int i = 0; i < parameterAnnotations.length; i++) {
            for (Annotation a : parameterAnnotations[i]) {
                Class<? extends Annotation> annotationType = a.annotationType();
                if (annotationType == ConstantParameter.class || annotationType == NonNullParameter.class) {
                    specialParameters[i] = true;
                }
            }
        }
        for (Node n : graph.getNodes()) {
            if (n instanceof IfNode) {
                IfNode ifNode = (IfNode) n;
                BranchProbabilityData profile = ifNode.getProfileData();
                if (!ProfileSource.isTrusted(profile.getProfileSource())) {
                    if (isExplodedLoopExit(ifNode)) {
                        continue;
                    }
                    LogicNode ln = ifNode.condition();
                    boolean found = false;
                    outer: for (Node input : ln.inputs()) {
                        if (input instanceof Invoke) {
                            Invoke invoke = (Invoke) input;
                            CallTargetNode mc = invoke.callTarget();
                            if (mc.invokeKind().isDirect()) {
                                ResolvedJavaMethod targetMethod = mc.targetMethod();
                                if (knownIntrinsicMethods.contains(targetMethod)) {
                                    found = true;
                                    break;
                                } else if (targetMethod.getAnnotation(Fold.class) != null) {
                                    // will fold the entire if away, not necessary to verify
                                    found = true;
                                    break;
                                } else {
                                    // some snippets have complex semantics separated in different
                                    // method in the same class, allow such patterns they will be
                                    // fully inlined
                                    if (targetMethod.getDeclaringClass().equals(method.getDeclaringClass())) {
                                        found = true;
                                        break;
                                    }
                                    // reading folded options in snippets may require some unboxing
                                    for (Node argument : mc.arguments()) {
                                        ValueNode pureArg = GraphUtil.unproxify((ValueNode) argument);
                                        if (pureArg instanceof Invoke && ((Invoke) pureArg).getTargetMethod().getAnnotation(Fold.class) != null) {
                                            found = true;
                                            break outer;
                                        }
                                    }
                                }
                            } else {
                                // abstract / interface methods called in a snippet, most likely due
                                // to overriden snippet logic that folds later, ignore
                                found = true;
                                break;
                            }
                        } else if (input instanceof ParameterNode) {
                            if (specialParameters[((ParameterNode) input).index()]) {
                                // constant or non null parameter, ignore
                                found = true;
                                break;
                            }
                        }
                    }
                    if (!found) {
                        throw new VerificationError("Node %s in snippet %s has unknown probability %s (nsp %s) and does not call" +
                                        "BranchProbabilityNode.probabiliy/GraalDirectives.inject<> on any of it's condition inputs.",
                                        ifNode, graph, profile,
                                        ifNode.getNodeSourcePosition());

                    }
                }
            }
        }
    }

    private static boolean isExplodedLoopExit(IfNode ifNode) {
        LoopExitNode lex = null;
        if (ifNode.trueSuccessor() instanceof LoopExitNode) {
            lex = (LoopExitNode) ifNode.trueSuccessor();
        } else if (ifNode.falseSuccessor() instanceof LoopExitNode) {
            lex = (LoopExitNode) ifNode.falseSuccessor();
        }
        if (lex != null) {
            for (FixedNode f : GraphUtil.predecessorIterable(lex.loopBegin().forwardEnd())) {
                if (f instanceof Invoke) {
                    if (((Invoke) f).callTarget().targetMethod().getName().equals("explodeLoop")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

}
