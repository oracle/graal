/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.code;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.hosted.analysis.NativeImagePointsToAnalysis;
import com.oracle.svm.hosted.phases.HostedGraphKit;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.java.AbstractNewObjectNode;
import jdk.graal.compiler.nodes.java.NewInstanceNode;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * When interface/virtual call resolution via JVMCI does not return a concrete implementation method
 * for a concrete implementation type, then most likely some classes are in an incompatible state,
 * i.e., a class was compiled against a different version of a dependent class than the class we got
 * on the class path. Since we use vtable calls for all interface/virtual method calls, we cannot
 * just leave such unresolved methods as "no method is invoked" in the static analysis and "null" in
 * the vtable: that would lead to wrong static analysis results, wrong devirtualizations, and/or
 * vtable calls to uninitialized vtable slots.
 *
 * The solution is to always resolve a concrete synthetic fallback method, which then throws the
 * correct error at run time. This "fallback resolution" is implemented in
 * {@link NativeImagePointsToAnalysis#fallbackResolveConcreteMethod}.
 */
public final class IncompatibleClassChangeFallbackMethod extends NonBytecodeMethod {

    private final ResolvedJavaMethod original;
    private final Class<? extends IncompatibleClassChangeError> resolutionError;

    public IncompatibleClassChangeFallbackMethod(ResolvedJavaType declaringClass, ResolvedJavaMethod original, Class<? extends IncompatibleClassChangeError> resolutionError) {
        super(original.getName(), false, declaringClass, original.getSignature(), original.getConstantPool());
        this.original = original;
        this.resolutionError = resolutionError;
    }

    public ResolvedJavaMethod getOriginal() {
        return original;
    }

    @Override
    public ResolvedJavaMethod unwrapTowardsOriginalMethod() {
        return original;
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, AnalysisMethod method, HostedProviders providers, Purpose purpose) {
        HostedGraphKit kit = new HostedGraphKit(debug, providers, method);
        AnalysisMethod constructor = kit.getMetaAccess().lookupJavaMethod(ReflectionUtil.lookupConstructor(resolutionError));

        AbstractNewObjectNode newInstance = kit.append(new NewInstanceNode(constructor.getDeclaringClass(), true));
        kit.createInvokeWithExceptionAndUnwind(constructor, InvokeKind.Special, kit.getFrameState(), kit.bci(), newInstance);
        kit.append(new UnwindNode(newInstance));
        return kit.finalizeGraph();
    }
}
