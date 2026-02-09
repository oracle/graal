/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle;

import static com.oracle.svm.core.deopt.Deoptimizer.StubType.NoDeoptStub;
import static com.oracle.svm.core.graal.code.SubstrateCallingConventionType.SubstrateCallingConventionArgumentKind.IMMUTABLE;
import static com.oracle.svm.core.graal.code.SubstrateCallingConventionType.SubstrateCallingConventionArgumentKind.VALUE_REFERENCE;
import static com.oracle.svm.util.AnnotationUtil.newAnnotationValue;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.pointsto.infrastructure.ResolvedSignature;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaMethod;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaType;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.NeverStrengthenGraphWithConstants;
import com.oracle.svm.core.SkipEpilogueSafepointCheck;
import com.oracle.svm.core.SkipStackOverflowCheck;
import com.oracle.svm.core.SubstrateControlFlowIntegrity;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.graal.code.AssignedLocation;
import com.oracle.svm.core.graal.code.CustomCallingConventionMethod;
import com.oracle.svm.core.graal.code.ExplicitCallingConvention;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionType.SubstrateCallingConventionArgumentKind;
import com.oracle.svm.core.graal.code.SubstrateRegisterConfigFactory;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;
import com.oracle.svm.hosted.code.NonBytecodeMethod;
import com.oracle.svm.hosted.phases.HostedGraphKit;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterHandler;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.truffle.TruffleBytecodeHandlerCallsite;
import jdk.graal.compiler.truffle.TruffleBytecodeHandlerCallsite.ArgumentInfo;
import jdk.graal.compiler.truffle.TruffleBytecodeHandlerCallsite.TruffleBytecodeHandlerTypes;
import jdk.graal.compiler.truffle.host.TruffleKnownHostTypes;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * A substrate stub implementation for calling Truffle bytecode handler.
 *
 * To minimize the overhead in the prologue and epilogue of the generated code, we inject the
 * {@link SkipStackOverflowCheck} annotation to eliminate the stack overflow check, and the
 * {@link SkipEpilogueSafepointCheck} annotation to eliminate the safepoint check when
 * {@link BytecodeInterpreterHandler#safepoint()} is false.
 *
 * We also inject the {@link NeverStrengthenGraphWithConstants} annotation to prevent the compiler
 * from replacing {@link jdk.graal.compiler.nodes.ParameterNode} with constants derived from static
 * analysis results, thereby replacing register accesses with memory accesses. This decision is
 * based on the assumption that further constant folding within a bytecode handler is unlikely to
 * happen.
 */
public final class SubstrateTruffleBytecodeHandlerStub extends NonBytecodeMethod implements CustomCallingConventionMethod {

    private final SubstrateTruffleBytecodeHandlerStubHolder stubHolder;
    private final TruffleBytecodeHandlerCallsite callsite;
    private final boolean threading;
    private final boolean needSafepoint;
    private final ResolvedJavaMethod nextOpcodeMethod;

    public SubstrateTruffleBytecodeHandlerStub(SubstrateTruffleBytecodeHandlerStubHolder stubHolder, ResolvedJavaType declaringClass, TruffleBytecodeHandlerCallsite callsite,
                    boolean threading, ResolvedJavaMethod nextOpcodeMethod, boolean needSafepoint) {
        super(callsite.getStubName(), true, declaringClass, ResolvedSignature.fromList(callsite.getArgumentTypes(),
                        callsite.getReturnType()), declaringClass.getDeclaredConstructors(false)[0].getConstantPool());
        this.stubHolder = stubHolder;
        this.callsite = callsite;
        this.threading = threading;
        this.nextOpcodeMethod = nextOpcodeMethod;
        this.needSafepoint = needSafepoint;
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, AnalysisMethod method, HostedProviders providers, Purpose purpose) {
        HostedGraphKit kit = new HostedGraphKit(debug, providers, method);
        return callsite.createStub(kit, method, threading, nextOpcodeMethod, () -> stubHolder.getBytecodeHandlers(callsite.getEnclosingMethod()));
    }

    public TruffleBytecodeHandlerCallsite getCallsite() {
        return callsite;
    }

    /**
     * Converts actual parameter JavaKind to kinds supported by AssignedLocation.
     *
     * @see AssignedLocation#forRegister
     */
    private static JavaKind toAssignedLocationJavaKind(JavaKind kind) {
        return switch (kind) {
            case Float, Double -> JavaKind.Double;
            default -> JavaKind.Long;
        };
    }

    @Override
    public SubstrateCallingConventionType getCallingConvention() {
        SubstrateTargetDescription target = ConfigurationValues.getTarget();
        RegisterConfig registerConfig = SubstrateRegisterConfigFactory.singleton()
                        .newRegisterFactory(SubstrateRegisterConfig.ConfigKind.NORMAL, null, target, SubstrateOptions.PreserveFramePointer.getValue());

        Register returnRegister = null;
        ResolvedJavaType returnType = callsite.getReturnType();
        if (returnType.getJavaKind() != JavaKind.Void) {
            returnRegister = registerConfig.getReturnRegister(returnType.getJavaKind());
            GraalError.guarantee(returnRegister != null, "Cannot allocate register for return type %s", returnType.getUnqualifiedName());
        }

        List<Register> argumentRegisters = new ArrayList<>();

        for (ArgumentInfo argumentInfo : callsite.getArgumentInfos()) {
            // For arguments configured with returnValue=true, reuse the return register as their
            // register allocation. This avoids unnecessary register moves by ensuring the handler's
            // return value is already placed into the correct argument location upon return.
            if (argumentInfo.copyFromReturn()) {
                argumentRegisters.add(returnRegister);
                continue;
            }

            // Find next available register
            Register registerForCurrentArgument = null;
            List<Register> filteredAllocatableRegisters = registerConfig.filterAllocatableRegisters(target.arch.getPlatformKind(argumentInfo.type().getJavaKind()),
                            registerConfig.getAllocatableRegisters());
            for (Register register : filteredAllocatableRegisters) {
                if (argumentRegisters.contains(register)) {
                    // register is used as preceding argument
                    continue;
                }
                if (register.equals(returnRegister)) {
                    // register is used as return register
                    continue;
                }
                if ((SubstrateControlFlowIntegrity.useSoftwareCFI() && register.equals(SubstrateControlFlowIntegrity.singleton().getCFITargetRegister()))) {
                    // register is used as software CFI register
                    continue;
                }

                registerForCurrentArgument = register;
                break;
            }

            GraalError.guarantee(registerForCurrentArgument != null, "no register available");
            argumentRegisters.add(registerForCurrentArgument);
        }

        List<ResolvedJavaType> argumentTypes = callsite.getArgumentTypes();
        AssignedLocation[] parameters = new AssignedLocation[argumentTypes.size()];
        SubstrateCallingConventionArgumentKind[] parameterKinds = new SubstrateCallingConventionArgumentKind[argumentTypes.size()];

        for (int i = 0; i < parameters.length; i++) {
            parameters[i] = AssignedLocation.forRegister(argumentRegisters.get(i), toAssignedLocationJavaKind(argumentTypes.get(i).getJavaKind()));
            // TruffleBytecodeHandlerCallsite either preserves the value or returns the updated
            // value in the same argument location
            parameterKinds[i] = callsite.isArgumentImmutable(i) ? IMMUTABLE : VALUE_REFERENCE;
        }

        AssignedLocation[] returnLoations = AssignedLocation.EMPTY_ARRAY;
        if (returnRegister != null) {
            returnLoations = new AssignedLocation[]{AssignedLocation.forRegister(returnRegister, toAssignedLocationJavaKind(callsite.getReturnType().getJavaKind()))};
        }

        return SubstrateCallingConventionType.makeCustom(false, parameters, returnLoations, parameterKinds, false, false);
    }

    private static final List<AnnotationValue> INJECTED_ANNOTATIONS = List.of(
                    newAnnotationValue(SkipStackOverflowCheck.class),
                    newAnnotationValue(NeverStrengthenGraphWithConstants.class),
                    newAnnotationValue(NeverInline.class,
                                    "value", "Keep bytecode handler stubs as standalone compilations to ease register pressure in caller and enable tail call threading"),
                    newAnnotationValue(ExplicitCallingConvention.class,
                                    "value", SubstrateCallingConventionKind.Custom),
                    newAnnotationValue(Deoptimizer.DeoptStub.class,
                                    "stubType", NoDeoptStub));

    private static final List<AnnotationValue> INJECTED_ANNOTATIONS_NO_SAFEPOINT = List.of(
                    newAnnotationValue(SkipStackOverflowCheck.class),
                    newAnnotationValue(SkipEpilogueSafepointCheck.class),
                    newAnnotationValue(NeverStrengthenGraphWithConstants.class),
                    newAnnotationValue(NeverInline.class,
                                    "value", "Keep bytecode handler stubs as standalone compilations to ease register pressure in caller and enable tail call threading"),
                    newAnnotationValue(ExplicitCallingConvention.class,
                                    "value", SubstrateCallingConventionKind.Custom),
                    newAnnotationValue(Deoptimizer.DeoptStub.class,
                                    "stubType", NoDeoptStub));

    @Override
    public List<AnnotationValue> getInjectedAnnotations() {
        return needSafepoint ? INJECTED_ANNOTATIONS : INJECTED_ANNOTATIONS_NO_SAFEPOINT;
    }

    @Override
    public boolean hasNeverInlineDirective() {
        return true;
    }

    @Override
    public boolean allowStrengthenGraphWithConstants() {
        return false;
    }

    static ResolvedJavaType unwrap(ResolvedJavaType type) {
        if (type instanceof WrappedJavaType wrappedJavaType) {
            return wrappedJavaType.getWrapped();
        }
        return type;
    }

    static ResolvedJavaMethod unwrap(ResolvedJavaMethod method) {
        if (method instanceof WrappedJavaMethod wrappedJavaMethod) {
            return wrappedJavaMethod.getWrapped();
        }
        return method;
    }

    static TruffleBytecodeHandlerTypes asTruffleBytecodeHandlerTypes(TruffleKnownHostTypes truffleKnownHostTypes) {
        return new TruffleBytecodeHandlerTypes(unwrap(truffleKnownHostTypes.BytecodeInterpreterSwitch),
                        unwrap(truffleKnownHostTypes.BytecodeInterpreterHandlerConfig),
                        unwrap(truffleKnownHostTypes.BytecodeInterpreterHandler),
                        unwrap(truffleKnownHostTypes.BytecodeInterpreterFetchOpcode));
    }
}
