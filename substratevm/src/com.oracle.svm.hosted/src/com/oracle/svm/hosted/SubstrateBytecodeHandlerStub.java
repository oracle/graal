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
package com.oracle.svm.hosted;

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
import com.oracle.svm.shared.NeverInline;
import com.oracle.svm.core.NeverStrengthenGraphWithConstants;
import com.oracle.svm.core.SkipEpilogueSafepointCheck;
import com.oracle.svm.core.SkipStackOverflowCheck;
import com.oracle.svm.core.SubstrateControlFlowIntegrity;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTarget;
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
import com.oracle.svm.util.OriginalMethodProvider;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.api.directives.BytecodeInterpreterDirectives.BytecodeInterpreterHandler;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.phases.util.BytecodeHandlerConfig;
import jdk.graal.compiler.phases.util.BytecodeHandlerConfig.ArgumentInfo;
import jdk.graal.compiler.phases.util.BytecodeHandlerStubHelper;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Substrate implementation of generated stubs for bytecode handlers.
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
public final class SubstrateBytecodeHandlerStub extends NonBytecodeMethod implements CustomCallingConventionMethod {

    private final SubstrateBytecodeHandlerStubHelper stubHolder;
    private final boolean threading;
    private final boolean needSafepoint;
    /** True for the default fallback stub that returns to the interpreter dispatch loop. */
    private final boolean isDefault;
    private final ResolvedJavaMethod nextOpcodeMethod;
    private final int templateIndex;
    /** Declaring type that owns the interpreter. */
    private final ResolvedJavaType interpreterHolder;
    /** Handler configuration that defines the argument expansion. */
    private final BytecodeHandlerConfig config;
    /** Bytecode handler method the stub was created for; null for the default fallback stub. */
    private final ResolvedJavaMethod targetMethod;

    public SubstrateBytecodeHandlerStub(SubstrateBytecodeHandlerStubHelper stubHolder, ResolvedJavaType declaringClass, String stubName,
                    ResolvedJavaType interpreterHolder, BytecodeHandlerConfig config, boolean threading, ResolvedJavaMethod nextOpcodeMethod, boolean needSafepoint, boolean isDefault,
                    ResolvedJavaMethod targetMethod, int templateIndex) {
        super(stubName, true, declaringClass, ResolvedSignature.fromList(config.getStubAbiArgumentTypes(),
                        config.getReturnType()), declaringClass.getDeclaredConstructors(false)[0].getConstantPool());
        this.stubHolder = stubHolder;
        this.threading = threading;
        this.isDefault = isDefault;
        this.nextOpcodeMethod = nextOpcodeMethod;
        this.templateIndex = templateIndex;
        this.needSafepoint = needSafepoint;
        this.interpreterHolder = interpreterHolder;
        this.config = config;
        this.targetMethod = targetMethod;
        assert targetMethod != null || isDefault;
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, AnalysisMethod method, HostedProviders providers, Purpose purpose) {
        HostedGraphKit kit = new HostedGraphKit(debug, providers, method);
        if (isDefault) {
            Register fallbackReturnRegister = config.hasCopyFromReturnArgument() ? null : getReturnRegister(getRegisterConfig());
            return BytecodeHandlerStubHelper.createEmptyStub(kit, config, fallbackReturnRegister, templateIndex, SubstrateBytecodeHandlerUnwindPath::writeTemplateStateOnCallee);
        }
        return BytecodeHandlerStubHelper.createStub(kit, method, 0, threading, nextOpcodeMethod,
                        index -> stubHolder.getBytecodeHandlers(interpreterHolder, config, index), config, targetMethod, templateIndex,
                        SubstrateBytecodeHandlerUnwindPath::writeTemplateStateOnCallee, SubstrateBytecodeHandlerUnwindPath::writeOnCallee);
    }

    /**
     * Returns whether {@code method} is the Java handler invoked by this generated stub. The
     * target and callee can be represented by different hosted or analysis wrappers, so direct
     * identity is preferred and comparison of their original Java methods is used as a fallback.
     * Default stubs have no Java handler target and always return false.
     */
    public boolean isTargetMethod(ResolvedJavaMethod method) {
        if (targetMethod == null) {
            return false;
        }
        if (targetMethod.equals(method)) {
            return true;
        }
        ResolvedJavaMethod originalTargetMethod = OriginalMethodProvider.getOriginalMethod(targetMethod);
        ResolvedJavaMethod originalMethod = OriginalMethodProvider.getOriginalMethod(method);
        return originalTargetMethod != null && originalTargetMethod.equals(originalMethod);
    }

