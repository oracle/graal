/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.reflect.hosted;

// Checkstyle: allow reflection

import java.lang.reflect.Constructor;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;

import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.hosted.phases.HostedGraphKit;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class ReflectiveNewInstanceMethod extends ReflectionMethod {

    private final Constructor<?> constructor;

    public ReflectiveNewInstanceMethod(String name, ResolvedJavaMethod prototype, Constructor<?> constructor) {
        super(name, prototype);
        this.constructor = constructor;
    }

    @Override
    public StructuredGraph buildGraph(DebugContext ctx, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        HostedGraphKit graphKit = new HostedGraphKit(ctx, providers, method);

        ResolvedJavaType type = providers.getMetaAccess().lookupJavaType(constructor.getDeclaringClass());

        graphKit.emitEnsureInitializedCall(type);

        ResolvedJavaMethod cons = providers.getMetaAccess().lookupJavaMethod(constructor);
        Class<?>[] argTypes = constructor.getParameterTypes();

        ValueNode ret = graphKit.append(createNewInstanceNode(type));

        ValueNode[] args = new ValueNode[argTypes.length + 1];
        args[0] = ret;

        ValueNode argumentArray = graphKit.loadLocal(0, JavaKind.Object);
        fillArgsArray(graphKit, argumentArray, 1, args, argTypes);

        createJavaCall(graphKit, cons, ret, args);

        return graphKit.finalizeGraph();
    }

    protected void createJavaCall(HostedGraphKit graphKit, ResolvedJavaMethod cons, ValueNode ret, ValueNode[] args) {
        graphKit.createJavaCallWithException(InvokeKind.Special, cons, args);

        graphKit.noExceptionPart();
        graphKit.createReturn(ret, JavaKind.Object);

        graphKit.exceptionPart();
        graphKit.throwInvocationTargetException(graphKit.exceptionObject());

        graphKit.endInvokeWithException();
    }

    protected ValueNode createNewInstanceNode(ResolvedJavaType type) {
        return new NewInstanceNode(type, true);
    }
}
