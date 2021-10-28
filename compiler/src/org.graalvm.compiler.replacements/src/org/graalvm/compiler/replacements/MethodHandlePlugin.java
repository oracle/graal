/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements;

import static org.graalvm.compiler.core.common.GraalOptions.MaximumRecursiveInlining;

import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.replacements.nodes.MacroNode.MacroParams;
import org.graalvm.compiler.replacements.nodes.MethodHandleNode;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.MethodHandleAccessProvider.IntrinsicMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class MethodHandlePlugin implements NodePlugin {
    private final MethodHandleAccessProvider methodHandleAccess;
    private final boolean safeForDeoptimization;

    public MethodHandlePlugin(MethodHandleAccessProvider methodHandleAccess, boolean safeForDeoptimization) {
        this.methodHandleAccess = methodHandleAccess;
        this.safeForDeoptimization = safeForDeoptimization;
    }

    @Override
    public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        IntrinsicMethod intrinsicMethod = methodHandleAccess.lookupMethodHandleIntrinsic(method);
        if (intrinsicMethod != null) {
            InvokeKind invokeKind = b.getInvokeKind();
            if (invokeKind != InvokeKind.Static) {
                args[0] = b.nullCheckedValue(args[0]);
            }
            StampPair invokeReturnStamp = b.getInvokeReturnStamp(b.getAssumptions());
            MethodHandleNode.GraphAdder adder = new MethodHandleNode.GraphAdder(b.getGraph()) {
                @Override
                public <T extends ValueNode> T add(T node) {
                    return b.add(node);
                }
            };
            InvokeNode invoke = MethodHandleNode.tryResolveTargetInvoke(adder, methodHandleAccess, intrinsicMethod, method, b.bci(), invokeReturnStamp, args);
            if (invoke == null) {
                MethodHandleNode methodHandleNode = new MethodHandleNode(intrinsicMethod, MacroParams.of(invokeKind, b.getMethod(), method, b.bci(), invokeReturnStamp, args));
                if (invokeReturnStamp.getTrustedStamp().getStackKind() == JavaKind.Void) {
                    b.add(methodHandleNode);
                } else {
                    b.addPush(invokeReturnStamp.getTrustedStamp().getStackKind(), methodHandleNode);
                }
            } else {
                CallTargetNode callTarget = invoke.callTarget();
                NodeInputList<ValueNode> argumentsList = callTarget.arguments();
                for (int i = 0; i < argumentsList.size(); ++i) {
                    argumentsList.initialize(i, b.append(argumentsList.get(i)));
                }

                boolean inlineEverything = false;
                if (safeForDeoptimization) {
                    // If a MemberName suffix argument is dropped, the replaced call cannot
                    // deoptimized since the necessary frame state cannot be reconstructed.
                    // As such, it needs to recursively inline everything.
                    inlineEverything = args.length != argumentsList.size();
                }
                ResolvedJavaMethod targetMethod = callTarget.targetMethod();
                if (inlineEverything && !targetMethod.hasBytecodes() && !b.getReplacements().hasSubstitution(targetMethod)) {
                    // we need to force-inline but we can not, leave the invoke as-is
                    return false;
                }

                int recursionDepth = b.recursiveInliningDepth(targetMethod);
                int maxRecursionDepth = MaximumRecursiveInlining.getValue(b.getOptions());
                if (recursionDepth > maxRecursionDepth) {
                    return false;
                }

                Invoke newInvoke = b.handleReplacedInvoke(invoke.getInvokeKind(), targetMethod, argumentsList.toArray(new ValueNode[argumentsList.size()]), inlineEverything);
                if (newInvoke != null && !newInvoke.callTarget().equals(invoke.callTarget()) && newInvoke.asFixedNode().isAlive()) {
                    // In the case where the invoke is not inlined, replace its call target with the
                    // special ResolvedMethodHandleCallTargetNode.
                    newInvoke.callTarget().replaceAndDelete(b.append(invoke.callTarget()));
                    return true;
                }
                /*
                 * After handleReplacedInvoke, a return type according to the signature of
                 * targetMethod has been pushed. That can be different than the type expected by the
                 * method handle invoke. Since there cannot be any implicit type conversion, the
                 * only safe option actually is that the return type is not used at all. If there is
                 * any other expected return type, the bytecodes are wrong. The JavaDoc of
                 * MethodHandle.invokeBasic states that this "could crash the JVM", so bailing out
                 * of compilation seems like a good idea.
                 */
                JavaKind invokeReturnKind = invokeReturnStamp.getTrustedStamp().getStackKind();
                JavaKind targetMethodReturnKind = targetMethod.getSignature().getReturnKind().getStackKind();
                if (invokeReturnKind != targetMethodReturnKind) {
                    b.pop(targetMethodReturnKind);
                    if (invokeReturnKind != JavaKind.Void) {
                        throw b.bailout("Cannot do any type conversion when invoking method handle, so return value must remain popped");
                    }
                }
            }
            return true;
        }
        return false;
    }
}
