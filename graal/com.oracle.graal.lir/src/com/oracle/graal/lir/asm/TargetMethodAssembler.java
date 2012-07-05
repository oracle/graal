/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIR.Code;
import com.oracle.max.asm.*;

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
    public final CompilationResult targetMethod;
    public final TargetDescription target;
    public final CodeCacheProvider runtime;
    public final FrameMap frameMap;

    /**
     * Out-of-line stubs to be emitted.
     */
    public final List<Code> stubs;

    /**
     * The object that emits code for managing a method's frame.
     * If null, no frame is used by the method.
     */
    public final FrameContext frameContext;

    private List<ExceptionInfo> exceptionInfoList;
    private int lastSafepointPos;

    public TargetMethodAssembler(TargetDescription target, CodeCacheProvider runtime, FrameMap frameMap, AbstractAssembler asm, FrameContext frameContext, List<Code> stubs) {
        this.target = target;
        this.runtime = runtime;
        this.frameMap = frameMap;
        this.stubs = stubs;
        this.asm = asm;
        this.targetMethod = new CompilationResult();
        this.frameContext = frameContext;
        // 0 is a valid pc for safepoints in template methods
        this.lastSafepointPos = -1;
    }

    public void setFrameSize(int frameSize) {
        targetMethod.setFrameSize(frameSize);
    }

    public CompilationResult.Mark recordMark(Object id) {
        return targetMethod.recordMark(asm.codeBuffer.position(), id, null);
    }

    public CompilationResult.Mark recordMark(Object id, CompilationResult.Mark[] references) {
        return targetMethod.recordMark(asm.codeBuffer.position(), id, references);
    }

    public void blockComment(String s) {
        targetMethod.addAnnotation(new CompilationResult.CodeComment(asm.codeBuffer.position(), s));
    }

    public CompilationResult finishTargetMethod(Object name, boolean isStub) {
        // Install code, data and frame size
        targetMethod.setTargetCode(asm.codeBuffer.close(false), asm.codeBuffer.position());

        // Record exception handlers if they exist
        if (exceptionInfoList != null) {
            for (ExceptionInfo ei : exceptionInfoList) {
                int codeOffset = ei.codeOffset;
                targetMethod.recordExceptionHandler(codeOffset, ei.exceptionEdge.label().position());
            }
        }

        Debug.metric("TargetMethods").increment();
        Debug.metric("CodeBytesEmitted").add(targetMethod.targetCodeSize());
        Debug.metric("SafepointsEmitted").add(targetMethod.getSafepoints().size());
        Debug.metric("DataPatches").add(targetMethod.getDataReferences().size());
        Debug.metric("ExceptionHandlersEmitted").add(targetMethod.getExceptionHandlers().size());
        Debug.log("Finished target method %s, isStub %b", name, isStub);
        return targetMethod;
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
            assert lastSafepointPos < pcOffset : lastSafepointPos + "<" + pcOffset;
            lastSafepointPos = pcOffset;
            targetMethod.recordSafepoint(pcOffset, info.debugInfo());
            assert info.exceptionEdge == null;
        }
    }

    public void recordDirectCall(int posBefore, int posAfter, Object callTarget, LIRFrameState info) {
        DebugInfo debugInfo = info != null ? info.debugInfo() : null;
        assert lastSafepointPos < posAfter;
        lastSafepointPos = posAfter;
        targetMethod.recordCall(posBefore, posAfter - posBefore, callTarget, debugInfo, true);
    }

    public void recordIndirectCall(int posBefore, int posAfter, Object callTarget, LIRFrameState info) {
        DebugInfo debugInfo = info != null ? info.debugInfo() : null;
        assert lastSafepointPos < posAfter;
        lastSafepointPos = posAfter;
        targetMethod.recordCall(posBefore, posAfter - posBefore, callTarget, debugInfo, false);
    }

    public void recordSafepoint(int pos, LIRFrameState info) {
        // safepoints always need debug info
        DebugInfo debugInfo = info.debugInfo();
        assert lastSafepointPos < pos;
        lastSafepointPos = pos;
        targetMethod.recordSafepoint(pos, debugInfo);
    }

    public Address recordDataReferenceInCode(Constant data, int alignment) {
        assert data != null;
        int pos = asm.codeBuffer.position();
        Debug.log("Data reference in code: pos = %d, data = %s", pos, data.toString());
        targetMethod.recordDataReference(pos, data, alignment);
        return Address.Placeholder;
    }

    public int lastSafepointPos() {
        return lastSafepointPos;
    }


    /**
     * Returns the integer value of any constants that can be represented by a 32-bit integer value,
     * including long constants that fit into the 32-bit range.
     */
    public int asIntConst(Value value) {
        assert (value.kind.stackKind() == Kind.Int || value.kind == Kind.Jsr || value.kind == Kind.Long) && isConstant(value);
        long c = ((Constant) value).asLong();
        if (!(NumUtil.isInt(c))) {
            throw GraalInternalError.shouldNotReachHere();
        }
        return (int) c;
    }

    /**
     * Returns the address of a float constant that is embedded as a data references into the code.
     */
    public Address asFloatConstRef(Value value) {
        return asFloatConstRef(value, 4);
    }

    public Address asFloatConstRef(Value value, int alignment) {
        assert value.kind == Kind.Float && isConstant(value);
        return recordDataReferenceInCode((Constant) value, alignment);
    }

    /**
     * Returns the address of a double constant that is embedded as a data references into the code.
     */
    public Address asDoubleConstRef(Value value) {
        return asDoubleConstRef(value, 8);
    }

    public Address asDoubleConstRef(Value value, int alignment) {
        assert value.kind == Kind.Double && isConstant(value);
        return recordDataReferenceInCode((Constant) value, alignment);
    }

    /**
     * Returns the address of a long constant that is embedded as a data references into the code.
     */
    public Address asLongConstRef(Value value) {
        assert value.kind == Kind.Long && isConstant(value);
        return recordDataReferenceInCode((Constant) value, 8);
    }

    public Address asIntAddr(Value value) {
        assert value.kind == Kind.Int;
        return asAddress(value);
    }

    public Address asLongAddr(Value value) {
        assert value.kind == Kind.Long;
        return asAddress(value);
    }

    public Address asObjectAddr(Value value) {
        assert value.kind == Kind.Object;
        return asAddress(value);
    }

    public Address asFloatAddr(Value value) {
        assert value.kind == Kind.Float;
        return asAddress(value);
    }

    public Address asDoubleAddr(Value value) {
        assert value.kind == Kind.Double;
        return asAddress(value);
    }

    public Address asAddress(Value value) {
        if (isStackSlot(value)) {
            StackSlot slot = (StackSlot) value;
            return new Address(slot.kind, frameMap.registerConfig.getFrameRegister().asValue(), frameMap.offsetForStackSlot(slot));
        }
        return (Address) value;
    }
}
