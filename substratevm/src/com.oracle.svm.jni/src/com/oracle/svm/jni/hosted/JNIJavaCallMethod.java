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

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.java.FrameStateBuilder;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.ObjectEqualsNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.word.WordTypes;

import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.graal.nodes.LoweredDeadEndNode;
import com.oracle.svm.hosted.code.FactoryMethodSupport;
import com.oracle.svm.hosted.code.NonBytecodeStaticMethod;
import com.oracle.svm.jni.JNIJavaCalls;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Generated method that is called by a {@link JNIJavaCallWrapperMethod} to invoke a specific Java
 * method, potentially non-virtually, and in the case of constructors, potentially allocating a new
 * object in the process.
 */
public class JNIJavaCallMethod extends NonBytecodeStaticMethod {

    public static class Factory {
        public JNIJavaCallMethod create(ResolvedJavaMethod method, AnalysisUniverse analysisUniverse, WordTypes wordTypes) {
            return new JNIJavaCallMethod(method, analysisUniverse, wordTypes);
        }
    }

    private static final Constructor<InstantiationException> INSTANTIATION_EXCEPTION_CONSTRUCTOR = ReflectionUtil.lookupConstructor(InstantiationException.class);

    static JNICallSignature getSignatureForTarget(ResolvedJavaMethod targetMethod, AnalysisUniverse analysisUniverse, WordTypes wordTypes) {
        Signature originalSignature = targetMethod.getSignature();
        int paramCount = originalSignature.getParameterCount(false);
        JavaKind[] paramKinds = new JavaKind[2 + paramCount];
        paramKinds[0] = JavaKind.Boolean; // non-virtual (== true)?
        paramKinds[1] = JavaKind.Object; // receiver or class obj
        for (int i = 0; i < paramCount; i++) {
            JavaType paramType = analysisUniverse.lookupAllowUnresolved(originalSignature.getParameterType(i, null));
            /*
             * We use only kinds and no specific object types so that a call wrapper can be reused
             * with any object types. Therefore, we have to do type checks in this method.
             */
            JavaKind paramKind = wordTypes.asKind(paramType);
            /*
             * Widen to the stack kind, i.e. from boolean/byte/short/char to int, for greater
             * reusability of call wrappers. This also changes the parameter type taken from the C
             * caller in the wrapper method, but that is not an issue: C requires an equivalent
             * integer promotion to take place for vararg calls (C99, 6.5.2.2-6), which also applies
             * to JNI calls taking a va_list. For JNI calls that are passed parameters in jvalue
             * arrays, we can just mask the extra bits in this method thanks to little endian order.
             */
            paramKinds[2 + i] = paramKind.getStackKind();
        }
        JavaType returnType = analysisUniverse.lookupAllowUnresolved(originalSignature.getReturnType(null));
        JavaKind returnKind = wordTypes.asKind(returnType);
        if (targetMethod.isConstructor()) {
            returnKind = JavaKind.Object; // return new (or previously allocated) object
        } else if (returnKind.isNumericInteger() || returnKind == JavaKind.Void) {
            // Use long for void and integer return types to increase the reusability of call
            // wrappers. This is fine with all our supported 64-bit calling conventions.
            returnKind = JavaKind.Long;
        }
        return new JNICallSignature(paramKinds, returnKind, analysisUniverse.getOriginalMetaAccess());
    }

    private final ResolvedJavaMethod targetMethod;

    public JNIJavaCallMethod(ResolvedJavaMethod targetMethod, AnalysisUniverse analysisUniverse, WordTypes wordTypes) {
        super("invoke_" + SubstrateUtil.uniqueShortName(targetMethod),
                        analysisUniverse.getOriginalMetaAccess().lookupJavaType(JNIJavaCalls.class),
                        getSignatureForTarget(targetMethod, analysisUniverse, wordTypes),
                        JNIJavaCalls.getConstantPool(analysisUniverse.getOriginalMetaAccess()));
        this.targetMethod = targetMethod;
    }

