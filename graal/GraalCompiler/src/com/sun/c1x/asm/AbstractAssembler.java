/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.asm;

import java.util.*;

import com.sun.c1x.*;
import com.sun.c1x.debug.*;
import com.sun.c1x.ir.*;
import com.sun.c1x.lir.*;
import com.sun.c1x.util.*;
import com.sun.cri.ci.*;
import com.sun.cri.ci.CiTargetMethod.CodeComment;
import com.sun.cri.ci.CiTargetMethod.Mark;
import com.sun.cri.ri.*;

/**
 * @author Marcelo Cintra
 * @author Thomas Wuerthinger
 */
public abstract class AbstractAssembler {

    public final Buffer codeBuffer;
    public final CiTarget target;
    public final CiTargetMethod targetMethod;
    public List<ExceptionInfo> exceptionInfoList;

    public AbstractAssembler(CiTarget target) {
        this.target = target;
        this.targetMethod = new CiTargetMethod();
        this.codeBuffer = new Buffer(target.arch.byteOrder);
    }

    public final void bind(Label l) {
        assert !l.isBound() : "can bind label only once";
        l.bind(codeBuffer.position());
        l.patchInstructions(this);
    }

    public void setFrameSize(int frameSize) {
        targetMethod.setFrameSize(frameSize);
    }

    public CiTargetMethod finishTargetMethod(Object name, RiRuntime runtime, int registerRestoreEpilogueOffset, boolean isStub) {
        // Install code, data and frame size
        targetMethod.setTargetCode(codeBuffer.close(false), codeBuffer.position());
        targetMethod.setRegisterRestoreEpilogueOffset(registerRestoreEpilogueOffset);

        // Record exception handlers if they exist
        if (exceptionInfoList != null) {
            for (ExceptionInfo ei : exceptionInfoList) {
                int codeOffset = ei.codeOffset;
                for (ExceptionHandler handler : ei.exceptionHandlers) {
                    int entryOffset = handler.entryCodeOffset();
                    RiType caughtType = handler.handler.catchType();
                    targetMethod.recordExceptionHandler(codeOffset, ei.bci, handler.scopeCount(), entryOffset, handler.handlerBCI(), caughtType);
                }
            }
        }

        if (C1XOptions.PrintMetrics) {
            C1XMetrics.TargetMethods++;
            C1XMetrics.CodeBytesEmitted += targetMethod.targetCodeSize();
            C1XMetrics.SafepointsEmitted += targetMethod.safepoints.size();
            C1XMetrics.DirectCallSitesEmitted += targetMethod.directCalls.size();
            C1XMetrics.IndirectCallSitesEmitted += targetMethod.indirectCalls.size();
            C1XMetrics.DataPatches += targetMethod.dataReferences.size();
            C1XMetrics.ExceptionHandlersEmitted += targetMethod.exceptionHandlers.size();
        }

        if (C1XOptions.PrintAssembly && !TTY.isSuppressed() && !isStub) {
            Util.printSection("Target Method", Util.SECTION_CHARACTER);
            TTY.println("Name: " + name);
            TTY.println("Frame size: " + targetMethod.frameSize());
            TTY.println("Register size: " + target.arch.registerReferenceMapBitCount);

            if (C1XOptions.PrintCodeBytes) {
                Util.printSection("Code", Util.SUB_SECTION_CHARACTER);
                TTY.println("Code: %d bytes", targetMethod.targetCodeSize());
                Util.printBytes(0L, targetMethod.targetCode(), 0, targetMethod.targetCodeSize(), C1XOptions.PrintAssemblyBytesPerLine);
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

            Util.printSection("Direct Call Sites", Util.SUB_SECTION_CHARACTER);
            for (CiTargetMethod.Call x : targetMethod.directCalls) {
                TTY.println(x.toString());
                if (noDis && x.debugInfo != null) {
                    TTY.println(CiUtil.indent(x.debugInfo.toString(), "  "));
                }
            }

            Util.printSection("Indirect Call Sites", Util.SUB_SECTION_CHARACTER);
            for (CiTargetMethod.Call x : targetMethod.indirectCalls) {
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
            if (info.exceptionHandlers != null) {
                if (exceptionInfoList == null) {
                    exceptionInfoList = new ArrayList<ExceptionInfo>(4);
                }
                exceptionInfoList.add(new ExceptionInfo(pcOffset, info.exceptionHandlers, info.state.bci));
            }
        }
    }

    public void recordImplicitException(int pcOffset, LIRDebugInfo info) {
        // record an implicit exception point
        if (info != null) {
            targetMethod.recordSafepoint(pcOffset, info.debugInfo());
            recordExceptionHandlers(pcOffset, info);
        }
    }

    protected void recordDirectCall(int posBefore, int posAfter, Object target, LIRDebugInfo info) {
        CiDebugInfo debugInfo = info != null ? info.debugInfo() : null;
        targetMethod.recordCall(posBefore, target, debugInfo, true);
    }

    protected void recordIndirectCall(int posBefore, int posAfter, Object target, LIRDebugInfo info) {
        CiDebugInfo debugInfo = info != null ? info.debugInfo() : null;
        targetMethod.recordCall(posBefore, target, debugInfo, false);
    }

    public void recordSafepoint(int pos, LIRDebugInfo info) {
        // safepoints always need debug info
        CiDebugInfo debugInfo = info.debugInfo();
        targetMethod.recordSafepoint(pos, debugInfo);
    }

    public CiAddress recordDataReferenceInCode(CiConstant data) {
        assert data != null;

        int pos = codeBuffer.position();

        if (C1XOptions.TraceRelocation) {
            TTY.print("Data reference in code: pos = %d, data = %s", pos, data.toString());
        }

        targetMethod.recordDataReference(pos, data);
        return CiAddress.Placeholder;
    }

    public Mark recordMark(Object id, Mark[] references) {
        return targetMethod.recordMark(codeBuffer.position(), id, references);
    }

    public abstract void nop();

    public abstract void nullCheck(CiRegister r);

    public abstract void align(int codeEntryAlignment);

    public abstract void patchJumpTarget(int branch, int target);

    public final void emitByte(int x) {
        codeBuffer.emitByte(x);
    }

    public final void emitShort(int x) {
        codeBuffer.emitShort(x);
    }

    public final void emitInt(int x) {
        codeBuffer.emitInt(x);
    }

    public final void emitLong(long x) {
        codeBuffer.emitLong(x);
    }

    public void blockComment(String s) {
        targetMethod.addAnnotation(new CodeComment(codeBuffer.position(), s));
    }
}
