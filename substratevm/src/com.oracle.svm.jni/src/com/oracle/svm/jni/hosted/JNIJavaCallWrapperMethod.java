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

// Checkstyle: allow reflection

import java.lang.reflect.Executable;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.java.FrameStateBuilder;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.FloatConvertNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.calc.ObjectEqualsNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.OS;
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
import com.oracle.svm.hosted.code.SimpleSignature;
import com.oracle.svm.jni.JNIJavaCallWrappers;
import com.oracle.svm.jni.access.JNINativeLinkage;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIMethodId;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;
import com.oracle.svm.jni.nativeapi.JNIValue;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
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
public final class JNIJavaCallWrapperMethod extends JNIGeneratedMethod {

    public enum CallVariant {
        VARARGS,
        ARRAY,
        VA_LIST,
    }

    private static final ClassCastException cachedArgumentClassCastException;
    static {
        cachedArgumentClassCastException = new ClassCastException("Object argument to JNI call does not match type in Java signature");
        cachedArgumentClassCastException.setStackTrace(new StackTraceElement[0]);
    }

    private static final NullPointerException cachedReceiverNullPointerException;
    static {
        cachedReceiverNullPointerException = new NullPointerException("The receiver of a JNI call must not be null");
        cachedArgumentClassCastException.setStackTrace(new StackTraceElement[0]);
    }

    private final NativeLibraries nativeLibs;

    private final ResolvedJavaType declaringClass;
    private final ConstantPool constantPool;
    private final Executable reflectMethod;
    private final CallVariant callVariant;
    private final boolean nonVirtual;
    private final ResolvedJavaMethod targetMethod;
    private final Signature signature;

    public JNIJavaCallWrapperMethod(Executable reflectMethod, CallVariant callVariant, boolean nonVirtual, MetaAccessProvider metaAccess, NativeLibraries nativeLibs) {
        assert !nonVirtual || !Modifier.isStatic(reflectMethod.getModifiers());
        this.declaringClass = metaAccess.lookupJavaType(JNIJavaCallWrappers.class);
        this.constantPool = JNIJavaCallWrappers.getConstantPool(metaAccess);
        this.reflectMethod = reflectMethod;
        this.nativeLibs = nativeLibs;
        this.callVariant = callVariant;
        this.nonVirtual = nonVirtual;
        this.targetMethod = metaAccess.lookupJavaMethod(reflectMethod);
        this.signature = createSignature(metaAccess);
    }