    @Override
    public JNICallSignature getSignature() {
        return (JNICallSignature) super.getSignature();
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        JNIGraphKit kit = new JNIGraphKit(debug, providers, method);

        ResolvedJavaMethod invokeMethod = targetMethod;
        UniverseMetaAccess metaAccess = (UniverseMetaAccess) providers.getMetaAccess();
        if (metaAccess.getWrapped() instanceof UniverseMetaAccess) {
            invokeMethod = ((UniverseMetaAccess) metaAccess.getWrapped()).getUniverse().lookup(invokeMethod);
        }
        invokeMethod = metaAccess.getUniverse().lookup(invokeMethod);

        ValueNode[] args;
        List<ValueNode> incomingArgs = kit.loadArguments(toParameterTypes());
        Signature invokeSignature = invokeMethod.getSignature();
        for (int i = 2; i < incomingArgs.size(); i++) {
            var paramType = (ResolvedJavaType) invokeSignature.getParameterType(i - 2, null);
            if (!paramType.isPrimitive() && !paramType.isJavaLangObject()) {
                incomingArgs.set(i, kit.checkObjectType(incomingArgs.get(i), paramType, false));
            } else if (paramType.getJavaKind().getStackKind() == JavaKind.Int) {
                // We might have widened the kind in the signature for better reusability of call
                // wrappers (read above) and need to mask extra bits now.
                JavaKind paramKind = paramType.getJavaKind();
                incomingArgs.set(i, kit.maskNumericIntBytes(incomingArgs.get(i), paramKind));
            }
        }
        int firstArg = invokeMethod.hasReceiver() ? 1 : 2;
        args = incomingArgs.subList(firstArg, incomingArgs.size()).toArray(ValueNode[]::new);

        ValueNode returnValue;
        boolean canBeStaticallyBound = invokeMethod.canBeStaticallyBound();
        if (canBeStaticallyBound || invokeMethod.isAbstract()) {
            returnValue = doInvoke(providers, kit, invokeMethod, canBeStaticallyBound, args.clone());
        } else {
            ValueNode nonVirtual = incomingArgs.get(0);
            LogicNode isVirtualCall = kit.unique(IntegerEqualsNode.create(nonVirtual, kit.createInt(0), NodeView.DEFAULT));

            kit.startIf(isVirtualCall, BranchProbabilityNode.FAST_PATH_PROFILE);
            kit.thenPart();
            ValueNode resultVirtual = doInvoke(providers, kit, invokeMethod, false, args.clone());

            kit.elsePart();
            ValueNode resultNonVirtual = doInvoke(providers, kit, invokeMethod, true, args.clone());

            AbstractMergeNode merge = kit.endIf();
            merge.setStateAfter(kit.getFrameState().create(kit.bci(), merge));

            ValueNode[] resultValues = {resultVirtual, resultNonVirtual};
            Stamp stamp = StampTool.meet(Arrays.asList(resultValues));
            returnValue = stamp.hasValues() ? kit.unique(new ValuePhiNode(stamp, merge, resultValues)) : null;
        }
        JavaKind returnKind = getSignature().getReturnKind();
        if (returnKind == JavaKind.Long) {
            // We might have widened to a long return type for better reusability of call wrappers
            if (returnValue == null || returnValue.stamp(NodeView.DEFAULT).isEmpty()) {
                returnValue = kit.createLong(0); // void method, return something
            } else {
                returnValue = kit.widenNumericInt(returnValue, JavaKind.Long);
            }
        }
        kit.createReturn(returnValue, returnKind);
        return kit.finalizeGraph();
    }

    private ValueNode doInvoke(HostedProviders providers, JNIGraphKit kit, ResolvedJavaMethod invokeMethod, boolean nonVirtual, ValueNode[] args) {
        UniverseMetaAccess metaAccess = (UniverseMetaAccess) providers.getMetaAccess();
        CallTargetNode.InvokeKind invokeKind = invokeMethod.isStatic() ? CallTargetNode.InvokeKind.Static : //
                        ((nonVirtual || invokeMethod.isConstructor()) ? CallTargetNode.InvokeKind.Special : CallTargetNode.InvokeKind.Virtual);
        ValueNode result;
        if (invokeMethod.isConstructor()) {
            /*
             * If the target method is a constructor, we can narrow down the JNI call to two
             * possible types of JNI functions: `Call<Type>Method` or `NewObject`.
             *
             * To distinguish `Call<Type>Method` from `NewObject`, we look at the second JNI call
             * parameter, which is either `jobject obj` (the receiver object) in the case of
             * `Call<Type>Method`, or `jclass clazz` (hub of the receiver object) for `NewObject`.
             */
            ResolvedJavaType receiverClass = invokeMethod.getDeclaringClass();
            Constant hub = providers.getConstantReflection().asObjectHub(receiverClass);
            ConstantNode hubNode = kit.createConstant(hub, JavaKind.Object);
            ObjectEqualsNode isNewObjectCall = kit.unique(new ObjectEqualsNode(args[0], hubNode));
            kit.startIf(isNewObjectCall, BranchProbabilityNode.FAST_PATH_PROFILE);
            kit.thenPart();
            ValueNode createdObject = null;
            if (invokeMethod.getDeclaringClass().isAbstract()) {
                ResolvedJavaMethod throwMethod = FactoryMethodSupport.singleton().lookup(metaAccess, metaAccess.lookupJavaMethod(INSTANTIATION_EXCEPTION_CONSTRUCTOR), true);
                createMethodCall(kit, throwMethod, CallTargetNode.InvokeKind.Static, kit.getFrameState());
                kit.append(new LoweredDeadEndNode());
            } else {
                ResolvedJavaMethod factoryMethod = FactoryMethodSupport.singleton().lookup(metaAccess, invokeMethod, false);
                ValueNode[] argsWithoutReceiver = Arrays.copyOfRange(args, 1, args.length);
                createdObject = createMethodCall(kit, factoryMethod, CallTargetNode.InvokeKind.Static, kit.getFrameState(), argsWithoutReceiver);
            }

            kit.elsePart();
            args[0] = kit.checkObjectType(args[0], invokeMethod.getDeclaringClass(), true);
            createMethodCall(kit, invokeMethod, invokeKind, kit.getFrameState(), args);

            AbstractMergeNode merge = kit.endIf();
            if (merge != null) {
                merge.setStateAfter(kit.getFrameState().create(kit.bci(), merge));
                result = kit.unique(new ValuePhiNode(StampFactory.object(), merge, new ValueNode[]{createdObject, args[0]}));
            } else {
                result = args[0];
            }
        } else {
            if (invokeMethod.hasReceiver()) {
                args[0] = kit.checkObjectType(args[0], invokeMethod.getDeclaringClass(), true);
            }
            result = createMethodCall(kit, invokeMethod, invokeKind, kit.getFrameState(), args);
        }
        return result;
    }

    protected ValueNode createMethodCall(JNIGraphKit kit, ResolvedJavaMethod invokeMethod, CallTargetNode.InvokeKind invokeKind, FrameStateBuilder frameState, ValueNode... args) {
        return kit.createInvokeWithExceptionAndUnwind(invokeMethod, invokeKind, frameState, kit.bci(), args);
    }

}
