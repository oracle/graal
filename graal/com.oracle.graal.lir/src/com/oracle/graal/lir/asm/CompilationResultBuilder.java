/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.lir.asm;

import static com.oracle.graal.lir.LIRValueUtil.asJavaConstant;
import static com.oracle.graal.lir.LIRValueUtil.isJavaConstant;
import static jdk.vm.ci.code.ValueUtil.asStackSlot;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.oracle.graal.asm.AbstractAddress;
import com.oracle.graal.asm.Assembler;
import com.oracle.graal.asm.NumUtil;
import com.oracle.graal.code.CompilationResult;
import com.oracle.graal.code.DataSection.Data;
import com.oracle.graal.code.DataSection.RawData;
import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.compiler.common.spi.ForeignCallsProvider;
import com.oracle.graal.compiler.common.type.DataPointerConstant;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.GraalError;
import com.oracle.graal.graph.NodeSourcePosition;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.LabelRef;
import com.oracle.graal.lir.framemap.FrameMap;
import com.oracle.graal.options.Option;
import com.oracle.graal.options.OptionType;
import com.oracle.graal.options.OptionValue;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.DebugInfo;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.DataSectionReference;
import jdk.vm.ci.code.site.InfopointReason;
import jdk.vm.ci.code.site.Mark;
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

    // @formatter:off
    @Option(help = "Include the LIR as comments with the final assembly.", type = OptionType.Debug)
    public static final OptionValue<Boolean> PrintLIRWithAssembly = new OptionValue<>(false);
    // @formatter:on

    private static class ExceptionInfo {

        public final int codeOffset;
        public final LabelRef exceptionEdge;

        ExceptionInfo(int pcOffset, LabelRef exceptionEdge) {
            this.codeOffset = pcOffset;
            this.exceptionEdge = exceptionEdge;
        }
    }

    public final Assembler asm;
    public final DataBuilder dataBuilder;
    public final CompilationResult compilationResult;
    public final TargetDescription target;
    public final CodeCacheProvider codeCache;
    public final ForeignCallsProvider foreignCalls;
    public final FrameMap frameMap;

    /**
     * The LIR for which code is being generated.
     */
    private LIR lir;

    /**
     * The index of the block currently being emitted.
     */
    private int currentBlockIndex;

    /**
     * The object that emits code for managing a method's frame.
     */
    public final FrameContext frameContext;

    private List<ExceptionInfo> exceptionInfoList;

    private final Map<Constant, Data> dataCache;

    private Consumer<LIRInstruction> beforeOp;
    private Consumer<LIRInstruction> afterOp;

    public CompilationResultBuilder(CodeCacheProvider codeCache, ForeignCallsProvider foreignCalls, FrameMap frameMap, Assembler asm, DataBuilder dataBuilder, FrameContext frameContext,
                    CompilationResult compilationResult) {
        // constants are already GVNed in the high level graph, so we can use an IdentityHashMap
        this(codeCache, foreignCalls, frameMap, asm, dataBuilder, frameContext, compilationResult, new IdentityHashMap<>());
    }

    public CompilationResultBuilder(CodeCacheProvider codeCache, ForeignCallsProvider foreignCalls, FrameMap frameMap, Assembler asm, DataBuilder dataBuilder, FrameContext frameContext,
                    CompilationResult compilationResult, Map<Constant, Data> dataCache) {
        this.target = codeCache.getTarget();
        this.codeCache = codeCache;
        this.foreignCalls = foreignCalls;
        this.frameMap = frameMap;
        this.asm = asm;
        this.dataBuilder = dataBuilder;
        this.compilationResult = compilationResult;
        this.frameContext = frameContext;
        assert frameContext != null;
        this.dataCache = dataCache;
    }

    public void setTotalFrameSize(int frameSize) {
        compilationResult.setTotalFrameSize(frameSize);
    }

    public void setMaxInterpreterFrameSize(int maxInterpreterFrameSize) {
        compilationResult.setMaxInterpreterFrameSize(maxInterpreterFrameSize);
    }

    public Mark recordMark(Object id) {
        return compilationResult.recordMark(asm.position(), id);
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
     * Calls {@link CompilationResult#close()} on {@link #compilationResult}.
     */
    protected void closeCompilationResult() {
        compilationResult.close();
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
        compilationResult.recordInfopoint(pcOffset, info.debugInfo(), InfopointReason.IMPLICIT_EXCEPTION);
        assert info.exceptionEdge == null;
    }

    public void recordDirectCall(int posBefore, int posAfter, InvokeTarget callTarget, LIRFrameState info) {
        DebugInfo debugInfo = info != null ? info.debugInfo() : null;
        compilationResult.recordCall(posBefore, posAfter - posBefore, callTarget, debugInfo, true);
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
        Debug.log("Inline data in code: pos = %d, data = %s", pos, data);
        if (data instanceof VMConstant) {
            compilationResult.recordDataPatch(pos, new ConstantReference((VMConstant) data));
        }
    }

    public AbstractAddress recordDataSectionReference(Data data) {
        assert data != null;
        DataSectionReference reference = compilationResult.getDataSection().insertData(data);
        compilationResult.recordDataPatch(asm.position(), reference);
        return asm.getPlaceholder();
    }

    public AbstractAddress recordDataReferenceInCode(DataPointerConstant constant) {
        return recordDataReferenceInCode(constant, constant.getAlignment());
    }

    public AbstractAddress recordDataReferenceInCode(Constant constant, int alignment) {
        assert constant != null;
        Debug.log("Constant reference in code: pos = %d, data = %s", asm.position(), constant);
        Data data = dataCache.get(constant);
        if (data == null) {
            data = dataBuilder.createDataItem(constant);
            dataCache.put(constant, data);
        }
        data.updateAlignment(alignment);
        return recordDataSectionReference(data);
    }

    public AbstractAddress recordDataReferenceInCode(byte[] data, int alignment) {
        assert data != null;
        if (Debug.isLogEnabled()) {
            Debug.log("Data reference in code: pos = %d, data = %s", asm.position(), Arrays.toString(data));
        }
        return recordDataSectionReference(new RawData(data, alignment));
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
        return asm.makeAddress(frameMap.getRegisterConfig().getFrameRegister(), frameMap.offsetForStackSlot(slot));
    }

    /**
     * Determines if a given edge from the block currently being emitted goes to its lexical
     * successor.
     */
    public boolean isSuccessorEdge(LabelRef edge) {
        assert lir != null;
        List<? extends AbstractBlockBase<?>> order = lir.codeEmittingOrder();
        assert order.get(currentBlockIndex) == edge.getSourceBlock();
        return currentBlockIndex < order.size() - 1 && order.get(currentBlockIndex + 1) == edge.getTargetBlock();
    }

    /**
     * Emits code for {@code lir} in its {@linkplain LIR#codeEmittingOrder() code emitting order}.
     */
    public void emit(@SuppressWarnings("hiding") LIR lir) {
        assert this.lir == null;
        assert currentBlockIndex == 0;
        this.lir = lir;
        this.currentBlockIndex = 0;
        frameContext.enter(this);
        for (AbstractBlockBase<?> b : lir.codeEmittingOrder()) {
            emitBlock(b);
            currentBlockIndex++;
        }
        this.lir = null;
        this.currentBlockIndex = 0;
    }

    private void emitBlock(AbstractBlockBase<?> block) {
        if (block == null) {
            return;
        }
        if (Debug.isDumpEnabled(Debug.BASIC_LOG_LEVEL) || PrintLIRWithAssembly.getValue()) {
            blockComment(String.format("block B%d %s", block.getId(), block.getLoop()));
        }

        for (LIRInstruction op : lir.getLIRforBlock(block)) {
            if (Debug.isDumpEnabled(Debug.BASIC_LOG_LEVEL) || PrintLIRWithAssembly.getValue()) {
                blockComment(String.format("%d %s", op.id(), op));
            }

            try {
                if (beforeOp != null) {
                    beforeOp.accept(op);
                }
                emitOp(this, op);
                if (afterOp != null) {
                    afterOp.accept(op);
                }
            } catch (GraalError e) {
                throw e.addContext("lir instruction", block + "@" + op.id() + " " + op + "\n" + lir.codeEmittingOrder());
            }
        }
    }

    private static void emitOp(CompilationResultBuilder crb, LIRInstruction op) {
        try {
            int start = crb.asm.position();
            op.emitCode(crb);
            if (op.getPosition() != null) {
                crb.recordSourceMapping(start, crb.asm.position(), op.getPosition());
            }
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
        if (dataCache != null) {
            dataCache.clear();
        }
    }

    public void setOpCallback(Consumer<LIRInstruction> beforeOp, Consumer<LIRInstruction> afterOp) {
        this.beforeOp = beforeOp;
        this.afterOp = afterOp;
    }
}
