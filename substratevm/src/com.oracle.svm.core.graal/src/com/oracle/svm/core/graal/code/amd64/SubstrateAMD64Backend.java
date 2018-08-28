/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.code.amd64;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;
import static com.oracle.svm.core.util.VMError.unimplemented;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rsp;
import static jdk.vm.ci.amd64.AMD64.xmm0;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static org.graalvm.compiler.lir.LIRValueUtil.asConstantValue;

import java.util.Collection;

import org.graalvm.collections.EconomicSet;
import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Address.Scale;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.amd64.AMD64ArithmeticLIRGenerator;
import org.graalvm.compiler.core.amd64.AMD64LIRGenerator;
import org.graalvm.compiler.core.amd64.AMD64LIRKindTool;
import org.graalvm.compiler.core.amd64.AMD64MoveFactory;
import org.graalvm.compiler.core.amd64.AMD64MoveFactoryBase;
import org.graalvm.compiler.core.amd64.AMD64MoveFactoryBase.BackupSlotProvider;
import org.graalvm.compiler.core.amd64.AMD64NodeLIRBuilder;
import org.graalvm.compiler.core.amd64.AMD64NodeMatchRules;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.spi.LIRKindTool;
import org.graalvm.compiler.core.gen.DebugInfoBuilder;
import org.graalvm.compiler.core.target.Backend;
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
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.amd64.AMD64AddressValue;
import org.graalvm.compiler.lir.amd64.AMD64BreakpointOp;
import org.graalvm.compiler.lir.amd64.AMD64Call;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.BranchOp;
import org.graalvm.compiler.lir.amd64.AMD64FrameMap;
import org.graalvm.compiler.lir.amd64.AMD64FrameMapBuilder;
import org.graalvm.compiler.lir.amd64.AMD64LIRInstruction;
import org.graalvm.compiler.lir.amd64.AMD64Move;
import org.graalvm.compiler.lir.amd64.AMD64Move.MoveFromConstOp;
import org.graalvm.compiler.lir.amd64.AMD64Move.PointerCompressionOp;
import org.graalvm.compiler.lir.amd64.AMD64PrefetchOp;
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
import org.graalvm.compiler.nodes.DirectCallTargetNode;
import org.graalvm.compiler.nodes.IndirectCallTargetNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.SafepointNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.spi.NodeValueMap;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.tiers.SuitesProvider;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.amd64.FrameAccess;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.graal.code.SubstrateCompiledCode;
import com.oracle.svm.core.graal.code.SubstrateDataBuilder;
import com.oracle.svm.core.graal.code.SubstrateLIRGenerator;
import com.oracle.svm.core.graal.code.SubstrateNodeLIRBuilder;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallLinkage;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;
import com.oracle.svm.core.graal.nodes.CGlobalDataLoadAddressNode;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.SubstrateReferenceMapBuilder;
import com.oracle.svm.core.meta.CompressedNullConstant;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.nodes.SafepointCheckNode;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Value;

public class SubstrateAMD64Backend extends Backend {

    public static final String MARK_PROLOGUE_DECD_RSP = "PROLOGUE_DECD_RSP";
    public static final String MARK_PROLOGUE_SAVED_REGS = "PROLOGUE_SAVED_REGS";
    public static final String MARK_PROLOGUE_END = "PROLOGUE_END";
    public static final String MARK_EPILOGUE_START = "EPILOGUE_START";
    public static final String MARK_EPILOGUE_INCD_RSP = "EPILOGUE_INCD_RSP";
    public static final String MARK_EPILOGUE_END = "EPILOGUE_END";

    protected static CompressEncoding getCompressEncoding() {
        return ImageSingletons.lookup(CompressEncoding.class);
    }

    public SubstrateAMD64Backend(Providers providers) {
        super(providers);
    }

    /**
     * A direct call, but without alignment nops for the call instruction. Used for fatal exception
     * calls.
     */
    @Opcode("CALL_DIRECT")
    public static class SubstrateAMD64DirectCallOp extends AMD64Call.DirectCallOp {
        public static final LIRInstructionClass<SubstrateAMD64DirectCallOp> TYPE = LIRInstructionClass.create(SubstrateAMD64DirectCallOp.class);

