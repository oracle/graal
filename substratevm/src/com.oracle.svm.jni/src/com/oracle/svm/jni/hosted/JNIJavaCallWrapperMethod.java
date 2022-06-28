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

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.java.FrameStateBuilder;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.IndirectCallTargetNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import org.graalvm.compiler.word.WordTypes;

import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaMethod;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaType;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.hosted.code.NonBytecodeStaticMethod;
import com.oracle.svm.jni.JNIJavaCallWrappers;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;

/**
 * Generated code with a specific signature for calling a Java method that has a compatible
 * signature from native code. The wrapper takes care of transitioning to a Java context and back to
 * native code, for catching and retaining unhandled exceptions, and if required, for unboxing
 * object handle arguments and boxing an object return value. It delegates to a generated
 * {@link JNIJavaCallMethod} for the actual call to a particular Java method.
 *
 * @see <a href=
 *      "https://docs.oracle.com/javase/8/docs/technotes/guides/jni/spec/functions.html">Java 8 JNI
 *      functions documentation</a>
 * @see <a href="https://docs.oracle.com/en/java/javase/11/docs/specs/jni/functions.html">Java 11
 *      JNI functions documentation</a>
 */
public class JNIJavaCallWrapperMethod extends NonBytecodeStaticMethod {
    private final Signature javaCallSignature;

    public JNIJavaCallWrapperMethod(JNICallSignature javaCallSignature, MetaAccessProvider metaAccess, WordTypes wordTypes) {
        super("invoke" + javaCallSignature.getIdentifier(), metaAccess.lookupJavaType(JNIJavaCallWrappers.class),
                        createSignature(javaCallSignature, metaAccess, wordTypes), JNIJavaCallWrappers.getConstantPool(metaAccess));
        this.javaCallSignature = javaCallSignature;
    }

    private static JNICallSignature createSignature(Signature targetSignature, MetaAccessProvider originalMetaAccess, WordTypes wordTypes) {
        JavaKind wordKind = wordTypes.getWordKind();
        int count = targetSignature.getParameterCount(false);
        JavaKind[] args = new JavaKind[3 + count - 2];
        args[0] = wordKind; // this (instance method) or class (static method) handle
        args[1] = wordKind; // jmethodID
        args[2] = JavaKind.Boolean.getStackKind(); // non-virtual?
        for (int i = 2; i < count; i++) { // skip non-virtual, receiver/class arguments
            JavaKind kind = targetSignature.getParameterKind(i);
            if (kind.isObject()) {
                kind = wordKind; // handle
            }
            args[3 + (i - 2)] = kind.getStackKind();
        }
        JavaKind returnKind = targetSignature.getReturnKind();
        if (returnKind.isObject()) {
            returnKind = wordKind; // handle
        }
        return new JNICallSignature(args, returnKind, originalMetaAccess);
    }

    @Override
    public JNICallSignature getSignature() {
        return (JNICallSignature) super.getSignature();
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        UniverseMetaAccess metaAccess = (UniverseMetaAccess) providers.getMetaAccess();
        JNIGraphKit kit = new JNIGraphKit(debug, providers, method);

        Signature invokeSignature = javaCallSignature;
        if (metaAccess.getWrapped() instanceof UniverseMetaAccess) {
            invokeSignature = ((UniverseMetaAccess) metaAccess.getWrapped()).getUniverse().lookup(
                            invokeSignature, (WrappedJavaType) ((WrappedJavaMethod) method).getWrapped().getDeclaringClass());
        }
        invokeSignature = metaAccess.getUniverse().lookup(invokeSignature, (WrappedJavaType) method.getDeclaringClass());

        JavaKind wordKind = providers.getWordTypes().getWordKind();
        int slotIndex = 0;
        ValueNode receiverOrClassHandle = kit.loadLocal(slotIndex, wordKind);
        ValueNode receiverOrClass = kit.unboxHandle(receiverOrClassHandle);
        slotIndex += wordKind.getSlotCount();
        ValueNode methodId = kit.loadLocal(slotIndex, wordKind);
        slotIndex += wordKind.getSlotCount();
        ValueNode nonVirtual = kit.loadLocal(slotIndex, JavaKind.Boolean.getStackKind());
        slotIndex += JavaKind.Boolean.getStackKind().getSlotCount();

        int firstParamIndex = 2;
        ValueNode[] loadedArgs = loadAndUnboxArguments(kit, providers, invokeSignature, firstParamIndex, slotIndex);

        ValueNode[] args = new ValueNode[2 + loadedArgs.length];
        args[0] = nonVirtual;
        args[1] = receiverOrClass;
        System.arraycopy(loadedArgs, 0, args, 2, loadedArgs.length);

        ValueNode javaCallAddress = kit.getJavaCallAddressFromMethodId(methodId);
        ValueNode returnValue = createMethodCall(kit, invokeSignature.toParameterTypes(null), invokeSignature.getReturnType(null), kit.getFrameState(), javaCallAddress, args);
        JavaKind returnKind = (returnValue != null) ? returnValue.getStackKind() : JavaKind.Void;
        if (returnKind.isObject()) {
            returnValue = kit.boxObjectInLocalHandle(returnValue);
        }
        kit.createReturn(returnValue, returnKind);
        return kit.finalizeGraph();
    }

