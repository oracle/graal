/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements;

import com.oracle.graal.graph.*;
import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.CallTargetNode.InvokeKind;
import com.oracle.graal.replacements.nodes.*;
import com.oracle.jvmci.meta.*;
import com.oracle.jvmci.meta.MethodHandleAccessProvider.IntrinsicMethod;

public class MethodHandleInvocationPlugin implements GenericInvocationPlugin {
    private final MethodHandleAccessProvider methodHandleAccess;
    private final GenericInvocationPlugin delegate;

    public MethodHandleInvocationPlugin(MethodHandleAccessProvider methodHandleAccess, GenericInvocationPlugin delegate) {
        this.methodHandleAccess = methodHandleAccess;
        this.delegate = delegate;
    }

    @Override
    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        IntrinsicMethod intrinsicMethod = methodHandleAccess.lookupMethodHandleIntrinsic(method);
        if (intrinsicMethod != null) {
            InvokeKind invokeKind = b.getInvokeKind();
            if (invokeKind != InvokeKind.Static) {
                args[0] = b.nullCheckedValue(args[0]);
            }
            JavaType invokeReturnType = b.getInvokeReturnType();
            InvokeNode invoke = MethodHandleNode.tryResolveTargetInvoke(b.getAssumptions(), b.getConstantReflection().getMethodHandleAccess(), intrinsicMethod, method, b.bci(), invokeReturnType, args);
            if (invoke == null) {
                MethodHandleNode methodHandleNode = new MethodHandleNode(intrinsicMethod, invokeKind, method, b.bci(), invokeReturnType, args);
                if (invokeReturnType.getKind() == Kind.Void) {
                    b.add(methodHandleNode);
                } else {
                    b.addPush(invokeReturnType.getKind(), methodHandleNode);
                }
            } else {
                CallTargetNode callTarget = invoke.callTarget();
                NodeInputList<ValueNode> argumentsList = callTarget.arguments();
                ValueNode[] newArgs = argumentsList.toArray(new ValueNode[argumentsList.size()]);
                for (ValueNode arg : newArgs) {
                    b.recursiveAppend(arg);
                }
                b.handleReplacedInvoke(invoke.getInvokeKind(), callTarget.targetMethod(), newArgs);
            }
            return true;
        }
        return delegate.apply(b, method, args);
    }
}