        public SubstrateAMD64DirectCallOp(ResolvedJavaMethod callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(TYPE, callTarget, result, parameters, temps, state);
        }

        @Override
        public void emitCode(CompilationResultBuilder tasm, AMD64MacroAssembler masm) {
            AMD64Call.directCall(tasm, masm, callTarget, null, false, state);
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

        public SubstrateLIRGenerationResult(CompilationIdentifier compilationId, LIR lir, FrameMapBuilder frameMapBuilder, CallingConvention callingConvention, SharedMethod method) {
            super(compilationId, lir, frameMapBuilder, callingConvention);
            this.method = method;

            if (method.canDeoptimize() || method.isDeoptTarget()) {
                ((FrameMapBuilderTool) frameMapBuilder).getFrameMap().reserveOutgoing(16);
            }
        }

        public SharedMethod getMethod() {
            return method;
        }
    }

    protected final class SubstrateAMD64LIRGenerator extends AMD64LIRGenerator implements SubstrateLIRGenerator {

        public SubstrateAMD64LIRGenerator(LIRKindTool lirKindTool, AMD64ArithmeticLIRGenerator arithmeticLIRGen, MoveFactory moveFactory, Providers providers, LIRGenerationResult lirGenRes) {
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

        private Register getHeapBaseRegister() {
            return getRegisterConfig().getHeapBaseRegister();
        }

        // @Override
        // public boolean canEliminateRedundantMoves() {
        // if (getResult().getMethod().isDeoptTarget()) {
        // /*
        // * Redundant move elimination can extend the liferanges of intervals, even over
        // * method calls. This would introduce new stack slots which are live for a method
        // * call, but not recognized during register allocation.
        // */
        // return false;
        // }
        // return true;
        // }

        @Override
        protected void emitForeignCallOp(ForeignCallLinkage linkage, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {
            SubstrateForeignCallLinkage callTarget = (SubstrateForeignCallLinkage) linkage;
            ResolvedJavaMethod targetMethod = callTarget.getMethod();
            append(new SubstrateAMD64DirectCallOp(targetMethod, result, arguments, temps, info));
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
        public void emitCCall(long address, CallingConvention nativeCallingConvention, Value[] args, int numberOfFloatingPointArguments) {
            throw unimplemented();
        }

        @Override
        public Value emitReadInstructionPointer() {
            return emitMove(new AMD64AddressValue(FrameAccess.getWordStamp().getLIRKind(getLIRKindTool()), AMD64.rip.asValue(FrameAccess.getWordStamp().getLIRKind(getLIRKindTool())), 0));
        }

        // private static LIRKind toStackKind(LIRKind kind) {
        // if (kind.getPlatformKind() instanceof Kind) {
        // Kind stackKind = ((Kind) kind.getPlatformKind()).getStackKind();
        // return kind.changeType(stackKind);
        // } else {
        // return kind;
        // }
        // }
        //
        // @Override
        // public Variable emitLoad(LIRKind kind, Value address, LIRFrameState state) {
        // AMD64AddressValue loadAddress = asAddressValue(address);
        // Variable result = newVariable(toStackKind(kind));
        // append(new LoadOp((Kind) kind.getPlatformKind(), result, loadAddress, state));
        // return result;
        // }
        //
        // @Override
        // public void emitStore(LIRKind kind, Value address, Value inputVal, LIRFrameState state) {
        // AMD64AddressValue storeAddress = asAddressValue(address);
        // if (isConstant(inputVal)) {
        // JavaConstant c = asConstant(inputVal);
        // if (canStoreConstant(c)) {
        // append(new StoreConstantOp((Kind) kind.getPlatformKind(), storeAddress, c, state));
        // return;
        // }
        // }
        // Variable input = load(inputVal);
        // append(new StoreOp((Kind) kind.getPlatformKind(), storeAddress, input, state));
        //
        // }
        //
        // @Override
        // public Value emitCompareAndSwap(Value address, Value expectedValue, Value newValue, Value
        // trueValue, Value falseValue) {
        // LIRKind kind = newValue.getLIRKind();
        // assert kind.equals(expectedValue.getLIRKind());
        // Kind memKind = (Kind) kind.getPlatformKind();
        //
        // AMD64AddressValue addressValue = asAddressValue(address);
        // RegisterValue raxRes = AMD64.rax.asValue(kind);
        // emitMove(raxRes, expectedValue);
        // append(new CompareAndSwapOp(memKind, raxRes, addressValue, raxRes,
        // asAllocatable(newValue)));
        //
        // assert trueValue.getLIRKind().equals(falseValue.getLIRKind());
        // Variable result = newVariable(trueValue.getLIRKind());
        // append(new CondMoveOp(result, Condition.EQ, asAllocatable(trueValue), falseValue));
        // return result;
        // }
        //
        // @Override
        // public Value emitAtomicReadAndAdd(Value address, Value delta) {
        // LIRKind kind = delta.getLIRKind();
        // Kind memKind = (Kind) kind.getPlatformKind();
        // Variable result = newVariable(kind);
        // AMD64AddressValue addressValue = asAddressValue(address);
        // append(new AMD64Move.AtomicReadAndAddOp(memKind, result, addressValue,
        // asAllocatable(delta)));
        // return result;
        // }
        //
        // @Override
        // public Value emitAtomicReadAndWrite(Value address, Value newValue) {
        // LIRKind kind = newValue.getLIRKind();
        // Kind memKind = (Kind) kind.getPlatformKind();
        // Variable result = newVariable(kind);
        // AMD64AddressValue addressValue = asAddressValue(address);
        // append(new AMD64Move.AtomicReadAndWriteOp(memKind, result, addressValue,
        // asAllocatable(newValue)));
        // return result;
        // }
        //
        // @Override
        // public void emitNullCheck(Value address, LIRFrameState state) {
        // if (address.getValueKind().getPlatformKind() == AMD64Kind.DWORD) {
        // CompressEncoding encoding = compressEncoding;
        // Value uncompressed;
        // if (encoding.getShift() <= 3) {
        // LIRKind wordKind = LIRKind.unknownReference(target().arch.getWordKind());
        // uncompressed = new AMD64AddressValue(wordKind, getHeapBaseRegister().asValue(wordKind),
        // asAllocatable(address), AMD64Address.Scale.fromInt(1 << encoding.getShift()), 0);
        // } else {
        // uncompressed = emitUncompress(address, encoding, false);
        // }
        // append(new AMD64Move.NullCheckOp(asAddressValue(uncompressed), state));
        // return;
        // }
        // super.emitNullCheck(address, state);
        // }

        @Override
        public void emitFarReturn(AllocatableValue result, Value sp, Value ip) {
            append(new AMD64FarReturnOp(result, asAllocatable(sp), asAllocatable(ip)));
        }

        @Override
        public void emitDeadEnd() {
            append(new DeadEndOp());
        }

        @Override
        public void emitPrefetchAllocate(Value address) {
            append(new AMD64PrefetchOp(asAddressValue(address), SubstrateOptions.AllocatePrefetchInstr.getValue()));
        }

        @Override
        public Value emitCompress(Value pointer, CompressEncoding encoding, boolean isNonNull) {
            Variable result = newVariable(getLIRKindTool().getNarrowOopKind());
            boolean nonNull = useLinearPointerCompression() || isNonNull;
            append(new AMD64Move.CompressPointerOp(result, asAllocatable(pointer), getHeapBaseRegister().asValue(), encoding, nonNull, getLIRKindTool()));
            return result;
        }

        @Override
        public Value emitUncompress(Value pointer, CompressEncoding encoding, boolean isNonNull) {
            assert pointer.getValueKind(LIRKind.class).getPlatformKind() == getLIRKindTool().getNarrowOopKind().getPlatformKind();
            Variable result = newVariable(getLIRKindTool().getObjectKind());
            boolean nonNull = useLinearPointerCompression() || isNonNull;
            append(new AMD64Move.UncompressPointerOp(result, asAllocatable(pointer), getHeapBaseRegister().asValue(), encoding, nonNull, getLIRKindTool()));
            return result;
        }

        @Override
        public void emitConvertNullToZero(AllocatableValue result, Value value) {
            if (useLinearPointerCompression()) {
                append(new AMD64Move.ConvertNullToZeroOp(result, (AllocatableValue) value));
            } else {
                emitMove(result, value);
            }
        }

        @Override
        public void emitConvertZeroToNull(AllocatableValue result, Value value) {
            if (useLinearPointerCompression()) {
                append(new AMD64Move.ConvertZeroToNullOp(result, (AllocatableValue) value));
            } else {
                emitMove(result, value);
            }
        }
    }

    public static final class SubstrateDebugInfoBuilder extends DebugInfoBuilder {
        public SubstrateDebugInfoBuilder(NodeValueMap nodeValueMap, DebugContext debug) {
            super(nodeValueMap, debug);
        }

        @Override
        protected JavaKind storageKind(JavaType type) {
            return ((SharedType) type).getStorageKind();
        }
    }

    public static final class SubstrateAMD64NodeLIRBuilder extends AMD64NodeLIRBuilder implements SubstrateNodeLIRBuilder {

        public SubstrateAMD64NodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool gen, AMD64NodeMatchRules nodeMatchRules) {
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
            append(new AMD64BreakpointOp(visitInvokeArguments(convention, node.arguments())));
        }

        @Override
        protected DebugInfoBuilder createDebugInfoBuilder(StructuredGraph graph, NodeValueMap nodeValueMap) {
            return new SubstrateDebugInfoBuilder(nodeValueMap, graph.getDebug());
        }

        @Override
        public Value[] visitInvokeArguments(CallingConvention invokeCc, Collection<ValueNode> arguments) {
            Value[] values = super.visitInvokeArguments(invokeCc, arguments);

            SubstrateCallingConventionType type = (SubstrateCallingConventionType) ((SubstrateCallingConvention) invokeCc).getType();
            if (type.nativeABI) {
                // Native functions might have varargs, in which case we need to set %al to the
                // number of XMM registers used for passing arguments
                int xmmCount = 0;
                for (Value v : values) {
                    if (isRegister(v) && asRegister(v).getRegisterCategory().equals(AMD64.XMM)) {
                        xmmCount++;
                    }
                }
                assert xmmCount <= 8;
                AllocatableValue xmmCountRegister = AMD64.rax.asValue(LIRKind.value(AMD64Kind.DWORD));
                gen.emitMoveConstant(xmmCountRegister, JavaConstant.forInt(xmmCount));
            }
            return values;
        }

        @Override
        protected void emitDirectCall(DirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
            ResolvedJavaMethod targetMethod = callTarget.targetMethod();
            append(new SubstrateAMD64DirectCallOp(targetMethod, result, parameters, temps, callState));
        }

        @Override
        protected void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
            // The register allocator cannot handle variables at call sites, need a fixed register.
            Register targetRegister = AMD64.rax;
            if (((SubstrateCallingConventionType) callTarget.callType()).nativeABI) {
                // Do not use RAX for C calls, it contains the number of XMM registers for varargs.
                targetRegister = AMD64.r10;
            }
            AllocatableValue targetAddress = targetRegister.asValue(FrameAccess.getWordStamp().getLIRKind(getLIRGeneratorTool().getLIRKindTool()));
            gen.emitMove(targetAddress, operand(callTarget.computedAddress()));
            ResolvedJavaMethod targetMethod = callTarget.targetMethod();
            append(new AMD64Call.IndirectCallOp(targetMethod, result, parameters, temps, targetAddress, callState));
        }

        @Override
        public void emitBranch(LogicNode node, LabelRef trueSuccessor, LabelRef falseSuccessor, double trueSuccessorProbability) {
            if (node instanceof SafepointCheckNode) {
                append(new AMD64DecrementingSafepointCheckOp());
                append(new BranchOp(ConditionFlag.Zero, trueSuccessor, falseSuccessor, trueSuccessorProbability));
            } else {
                super.emitBranch(node, trueSuccessor, falseSuccessor, trueSuccessorProbability);
            }
        }

        @Override
        public void emitCGlobalDataLoadAddress(CGlobalDataLoadAddressNode node) {
            Variable result = gen.newVariable(gen.getLIRKindTool().getWordKind());
            append(new AMD64CGlobalDataLoadAddressOp(node.getDataInfo(), result));
            setResult(node, result);
        }
    }

