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
package com.oracle.svm.hosted.jni;

import java.lang.reflect.Constructor;
import java.util.List;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.IndirectCallTargetNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ProfileData.BranchProbabilityData;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.ObjectEqualsNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.word.WordTypes;

import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.graal.pointsto.infrastructure.WrappedSignature;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.graal.nodes.LoweredDeadEndNode;
import com.oracle.svm.core.jni.JNIJavaCallWrapperHolder;
import com.oracle.svm.core.jni.access.JNIAccessibleMethod;
import com.oracle.svm.hosted.code.FactoryMethodSupport;
import com.oracle.svm.hosted.code.NonBytecodeMethod;
import com.oracle.svm.hosted.code.SimpleSignature;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Generates interruptible code with a specific signature for calling a Java method that has a
 * compatible signature from native code. Note that the generated interruptible code is called by a
 * separately generated wrapper (see {@link JNIJavaCallVariantWrapperMethod}) that takes care of
 * transitioning to a Java context and back to native code, as well as catching and retaining
 * unhandled exceptions.
 *
 * @see <a href=
 *      "https://docs.oracle.com/javase/8/docs/technotes/guides/jni/spec/functions.html">Java 8 JNI
 *      functions documentation</a>
 * @see <a href="https://docs.oracle.com/en/java/javase/11/docs/specs/jni/functions.html">Java 11
 *      JNI functions documentation</a>
 */
public class JNIJavaCallWrapperMethod extends NonBytecodeMethod {
    private static final Constructor<InstantiationException> INSTANTIATION_EXCEPTION_CONSTRUCTOR = ReflectionUtil.lookupConstructor(InstantiationException.class);

    public static class Factory {
        public JNIJavaCallWrapperMethod create(SimpleSignature targetSignature, MetaAccessProvider originalMetaAccess, WordTypes wordTypes) {
            return new JNIJavaCallWrapperMethod(targetSignature, originalMetaAccess, wordTypes);
        }

        @SuppressWarnings("unused")
        public boolean canInvokeConstructorOnObject(ResolvedJavaMethod constructor, MetaAccessProvider originalMetaAccess) {
            return true;
        }
    }

    public static SimpleSignature getGeneralizedSignatureForTarget(ResolvedJavaMethod targetMethod, MetaAccessProvider originalMetaAccess) {
        ResolvedJavaType objectType = originalMetaAccess.lookupJavaType(Object.class);
        JavaType[] paramTypes = targetMethod.getSignature().toParameterTypes(null);
        // Note: does not include the receiver.
        for (int i = 0; i < paramTypes.length; i++) {
            if (paramTypes[i].getJavaKind().isObject()) {
                paramTypes[i] = objectType;
            }
        }
        JavaKind returnKind = targetMethod.getSignature().getReturnKind();
        if (targetMethod.isConstructor()) {
            returnKind = JavaKind.Object; // return new (or previously allocated) object
        } else if (returnKind.isNumericInteger() || returnKind == JavaKind.Void) {
            // Use long for void and integer return types to increase the reusability of call
            // wrappers. This is fine with our supported 64-bit calling conventions.
            returnKind = JavaKind.Long;
        }
        JavaType returnType = returnKind.isObject() ? objectType : originalMetaAccess.lookupJavaType(returnKind.toJavaClass());
        return new SimpleSignature(paramTypes, returnType);
    }

    private final Signature targetSignature;

    protected JNIJavaCallWrapperMethod(SimpleSignature targetSignature, MetaAccessProvider metaAccess, WordTypes wordTypes) {
        super("invoke_" + targetSignature.getIdentifier(), true, metaAccess.lookupJavaType(JNIJavaCallWrapperHolder.class),
                        createSignature(targetSignature, metaAccess, wordTypes), JNIJavaCallWrapperHolder.getConstantPool(metaAccess));
        this.targetSignature = targetSignature;
    }

