/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.asm;

import static jdk.vm.ci.code.ValueUtil.asStackSlot;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;
import static org.graalvm.compiler.core.common.GraalOptions.IsolatedLoopHeaderAlignment;
import static org.graalvm.compiler.lir.LIRValueUtil.asJavaConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isJavaConstant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.asm.AbstractAddress;
import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.code.CompilationResult.CodeAnnotation;
import org.graalvm.compiler.code.CompilationResult.JumpTable;
import org.graalvm.compiler.code.DataSection.Data;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.common.spi.CodeGenProviders;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.type.DataPointerConstant;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.lir.ImplicitLIRFrameState;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstructionVerifier;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.StandardOp;
import org.graalvm.compiler.lir.StandardOp.LabelHoldingOp;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.serviceprovider.GraalServices;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.DebugInfo;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.DataSectionReference;
import jdk.vm.ci.code.site.Infopoint;
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
public class CompilationResultBuilder {

    private static final List<LIRInstructionVerifier> LIR_INSTRUCTION_VERIFIERS = new ArrayList<>();

    static {
        for (LIRInstructionVerifier verifier : GraalServices.load(LIRInstructionVerifier.class)) {
            if (verifier.isEnabled()) {
                LIR_INSTRUCTION_VERIFIERS.add(verifier);
            }
        }
    }

    public static class Options {
        @Option(help = "Include the LIR as comments with the final assembly.", type = OptionType.Debug) //
        public static final OptionKey<Boolean> PrintLIRWithAssembly = new OptionKey<>(false);
    }

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

    public final Assembler asm;
    public final DataBuilder dataBuilder;
    public final CompilationResult compilationResult;
    public final Register uncompressedNullRegister;
    public final TargetDescription target;
    public final CodeGenProviders providers;
    public final CodeCacheProvider codeCache;
    public final ForeignCallsProvider foreignCalls;
    public final FrameMap frameMap;

    /**
     * The LIR for which code is being generated.
     */
    protected LIR lir;

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

    public CompilationResultBuilder(CodeGenProviders providers,
                    FrameMap frameMap,
                    Assembler asm,
                    DataBuilder dataBuilder,
                    FrameContext frameContext,
                    OptionValues options,
                    DebugContext debug,
                    CompilationResult compilationResult,
                    Register uncompressedNullRegister) {
        this(providers,
                        frameMap,
                        asm,
                        dataBuilder,
                        frameContext,
                        options,
                        debug,
                        compilationResult,
                        uncompressedNullRegister,
                        EconomicMap.create(Equivalence.DEFAULT));
    }

    public CompilationResultBuilder(CodeGenProviders providers,
                    FrameMap frameMap,
                    Assembler asm,
                    DataBuilder dataBuilder,
                    FrameContext frameContext,
                    OptionValues options,
                    DebugContext debug,
                    CompilationResult compilationResult,
                    Register uncompressedNullRegister,
                    EconomicMap<Constant, Data> dataCache) {
        this.target = providers.getCodeCache().getTarget();
        this.providers = providers;
        this.codeCache = providers.getCodeCache();
        this.foreignCalls = providers.getForeignCalls();
        this.frameMap = frameMap;
        this.asm = asm;
        this.dataBuilder = dataBuilder;
        this.compilationResult = compilationResult;
        this.uncompressedNullRegister = uncompressedNullRegister;
        this.frameContext = frameContext;
        this.options = options;
        this.debug = debug;
        assert frameContext != null;
        this.dataCache = dataCache;
    }

    public void setTotalFrameSize(int frameSize) {
        compilationResult.setTotalFrameSize(frameSize);
    }

