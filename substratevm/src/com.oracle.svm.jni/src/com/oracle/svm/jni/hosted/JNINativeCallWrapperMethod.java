/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jni.hosted;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.word.WordTypes;

import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.hosted.code.NonBytecodeStaticMethod;
import com.oracle.svm.hosted.code.SimpleSignature;
import com.oracle.svm.jni.JNINativeCallWrappers;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;

/**
 * Generated code for calling native methods with a specific {@linkplain JNICallSignature form} from
 * Java code. The wrapper takes care of transitioning to native code and back to Java, and if
 * required, for boxing object arguments in handles, and for unboxing an object return value.
 */
class JNINativeCallWrapperMethod extends NonBytecodeStaticMethod {

    /**
     * Use long for void and integer return types to increase the reusability of call wrappers. This
     * is fine with all our supported 64-bit calling conventions.
     */
    static boolean returnKindWidensToLong(JavaKind returnKind) {
        return returnKind.isNumericInteger() || returnKind == JavaKind.Void;
    }

    static JNICallSignature getSignatureForTarget(ResolvedJavaMethod method, AnalysisUniverse universe, WordTypes wordTypes) {
        assert method.isNative() && !method.isConstructor();

        Signature signature = method.getSignature();
        int count = signature.getParameterCount(false);
        JavaKind[] params = new JavaKind[2 + count];
        params[0] = wordTypes.getWordKind(); // native target code address
        params[1] = JavaKind.Object; // receiver or class object
        for (int i = 0; i < count; i++) {
            JavaType paramType = universe.lookupAllowUnresolved(signature.getParameterType(i, null));
            params[2 + i] = wordTypes.asKind(paramType);
        }
        JavaType returnType = universe.lookupAllowUnresolved(signature.getReturnType(null));
        JavaKind returnKind = wordTypes.asKind(returnType);
        if (returnKindWidensToLong(returnKind)) {
            returnKind = JavaKind.Long;
        }
        return new JNICallSignature(params, returnKind, universe.getOriginalMetaAccess());
    }

    JNINativeCallWrapperMethod(JNICallSignature signature, MetaAccessProvider originalMetaAccess) {
        super("invoke" + signature.getIdentifier(), originalMetaAccess.lookupJavaType(JNINativeCallWrappers.class),
                        signature, JNINativeCallWrappers.getConstantPool(originalMetaAccess));
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        JNIGraphKit kit = new JNIGraphKit(debug, providers, method);
        InvokeWithExceptionNode handleFrame = kit.nativeCallPrologue();
        ValueNode environment = kit.environment();

        JavaType javaReturnType = method.getSignature().getReturnType(null);
        JavaType[] javaArgumentTypes = method.toParameterTypes();
        List<ValueNode> javaArguments = kit.loadArguments(javaArgumentTypes);
        ValueNode callAddress = javaArguments.get(0);

        List<ValueNode> jniArguments = new ArrayList<>(1 + javaArguments.size());
        List<JavaType> jniArgumentTypes = new ArrayList<>(1 + javaArguments.size());
        JavaType environmentType = providers.getMetaAccess().lookupJavaType(JNIEnvironment.class);
        JavaType objectHandleType = providers.getMetaAccess().lookupJavaType(JNIObjectHandle.class);
        jniArguments.add(environment);
        jniArgumentTypes.add(environmentType);
        for (int i = 1; i < javaArguments.size(); i++) {
            ValueNode arg = javaArguments.get(i);
            JavaType argType = javaArgumentTypes[i];
            if (argType.getJavaKind().isObject()) {
                ValueNode obj = javaArguments.get(i);
                arg = kit.boxObjectInLocalHandle(obj);
                argType = objectHandleType;
            }
            jniArguments.add(arg);
            jniArgumentTypes.add(argType);
        }
        assert jniArguments.size() == jniArgumentTypes.size();
        JavaType jniReturnType = javaReturnType;
        if (jniReturnType.getJavaKind().isObject()) {
            jniReturnType = objectHandleType;
        }

        kit.getFrameState().clearLocals();

        Signature jniSignature = new SimpleSignature(jniArgumentTypes, jniReturnType);
        ValueNode returnValue = kit.createCFunctionCall(callAddress, jniArguments, jniSignature, StatusSupport.STATUS_IN_NATIVE, false);

        if (javaReturnType.getJavaKind().isObject()) {
            returnValue = kit.unboxHandle(returnValue); // before destroying handles in epilogue
        }
        kit.nativeCallEpilogue(handleFrame);
        kit.rethrowPendingException();
        kit.createReturn(returnValue, javaReturnType.getJavaKind());
        return kit.finalizeGraph();
    }
}
