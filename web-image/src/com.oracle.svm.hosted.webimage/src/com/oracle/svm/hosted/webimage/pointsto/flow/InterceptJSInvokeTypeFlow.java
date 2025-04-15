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

package com.oracle.svm.hosted.webimage.pointsto.flow;

import java.util.Optional;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives;
import com.oracle.svm.hosted.webimage.codegen.node.InterceptJSInvokeNode;
import com.oracle.svm.hosted.webimage.util.ReflectUtil;

import jdk.vm.ci.code.BytecodePosition;

/**
 * Type flow that corresponds to the {@link InterceptJSInvokeNode} node.
 */
public class InterceptJSInvokeTypeFlow extends TypeFlow<BytecodePosition> {
    private final AnalysisMethod targetMethod;
    private final TypeFlow<?>[] arguments;

    public InterceptJSInvokeTypeFlow(BytecodePosition source, AnalysisMethod targetMethod, TypeFlow<?>[] arguments) {
        super(source, null);
        this.targetMethod = targetMethod;
        this.arguments = arguments;
    }

    private InterceptJSInvokeTypeFlow(PointsToAnalysis bb, MethodFlowsGraph methodFlows, InterceptJSInvokeTypeFlow original) {
        super(original, methodFlows);
        this.targetMethod = original.targetMethod;
        this.arguments = new TypeFlow<?>[original.arguments.length];
        for (int i = 0; i < this.arguments.length; i++) {
            if (original.arguments[i] != null) {
                this.arguments[i] = methodFlows.lookupCloneOf(bb, original.arguments[i]);
            }
        }
    }

    @Override
    public TypeFlow<BytecodePosition> copy(PointsToAnalysis bb, MethodFlowsGraph methodFlows) {
        return new InterceptJSInvokeTypeFlow(bb, methodFlows, this);
    }

    @Override
    public boolean canSaturate(PointsToAnalysis bb) {
        return false;
    }

    @Override
    protected void onFlowEnabled(PointsToAnalysis bb) {
        super.onFlowEnabled(bb);
        bb.postTask(() -> onObservedUpdate(bb));
    }

    @Override
    public void onObservedUpdate(PointsToAnalysis bb) {
        if (!isFlowEnabled()) {
            return;
        }
        // SAM metadata must be preserved if a functional interface implementation reaches the
        // callsite.
        for (AnalysisObject object : getState().objects(bb)) {
            Optional<AnalysisMethod> sam = ReflectUtil.singleAbstractMethodForClass(bb.getMetaAccess(), object.type());
            if (sam.isPresent()) {
                bb.addRootMethod(sam.get(), false, "SAM method to JS, registered in " + InterceptJSInvokeTypeFlow.class);
                SubstrateCompilationDirectives.singleton().registerForcedCompilation(sam.get());
            }
        }
    }

    @Override
    public void onObservedSaturated(PointsToAnalysis bb, TypeFlow<?> observed) {
        // If the input type is saturated, then it must be replaced with the
        // all-instantiated-type-flow of the corresponding type parameter.
        for (int i = 0; i < this.arguments.length; i++) {
            if (this.arguments[i] == observed) {
                TypeFlow<?> paramTypeFlow;
                if (targetMethod.isStatic()) {
                    paramTypeFlow = targetMethod.getSignature().getParameterType(i).getTypeFlow(bb, true);
                } else {
                    if (i == 0) {
                        paramTypeFlow = targetMethod.getDeclaringClass().getTypeFlow(bb, true);
                    } else {
                        paramTypeFlow = targetMethod.getSignature().getParameterType(i - 1).getTypeFlow(bb, true);
                    }
                }
                this.arguments[i] = paramTypeFlow;
                this.arguments[i].addUse(bb, this);
                this.arguments[i].addObserver(bb, this);
            }
        }
    }
}
