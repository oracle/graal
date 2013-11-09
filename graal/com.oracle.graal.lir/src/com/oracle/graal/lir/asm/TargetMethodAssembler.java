/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.api.code.CompilationResult.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.nodes.*;

public class TargetMethodAssembler {

    private static class ExceptionInfo {

        public final int codeOffset;
        public final LabelRef exceptionEdge;

        public ExceptionInfo(int pcOffset, LabelRef exceptionEdge) {
            this.codeOffset = pcOffset;
            this.exceptionEdge = exceptionEdge;
        }
    }

    public final AbstractAssembler asm;
    public final CompilationResult compilationResult;
    public final TargetDescription target;
    public final CodeCacheProvider codeCache;
    public final ForeignCallsProvider foreignCalls;
    public final FrameMap frameMap;

    /**
     * The object that emits code for managing a method's frame. If null, no frame is used by the
     * method.
     */
    public final FrameContext frameContext;

    private List<ExceptionInfo> exceptionInfoList;

    public TargetMethodAssembler(CodeCacheProvider codeCache, ForeignCallsProvider foreignCalls, FrameMap frameMap, AbstractAssembler asm, FrameContext frameContext,
                    CompilationResult compilationResult) {
        this.target = codeCache.getTarget();
        this.codeCache = codeCache;
        this.foreignCalls = foreignCalls;
        this.frameMap = frameMap;
        this.asm = asm;
        this.compilationResult = compilationResult;
        this.frameContext = frameContext;
    }

    public void setFrameSize(int frameSize) {
        compilationResult.setFrameSize(frameSize);
    }

    private static final CompilationResult.Mark[] NO_REFS = {};

    public CompilationResult.Mark recordMark(Object id) {
        return compilationResult.recordMark(asm.codeBuffer.position(), id, NO_REFS);
    }

    public CompilationResult.Mark recordMark(Object id, CompilationResult.Mark... references) {
        return compilationResult.recordMark(asm.codeBuffer.position(), id, references);
    }

    public void blockComment(String s) {
        compilationResult.addAnnotation(new CompilationResult.CodeComment(asm.codeBuffer.position(), s));
    }

    public CompilationResult finishTargetMethod(StructuredGraph graph) {
        // Install code, data and frame size
        compilationResult.setTargetCode(asm.codeBuffer.close(false), asm.codeBuffer.position());

        // Record exception handlers if they exist
        if (exceptionInfoList != null) {
            for (ExceptionInfo ei : exceptionInfoList) {
                int codeOffset = ei.codeOffset;
                compilationResult.recordExceptionHandler(codeOffset, ei.exceptionEdge.label().position());
            }
        }

        if (Debug.isMeterEnabled()) {
            List<DataPatch> ldp = compilationResult.getDataReferences();
            DebugMetric[] dms = new DebugMetric[Kind.values().length];
            for (int i = 0; i < dms.length; i++) {
                dms[i] = Debug.metric("DataPatches-" + Kind.values()[i].toString());
            }
            DebugMetric dmRaw = Debug.metric("DataPatches-raw");

            for (DataPatch dp : ldp) {
                if (dp.constant != null) {
                    dms[dp.constant.getKind().ordinal()].add(1);
                } else {
                    dmRaw.add(1);
                }
            }

            Debug.metric("TargetMethods").increment();
            Debug.metric("CodeBytesEmitted").add(compilationResult.getTargetCodeSize());
            Debug.metric("InfopointsEmitted").add(compilationResult.getInfopoints().size());
            Debug.metric("DataPatches").add(ldp.size());
            Debug.metric("ExceptionHandlersEmitted").add(compilationResult.getExceptionHandlers().size());
        }
        Debug.log("Finished compiling %s", graph);
        return compilationResult;
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
        // record an implicit exception point
        if (info != null) {
            compilationResult.recordInfopoint(pcOffset, info.debugInfo(), InfopointReason.IMPLICIT_EXCEPTION);
            assert info.exceptionEdge == null;
        }
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

    public AbstractAddress recordDataReferenceInCode(Constant data, int alignment, boolean inlined) {
        assert data != null;
        int pos = asm.codeBuffer.position();
        Debug.log("Data reference in code: pos = %d, data = %s", pos, data.toString());
        compilationResult.recordDataReference(pos, data, alignment, inlined);
        return asm.getPlaceholder();
    }

    public AbstractAddress recordDataReferenceInCode(byte[] data, int alignment) {
        assert data != null;
        int pos = asm.codeBuffer.position();
        Debug.log("Raw data reference in code: pos = %d, data = %s", pos, data.toString());
        compilationResult.recordDataReference(pos, data, alignment);
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
        return recordDataReferenceInCode((Constant) value, alignment, false);
    }

    /**
     * Returns the address of a double constant that is embedded as a data reference into the code.
     */
    public AbstractAddress asDoubleConstRef(Value value) {
        return asDoubleConstRef(value, 8);
    }

    public AbstractAddress asDoubleConstRef(Value value, int alignment) {
        assert value.getKind() == Kind.Double && isConstant(value);
        return recordDataReferenceInCode((Constant) value, alignment, false);
    }

    /**
     * Returns the address of a long constant that is embedded as a data reference into the code.
     */
    public AbstractAddress asLongConstRef(Value value) {
        assert value.getKind() == Kind.Long && isConstant(value);
        return recordDataReferenceInCode((Constant) value, 8, false);
    }

    /**
     * Returns the address of an object constant that is embedded as a data reference into the code.
     */
    public AbstractAddress asObjectConstRef(Value value) {
        assert value.getKind() == Kind.Object && isConstant(value);
        return recordDataReferenceInCode((Constant) value, 8, false);
    }

    public AbstractAddress asIntAddr(Value value) {
        assert value.getKind() == Kind.Int;
        return asAddress(value);
    }

    public AbstractAddress asLongAddr(Value value) {
        assert value.getKind() == Kind.Long;
        return asAddress(value);
    }

    public AbstractAddress asObjectAddr(Value value) {
        assert value.getKind() == Kind.Object;
        return asAddress(value);
    }

    public AbstractAddress asFloatAddr(Value value) {
        assert value.getKind() == Kind.Float;
        return asAddress(value);
    }

    public AbstractAddress asDoubleAddr(Value value) {
        assert value.getKind() == Kind.Double;
        return asAddress(value);
    }

    public AbstractAddress asAddress(Value value) {
        assert isStackSlot(value);
        StackSlot slot = asStackSlot(value);
        return asm.makeAddress(frameMap.registerConfig.getFrameRegister(), frameMap.offsetForStackSlot(slot));
    }
}
