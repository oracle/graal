/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.MalformedParametersException;
import java.lang.reflect.Method;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.VerifyPhase;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod.Parameter;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * {@link Node#getOptions()} is unsafe for use during canonicalization so try to verify that it
 * isn't used when a {@link CanonicalizerTool} is available in the arguments. This is slightly more
 * general but since there are several canonical methods with varying signatures this covers more
 * cases.
 */
public class VerifyGetOptionsUsage extends VerifyPhase<CoreProviders> {
    static Method lookupMethod(Class<?> klass, String name) {
        for (Method m : klass.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new InternalError();
    }

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        MetaAccessProvider metaAccess = context.getMetaAccess();
        ResolvedJavaType canonicalizerToolClass = metaAccess.lookupJavaType(CanonicalizerTool.class);
        boolean hasTool = false;
        ResolvedJavaMethod method = graph.method();
        try {
            Parameter[] parameters = method.getParameters();
            if (parameters != null) {
                for (ResolvedJavaMethod.Parameter parameter : parameters) {
                    if (parameter.getType().getName().equals(canonicalizerToolClass.getName())) {
                        hasTool = true;
                        break;
                    }
                }
            }
        } catch (MalformedParametersException e) {
            // Lambdas sometimes have malformed parameters so ignore this.
        }
        if (hasTool) {
            ResolvedJavaMethod getOptionsMethod = metaAccess.lookupJavaMethod(lookupMethod(Node.class, "getOptions"));
            for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
                ResolvedJavaMethod callee = t.targetMethod();
                if (callee.equals(getOptionsMethod)) {
                    if (hasTool) {
                        throw new VerificationError("Must use CanonicalizerTool.getOptions() instead of Node.getOptions() in method '%s' of class '%s'.",
                                        method.getName(), method.getDeclaringClass().getName());
                    }
                }
            }
        }
    }

}
