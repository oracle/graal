/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.asm;

import static jdk.graal.compiler.core.common.GraalOptions.IsolatedLoopHeaderAlignment;
import static jdk.vm.ci.code.ValueUtil.asStackSlot;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;
import java.util.Objects;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.asm.AbstractAddress;
import jdk.graal.compiler.asm.Assembler;
import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.code.CompilationResult.CodeAnnotation;
import jdk.graal.compiler.code.CompilationResult.JumpTable;
import jdk.graal.compiler.code.DataSection.Data;
import jdk.graal.compiler.core.common.cfg.AbstractControlFlowGraph;
import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.type.DataPointerConstant;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugOptions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.lir.ImplicitLIRFrameState;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstructionVerifier;
import jdk.graal.compiler.lir.LabelRef;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.lir.StandardOp.LabelHoldingOp;
import jdk.graal.compiler.lir.framemap.FrameMap;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.CoreProvidersDelegate;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.DebugInfo;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.DataSectionReference;
import jdk.vm.ci.code.site.InfopointReason;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.InvokeTarget;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.VMConstant;
import jdk.vm.ci.meta.Value;

/**
 * Fills in a {@link CompilationResult} as its code is being assembled.
 *
 * @see CompilationResultBuilderFactory
 */
public class CompilationResultBuilder extends CoreProvidersDelegate {

    public static class Options {
        @Option(help = "Include the LIR as comments with the final assembly.", type = OptionType.Debug) //
        public static final OptionKey<Boolean> PrintLIRWithAssembly = new OptionKey<>(false);
    }

    public static final List<LIRInstructionVerifier> NO_VERIFIERS = Collections.emptyList();

    private static class ExceptionInfo {

        public final int codeOffset;
        public final LabelRef exceptionEdge;

        ExceptionInfo(int pcOffset, LabelRef exceptionEdge) {
            this.codeOffset = pcOffset;
            this.exceptionEdge = exceptionEdge;
        }
    }

    public static class PendingImplicitException {

        public final int codeOffset;
        public final ImplicitLIRFrameState state;

        PendingImplicitException(int pcOffset, ImplicitLIRFrameState state) {
            this.codeOffset = pcOffset;
            this.state = state;
        }
    }

    public final Assembler<?> asm;
    public final DataBuilder dataBuilder;
    public final CompilationResult compilationResult;
    public final Register uncompressedNullRegister;
    public final TargetDescription target;
    public final FrameMap frameMap;

    /**
     * The LIR for which code is being generated.
     */
    protected final LIR lir;

    /**
     * The index of the block currently being emitted.
     */
    protected int currentBlockIndex;

    /**
     * The object that emits code for managing a method's frame.
     */
    public final FrameContext frameContext;

    private List<ExceptionInfo> exceptionInfoList;
    private List<PendingImplicitException> pendingImplicitExceptionList;

    private final OptionValues options;
    private final DebugContext debug;
    private final EconomicMap<Constant, Data> dataCache;

    /**
     * These position maps are used for estimating offsets of forward branches. Used for
     * architectures where certain branch instructions have limited displacement such as ARM tbz.
     */
    private EconomicMap<Label, Integer> labelBindLirPositions;
    private EconomicMap<LIRInstruction, Integer> lirPositions;
    /**
     * This flag is for setting the
     * {@link CompilationResultBuilder#labelWithinLIRRange(LIRInstruction, Label, int)} into a
     * conservative mode and always answering false.
     */
    private boolean conservativeLabelOffsets = false;

    public final boolean mustReplaceWithUncompressedNullRegister(JavaConstant nullConstant) {
        return !uncompressedNullRegister.equals(Register.None) && JavaConstant.NULL_POINTER.equals(nullConstant);
    }

    /**
     * This flag indicates whether the assembler should emit a separate deoptimization handler for
     * method handle invocations.
     */
    private boolean needsMHDeoptHandler = false;

    /** PCOffset passed within last call to {@link #recordImplicitException(int, LIRFrameState)}. */
    private int lastImplicitExceptionOffset = Integer.MIN_VALUE;

    private final List<LIRInstructionVerifier> lirInstructionVerifiers;