    private static SimpleSignature createSignature(Signature targetSignature, MetaAccessProvider originalMetaAccess, WordTypes wordTypes) {
        JavaKind wordKind = wordTypes.getWordKind();
        int count = targetSignature.getParameterCount(false);
        JavaKind[] args = new JavaKind[3 + count];
        args[0] = wordKind; // this (instance method) or class (static method) handle
        args[1] = wordKind; // jmethodID
        args[2] = JavaKind.Boolean.getStackKind(); // non-virtual?
        for (int i = 0; i < count; i++) { // skip non-virtual, receiver/class arguments
            JavaKind kind = targetSignature.getParameterKind(i);
            if (kind.isObject()) {
                kind = wordKind; // handle
            }
            /*
             * Widen to the stack kind, i.e. from boolean/byte/short/char to int, for greater
             * reusability of call variant wrappers. This also changes the parameter type from C,
             * but that is not an issue: C requires an equivalent integer promotion to take place
             * for vararg calls (C99, 6.5.2.2-6), which also applies to JNI calls taking a va_list.
             * For JNI calls which are passed parameters in jvalue arrays, we can just mask the
             * extra bits in this method thanks to little endian order.
             */
            args[3 + i] = kind.getStackKind();
        }
        JavaKind returnKind = targetSignature.getReturnKind();
        if (returnKind.isObject()) {
            returnKind = wordKind; // handle
        }
        return SimpleSignature.fromKinds(args, returnKind, originalMetaAccess);
    }

    @Override
    public SimpleSignature getSignature() {
        return (SimpleSignature) super.getSignature();
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        UniverseMetaAccess metaAccess = (UniverseMetaAccess) providers.getMetaAccess();
        JNIGraphKit kit = new JNIGraphKit(debug, providers, method, purpose);

        AnalysisMetaAccess aMetaAccess = (AnalysisMetaAccess) ((metaAccess instanceof AnalysisMetaAccess) ? metaAccess : metaAccess.getWrapped());
        Signature invokeSignature = aMetaAccess.getUniverse().lookup(targetSignature, getDeclaringClass());
        if (metaAccess instanceof HostedMetaAccess) {
            // signature might not exist in the hosted universe because it does not match any method
            invokeSignature = new WrappedSignature(metaAccess.getUniverse(), invokeSignature, getDeclaringClass());
        }

        JavaKind wordKind = providers.getWordTypes().getWordKind();
        int slotIndex = 0;
        ValueNode receiverOrClassHandle = kit.loadLocal(slotIndex, wordKind);
        ValueNode receiverOrClass = kit.invokeUnboxHandle(receiverOrClassHandle);
        slotIndex += wordKind.getSlotCount();
        ValueNode methodId = kit.loadLocal(slotIndex, wordKind);
        slotIndex += wordKind.getSlotCount();
        ValueNode nonVirtual = kit.loadLocal(slotIndex, JavaKind.Boolean.getStackKind());
        slotIndex += JavaKind.Boolean.getStackKind().getSlotCount();

        ValueNode[] args = loadAndUnboxArguments(kit, providers, invokeSignature, slotIndex);
        ValueNode returnValue = createCall(kit, invokeSignature, methodId, receiverOrClass, nonVirtual, args);

        JavaKind returnKind = returnValue.getStackKind();
        if (returnKind.isObject()) {
            returnValue = kit.invokeBoxObjectInLocalHandle(returnValue);
        }
        kit.createReturn(returnValue, returnKind);
        return kit.finalizeGraph();
    }

    private ValueNode createCall(JNIGraphKit kit, Signature invokeSignature, ValueNode methodId, ValueNode receiverOrClass, ValueNode nonVirtual, ValueNode[] args) {
        ValueNode declaringClass = kit.invokeGetDeclaringClassForMethod(methodId);
        if (!invokeSignature.getReturnKind().isObject()) {
            return createRegularMethodCall(kit, invokeSignature, methodId, receiverOrClass, nonVirtual, args);
        }

        ValueNode newObjectAddress = kit.invokeGetNewObjectAddress(methodId);
        kit.startIf(IntegerEqualsNode.create(newObjectAddress, kit.createWord(0), NodeView.DEFAULT), BranchProbabilityData.unknown());
        kit.thenPart();
        ValueNode methodReturnValue = createRegularMethodCall(kit, invokeSignature, methodId, receiverOrClass, nonVirtual, args);
        kit.elsePart();
        ValueNode receiverOrCreatedObject = createNewObjectOrConstructorCall(kit, invokeSignature, methodId, declaringClass, newObjectAddress, receiverOrClass, args);
        AbstractMergeNode merge = kit.endIf();
        return mergeValues(kit, merge, kit.bci(), methodReturnValue, receiverOrCreatedObject);
    }

