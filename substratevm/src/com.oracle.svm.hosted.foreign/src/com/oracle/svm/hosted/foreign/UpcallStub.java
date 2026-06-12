/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.foreign;

import static com.oracle.graal.pointsto.infrastructure.ResolvedSignature.fromMethodType;
import static com.oracle.svm.util.AnnotationUtil.newAnnotationValue;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.VERY_FAST_PATH_PROBABILITY;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.word.LocationIdentity;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.core.UninterruptibleAnnotationUtils;
import com.oracle.svm.core.foreign.AbiUtils;
import com.oracle.svm.core.foreign.AbiUtils.Adapter.Result.TypeAdaptation;
import com.oracle.svm.core.foreign.JavaEntryPointInfo;
import com.oracle.svm.core.foreign.UpcallStubsHolder;
import com.oracle.svm.core.graal.code.AssignedLocation;
import com.oracle.svm.core.graal.code.CustomCallingConventionMethod;
import com.oracle.svm.core.graal.code.ExplicitCallingConvention;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
import com.oracle.svm.core.graal.code.SubstrateRegisterConfigFactory;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;
import com.oracle.svm.core.graal.nodes.CEntryPointEnterNode;
import com.oracle.svm.core.graal.nodes.CEntryPointLeaveNode;
import com.oracle.svm.core.graal.nodes.CEntryPointUtilityNode;
import com.oracle.svm.core.graal.nodes.LoweredDeadEndNode;
import com.oracle.svm.core.graal.stackvalue.StackValueNode;
import com.oracle.svm.guest.staging.c.function.CEntryPointErrors;
import com.oracle.svm.hosted.code.NonBytecodeMethod;
import com.oracle.svm.shared.util.BasedOnJDKFile;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.GuestAccess;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.java.FrameStateBuilder;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.DeadEndNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ProfileData;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.replacements.nodes.CStringConstant;
import jdk.graal.compiler.replacements.nodes.WriteRegisterNode;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+21/src/hotspot/share/prims/upcallLinker.cpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+17/src/hotspot/cpu/x86/upcallLinker_x86_64.cpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+17/src/hotspot/cpu/aarch64/upcallLinker_aarch64.cpp")
public abstract class UpcallStub extends NonBytecodeMethod {
    protected final JavaEntryPointInfo jep;

    protected UpcallStub(JavaEntryPointInfo jep, MethodType methodType, MetaAccessProvider metaAccess, boolean highLevel, boolean direct, boolean injectReceiver) {
        super(UpcallStubsHolder.stubName(jep, highLevel, direct, injectReceiver),
                        true,
                        metaAccess.lookupJavaType(UpcallStubsHolder.class),
                        fromMethodType(methodType, metaAccess),
                        UpcallStubsHolder.getConstantPool(metaAccess));
        this.jep = jep;
    }
}

/**
 * In charge of low-level stuff: receiving arguments, preserving/restoring registers, set thread
 * status, etc.
 * <p>
 * Unlike downcalls where methods could have varargs, which are not supported by the backend, this
 * cannot happen for upcalls. We therefore use a custom calling convention to surface the trampoline
 * injected isolate and runtime object registers as explicit parameters while still delegating all
 * native arguments to the standard native-to-Java mappings.
 * <p>
 * The method type is of the form (<>: argument; []: optional argument)
 *
 * <pre>
 * {@code
 *      <actual arg 1> <actual arg 2> ...
 * }
 * </pre>
 *
 * with the following arguments being passed using special registers:
 * <ul>
 * <li>In {@link AbiUtils.Registers#methodHandleOrReceiver}: The address of the target method's
 * receiver if {@link #injectReceiver} is {@code true}. Otherwise, the {@link MethodHandle} to
 * call.</li>
 * <li>In {@link AbiUtils.Registers#isolate}: The {@link org.graalvm.nativeimage.Isolate} to
 * enter.</li>
 * </ul>
 */
final class LowLevelUpcallStub extends UpcallStub implements CustomCallingConventionMethod {
    private final ResolvedJavaMethod highLevelStub;
    private final List<Register> savedRegisters;
    private final AssignedLocation[] parametersAssignment;
    private final boolean injectReceiver;