    public CompilationResultBuilder(CoreProviders providers,
                    FrameMap frameMap,
                    Assembler<?> asm,
                    DataBuilder dataBuilder,
                    FrameContext frameContext,
                    OptionValues options,
                    DebugContext debug,
                    CompilationResult compilationResult,
                    Register uncompressedNullRegister,
                    List<LIRInstructionVerifier> lirInstructionVerifiers,
                    LIR lir) {
        super(providers);
        this.target = providers.getCodeCache().getTarget();
        this.frameMap = frameMap;
        this.asm = asm;
        this.dataBuilder = dataBuilder;
        this.lir = lir;
        this.compilationResult = compilationResult;
        this.uncompressedNullRegister = uncompressedNullRegister;
        this.frameContext = frameContext;
        this.options = options;
        this.debug = debug;
        assert frameContext != null;
        this.dataCache = EconomicMap.create(Equivalence.DEFAULT);
        this.lirInstructionVerifiers = Objects.requireNonNull(lirInstructionVerifiers);
    }

    public void setTotalFrameSize(int frameSize) {
        compilationResult.setTotalFrameSize(frameSize);
    }

    public void setMaxInterpreterFrameSize(int maxInterpreterFrameSize) {
        compilationResult.setMaxInterpreterFrameSize(maxInterpreterFrameSize);
    }

    /**
     * Sets the minimum alignment for an item in the {@linkplain DataSectionReference data section}.
     */
    public void setMinDataSectionItemAlignment(int alignment) {
        compilationResult.setMinDataSectionItemAlignment(alignment);
    }

    /**
     * Associates {@code markId} with position {@code codePos} in the compilation result.
     *
     * @return the recorded entry for the mark
     */
    public CompilationResult.CodeMark recordMark(int codePos, CompilationResult.MarkId markId) {
        return compilationResult.recordMark(codePos, markId);
    }

    /**
     * Associates {@code markId} with the current assembler position in the compilation result.
     *
     * @return the recorded entry for the mark
     */
    public CompilationResult.CodeMark recordMark(CompilationResult.MarkId markId) {
        return recordMark(asm.position(), markId);
    }

    public void blockComment(String s) {
        compilationResult.addAnnotation(new CompilationResult.CodeComment(asm.position(), s));
    }

    /**
     * Sets the {@linkplain CompilationResult#setTargetCode(byte[], int) code} and
     * {@linkplain CompilationResult#recordExceptionHandler(int, int) exception handler} fields of
     * the compilation result and then {@linkplain #closeCompilationResult() closes} it.
     */
    public void finish() {
        int position = asm.position();
        compilationResult.setTargetCode(asm.close(false), position);

        // Record exception handlers if they exist
        if (exceptionInfoList != null) {
            for (ExceptionInfo ei : exceptionInfoList) {
                int codeOffset = ei.codeOffset;
                compilationResult.recordExceptionHandler(codeOffset, ei.exceptionEdge.label().position());
            }
        }
        closeCompilationResult();
    }

    /**
     * Calls {@link CompilationResult#close(OptionValues)} on {@link #compilationResult}.
     */
    protected void closeCompilationResult() {
        compilationResult.close(options);
    }

    public void recordExceptionHandlers(int pcOffset, LIRFrameState info) {
        if (info != null) {
            if (info.exceptionEdge != null) {
                if (exceptionInfoList == null) {
                    exceptionInfoList = new ArrayList<>(4);
                }
                exceptionInfoList.add(new ExceptionInfo(pcOffset, info.exceptionEdge));
            }
        }
    }

    public void recordImplicitException(int pcOffset, LIRFrameState info) {
        lastImplicitExceptionOffset = pcOffset;
        if (info instanceof ImplicitLIRFrameState) {
            if (pendingImplicitExceptionList == null) {
                pendingImplicitExceptionList = new ArrayList<>(4);
            }
            pendingImplicitExceptionList.add(new PendingImplicitException(pcOffset, (ImplicitLIRFrameState) info));
        } else {
            recordImplicitException(pcOffset, pcOffset, info);
        }
    }

    public void recordImplicitException(int pcOffset, int dispatchOffset, LIRFrameState info) {
        compilationResult.recordImplicitException(pcOffset, dispatchOffset, info.debugInfo());
        assert info.exceptionEdge == null;
    }

