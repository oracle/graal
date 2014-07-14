/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.java;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.type.*;

public class MethodCallTargetNode extends CallTargetNode implements IterableNodeType, Canonicalizable {

    private final JavaType returnType;
    private InvokeKind invokeKind;

    /**
     * @param arguments
     */
    public MethodCallTargetNode(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] arguments, JavaType returnType) {
        super(arguments, targetMethod);
        this.invokeKind = invokeKind;
        this.returnType = returnType;
    }

    public InvokeKind invokeKind() {
        return invokeKind;
    }

    public void setInvokeKind(InvokeKind kind) {
        this.invokeKind = kind;
    }

    /**
     * Gets the instruction that produces the receiver object for this invocation, if any.
     *
     * @return the instruction that produces the receiver object for this invocation if any,
     *         {@code null} if this invocation does not take a receiver object
     */
    public ValueNode receiver() {
        return isStatic() ? null : arguments().get(0);
    }

    /**
     * Checks whether this is an invocation of a static method.
     *
     * @return {@code true} if the invocation is a static invocation
     */
    public boolean isStatic() {
        return invokeKind() == InvokeKind.Static;
    }

    public Kind returnKind() {
        return targetMethod().getSignature().getReturnKind();
    }

    public Invoke invoke() {
        return (Invoke) this.usages().first();
    }

    @Override
    public boolean verify() {
        assert usages().count() <= 1 : "call target may only be used by a single invoke";
        for (Node n : usages()) {
            assertTrue(n instanceof Invoke, "call target can only be used from an invoke (%s)", n);
        }
        if (invokeKind == InvokeKind.Special || invokeKind == InvokeKind.Static) {
            assertFalse(targetMethod().isAbstract(), "special calls or static calls are only allowed for concrete methods (%s)", targetMethod());
        }
        if (invokeKind == InvokeKind.Static) {
            assertTrue(targetMethod().isStatic(), "static calls are only allowed for static methods (%s)", targetMethod());
        } else {
            assertFalse(targetMethod().isStatic(), "static calls are only allowed for non-static methods (%s)", targetMethod());
        }
        return super.verify();
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Long) {
            return super.toString(Verbosity.Short) + "(" + targetMethod() + ")";
        } else {
            return super.toString(verbosity);
        }
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (invokeKind == InvokeKind.Interface || invokeKind == InvokeKind.Virtual) {
            // attempt to devirtualize the call

            // check for trivial cases (e.g. final methods, nonvirtual methods)
            if (targetMethod().canBeStaticallyBound()) {
                invokeKind = InvokeKind.Special;
                return this;
            }

            // check if the type of the receiver can narrow the result
            ValueNode receiver = receiver();
            ResolvedJavaType type = StampTool.typeOrNull(receiver);
            if (type != null && (invoke().stateAfter() != null || invoke().stateDuring() != null)) {
                /*
                 * either the holder class is exact, or the receiver object has an exact type, or
                 * it's an array type
                 */
                ResolvedJavaMethod resolvedMethod = type.resolveMethod(targetMethod(), invoke().getContextType());
                if (resolvedMethod != null && (resolvedMethod.canBeStaticallyBound() || StampTool.isExactType(receiver) || type.isArray())) {
                    invokeKind = InvokeKind.Special;
                    setTargetMethod(resolvedMethod);
                    return this;
                }
                if (tool.assumptions() != null && tool.assumptions().useOptimisticAssumptions()) {
                    ResolvedJavaType uniqueConcreteType = type.findUniqueConcreteSubtype();
                    if (uniqueConcreteType != null) {
                        ResolvedJavaMethod methodFromUniqueType = uniqueConcreteType.resolveMethod(targetMethod(), invoke().getContextType());
                        if (methodFromUniqueType != null) {
                            tool.assumptions().recordConcreteSubtype(type, uniqueConcreteType);
                            invokeKind = InvokeKind.Special;
                            setTargetMethod(methodFromUniqueType);
                            return this;
                        }
                    }

                    ResolvedJavaMethod uniqueConcreteMethod = type.findUniqueConcreteMethod(targetMethod());
                    if (uniqueConcreteMethod != null) {
                        tool.assumptions().recordConcreteMethod(targetMethod(), type, uniqueConcreteMethod);
                        invokeKind = InvokeKind.Special;
                        setTargetMethod(uniqueConcreteMethod);
                        return this;
                    }
                }
            }
        }
        return this;
    }

    @Override
    public Stamp returnStamp() {
        Kind returnKind = targetMethod().getSignature().getReturnKind();
        if (returnKind == Kind.Object && returnType instanceof ResolvedJavaType) {
            return StampFactory.declared((ResolvedJavaType) returnType);
        } else {
            return StampFactory.forKind(returnKind);
        }
    }

    public JavaType returnType() {
        return returnType;
    }

    @Override
    public String targetName() {
        if (targetMethod() == null) {
            return "??Invalid!";
        }
        return targetMethod().format("%h.%n");
    }

    public static MethodCallTargetNode find(StructuredGraph graph, ResolvedJavaMethod method) {
        for (MethodCallTargetNode target : graph.getNodes(MethodCallTargetNode.class)) {
            if (target.targetMethod().equals(method)) {
                return target;
            }
        }
        return null;
    }
}