    static LowLevelUpcallStub make(JavaEntryPointInfo jep, AnalysisUniverse universe, MetaAccessProvider metaAccess) {
        TypeAdaptation adapted = AbiUtils.singleton().adapt(jep);
        AnalysisMethod highLevelStubMethod = universe.lookup(new HighLevelUpcallStub(jep, adapted, metaAccess));
        return new LowLevelUpcallStub(highLevelStubMethod, jep, adapted, metaAccess, false, false);
    }

    static LowLevelUpcallStub makeDirect(MethodHandle target, JavaEntryPointInfo nativeJep, JavaEntryPointInfo targetJep, AnalysisUniverse universe, MetaAccessProvider metaAccess,
                    boolean injectedReceiver) {
        TypeAdaptation nativeAdapted = AbiUtils.singleton().adapt(nativeJep);
        TypeAdaptation targetAdapted = AbiUtils.singleton().adapt(targetJep);
        AnalysisMethod highLevelStubMethod = universe.lookup(new HighLevelDirectUpcallStub(target, targetJep, targetAdapted, metaAccess, injectedReceiver));
        return new LowLevelUpcallStub(highLevelStubMethod, nativeJep, nativeAdapted, metaAccess, true, injectedReceiver);
    }

    private LowLevelUpcallStub(AnalysisMethod highLevelStubMethod, JavaEntryPointInfo jep, AbiUtils.Adapter.Result.TypeAdaptation adapted, MetaAccessProvider metaAccess, boolean direct,
                    boolean injectReceiver) {
        super(jep, toLowLevelType(adapted.callType(), injectReceiver), metaAccess, false, direct, injectReceiver);
        this.highLevelStub = highLevelStubMethod;
        this.savedRegisters = SubstrateRegisterConfigFactory.singleton()
                        .newRegisterFactory(SubstrateRegisterConfig.ConfigKind.NATIVE_TO_JAVA, null, SubstrateTarget.singleton(), SubstrateOptions.PreserveFramePointer.getValue())
                        .getCalleeSaveRegisters();
        this.parametersAssignment = toLowLevelAssignments(adapted);
        this.injectReceiver = injectReceiver;
    }

    private static MethodType toLowLevelType(MethodType callType, boolean injectedReceiver) {
        Class<?> specialArgumentType = injectedReceiver ? long.class : MethodHandle.class;
        return callType.insertParameterTypes(0, specialArgumentType, long.class);
    }

    /**
     * Prepends assigned locations for special registers that will contain the method handle (or the
     * receiver address for bound instance direct upcalls) and the isolate pointer. This tells the
     * compiler that those registers are used for parameters.
     */
    private static AssignedLocation[] toLowLevelAssignments(TypeAdaptation adapted) {
        AbiUtils.Registers specialRegisters = AbiUtils.singleton().upcallSpecialArgumentsRegisters();
        List<AssignedLocation> assignedLocations = adapted.parametersAssignment();
        AssignedLocation[] extendedAssignments = new AssignedLocation[assignedLocations.size() + 2];
        extendedAssignments[0] = AssignedLocation.forRegister(specialRegisters.methodHandleOrReceiver(), JavaKind.Long);
        extendedAssignments[1] = AssignedLocation.forRegister(specialRegisters.isolate(), JavaKind.Long);
        for (int i = 0; i < assignedLocations.size(); i++) {
            extendedAssignments[i + 2] = assignedLocations.get(i);
        }
        return extendedAssignments;
    }

