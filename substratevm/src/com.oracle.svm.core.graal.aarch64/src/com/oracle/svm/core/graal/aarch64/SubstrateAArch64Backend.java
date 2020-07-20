/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.aarch64;

import static com.oracle.svm.core.graal.code.SubstrateBackend.SubstrateMarkId.PROLOGUE_DECD_RSP;
import static com.oracle.svm.core.graal.code.SubstrateBackend.SubstrateMarkId.PROLOGUE_END;
import static com.oracle.svm.core.util.VMError.shouldNotReachHere;
import static com.oracle.svm.core.util.VMError.unimplemented;
import static jdk.vm.ci.aarch64.AArch64.lr;
import static jdk.vm.ci.aarch64.AArch64.r0;
import static jdk.vm.ci.aarch64.AArch64.r1;
import static jdk.vm.ci.aarch64.AArch64.r29;
import static jdk.vm.ci.aarch64.AArch64.sp;
import static org.graalvm.compiler.core.common.GraalOptions.ZapStackOnMethodEntry;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRValueUtil.asConstantValue;

import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.asm.BranchTargetOutOfBoundsException;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.PrefetchMode;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.ShiftType;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.aarch64.AArch64AddressLoweringByUse;
import org.graalvm.compiler.core.aarch64.AArch64ArithmeticLIRGenerator;
import org.graalvm.compiler.core.aarch64.AArch64LIRGenerator;
import org.graalvm.compiler.core.aarch64.AArch64LIRKindTool;
import org.graalvm.compiler.core.aarch64.AArch64MoveFactory;
import org.graalvm.compiler.core.aarch64.AArch64NodeLIRBuilder;
import org.graalvm.compiler.core.aarch64.AArch64NodeMatchRules;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.spi.LIRKindTool;
import org.graalvm.compiler.core.gen.DebugInfoBuilder;
import org.graalvm.compiler.core.gen.LIRGenerationProvider;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.StandardOp.BlockEndOp;
import org.graalvm.compiler.lir.StandardOp.LoadConstantOp;
import org.graalvm.compiler.lir.StandardOp.ZapRegistersOp;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.aarch64.AArch64BreakpointOp;
import org.graalvm.compiler.lir.aarch64.AArch64Call;
import org.graalvm.compiler.lir.aarch64.AArch64ControlFlow;
import org.graalvm.compiler.lir.aarch64.AArch64FrameMap;
import org.graalvm.compiler.lir.aarch64.AArch64FrameMapBuilder;
import org.graalvm.compiler.lir.aarch64.AArch64LIRInstruction;
import org.graalvm.compiler.lir.aarch64.AArch64Move;
import org.graalvm.compiler.lir.aarch64.AArch64Move.PointerCompressionOp;
import org.graalvm.compiler.lir.aarch64.AArch64PrefetchOp;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.asm.DataBuilder;
import org.graalvm.compiler.lir.asm.FrameContext;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.lir.framemap.FrameMapBuilder;
import org.graalvm.compiler.lir.framemap.FrameMapBuilderTool;
import org.graalvm.compiler.lir.framemap.ReferenceMapBuilder;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodes.BreakpointNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.DirectCallTargetNode;
import org.graalvm.compiler.nodes.IndirectCallTargetNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.SafepointNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.spi.NodeValueMap;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.phases.common.AddressLoweringByUsePhase;
import org.graalvm.compiler.phases.tiers.SuitesProvider;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.graal.code.PatchConsumerFactory;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
import com.oracle.svm.core.graal.code.SubstrateCompiledCode;
import com.oracle.svm.core.graal.code.SubstrateDataBuilder;
import com.oracle.svm.core.graal.code.SubstrateDebugInfoBuilder;
import com.oracle.svm.core.graal.code.SubstrateLIRGenerator;
import com.oracle.svm.core.graal.code.SubstrateNodeLIRBuilder;
import com.oracle.svm.core.graal.lir.VerificationMarkerOp;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallLinkage;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;
import com.oracle.svm.core.graal.nodes.CGlobalDataLoadAddressNode;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.SubstrateReferenceMapBuilder;
import com.oracle.svm.core.meta.CompressedNullConstant;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.nodes.SafepointCheckNode;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Value;

public class SubstrateAArch64Backend extends SubstrateBackend implements LIRGenerationProvider {

    protected static CompressEncoding getCompressEncoding() {
        return ImageSingletons.lookup(CompressEncoding.class);
    }

    public SubstrateAArch64Backend(Providers providers) {
        super(providers);
    }

    @Opcode("CALL_DIRECT")
    public static class SubstrateAArch64DirectCallOp extends AArch64Call.DirectCallOp {
        public static final LIRInstructionClass<SubstrateAArch64DirectCallOp> TYPE = LIRInstructionClass.create(SubstrateAArch64DirectCallOp.class);

        private final RuntimeConfiguration runtimeConfiguration;
        private final int newThreadStatus;
        @Use({REG, OperandFlag.ILLEGAL}) private Value javaFrameAnchor;