    private static ValueNode createRegularMethodCall(JNIGraphKit kit, Signature invokeSignature, ValueNode methodId,
                    ValueNode receiverOrClass, ValueNode nonVirtual, ValueNode[] args) {
        ValueNode methodAddress = kit.invokeGetJavaCallAddress(methodId, receiverOrClass, nonVirtual);
        ValueNode isStatic = kit.invokeIsStaticMethod(methodId);
        kit.startIf(IntegerEqualsNode.create(isStatic, kit.createInt(0), NodeView.DEFAULT), BranchProbabilityData.unknown());
        kit.thenPart();
        ValueNode nonstaticResult = createMethodCallWithReceiver(kit, invokeSignature, methodAddress, receiverOrClass, args);
        kit.elsePart();
        ValueNode staticResult = createMethodCall(kit, invokeSignature.getReturnType(null), invokeSignature.toParameterTypes(null), methodAddress, args);
        AbstractMergeNode merge = kit.endIf();
        return mergeValues(kit, merge, kit.bci(), nonstaticResult, staticResult);
    }

    protected ValueNode createNewObjectOrConstructorCall(JNIGraphKit kit, Signature invokeSignature, ValueNode methodId,
                    ValueNode declaringClass, ValueNode newObjectAddress, ValueNode receiverOrClass, ValueNode[] args) {
        /*
         * The called function could either be NewObject or Call<Type>Method with a constructor
         * (without creating a new object).
         *
         * To distinguish them, we look at the second parameter, which is either `jobject obj` (the
         * receiver object) for `Call<Type>Method`, or `jclass clazz` (hub of the receiver object)
         * for `NewObject`.
         */
        ObjectEqualsNode isNewObjectCall = kit.unique(new ObjectEqualsNode(receiverOrClass, declaringClass));
        kit.startIf(isNewObjectCall, BranchProbabilityNode.FAST_PATH_PROFILE);
        kit.thenPart();
        ValueNode createdObject = createNewObjectCall(kit, invokeSignature, newObjectAddress, args);

        kit.elsePart();
        createConstructorCall(kit, invokeSignature, methodId, declaringClass, receiverOrClass, args);

        AbstractMergeNode merge = kit.endIf();
        return mergeValues(kit, merge, kit.bci(), createdObject, receiverOrClass);
    }

    protected ValueNode createConstructorCall(JNIGraphKit kit, Signature invokeSignature, ValueNode methodId,
                    @SuppressWarnings("unused") ValueNode declaringClass, ValueNode receiverOrClass, ValueNode[] args) {
        ValueNode methodAddress = kit.invokeGetJavaCallAddress(methodId, receiverOrClass, kit.createInt(1));
        return createMethodCallWithReceiver(kit, invokeSignature, methodAddress, receiverOrClass, args);
    }

    private static ValueNode createMethodCallWithReceiver(JNIGraphKit kit, Signature invokeSignature, ValueNode methodAddress, ValueNode receiver, ValueNode[] args) {
        ValueNode[] argsWithReceiver = new ValueNode[1 + args.length];
        argsWithReceiver[0] = kit.maybeCreateExplicitNullCheck(receiver);
        System.arraycopy(args, 0, argsWithReceiver, 1, args.length);
        JavaType[] paramTypes = invokeSignature.toParameterTypes(kit.getMetaAccess().lookupJavaType(Object.class));
        return createMethodCall(kit, invokeSignature.getReturnType(null), paramTypes, methodAddress, argsWithReceiver);
    }