    /**
     * Implementation note: it would have been nice to be able to reuse the
     * {@link com.oracle.svm.core.foreign.AbiUtils.Adapter} facilities to implement the argument
     * transformations between the low and high call. Unfortunately, these facilities are not really
     * suited here: direct upcalls with receiver injection need to keep the native callback
     * assignments unchanged while passing a different Java target signature to the high-level stub.
     * Applying all transformations in one step would mix those two views of the arguments.
     */
    @Override
    public StructuredGraph buildGraph(DebugContext debug, AnalysisMethod method, HostedProviders providers, Purpose purpose) {
        assert ExplicitCallingConvention.Util.getCallingConventionKind(method, false) == SubstrateCallingConventionKind.Custom;
        assert UninterruptibleAnnotationUtils.isUninterruptible(method);
        ForeignGraphKit kit = new ForeignGraphKit(debug, providers, method);

        /*
         * Read all relevant values, i.e. the MH to call (or receiver address), the current Isolate,
         * the function-preserved registers and function's arguments.
         *
         * The trampoline seeds dedicated registers for the method handle (or receiver address) and
         * isolate; the custom calling convention exposes them as the first parameters.
         */
        AbiUtils.Registers registers = AbiUtils.singleton().upcallSpecialArgumentsRegisters();
        List<ValueNode> arguments = new ArrayList<>(kit.getInitialArguments());
        ValueNode methodHandleOrReceiver = arguments.removeFirst();
        ValueNode isolate = arguments.removeFirst();

        /*
         * Prologue: save callee-save registers, allocate return space if needed, transition from
         * native to Java.
         *
         * Saving the callee-save registers is necessary because the invocation of the high-level
         * stub uses the Java calling convention which may interfere with those registers.
         */
        assert !savedRegisters.contains(registers.methodHandleOrReceiver());
        assert !savedRegisters.contains(registers.isolate());
        ValueNode enterResult = kit.append(CEntryPointEnterNode.attachThread(isolate, true));

        kit.startIf(IntegerEqualsNode.create(enterResult, ConstantNode.forInt(CEntryPointErrors.NO_ERROR, kit.getGraph()), NodeView.DEFAULT),
                        ProfileData.BranchProbabilityData.create(VERY_FAST_PATH_PROBABILITY, ProfileData.ProfileSource.UNKNOWN));
        kit.thenPart();
        kit.elsePart();
        CStringConstant cst = new CStringConstant("Could not enter isolate.");
        ValueNode msg = ConstantNode.forConstant(StampFactory.pointer(), cst, kit.getMetaAccess());
        kit.append(new CEntryPointUtilityNode(CEntryPointUtilityNode.UtilityAction.FailFatally, enterResult, msg));
        kit.append(new DeadEndNode());
        kit.endIf();

        StackValueNode returnBuffer = null;
        if (jep.buffersReturn()) {
            assert jep.handleType().returnType().equals(void.class) : getName();
            returnBuffer = kit.append(StackValueNode.create(jep.returnBufferSize(), method, BytecodeFrame.UNKNOWN_BCI, true));
            FrameState frameState = new FrameState(BytecodeFrame.UNKNOWN_BCI);
            frameState.invalidateForDeoptimization();
            returnBuffer.setStateAfter(kit.getGraph().add(frameState));
            arguments.addFirst(returnBuffer);
        }

        /*
         * Direct upcalls for bound instance receivers reuse the special method-handle register to
         * carry the receiver address. Insert that value into the Java target arguments without
         * changing the native callback ABI. Keep an injected return buffer, if present, at the
         * front of the argument list.
         */
        if (injectReceiver) {
            int receiverArgumentIndex = jep.buffersReturn() ? 1 : 0;
            VMError.guarantee(arguments.size() >= receiverArgumentIndex);
            arguments.add(receiverArgumentIndex, methodHandleOrReceiver);
        } else {
            arguments.addFirst(methodHandleOrReceiver);
        }

        /*
         * Transfers to the Java-side stub; note that exceptions should be handled there. We
         * explicitly disable inline for this call to prevent that operations floating to a point
         * where the base registers are not initialized yet.
         */
        InvokeWithExceptionNode returnValue = kit.createJavaCallWithException(CallTargetNode.InvokeKind.Static, highLevelStub, arguments.toArray(ValueNode.EMPTY_ARRAY));
        returnValue.setUseForInlining(false);

        kit.exceptionPart();
        kit.append(new DeadEndNode());
        kit.endInvokeWithException();

        /*
         * Epilogue: transition from Java to native, setup return registers, restore
         * function-preserved registers, return.
         */
        if (jep.buffersReturn()) {
            assert returnBuffer != null;
            long offset = 0;
            for (AssignedLocation loc : AbiUtils.create().toMemoryAssignment(jep.returnAssignment(), true)) {
                assert loc.assignsToRegister();

                AddressNode address = new OffsetAddressNode(returnBuffer, ConstantNode.forLong(offset, kit.getGraph()));
                ReadNode val = kit.append(new ReadNode(address, LocationIdentity.any(), StampFactory.forKind(loc.registerKind()), BarrierType.NONE, MemoryOrderMode.PLAIN));
                kit.append(new WriteRegisterNode(loc.register(), val));

                switch (loc.registerKind()) {
                    case Long -> offset += 8;
                    case Double -> offset += 16;
                    default -> VMError.shouldNotReachHere("Invalid kind");
                }
            }
            assert offset == jep.returnBufferSize();
        }
        kit.append(new CEntryPointLeaveNode(CEntryPointLeaveNode.LeaveAction.Leave));
        kit.createReturn(returnValue, jep.cMethodType());

        return kit.finalizeGraph();
    }