    public int getLastImplicitExceptionOffset() {
        return lastImplicitExceptionOffset;
    }

    /**
     * Helper to mark invalid deoptimization state as needed.
     */
    private void recordIfCallInvalidForDeoptimization(LIRFrameState info, Call call) {
        if (info != null && !info.validForDeoptimization && info.hasDebugInfo()) {
            DebugInfo debugInfo = info.debugInfo();
            assert debugInfo != null;
            if (debugInfo.hasFrame()) {
                compilationResult.recordCallInvalidForDeoptimization(call);
            }
        }
    }

    public Call recordDirectCall(int posBefore, int posAfter, InvokeTarget callTarget, LIRFrameState info) {
        DebugInfo debugInfo = info != null ? info.debugInfo() : null;
        Call call = compilationResult.recordCall(posBefore, posAfter - posBefore, callTarget, debugInfo, true);
        recordIfCallInvalidForDeoptimization(info, call);
        return call;
    }

    public Call recordIndirectCall(int posBefore, int posAfter, InvokeTarget callTarget, LIRFrameState info) {
        DebugInfo debugInfo = info != null ? info.debugInfo() : null;
        Call infopoint = compilationResult.recordCall(posBefore, posAfter - posBefore, callTarget, debugInfo, false);
        recordIfCallInvalidForDeoptimization(info, infopoint);
        return infopoint;
    }

    public void recordInfopoint(int pos, LIRFrameState info, InfopointReason reason) {
        // infopoints always need debug info
        DebugInfo debugInfo = info.debugInfo();
        recordInfopoint(pos, debugInfo, reason);
    }

    public void recordInfopoint(int pos, DebugInfo debugInfo, InfopointReason reason) {
        compilationResult.recordInfopoint(pos, debugInfo, reason);
    }

    public void recordSourceMapping(int pcOffset, int endPcOffset, NodeSourcePosition sourcePosition) {
        compilationResult.recordSourceMapping(pcOffset, endPcOffset, sourcePosition);
    }

    public void recordInlineDataInCode(Constant data) {
        assert data != null;
        int pos = asm.position();
        debug.log("Inline data in code: pos = %d, data = %s", pos, data);
        if (data instanceof VMConstant) {
            compilationResult.recordDataPatch(pos, new ConstantReference((VMConstant) data));
        }
    }

    public AbstractAddress recordDataSectionReference(Data data) {
        assert data != null;
        DataSectionReference reference = compilationResult.getDataSection().insertData(data);
        int instructionStart = asm.position();
        compilationResult.recordDataPatch(instructionStart, reference);
        return asm.getPlaceholder(instructionStart);
    }

    public AbstractAddress recordDataReferenceInCode(DataPointerConstant constant) {
        return recordDataReferenceInCode(constant, constant.getAlignment());
    }

    public AbstractAddress recordDataReferenceInCode(Constant constant, int alignment) {
        assert constant != null;
        debug.log("Constant reference in code: pos = %d, data = %s", asm.position(), constant);
        Data data = createDataItem(constant);
        dataBuilder.updateAlignment(data, alignment);
        return recordDataSectionReference(data);
    }

    public AbstractAddress recordDataReferenceInCode(Data data, int alignment) {
        assert data != null;
        dataBuilder.updateAlignment(data, alignment);
        return recordDataSectionReference(data);
    }

    /**
     * Creates an entry in the data section for the given constant.
     *
     * During one compilation if this is called multiple times for the same constant (as determined
     * by {@link Object#equals(Object)}), the same data entry will be returned for every call.
     */
    public Data createDataItem(Constant constant) {
        Data data = dataCache.get(constant);
        if (data == null) {
            data = dataBuilder.createDataItem(constant);
            dataCache.put(constant, data);
        }
        return data;
    }

    public AbstractAddress recordDataReferenceInCode(byte[] data, int alignment) {
        assert data != null;
        if (debug.isLogEnabled()) {
            debug.log("Data reference in code: pos = %d, data = %s", asm.position(), Arrays.toString(data));
        }
        ArrayDataPointerConstant arrayConstant = new ArrayDataPointerConstant(data, alignment);
        return recordDataSectionReference(dataBuilder.createSerializableData(arrayConstant, alignment));
    }

