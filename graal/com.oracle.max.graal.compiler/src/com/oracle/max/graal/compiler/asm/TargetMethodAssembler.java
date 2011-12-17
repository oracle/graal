/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.asm;

import java.util.*;

import com.oracle.max.asm.*;
import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.util.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

public class TargetMethodAssembler {
    public final GraalCompilation compilation;
    public final AbstractAssembler asm;
    public final CiTargetMethod targetMethod;
    public final CiTarget target;
    private List<ExceptionInfo> exceptionInfoList;
    private int lastSafepointPos;

    public TargetMethodAssembler(GraalCompilation compilation, AbstractAssembler asm) {
        this.compilation = compilation;
        this.asm = asm;
        this.targetMethod = new CiTargetMethod();
        this.target = compilation.compiler.target;
        // 0 is a valid pc for safepoints in template methods
        this.lastSafepointPos = -1;
    }

    public void setFrameSize(int frameSize) {
        targetMethod.setFrameSize(frameSize);
    }

    public CiTargetMethod.Mark recordMark(Object id, CiTargetMethod.Mark[] references) {
        return targetMethod.recordMark(asm.codeBuffer.position(), id, references);
    }

    public void blockComment(String s) {
        targetMethod.addAnnotation(new CiTargetMethod.CodeComment(asm.codeBuffer.position(), s));
    }

    public CiTargetMethod finishTargetMethod(Object name, RiRuntime runtime, boolean isStub) {
        // Install code, data and frame size
        targetMethod.setTargetCode(asm.codeBuffer.close(false), asm.codeBuffer.position());

        // Record exception handlers if they exist
        if (exceptionInfoList != null) {
            for (ExceptionInfo ei : exceptionInfoList) {
                int codeOffset = ei.codeOffset;
                targetMethod.recordExceptionHandler(codeOffset, -1, 0, ei.exceptionEdge.label().position(), -1, null);
            }
        }

        if (GraalOptions.Meter) {
            compilation.compiler.context.metrics.TargetMethods++;
            compilation.compiler.context.metrics.CodeBytesEmitted += targetMethod.targetCodeSize();
            compilation.compiler.context.metrics.SafepointsEmitted += targetMethod.safepoints.size();
            compilation.compiler.context.metrics.DataPatches += targetMethod.dataReferences.size();
            compilation.compiler.context.metrics.ExceptionHandlersEmitted += targetMethod.exceptionHandlers.size();
        }

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

        return targetMethod;
    }

    public void recordExceptionHandlers(int pcOffset, LIRDebugInfo info) {
        if (info != null) {
            if (info.exceptionEdge != null) {
                if (exceptionInfoList == null) {
                    exceptionInfoList = new ArrayList<ExceptionInfo>(4);
                }
                exceptionInfoList.add(new ExceptionInfo(pcOffset, info.exceptionEdge, info.topFrame.bci));
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

    public void recordDirectCall(int posBefore, int posAfter, Object target, LIRDebugInfo info) {
        CiDebugInfo debugInfo = info != null ? info.debugInfo() : null;
        assert lastSafepointPos < posAfter;
        lastSafepointPos = posAfter;
        targetMethod.recordCall(posBefore, posAfter - posBefore, target, debugInfo, true);
    }

    public void recordIndirectCall(int posBefore, int posAfter, Object target, LIRDebugInfo info) {
        CiDebugInfo debugInfo = info != null ? info.debugInfo() : null;
        assert lastSafepointPos < posAfter;
        lastSafepointPos = posAfter;
        targetMethod.recordCall(posBefore, posAfter - posBefore, target, debugInfo, false);
    }

    public void recordSafepoint(int pos, LIRDebugInfo info) {
        // safepoints always need debug info
        CiDebugInfo debugInfo = info.debugInfo();
        assert lastSafepointPos < pos;
        lastSafepointPos = pos;
        targetMethod.recordSafepoint(pos, debugInfo);
    }

    public CiAddress recordDataReferenceInCode(CiConstant data, int alignment) {
        assert data != null;

        int pos = asm.codeBuffer.position();

        if (GraalOptions.TraceRelocation) {
            TTY.print("Data reference in code: pos = %d, data = %s", pos, data.toString());
        }

        targetMethod.recordDataReference(pos, data, alignment);
        return CiAddress.Placeholder;
    }

    public int lastSafepointPos() {
        return lastSafepointPos;
    }


    public CiRegister asIntReg(CiValue value) {
        assert value.kind == CiKind.Int || value.kind == CiKind.Jsr;
        return asRegister(value);
    }

    public CiRegister asLongReg(CiValue value) {
        assert value.kind == CiKind.Long : value.kind;
        return asRegister(value);
    }

    public CiRegister asObjectReg(CiValue value) {
        assert value.kind == CiKind.Object;
        return asRegister(value);
    }

    public CiRegister asFloatReg(CiValue value) {
        assert value.kind == CiKind.Float;
        return asRegister(value);
    }

    public CiRegister asDoubleReg(CiValue value) {
        assert value.kind == CiKind.Double;
        return asRegister(value);
    }

    public CiRegister asRegister(CiValue value) {
        assert value.isRegister();
        return value.asRegister();
    }

    /**
     * Returns the integer value of any constants that can be represented by a 32-bit integer value,
     * including long constants that fit into the 32-bit range.
     */
    public int asIntConst(CiValue value) {
        assert (value.kind.stackKind() == CiKind.Int || value.kind == CiKind.Jsr || value.kind == CiKind.Long) && value.isConstant();
        long c = ((CiConstant) value).asLong();
        if (!(NumUtil.isInt(c))) {
            throw Util.shouldNotReachHere();
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
        assert value.kind == CiKind.Float && value.isConstant();
        return recordDataReferenceInCode((CiConstant) value, alignment);
    }

    /**
     * Returns the address of a double constant that is embedded as a data references into the code.
     */
    public CiAddress asDoubleConstRef(CiValue value) {
        return asDoubleConstRef(value, 8);
    }

    public CiAddress asDoubleConstRef(CiValue value, int alignment) {
        assert value.kind == CiKind.Double && value.isConstant();
        return recordDataReferenceInCode((CiConstant) value, alignment);
    }

    public CiAddress asAddress(CiValue value) {
        if (value.isStackSlot()) {
            return compilation.frameMap().toStackAddress((CiStackSlot) value);
        }
        return (CiAddress) value;
    }
}
