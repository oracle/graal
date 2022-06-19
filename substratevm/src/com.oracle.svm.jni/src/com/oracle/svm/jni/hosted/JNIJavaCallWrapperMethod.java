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

import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.core.common.type.Stamp;
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
import org.graalvm.compiler.nodes.calc.FloatConvertNode;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaMethod;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaType;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.graal.nodes.CEntryPointEnterNode;
import com.oracle.svm.core.graal.nodes.CEntryPointLeaveNode;
import com.oracle.svm.core.graal.nodes.CEntryPointLeaveNode.LeaveAction;
import com.oracle.svm.core.graal.nodes.CInterfaceReadNode;
import com.oracle.svm.core.graal.nodes.ReadCallerStackPointerNode;
import com.oracle.svm.core.graal.nodes.VaListNextArgNode;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.code.EntryPointCallStubMethod;
import com.oracle.svm.hosted.code.SimpleSignature;
import com.oracle.svm.jni.JNIJavaCallWrappers;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIMethodId;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;
import com.oracle.svm.jni.nativeapi.JNIValue;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
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
public class JNIJavaCallWrapperMethod extends EntryPointCallStubMethod {
    public static class Factory {
        public JNIJavaCallWrapperMethod create(JNICallSignature javaCallSignature, CallVariant callVariant, boolean nonVirtual, MetaAccessProvider metaAccess) {
            return new JNIJavaCallWrapperMethod(javaCallSignature, callVariant, nonVirtual, metaAccess);
        }
    }

    public enum CallVariant {
        VARARGS,
        ARRAY,
        VA_LIST,
    }

    private final Signature javaCallSignature;
    private final CallVariant callVariant;
    private final boolean nonVirtual;

    protected JNIJavaCallWrapperMethod(JNICallSignature javaCallSignature, CallVariant callVariant, boolean nonVirtual, MetaAccessProvider metaAccess) {
        super(createName(javaCallSignature, callVariant, nonVirtual),
                        metaAccess.lookupJavaType(JNIJavaCallWrappers.class),
                        createSignature(javaCallSignature, callVariant, nonVirtual, metaAccess),
                        JNIJavaCallWrappers.getConstantPool(metaAccess));
        this.javaCallSignature = javaCallSignature;
        this.callVariant = callVariant;
        this.nonVirtual = nonVirtual;
    }

    private static String createName(JNICallSignature targetSignature, CallVariant callVariant, boolean nonVirtual) {
        return "invoke" + targetSignature.getIdentifier() + "_" + callVariant.name() + (nonVirtual ? "_Nonvirtual" : "");
    }

