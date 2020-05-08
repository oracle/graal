/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.test;

import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.VerifyPhase;
import org.graalvm.libgraal.jni.HotSpotToSVMScope;
import org.graalvm.libgraal.jni.JNI;
import org.graalvm.libgraal.jni.JNI.JObject;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Checks the invariant from {@link HotSpotToSVMScope} about object return values being passed
 * outside the scope with {@link HotSpotToSVMScope#setObjectResult(JObject)} and
 * {@link HotSpotToSVMScope#getObjectResult()}.
 */
public class VerifyHotSpotToSVMScope extends VerifyPhase<CoreProviders> {

    private static ValueNode unPi(ValueNode node) {
        if (node instanceof PiNode) {
            PiNode pi = (PiNode) node;
            return unPi(pi.object());
        }
        return node;
    }

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        MetaAccessProvider metaAccess = context.getMetaAccess();
        final ResolvedJavaType hotSpotToSVMScopeType = metaAccess.lookupJavaType(HotSpotToSVMScope.class);
        final ResolvedJavaType jobjectType = metaAccess.lookupJavaType(JNI.JObject.class);

        ResolvedJavaMethod caller = graph.method();

        JavaType returnType = caller.getSignature().getReturnType(caller.getDeclaringClass());
        if (returnType instanceof ResolvedJavaType && jobjectType.isAssignableFrom((ResolvedJavaType) returnType)) {
            if (usesHotSpotToSVMScope(graph, hotSpotToSVMScopeType)) {
                for (ReturnNode node : graph.getNodes().filter(ReturnNode.class)) {
                    ValueNode result = unPi(node.result());
                    if (result instanceof Invoke) {
                        Invoke invoke = (Invoke) result;
                        ResolvedJavaMethod callee = invoke.callTarget().targetMethod();
                        if (callee.getDeclaringClass().equals(hotSpotToSVMScopeType) && callee.getName().equals("getObjectResult")) {
                            continue;
                        }
                    }
                    throw new VerificationError("%s: Return value of type %s must be transferred " +
                                    "out of a %s with setObjectResult() and returned by getObjectResult()",
                                    caller.format("%H.%n(%p)"),
                                    JNI.JObject.class.getSimpleName(),
                                    HotSpotToSVMScope.class.getSimpleName());
                }
            }
        }
    }

    private static boolean usesHotSpotToSVMScope(StructuredGraph graph, final ResolvedJavaType hotSpotToSVMScopeType) {
        for (NewInstanceNode node : graph.getNodes().filter(NewInstanceNode.class)) {
            ResolvedJavaType type = node.instanceClass();
            if (hotSpotToSVMScopeType.isAssignableFrom(type)) {
                return true;
            }
        }
        return false;
    }
}
