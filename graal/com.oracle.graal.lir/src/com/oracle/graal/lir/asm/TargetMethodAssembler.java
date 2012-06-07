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

import static com.oracle.max.cri.ci.CiValueUtil.*;

import java.util.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIR.Code;
import com.oracle.max.asm.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;

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
    public final CiTargetMethod targetMethod;
    public final CiTarget target;
    public final RiRuntime runtime;
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

    public TargetMethodAssembler(CiTarget target, RiRuntime runtime, FrameMap frameMap, AbstractAssembler asm, FrameContext frameContext, List<Code> stubs) {
        this.target = target;
        this.runtime = runtime;
        this.frameMap = frameMap;
        this.stubs = stubs;
        this.asm = asm;
        this.targetMethod = new CiTargetMethod();
        this.frameContext = frameContext;
        // 0 is a valid pc for safepoints in template methods
        this.lastSafepointPos = -1;
    }

    public void setFrameSize(int frameSize) {
        targetMethod.setFrameSize(frameSize);
    }

    public CiTargetMethod.Mark recordMark(Object id) {
        return targetMethod.recordMark(asm.codeBuffer.position(), id, null);
    }

    public CiTargetMethod.Mark recordMark(Object id, CiTargetMethod.Mark[] references) {
        return targetMethod.recordMark(asm.codeBuffer.position(), id, references);
    }

    public void blockComment(String s) {
        targetMethod.addAnnotation(new CiTargetMethod.CodeComment(asm.codeBuffer.position(), s));
    }

    public CiTargetMethod finishTargetMethod(Object name, boolean isStub) {
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
        Debug.metric("SafepointsEmitted").add(targetMethod.safepoints.size());
        Debug.metric("DataPatches").add(targetMethod.dataReferences.size());
        Debug.metric("ExceptionHandlersEmitted").add(targetMethod.exceptionHandlers.size());

        Debug.log("Finished target method %s, isStub %b", name, isStub);
/*
        if (GraalOptions.PrintAssembly && !TTY.isSuppressed() && !isStub) {
            Util.printSection("Target Method", Util.SECTION_CHARACTER);
            TTY.println("Name: " + name);
            TTY.println("Frame size: " + targetMethod.frameSize());
            TTY.println("Register size: " + asm.target.arch.registerReferenceMapBitCount);

            if (GraalOptions.PrintCodeBytes) {
                Util.printSection("Code", Util.SUB_SECTION_CHARACTER);
                TTY.println("Code: %d bytes", targetMethod.targetCodeSize());
                Util.printBytes(0L, targetMethod.targetCode(), 0, targetMethod.targetCodeSize(), GraalOptions.PrintAssemblyBytesPerLine);
            }

            Util.printSection("Disassembly", Util.SUB_SECTION_CHARACTER);
            String disassembly = runtime.disassemble(targetMethod);
            TTY.println(disassembly);
            boolean noDis = disassembly == null || disassembly.length() == 0;

            Util.printSection("Safepoints", Util.SUB_SECTION_CHARACTER);
            for (CiTargetMethod.Safepoint x : targetMethod.safepoints) {
                TTY.println(x.toString());
                if (noDis && x.debugInfo != null) {
                    TTY.println(CiUtil.indent(x.debugInfo.toString(), "  "));
                }
            }

            Util.printSection("Data Patches", Util.SUB_SECTION_CHARACTER);
            for (CiTargetMethod.DataPatch x : targetMethod.dataReferences) {
                TTY.println(x.toString());
            }

            Util.printSection("Marks", Util.SUB_SECTION_CHARACTER);
            for (CiTargetMethod.Mark x : targetMethod.marks) {
                TTY.println(x.toString());
            }

            Util.printSection("Exception Handlers", Util.SUB_SECTION_CHARACTER);
            for (CiTargetMethod.ExceptionHandler x : targetMethod.exceptionHandlers) {
                TTY.println(x.toString());
            }
        }
*/

        return targetMethod;
    }

    public void recordExceptionHandlers(int pcOffset, LIRDebugInfo info) {
        if (info != null) {
            if (info.exceptionEdge != null) {
                if (exceptionInfoList == null) {
                    exceptionInfoList = new ArrayList<>(4);
                }
                exceptionInfoList.add(new ExceptionInfo(pcOffset, info.exceptionEdge));
            }
        }
    }

    public void recordImplicitException(int pcOffset, LIRDebugInfo info) {
        // record an implicit exception point
        if (info != null) {
            assert lastSafepointPos < pcOffset : lastSafepointPos + "<" + pcOffset;
            lastSafepointPos = pcOffset;
            targetMethod.recordSafepoint(pcOffset, info.debugInfo());
            assert info.exceptionEdge == null;
        }
    }

    public void recordDirectCall(int posBefore, int posAfter, Object callTarget, LIRDebugInfo info) {
        CiDebugInfo debugInfo = info != null ? info.debugInfo() : null;
        assert lastSafepointPos < posAfter;
        lastSafepointPos = posAfter;
        targetMethod.recordCall(posBefore, posAfter - posBefore, callTarget, debugInfo, true);
    }

    public void recordIndirectCall(int posBefore, int posAfter, Object callTarget, LIRDebugInfo info) {
        CiDebugInfo debugInfo = info != null ? info.debugInfo() : null;
        assert lastSafepointPos < posAfter;
        lastSafepointPos = posAfter;
        targetMethod.recordCall(posBefore, posAfter - posBefore, callTarget, debugInfo, false);
    }

    public void recordSafepoint(int pos, LIRDebugInfo info) {
        // safepoints always need debug info
        CiDebugInfo debugInfo = info.debugInfo();
        assert lastSafepointPos < pos;
        lastSafepointPos = pos;
        targetMethod.recordSafepoint(pos, debugInfo);
    }

    public CiAddress recordDataReferenceInCode(RiConstant data, int alignment) {
        assert data != null;
        int pos = asm.codeBuffer.position();
        Debug.log("Data reference in code: pos = %d, data = %s", pos, data.toString());
        targetMethod.recordDataReference(pos, data, alignment);
        return CiAddress.Placeholder;
    }

    public int lastSafepointPos() {
        return lastSafepointPos;
    }


    /**
     * Returns the integer value of any constants that can be represented by a 32-bit integer value,
     * including long constants that fit into the 32-bit range.
     */
    public int asIntConst(CiValue value) {
        assert (value.kind.stackKind() == CiKind.Int || value.kind == CiKind.Jsr || value.kind == CiKind.Long) && isConstant(value);
        long c = ((RiConstant) value).asLong();
        if (!(NumUtil.isInt(c))) {
            throw GraalInternalError.shouldNotReachHere();
        }
        return (int) c;
    }

    /**
     * Returns the address of a float constant that is embedded as a data references into the code.
     */
    public CiAddress asFloatConstRef(CiValue value) {
        return asFloatConstRef(value, 4);
    }

    public CiAddress asFloatConstRef(CiValue value, int alignment) {
        assert value.kind == CiKind.Float && isConstant(value);
        return recordDataReferenceInCode((RiConstant) value, alignment);
    }

    /**
     * Returns the address of a double constant that is embedded as a data references into the code.
     */
    public CiAddress asDoubleConstRef(CiValue value) {
        return asDoubleConstRef(value, 8);
    }

    public CiAddress asDoubleConstRef(CiValue value, int alignment) {
        assert value.kind == CiKind.Double && isConstant(value);
        return recordDataReferenceInCode((RiConstant) value, alignment);
    }

    /**
     * Returns the address of a long constant that is embedded as a data references into the code.
     */
    public CiAddress asLongConstRef(CiValue value) {
        assert value.kind == CiKind.Long && isConstant(value);
        return recordDataReferenceInCode((RiConstant) value, 8);
    }

    public CiAddress asIntAddr(CiValue value) {
        assert value.kind == CiKind.Int;
        return asAddress(value);
    }

    public CiAddress asLongAddr(CiValue value) {
        assert value.kind == CiKind.Long;
        return asAddress(value);
    }

    public CiAddress asObjectAddr(CiValue value) {
        assert value.kind == CiKind.Object;
        return asAddress(value);
    }

    public CiAddress asFloatAddr(CiValue value) {
        assert value.kind == CiKind.Float;
        return asAddress(value);
    }

    public CiAddress asDoubleAddr(CiValue value) {
        assert value.kind == CiKind.Double;
        return asAddress(value);
    }

    public CiAddress asAddress(CiValue value) {
        if (isStackSlot(value)) {
            CiStackSlot slot = (CiStackSlot) value;
            return new CiAddress(slot.kind, frameMap.registerConfig.getFrameRegister().asValue(), frameMap.offsetForStackSlot(slot));
        }
        return (CiAddress) value;
    }
}