        public SubstrateAArch64DirectCallOp(RuntimeConfiguration runtimeConfiguration, ResolvedJavaMethod callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState state,
                        Value javaFrameAnchor, int newThreadStatus) {
            super(TYPE, callTarget, result, parameters, temps, state);
            this.runtimeConfiguration = runtimeConfiguration;
            this.javaFrameAnchor = javaFrameAnchor;
            this.newThreadStatus = newThreadStatus;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            maybeTransitionToNative(crb, masm, runtimeConfiguration, javaFrameAnchor, state, newThreadStatus);
            AArch64Call.directCall(crb, masm, callTarget, null, state);
        }
    }

    @Opcode("CALL_INDIRECT")
    public static class SubstrateAArch64IndirectCallOp extends AArch64Call.IndirectCallOp {
        public static final LIRInstructionClass<SubstrateAArch64IndirectCallOp> TYPE = LIRInstructionClass.create(SubstrateAArch64IndirectCallOp.class);
        private final RuntimeConfiguration runtimeConfiguration;
        private final int newThreadStatus;
        @Use({REG, OperandFlag.ILLEGAL}) private Value javaFrameAnchor;

        public SubstrateAArch64IndirectCallOp(RuntimeConfiguration runtimeConfiguration, ResolvedJavaMethod callTarget, Value result, Value[] parameters, Value[] temps, Value targetAddress,
                        LIRFrameState state, Value javaFrameAnchor, int newThreadStatus) {
            super(TYPE, callTarget, result, parameters, temps, targetAddress, state);
            this.runtimeConfiguration = runtimeConfiguration;
            this.javaFrameAnchor = javaFrameAnchor;
            this.newThreadStatus = newThreadStatus;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            maybeTransitionToNative(crb, masm, runtimeConfiguration, javaFrameAnchor, state, newThreadStatus);
            super.emitCode(crb, masm);
        }
    }

    static void maybeTransitionToNative(CompilationResultBuilder crb, AArch64MacroAssembler masm, RuntimeConfiguration runtimeConfiguration, Value javaFrameAnchor, LIRFrameState state,
                    int newThreadStatus) {
        if (ValueUtil.isIllegal(javaFrameAnchor)) {
            /* Not a call that needs to set up a JavaFrameAnchor. */
            assert newThreadStatus == StatusSupport.STATUS_ILLEGAL;
            return;
        }
        assert StatusSupport.isValidStatus(newThreadStatus);

        Register anchor = ValueUtil.asRegister(javaFrameAnchor);

        /*
         * We record the instruction to load the current instruction pointer as a Call infopoint, so
         * that the same metadata is emitted in the machine code as for a normal call instruction.
         * The adr loads the offset 8 relative to the begin of the adr instruction, which is the
         * same as for a blr instruction. So the usual AArch64 specific semantics that all the
         * metadata is registered for the end of the instruction just works.
         */
        int startPos = masm.position();
        try (ScratchRegister scratch = masm.getScratchRegister()) {
            Register tempRegister = scratch.getRegister();
            // Save PC
            masm.adr(tempRegister, 4); // Read PC + 4
            crb.recordIndirectCall(startPos, masm.position(), null, state);
            masm.str(64, tempRegister, AArch64Address.createPairUnscaledImmediateAddress(anchor, runtimeConfiguration.getJavaFrameAnchorLastIPOffset()));
            // Save SP
            masm.mov(64, tempRegister, sp);
            masm.str(64, tempRegister, AArch64Address.createPairUnscaledImmediateAddress(anchor, runtimeConfiguration.getJavaFrameAnchorLastSPOffset()));
        }

        if (SubstrateOptions.MultiThreaded.getValue()) {
            /*
             * Change VMThread status from Java to Native. Note a "store release" is needed for this
             * update to ensure VMThread status is only updated once all prior stores are also
             * observable.
             */
            try (ScratchRegister scratch1 = masm.getScratchRegister(); ScratchRegister scratch2 = masm.getScratchRegister()) {
                Register statusValueRegister = scratch1.getRegister();
                Register statusAddressRegister = scratch2.getRegister();
                masm.mov(statusValueRegister, newThreadStatus);
                masm.lea(statusAddressRegister, AArch64Address.createUnscaledImmediateAddress(runtimeConfiguration.getThreadRegister(), runtimeConfiguration.getVMThreadStatusOffset()));
                masm.stlr(32, statusValueRegister, statusAddressRegister);
            }
        }
    }

    /**
     * Marks a point that is unreachable because a previous instruction never returns.
     */
    @Opcode("DEAD_END")
    public static class DeadEndOp extends LIRInstruction implements BlockEndOp {
        public static final LIRInstructionClass<DeadEndOp> TYPE = LIRInstructionClass.create(DeadEndOp.class);

        public DeadEndOp() {
            super(TYPE);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
            /*
             * We could add some code in debug builds here checking that it is really unreachable.
             */
        }
    }