    /**
     * Builds a JNI {@code Call<Type>Method} call, returning a node that contains the return value
     * or null/zero/false if an exception occurred (in which case the exception becomes a JNI
     * pending exception).
     */
    protected ValueNode createMethodCall(JNIGraphKit kit, JavaType[] paramTypes, JavaType returnType, FrameStateBuilder state, ValueNode address, ValueNode... args) {
        int bci = kit.bci();
        InvokeWithExceptionNode invoke = startInvokeWithRetainedException(kit, paramTypes, returnType, state, bci, address, args);
        AbstractMergeNode invokeMerge = kit.endInvokeWithException();

        if (invoke.getStackKind() == JavaKind.Void) {
            invokeMerge.setStateAfter(state.create(bci, invokeMerge));
            return null;
        }

        ValueNode exceptionValue = kit.unique(ConstantNode.defaultForKind(invoke.getStackKind()));
        ValueNode[] inputs = {invoke, exceptionValue};
        ValueNode returnValue = kit.getGraph().addWithoutUnique(new ValuePhiNode(invoke.stamp(NodeView.DEFAULT), invokeMerge, inputs));
        JavaKind returnKind = returnValue.getStackKind();
        state.push(returnKind, returnValue);
        invokeMerge.setStateAfter(state.create(bci, invokeMerge));
        state.pop(returnKind);
        return returnValue;
    }

    protected static InvokeWithExceptionNode startInvokeWithRetainedException(JNIGraphKit kit, JavaType[] paramTypes,
                    JavaType returnType, FrameStateBuilder state, int bci, ValueNode methodAddress, ValueNode... args) {
        ValueNode formerPendingException = kit.getAndClearPendingException();

        StampPair returnStamp = StampFactory.forDeclaredType(kit.getAssumptions(), returnType, false);
        CallTargetNode callTarget = new IndirectCallTargetNode(methodAddress, args, returnStamp,
                        paramTypes, null, SubstrateCallingConventionKind.Java.toType(true), InvokeKind.Static);
        InvokeWithExceptionNode invoke = kit.startInvokeWithException(callTarget, state, bci);

        kit.noExceptionPart(); // no new exception was thrown, restore the formerly pending one
        kit.setPendingException(formerPendingException);

        kit.exceptionPart();
        ExceptionObjectNode exceptionObject = kit.exceptionObject();
        kit.setPendingException(exceptionObject);

        return invoke;
    }

    private static ValueNode[] loadAndUnboxArguments(JNIGraphKit kit, HostedProviders providers, Signature invokeSignature, int firstParamIndex, int firstSlotIndex) {
        int slotIndex = firstSlotIndex;
        int count = invokeSignature.getParameterCount(false);
        ValueNode[] args = new ValueNode[count - firstParamIndex];
        for (int i = 0; i < args.length; i++) {
            JavaKind kind = invokeSignature.getParameterKind(firstParamIndex + i);
            assert kind == kind.getStackKind() : "conversions and bit masking must happen in JNIJavaCallMethod";
            JavaKind loadKind = kind;
            if (kind.isObject()) {
                loadKind = providers.getWordTypes().getWordKind();
            }
            ValueNode value = kit.loadLocal(slotIndex, loadKind);
            if (kind.isObject()) {
                value = kit.unboxHandle(value);
            }
            args[i] = value;
            slotIndex += loadKind.getSlotCount();
        }
        return args;
    }
}