    private static final List<AnnotationValue> INJECTED_ANNOTATIONS = List.of(
                    newAnnotationValue(ExplicitCallingConvention.class,
                                    "value", SubstrateCallingConventionKind.Custom),
                    newAnnotationValue(GuestAccess.elements().Uninterruptible,
                                    "calleeMustBe", false,
                                    "reason", "Directly accesses registers and IsolateThread might not be correctly set up"));

    @Override
    public List<AnnotationValue> getInjectedAnnotations() {
        return INJECTED_ANNOTATIONS;
    }

    @Override
    public SubstrateCallingConventionType getCallingConvention() {
        return SubstrateCallingConventionType.makeCustom(
                        false,
                        parametersAssignment,
                        AbiUtils.singleton().toMemoryAssignment(jep.returnAssignment(), true));
    }

}

/** In charge of high-level stuff, mainly invoking the method handle. */
class HighLevelUpcallStub extends UpcallStub {
    static final Method INVOKE = ReflectionUtil.lookupMethod(
                    MethodHandle.class,
                    "invokeWithArguments",
                    Object[].class);

    private static MethodType computeType(JavaEntryPointInfo jep, MethodType lowTypeParam) {
        MethodType lowType = lowTypeParam;
        /* Inject return buffer */
        if (jep.buffersReturn()) {
            lowType = lowType.insertParameterTypes(0, long.class);
        }
        /* Inject method handle */
        return lowType.insertParameterTypes(0, MethodHandle.class);
    }

    HighLevelUpcallStub(JavaEntryPointInfo jep, AbiUtils.Adapter.Result.TypeAdaptation adapted, MetaAccessProvider metaAccess) {
        super(jep, computeType(jep, adapted.callType()), metaAccess, true, false, false);
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, AnalysisMethod method, HostedProviders providers, Purpose purpose) {
        ForeignGraphKit kit = new ForeignGraphKit(debug, providers, method);
        MetaAccessProvider metaAccess = kit.getMetaAccess();
        FrameStateBuilder frame = kit.getFrameState();

        List<ValueNode> allArguments = new ArrayList<>(kit.getInitialArguments());

        ValueNode mh = allArguments.removeFirst();
        /* If adaptations are ever needed for upcalls, they should most likely be applied here */
        allArguments = kit.boxArguments(allArguments, jep.handleType());
        ValueNode arguments = kit.packArguments(allArguments);

        frame.clearLocals();
        InvokeWithExceptionNode returnValue = kit.createJavaCallWithException(CallTargetNode.InvokeKind.Virtual, metaAccess.lookupJavaMethod(INVOKE), mh, arguments);
        kit.exceptionPart();
        /*
         * Per documentation, it is the user's responsibility to not throw exceptions from upcalls
         * (e.g. by using MethodHandles#catchException).
         */
        kit.append(new CEntryPointLeaveNode(CEntryPointLeaveNode.LeaveAction.ExceptionAbort, kit.exceptionObject()));
        kit.append(new LoweredDeadEndNode());
        kit.endInvokeWithException();

        var unboxedReturn = kit.unbox(returnValue, jep.cMethodType());
        kit.createReturn(unboxedReturn, jep.cMethodType());

        return kit.finalizeGraph();
    }
}

/**
 * Similar to HighLevelUpcallStub but is bound to a constant method handle. This method then acts as
 * an intrinsification enabler
 */
class HighLevelDirectUpcallStub extends UpcallStub {

    private static MethodType computeType(JavaEntryPointInfo jep, MethodType lowTypeParam, boolean injectedReceiver) {
        MethodType lowType = lowTypeParam;
        /* Inject return buffer */
        if (jep.buffersReturn()) {
            lowType = lowType.insertParameterTypes(0, long.class);
        }
        if (injectedReceiver) {
            return lowType;
        }
        /* Inject method handle */
        return lowType.insertParameterTypes(0, MethodHandle.class);
    }

    private final MethodHandle target;
    private final boolean injectedReceiver;