    protected static final class SubstrateLIRGenerationResult extends LIRGenerationResult {

        private final SharedMethod method;

        public SubstrateLIRGenerationResult(CompilationIdentifier compilationId, LIR lir, FrameMapBuilder frameMapBuilder, RegisterAllocationConfig registerAllocationConfig,
                        CallingConvention callingConvention, SharedMethod method) {
            super(compilationId, lir, frameMapBuilder, registerAllocationConfig, callingConvention);
            this.method = method;

            if (method.canDeoptimize() || method.isDeoptTarget()) {
                ((FrameMapBuilderTool) frameMapBuilder).getFrameMap().reserveOutgoing(16);
            }
        }

        public SharedMethod getMethod() {
            return method;
        }
    }

    protected final class SubstrateAArch64LIRGenerator extends AArch64LIRGenerator implements SubstrateLIRGenerator {

        public SubstrateAArch64LIRGenerator(LIRKindTool lirKindTool, AArch64ArithmeticLIRGenerator arithmeticLIRGen, MoveFactory moveFactory, Providers providers, LIRGenerationResult lirGenRes) {
            super(lirKindTool, arithmeticLIRGen, moveFactory, providers, lirGenRes);
        }

        @Override
        public SubstrateLIRGenerationResult getResult() {
            return (SubstrateLIRGenerationResult) super.getResult();
        }

        @Override
        public SubstrateRegisterConfig getRegisterConfig() {
            return (SubstrateRegisterConfig) super.getRegisterConfig();
        }

        @Override
        protected void emitForeignCallOp(ForeignCallLinkage linkage, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {
            SubstrateForeignCallLinkage callTarget = (SubstrateForeignCallLinkage) linkage;
            ResolvedJavaMethod targetMethod = callTarget.getMethod();
            append(new SubstrateAArch64DirectCallOp(getRuntimeConfiguration(), targetMethod, result, arguments, temps, info, Value.ILLEGAL, StatusSupport.STATUS_ILLEGAL));
        }

        @Override
        public void emitUnwind(Value operand) {
            throw shouldNotReachHere("handled by lowering");
        }

        @Override
        public void emitDeoptimize(Value actionAndReason, Value failedSpeculation, LIRFrameState state) {
            throw shouldNotReachHere("Substrate VM does not use deoptimization");
        }

        @Override
        public void emitVerificationMarker(Object marker) {
            append(new VerificationMarkerOp(marker));
        }

        @Override
        public void emitInstructionSynchronizationBarrier() {
            append(new AArch64InstructionSynchronizationBarrierOp());
        }

        @Override
        public void emitFarReturn(AllocatableValue result, Value stackPointer, Value ip, boolean fromMethodWithCalleeSavedRegisters) {
            append(new AArch64FarReturnOp(asAllocatable(result), asAllocatable(stackPointer), asAllocatable(ip)));
        }

        @Override
        public void emitDeadEnd() {
            append(new DeadEndOp());
        }

        @Override
        public void emitPrefetchAllocate(Value address) {
            append(new AArch64PrefetchOp(asAddressValue(address), PrefetchMode.PSTL1KEEP));
        }

        @Override
        public Value emitCompress(Value pointer, CompressEncoding encoding, boolean isNonNull) {
            Variable result = newVariable(getLIRKindTool().getNarrowOopKind());
            boolean nonNull = useLinearPointerCompression() || isNonNull;
            append(new AArch64Move.CompressPointerOp(result, asAllocatable(pointer), getRegisterConfig().getHeapBaseRegister().asValue(), encoding, nonNull, getLIRKindTool()));
            return result;
        }

        @Override
        public Value emitUncompress(Value pointer, CompressEncoding encoding, boolean isNonNull) {
            assert pointer.getValueKind(LIRKind.class).getPlatformKind() == getLIRKindTool().getNarrowOopKind().getPlatformKind();
            Variable result = newVariable(getLIRKindTool().getObjectKind());
            boolean nonNull = useLinearPointerCompression() || isNonNull;
            append(new AArch64Move.UncompressPointerOp(result, asAllocatable(pointer), getRegisterConfig().getHeapBaseRegister().asValue(), encoding, nonNull, getLIRKindTool()));
            return result;
        }

        @Override
        public void emitCCall(long address, CallingConvention nativeCallingConvention, Value[] args) {
            throw unimplemented();
        }

        @Override
        public ZapRegistersOp createZapRegisters(Register[] zappedRegisters, JavaConstant[] zapValues) {
            throw unimplemented();
        }

        @Override
        public LIRInstruction createZapArgumentSpace(StackSlot[] zappedStack, JavaConstant[] zapValues) {
            throw unimplemented();
        }

        @Override
        public void emitConvertNullToZero(AllocatableValue result, Value value) {
            if (useLinearPointerCompression()) {
                append(new AArch64Move.ConvertNullToZeroOp(result, (AllocatableValue) value));
            } else {
                emitMove(result, value);
            }
        }

        @Override
        public void emitConvertZeroToNull(AllocatableValue result, Value value) {
            if (useLinearPointerCompression()) {
                append(new AArch64Move.ConvertZeroToNullOp(result, (AllocatableValue) value));
            } else {
                emitMove(result, value);
            }
        }

        @Override
        public void emitReturn(JavaKind kind, Value input) {
            AllocatableValue operand = Value.ILLEGAL;
            if (input != null) {
                operand = resultOperandFor(kind, input.getValueKind());
                emitMove(operand, input);
            }
            append(new AArch64ControlFlow.ReturnOp(operand));
        }
    }

