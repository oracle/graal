/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Executable;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.java.FrameStateBuilder;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.FloatConvertNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.nodes.calc.ObjectEqualsNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.extended.BytecodeExceptionNode;
import org.graalvm.compiler.nodes.extended.BytecodeExceptionNode.BytecodeExceptionKind;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.graal.nodes.CEntryPointEnterNode;
import com.oracle.svm.core.graal.nodes.CEntryPointLeaveNode;
import com.oracle.svm.core.graal.nodes.CEntryPointLeaveNode.LeaveAction;
import com.oracle.svm.core.graal.nodes.CInterfaceReadNode;
import com.oracle.svm.core.graal.nodes.ReadCallerStackPointerNode;
import com.oracle.svm.core.graal.nodes.VaListNextArgNode;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.info.ElementInfo;
import com.oracle.svm.hosted.c.info.StructFieldInfo;
import com.oracle.svm.hosted.c.info.StructInfo;
import com.oracle.svm.hosted.code.EntryPointCallStubMethod;
import com.oracle.svm.hosted.code.FactoryMethodSupport;
import com.oracle.svm.hosted.code.SimpleSignature;
import com.oracle.svm.jni.JNIJavaCallWrappers;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIMethodId;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;
import com.oracle.svm.jni.nativeapi.JNIValue;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Generated code for calling a specific Java method from native code. The wrapper takes care of
 * transitioning to a Java context and back to native code, for catching and retaining unhandled
 * exceptions, and if required, for unboxing object handle arguments and boxing an object return
 * value.
 *
 * @see <a href=
 *      "https://docs.oracle.com/javase/8/docs/technotes/guides/jni/spec/functions.html">Java 8 JNI
 *      functions documentation</a>
 * @see <a href="https://docs.oracle.com/en/java/javase/11/docs/specs/jni/functions.html">Java 11
 *      JNI functions documentation</a>
 */
public final class JNIJavaCallWrapperMethod extends EntryPointCallStubMethod {

    public enum CallVariant {
        VARARGS,
        ARRAY,
        VA_LIST,
    }

    private final NativeLibraries nativeLibs;

    private final Executable reflectMethod;
    private final CallVariant callVariant;
    private final boolean nonVirtual;

    public JNIJavaCallWrapperMethod(Executable reflectMethod, CallVariant callVariant, boolean nonVirtual, MetaAccessProvider metaAccess, NativeLibraries nativeLibs) {
        super(createName(reflectMethod, callVariant, nonVirtual),
                        metaAccess.lookupJavaType(JNIJavaCallWrappers.class),
                        createSignature(reflectMethod, callVariant, nonVirtual, metaAccess),
                        JNIJavaCallWrappers.getConstantPool(metaAccess));
        assert !nonVirtual || !Modifier.isStatic(reflectMethod.getModifiers());
        this.reflectMethod = reflectMethod;
        this.nativeLibs = nativeLibs;
        this.callVariant = callVariant;
        this.nonVirtual = nonVirtual;
    }

    private static String createName(Executable reflectMethod, CallVariant callVariant, boolean nonVirtual) {
        return "jniInvoke_" + callVariant.name() + (nonVirtual ? "_Nonvirtual" : "") + "_" + SubstrateUtil.uniqueShortName(reflectMethod);
    }

