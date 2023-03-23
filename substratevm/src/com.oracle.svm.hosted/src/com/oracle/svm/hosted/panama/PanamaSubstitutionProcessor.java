package com.oracle.svm.hosted.panama;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
import com.oracle.svm.core.panama.downcalls.PanamaDowncallsSupport;
import com.oracle.svm.core.panama.Target_jdk_internal_foreign_abi_NativeEntrypoint;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.code.SimpleSignature;
import jdk.vm.ci.meta.Signature;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.java.FrameStateBuilder;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ProfileData;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;

import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.hosted.code.NonBytecodeMethod;
import com.oracle.svm.hosted.phases.HostedGraphKit;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.nativeimage.c.function.CFunction;


public class PanamaSubstitutionProcessor extends SubstitutionProcessor {

    private record KindSignature(JavaKind ret, JavaKind[] params) {
        public int parameterCount() {
            return params.length;
        }

        public JavaKind parameter(int i) {
            return params[i];
        }

        public Signature resolve(MetaAccessProvider meta) {
            return SimpleSignature.fromKinds(params, ret, meta);
        }

        public static KindSignature create(MethodType mt, int dropFirstParameters) {
            var params = new JavaKind[mt.parameterCount() - dropFirstParameters];
            for (int i = 0; i < params.length; ++i) {
                params[i] = JavaKind.fromJavaClass(mt.parameterType(i + dropFirstParameters));
            }
            var ret = JavaKind.fromJavaClass(mt.returnType());
            return new KindSignature(ret, params);
        }
    }

    private static class DoLinkToNative extends NonBytecodeMethod {

        ResolvedJavaMethod original;

        public DoLinkToNative(ResolvedJavaMethod original) {
            super(original.getName(), original.isStatic(), original.getDeclaringClass(), original.getSignature(), original.getConstantPool());
            this.original = original;
        }

        @Override
        @SuppressWarnings("try")
        public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
            HostedGraphKit kit = new HostedGraphKit(debug, providers, method, purpose);
            FrameStateBuilder state = kit.getFrameState();
            boolean deoptimizationTarget = MultiMethod.isDeoptTarget(method);
            List<ValueNode> arguments = kit.loadArguments(original.toParameterTypes());

            ValueNode address = arguments.get(0);
            ValueNode typeId = arguments.get(1);
            ValueNode argumentsArray = arguments.get(2);

            try (var ignored = debug.logAndIndent("Generating doLinkToNative for types:")) {
                for (Target_jdk_internal_foreign_abi_NativeEntrypoint nep: PanamaDowncallsSupport.singleton().registered()) {
                    debug.log(nep.methodType().toString());
                }
            }

            var nepIdPairs = PanamaDowncallsSupport.singleton().mapping().getEntries();
            while (nepIdPairs.advance()) {
                Target_jdk_internal_foreign_abi_NativeEntrypoint nep = nepIdPairs.getKey();
                int id = nepIdPairs.getValue();

                kit.startIf(kit.unique(IntegerEqualsNode.create(typeId, kit.createInt(id), NodeView.DEFAULT)), ProfileData.BranchProbabilityData.injected(1f/2));
                kit.thenPart();
                    buildCall(state, kit, nep, address, argumentsArray, deoptimizationTarget);
                kit.elsePart();
            }
            kit.createReturn(kit.createObject(null), JavaKind.Object);
            for (int i = 0; i < PanamaDowncallsSupport.singleton().stubCount(); ++i) {
                kit.endIf();
            }

            return kit.finalizeGraph();
        }

        private static void buildCall(FrameStateBuilder state, HostedGraphKit kit, Target_jdk_internal_foreign_abi_NativeEntrypoint nep, ValueNode callAddress, ValueNode argumentsArray, boolean deoptimizationTarget) {
            KindSignature signature = KindSignature.create(nep.methodType(), 1);
            checkSignature(signature);

            List<ValueNode> arguments = convertArguments(kit, signature, argumentsArray);
            state.clearLocals();
            SubstrateCallingConventionType cc = SubstrateCallingConventionKind.Native.toType(true)
                    .withParametersAssigned(nep.parametersAssignment())
                    .withReturnSaving(nep.returnsAssignment()); // Assignment might be null, in which case this is a no-op
            ValueNode returnValue = kit.createCFunctionCall(callAddress, arguments, signature.resolve(kit.getMetaAccess()), VMThreads.StatusSupport.getNewThreadStatus(CFunction.Transition.TO_NATIVE), deoptimizationTarget, cc);

            returnValue = adaptReturnValue(kit, signature, returnValue);
            kit.createReturn(returnValue, JavaKind.Object);
        }

        private static void checkKind(JavaKind kind, String id) {
            UserError.guarantee(kind.isPrimitive(), "Only primitive types are supported; got " + kind + "@" + id);
        }

        private static void checkSignature(KindSignature signature) {
            checkKind(signature.ret(), "ret");
            for (int i = 0; i < signature.parameterCount(); ++i) {
                checkKind(signature.parameter(i), "param" + i);
            }
        }

        // TODO: figure out how to prevent boxing

        private static List<ValueNode> convertArguments(HostedGraphKit kit, KindSignature signature, ValueNode argumentsArray) {
            var args = kit.liftArray(argumentsArray, JavaKind.Object, signature.parameterCount());
            for (int i = 0; i < args.size(); ++i) {
                args.set(i, kit.createUnboxing(args.get(i), signature.parameter(i)));
            }
            return args;
        }

        public static ValueNode adaptReturnValue(HostedGraphKit kit, KindSignature signature, ValueNode invokeValue) {
            ValueNode returnValue = invokeValue;
            if (signature.ret().equals(JavaKind.Void)) {
                return kit.createObject(null);
            }

            var boxed = kit.getMetaAccess().lookupJavaType(signature.ret().toBoxedJavaClass());
            returnValue = kit.createBoxing(returnValue, signature.ret(), boxed);
            return returnValue;
        }
    }


    private final Map<ResolvedJavaMethod, ResolvedJavaMethod> methodSubstitutions;

    public PanamaSubstitutionProcessor(MetaAccessProvider metaAccessProvider) {
        Method linkToNative;
        try {
            linkToNative = PanamaDowncallsSupport.class.getDeclaredMethod("doLinkToNative", long.class, int.class, Object[].class);
        } catch (NoSuchMethodException e) {
            throw shouldNotReachHere("com.oracle.svm.core.panama.downcalls.PanamaDowncallsLinking.doLinkToNative must exist and have expected signature.");
        }

        linkToNative.setAccessible(true);
        ResolvedJavaMethod resolvedLinkToNative = metaAccessProvider.lookupJavaMethod(linkToNative);

        this.methodSubstitutions = Map.of(resolvedLinkToNative, new DoLinkToNative(resolvedLinkToNative));
    }

    @Override
    public ResolvedJavaMethod lookup(ResolvedJavaMethod method) {
        if (methodSubstitutions.containsKey(method)) {
            return methodSubstitutions.get(method);
        }
        return method;
    }

    @Override
    public ResolvedJavaMethod resolve(ResolvedJavaMethod method) {
        if (method instanceof DoLinkToNative ps) {
            return ps.original;
        }
        return method;
    }
}