    public final class SubstrateAArch64NodeLIRBuilder extends AArch64NodeLIRBuilder implements SubstrateNodeLIRBuilder {

        public SubstrateAArch64NodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool gen, AArch64NodeMatchRules nodeMatchRules) {
            super(graph, gen, nodeMatchRules);
        }

        @Override
        public void visitSafepointNode(SafepointNode node) {
            throw shouldNotReachHere("handled by lowering");
        }

        @Override
        public void visitBreakpointNode(BreakpointNode node) {
            JavaType[] sig = new JavaType[node.arguments().size()];
            for (int i = 0; i < sig.length; i++) {
                sig[i] = node.arguments().get(i).stamp(NodeView.DEFAULT).javaType(gen.getMetaAccess());
            }

            CallingConvention convention = gen.getRegisterConfig().getCallingConvention(SubstrateCallingConventionType.JavaCall, null, sig, gen);
            append(new AArch64BreakpointOp(visitInvokeArguments(convention, node.arguments())));
        }

        @Override
        protected DebugInfoBuilder createDebugInfoBuilder(StructuredGraph graph, NodeValueMap nodeValueMap) {
            return new SubstrateDebugInfoBuilder(graph, gen.getProviders().getMetaAccessExtensionProvider(), nodeValueMap);
        }

        @Override
        protected void emitDirectCall(DirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
            ResolvedJavaMethod targetMethod = callTarget.targetMethod();
            append(new SubstrateAArch64DirectCallOp(getRuntimeConfiguration(), targetMethod, result, parameters, temps, callState, setupJavaFrameAnchor(callTarget),
                            getNewThreadStatus(callTarget)));
        }

        @Override
        protected void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
            // The register allocator cannot handle variables at call sites, need a fixed register.
            Register targetRegister = AArch64.lr;
            AllocatableValue targetAddress = targetRegister.asValue(FrameAccess.getWordStamp().getLIRKind(getLIRGeneratorTool().getLIRKindTool()));
            gen.emitMove(targetAddress, operand(callTarget.computedAddress()));
            ResolvedJavaMethod targetMethod = callTarget.targetMethod();
            append(new SubstrateAArch64IndirectCallOp(getRuntimeConfiguration(), targetMethod, result, parameters, temps, targetAddress, callState, setupJavaFrameAnchor(callTarget),
                            getNewThreadStatus(callTarget)));
        }

        private AllocatableValue setupJavaFrameAnchor(CallTargetNode callTarget) {
            if (!hasJavaFrameAnchor(callTarget)) {
                return Value.ILLEGAL;
            }
            /* Register allocator cannot handle variables at call sites, need a fixed register. */
            Register frameAnchorRegister = AArch64.r13;
            AllocatableValue frameAnchor = frameAnchorRegister.asValue(FrameAccess.getWordStamp().getLIRKind(getLIRGeneratorTool().getLIRKindTool()));
            gen.emitMove(frameAnchor, operand(getJavaFrameAnchor(callTarget)));
            return frameAnchor;
        }

        @Override
        public void emitBranch(LogicNode node, LabelRef trueSuccessor, LabelRef falseSuccessor, double trueSuccessorProbability) {
            if (node instanceof SafepointCheckNode) {
                AArch64SafepointCheckOp op = new AArch64SafepointCheckOp();
                append(op);
                append(new AArch64ControlFlow.BranchOp(op.getConditionFlag(), trueSuccessor, falseSuccessor, trueSuccessorProbability));
            } else {
                super.emitBranch(node, trueSuccessor, falseSuccessor, trueSuccessorProbability);
            }
        }

        @Override
        public void emitCGlobalDataLoadAddress(CGlobalDataLoadAddressNode node) {
            Variable result = gen.newVariable(gen.getLIRKindTool().getWordKind());
            append(new AArch64CGlobalDataLoadAddressOp(node.getDataInfo(), result));
            setResult(node, result);
        }