    /**
     * Returns the address of a float constant that is embedded as a data reference into the code.
     */
    public AbstractAddress asFloatConstRef(JavaConstant value) {
        return asFloatConstRef(value, 4);
    }

    public AbstractAddress asFloatConstRef(JavaConstant value, int alignment) {
        assert value.getJavaKind() == JavaKind.Float : value;
        return recordDataReferenceInCode(value, alignment);
    }

    /**
     * Returns the address of a double constant that is embedded as a data reference into the code.
     */
    public AbstractAddress asDoubleConstRef(JavaConstant value) {
        return asDoubleConstRef(value, 8);
    }

    public AbstractAddress asDoubleConstRef(JavaConstant value, int alignment) {
        assert value.getJavaKind() == JavaKind.Double : value;
        return recordDataReferenceInCode(value, alignment);
    }

    /**
     * Returns the address of a long constant that is embedded as a data reference into the code.
     */
    public AbstractAddress asLongConstRef(JavaConstant value) {
        assert value.getJavaKind() == JavaKind.Long : value;
        return recordDataReferenceInCode(value, 8);
    }

    public AbstractAddress asAddress(Value value) {
        assert isStackSlot(value);
        StackSlot slot = asStackSlot(value);
        int size = slot.getPlatformKind().getSizeInBytes() * Byte.SIZE;
        return asm.makeAddress(size, frameMap.getRegisterConfig().getFrameRegister(), frameMap.offsetForStackSlot(slot));
    }

    /**
     * Determines if a given edge from the block currently being emitted goes to its lexical
     * successor.
     */
    public boolean isSuccessorEdge(LabelRef edge) {
        assert lir != null;
        int[] order = lir.codeEmittingOrder();
        assert order[currentBlockIndex] == edge.getSourceBlock().getId() : Assertions.errorMessage(order[currentBlockIndex], edge, edge.getSourceBlock());
        BasicBlock<?> nextBlock = LIR.getNextBlock(lir.getControlFlowGraph(), order, currentBlockIndex);
        return nextBlock == edge.getTargetBlock();
    }

    private class BasicBlockInfoLogger {
        final boolean isEnable;
        final Formatter formatter;

        BasicBlockInfoLogger() {
            this.isEnable = DebugOptions.PrintBBInfo.getValue(options) && debug.methodFilterMatchesCurrentMethod();
            this.formatter = this.isEnable ? new Formatter() : null;
        }

        void log(BasicBlock<?> b, int startPC, int endPC) {
            if (this.isEnable) {
                // Dump basic block information using the following csv format
                // BBid, BBStartingPC, BBEndingPC, BBfreq, [(succID, succProbability)*]
                this.formatter.format("%d, %d, %d, %f, [", b.getId(), startPC, endPC, b.getRelativeFrequency());
                for (int i = 0; i < b.getSuccessorCount(); ++i) {
                    if (i < b.getSuccessorCount() - 1) {
                        this.formatter.format("(%d, %f),", b.getSuccessorAt(i).getId(), b.getSuccessorProbabilityAt(i));
                    } else {
                        this.formatter.format("(%d, %f)", b.getSuccessorAt(i).getId(), b.getSuccessorProbabilityAt(i));
                    }
                }
                this.formatter.format("]\n");
            }
        }

        void close() {
            if (this.isEnable) {
                final String path = debug.getDumpPath(".blocks", false);
                try {
                    Files.writeString(Paths.get(path), this.formatter.toString());
                } catch (IOException e) {
                    throw debug.handle(e);
                }
            }
        }
    }