    public void setMaxInterpreterFrameSize(int maxInterpreterFrameSize) {
        compilationResult.setMaxInterpreterFrameSize(maxInterpreterFrameSize);
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
        if (GraalServices.supportsArbitraryImplicitException() && info instanceof ImplicitLIRFrameState) {
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

    public boolean hasImplicitException(int pcOffset) {
        List<Infopoint> infopoints = compilationResult.getInfopoints();
        for (Infopoint infopoint : infopoints) {
            if (infopoint.pcOffset == pcOffset && infopoint.reason == InfopointReason.IMPLICIT_EXCEPTION) {
                return true;
            }
        }
        if (pendingImplicitExceptionList != null) {
            for (PendingImplicitException pendingImplicitException : pendingImplicitExceptionList) {
                if (pendingImplicitException.codeOffset == pcOffset) {
                    return true;
                }
            }
        }
        return false;
    }

    public Call recordDirectCall(int posBefore, int posAfter, InvokeTarget callTarget, LIRFrameState info) {
        DebugInfo debugInfo = info != null ? info.debugInfo() : null;
        return compilationResult.recordCall(posBefore, posAfter - posBefore, callTarget, debugInfo, true);
    }

    public void recordIndirectCall(int posBefore, int posAfter, InvokeTarget callTarget, LIRFrameState info) {
        DebugInfo debugInfo = info != null ? info.debugInfo() : null;
        compilationResult.recordCall(posBefore, posAfter - posBefore, callTarget, debugInfo, false);
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

    public void recordInlineDataInCodeWithNote(Constant data, Object note) {
        assert data != null;
        int pos = asm.position();
        debug.log("Inline data in code: pos = %d, data = %s, note = %s", pos, data, note);
        if (data instanceof VMConstant) {
            compilationResult.recordDataPatchWithNote(pos, new ConstantReference((VMConstant) data), note);
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
     * Returns the integer value of any constant that can be represented by a 32-bit integer value,
     * including long constants that fit into the 32-bit range.
     */
    public int asIntConst(Value value) {
        assert isJavaConstant(value) && asJavaConstant(value).getJavaKind().isNumericInteger();
        JavaConstant constant = asJavaConstant(value);
        long c = constant.asLong();
        if (!NumUtil.isInt(c)) {
            throw GraalError.shouldNotReachHere();
        }
        return (int) c;
    }

    /**
     * Returns the float value of any constant that can be represented by a 32-bit float value.
     */
    public float asFloatConst(Value value) {
        assert isJavaConstant(value) && asJavaConstant(value).getJavaKind() == JavaKind.Float;
        JavaConstant constant = asJavaConstant(value);
        return constant.asFloat();
    }

    /**
     * Returns the long value of any constant that can be represented by a 64-bit long value.
     */
    public long asLongConst(Value value) {
        assert isJavaConstant(value) && asJavaConstant(value).getJavaKind() == JavaKind.Long;
        JavaConstant constant = asJavaConstant(value);
        return constant.asLong();
    }

    /**
     * Returns the double value of any constant that can be represented by a 64-bit float value.
     */
    public double asDoubleConst(Value value) {
        assert isJavaConstant(value) && asJavaConstant(value).getJavaKind() == JavaKind.Double;
        JavaConstant constant = asJavaConstant(value);
        return constant.asDouble();
    }

    /**
     * Returns the address of a float constant that is embedded as a data reference into the code.
     */
    public AbstractAddress asFloatConstRef(JavaConstant value) {
        return asFloatConstRef(value, 4);
    }

    public AbstractAddress asFloatConstRef(JavaConstant value, int alignment) {
        assert value.getJavaKind() == JavaKind.Float;
        return recordDataReferenceInCode(value, alignment);
    }

    /**
     * Returns the address of a double constant that is embedded as a data reference into the code.
     */
    public AbstractAddress asDoubleConstRef(JavaConstant value) {
        return asDoubleConstRef(value, 8);
    }

    public AbstractAddress asDoubleConstRef(JavaConstant value, int alignment) {
        assert value.getJavaKind() == JavaKind.Double;
        return recordDataReferenceInCode(value, alignment);
    }

    /**
     * Returns the address of a long constant that is embedded as a data reference into the code.
     */
    public AbstractAddress asLongConstRef(JavaConstant value) {
        assert value.getJavaKind() == JavaKind.Long;
        return recordDataReferenceInCode(value, 8);
    }

    /**
     * Returns the address of an object constant that is embedded as a data reference into the code.
     */
    public AbstractAddress asObjectConstRef(JavaConstant value) {
        assert value.getJavaKind() == JavaKind.Object;
        return recordDataReferenceInCode(value, 8);
    }

    public AbstractAddress asByteAddr(Value value) {
        assert value.getPlatformKind().getSizeInBytes() >= JavaKind.Byte.getByteCount();
        return asAddress(value);
    }

    public AbstractAddress asShortAddr(Value value) {
        assert value.getPlatformKind().getSizeInBytes() >= JavaKind.Short.getByteCount();
        return asAddress(value);
    }

    public AbstractAddress asIntAddr(Value value) {
        assert value.getPlatformKind().getSizeInBytes() >= JavaKind.Int.getByteCount();
        return asAddress(value);
    }

    public AbstractAddress asLongAddr(Value value) {
        assert value.getPlatformKind().getSizeInBytes() >= JavaKind.Long.getByteCount();
        return asAddress(value);
    }

    public AbstractAddress asFloatAddr(Value value) {
        assert value.getPlatformKind().getSizeInBytes() >= JavaKind.Float.getByteCount();
        return asAddress(value);
    }

    public AbstractAddress asDoubleAddr(Value value) {
        assert value.getPlatformKind().getSizeInBytes() >= JavaKind.Double.getByteCount();
        return asAddress(value);
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
        AbstractBlockBase<?>[] order = lir.codeEmittingOrder();
        assert order[currentBlockIndex] == edge.getSourceBlock();
        AbstractBlockBase<?> nextBlock = LIR.getNextBlock(order, currentBlockIndex);
        return nextBlock == edge.getTargetBlock();
    }

    /**
     * Emits code for {@code lir} in its {@linkplain LIR#codeEmittingOrder() code emitting order}.
     */
    public void emit(@SuppressWarnings("hiding") LIR lir) {
        assert this.lir == null;
        assert currentBlockIndex == 0;
        assert lastImplicitExceptionOffset == Integer.MIN_VALUE;
        this.lir = lir;
        this.currentBlockIndex = 0;
        this.lastImplicitExceptionOffset = Integer.MIN_VALUE;
        frameContext.enter(this);
        AbstractBlockBase<?> previousBlock = null;
        for (AbstractBlockBase<?> b : lir.codeEmittingOrder()) {
            assert (b == null && lir.codeEmittingOrder()[currentBlockIndex] == null) || lir.codeEmittingOrder()[currentBlockIndex].equals(b);
            if (b != null) {
                if (b.isAligned() && previousBlock != null && Arrays.stream(previousBlock.getSuccessors()).noneMatch((x) -> x == b)) {
                    ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(b);
                    assert instructions.get(0) instanceof StandardOp.LabelOp : "first instruction must always be a label";
                    StandardOp.LabelOp label = (StandardOp.LabelOp) instructions.get(0);
                    label.setAlignment(IsolatedLoopHeaderAlignment.getValue(options));
                }
                emitBlock(b);
                previousBlock = b;
            }
            currentBlockIndex++;
        }
        this.lir = null;
        this.currentBlockIndex = 0;
        this.lastImplicitExceptionOffset = Integer.MIN_VALUE;
    }

    public LIR getLIR() {
        assert lir != null;
        return lir;
    }

    private void emitBlock(AbstractBlockBase<?> block) {
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

    private void emitOp(LIRInstruction op) {
        try {
            int start = asm.position();
            op.emitCode(this);
            if (op.getPosition() != null) {
                recordSourceMapping(start, asm.position(), op.getPosition());
            }
            if (LIR_INSTRUCTION_VERIFIERS.size() > 0 && start < asm.position()) {
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
                for (LIRInstructionVerifier verifier : LIR_INSTRUCTION_VERIFIERS) {
                    verifier.verify(op, emittedCode);
                }
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
        lir = null;
        currentBlockIndex = 0;
        lastImplicitExceptionOffset = Integer.MIN_VALUE;
    }

    public OptionValues getOptions() {
        return options;
    }

    /**
     * Builds up a map for label and LIR instruction positions where labels are or labels pointing
     * to.
     */
    public void buildLabelOffsets(LIR generatedLIR) {
        labelBindLirPositions = EconomicMap.create(Equivalence.IDENTITY);
        lirPositions = EconomicMap.create(Equivalence.IDENTITY);
        int instructionPosition = 0;
        for (AbstractBlockBase<?> block : generatedLIR.codeEmittingOrder()) {
            if (block != null) {
                for (LIRInstruction op : generatedLIR.getLIRforBlock(block)) {
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

    public final boolean needsClearUpperVectorRegisters() {
        for (AbstractBlockBase<?> block : lir.codeEmittingOrder()) {
            if (block == null) {
                continue;
            }
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
