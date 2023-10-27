package com.oracle.svm.hosted.foreign;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.List;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.java.FrameStateBuilder;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.DeadEndNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.Invoke;
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
import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.LocationIdentity;

import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.foreign.AbiUtils;
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
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.annotation.AnnotationValue;
import com.oracle.svm.hosted.annotation.SubstrateAnnotationExtractor;
import com.oracle.svm.hosted.code.NonBytecodeMethod;
import com.oracle.svm.hosted.code.SimpleSignature;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.RegisterArray;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.VERY_FAST_PATH_PROBABILITY;

public abstract class UpcallStub extends NonBytecodeMethod {
    protected final JavaEntryPointInfo jep;

    protected UpcallStub(JavaEntryPointInfo jep, MethodType methodType, MetaAccessProvider metaAccess, boolean high) {
        super(
                        UpcallStubsHolder.stubName(jep, high),
                        true,
                        metaAccess.lookupJavaType(UpcallStubsHolder.class),
                        SimpleSignature.fromMethodType(methodType, metaAccess),
                        UpcallStubsHolder.getConstantPool(metaAccess));
        this.jep = jep;
    }

    public static UpcallStub create(JavaEntryPointInfo jep, AnalysisUniverse universe, MetaAccessProvider metaAccess) {
        return LowUpcallStub.make(jep, universe, metaAccess);
    }
}

/**
 * In charge of low-level stuff: receiving arguments, preserving/restoring registers, set thread
 * status...
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
class LowUpcallStub extends UpcallStub implements CustomCallingConventionMethod {
    private final ResolvedJavaMethod javaSide;
    private final RegisterArray savedRegisters;
    private final AssignedLocation[] parametersAssignment;

    static LowUpcallStub make(JavaEntryPointInfo jep, AnalysisUniverse universe, MetaAccessProvider metaAccess) {
        return new LowUpcallStub(jep, AbiUtils.singleton().adapt(jep), universe, metaAccess);
    }

    private LowUpcallStub(JavaEntryPointInfo jep, AbiUtils.Adapter.Result.TypeAdaptation adapted, AnalysisUniverse universe, MetaAccessProvider metaAccess) {
        super(jep, adapted.callType(), metaAccess, false);
        this.javaSide = universe.lookup(new HighUpcallStub(jep, adapted, metaAccess));
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
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        assert ExplicitCallingConvention.Util.getCallingConventionKind(method, false) == SubstrateCallingConventionKind.Custom;
        assert Uninterruptible.Utils.isUninterruptible(method);
        ForeignGraphKit kit = new ForeignGraphKit(debug, providers, method, purpose);

        /*
         * Read all relevant values, i.e. the MH to call, the current Isolate, the
         * function-preserved registers and function's arguments
         *
         * The special arguments read from specific registers were set up by the trampoline
         */
        AbiUtils.Registers registers = AbiUtils.singleton().upcallSpecialArgumentsRegisters();
        ValueNode mh = kit.bindRegister(registers.methodHandle(), JavaKind.Object);
        ValueNode isolate = kit.append(kit.bindRegister(registers.isolate(), JavaKind.Long));
        List<ValueNode> arguments = kit.loadArguments(getSignature().toParameterTypes(null));

        /*
         * Prologue: save function-preserved registers, allocate return space if needed, transition
         * from native to Java
         */
        assert !savedRegisters.asList().contains(registers.methodHandle());
        assert !savedRegisters.asList().contains(registers.isolate());
        var save = kit.saveRegisters(savedRegisters);
        ValueNode enterResult = kit.append(CEntryPointEnterNode.enterByIsolate(isolate));

        kit.startIf(IntegerEqualsNode.create(enterResult, ConstantNode.forInt(CEntryPointErrors.NO_ERROR, kit.getGraph()), NodeView.DEFAULT),
                        ProfileData.BranchProbabilityData.create(VERY_FAST_PATH_PROBABILITY, ProfileData.ProfileSource.UNKNOWN));
        kit.thenPart();
        // Fast path: isolate successfully entered
        kit.elsePart();
        // Slow path: stop execution
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
            arguments.add(0, returnBuffer);
        }

        /*
         * Transfers to the java-side stub; note that exceptions should be handled int the Java stub
         * (if they are handled...)
         */
        arguments.add(0, mh);
        InvokeWithExceptionNode returnValue = kit.createJavaCallWithException(CallTargetNode.InvokeKind.Static, javaSide, arguments.toArray(ValueNode.EMPTY_ARRAY));
        kit.exceptionPart();
        kit.append(new DeadEndNode());
        kit.endInvokeWithException();

        /*
         * Epilogue: transition from Java to native, setup return registers, restore
         * function-preserved registers, return
         */
        if (jep.buffersReturn()) {
            assert returnBuffer != null;
            long offset = 0;
            for (AssignedLocation loc : AbiUtils.create().toMemoryAssignment(jep.returnAssignment(), true)) {
                assert loc.assignsToRegister();
                assert !save.containsKey(loc.register());

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
        kit.restoreRegisters(save);
        kit.createReturn(returnValue, jep.cMethodType());

        return kit.finalizeGraph();
    }

    @Uninterruptible(reason = "Manually access registers and IsolateThread might not be correctly setup", calleeMustBe = false)
    @ExplicitCallingConvention(SubstrateCallingConventionKind.Custom)
    private static void annotationsHolder() {
    }

    private static final Method ANNOTATIONS_HOLDER = ReflectionUtil.lookupMethod(LowUpcallStub.class, "annotationsHolder");

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

/**
 * In charge of high-level stuff, mainly invoking the method handle
 */
class HighUpcallStub extends UpcallStub {
    private static final Method INVOKE = ReflectionUtil.lookupMethod(
                    MethodHandle.class,
                    "invokeWithArguments",
                    Object[].class);

    private static MethodType computeType(JavaEntryPointInfo jep, MethodType lowType) {
        /* Inject return buffer */
        if (jep.buffersReturn()) {
            lowType = lowType.insertParameterTypes(0, long.class);
        }
        /* Inject method handle */
        return lowType.insertParameterTypes(0, MethodHandle.class);
    }

    HighUpcallStub(JavaEntryPointInfo jep, AbiUtils.Adapter.Result.TypeAdaptation adapted, MetaAccessProvider metaAccess) {
        super(jep, computeType(jep, adapted.callType()), metaAccess, true);
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        ForeignGraphKit kit = new ForeignGraphKit(debug, providers, method, purpose);
        MetaAccessProvider metaAccess = kit.getMetaAccess();
        FrameStateBuilder frame = kit.getFrameState();

        List<ValueNode> allArguments = kit.loadArguments(getSignature().toParameterTypes(null));

        ValueNode mh = allArguments.remove(0);
        /* If adaptations are ever needed for upcall, they should most likely be applied here */
        allArguments = kit.boxArguments(allArguments, jep.handleType());
        ValueNode arguments = kit.packArguments(allArguments);

        frame.clearLocals();
        InvokeWithExceptionNode returnValue = kit.createJavaCallWithException(CallTargetNode.InvokeKind.Virtual, metaAccess.lookupJavaMethod(INVOKE), mh, arguments);
        returnValue.setInlineControl(Invoke.InlineControl.Never); // For debug
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