    /**
     * Emits code for {@code lir} in its {@linkplain LIR#codeEmittingOrder() code emitting order}.
     */
    public void emitLIR() {
        assert currentBlockIndex == 0 : currentBlockIndex;
        assert lastImplicitExceptionOffset == Integer.MIN_VALUE : lastImplicitExceptionOffset;
        this.currentBlockIndex = 0;
        this.lastImplicitExceptionOffset = Integer.MIN_VALUE;
        frameContext.enter(this);
        final BasicBlockInfoLogger logger = new BasicBlockInfoLogger();
        BasicBlock<?> previousBlock = null;
        for (int blockId : lir.codeEmittingOrder()) {
            BasicBlock<?> b = lir.getBlockById(blockId);
            assert (b == null && lir.codeEmittingOrder()[currentBlockIndex] == AbstractControlFlowGraph.INVALID_BLOCK_ID) ||
                            lir.codeEmittingOrder()[currentBlockIndex] == blockId : Assertions.errorMessageContext("b", b, "lir.codeOrder", lir.codeEmittingOrder(), "currentBlockIndex",
                                            currentBlockIndex, "blockId", blockId);
            if (b != null) {
                if (b.isAligned() && previousBlock != null) {
                    boolean hasSuccessorB = false;
                    for (int i = 0; i < previousBlock.getSuccessorCount(); i++) {
                        BasicBlock<?> succ = previousBlock.getSuccessorAt(i);
                        if (succ == b) {
                            hasSuccessorB = true;
                            break;
                        }
                    }
                    if (!hasSuccessorB) {
                        ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(b);
                        assert instructions.get(0) instanceof StandardOp.LabelOp : "first instruction must always be a label";
                        StandardOp.LabelOp label = (StandardOp.LabelOp) instructions.get(0);
                        label.setAlignment(IsolatedLoopHeaderAlignment.getValue(options));
                    }
                }
                int basicBlockStartingPC = asm.position();
                emitBlock(b);
                int basicBlockEndingPC = asm.position();
                logger.log(b, basicBlockStartingPC, basicBlockEndingPC);
                previousBlock = b;
            }
            currentBlockIndex++;
        }

        ArrayList<LIRInstruction.LIRInstructionSlowPath> slowPaths = lir.getSlowPaths();
        if (slowPaths != null) {
            for (LIRInstruction.LIRInstructionSlowPath slowPath : slowPaths) {
                try {
                    emitSlowPath(slowPath);
                } catch (GraalError e) {
                    if (slowPath.forOp() != null) {
                        throw e.addContext("lir instruction", "slow path for " + slowPath.forOp() + " " + slowPath);
                    } else {
                        throw e.addContext("lir instruction", "slow path " + slowPath);
                    }
                }
            }
        }

        logger.close();
        this.currentBlockIndex = 0;
        this.lastImplicitExceptionOffset = Integer.MIN_VALUE;
    }

    public LIR getLIR() {
        return lir;
    }

    private void emitBlock(BasicBlock<?> block) {
        if (block == null) {
            return;
        }
        boolean emitComment = debug.isDumpEnabled(DebugContext.BASIC_LEVEL) || Options.PrintLIRWithAssembly.getValue(getOptions());
        if (emitComment) {
            blockComment(String.format("block B%d %s", block.getId(), block.getLoop()));
        }

        for (LIRInstruction op : lir.getLIRforBlock(block)) {
            if (emitComment) {
                blockComment(String.format("%d %s", op.id(), op));
            }

            try {
                emitOp(op);
            } catch (GraalError e) {
                throw e.addContext("lir instruction", block + "@" + op.id() + " " + op.getClass().getName() + " " + op);
            }
        }
    }

    private void emitSlowPath(LIRInstruction.LIRInstructionSlowPath op) {
        try {
            op.emitSlowPathCode();
            // Ensure the slow path doesn't fall through
            asm.halt();
        } catch (BailoutException e) {
            throw e;
        } catch (AssertionError t) {
            throw new GraalError(t);
        } catch (RuntimeException t) {
            throw new GraalError(t);
        }
    }

    private void emitOp(LIRInstruction op) {
        try {
            int start = asm.position();
            op.emitCode(this);
            if (op.getPosition() != null) {
                recordSourceMapping(start, asm.position(), op.getPosition());
            }
            if (!lirInstructionVerifiers.isEmpty() && start < asm.position()) {
                int end = asm.position();
                for (CodeAnnotation codeAnnotation : compilationResult.getCodeAnnotations()) {
                    if (codeAnnotation instanceof JumpTable) {
                        // Skip jump table. Here we assume the jump table is at the tail of the
                        // emitted code.
                        int jumpTableStart = codeAnnotation.getPosition();
                        if (jumpTableStart >= start && jumpTableStart < end) {
                            end = jumpTableStart;
                        }
                    }
                }
                byte[] emittedCode = asm.copy(start, end);
                lirInstructionVerifiers.forEach(v -> v.verify(op, emittedCode));
            }
        } catch (BailoutException e) {
            throw e;
        } catch (AssertionError t) {
            throw new GraalError(t);
        } catch (RuntimeException t) {
            throw new GraalError(t);
        }
    }

