/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.api.code.ValueUtil.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CompilationResult.Data;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.cfg.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;

/**
 * Fills in a {@link CompilationResult} as its code is being assembled.
 *
 * @see CompilationResultBuilderFactory
 */
public class CompilationResultBuilder {

    private static class ExceptionInfo {

        public final int codeOffset;
        public final LabelRef exceptionEdge;

        public ExceptionInfo(int pcOffset, LabelRef exceptionEdge) {
            this.codeOffset = pcOffset;
            this.exceptionEdge = exceptionEdge;
        }
    }

    public final Assembler asm;
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

    public CompilationResultBuilder(CodeCacheProvider codeCache, ForeignCallsProvider foreignCalls, FrameMap frameMap, Assembler asm, FrameContext frameContext, CompilationResult compilationResult) {
        this.target = codeCache.getTarget();
        this.codeCache = codeCache;
        this.foreignCalls = foreignCalls;
        this.frameMap = frameMap;
        this.asm = asm;
        this.compilationResult = compilationResult;
        this.frameContext = frameContext;
        assert frameContext != null;
    }

    public void setTotalFrameSize(int frameSize) {
        compilationResult.setTotalFrameSize(frameSize);
    }

    private static final CompilationResult.Mark[] NO_REFS = {};

    public CompilationResult.Mark recordMark(Object id) {
        return compilationResult.recordMark(asm.position(), id, NO_REFS);
    }

    public CompilationResult.Mark recordMark(Object id, CompilationResult.Mark... references) {
        return compilationResult.recordMark(asm.position(), id, references);
    }

    public void blockComment(String s) {
        compilationResult.addAnnotation(new CompilationResult.CodeComment(asm.position(), s));
    }

    /**
     * Sets the {@linkplain CompilationResult#setTargetCode(byte[], int) code} and
     * {@linkplain CompilationResult#recordExceptionHandler(int, int) exception handler} fields of
     * the compilation result.
     */
    public void finish() {
        compilationResult.setTargetCode(asm.close(false), asm.position());

        // Record exception handlers if they exist
        if (exceptionInfoList != null) {
            for (ExceptionInfo ei : exceptionInfoList) {
                int codeOffset = ei.codeOffset;
                compilationResult.recordExceptionHandler(codeOffset, ei.exceptionEdge.label().position());
            }
        }
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
        compilationResult.recordInfopoint(pos, debugInfo, reason);
    }

    public void recordInlineDataInCode(Constant data) {
        recordInlineDataInCode(codeCache.createDataItem(data, 0));
    }

    public void recordInlineDataInCode(Data data) {
        assert data != null;
        int pos = asm.position();
        Debug.log("Inline data in code: pos = %d, data = %s", pos, data);
        compilationResult.recordInlineData(pos, data);
    }

    public AbstractAddress recordDataReferenceInCode(Constant data, int alignment) {
        assert data != null;
        return recordDataReferenceInCode(codeCache.createDataItem(data, alignment));
    }

    public AbstractAddress recordDataReferenceInCode(Data data) {
        assert data != null;
        int pos = asm.position();
        Debug.log("Data reference in code: pos = %d, data = %s", pos, data);
        compilationResult.recordDataReference(pos, data);
        return asm.getPlaceholder();
    }

    /**
     * Returns the integer value of any constant that can be represented by a 32-bit integer value,
     * including long constants that fit into the 32-bit range.
     */
    public int asIntConst(Value value) {
        assert (value.getKind().isNumericInteger()) && isConstant(value);
        Constant constant = (Constant) value;
        assert !codeCache.needsDataPatch(constant) : constant + " should be in a DataPatch";
        long c = constant.asLong();
        if (!NumUtil.isInt(c)) {
            throw GraalInternalError.shouldNotReachHere();
        }
        return (int) c;
    }

    /**
     * Returns the float value of any constant that can be represented by a 32-bit float value.
     */
    public float asFloatConst(Value value) {
        assert (value.getKind().getStackKind() == Kind.Float && isConstant(value));
        Constant constant = (Constant) value;
        assert !codeCache.needsDataPatch(constant) : constant + " should be in a DataPatch";
        return constant.asFloat();
    }

    /**
     * Returns the long value of any constant that can be represented by a 64-bit long value.
     */
    public long asLongConst(Value value) {
        assert (value.getKind().getStackKind() == Kind.Long && isConstant(value));
        Constant constant = (Constant) value;
        assert !codeCache.needsDataPatch(constant) : constant + " should be in a DataPatch";
        return constant.asLong();
    }