    private static SimpleSignature createSignature(Signature targetSignature, CallVariant callVariant, boolean nonVirtual, MetaAccessProvider metaAccess) {
        ResolvedJavaType objectHandle = metaAccess.lookupJavaType(JNIObjectHandle.class);
        List<JavaType> args = new ArrayList<>();
        args.add(metaAccess.lookupJavaType(JNIEnvironment.class));
        args.add(objectHandle); // this (instance method) or class (static method)
        if (nonVirtual) {
            args.add(objectHandle); // class of implementation to invoke
        }
        args.add(metaAccess.lookupJavaType(JNIMethodId.class));
        if (callVariant == CallVariant.VARARGS) {
            int count = targetSignature.getParameterCount(false);
            for (int i = 2; i < count; i++) { // skip non-virtual, receiver/class arguments
                JavaKind kind = targetSignature.getParameterKind(i);
                if (kind.isObject()) {
                    args.add(objectHandle);
                } else if (kind == JavaKind.Float) {
                    // C varargs promote float to double (C99, 6.5.2.2-6)
                    args.add(metaAccess.lookupJavaType(JavaKind.Double.toJavaClass()));
                } else { // C varargs promote sub-words to int (C99, 6.5.2.2-6)
                    args.add(metaAccess.lookupJavaType(kind.getStackKind().toJavaClass()));
                }
            }
        } else if (callVariant == CallVariant.ARRAY) {
            args.add(metaAccess.lookupJavaType(JNIValue.class)); // const jvalue *
        } else if (callVariant == CallVariant.VA_LIST) {
            args.add(metaAccess.lookupJavaType(WordBase.class)); // va_list (a pointer of some kind)
        } else {
            throw VMError.shouldNotReachHere();
        }
        JavaType returnType = targetSignature.getReturnType(null);
        if (returnType.getJavaKind().isObject()) {
            returnType = objectHandle;
        }
        return new SimpleSignature(args, returnType);
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        UniverseMetaAccess metaAccess = (UniverseMetaAccess) providers.getMetaAccess();
        JNIGraphKit kit = new JNIGraphKit(debug, providers, method);
        FrameStateBuilder state = new FrameStateBuilder(null, method, kit.getGraph());
        state.initializeForMethodStart(null, true, providers.getGraphBuilderPlugins());

        JavaKind vmThreadKind = metaAccess.lookupJavaType(JNIEnvironment.class).getJavaKind();
        ValueNode vmThread = kit.loadLocal(0, vmThreadKind);
        kit.append(CEntryPointEnterNode.enter(vmThread));

        Signature invokeSignature = javaCallSignature;
        if (metaAccess.getWrapped() instanceof UniverseMetaAccess) {
            invokeSignature = ((UniverseMetaAccess) metaAccess.getWrapped()).getUniverse().lookup(
                            invokeSignature, (WrappedJavaType) ((WrappedJavaMethod) method).getWrapped().getDeclaringClass());
        }
        invokeSignature = metaAccess.getUniverse().lookup(invokeSignature, (WrappedJavaType) method.getDeclaringClass());

        int firstParamIndex = 2;
        List<ValueNode> loadedArgs = loadAndUnboxArguments(kit, providers, invokeSignature, firstParamIndex);

        int slotIndex = metaAccess.lookupJavaType(JNIEnvironment.class).getJavaKind().getSlotCount();
        JavaKind receiverOrClassKind = metaAccess.lookupJavaType(JNIObjectHandle.class).getJavaKind();
        ValueNode receiverOrClassHandle = kit.loadLocal(slotIndex, receiverOrClassKind);
        ValueNode receiverOrClass = kit.unboxHandle(receiverOrClassHandle);
        slotIndex += receiverOrClassKind.getSlotCount();
        if (nonVirtual) {
            slotIndex += receiverOrClassKind.getSlotCount();
        }
        JavaKind methodIdKind = metaAccess.lookupJavaType(JNIMethodId.class).getJavaKind();
        ValueNode methodId = kit.loadLocal(slotIndex, methodIdKind);
        ValueNode javaCallAddress = kit.getJavaCallAddressFromMethodId(methodId);

        ValueNode[] args = new ValueNode[2 + loadedArgs.size()];
        args[0] = kit.createInt(nonVirtual ? 1 : 0);
        args[1] = receiverOrClass;
        for (int i = 0; i < loadedArgs.size(); i++) {
            args[2 + i] = loadedArgs.get(i);
        }

        ValueNode returnValue = createMethodCall(kit, invokeSignature.toParameterTypes(null), invokeSignature.getReturnType(null), state, javaCallAddress, args);
        JavaKind returnKind = (returnValue != null) ? returnValue.getStackKind() : JavaKind.Void;
        if (returnKind.isObject()) {
            returnValue = kit.boxObjectInLocalHandle(returnValue);
        }

        CEntryPointLeaveNode leave = new CEntryPointLeaveNode(LeaveAction.Leave);
        kit.append(leave);
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

    /**
     * Creates {@linkplain ValueNode IR nodes} for the arguments passed to the JNI call. The
     * arguments do not include the receiver of the call, but only the actual arguments passed to
     * the JNI target function.
     *
     * @return List of created argument nodes and their type
     */
    private List<ValueNode> loadAndUnboxArguments(JNIGraphKit kit, HostedProviders providers, Signature invokeSignature, int firstParamIndex) {
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        List<ValueNode> args = new ArrayList<>();
        int slotIndex = firstArgumentSlotIndex(metaAccess);
        int count = invokeSignature.getParameterCount(false);
        // Windows and iOS CallVariant.VA_LIST is identical to CallVariant.ARRAY
        // iOS CallVariant.VARARGS stores values as an array on the stack
        if (callVariant == CallVariant.ARRAY ||
                        (Platform.includedIn(Platform.DARWIN_AARCH64.class) && (callVariant == CallVariant.VARARGS || callVariant == CallVariant.VA_LIST)) ||
                        (Platform.includedIn(Platform.WINDOWS.class) && callVariant == CallVariant.VA_LIST)) {
            ResolvedJavaType elementType = metaAccess.lookupJavaType(JNIValue.class);
            int elementSize = SizeOf.get(JNIValue.class);
            ValueNode array;
            if (callVariant == CallVariant.VARARGS) {
                array = kit.append(new ReadCallerStackPointerNode());
            } else {
                array = kit.loadLocal(slotIndex, elementType.getJavaKind());
            }
            for (int i = firstParamIndex; i < count; i++) {
                JavaKind kind = invokeSignature.getParameterKind(i);
                assert kind == kind.getStackKind() : "other conversions and bit masking than below must happen in JNIJavaCallMethod";
                JavaKind readKind = kind;
                if (kind == JavaKind.Float && (callVariant == CallVariant.VARARGS || callVariant == CallVariant.VA_LIST)) {
                    readKind = JavaKind.Double;
                } else if (kind.isObject()) {
                    readKind = providers.getWordTypes().getWordKind();
                }
                /*
                 * jvalue is a union, and all members of a C union should have the same address,
                 * which is that of the union itself (C99 6.7.2.1-14). Therefore, we do not need a
                 * field offset lookup and we can also read a larger type than we need (e.g. int for
                 * char) and mask the extra bits on little-endian architectures so that we can reuse
                 * a wrapper for more different signatures.
                 */
                int offset = (i - firstParamIndex) * elementSize;
                JavaKind wordKind = providers.getWordTypes().getWordKind();
                ConstantNode offsetConstant = kit.createConstant(JavaConstant.forIntegerKind(wordKind, offset), wordKind);
                OffsetAddressNode address = kit.unique(new OffsetAddressNode(array, offsetConstant));
                Stamp readStamp = StampFactory.forKind(readKind);
                ValueNode value = kit.append(new CInterfaceReadNode(address, LocationIdentity.any(), readStamp, BarrierType.NONE, "args[" + i + "]"));
                if (kind == JavaKind.Float && readKind == JavaKind.Double) {
                    value = kit.unique(new FloatConvertNode(FloatConvert.D2F, value));
                } else if (kind.isObject()) {
                    value = kit.unboxHandle(value);
                }
                args.add(value);
            }
        } else if (callVariant == CallVariant.VARARGS) {
            for (int i = firstParamIndex; i < count; i++) {
                JavaKind kind = invokeSignature.getParameterKind(i);
                assert kind == kind.getStackKind() : "other conversions and bit masking than below must happen in JNIJavaCallMethod";
                JavaKind loadKind = kind;
                if (loadKind == JavaKind.Float) {
                    loadKind = JavaKind.Double; // C varargs promote float to double (C99 6.5.2.2-6)
                }
                ValueNode value = kit.loadLocal(slotIndex, loadKind);
                if (kind == JavaKind.Float) {
                    value = kit.unique(new FloatConvertNode(FloatConvert.D2F, value));
                } else if (kind.isObject()) {
                    value = kit.unboxHandle(value);
                }
                args.add(value);
                slotIndex += loadKind.getSlotCount();
            }
        } else if (callVariant == CallVariant.VA_LIST) {
            ValueNode valist = kit.loadLocal(slotIndex, metaAccess.lookupJavaType(WordBase.class).getJavaKind());
            for (int i = firstParamIndex; i < count; i++) {
                JavaKind kind = invokeSignature.getParameterKind(i);
                assert kind == kind.getStackKind() : "other conversions and bit masking than below must happen in JNIJavaCallMethod";
                JavaKind loadKind = kind;
                if (loadKind.isObject()) {
                    loadKind = providers.getWordTypes().getWordKind();
                }
                ValueNode value = kit.append(new VaListNextArgNode(loadKind, valist));
                if (kind.isObject()) {
                    value = kit.unboxHandle(value);
                }
                args.add(value);
            }
        } else {
            throw VMError.unsupportedFeature("Call variant: " + callVariant);
        }
        return args;
    }

    /** Returns the index of the frame state local for the first actual (non-receiver) argument. */
    private int firstArgumentSlotIndex(MetaAccessProvider metaAccess) {
        return metaAccess.lookupJavaType(JNIEnvironment.class).getJavaKind().getSlotCount() +
                        metaAccess.lookupJavaType(JNIObjectHandle.class).getJavaKind().getSlotCount() +
                        (nonVirtual ? metaAccess.lookupJavaType(JNIObjectHandle.class).getJavaKind().getSlotCount() : 0) +
                        metaAccess.lookupJavaType(JNIMethodId.class).getJavaKind().getSlotCount();
    }
}
