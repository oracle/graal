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
package com.oracle.svm.hosted.code;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.NeverInlineTrivial;
import com.oracle.svm.hosted.annotation.AnnotationValue;
import com.oracle.svm.hosted.annotation.SubstrateAnnotationExtractor;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.phases.HostedGraphKit;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.AbstractNewObjectNode;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

public final class FactoryMethod extends NonBytecodeMethod {

    private final ResolvedJavaMethod targetConstructor;
    private final boolean throwAllocatedObject;

    FactoryMethod(String name, ResolvedJavaMethod targetConstructor, ResolvedJavaType declaringClass, Signature signature, ConstantPool constantPool, boolean throwAllocatedObject) {
        super(name, true, declaringClass, signature, constantPool);
        this.targetConstructor = targetConstructor;
        this.throwAllocatedObject = throwAllocatedObject;

        assert targetConstructor.isConstructor() : targetConstructor;
        assert !(targetConstructor instanceof AnalysisMethod) && !(targetConstructor instanceof HostedMethod) : targetConstructor;
    }

    /**
     * Even though factory methods have few Graal nodes and are therefore considered "trivial", we
     * do not want them inlined immediately by the trivial method inliner because we know that the
     * machine code for allocations is large. Note that this does not preclude later inlining of the
     * method as part of the regular AOT compilation pipeline.
     */
    @NeverInlineTrivial("FactoryMethod")
    @SuppressWarnings("unused")
    private static void annotationHolder() {
    }

    private static final AnnotationValue[] INJECTED_ANNOTATIONS = SubstrateAnnotationExtractor.prepareInjectedAnnotations(
                    ReflectionUtil.lookupMethod(FactoryMethod.class, "annotationHolder").getAnnotation(NeverInlineTrivial.class));

    @Override
    public AnnotationValue[] getInjectedAnnotations() {
        return INJECTED_ANNOTATIONS;
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, AnalysisMethod method, HostedProviders providers, Purpose purpose) {
        HostedGraphKit kit = new HostedGraphKit(debug, providers, method);
        FactoryMethodSupport support = ImageSingletons.lookup(FactoryMethodSupport.class);

        AnalysisMethod aTargetConstructor = kit.getMetaAccess().getUniverse().lookup(targetConstructor);

        AbstractNewObjectNode newInstance = support.createNewInstance(kit, aTargetConstructor.getDeclaringClass(), true);

        ValueNode[] originalArgs = kit.getInitialArguments().toArray(ValueNode.EMPTY_ARRAY);
        ValueNode[] invokeArgs = new ValueNode[originalArgs.length + 1];
        invokeArgs[0] = newInstance;
        System.arraycopy(originalArgs, 0, invokeArgs, 1, originalArgs.length);
        kit.createInvokeWithExceptionAndUnwind(aTargetConstructor, InvokeKind.Special, kit.getFrameState(), kit.bci(), invokeArgs);

        if (throwAllocatedObject) {
            kit.append(new UnwindNode(newInstance));
        } else {
            kit.createReturn(newInstance, newInstance.getStackKind());
        }
        return kit.finalizeGraph();
    }

    public ResolvedJavaMethod getTargetConstructor() {
        return targetConstructor;
    }
}