    public void resetForEmittingCode() {
        asm.reset();
        compilationResult.resetForEmittingCode();
        if (exceptionInfoList != null) {
            exceptionInfoList.clear();
        }
        if (pendingImplicitExceptionList != null) {
            pendingImplicitExceptionList.clear();
        }
        if (dataCache != null) {
            dataCache.clear();
        }
        currentBlockIndex = 0;
        lastImplicitExceptionOffset = Integer.MIN_VALUE;
        lir.resetLabels();
        needsMHDeoptHandler = false;
        conservativeLabelOffsets = false;
        if (labelBindLirPositions != null) {
            labelBindLirPositions.clear();
        }
        if (lirPositions != null) {
            lirPositions.clear();
        }
    }

    public OptionValues getOptions() {
        return options;
    }

    /**
     * Builds up a map for label and LIR instruction positions where labels are or labels pointing
     * to.
     */
    public void buildLabelOffsets() {
        labelBindLirPositions = EconomicMap.create(Equivalence.IDENTITY);
        lirPositions = EconomicMap.create(Equivalence.IDENTITY);
        int instructionPosition = 0;
        for (int blockId : lir.getBlocks()) {
            if (LIR.isBlockDeleted(blockId)) {
                continue;
            }
            BasicBlock<?> block = lir.getBlockById(blockId);
            for (LIRInstruction op : lir.getLIRforBlock(block)) {
                if (op instanceof LabelHoldingOp) {
                    Label label = ((LabelHoldingOp) op).getLabel();
                    if (label != null) {
                        labelBindLirPositions.put(label, instructionPosition);
                    }
                }
                lirPositions.put(op, instructionPosition);
                instructionPosition++;
            }
        }
    }

    /**
     * Determines whether the distance from the LIR instruction to the label is within
     * maxLIRDistance LIR instructions.
     *
     * @param maxLIRDistance Maximum number of LIR instructions between label and instruction
     */
    public boolean labelWithinLIRRange(LIRInstruction instruction, Label label, int maxLIRDistance) {
        if (conservativeLabelOffsets) {
            /* Conservatively assume distance is too far. */
            return false;
        }
        Integer labelPosition = labelBindLirPositions.get(label);
        Integer instructionPosition = lirPositions.get(instruction);
        if (labelPosition != null && instructionPosition != null) {
            /* If both LIR positions are known, then check distance between instructions. */
            return Math.abs(labelPosition - instructionPosition) < maxLIRDistance;
        } else {
            /* Otherwise, it is not possible to make an estimation. */
            return false;
        }
    }

    /**
     * Sets this CompilationResultBuilder into conservative mode. If set,
     * {@link CompilationResultBuilder#labelWithinLIRRange(LIRInstruction, Label, int)} always
     * returns false.
     */
    public void setConservativeLabelRanges() {
        this.conservativeLabelOffsets = true;
    }

    /**
     * Query, whether this {@link CompilationResultBuilder} uses conservative label ranges. This
     * allows for larger jump distances at the cost of increased code size.
     */
    public boolean usesConservativeLabelRanges() {
        return this.conservativeLabelOffsets;
    }

    public final boolean needsClearUpperVectorRegisters() {
        for (int blockId : lir.getBlocks()) {
            if (LIR.isBlockDeleted(blockId)) {
                continue;
            }
            BasicBlock<?> block = lir.getBlockById(blockId);
            for (LIRInstruction op : lir.getLIRforBlock(block)) {
                if (op.needsClearUpperVectorRegisters()) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setNeedsMHDeoptHandler() {
        this.needsMHDeoptHandler = true;
    }

    public boolean needsMHDeoptHandler() {
        return needsMHDeoptHandler;
    }

    public List<PendingImplicitException> getPendingImplicitExceptionList() {
        return pendingImplicitExceptionList;
    }
}