        @Override
        public Variable emitReadReturnAddress() {
            return getLIRGeneratorTool().emitMove(StackSlot.get(getLIRGeneratorTool().getLIRKind(FrameAccess.getWordStamp()), -FrameAccess.returnAddressSize(), true));
        }
    }

    protected static class SubstrateAArch64FrameContext implements FrameContext {

        @Override
        public void enter(CompilationResultBuilder crb) {
            FrameMap frameMap = crb.frameMap;
            final int frameSize = frameMap.frameSize();
            final int totalFrameSize = frameMap.totalFrameSize();
            assert frameSize + 2 * crb.target.arch.getWordSize() == totalFrameSize : "total framesize should be framesize + 2 words";
            AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;
            crb.blockComment("[method prologue]");

            try (ScratchRegister sc = masm.getScratchRegister()) {
                int wordSize = crb.target.arch.getWordSize();
                Register rscratch1 = sc.getRegister();
                assert totalFrameSize > 0;
                if (frameSize < 1 << 9) {
                    masm.sub(64, sp, sp, totalFrameSize);
                    masm.stp(64, r29, lr, AArch64Address.createScaledImmediateAddress(sp, frameSize / wordSize));
                } else {
                    masm.stp(64, r29, lr, AArch64Address.createPreIndexedImmediateAddress(sp, -2));
                    if (frameSize < 1 << 12) {
                        masm.sub(64, sp, sp, totalFrameSize - 2 * wordSize);
                    } else {
                        masm.mov(rscratch1, totalFrameSize - 2 * wordSize);
                        masm.sub(64, sp, sp, rscratch1);
                    }
                }
            }
            if (ZapStackOnMethodEntry.getValue(crb.getOptions())) {
                try (ScratchRegister sc = masm.getScratchRegister()) {
                    Register scratch = sc.getRegister();
                    int longSize = 8;
                    masm.mov(64, scratch, sp);
                    AArch64Address address = AArch64Address.createPostIndexedImmediateAddress(scratch, longSize);
                    try (ScratchRegister sc2 = masm.getScratchRegister()) {
                        Register value = sc2.getRegister();
                        masm.mov(value, 0xBADDECAFFC0FFEEL);
                        for (int i = 0; i < frameSize; i += longSize) {
                            masm.str(64, value, address);
                        }
                    }

                }
            }
            crb.recordMark(PROLOGUE_DECD_RSP);
            crb.recordMark(PROLOGUE_END);
        }

        @Override
        public void leave(CompilationResultBuilder crb) {
            int frameSize = crb.frameMap.frameSize();

            crb.recordMark(SubstrateMarkId.EPILOGUE_START);
            AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;
            FrameMap frameMap = crb.frameMap;
            final int totalFrameSize = frameMap.totalFrameSize();

            crb.blockComment("[method epilogue]");
            try (ScratchRegister sc = masm.getScratchRegister()) {
                int wordSize = crb.target.arch.getWordSize();
                Register rscratch1 = sc.getRegister();
                assert totalFrameSize > 0;
                if (frameSize < 1 << 9) {
                    masm.ldp(64, r29, lr, AArch64Address.createScaledImmediateAddress(sp, frameSize / wordSize));
                    masm.add(64, sp, sp, totalFrameSize);
                } else {
                    if (frameSize < 1 << 12) {
                        masm.add(64, sp, sp, totalFrameSize - 2 * wordSize);
                    } else {
                        masm.mov(rscratch1, totalFrameSize - 2 * wordSize);
                        masm.add(64, sp, sp, rscratch1);
                    }
                    masm.ldp(64, r29, lr, AArch64Address.createPostIndexedImmediateAddress(sp, 2));
                }
            }
            if (frameSize != 0) {
                crb.recordMark(SubstrateMarkId.EPILOGUE_INCD_RSP);
            }
        }

        @Override
        public void returned(CompilationResultBuilder crb) {
            crb.recordMark(SubstrateMarkId.EPILOGUE_END);
        }

        @Override
        public boolean hasFrame() {
            return true;
        }
    }

    /**
     * Generates the prolog of a {@link com.oracle.svm.core.deopt.Deoptimizer.StubType#EntryStub}
     * method.
     */
    protected static class DeoptEntryStubContext extends SubstrateAArch64FrameContext {
        @Override
        public void enter(CompilationResultBuilder tasm) {
            AArch64MacroAssembler asm = (AArch64MacroAssembler) tasm.asm;

            // Move the DeoptimizedFrame into r1
            asm.ldr(64, r1, AArch64Address.createUnscaledImmediateAddress(sp, 0));

            // Store the original return value registers
            int scratchOffset = DeoptimizedFrame.getScratchSpaceOffset();
            asm.str(64, r0, AArch64Address.createUnscaledImmediateAddress(r1, scratchOffset));

            // Move DeoptimizedFrame into the first argument register (static method)
            asm.mov(64, r0, r1);

            super.enter(tasm);
        }
    }

    /**
     * Generates the epilog of a {@link com.oracle.svm.core.deopt.Deoptimizer.StubType#ExitStub}
     * method.
     */
    protected static class DeoptExitStubContext extends SubstrateAArch64FrameContext {
        @Override
        public void enter(CompilationResultBuilder tasm) {
            AArch64MacroAssembler asm = (AArch64MacroAssembler) tasm.asm;

            /* The new stack pointer is passed in as the first method parameter. */
            asm.mov(64, sp, r0);

            super.enter(tasm);
        }

        @Override
        public void leave(CompilationResultBuilder tasm) {
            AArch64MacroAssembler asm = (AArch64MacroAssembler) tasm.asm;

            super.leave(tasm);

            // Restore the return value registers (the DeoptimizedFrame is in r1).
            int scratchOffset = DeoptimizedFrame.getScratchSpaceOffset();
            asm.ldr(64, r0, AArch64Address.createUnscaledImmediateAddress(r0, scratchOffset));
        }
    }

    static class SubstrateReferenceMapBuilderFactory implements FrameMap.ReferenceMapBuilderFactory {
        @Override
        public ReferenceMapBuilder newReferenceMapBuilder(int totalFrameSize) {
            return new SubstrateReferenceMapBuilder(totalFrameSize);
        }
    }

    protected static class SubstrateAArch64MoveFactory extends AArch64MoveFactory {

        private final SharedMethod method;
        @SuppressWarnings("unused") private final LIRKindTool lirKindTool;
        @SuppressWarnings("unused") private final SubstrateAArch64RegisterConfig registerConfig;

        protected SubstrateAArch64MoveFactory(SharedMethod method, LIRKindTool lirKindTool, SubstrateAArch64RegisterConfig registerConfig) {
            super();
            this.method = method;
            this.lirKindTool = lirKindTool;
            this.registerConfig = registerConfig;
        }

        @Override
        public boolean allowConstantToStackMove(Constant constant) {
            if (constant instanceof SubstrateObjectConstant && method.isDeoptTarget()) {
                return false;
            }
            return super.allowConstantToStackMove(constant);
        }

        @Override
        public AArch64LIRInstruction createLoad(AllocatableValue dst, Constant src) {
            if (CompressedNullConstant.COMPRESSED_NULL.equals(src)) {
                return super.createLoad(dst, JavaConstant.INT_0);
            } else if (src instanceof SubstrateObjectConstant) {
                return loadObjectConstant(dst, (SubstrateObjectConstant) src);
            }
            return super.createLoad(dst, src);
        }

        @Override
        public LIRInstruction createStackLoad(AllocatableValue dst, Constant src) {
            if (CompressedNullConstant.COMPRESSED_NULL.equals(src)) {
                return super.createStackLoad(dst, JavaConstant.INT_0);
            } else if (src instanceof SubstrateObjectConstant) {
                return loadObjectConstant(dst, (SubstrateObjectConstant) src);
            }
            return super.createStackLoad(dst, src);
        }

        protected AArch64LIRInstruction loadObjectConstant(AllocatableValue dst, SubstrateObjectConstant constant) {
            if (ReferenceAccess.singleton().haveCompressedReferences()) {
                RegisterValue heapBase = registerConfig.getHeapBaseRegister().asValue();
                return new LoadCompressedObjectConstantOp(dst, constant, heapBase, getCompressEncoding(), lirKindTool);
            }
            return new AArch64Move.LoadInlineConstant(constant, dst);
        }
    }

    /*
     * The constant denotes the result produced by this node. Thus if the constant is compressed,
     * the result must be compressed and vice versa. Both compressed and uncompressed constants can
     * be loaded by compiled code.
     *
     * Method getConstant() could uncompress the constant value from the node input. That would
     * require a few indirections and an allocation of an uncompressed constant. The allocation
     * could be eliminated if we stored uncompressed ConstantValue as input. But as this method
     * looks performance-critical, it is still faster to memorize the original constant in the node.
     */
    public static final class LoadCompressedObjectConstantOp extends PointerCompressionOp implements LoadConstantOp {
        public static final LIRInstructionClass<LoadCompressedObjectConstantOp> TYPE = LIRInstructionClass.create(LoadCompressedObjectConstantOp.class);

        static JavaConstant asCompressed(SubstrateObjectConstant constant) {
            // We only want compressed references in code
            return constant.isCompressed() ? constant : constant.compress();
        }

        private final SubstrateObjectConstant constant;

        public LoadCompressedObjectConstantOp(AllocatableValue result, SubstrateObjectConstant constant, AllocatableValue baseRegister, CompressEncoding encoding, LIRKindTool lirKindTool) {
            super(TYPE, result, new ConstantValue(lirKindTool.getNarrowOopKind(), asCompressed(constant)), baseRegister, encoding, true, lirKindTool);
            this.constant = constant;
        }

        @Override
        public Constant getConstant() {
            return constant;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            /*
             * WARNING: must NOT have side effects. Preserve the flags register!
             */
            Register resultReg = getResultRegister();
            int referenceSize = ConfigurationValues.getObjectLayout().getReferenceSize();
            Constant inputConstant = asConstantValue(getInput()).getConstant();
            if (masm.target.inlineObjects) {
                crb.recordInlineDataInCode(inputConstant);
                masm.mov(resultReg, 0xDEADDEADDEADDEADL, true);
            } else {
                crb.recordDataReferenceInCode(inputConstant, referenceSize);
                AArch64Address address = AArch64Address.createScaledImmediateAddress(resultReg, 0x0);
                masm.adrpLdr(referenceSize * 8, resultReg, address);
            }
            if (!constant.isCompressed()) { // the result is expected to be uncompressed
                Register baseReg = getBaseRegister(crb);
                assert !baseReg.equals(Register.None) || getShift() != 0 : "no compression in place";
                masm.add(64, resultReg, baseReg, resultReg, ShiftType.LSL, getShift());
            }
        }
    }

    public FrameMapBuilder newFrameMapBuilder(RegisterConfig registerConfig) {
        RegisterConfig registerConfigNonNull = registerConfig == null ? getCodeCache().getRegisterConfig() : registerConfig;
        return new AArch64FrameMapBuilder(newFrameMap(registerConfigNonNull), getCodeCache(), registerConfigNonNull);
    }

    public FrameMap newFrameMap(RegisterConfig registerConfig) {
        return new AArch64FrameMap(getProviders().getCodeCache(), registerConfig, new SubstrateReferenceMapBuilderFactory());
    }

    @Override
    public CompilationResultBuilder newCompilationResultBuilder(LIRGenerationResult lirGenResult, FrameMap frameMap, CompilationResult compilationResult, CompilationResultBuilderFactory factory) {
        Assembler masm = new AArch64MacroAssembler(getTarget());
        PatchConsumerFactory patchConsumerFactory;
        if (SubstrateUtil.HOSTED) {
            patchConsumerFactory = PatchConsumerFactory.HostedPatchConsumerFactory.factory();
        } else {
            patchConsumerFactory = PatchConsumerFactory.NativePatchConsumerFactory.factory();
        }
        masm.setCodePatchingAnnotationConsumer(patchConsumerFactory.newConsumer(compilationResult));
        SharedMethod method = ((SubstrateLIRGenerationResult) lirGenResult).getMethod();
        Deoptimizer.StubType stubType = method.getDeoptStubType();
        DataBuilder dataBuilder = new SubstrateDataBuilder();
        final FrameContext frameContext;
        if (stubType == Deoptimizer.StubType.EntryStub) {
            frameContext = new DeoptEntryStubContext();
        } else if (stubType == Deoptimizer.StubType.ExitStub) {
            frameContext = new DeoptExitStubContext();
        } else {
            frameContext = new SubstrateAArch64FrameContext();
        }
        LIR lir = lirGenResult.getLIR();
        OptionValues options = lir.getOptions();
        DebugContext debug = lir.getDebug();
        Register uncompressedNullRegister = useLinearPointerCompression() ? getHeapBaseRegister(lirGenResult) : Register.None;
        CompilationResultBuilder tasm = factory.createBuilder(getCodeCache(), getForeignCalls(), lirGenResult.getFrameMap(), masm, dataBuilder, frameContext, options, debug, compilationResult,
                        uncompressedNullRegister);
        tasm.setTotalFrameSize(lirGenResult.getFrameMap().totalFrameSize());
        return tasm;
    }

    protected AArch64ArithmeticLIRGenerator createArithmeticLIRGen(AllocatableValue nullRegisterValue) {
        return new AArch64ArithmeticLIRGenerator(nullRegisterValue);
    }

    protected static SubstrateAArch64RegisterConfig getRegisterConfig(LIRGenerationResult lirGenRes) {
        return (SubstrateAArch64RegisterConfig) lirGenRes.getRegisterConfig();
    }

    private static Register getHeapBaseRegister(LIRGenerationResult lirGenRes) {
        return getRegisterConfig(lirGenRes).getHeapBaseRegister();
    }

    protected AArch64MoveFactory createMoveFactory(LIRGenerationResult lirGenRes) {
        SharedMethod method = ((SubstrateLIRGenerationResult) lirGenRes).getMethod();
        return new SubstrateAArch64MoveFactory(method, createLirKindTool(), getRegisterConfig(lirGenRes));
    }

    protected static class SubstrateAArch64LIRKindTool extends AArch64LIRKindTool {
        @Override
        public LIRKind getNarrowOopKind() {
            return LIRKind.compressedReference(AArch64Kind.QWORD);
        }

        @Override
        public LIRKind getNarrowPointerKind() {
            throw VMError.shouldNotReachHere();
        }
    }

    protected AArch64LIRKindTool createLirKindTool() {
        return new SubstrateAArch64LIRKindTool();
    }

    @Override
    public LIRGeneratorTool newLIRGenerator(LIRGenerationResult lirGenRes) {
        RegisterValue nullRegisterValue = useLinearPointerCompression() ? getHeapBaseRegister(lirGenRes).asValue(LIRKind.unknownReference(AArch64Kind.QWORD)) : null;
        AArch64ArithmeticLIRGenerator arithmeticLIRGen = createArithmeticLIRGen(nullRegisterValue);
        AArch64MoveFactory moveFactory = createMoveFactory(lirGenRes);
        return new SubstrateAArch64LIRGenerator(createLirKindTool(), arithmeticLIRGen, moveFactory, getProviders(), lirGenRes);
    }

    protected AArch64Kind getCompressedOopKind() {
        return AArch64Kind.QWORD;
    }

    protected AArch64NodeMatchRules createMatchRules(LIRGeneratorTool lirGen) {
        return new AArch64NodeMatchRules(lirGen);
    }

    @Override
    public NodeLIRBuilderTool newNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen) {
        AArch64NodeMatchRules nodeMatchRules = createMatchRules(lirGen);
        return new SubstrateAArch64NodeLIRBuilder(graph, lirGen, nodeMatchRules);
    }

    private static boolean useLinearPointerCompression() {
        return SubstrateOptions.SpawnIsolates.getValue();
    }

    @Override
    public RegisterAllocationConfig newRegisterAllocationConfig(RegisterConfig registerConfig, String[] allocationRestrictedTo) {
        RegisterConfig registerConfigNonNull = registerConfig == null ? getCodeCache().getRegisterConfig() : registerConfig;
        return new RegisterAllocationConfig(registerConfigNonNull, allocationRestrictedTo);
    }

    @Override
    public CompilationResult createJNITrampolineMethod(ResolvedJavaMethod method, CompilationIdentifier identifier,
                    RegisterValue threadArg, int threadIsolateOffset, RegisterValue methodIdArg, int methodObjEntryPointOffset) {

        CompilationResult result = new CompilationResult(identifier);
        AArch64MacroAssembler asm = new AArch64MacroAssembler(getTarget());
        try (ScratchRegister scratch = asm.getScratchRegister()) {
            Register scratchRegister = scratch.getRegister();
            if (SubstrateOptions.SpawnIsolates.getValue()) { // method id is offset from heap base
                asm.ldr(64, scratchRegister, AArch64Address.createUnscaledImmediateAddress(threadArg.getRegister(), threadIsolateOffset));
                asm.add(64, scratchRegister, scratchRegister, methodIdArg.getRegister());
                asm.ldr(64, scratchRegister, AArch64Address.createUnscaledImmediateAddress(scratchRegister, methodObjEntryPointOffset));
            } else { // method id is address of method object
                asm.ldr(64, scratchRegister, AArch64Address.createUnscaledImmediateAddress(methodIdArg.getRegister(), methodObjEntryPointOffset));
            }
            asm.jmp(scratchRegister);
        }
        result.recordMark(asm.position(), PROLOGUE_DECD_RSP);
        result.recordMark(asm.position(), PROLOGUE_END);
        byte[] instructions = asm.close(true);
        result.setTargetCode(instructions, instructions.length);
        result.setTotalFrameSize(getTarget().wordSize); // not really, but 0 not allowed
        return result;
    }

    @Override
    protected CompiledCode createCompiledCode(ResolvedJavaMethod method, CompilationRequest compilationRequest, CompilationResult compilationResult, boolean isDefault, OptionValues options) {
        return new SubstrateCompiledCode(compilationResult);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, LIR lir, ResolvedJavaMethod installedCodeOwner) {
        try {
            crb.buildLabelOffsets(lir);
            crb.emit(lir);
        } catch (BranchTargetOutOfBoundsException e) {
            // A branch estimation was wrong, now retry with conservative label ranges, this
            // should always work
            crb.setConservativeLabelRanges();
            crb.resetForEmittingCode();
            lir.resetLabels();
            crb.emit(lir);
        }
    }

    @Override
    public SuitesProvider getSuites() {
        throw unimplemented();
    }

    /**
     * Returns the amount of scratch space which must be reserved for return value registers in
     * {@link DeoptimizedFrame}.
     */
    public static int getDeoptScratchSpace() {
        // Space for two 64-bit registers: rax and xmm0
        return 2 * 8;
    }

    @Override
    public LIRGenerationResult newLIRGenerationResult(CompilationIdentifier compilationId, LIR lir, RegisterAllocationConfig registerAllocationConfig, StructuredGraph graph, Object stub) {
        SharedMethod method = (SharedMethod) graph.method();
        CallingConvention callingConvention = CodeUtil.getCallingConvention(getCodeCache(), method.isEntryPoint() ? SubstrateCallingConventionType.NativeCallee
                        : SubstrateCallingConventionType.JavaCallee, method, this);
        return new SubstrateLIRGenerationResult(compilationId, lir, newFrameMapBuilder(registerAllocationConfig.getRegisterConfig()), registerAllocationConfig, callingConvention, method);
    }

    @Override
    public Phase newAddressLoweringPhase(CodeCacheProvider codeCache) {
        return new AddressLoweringByUsePhase(new AArch64AddressLoweringByUse(createLirKindTool(), false));
    }
}
