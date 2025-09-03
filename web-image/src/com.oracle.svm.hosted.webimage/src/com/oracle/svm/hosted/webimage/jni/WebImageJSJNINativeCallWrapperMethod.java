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
package com.oracle.svm.hosted.webimage.jni;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.pointsto.infrastructure.ResolvedSignature;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaMethod;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.jni.JNIGeneratedMethodSupport;
import com.oracle.svm.core.jni.access.JNINativeLinkage;
import com.oracle.svm.core.jni.headers.JNIEnvironment;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.annotation.CustomSubstitutionMethod;
import com.oracle.svm.hosted.jni.JNIAccessFeature;
import com.oracle.svm.hosted.webimage.phases.WebImageHostedGraphKit;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

// TODO(GR-35288): Implement proper JNI call support.
public class WebImageJSJNINativeCallWrapperMethod extends CustomSubstitutionMethod {
    private final JNINativeLinkage linkage;

    public WebImageJSJNINativeCallWrapperMethod(ResolvedJavaMethod original) {
        super(original);
        this.linkage = createLinkage(original);
    }

    private static JNINativeLinkage createLinkage(ResolvedJavaMethod method) {
        ResolvedJavaMethod unwrapped = method;
        while (unwrapped instanceof WrappedJavaMethod) {
            unwrapped = ((WrappedJavaMethod) unwrapped).getWrapped();
        }
        String className = unwrapped.getDeclaringClass().getName();
        String descriptor = unwrapped.getSignature().toMethodDescriptor();
        return JNIAccessFeature.singleton().makeLinkage(className, unwrapped.getName(), descriptor);
    }

    @Override
    public boolean isSynthetic() {
        return true;
    }

    @Override
    public int getModifiers() {
        final int synthetic = 0x1000;
        // A synchronized method requires some special handling. Instead, if the wrapped method is
        // declared synchronized, we add graph nodes to lock and unlock accordingly.
        return (getOriginal().getModifiers() | synthetic) & ~(Modifier.NATIVE | Modifier.SYNCHRONIZED);
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, AnalysisMethod method, HostedProviders providers, Purpose purpose) {
        WebImageHostedGraphKit kit = new WebImageHostedGraphKit(debug, providers, method, purpose);
        AnalysisType javaReturnType = method.getSignature().getReturnType();

        ValueNode callAddress;
        if (linkage.isBuiltInFunction()) {
            throw VMError.unsupportedFeature("Builtin library functions not yet supported.");
        } else {
            callAddress = nativeCallAddress(kit, kit.createObject(linkage));
        }

        List<ValueNode> jniArguments = new ArrayList<>();
        List<AnalysisType> jniArgumentTypes = new ArrayList<>();
        ValueNode environment = kit.createConstant(JavaConstant.NULL_POINTER, JavaKind.Object);
        AnalysisType environmentType = kit.getMetaAccess().lookupJavaType(JNIEnvironment.class);
        jniArguments.add(environment);
        jniArgumentTypes.add(environmentType);

        if (method.isStatic()) {
            JavaConstant clazz = kit.getConstantReflection().asJavaClass(method.getDeclaringClass());
            ConstantNode clazzNode = ConstantNode.forConstant(clazz, kit.getMetaAccess(), kit.getGraph());
            jniArguments.add(clazzNode);
            jniArgumentTypes.add(kit.getMetaAccess().lookupJavaType(Class.class));
        }

        var javaArgumentTypes = method.toParameterList();
        List<ValueNode> javaArguments = kit.getInitialArguments();
        for (int i = 0; i < javaArguments.size(); i++) {
            ValueNode arg = javaArguments.get(i);
            AnalysisType argType = javaArgumentTypes.get(i);
            ValueNode jsArg = kit.createConvertToJs(arg, argType.getJavaKind());
            jniArguments.add(jsArg);
            jniArgumentTypes.add(argType);
        }

        /**
         * We have the invariant that integers with fewer than 32 bits are represented as 32-bit
         * integers. Therefore, we get the stackKind for the return type. For more details, see the
         * comment in
         * {@link com.oracle.svm.core.graal.replacements.SubstrateGraphKit#createCFunctionCall(ValueNode, List, Signature, int, boolean)}.
         */
        AnalysisType jniReturnType = javaReturnType;
        switch (jniReturnType.getJavaKind()) {
            case Boolean:
            case Byte:
            case Char:
            case Short:
                jniReturnType = kit.getMetaAccess().lookupJavaType(int.class);
        }

        ResolvedSignature<AnalysisType> jniSignature = ResolvedSignature.fromList(jniArgumentTypes, jniReturnType);
        ValueNode returnValue = kit.createIndirectCall(callAddress, jniArguments, jniSignature, SubstrateCallingConventionKind.Java);
        kit.createReturn(returnValue, javaReturnType.getJavaKind());

        return kit.finalizeGraph();
    }

    public static InvokeWithExceptionNode nativeCallAddress(WebImageHostedGraphKit kit, ValueNode linkageObject) {
        ResolvedJavaMethod method = kit.findMethod(JNIGeneratedMethodSupport.class, "nativeCallAddress", true);
        int invokeBci = kit.bci();
        return kit.createInvokeWithExceptionAndUnwind(method, CallTargetNode.InvokeKind.Static, kit.getFrameState(), invokeBci, linkageObject);
    }
}
