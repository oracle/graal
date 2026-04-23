/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.phases;

import com.oracle.graal.pointsto.meta.AnalysisType;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode.BytecodeExceptionKind;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Attempts to devirtualize interface calls similar to
 * {@link jdk.graal.compiler.nodes.java.MethodCallTargetNode#tryDevirtualizeInterfaceCall}.
 * 
 * If speculations are in general not possible, devirtualizing interface calls needs to be done
 * during graph building because it is necessary to introduce a type check that may throw a
 * ClassCastException.
 *
 * This node plugin therefore intercepts interface invocations, makes use of
 * {@link ResolvedJavaType#getSingleImplementor} and {@link ResolvedJavaType#resolveConcreteMethod},
 * and if possible, replaces the interface call with a virtual (or special) call.
 */
public final class DevirtualizeInterfaceCallPlugin implements NodePlugin {

    @Override
    public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        /*
         * We rely on the parser to emit an explicit receiver null check before this plugin runs.
         * Without explicit exceptions, a null receiver would be translated into ClassCastException
         * by the instanceof check below instead of the required NullPointerException.
         */
        if (!b.needsExplicitException()) {
            return false;
        }

        if (b.getInvokeKind() != InvokeKind.Interface) {
            return false;
        }
        ResolvedJavaType referencedType = b.getInvokeReferencedType();
        if (referencedType == null) {
            return false;
        }

        /*
         * If assumptions may be recorded, there is no benefit in devirtualizing an interface call
         * during graph building. This will then be done as part of the MethodCallTargetNode
         * optimization.
         */
        if (b.getAssumptions() != null) {
            return false;
        }

        assert referencedType instanceof AnalysisType;

        /*
         * We need to check if the declaring class of the method is an interface to avoid recursive
         * simplification for virtual interface methods calls.
         */
        ResolvedJavaType declaredReceiverType = method.getDeclaringClass();
        if (!declaredReceiverType.isInterface()) {
            return false;
        }

        /*
         * If singleImplementor is equal to declaredReceiverType it means that there are multiple
         * implementors.
         */
        ResolvedJavaType singleImplementor = referencedType.getSingleImplementor();
        if (singleImplementor == null || singleImplementor.equals(declaredReceiverType)) {
            return false;
        }

        ResolvedJavaMethod singleImplementorMethod = singleImplementor.resolveConcreteMethod(method, b.getMethod().getDeclaringClass());
        if (singleImplementorMethod == null) {
            return false;
        }

        TypeReference singleImplementorTypeReference = TypeReference.createWithoutAssumptions(singleImplementor);

        /*
         * The created node structure must fulfill two properties: 1) it must be ensured that the
         * receiver implements the interface (i.e. 'referencedType'). The verifier does not prove
         * this so a dynamic check is required. 2) it must be ensured that there is still only one
         * implementor of this interface, i.e. that the correct method will be called. Because an
         * instanceof check is needed anyway, both properties can be verified by checking if the
         * receiver is an instance of the single implementor.
         */
        LogicNode condition = InstanceOfNode.create(singleImplementorTypeReference, args[0]);
        if (condition.isContradiction()) {
            return false;
        }

        AnalysisType analysisSingleImplementor = (AnalysisType) singleImplementor;
        if (analysisSingleImplementor.isInstanceClass() && !analysisSingleImplementor.isAbstract()) {
            analysisSingleImplementor.registerAsInstantiated("parse-time devirtualized single implementor");
        }

        b.append(condition);
        JavaConstant javaClass = b.getConstantReflection().asJavaClass(referencedType);
        ConstantNode cls = b.add(ConstantNode.forConstant(StampFactory.forKind(JavaKind.Object), javaClass, b.getMetaAccess(), b.getGraph()));
        AbstractBeginNode passingSuccessor = b.emitBytecodeExceptionCheck(condition, true, BytecodeExceptionKind.CLASS_CAST, args[0], cls);
        args[0] = b.add(PiNode.create(args[0], StampFactory.objectNonNull(singleImplementorTypeReference), passingSuccessor));

        /*
         * The singleImplementor may not be a leaf type. In this case, we still need to create a
         * virtual invocation. This is fine because we can already turn an interface call into a
         * virtual call and if there is no other override of the target method, subsequent
         * optimizations will be able to further devirtualize the call.
         */
        InvokeKind invokeKind;
        if (singleImplementorTypeReference.isExact()) {
            invokeKind = InvokeKind.Special;
        } else {
            invokeKind = InvokeKind.Virtual;
        }
        b.handleReplacedInvoke(invokeKind, singleImplementorMethod, args, false);
        return true;
    }
}