    private static ValueNode createNewObjectCall(JNIGraphKit kit, Signature invokeSignature, ValueNode newObjectAddress, ValueNode[] args) {
        ConstantNode abstractTypeSentinel = kit.createWord(JNIAccessibleMethod.NEW_OBJECT_INVALID_FOR_ABSTRACT_TYPE);
        kit.startIf(IntegerEqualsNode.create(newObjectAddress, abstractTypeSentinel, NodeView.DEFAULT), BranchProbabilityNode.SLOW_PATH_PROFILE);
        kit.thenPart();
        ResolvedJavaMethod exceptionCtor = kit.getMetaAccess().lookupJavaMethod(INSTANTIATION_EXCEPTION_CONSTRUCTOR);
        ResolvedJavaMethod throwMethod = FactoryMethodSupport.singleton().lookup((UniverseMetaAccess) kit.getMetaAccess(), exceptionCtor, true);
        kit.createInvokeWithExceptionAndUnwind(throwMethod, CallTargetNode.InvokeKind.Static, kit.getFrameState(), kit.bci());
        kit.append(new LoweredDeadEndNode());
        kit.endIf();

        return createMethodCall(kit, invokeSignature.getReturnType(null), invokeSignature.toParameterTypes(null), newObjectAddress, args);
    }

    private static ValueNode createMethodCall(JNIGraphKit kit, JavaType returnType, JavaType[] paramTypes, ValueNode methodAddress, ValueNode[] args) {
        StampPair returnStamp = StampFactory.forDeclaredType(kit.getAssumptions(), returnType, false);
        CallTargetNode callTarget = new IndirectCallTargetNode(methodAddress, args, returnStamp, paramTypes,
                        null, SubstrateCallingConventionKind.Java.toType(true), CallTargetNode.InvokeKind.Static);

        InvokeWithExceptionNode invoke = kit.startInvokeWithException(callTarget, kit.getFrameState(), kit.bci());
        kit.exceptionPart();
        ExceptionObjectNode exception = kit.exceptionObject();
        kit.append(new UnwindNode(exception));
        kit.endInvokeWithException();
        return invoke;
    }

    private static ValueNode mergeValues(JNIGraphKit kit, AbstractMergeNode merge, int bci, ValueNode... values) {
        Stamp stamp = StampTool.meet(List.of(values));
        ValueNode returnValue = kit.getGraph().addWithoutUnique(new ValuePhiNode(stamp, merge, values));
        JavaKind returnKind = returnValue.getStackKind();
        kit.getFrameState().push(returnKind, returnValue);
        merge.setStateAfter(kit.getFrameState().create(bci, merge));
        kit.getFrameState().pop(returnKind);
        return returnValue;
    }

    private static ValueNode[] loadAndUnboxArguments(JNIGraphKit kit, HostedProviders providers, Signature invokeSignature, int firstSlotIndex) {
        int slotIndex = firstSlotIndex;
        int count = invokeSignature.getParameterCount(false);
        ValueNode[] args = new ValueNode[count];
        for (int i = 0; i < args.length; i++) {
            ResolvedJavaType type = (ResolvedJavaType) invokeSignature.getParameterType(i, null);
            JavaKind kind = type.getJavaKind();
            JavaKind loadKind = kind;
            if (kind.isObject()) {
                loadKind = providers.getWordTypes().getWordKind();
            } else if (kind != kind.getStackKind()) {
                // We widened the kind in the signature for better reusability of call variant
                // wrappers (read above) and need to mask extra bits below.
                loadKind = kind.getStackKind();
            }
            ValueNode value = kit.loadLocal(slotIndex, loadKind);
            if (kind.isObject()) {
                value = kit.invokeUnboxHandle(value);
                value = kit.checkObjectType(value, type, false);
            } else if (kind != loadKind) {
                value = kit.maskNumericIntBytes(value, kind);
            }
            args[i] = value;
            slotIndex += loadKind.getSlotCount();
        }
        return args;
    }
}