    /** Returns whether this is the fallback stub that returns to the interpreter dispatch loop. */
    public boolean isDefaultStub() {
        return isDefault;
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

    private static RegisterConfig getRegisterConfig() {
        SubstrateTarget target = SubstrateTarget.singleton();
        return SubstrateRegisterConfigFactory.singleton().newRegisterFactory(SubstrateRegisterConfig.ConfigKind.NORMAL, null, target, SubstrateOptions.PreserveFramePointer.getValue());
    }

    private Register getReturnRegister(RegisterConfig registerConfig) {
        Register returnRegister = null;
        ResolvedJavaType returnType = config.getReturnType();
        if (returnType.getJavaKind() != JavaKind.Void) {
            returnRegister = registerConfig.getReturnRegister(returnType.getJavaKind());
            GraalError.guarantee(returnRegister != null, "Cannot allocate register for return type %s", returnType.getUnqualifiedName());
        }
        return returnRegister;
    }

    private record AllocatableRegisters(Architecture arch, List<Register> gpRegisters, List<Register> floatRegisters) {

        AssignedLocation allocate(JavaKind kind) {
            Register register = switch (kind) {
                case Float, Double -> removeRegister(floatRegisters, kind);
                default -> removeRegister(gpRegisters, kind);
            };
            assert arch.canStoreValue(register.getRegisterCategory(), arch.getPlatformKind(kind)) : register + " cannot store " + kind;
            return AssignedLocation.forRegister(register, toAssignedLocationJavaKind(kind));
        }

        private static Register removeRegister(List<Register> registers, JavaKind kind) {
            GraalError.guarantee(!registers.isEmpty(), "no register available for %s", kind);
            return registers.removeFirst();
        }

        static AllocatableRegisters create(RegisterConfig registerConfig, boolean hasPendingExceptionState, Register reservedReturnRegister) {
            Architecture arch = SubstrateTarget.singleton().arch;
            List<Register> gpRegisters = getOrderedAllocatableRegisters(registerConfig, arch, hasPendingExceptionState);
            List<Register> floatRegisters = getOrderedFloatAllocatableRegisters(registerConfig, arch);
            if (reservedReturnRegister != null) {
                gpRegisters.remove(reservedReturnRegister);
                floatRegisters.remove(reservedReturnRegister);
            }
            return new AllocatableRegisters(arch, gpRegisters, floatRegisters);
        }

        /*
         * Prefer registers that do not overlap with the normal platform ABI argument registers,
         * including floating-point/vector argument registers. Handler values then have a better
         * chance to stay in bytecode-handler ABI locations across ordinary calls inside the stub,
         * avoiding moves and spills. The normal ABI registers remain fallbacks when the preferred
         * registers are exhausted. These lists define only the order within each register class;
         * actual selection is restricted to registers exposed by RegisterConfig, so platform-owned
         * reserved registers stay controlled by the platform register configuration. The AMD64 GP
         * list intentionally does not include rbp.
         */
        private static final List<String> AMD64_BYTECODE_HANDLER_GP_ARGUMENT_ORDER = List.of(
                        "rbx", "r11", "r10", "r14", "r13", "r12",
                        "r9", "r8", "rcx", "rdx", "rsi", "rdi", "rax");

        private static final List<String> AMD64_BYTECODE_HANDLER_FP_ARGUMENT_ORDER = List.of(
                        "xmm8", "xmm9", "xmm10", "xmm11", "xmm12", "xmm13", "xmm14", "xmm15",
                        "xmm7", "xmm6", "xmm5", "xmm4", "xmm3", "xmm2", "xmm1", "xmm0");

        /*
         * On AArch64, prefer caller-saved registers for values that may be live on exception edges.
         * The exception far-return path does not restore ordinary callee-saved registers from these
         * custom stub frames, while caller-saved registers are killed by invokes and therefore force
         * the compiler to preserve exception-edge live values explicitly.
         */
        private static final List<String> AARCH64_BYTECODE_HANDLER_GP_ARGUMENT_ORDER = List.of(
                        "r11", "r12", "r13", "r14", "r15", "r16", "r17",
                        "r18", "r19", "r20", "r21", "r22", "r23", "r24",
                        "r25", "r26", "r27", "r28",
                        "r10", "r9", "r8", "r7", "r6", "r5", "r4",
                        "r3", "r2", "r1", "r0");

        private static final List<String> AARCH64_BYTECODE_HANDLER_FP_ARGUMENT_ORDER = List.of(
                        "v8", "v9", "v10", "v11", "v12", "v13", "v14", "v15",
                        "v16", "v17", "v18", "v19", "v20", "v21", "v22", "v23",
                        "v24", "v25", "v26", "v27", "v28", "v29", "v30", "v31",
                        "v7", "v6", "v5", "v4", "v3", "v2", "v1", "v0");

        private static List<Register> getOrderedAllocatableRegisters(RegisterConfig registerConfig, Architecture arch, boolean hasPendingExceptionState) {
            List<Register> allocatableRegisters = new ArrayList<>(registerConfig.filterAllocatableRegisters(arch.getPlatformKind(JavaKind.Long), registerConfig.getAllocatableRegisters()));

            if (hasPendingExceptionState) {
                /*
                 * The object return register carries the exception object on exception edges. Do not
                 * allocate it to a bytecode-handler argument when pending state can be live there.
                 */
                allocatableRegisters.remove(registerConfig.getReturnRegister(JavaKind.Object));
            }
            if (SubstrateControlFlowIntegrity.useSoftwareCFI()) {
                allocatableRegisters.remove(SubstrateControlFlowIntegrity.singleton().getCFITargetRegister());
            }
            return switch (arch) {
                case AMD64 _ -> selectOrderedPreferredRegisters(allocatableRegisters, AMD64_BYTECODE_HANDLER_GP_ARGUMENT_ORDER);
                case AArch64 _ -> selectOrderedPreferredRegisters(allocatableRegisters, AARCH64_BYTECODE_HANDLER_GP_ARGUMENT_ORDER);
                default -> allocatableRegisters;
            };
        }

        private static List<Register> getOrderedFloatAllocatableRegisters(RegisterConfig registerConfig, Architecture arch) {
            List<Register> allocatableRegisters = new ArrayList<>(registerConfig.filterAllocatableRegisters(arch.getPlatformKind(JavaKind.Double), registerConfig.getAllocatableRegisters()));
            return switch (arch) {
                case AMD64 _ -> selectOrderedPreferredRegisters(allocatableRegisters, AMD64_BYTECODE_HANDLER_FP_ARGUMENT_ORDER);
                case AArch64 _ -> selectOrderedPreferredRegisters(allocatableRegisters, AARCH64_BYTECODE_HANDLER_FP_ARGUMENT_ORDER);
                default -> allocatableRegisters;
            };
        }

        private static List<Register> selectOrderedPreferredRegisters(List<Register> allocatableRegisters, List<String> preferredRegisterNames) {
            List<Register> orderedRegisters = new ArrayList<>(allocatableRegisters.size());
            for (String registerName : preferredRegisterNames) {
                for (Register register : allocatableRegisters) {
                    if (register.name.equals(registerName)) {
                        orderedRegisters.add(register);
                        break;
                    }
                }
            }
            return orderedRegisters;
        }
    }

    private AssignedLocation[] allocateArgumentLocations(RegisterConfig registerConfig, Register reservedReturnRegister) {
        List<ArgumentInfo> stubAbiArgumentInfos = config.getStubAbiArgumentInfos();
        AssignedLocation[] argumentLocations = new AssignedLocation[stubAbiArgumentInfos.size()];
        AllocatableRegisters allocatableRegisters = AllocatableRegisters.create(registerConfig, config.hasPendingExceptionState(), reservedReturnRegister);

        /*
         * Values published as pending exception state must remain available at the throwing call.
         * Allocate them first so they receive the target architecture's preferred locations.
         */
        for (ArgumentInfo argumentInfo : stubAbiArgumentInfos) {
            if (argumentInfo.needsPendingExceptionState()) {
                argumentLocations[argumentInfo.index()] = allocatableRegisters.allocate(argumentInfo.type().getJavaKind());
            }
        }
        for (ArgumentInfo argumentInfo : stubAbiArgumentInfos) {
            if (!argumentInfo.needsPendingExceptionState()) {
                argumentLocations[argumentInfo.index()] = allocatableRegisters.allocate(argumentInfo.type().getJavaKind());
            }
        }
        return argumentLocations;
    }

    @Override
    public SubstrateCallingConventionType getCallingConvention() {
        RegisterConfig registerConfig = getRegisterConfig();
        ArgumentInfo copyFromReturnArgument = config.getCopyFromReturnArgument();
        Register fallbackReturnRegister = copyFromReturnArgument == null ? getReturnRegister(registerConfig) : null;

        /*
         * Without copyFromReturn, the normal platform return register is independent from all
         * bytecode-handler arguments and must not be allocated to one of them.
         */
        AssignedLocation[] parameters = allocateArgumentLocations(registerConfig, fallbackReturnRegister);

        SubstrateCallingConventionArgumentKind[] parameterKinds = new SubstrateCallingConventionArgumentKind[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            // BytecodeHandlerCallSite either preserves the value or returns the updated
            // value in the same argument location
            parameterKinds[i] = config.isStubAbiArgumentImmutable(i) ? IMMUTABLE : VALUE_REFERENCE;
        }

        AssignedLocation[] returnLocations = AssignedLocation.EMPTY_ARRAY;
        if (copyFromReturnArgument != null) {
            GraalError.guarantee(copyFromReturnArgument.type().getJavaKind().getStackKind() == config.getReturnType().getJavaKind().getStackKind(),
                            "returnValue argument type %s does not match return type %s", copyFromReturnArgument.type().getJavaKind(), config.getReturnType().getJavaKind());
            returnLocations = new AssignedLocation[]{parameters[copyFromReturnArgument.index()]};
        } else {
            if (fallbackReturnRegister != null) {
                returnLocations = new AssignedLocation[]{AssignedLocation.forRegister(fallbackReturnRegister, toAssignedLocationJavaKind(config.getReturnType().getJavaKind()))};
            }
        }

        return SubstrateCallingConventionType.makeCustom(false, parameters, returnLocations, parameterKinds, false, false);
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

}