    HighLevelDirectUpcallStub(MethodHandle handle, JavaEntryPointInfo jep, AbiUtils.Adapter.Result.TypeAdaptation adapted, MetaAccessProvider metaAccess, boolean injectedReceiver) {
        super(jep, computeType(jep, adapted.callType(), injectedReceiver), metaAccess, true, true, injectedReceiver);
        this.target = handle;
        this.injectedReceiver = injectedReceiver;
        VMError.guarantee(handle.type().equals(jep.handleType()));
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, AnalysisMethod method, HostedProviders providers, Purpose purpose) {
        ForeignGraphKit kit = new ForeignGraphKit(debug, providers, method);
        FrameStateBuilder frame = kit.getFrameState();

        List<ValueNode> allArguments = new ArrayList<>(kit.getInitialArguments());

        JavaConstant targetMethodHandle = kit.getSnippetReflection().forObject(target);
        ConstantNode constMH = kit.createConstant(targetMethodHandle, JavaKind.Object);

        /*
         * Support for bound method handles that invoke an instance method. In that case, the first
         * non-return-buffer argument to this high-level upcall stub is the address of the receiver
         * object of the method to be invoked. Otherwise, it is the method handle, which we discard
         * for the call.
         */
        if (injectedReceiver) {
            ValueNode receiverAddress = allArguments.getFirst();
            VMError.guarantee(StampFactory.forKind(JavaKind.Long).equals(receiverAddress.stamp(NodeView.DEFAULT)));
        } else {
            // we don't use the argument that is passed from the trampoline
            allArguments.removeFirst();
        }

        InvokeWithExceptionNode returnValue;
        /*
         * Attempt to resolve the target of 'invokeBasic'. This should resolve the call of the
         * polymorphic signature method 'invokeExact' to the actually implementing method (i.e. the
         * one generated by the lambda form; usually named 'invokeExact_MT'). The resolved target
         * has a specialized signature (i.e. no longer takes 'Object[]') and so we omit boxing.
         * Further, this method is annotated with 'LambdaForm.Compiled' and recognized by
         * InlineBeforeAnalysis as method handle intrinsification root.
         *
         * If resolving does not work, a call to a generic invocation method will be emitted (same
         * as in 'UpcallStub'). We will still use the constant method handle as receiver to enable
         * some optimizations but the method handle will most certainly still be interpreted.
         */
        ResolvedJavaMethod resolvedJavaMethod = providers.getConstantReflection().getMethodHandleAccess().resolveInvokeBasicTarget(targetMethodHandle, true);
        if (resolvedJavaMethod != null) {
            /*
             * Always use the constant method handle this stub was created with as receiver for the
             * method handle invocation. This is necessary to enable method handle intrinsification.
             */
            allArguments.addFirst(constMH);
            frame.clearLocals();
            InvokeKind invokeKind = resolvedJavaMethod.isStatic() ? InvokeKind.Static : InvokeKind.Virtual;
            returnValue = kit.createJavaCallWithException(invokeKind, resolvedJavaMethod, allArguments.toArray(ValueNode.EMPTY_ARRAY));
        } else {
            /*
             * If adaptations are ever needed for upcalls, they should most likely be applied here
             */
            allArguments = kit.boxArguments(allArguments, jep.handleType());
            ValueNode arguments = kit.packArguments(allArguments);
            frame.clearLocals();
            MetaAccessProvider metaAccess = kit.getMetaAccess();
            returnValue = kit.createJavaCallWithException(CallTargetNode.InvokeKind.Virtual, metaAccess.lookupJavaMethod(HighLevelUpcallStub.INVOKE), constMH, arguments);
        }

        kit.exceptionPart();
        /*
         * Per documentation, it is the user's responsibility to not throw exceptions from upcalls
         * (e.g. by using MethodHandles#catchException).
         */
        kit.append(new CEntryPointLeaveNode(CEntryPointLeaveNode.LeaveAction.ExceptionAbort, kit.exceptionObject()));
        kit.append(new LoweredDeadEndNode());
        kit.endInvokeWithException();

        ValueNode unboxedReturn;
        if (resolvedJavaMethod != null) {
            unboxedReturn = returnValue;
        } else {
            unboxedReturn = kit.unbox(returnValue, jep.cMethodType());
        }
        assert JavaKind.fromJavaClass(jep.cMethodType().returnType()) == unboxedReturn.getStackKind();
        kit.createReturn(unboxedReturn, jep.cMethodType());

        return kit.finalizeGraph();
    }
}
