/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.VERY_FAST_PATH_PROBABILITY;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.LocationIdentity;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.config.ConfigurationValues;
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
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.annotation.AnnotationValue;
import com.oracle.svm.hosted.annotation.SubstrateAnnotationExtractor;
import com.oracle.svm.hosted.code.NonBytecodeMethod;
import com.oracle.svm.util.ReflectionUtil;

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

    protected UpcallStub(JavaEntryPointInfo jep, MethodType methodType, MetaAccessProvider metaAccess, boolean highLevel, boolean direct) {
        super(UpcallStubsHolder.stubName(jep, highLevel, direct),
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
 * cannot happen for upcalls. As such, setting the method's calling convention to
 * {@link SubstrateCallingConventionKind#Native} should be sufficient; there should be no need for a
 * customized calling convention.
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
 * <li>The {@link MethodHandle} to call in {@link AbiUtils#upcallSpecialArgumentsRegisters()}</li>
 * <li>The {@link org.graalvm.nativeimage.IsolateThread} to enter in
 * {@link ReservedRegisters#getThreadRegister}</li>
 * </ul>
 */
final class LowLevelUpcallStub extends UpcallStub implements CustomCallingConventionMethod {
    private final ResolvedJavaMethod highLevelStub;
    private final List<Register> savedRegisters;
    private final AssignedLocation[] parametersAssignment;

    static LowLevelUpcallStub make(JavaEntryPointInfo jep, AnalysisUniverse universe, MetaAccessProvider metaAccess) {
        TypeAdaptation adapted = AbiUtils.singleton().adapt(jep);
        AnalysisMethod highLevelStubMethod = universe.lookup(new HighLevelUpcallStub(jep, adapted, metaAccess));
        return new LowLevelUpcallStub(highLevelStubMethod, jep, adapted, metaAccess, false);
    }

    static LowLevelUpcallStub makeDirect(MethodHandle target, JavaEntryPointInfo jep, AnalysisUniverse universe, MetaAccessProvider metaAccess) {
        TypeAdaptation adapted = AbiUtils.singleton().adapt(jep);
        AnalysisMethod highLevelStubMethod = universe.lookup(new HighLevelDirectUpcallStub(target, jep, adapted, metaAccess));
        return new LowLevelUpcallStub(highLevelStubMethod, jep, adapted, metaAccess, true);
    }

    private LowLevelUpcallStub(AnalysisMethod highLevelStubMethod, JavaEntryPointInfo jep, AbiUtils.Adapter.Result.TypeAdaptation adapted, MetaAccessProvider metaAccess, boolean direct) {
        super(jep, adapted.callType(), metaAccess, false, direct);
        this.highLevelStub = highLevelStubMethod;
        this.savedRegisters = ImageSingletons.lookup(SubstrateRegisterConfigFactory.class)
                        .newRegisterFactory(SubstrateRegisterConfig.ConfigKind.NATIVE_TO_JAVA, null, ConfigurationValues.getTarget(), SubstrateOptions.PreserveFramePointer.getValue())
                        .getCalleeSaveRegisters();
        this.parametersAssignment = adapted.parametersAssignment().toArray(new AssignedLocation[0]);
    }

    /**
     * Implementation note: it would have been nice to be able to reuse the
     * {@link com.oracle.svm.core.foreign.AbiUtils.Adapter} facilities to implement the argument
     * transformations between the low and high call. Unfortunately, these facilities are not really
     * suited here: there is no assignment to transform, the type must be transformed before the
     * function is actually called (so all transformations should not be applied at the same time)
     * and finally argument injection is not currently supported.
     */
    @Override
    public StructuredGraph buildGraph(DebugContext debug, AnalysisMethod method, HostedProviders providers, Purpose purpose) {
        assert ExplicitCallingConvention.Util.getCallingConventionKind(method, false) == SubstrateCallingConventionKind.Custom;
        assert Uninterruptible.Utils.isUninterruptible(method);
        ForeignGraphKit kit = new ForeignGraphKit(debug, providers, method);

        /*
         * Read all relevant values, i.e. the MH to call, the current Isolate, the
         * function-preserved registers and function's arguments
         *
         * The special arguments read from specific registers were set up by the trampoline.
         */
        AbiUtils.Registers registers = AbiUtils.singleton().upcallSpecialArgumentsRegisters();
        ValueNode mh = kit.bindRegister(registers.methodHandle(), JavaKind.Object);
        ValueNode isolate = kit.append(kit.bindRegister(registers.isolate(), JavaKind.Long));
        List<ValueNode> arguments = new ArrayList<>(kit.getInitialArguments());

        /*
         * Prologue: save callee-save registers, allocate return space if needed, transition from
         * native to Java.
         *
         * Saving the callee-save registers is necessary because the invocation of the high-level
         * stub uses the Java calling convention which may interfere with those registers.
         */
        assert !savedRegisters.contains(registers.methodHandle());
        assert !savedRegisters.contains(registers.isolate());
        ValueNode enterResult = kit.append(CEntryPointEnterNode.attachThread(isolate, false, true));

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
         * Transfers to the Java-side stub; note that exceptions should be handled there. We
         * explicitly disable inline for this call to prevent that operations floating to a point
         * where the base registers are not initialized yet.
         */
        arguments.addFirst(mh);
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

    @Uninterruptible(reason = "Directly accesses registers and IsolateThread might not be correctly set up", calleeMustBe = false)
    @ExplicitCallingConvention(SubstrateCallingConventionKind.Custom)
    private static void annotationsHolder() {
    }

    private static final Method ANNOTATIONS_HOLDER = ReflectionUtil.lookupMethod(LowLevelUpcallStub.class, "annotationsHolder");

    private static final AnnotationValue[] INJECTED_ANNOTATIONS = SubstrateAnnotationExtractor.prepareInjectedAnnotations(
                    AnnotationAccess.getAnnotation(ANNOTATIONS_HOLDER, ExplicitCallingConvention.class),
                    Uninterruptible.Utils.getAnnotation(ANNOTATIONS_HOLDER));

    @Override
    public AnnotationValue[] getInjectedAnnotations() {
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
        super(jep, computeType(jep, adapted.callType()), metaAccess, true, false);
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

    private static MethodType computeType(JavaEntryPointInfo jep, MethodType lowTypeParam) {
        MethodType lowType = lowTypeParam;
        /* Inject return buffer */
        if (jep.buffersReturn()) {
            lowType = lowType.insertParameterTypes(0, long.class);
        }
        /* Inject method handle */
        return lowType.insertParameterTypes(0, MethodHandle.class);
    }

    private final MethodHandle target;

    HighLevelDirectUpcallStub(MethodHandle handle, JavaEntryPointInfo jep, AbiUtils.Adapter.Result.TypeAdaptation adapted, MetaAccessProvider metaAccess) {
        super(jep, computeType(jep, adapted.callType()), metaAccess, true, true);
        this.target = handle;
        VMError.guarantee(handle.type().equals(jep.handleType()));
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, AnalysisMethod method, HostedProviders providers, Purpose purpose) {
        ForeignGraphKit kit = new ForeignGraphKit(debug, providers, method);
        FrameStateBuilder frame = kit.getFrameState();

        List<ValueNode> allArguments = new ArrayList<>(kit.getInitialArguments());

        JavaConstant targetMethodHandle = kit.getSnippetReflection().forObject(target);
        ConstantNode constMH = kit.createConstant(targetMethodHandle, JavaKind.Object);

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
             * Replace the dynamically passed receiver handle by the constant handle this stub was
             * created with. This is necessary to enable the method handle intrinsification.
             */
            allArguments.set(0, constMH);
            frame.clearLocals();
            InvokeKind invokeKind = resolvedJavaMethod.isStatic() ? InvokeKind.Static : InvokeKind.Virtual;
            returnValue = kit.createJavaCallWithException(invokeKind, resolvedJavaMethod, allArguments.toArray(ValueNode.EMPTY_ARRAY));
        } else {
            // we don't use the argument that is passed from the trampoline to the stubs right now
            allArguments.removeFirst();

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