    protected static class SubstrateAMD64FrameContext implements FrameContext {

        @Override
        public void enter(CompilationResultBuilder tasm) {
            AMD64MacroAssembler asm = (AMD64MacroAssembler) tasm.asm;
            int frameSize = tasm.frameMap.frameSize();

            asm.decrementq(rsp, frameSize);
            tasm.recordMark(MARK_PROLOGUE_DECD_RSP);
            tasm.recordMark(MARK_PROLOGUE_END);
        }

        @Override
        public void leave(CompilationResultBuilder tasm) {
            AMD64MacroAssembler asm = (AMD64MacroAssembler) tasm.asm;
            int frameSize = tasm.frameMap.frameSize();

            tasm.recordMark(MARK_EPILOGUE_START);
            asm.incrementq(rsp, frameSize);
            if (frameSize != 0) {
                tasm.recordMark(MARK_EPILOGUE_INCD_RSP);
            }
            tasm.recordMark(MARK_EPILOGUE_END);
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
    protected static class DeoptEntryStubContext extends SubstrateAMD64FrameContext {
        @Override
        public void enter(CompilationResultBuilder tasm) {
            AMD64MacroAssembler asm = (AMD64MacroAssembler) tasm.asm;

            assert getDeoptScratchSpace() >= 16;

            // Move the DeoptimizedFrame into rdi
            asm.movq(rdi, new AMD64Address(rsp, 0));

            // Store the original return value registers
            int scratchOffset = DeoptimizedFrame.getScratchSpaceOffset();
            asm.movq(new AMD64Address(rdi, scratchOffset), rax);
            asm.movq(new AMD64Address(rdi, scratchOffset + 8), xmm0);

            super.enter(tasm);
        }
    }

    /**
     * Generates the epilog of a {@link com.oracle.svm.core.deopt.Deoptimizer.StubType#ExitStub}
     * method.
     */
    protected static class DeoptExitStubContext extends SubstrateAMD64FrameContext {
        @Override
        public void leave(CompilationResultBuilder tasm) {
            AMD64MacroAssembler asm = (AMD64MacroAssembler) tasm.asm;

            assert getDeoptScratchSpace() >= 16;

            super.leave(tasm);

            // Restore the return value registers (the DeoptimizedFrame is in rax).
            int scratchOffset = DeoptimizedFrame.getScratchSpaceOffset();
            asm.movq(xmm0, new AMD64Address(rax, scratchOffset + 8));
            asm.movq(rax, new AMD64Address(rax, scratchOffset));
        }
    }

    static class SubstrateReferenceMapBuilderFactory implements FrameMap.ReferenceMapBuilderFactory {
        @Override
        public ReferenceMapBuilder newReferenceMapBuilder(int totalFrameSize) {
            return new SubstrateReferenceMapBuilder(totalFrameSize);
        }
    }

    protected static class SubstrateAMD64MoveFactory extends AMD64MoveFactory {

        private final SharedMethod method;
        private final LIRKindTool lirKindTool;
        private final SubstrateAMD64RegisterConfig registerConfig;

        protected SubstrateAMD64MoveFactory(BackupSlotProvider backupSlotProvider, SharedMethod method, LIRKindTool lirKindTool, SubstrateAMD64RegisterConfig registerConfig) {
            super(backupSlotProvider);
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
        public AMD64LIRInstruction createLoad(AllocatableValue dst, Constant src) {
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

        protected AMD64LIRInstruction loadObjectConstant(AllocatableValue dst, SubstrateObjectConstant constant) {
            if (ReferenceAccess.singleton().haveCompressedReferences()) {
                RegisterValue heapBase = registerConfig.getHeapBaseRegister().asValue();
                return new LoadCompressedObjectConstantOp(dst, constant, heapBase, getCompressEncoding(), lirKindTool);
            }
            return new MoveFromConstOp(dst, constant);
        }

        /*
         * The constant denotes the result produced by this node. Thus if the constant is
         * compressed, the result must be compressed and vice versa. Both compressed and
         * uncompressed constants can be loaded by compiled code.
         *
         * Method getConstant() could uncompress the constant value from the node input. That would
         * require a few indirections and an allocation of an uncompressed constant. The allocation
         * could be eliminated if we stored uncompressed ConstantValue as input. But as this method
         * looks performance-critical, it is still faster to memorize the original constant in the
         * node.
         */
        public static final class LoadCompressedObjectConstantOp extends PointerCompressionOp implements LoadConstantOp {
            public static final LIRInstructionClass<LoadCompressedObjectConstantOp> TYPE = LIRInstructionClass.create(LoadCompressedObjectConstantOp.class);

            static JavaConstant asCompressed(SubstrateObjectConstant constant) {
                // We only want compressed references in code
                return constant.isCompressed() ? constant : constant.compress();
            }

            private final SubstrateObjectConstant constant;

            LoadCompressedObjectConstantOp(AllocatableValue result, SubstrateObjectConstant constant, AllocatableValue baseRegister, CompressEncoding encoding, LIRKindTool lirKindTool) {
                super(TYPE, result, new ConstantValue(lirKindTool.getNarrowOopKind(), asCompressed(constant)), baseRegister, encoding, true, lirKindTool);
                this.constant = constant;
            }

            @Override
            public Constant getConstant() {
                return constant;
            }

            @Override
            public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
                /*
                 * WARNING: must NOT have side effects. Preserve the flags register!
                 */
                Register resultReg = getResultRegister();
                int referenceSize = ConfigurationValues.getObjectLayout().getReferenceSize();
                Constant inputConstant = asConstantValue(getInput()).getConstant();
                if (masm.target.inlineObjects) {
                    crb.recordInlineDataInCode(inputConstant);
                    masm.movq(resultReg, 0xDEADDEADDEADDEADL, true);
                } else {
                    AMD64Address address = (AMD64Address) crb.recordDataReferenceInCode(inputConstant, referenceSize);
                    masm.movq(resultReg, address);
                }
                if (!constant.isCompressed()) { // the result is expected to be uncompressed
                    Register baseReg = getBaseRegister(crb);
                    assert !baseReg.equals(Register.None) || getShift() != 0 : "no compression in place";
                    masm.leaq(resultReg, new AMD64Address(baseReg, resultReg, Scale.fromShift(getShift())));
                }
            }
        }
    }

    @Override
    public FrameMapBuilder newFrameMapBuilder(RegisterConfig registerConfig) {
        RegisterConfig registerConfigNonNull = registerConfig == null ? getCodeCache().getRegisterConfig() : registerConfig;
        return new AMD64FrameMapBuilder(newFrameMap(registerConfigNonNull), getCodeCache(), registerConfigNonNull);
    }

    @Override
    public FrameMap newFrameMap(RegisterConfig registerConfig) {
        return new AMD64FrameMap(getProviders().getCodeCache(), registerConfig, new SubstrateReferenceMapBuilderFactory());
    }

    @Override
    public LIRGenerationResult newLIRGenerationResult(CompilationIdentifier compilationId, LIR lir, FrameMapBuilder frameMapBuilder, StructuredGraph graph, Object stub) {
        SharedMethod method = (SharedMethod) graph.method();
        CallingConvention callingConvention = CodeUtil.getCallingConvention(getCodeCache(), method.isEntryPoint() ? SubstrateCallingConventionType.NativeCallee
                        : SubstrateCallingConventionType.JavaCallee, method, this);
        return new SubstrateLIRGenerationResult(compilationId, lir, frameMapBuilder, callingConvention, method);
    }

    protected AMD64ArithmeticLIRGenerator createArithmeticLIRGen(RegisterValue nullRegisterValue) {
        return new AMD64ArithmeticLIRGenerator(nullRegisterValue, null);
    }

    protected static SubstrateAMD64RegisterConfig getRegisterConfig(LIRGenerationResult lirGenRes) {
        return (SubstrateAMD64RegisterConfig) lirGenRes.getRegisterConfig();
    }

    private static Register getHeapBaseRegister(LIRGenerationResult lirGenRes) {
        return getRegisterConfig(lirGenRes).getHeapBaseRegister();
    }

    protected AMD64MoveFactoryBase createMoveFactory(LIRGenerationResult lirGenRes, BackupSlotProvider backupSlotProvider) {
        SharedMethod method = ((SubstrateLIRGenerationResult) lirGenRes).getMethod();
        return new SubstrateAMD64MoveFactory(backupSlotProvider, method, createLirKindTool(), getRegisterConfig(lirGenRes));
    }

    protected static class SubstrateAMD64LIRKindTool extends AMD64LIRKindTool {
        @Override
        public LIRKind getNarrowOopKind() {
            return LIRKind.compressedReference(AMD64Kind.QWORD);
        }

        @Override
        public LIRKind getNarrowPointerKind() {
            throw VMError.shouldNotReachHere();
        }
    }

    protected LIRKindTool createLirKindTool() {
        return new SubstrateAMD64LIRKindTool();
    }

    @Override
    public LIRGeneratorTool newLIRGenerator(LIRGenerationResult lirGenRes) {
        RegisterValue nullRegisterValue = useLinearPointerCompression() ? getHeapBaseRegister(lirGenRes).asValue() : null;
        AMD64ArithmeticLIRGenerator arithmeticLIRGen = createArithmeticLIRGen(nullRegisterValue);
        BackupSlotProvider backupSlotProvider = new BackupSlotProvider(lirGenRes.getFrameMapBuilder());
        AMD64MoveFactoryBase moveFactory = createMoveFactory(lirGenRes, backupSlotProvider);
        return new SubstrateAMD64LIRGenerator(createLirKindTool(), arithmeticLIRGen, moveFactory, getProviders(), lirGenRes);
    }

    protected AMD64NodeMatchRules createMatchRules(LIRGeneratorTool lirGen) {
        return new AMD64NodeMatchRules(lirGen);
    }

    @Override
    public NodeLIRBuilderTool newNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen) {
        AMD64NodeMatchRules nodeMatchRules = createMatchRules(lirGen);
        return new SubstrateAMD64NodeLIRBuilder(graph, lirGen, nodeMatchRules);
    }

    private static boolean useLinearPointerCompression() {
        return SubstrateOptions.UseLinearPointerCompression.getValue() && SubstrateOptions.UseHeapBaseRegister.getValue();
    }

    @Override
    public CompilationResultBuilder newCompilationResultBuilder(LIRGenerationResult lirGenResult, FrameMap frameMap, CompilationResult compilationResult, CompilationResultBuilderFactory factory) {
        Assembler masm = createAssembler(frameMap);
        SharedMethod method = ((SubstrateLIRGenerationResult) lirGenResult).getMethod();
        Deoptimizer.StubType stubType = method.getDeoptStubType();
        DataBuilder dataBuilder = new SubstrateDataBuilder();
        final FrameContext frameContext;
        if (stubType == Deoptimizer.StubType.EntryStub) {
            frameContext = new DeoptEntryStubContext();
        } else if (stubType == Deoptimizer.StubType.ExitStub) {
            frameContext = new DeoptExitStubContext();
        } else {
            frameContext = new SubstrateAMD64FrameContext();
        }
        LIR lir = lirGenResult.getLIR();
        OptionValues options = lir.getOptions();
        DebugContext debug = lir.getDebug();
        Register nullRegister = useLinearPointerCompression() ? getHeapBaseRegister(lirGenResult) : Register.None;
        CompilationResultBuilder tasm = factory.createBuilder(getCodeCache(), getForeignCalls(), lirGenResult.getFrameMap(), masm, dataBuilder, frameContext, options, debug, compilationResult,
                        nullRegister);
        tasm.setTotalFrameSize(lirGenResult.getFrameMap().totalFrameSize());
        return tasm;
    }

    @Override
    public RegisterAllocationConfig newRegisterAllocationConfig(RegisterConfig registerConfig, String[] allocationRestrictedTo) {
        RegisterConfig registerConfigNonNull = registerConfig == null ? getCodeCache().getRegisterConfig() : registerConfig;
        return new RegisterAllocationConfig(registerConfigNonNull, allocationRestrictedTo);
    }

    @Override
    protected Assembler createAssembler(FrameMap frameMap) {
        return new AMD64MacroAssembler(getTarget());
    }

    @Override
    public CompiledCode createCompiledCode(ResolvedJavaMethod method, CompilationRequest compilationRequest, CompilationResult compilationResult) {
        return new SubstrateCompiledCode(compilationResult);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, LIR lir, ResolvedJavaMethod installedCodeOwner) {
        crb.emit(lir);
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
    public EconomicSet<Register> translateToCallerRegisters(EconomicSet<Register> calleeRegisters) {
        return calleeRegisters;
    }
}
