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

import java.lang.annotation.Annotation;
import java.util.Objects;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.AbstractNewObjectNode;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.NeverInlineTrivial;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.phases.HostedGraphKit;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

public final class FactoryMethod extends NonBytecodeStaticMethod {

    private final ResolvedJavaMethod targetConstructor;
    private final boolean throwAllocatedObject;

    FactoryMethod(ResolvedJavaMethod targetConstructor, ResolvedJavaType declaringClass, Signature signature, ConstantPool constantPool, boolean throwAllocatedObject) {
        super(SubstrateUtil.uniqueShortName(targetConstructor), declaringClass, signature, constantPool);
        this.targetConstructor = targetConstructor;
        this.throwAllocatedObject = throwAllocatedObject;

        assert targetConstructor.isConstructor();
        assert !(targetConstructor instanceof AnalysisMethod) && !(targetConstructor instanceof HostedMethod);
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

    private static final NeverInlineTrivial INLINE_ANNOTATION = Objects.requireNonNull(
                    ReflectionUtil.lookupMethod(FactoryMethod.class, "annotationHolder").getAnnotation(NeverInlineTrivial.class));

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        if (annotationClass.isInstance(INLINE_ANNOTATION)) {
            return annotationClass.cast(INLINE_ANNOTATION);
        }
        return null;
    }

    @Override
    public Annotation[] getAnnotations() {
        return new Annotation[]{INLINE_ANNOTATION};
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return new Annotation[]{INLINE_ANNOTATION};
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        FactoryMethodSupport support = ImageSingletons.lookup(FactoryMethodSupport.class);

        UniverseMetaAccess metaAccess = (UniverseMetaAccess) providers.getMetaAccess();
        ResolvedJavaMethod universeTargetConstructor = lookupMethodInUniverse(metaAccess, targetConstructor);
        HostedGraphKit kit = new HostedGraphKit(debug, providers, method);

        AbstractNewObjectNode newInstance = support.createNewInstance(kit, universeTargetConstructor.getDeclaringClass(), true);

        ValueNode[] originalArgs = kit.loadArguments(method.toParameterTypes()).toArray(new ValueNode[0]);
        ValueNode[] invokeArgs = new ValueNode[originalArgs.length + 1];
        invokeArgs[0] = newInstance;
        System.arraycopy(originalArgs, 0, invokeArgs, 1, originalArgs.length);
        InvokeWithExceptionNode invoke = kit.createInvokeWithExceptionAndUnwind(universeTargetConstructor, InvokeKind.Special, kit.getFrameState(), kit.bci(), invokeArgs);
        if (support.inlineConstructor(universeTargetConstructor)) {
            kit.inline(invoke, "Constructor in FactoryMethod", "FactoryMethod");
        }

        if (throwAllocatedObject) {
            kit.append(new UnwindNode(newInstance));
        } else {
            kit.createReturn(newInstance, newInstance.getStackKind());
        }
        return kit.finalizeGraph();
    }

    private ResolvedJavaMethod lookupMethodInUniverse(UniverseMetaAccess metaAccess, ResolvedJavaMethod method) {
        ResolvedJavaMethod universeMethod = method;
        MetaAccessProvider wrappedMetaAccess = metaAccess.getWrapped();
        if (wrappedMetaAccess instanceof UniverseMetaAccess) {
            universeMethod = lookupMethodInUniverse((UniverseMetaAccess) wrappedMetaAccess, universeMethod);
        }
        return metaAccess.getUniverse().lookup(universeMethod);
    }
}