    private SimpleSignature createSignature(MetaAccessProvider metaAccess) {
        ResolvedJavaType objectHandle = metaAccess.lookupJavaType(JNIObjectHandle.class);
        List<JavaType> args = new ArrayList<>();
        args.add(metaAccess.lookupJavaType(JNIEnvironment.class));
        args.add(objectHandle); // this (instance method) or class (static method)
        if (nonVirtual) {
            args.add(objectHandle); // class of implementation to invoke
        }
        args.add(metaAccess.lookupJavaType(JNIMethodId.class));
        Signature targetSignature = targetMethod.getSignature();
        if (callVariant == CallVariant.VARARGS) {
            for (JavaType targetArg : targetSignature.toParameterTypes(null)) {
                JavaKind kind = targetArg.getJavaKind();
                if (kind.isObject()) {
                    args.add(objectHandle);
                } else if (kind == JavaKind.Float) { // C varargs promote float to double
                    args.add(metaAccess.lookupJavaType(JavaKind.Double.toJavaClass()));
                } else {
                    args.add(targetArg);
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
        StructuredGraph graph = kit.getGraph();
        FrameStateBuilder state = new FrameStateBuilder(null, method, graph);
        state.initializeForMethodStart(null, true, providers.getGraphBuilderPlugins());

        JavaKind vmThreadKind = metaAccess.lookupJavaType(JNIEnvironment.class).getJavaKind();
        ValueNode vmThread = kit.loadLocal(0, vmThreadKind);
        kit.append(CEntryPointEnterNode.enter(vmThread));

        ResolvedJavaMethod invokeMethod = providers.getMetaAccess().lookupJavaMethod(reflectMethod);
        Signature invokeSignature = invokeMethod.getSignature();
        List<Pair<ValueNode, ResolvedJavaType>> argsWithTypes = loadAndUnboxArguments(kit, providers, invokeSignature);

        /* Unbox handle if there is one. */
        ValueNode unboxedHandle = null; // only available if there is a receiver
        if (invokeMethod.hasReceiver()) {
            int javaIndex = metaAccess.lookupJavaType(JNIEnvironment.class).getJavaKind().getSlotCount();
            JavaKind handleKind = metaAccess.lookupJavaType(JNIObjectHandle.class).getJavaKind();
            ValueNode handle = kit.loadLocal(javaIndex, handleKind);
            unboxedHandle = kit.unboxHandle(handle);
        }

        /* Dynamically type-check the call arguments. */
        IfNode ifNode = kit.startIf(null, BranchProbabilityNode.FAST_PATH_PROBABILITY);
        kit.thenPart();
        LogicNode typeChecks = LogicConstantNode.tautology(kit.getGraph());
        int argIndex = invokeMethod.hasReceiver() ? 1 : 0;
        ValueNode[] args = new ValueNode[argIndex + argsWithTypes.size()];
        for (Pair<ValueNode, ResolvedJavaType> argsWithType : argsWithTypes) {
            ValueNode value = argsWithType.getLeft();
            ResolvedJavaType type = argsWithType.getRight();
            if (!type.isPrimitive() && !type.isJavaLangObject()) {
                TypeReference typeRef = TypeReference.createTrusted(kit.getAssumptions(), type);
                LogicNode instanceOf = kit.unique(InstanceOfNode.createAllowNull(typeRef, value, null, null));
                typeChecks = LogicNode.and(typeChecks, instanceOf, BranchProbabilityNode.FAST_PATH_PROBABILITY);
                FixedGuardNode guard = kit.append(new FixedGuardNode(instanceOf, DeoptimizationReason.ClassCastException, DeoptimizationAction.None, false));
                value = kit.append(PiNode.create(value, StampFactory.object(typeRef), guard));
            }
            args[argIndex++] = value;
        }
        ifNode.setCondition(typeChecks); // safe because logic nodes are floating

        /* Dynamically type-check the receiver type, and invoke the method if it matches. */
        InvokeKind invokeKind = invokeMethod.isStatic() ? InvokeKind.Static : //
                        ((nonVirtual || invokeMethod.isConstructor()) ? InvokeKind.Special : InvokeKind.Virtual);
        JNIJavaCallWrapperMethodSupport support = ImageSingletons.lookup(JNIJavaCallWrapperMethodSupport.class);
        ValueNode returnValue;
        if (!invokeMethod.hasReceiver()) {
            returnValue = support.createCallTypeMethodCall(kit, invokeMethod, invokeKind, state, args);
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
            ObjectEqualsNode isNewObjectCall = kit.unique(new ObjectEqualsNode(unboxedHandle, hubNode));
            kit.startIf(isNewObjectCall, BranchProbabilityNode.FAST_PATH_PROBABILITY);
            kit.thenPart();
            ValueNode createdReceiverOrException = support.createNewObjectCall(kit, invokeMethod, state, args);
            kit.elsePart();
            args[0] = unboxedHandle;
            ValueNode unboxedReceiverOrException = typeCheckReceiverAndCreateCallTypeMethod(kit, invokeMethod, invokeKind, state, args);
            AbstractMergeNode merge = kit.endIf();
            merge.setStateAfter(kit.getFrameState().create(kit.bci(), merge));
            returnValue = kit.unique(new ValuePhiNode(StampFactory.object(), merge, new ValueNode[]{createdReceiverOrException, unboxedReceiverOrException}));
        } else {
            /*
             * This is a JNI call to `Call<Type>Method` to a non-static method. The instanceof check
             * on the receiver can be placed in the same `if` as the type check for the method
             * arguments (as long as the handle was unboxed before the if, because all other nodes
             * are floating).
             */
            TypeReference expectedReceiverType = TypeReference.createTrusted(kit.getAssumptions(), invokeMethod.getDeclaringClass());
            LogicNode instanceOf = kit.getGraph().addOrUniqueWithInputs(InstanceOfNode.create(expectedReceiverType, unboxedHandle));
            typeChecks = LogicConstantNode.and(typeChecks, instanceOf, BranchProbabilityNode.FAST_PATH_PROBABILITY);
            ifNode.setCondition(typeChecks); // safe because logic nodes are floating
            FixedGuardNode guard = kit.append(new FixedGuardNode(instanceOf, DeoptimizationReason.ClassCastException, DeoptimizationAction.None, false));
            ValueNode piNode = PiNode.create(unboxedHandle, StampFactory.object(expectedReceiverType), guard);
            if (piNode != unboxedHandle) {
                kit.append(piNode);
            }
            args[0] = unboxedHandle;
            returnValue = support.createCallTypeMethodCall(kit, invokeMethod, invokeKind, state, args);
        }

        /* If argument types are wrong, throw an exception. */
        kit.elsePart();
        ConstantNode exceptionObject = kit.createObject(cachedArgumentClassCastException);
        kit.setPendingException(exceptionObject);

        AbstractMergeNode merge = kit.endIf();
        JavaKind returnKind = returnValue != null ? returnValue.getStackKind() : JavaKind.Void;
        if (returnValue != null) {
            /* Create Phi for the return value, with placeholder value on the exception branch. */
            ValueNode typeMismatchResult = kit.unique(ConstantNode.defaultForKind(returnValue.getStackKind()));
            ValueNode[] inputs = {returnValue, typeMismatchResult};
            returnValue = kit.getGraph().addWithoutUnique(new ValuePhiNode(returnValue.stamp(NodeView.DEFAULT), merge, inputs));
            state.push(returnKind, returnValue);
            merge.setStateAfter(state.create(kit.bci(), merge));
            state.pop(returnKind);
            if (returnKind.isObject()) {
                returnValue = kit.boxObjectInLocalHandle(returnValue);
            }
        } else {
            merge.setStateAfter(state.create(kit.bci(), merge));
        }

        kit.appendStateSplitProxy(state);
        CEntryPointLeaveNode leave = new CEntryPointLeaveNode(LeaveAction.Leave);
        kit.append(leave);
        kit.createReturn(returnValue, returnKind);

        return kit.finalizeGraph();
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
        if ((OS.getCurrent() == OS.DARWIN && Platform.includedIn(Platform.AARCH64.class) && (callVariant == CallVariant.VARARGS || callVariant == CallVariant.VA_LIST)) ||
                        (OS.getCurrent() == OS.WINDOWS && callVariant == CallVariant.VA_LIST) || callVariant == CallVariant.ARRAY) {
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
                JavaKind readKind = type.getJavaKind();
                if (readKind == JavaKind.Float && (callVariant == CallVariant.VARARGS || callVariant == CallVariant.VA_LIST)) {
                    readKind = JavaKind.Double;
                }
                StructFieldInfo fieldInfo = getJNIValueOffsetOf(elementType, readKind);
                int offset = i * elementSize + fieldInfo.getOffsetInfo().getProperty();
                ConstantNode offsetConstant = kit.createConstant(JavaConstant.forInt(offset), providers.getWordTypes().getWordKind());
                OffsetAddressNode address = kit.unique(new OffsetAddressNode(array, offsetConstant));
                LocationIdentity locationIdentity = fieldInfo.getLocationIdentity();
                if (locationIdentity == null) {
                    locationIdentity = LocationIdentity.any();
                }
                Stamp readStamp = getNarrowStamp(providers, readKind);
                ValueNode value = kit.append(new CInterfaceReadNode(address, locationIdentity, readStamp, BarrierType.NONE, "args[" + i + "]"));
                JavaKind stackKind = readKind.getStackKind();
                if (type.getJavaKind() == JavaKind.Float && (callVariant == CallVariant.VARARGS || callVariant == CallVariant.VA_LIST)) {
                    value = kit.unique(new FloatConvertNode(FloatConvert.D2F, value));
                } else if (readKind != stackKind) {
                    assert stackKind.getBitCount() > readKind.getBitCount() : "read kind must be narrower than stack kind";
                    if (readKind.isUnsigned()) { // needed or another op may illegally sign-extend
                        value = kit.unique(new ZeroExtendNode(value, stackKind.getBitCount()));
                    } else {
                        value = kit.unique(new SignExtendNode(value, stackKind.getBitCount()));
                    }
                } else if (readKind.isObject()) {
                    value = kit.unboxHandle(value);
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
                }
                args.add(Pair.create(value, type));
                javaIndex += loadKind.getSlotCount();
            }
        } else if (callVariant == CallVariant.VA_LIST) {
            ValueNode valist = kit.loadLocal(javaIndex, metaAccess.lookupJavaType(WordBase.class).getJavaKind());
            for (int i = 0; i < count; i++) {
                ResolvedJavaType type = (ResolvedJavaType) invokeSignature.getParameterType(i, null);
                JavaKind loadKind = type.getJavaKind();
                if (loadKind.isObject()) {
                    loadKind = providers.getWordTypes().getWordKind();
                }
                ValueNode value = kit.append(new VaListNextArgNode(loadKind, valist));
                if (type.getJavaKind().isObject()) {
                    value = kit.unboxHandle(value);
                }
                args.add(Pair.create(value, type));
            }
        } else {
            throw VMError.unsupportedFeature("Call variant: " + callVariant);
        }
        return args;
    }

    /**
     * Creates the nodes to type-check and null-check the receiver; creates the nodes for a
     * {@code Call<Type>Method} JNI call, which will be executed iff the check succeeds.
     * 
     * @param kit Graph building kit
     * @param invokeMethod Method to invoke. This method should take a receiver.
     * @param invokeKind Kind of invoke
     * @param state Used for creating {@linkplain AbstractMergeNode merge node} FrameStates
     * @param args Args to pass to the method. The first argument should be the receiver.
     * 
     * @return A node representing the return value of the invoke, or the thrown error. Errors may
     *         be thrown by the type check, or by the called method. Returns {@code null} if the
     *         method is an instance method with a {@link JavaKind#Void void} return type.
     */
    private static ValueNode typeCheckReceiverAndCreateCallTypeMethod(JNIGraphKit kit, ResolvedJavaMethod invokeMethod, InvokeKind invokeKind, FrameStateBuilder state, ValueNode... args) {
        assert invokeMethod.hasReceiver() : "Expected to be called on a method that takes a receiver";
        assert args.length > 0 : "Expected args to at least contain a receiver";
        assert args[0] != null : "Expected the receiver to be non-null";
        assert args[0].getStackKind() == JavaKind.Object : "Expected the receiver to be an object";
        ValueNode receiver = args[0];

        ResolvedJavaType receiverClass = invokeMethod.getDeclaringClass();
        TypeReference expectedTypeRef = TypeReference.createTrusted(kit.getAssumptions(), receiverClass);
        /* Receiver must not be null, so we use an instanceof test is false for null values */
        LogicNode instanceOf = kit.unique(InstanceOfNode.create(expectedTypeRef, receiver, null, null));
        kit.startIf(instanceOf, BranchProbabilityNode.FAST_PATH_PROBABILITY);

        kit.thenPart();
        FixedGuardNode guard = kit.append(new FixedGuardNode(instanceOf, DeoptimizationReason.ClassCastException, DeoptimizationAction.None, false));
        args[0] = kit.append(PiNode.create(receiver, StampFactory.object(expectedTypeRef), guard));
        ValueNode invokeResult = ImageSingletons.lookup(JNIJavaCallWrapperMethodSupport.class).createCallTypeMethodCall(kit, invokeMethod, invokeKind, state, args);

        kit.elsePart();
        kit.startIf(kit.unique(IsNullNode.create(receiver)), 0.5);
        kit.thenPart();
        ConstantNode nullExceptionObject = kit.createObject(cachedReceiverNullPointerException);
        kit.setPendingException(nullExceptionObject);
        kit.elsePart();
        ConstantNode castExceptionObject = kit.createObject(cachedArgumentClassCastException);
        kit.setPendingException(castExceptionObject);
        AbstractMergeNode nullCheckMerge = kit.endIf();
        nullCheckMerge.setStateAfter(state.create(kit.bci(), nullCheckMerge));
        Stamp stamp = nullExceptionObject.stamp(NodeView.DEFAULT).meet(castExceptionObject.stamp(NodeView.DEFAULT));
        ValuePhiNode exceptionResult = kit.unique(new ValuePhiNode(stamp, nullCheckMerge, new ValueNode[]{nullExceptionObject, castExceptionObject}));

        AbstractMergeNode receiverCheckMerge = kit.endIf();
        receiverCheckMerge.setStateAfter(state.create(kit.bci(), receiverCheckMerge));
        if (invokeResult == null) {
            return null;
        }
        return kit.getGraph().addWithoutUnique(new ValuePhiNode(invokeResult.stamp(NodeView.DEFAULT), receiverCheckMerge, new ValueNode[]{invokeResult, exceptionResult}));
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

    private static Stamp getNarrowStamp(HostedProviders providers, JavaKind kind) {
        if (kind.isNumericInteger()) {
            // avoid widened stamp to prevent reading undefined bits
            if (kind.isUnsigned()) {
                return StampFactory.forUnsignedInteger(kind.getBitCount(), kind.getMinValue(), kind.getMaxValue());
            } else {
                return StampFactory.forInteger(kind.getBitCount(), kind.getMinValue(), kind.getMaxValue());
            }
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

    @Override
    public Signature getSignature() {
        return signature;
    }

    @Override
    public String getName() {
        String full = targetMethod.getDeclaringClass().getName() + "." + targetMethod.getName() + targetMethod.getSignature().toMethodDescriptor();
        return "jniInvoke_" + callVariant.name() + (nonVirtual ? "_Nonvirtual" : "") + ":" + JNINativeLinkage.mangle(full);
    }

    @Override
    public ResolvedJavaType getDeclaringClass() {
        return declaringClass;
    }

    @Override
    public ConstantPool getConstantPool() {
        return constantPool;
    }

}
