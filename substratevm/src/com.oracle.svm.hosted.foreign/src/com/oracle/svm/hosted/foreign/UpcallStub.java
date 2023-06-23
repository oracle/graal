package com.oracle.svm.hosted.foreign;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.List;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.java.FrameStateBuilder;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.DeadEndNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.foreign.AbiUtils;
import com.oracle.svm.core.foreign.JavaEntryPointInfo;
import com.oracle.svm.core.foreign.UpcallStubsHolder;
import com.oracle.svm.core.graal.code.ExplicitCallingConvention;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.graal.code.SubstrateRegisterConfigFactory;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;
import com.oracle.svm.core.graal.nodes.CEntryPointEnterNode;
import com.oracle.svm.core.graal.nodes.CEntryPointLeaveNode;
import com.oracle.svm.core.graal.nodes.ReadReservedRegister;
import com.oracle.svm.hosted.annotation.AnnotationValue;
import com.oracle.svm.hosted.annotation.SubstrateAnnotationExtractor;
import com.oracle.svm.hosted.code.NonBytecodeMethod;
import com.oracle.svm.hosted.code.SimpleSignature;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.code.RegisterArray;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public abstract class UpcallStub extends NonBytecodeMethod {
    protected final JavaEntryPointInfo jep;

    protected UpcallStub(JavaEntryPointInfo jep, MetaAccessProvider metaAccess, boolean javaSide) {
        super(
                        UpcallStubsHolder.stubName(jep, javaSide),
                        true,
                        metaAccess.lookupJavaType(UpcallStubsHolder.class),
                        SimpleSignature.fromMethodType(javaSide ? jep.javaMethodType() : jep.cMethodType(), metaAccess),
                        UpcallStubsHolder.getConstantPool(metaAccess));
        this.jep = jep;
    }

    public static UpcallStub create(JavaEntryPointInfo jep, AnalysisUniverse universe, MetaAccessProvider metaAccess) {
        return new UpcallStubC(jep, universe, metaAccess);
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
 */
class UpcallStubC extends UpcallStub {
    private final ResolvedJavaMethod javaSide;
    private final RegisterArray savedRegisters;

    public UpcallStubC(JavaEntryPointInfo jep, AnalysisUniverse universe, MetaAccessProvider metaAccess) {
        super(jep, metaAccess, false);
        this.javaSide = universe.lookup(new UpcallStubJava(jep, metaAccess));
        SubstrateTargetDescription target = ConfigurationValues.getTarget();
        this.savedRegisters = ImageSingletons.lookup(SubstrateRegisterConfigFactory.class)
                        .newRegisterFactory(SubstrateRegisterConfig.ConfigKind.NATIVE_TO_JAVA, null, target, SubstrateOptions.PreserveFramePointer.getValue()).getCalleeSaveRegisters();
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        assert ExplicitCallingConvention.Util.getCallingConventionKind(method, false) == SubstrateCallingConventionKind.Native;
        assert Uninterruptible.Utils.isUninterruptible(method);
        ForeignGraphKit kit = new ForeignGraphKit(debug, providers, method, purpose);

        /*
         * Read all relevant values, i.e. the MH to call, the current Isolate, the
         * function-preserved registers and function's arguments
         */
        ValueNode mh = kit.bindRegister(AbiUtils.singleton().trampolineIndexRegister(), JavaKind.Object);
        // TODO change, as register might have been used by native
        ValueNode isolateThread = kit.append(ReadReservedRegister.createReadIsolateThreadNode(kit.getGraph()));
        List<ValueNode> arguments = kit.loadArguments(getSignature().toParameterTypes(null));
        arguments.add(0, mh);

        /* Prologue: save function-preserved registers and transition from native to Java */
        var save = kit.saveRegisters(savedRegisters);
        kit.append(CEntryPointEnterNode.enter(isolateThread));

        /*
         * Transfers to the java-side stub; note that exceptions should be handled there (if they
         * are handled...)
         */
        InvokeWithExceptionNode returnValue = kit.createJavaCallWithException(CallTargetNode.InvokeKind.Static, javaSide, arguments.toArray(ValueNode.EMPTY_ARRAY));
        kit.exceptionPart();
        kit.append(new DeadEndNode());
        kit.endInvokeWithException();

        /*
         * Epilogue: restore function-preserved registers, transition from Java to native and return
         */
        kit.append(new CEntryPointLeaveNode(CEntryPointLeaveNode.LeaveAction.Leave));
        kit.restoreRegisters(save);
        kit.createReturn(returnValue, jep.cMethodType());

        return kit.finalizeGraph();
    }

    @Uninterruptible(reason = "Manually access registers and IsolateThread might not be correctly setup", calleeMustBe = false)
    @ExplicitCallingConvention(SubstrateCallingConventionKind.Native)
    private static void annotationsHolder() {
    }

    private static final Method ANNOTATIONS_HOLDER = ReflectionUtil.lookupMethod(UpcallStubC.class, "annotationsHolder");

    private static final AnnotationValue[] INJECTED_ANNOTATIONS = SubstrateAnnotationExtractor.prepareInjectedAnnotations(
                    AnnotationAccess.getAnnotation(ANNOTATIONS_HOLDER, ExplicitCallingConvention.class),
                    Uninterruptible.Utils.getAnnotation(ANNOTATIONS_HOLDER));

    @Override
    public AnnotationValue[] getInjectedAnnotations() {
        return INJECTED_ANNOTATIONS;
    }
}

/**
 * In charge of high-level stuff, mainly invoking the method handle
 */
class UpcallStubJava extends UpcallStub {
    private static final Method INVOKE = ReflectionUtil.lookupMethod(
                    MethodHandle.class,
                    "invokeWithArguments",
                    Object[].class);

    public UpcallStubJava(JavaEntryPointInfo jep, MetaAccessProvider metaAccess) {
        super(jep, metaAccess, true);
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        ForeignGraphKit kit = new ForeignGraphKit(debug, providers, method, purpose);
        MetaAccessProvider metaAccess = kit.getMetaAccess();
        FrameStateBuilder frame = kit.getFrameState();

        List<ValueNode> allArguments = kit.loadArguments(getSignature().toParameterTypes(null));

        ValueNode mh = allArguments.remove(0);
        /* If adaptations are ever needed for upcall, it should most likely be applied here */
        ValueNode arguments = kit.packArguments(allArguments);

        frame.clearLocals();
        frame.clearStack();
        InvokeWithExceptionNode returnValue = kit.createJavaCallWithException(CallTargetNode.InvokeKind.Virtual, metaAccess.lookupJavaMethod(INVOKE), mh, arguments);
        returnValue.setInlineControl(Invoke.InlineControl.Never); // For debug
        kit.exceptionPart();
        /*
         * Per documentation, it is the user's responsibility to not throw exceptions from upcalls
         * (e.g. by using MethodHandles#catchException).
         */
        kit.append(new DeadEndNode());
        kit.endInvokeWithException();

        var unboxedReturn = kit.unbox(returnValue, jep.cMethodType());
        kit.createReturn(unboxedReturn, jep.cMethodType());

        return kit.finalizeGraph();
    }
}