    private static SimpleSignature createSignature(Executable reflectMethod, CallVariant callVariant, boolean nonVirtual, MetaAccessProvider metaAccess) {
        ResolvedJavaType objectHandle = metaAccess.lookupJavaType(JNIObjectHandle.class);
        List<JavaType> args = new ArrayList<>();
        args.add(metaAccess.lookupJavaType(JNIEnvironment.class));
        args.add(objectHandle); // this (instance method) or class (static method)
        if (nonVirtual) {
            args.add(objectHandle); // class of implementation to invoke
        }
        args.add(metaAccess.lookupJavaType(JNIMethodId.class));
        ResolvedJavaMethod targetMethod = metaAccess.lookupJavaMethod(reflectMethod);
        Signature targetSignature = targetMethod.getSignature();
        if (callVariant == CallVariant.VARARGS) {
            for (JavaType targetArg : targetSignature.toParameterTypes(null)) {
                JavaKind kind = targetArg.getJavaKind();
                if (kind.isObject()) {
                    args.add(objectHandle);
                } else if (kind == JavaKind.Float) { // C varargs promote float to double
                    args.add(metaAccess.lookupJavaType(JavaKind.Double.toJavaClass()));
                } else { // C varargs promote sub-words to int
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
        if (returnType.getJavaKind().isObject() || targetMethod.isConstructor()) {
            // Constructor: returns `this` to implement NewObject
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

        ResolvedJavaMethod invokeMethod = providers.getMetaAccess().lookupJavaMethod(reflectMethod);
        Signature invokeSignature = invokeMethod.getSignature();
        List<Pair<ValueNode, ResolvedJavaType>> argsWithTypes = loadAndUnboxArguments(kit, providers, invokeSignature);

        /* Unbox receiver handle if there is one. */
        ValueNode unboxedReceiver = null;
        if (invokeMethod.hasReceiver()) {
            int javaIndex = metaAccess.lookupJavaType(JNIEnvironment.class).getJavaKind().getSlotCount();
            JavaKind handleKind = metaAccess.lookupJavaType(JNIObjectHandle.class).getJavaKind();
            ValueNode handle = kit.loadLocal(javaIndex, handleKind);
            unboxedReceiver = kit.unboxHandle(handle);
        }

        /*
         * Dynamically type-check the call arguments. Use a chain of IfNodes rather than a logic
         * expression that can become too complex for the static analysis.
         */
        List<EndNode> illegalTypeEnds = new ArrayList<>();
        int argIndex = invokeMethod.hasReceiver() ? 1 : 0;
        ValueNode[] args = new ValueNode[argIndex + argsWithTypes.size()];
        for (Pair<ValueNode, ResolvedJavaType> argsWithType : argsWithTypes) {
            ValueNode value = argsWithType.getLeft();
            ResolvedJavaType type = argsWithType.getRight();
            if (!type.isPrimitive() && !type.isJavaLangObject()) {
                value = typeChecked(kit, value, type, illegalTypeEnds, false);
            }
            args[argIndex++] = value;
        }

        /* Dynamically type-check the receiver type, and invoke the method if it matches. */
        InvokeKind invokeKind = invokeMethod.isStatic() ? InvokeKind.Static : //
                        ((nonVirtual || invokeMethod.isConstructor()) ? InvokeKind.Special : InvokeKind.Virtual);
        ValueNode returnValue;
        if (!invokeMethod.hasReceiver()) {
            returnValue = createMethodCall(kit, invokeMethod, invokeKind, state, args);
        } else if (invokeMethod.isConstructor()) {
            /*
             * If the target method is a constructor, we can narrow down the JNI call to two
             * possible types of JNI functions: `Call<Type>Method` or `NewObject`.
             *
             * To distinguish `Call<Type>Method` from `NewObject`, we can look at JNI call parameter
             * 1, which is either `jobject obj` (the receiver object) in the case of
             * `Call<Type>Method`, or `jclass clazz` (the hub of the receiver object) in the case of
             * `NewObject`.
             */
            ResolvedJavaType receiverClass = invokeMethod.getDeclaringClass();
            Constant hub = providers.getConstantReflection().asObjectHub(receiverClass);
            ConstantNode hubNode = kit.createConstant(hub, JavaKind.Object);
            ObjectEqualsNode isNewObjectCall = kit.unique(new ObjectEqualsNode(unboxedReceiver, hubNode));
            kit.startIf(isNewObjectCall, BranchProbabilityNode.FAST_PATH_PROFILE);
            kit.thenPart();
            ValueNode createdObjectOrNull = createNewObjectCall(metaAccess, kit, invokeMethod, state, args);
            kit.elsePart();
            args[0] = typeChecked(kit, unboxedReceiver, invokeMethod.getDeclaringClass(), illegalTypeEnds, true);
            ValueNode unboxedReceiverOrNull = createMethodCall(kit, invokeMethod, invokeKind, state, args);
            AbstractMergeNode merge = kit.endIf();
            merge.setStateAfter(kit.getFrameState().create(kit.bci(), merge));
            returnValue = kit.unique(new ValuePhiNode(StampFactory.object(), merge, new ValueNode[]{createdObjectOrNull, unboxedReceiverOrNull}));
        } else {
            // This is a JNI call via `Call<Type>Method` to a non-static method
            args[0] = typeChecked(kit, unboxedReceiver, invokeMethod.getDeclaringClass(), illegalTypeEnds, true);
            returnValue = createMethodCall(kit, invokeMethod, invokeKind, state, args);
        }
        JavaKind returnKind = (returnValue != null) ? returnValue.getStackKind() : JavaKind.Void;

        if (!illegalTypeEnds.isEmpty()) {
            /*
             * The following is awkward because we need to maintain a last fixed node in GraphKit
             * while building non-sequential control flow, so we append nodes and rewire control
             * flow later. Be careful when making any changes.
             */
            BeginNode afterSuccess = kit.append(new BeginNode());

            ValueNode exception;
            if (illegalTypeEnds.size() == 1) {
                BeginNode illegalTypeBegin = kit.append(new BeginNode());
                illegalTypeBegin.replaceAtPredecessor(null);

                EndNode end = illegalTypeEnds.get(0);
                exception = (BytecodeExceptionNode) end.predecessor();
                end.replaceAtPredecessor(illegalTypeBegin);
                end.safeDelete();
            } else {
                MergeNode illegalTypesMerge = kit.append(new MergeNode());
                ValuePhiNode phi = kit.getGraph().addWithoutUnique(new ValuePhiNode(StampFactory.object(), illegalTypesMerge));
                for (EndNode end : illegalTypeEnds) {
                    illegalTypesMerge.addForwardEnd(end);
                    phi.addInput((BytecodeExceptionNode) end.predecessor());
                }
                illegalTypesMerge.setStateAfter(state.create(kit.bci(), illegalTypesMerge));
                phi.inferStamp();
                exception = phi;
            }
            kit.setPendingException(exception);
            BeginNode afterIllegalType = kit.append(new BeginNode());

            MergeNode returnMerge = kit.append(new MergeNode());
            EndNode afterSuccessEnd = kit.add(new EndNode());
            afterSuccess.setNext(afterSuccessEnd);
            returnMerge.addForwardEnd(afterSuccessEnd);
            EndNode afterIllegalTypeEnd = kit.add(new EndNode());
            afterIllegalType.setNext(afterIllegalTypeEnd);
            returnMerge.addForwardEnd(afterIllegalTypeEnd);

            if (returnValue != null) {
                // Create Phi for the return value, with null/zero/false on the exception branch.
                ValueNode typeMismatchResult = kit.unique(ConstantNode.defaultForKind(returnValue.getStackKind()));
                ValueNode[] inputs = {returnValue, typeMismatchResult};
                returnValue = kit.getGraph().addWithoutUnique(new ValuePhiNode(returnValue.stamp(NodeView.DEFAULT), returnMerge, inputs));
                state.push(returnKind, returnValue);
                returnMerge.setStateAfter(state.create(kit.bci(), returnMerge));
                state.pop(returnKind);
            } else {
                returnMerge.setStateAfter(state.create(kit.bci(), returnMerge));
            }
            kit.appendStateSplitProxy(state);
        }

        if (returnKind.isObject()) {
            returnValue = kit.boxObjectInLocalHandle(returnValue);
        }

        CEntryPointLeaveNode leave = new CEntryPointLeaveNode(LeaveAction.Leave);
        kit.append(leave);
        kit.createReturn(returnValue, returnKind);

        return kit.finalizeGraph();
    }

    /**
     * Builds the object allocation for a JNI {@code NewObject} call, returning a node that contains
     * the created object or for {@code null} when an exception occurred (in which case the
     * exception becomes a JNI pending exception).
     */
    private static ValueNode createNewObjectCall(UniverseMetaAccess metaAccess, JNIGraphKit kit, ResolvedJavaMethod constructor, FrameStateBuilder state, ValueNode... argsWithReceiver) {
        assert constructor.isConstructor() : "Cannot create a NewObject call to the non-constructor method " + constructor;

        ResolvedJavaMethod factoryMethod = FactoryMethodSupport.singleton().lookup(metaAccess, constructor, false);

        int bci = kit.bci();
        ValueNode[] argsWithoutReceiver = Arrays.copyOfRange(argsWithReceiver, 1, argsWithReceiver.length);
        ValueNode createdObject = startInvokeWithRetainedException(kit, factoryMethod, InvokeKind.Static, state, bci, argsWithoutReceiver);
        AbstractMergeNode merge = kit.endInvokeWithException();
        merge.setStateAfter(state.create(bci, merge));

        Stamp objectStamp = StampFactory.forDeclaredType(null, constructor.getDeclaringClass(), true).getTrustedStamp();
        ValueNode exceptionValue = kit.unique(ConstantNode.defaultForKind(JavaKind.Object));
        return kit.getGraph().addWithoutUnique(new ValuePhiNode(objectStamp, merge, new ValueNode[]{createdObject, exceptionValue}));
    }

    /**
     * Builds a JNI {@code Call<Type>Method} call, returning a node that contains the return value
     * or null/zero/false when an exception occurred (in which case the exception becomes a JNI
     * pending exception).
     */
    private static ValueNode createMethodCall(JNIGraphKit kit, ResolvedJavaMethod invokeMethod, InvokeKind invokeKind, FrameStateBuilder state, ValueNode... args) {
        int bci = kit.bci();
        InvokeWithExceptionNode invoke = startInvokeWithRetainedException(kit, invokeMethod, invokeKind, state, bci, args);
        AbstractMergeNode invokeMerge = kit.endInvokeWithException();

        if (invoke.getStackKind() == JavaKind.Void && !invokeMethod.isConstructor()) {
            invokeMerge.setStateAfter(state.create(bci, invokeMerge));
            return null;
        }

        ValueNode successValue = invokeMethod.isConstructor() ? args[0] : invoke;
        ValueNode exceptionValue = kit.unique(ConstantNode.defaultForKind(successValue.getStackKind()));
        ValueNode[] inputs = {successValue, exceptionValue};
        ValueNode returnValue = kit.getGraph().addWithoutUnique(new ValuePhiNode(successValue.stamp(NodeView.DEFAULT), invokeMerge, inputs));
        JavaKind returnKind = returnValue.getStackKind();
        state.push(returnKind, returnValue);
        invokeMerge.setStateAfter(state.create(bci, invokeMerge));
        state.pop(returnKind);
        return returnValue;
    }

    private static InvokeWithExceptionNode startInvokeWithRetainedException(JNIGraphKit kit, ResolvedJavaMethod invokeMethod, InvokeKind kind, FrameStateBuilder state, int bci, ValueNode... args) {
        ValueNode formerPendingException = kit.getAndClearPendingException();
        InvokeWithExceptionNode invoke = kit.startInvokeWithException(invokeMethod, kind, state, bci, args);

        kit.noExceptionPart(); // no new exception was thrown, restore the formerly pending one
        kit.setPendingException(formerPendingException);

        kit.exceptionPart();
        ExceptionObjectNode exceptionObject = kit.exceptionObject();
        kit.setPendingException(exceptionObject);

        return invoke;
    }

    private static PiNode typeChecked(JNIGraphKit kit, ValueNode uncheckedValue, ResolvedJavaType type, List<EndNode> illegalTypeEnds, boolean isReceiver) {
        ValueNode value = uncheckedValue;
        if (isReceiver && !StampTool.isPointerNonNull(value)) {
            IfNode ifNode = kit.startIf(kit.unique(IsNullNode.create(value)), BranchProbabilityNode.SLOW_PATH_PROFILE);
            kit.thenPart();
            kit.append(kit.createBytecodeExceptionObjectNode(BytecodeExceptionKind.NULL_POINTER, false));
            illegalTypeEnds.add(kit.append(new EndNode()));
            kit.endIf();
            Stamp nonNullStamp = value.stamp(NodeView.DEFAULT).improveWith(StampFactory.objectNonNull());
            value = kit.append(new PiNode(value, nonNullStamp, ifNode.falseSuccessor()));
        }
        TypeReference typeRef = TypeReference.createTrusted(kit.getAssumptions(), type);
        LogicNode instanceOf = kit.append(InstanceOfNode.createAllowNull(typeRef, value, null, null));
        IfNode ifNode = kit.startIf(instanceOf, BranchProbabilityNode.FAST_PATH_PROFILE);
        kit.elsePart();
        ConstantNode typeNode = kit.createConstant(kit.getConstantReflection().asJavaClass(type), JavaKind.Object);
        kit.createBytecodeExceptionObjectNode(BytecodeExceptionKind.CLASS_CAST, false, value, typeNode);
        illegalTypeEnds.add(kit.append(new EndNode()));
        kit.endIf();
        Stamp checkedStamp = value.stamp(NodeView.DEFAULT).improveWith(StampFactory.objectNonNull(typeRef));
        return kit.unique(new PiNode(value, checkedStamp, ifNode.trueSuccessor()));
    }

    /**
     * Creates {@linkplain ValueNode IR nodes} for the arguments passed to the JNI call. The
     * arguments do not include the receiver of the call, but only the actual arguments passed to
     * the JNI target function.
     *
     * @return List of created argument nodes and their type
     */
    private List<Pair<ValueNode, ResolvedJavaType>> loadAndUnboxArguments(JNIGraphKit kit, HostedProviders providers, Signature invokeSignature) {
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        List<Pair<ValueNode, ResolvedJavaType>> args = new ArrayList<>();
        int javaIndex = argumentsJavaIndex(metaAccess);
        int count = invokeSignature.getParameterCount(false);
        // Windows and iOS CallVariant.VA_LIST is identical to CallVariant.ARRAY
        // iOS CallVariant.VARARGS stores values as an array on the stack
        if ((Platform.includedIn(Platform.DARWIN_AARCH64.class) && (callVariant == CallVariant.VARARGS || callVariant == CallVariant.VA_LIST)) ||
                        (Platform.includedIn(Platform.WINDOWS.class) && callVariant == CallVariant.VA_LIST) || callVariant == CallVariant.ARRAY) {
            ResolvedJavaType elementType = metaAccess.lookupJavaType(JNIValue.class);
            int elementSize = SizeOf.get(JNIValue.class);
            ValueNode array;
            if (callVariant == CallVariant.VARARGS) {
                array = kit.append(new ReadCallerStackPointerNode());
            } else {
                array = kit.loadLocal(javaIndex, elementType.getJavaKind());
            }
            for (int i = 0; i < count; i++) {
                ResolvedJavaType type = (ResolvedJavaType) invokeSignature.getParameterType(i, null);
                JavaKind kind = type.getJavaKind();
                JavaKind readKind = callVariant == CallVariant.ARRAY ? kind : kind.getStackKind();
                if (readKind == JavaKind.Float && (callVariant == CallVariant.VARARGS || callVariant == CallVariant.VA_LIST)) {
                    readKind = JavaKind.Double;
                }
                StructFieldInfo fieldInfo = getJNIValueOffsetOf(elementType, readKind);
                int offset = i * elementSize + fieldInfo.getOffsetInfo().getProperty();
                JavaKind wordKind = providers.getWordTypes().getWordKind();
                ConstantNode offsetConstant = kit.createConstant(JavaConstant.forIntegerKind(wordKind, offset), wordKind);
                OffsetAddressNode address = kit.unique(new OffsetAddressNode(array, offsetConstant));
                LocationIdentity locationIdentity = fieldInfo.getLocationIdentity();
                if (locationIdentity == null) {
                    locationIdentity = LocationIdentity.any();
                }
                Stamp readStamp = getNarrowStamp(providers, readKind);
                ValueNode value = kit.append(new CInterfaceReadNode(address, locationIdentity, readStamp, BarrierType.NONE, "args[" + i + "]"));
                if (kind == JavaKind.Float && (callVariant == CallVariant.VARARGS || callVariant == CallVariant.VA_LIST)) {
                    value = kit.unique(new FloatConvertNode(FloatConvert.D2F, value));
                } else if (kind.isObject()) {
                    value = kit.unboxHandle(value);
                } else if (kind == JavaKind.Boolean) {
                    value = convertToBoolean(kit, value);
                } else if (kind != kind.getStackKind() && callVariant == CallVariant.ARRAY) {
                    value = maskSubWordValue(kit, value, kind);
                }
                args.add(Pair.create(value, type));
            }
        } else if (callVariant == CallVariant.VARARGS) {
            for (int i = 0; i < count; i++) {
                ResolvedJavaType type = (ResolvedJavaType) invokeSignature.getParameterType(i, null);
                JavaKind kind = type.getJavaKind();
                JavaKind loadKind = kind;
                if (loadKind == JavaKind.Float) { // C varargs promote float to double
                    loadKind = JavaKind.Double;
                }
                ValueNode value = kit.loadLocal(javaIndex, loadKind);
                if (kind == JavaKind.Float) {
                    value = kit.unique(new FloatConvertNode(FloatConvert.D2F, value));
                } else if (kind.isObject()) {
                    value = kit.unboxHandle(value);
                } else if (kind == JavaKind.Boolean) {
                    value = convertToBoolean(kit, value);
                }
                args.add(Pair.create(value, type));
                javaIndex += loadKind.getSlotCount();
            }
        } else if (callVariant == CallVariant.VA_LIST) {
            ValueNode valist = kit.loadLocal(javaIndex, metaAccess.lookupJavaType(WordBase.class).getJavaKind());
            for (int i = 0; i < count; i++) {
                ResolvedJavaType type = (ResolvedJavaType) invokeSignature.getParameterType(i, null);
                JavaKind kind = type.getJavaKind();
                JavaKind loadKind = kind.getStackKind();
                if (loadKind.isObject()) {
                    loadKind = providers.getWordTypes().getWordKind();
                }
                ValueNode value = kit.append(new VaListNextArgNode(loadKind, valist));
                if (kind.isObject()) {
                    value = kit.unboxHandle(value);
                } else if (kind == JavaKind.Boolean) {
                    value = convertToBoolean(kit, value);
                }
                args.add(Pair.create(value, type));
            }
        } else {
            throw VMError.unsupportedFeature("Call variant: " + callVariant);
        }
        return args;
    }

    /**
     * Returns the index of the frame state local for the first argument.
     */
    private int argumentsJavaIndex(MetaAccessProvider metaAccess) {
        return metaAccess.lookupJavaType(JNIEnvironment.class).getJavaKind().getSlotCount() +
                        metaAccess.lookupJavaType(JNIObjectHandle.class).getJavaKind().getSlotCount() +
                        (nonVirtual ? metaAccess.lookupJavaType(JNIObjectHandle.class).getJavaKind().getSlotCount() : 0) +
                        metaAccess.lookupJavaType(JNIMethodId.class).getJavaKind().getSlotCount();
    }

    /** Converts 0 to {@code false}, and 1-255 to {@code true}. */
    private static ValueNode convertToBoolean(JNIGraphKit kit, ValueNode value) {
        ValueNode maskedValue = maskSubWordValue(kit, value, JavaKind.Boolean);
        LogicNode isZero = IntegerEqualsNode.create(maskedValue, ConstantNode.forInt(0), NodeView.DEFAULT);
        return kit.append(ConditionalNode.create(isZero, ConstantNode.forBoolean(false), ConstantNode.forBoolean(true), NodeView.DEFAULT));
    }

    /** Masks a sub-word value to ensure that unused high bits are indeed cleared. */
    private static ValueNode maskSubWordValue(JNIGraphKit kit, ValueNode value, JavaKind kind) {
        assert kind != kind.getStackKind();
        ValueNode narrow = kit.append(NarrowNode.create(value, kind.getByteCount() * Byte.SIZE, NodeView.DEFAULT));
        if (kind.isUnsigned()) {
            return kit.append(ZeroExtendNode.create(narrow, Integer.SIZE, NodeView.DEFAULT));
        } else {
            return kit.append(SignExtendNode.create(narrow, Integer.SIZE, NodeView.DEFAULT));
        }
    }

    private static Stamp getNarrowStamp(HostedProviders providers, JavaKind kind) {
        if (kind != kind.getStackKind()) {
            // avoid widened stamp to prevent reading undefined bits
            return StampFactory.forInteger(kind.getByteCount() * Byte.SIZE);
        } else if (kind.isObject()) {
            ResolvedJavaType objectHandle = providers.getMetaAccess().lookupJavaType(JNIObjectHandle.class);
            return providers.getWordTypes().getWordStamp(objectHandle);
        } else {
            return StampFactory.forKind(kind);
        }
    }

    private StructFieldInfo getJNIValueOffsetOf(ResolvedJavaType jniValueType, JavaKind kind) {
        String name = String.valueOf(kind.isObject() ? 'l' : Character.toLowerCase(kind.getTypeChar()));
        StructInfo structInfo = (StructInfo) nativeLibs.findElementInfo(jniValueType);
        for (ElementInfo elementInfo : structInfo.getChildren()) {
            if (elementInfo instanceof StructFieldInfo) {
                StructFieldInfo fieldInfo = (StructFieldInfo) elementInfo;
                if (name.equals(fieldInfo.getName())) {
                    return fieldInfo;
                }
            }
        }
        throw VMError.shouldNotReachHere();
    }
}