    /**
     * Returns the double value of any constant that can be represented by a 64-bit float value.
     */
    public double asDoubleConst(Value value) {
        assert (value.getKind().getStackKind() == Kind.Double && isConstant(value));
        Constant constant = (Constant) value;
        assert !codeCache.needsDataPatch(constant) : constant + " should be in a DataPatch";
        return constant.asDouble();
    }

    /**
     * Returns the address of a float constant that is embedded as a data reference into the code.
     */
    public AbstractAddress asFloatConstRef(Value value) {
        return asFloatConstRef(value, 4);
    }

    public AbstractAddress asFloatConstRef(Value value, int alignment) {
        assert value.getKind() == Kind.Float && isConstant(value);
        return recordDataReferenceInCode((Constant) value, alignment);
    }

    /**
     * Returns the address of a double constant that is embedded as a data reference into the code.
     */
    public AbstractAddress asDoubleConstRef(Value value) {
        return asDoubleConstRef(value, 8);
    }

    public AbstractAddress asDoubleConstRef(Value value, int alignment) {
        assert value.getKind() == Kind.Double && isConstant(value);
        return recordDataReferenceInCode((Constant) value, alignment);
    }

    /**
     * Returns the address of a long constant that is embedded as a data reference into the code.
     */
    public AbstractAddress asLongConstRef(Value value) {
        assert value.getKind() == Kind.Long && isConstant(value);
        return recordDataReferenceInCode((Constant) value, 8);
    }

    /**
     * Returns the address of an object constant that is embedded as a data reference into the code.
     */
    public AbstractAddress asObjectConstRef(Value value) {
        assert value.getKind() == Kind.Object && isConstant(value);
        return recordDataReferenceInCode((Constant) value, 8);
    }

    public AbstractAddress asByteAddr(Value value) {
        assert value.getKind().getByteCount() >= Kind.Byte.getByteCount();
        return asAddress(value);
    }

    public AbstractAddress asShortAddr(Value value) {
        assert value.getKind().getByteCount() >= Kind.Short.getByteCount();
        return asAddress(value);
    }

    public AbstractAddress asIntAddr(Value value) {
        assert value.getKind().getByteCount() >= Kind.Int.getByteCount();
        return asAddress(value);
    }

    public AbstractAddress asLongAddr(Value value) {
        assert value.getKind().getByteCount() >= Kind.Long.getByteCount();
        return asAddress(value);
    }

    public AbstractAddress asObjectAddr(Value value) {
        assert value.getKind() == Kind.Object;
        return asAddress(value);
    }

    public AbstractAddress asFloatAddr(Value value) {
        assert value.getKind().getByteCount() >= Kind.Float.getByteCount();
        return asAddress(value);
    }

    public AbstractAddress asDoubleAddr(Value value) {
        assert value.getKind().getByteCount() >= Kind.Double.getByteCount();
        return asAddress(value);
    }

    public AbstractAddress asAddress(Value value) {
        assert isStackSlot(value);
        StackSlot slot = asStackSlot(value);
        return asm.makeAddress(frameMap.registerConfig.getFrameRegister(), frameMap.offsetForStackSlot(slot));
    }

    /**
     * Determines if a given edge from the block currently being emitted goes to its lexical
     * successor.
     */
    public boolean isSuccessorEdge(LabelRef edge) {
        assert lir != null;
        List<? extends AbstractBlock<?>> order = lir.codeEmittingOrder();
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
        for (AbstractBlock<?> b : lir.codeEmittingOrder()) {
            emitBlock(b);
            currentBlockIndex++;
        }
        this.lir = null;
        this.currentBlockIndex = 0;
    }

    private void emitBlock(AbstractBlock<?> block) {
        if (Debug.isDumpEnabled()) {
            blockComment(String.format("block B%d %s", block.getId(), block.getLoop()));
        }

        for (LIRInstruction op : lir.getLIRforBlock(block)) {
            if (Debug.isDumpEnabled()) {
                blockComment(String.format("%d %s", op.id(), op));
            }

            try {
                emitOp(this, op);
            } catch (GraalInternalError e) {
                throw e.addContext("lir instruction", block + "@" + op.id() + " " + op + "\n" + lir.codeEmittingOrder());
            }
        }
    }

    private static void emitOp(CompilationResultBuilder crb, LIRInstruction op) {
        try {
            op.emitCode(crb);
        } catch (AssertionError t) {
            throw new GraalInternalError(t);
        } catch (RuntimeException t) {
            throw new GraalInternalError(t);
        }
    }
}
